# Start only Pedido360 resources, in dependency order. Run with AWS Academy temporary credentials.
param([string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
Write-Host 'Starting NAT, Kafka and RabbitMQ ...'
Invoke-InstanceAction start @("$Prefix-nat", "$Prefix-rabbitmq", "$Prefix-kafka")
$db = aws rds describe-db-instances --db-instance-identifier "$Prefix-postgres" --query 'DBInstances[0].DBInstanceStatus' --output text 2>$null
if ($LASTEXITCODE -eq 0 -and $db -eq 'stopped') {
    aws rds start-db-instance --db-instance-identifier "$Prefix-postgres" --output none
    if ($LASTEXITCODE -ne 0) { throw 'RDS start failed.' }
    aws rds wait db-instance-available --db-instance-identifier "$Prefix-postgres"
    if ($LASTEXITCODE -ne 0) { throw 'RDS did not become available.' }
}
Write-Host 'Starting backend and frontend EC2 ...'
$others = @(Get-LabInstances | Where-Object { $_.Name -notin @("$Prefix-nat", "$Prefix-rabbitmq", "$Prefix-kafka") })
Invoke-InstanceAction start @($others | ForEach-Object { $_.Name })
& "$PSScriptRoot/lab-status.ps1" -Prefix $Prefix
