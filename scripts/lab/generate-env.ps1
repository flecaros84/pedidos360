# Generate per-EC2 .env files from Terraform outputs and a private master config.
# Never commit the generated files; upload them to SSM SecureString.
param([Parameter(Mandatory=$true)][string]$MasterEnv,
      [string]$TerraformDir='terraform/lab',
      [string]$ImageTag='integration')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $MasterEnv)) { throw "Missing master env: $MasterEnv" }
$master = @{}
foreach ($line in Get-Content $MasterEnv) {
  if ($line -match '^\s*(#|$)') { continue }
  if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $master[$Matches[1]] = $Matches[2] }
}
function Required([string]$Key) {
  if (-not $master.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($master[$Key])) {
    throw "Missing $Key in private master env"
  }
  return $master[$Key]
}
$tenant = Required 'AZURE_TENANT_ID'
$client = Required 'AZURE_CLIENT_ID'
$scope = Required 'AZURE_API_SCOPE'
$audience = Required 'AZURE_AUDIENCE'
$dbPassword = Required 'DATABASE_PASSWORD'
$rabbitPassword = Required 'RABBITMQ_PASSWORD'
$rabbitUser = Required 'RABBITMQ_USERNAME'
$internalToken = Required 'INTERNAL_SERVICE_TOKEN'
$antifraudKey = Required 'ANTIFRAUD_HMAC_KEY'
Push-Location $TerraformDir
try { $tf = terraform output -json | ConvertFrom-Json } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'Failed to obtain Terraform outputs.' }
$out = Join-Path (Get-Location) '.env.generated'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$api = $tf.api_base_url.value.TrimEnd('/')
$front = $tf.frontend_url.value.TrimEnd('/')
$ip = $tf.backend_private_ips.value
$postgresHost = $tf.db_endpoint.value
$rabbitPrivate = $tf.rabbitmq_private_ip.value
$kafkaPrivate = $tf.kafka_private_ip.value
$issuer = "https://login.microsoftonline.com/$tenant/v2.0"
$services = @('auth','cart','products','orders','notifications','payment','audit','analytics','dispatch')
foreach ($svc in $services) {
  $lines = [System.Collections.Generic.List[string]]::new()
  $lines.Add("IMAGE_TAG=$ImageTag")
  $lines.Add('APP_SECURITY_MODE=azure')
  $lines.Add("AZURE_TENANT_ID=$tenant")
  $lines.Add("AZURE_CLIENT_ID=$client")
  $lines.Add("AZURE_ISSUER_URI=$issuer")
  $lines.Add("AZURE_AUDIENCE=$audience")
  $lines.Add("APP_CORS_ALLOWED_ORIGINS=$front")
  if ($svc -ne 'auth') {
    $lines.Add("DATABASE_URL=jdbc:postgresql://$postgresHost/pedidos360")
    $lines.Add('DATABASE_USERNAME=pedidos360')
    $lines.Add("DATABASE_PASSWORD=$dbPassword")
  }
  if ($svc -in @('products','orders','notifications','payment','audit','dispatch')) {
    $lines.Add("RABBITMQ_HOST=$rabbitPrivate")
    $lines.Add("RABBITMQ_USERNAME=$rabbitUser")
    $lines.Add("RABBITMQ_PASSWORD=$rabbitPassword")
  }
  if ($svc -in @('cart','orders')) { $lines.Add("INTERNAL_SERVICE_TOKEN=$internalToken") }
  if ($svc -in @('products','cart','analytics')) { $lines.Add("KAFKA_BOOTSTRAP_SERVERS=${kafkaPrivate}:9092") }
  if ($svc -in @('cart')) { $lines.Add("PRODUCTS_INTERNAL_URL=http://$($ip.products):8083") }
  if ($svc -eq 'products') { $lines.Add('CATALOG_SEED_DEMO=true'); $lines.Add("INTERNAL_SERVICE_TOKEN=$internalToken") }
  if ($svc -eq 'orders') { $lines.Add("CART_INTERNAL_URL=http://$($ip.cart):8082") }
  if ($svc -eq 'payment') {
    $lines.Add("ORDERS_INTERNAL_URL=http://$($ip.orders):8084")
    $lines.Add("PRODUCTS_INTERNAL_URL=http://$($ip.products):8083")
    $lines.Add("INTERNAL_SERVICE_TOKEN=$internalToken")
    $lines.Add("ANTIFRAUD_HMAC_KEY=$antifraudKey")
    $simulate = if ($master.ContainsKey('PAYMENT_SIMULATION_ENABLED')) { $master['PAYMENT_SIMULATION_ENABLED'] } else { 'true' }
    $lines.Add("PAYMENT_SIMULATION_ENABLED=$simulate")
    $lines.Add("FRONTEND_PUBLIC_URL=$front")
    $lines.Add("WEBPAY_RETURN_URL=$api/api/payments/webpay/return")
    foreach ($name in @('WEBPAY_COMMERCE_CODE','WEBPAY_API_KEY','WEBPAY_API_BASE')) {
      if ($master.ContainsKey($name)) { $lines.Add("$name=$($master[$name])") }
    }
  }
  if ($svc -eq 'notifications') {
    foreach ($name in @('SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','SMTP_FROM')) {
      $lines.Add("$name=$(Required $name)")
    }
    $lines.Add('SMTP_AUTH=true');$lines.Add('SMTP_STARTTLS=true')
    $lines.Add('NOTIFICATIONS_ALLOW_TEST=true')
  }
  if ($svc -eq 'analytics') {
    $region = if ($master.ContainsKey("AWS_REGION") -and -not [string]::IsNullOrWhiteSpace($master["AWS_REGION"])) { $master["AWS_REGION"] } else { "us-east-1" }
    $lines.Add("AWS_REGION=$region")
    $lines.Add("DYNAMODB_TABLE=$($tf.dynamodb_table.value)")
    $lines.Add("WS_TICKETS_TABLE=$($tf.ws_tickets_table.value)")
    $lines.Add("WS_CONNECTIONS_TABLE=$($tf.ws_connections_table.value)")
    $lines.Add("WS_MANAGEMENT_ENDPOINT=$($tf.ws_management_endpoint.value)")
  }
  Set-Content -Path (Join-Path $out "$svc.env") -Value ($lines -join "`n")
}
$frontEnv = @(
  "IMAGE_TAG=$ImageTag", 'AUTH_MODE=azure',
  "AZURE_TENANT_ID=$tenant", "AZURE_CLIENT_ID=$client", "AZURE_API_SCOPE=$scope",
  "AZURE_REDIRECT_URI=$front", "AUTH_API_URL=$api", "CART_API_URL=$api",
  "PRODUCTS_API_URL=$api", "ORDERS_API_URL=$api", "PAYMENTS_API_URL=$api",
  "ANALYTICS_API_URL=$api", "DISPATCH_API_URL=$api", "WS_PUBLIC_URL=$($tf.ws_public_url.value)"
)
Set-Content -Path (Join-Path $out 'frontend.env') -Value ($frontEnv -join "`n")
Set-Content -Path (Join-Path $out 'rabbitmq.env') -Value @(
  "RABBITMQ_DEFAULT_USER=$rabbitUser", "RABBITMQ_DEFAULT_PASS=$rabbitPassword"
)
Set-Content -Path (Join-Path $out 'kafka.env') -Value @(
  'KAFKA_NODE_ID=1','KAFKA_PROCESS_ROLES=broker,controller',
  'KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093',
  "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${kafkaPrivate}:9092",
  'KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER',
  'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
  'KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093',
  'KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT',
  'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
  'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1',
  'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1',
  'KAFKA_LOG_DIRS=/var/lib/kafka/data'
)
Write-Host "Generated 12 .env files in $out. Treat them as secrets."
