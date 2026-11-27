# Deploy all containers across five shared backend hosts, plus Kafka, RabbitMQ and frontend.
param(
    [string]$Prefix = 'pedidos360',
    [string]$ImageTag = 'integration',
    [string[]]$Services = @()
)
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession

if ($ImageTag -notmatch '^(integration|[a-f0-9]{40})$') {
    throw 'ImageTag must be integration or a full immutable git SHA.'
}

$hosts = @{
    auth = 'cart'
    cart = 'cart'
    products = 'products'
    orders = 'products'
    notifications = 'audit'
    audit = 'audit'
    payment = 'dispatch'
    dispatch = 'dispatch'
    analytics = 'analytics'
    rabbitmq = 'rabbitmq'
    kafka = 'kafka'
    frontend = 'frontend'
}
$ports = @{
    auth = 8081
    cart = 8082
    products = 8083
    orders = 8084
    notifications = 8085
    payment = 8086
    audit = 8087
    analytics = 8088
    dispatch = 8089
}
# PowerShell variable names are case-insensitive: never reuse $Services as $services.
$availableServices = @('rabbitmq','kafka','products','cart','auth','orders',
                       'notifications','payment','audit','analytics','dispatch','frontend')
$requestedServices = @($Services)
if ($requestedServices.Count -gt 0) {
    foreach ($name in $requestedServices) {
        if ($name -notin $availableServices) { throw "Unknown service: $name" }
    }
    $deployServices = @($availableServices | Where-Object { $_ -in $requestedServices })
} else {
    $deployServices = @($availableServices)
}
Write-Host "Deploy selection: $($deployServices -join ', ')" -ForegroundColor Cyan
$instances = @(Get-LabInstances | Where-Object { $_.State -eq 'running' })

foreach ($service in $deployServices) {
    $hostName = "$Prefix-$($hosts[$service])"
    $server = $instances | Where-Object { $_.Name -eq $hostName } | Select-Object -First 1
    if (-not $server) { throw "No running EC2 host '$hostName' for $service." }

    if ($hosts[$service] -eq $service) {
        # Primary service keeps its existing systemd unit.
        $commands = @(
            'set -e',
            '/opt/pedidos360/configure.sh',
            "sed -i '/^IMAGE_TAG=/d' /opt/pedidos360/service.env",
            "echo IMAGE_TAG=$ImageTag >> /opt/pedidos360/service.env",
            'systemctl restart pedidos360.service',
            'sleep 3',
            "docker inspect -f '{{.State.Running}}' pedidos360-$service | grep -qx true"
        )
        if ($service -eq 'kafka') {
            # The official Kafka container runs as UID/GID 1000 and needs write access.
            $commands = @(
                'set -e',
                'install -d -m 755 /opt/pedidos360/kafka-data',
                'chown -R 1000:1000 /opt/pedidos360/kafka-data'
            ) + $commands + @(
                'docker rm -f pedidos360-kafka-ui >/dev/null 2>&1 || true',
                'docker pull ghcr.io/kafbat/kafka-ui:v1.5.1',
                'docker run -d --name pedidos360-kafka-ui --restart unless-stopped --network host --memory 850m -e KAFKA_CLUSTERS_0_NAME=pedidos360 -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=127.0.0.1:9092 -e DYNAMIC_CONFIG_ENABLED=false ghcr.io/kafbat/kafka-ui:v1.5.1',
                "docker inspect -f '{{.State.Running}}' pedidos360-kafka-ui | grep -qx true"
            )
        }
    } else {
        # Secondary service runs as its own named Docker container on the shared host.
        $envFile = "/opt/pedidos360/$service.env"
        $image = "ghcr.io/flecaros84/pedidos360-$($service):$ImageTag"
        $port = $ports[$service]
        $commands = @(
            'set -e',
            "aws ssm get-parameter --name /pedidos360/lab/$service/env --with-decryption --query Parameter.Value --output text > $envFile",
            "chmod 600 $envFile",
            "docker rm -f pedidos360-$service >/dev/null 2>&1 || true",
            "docker pull $image",
            "docker run -d --name pedidos360-$service --restart unless-stopped --env-file $envFile -p $($port):$($port) $image",
            'sleep 3',
            "docker inspect -f '{{.State.Running}}' pedidos360-$service | grep -qx true"
        )
    }

    $tmp = New-TemporaryFile
    try {
        $request = @{
            DocumentName = 'AWS-RunShellScript'
            InstanceIds = @($server.Id)
            Parameters = @{ commands = @($commands) }
        } | ConvertTo-Json -Depth 8
        [System.IO.File]::WriteAllText($tmp.FullName, $request,
            [System.Text.UTF8Encoding]::new($false))
        $commandId = aws ssm send-command --cli-input-json "file://$($tmp.FullName)" --query Command.CommandId --output text
        if ($LASTEXITCODE -ne 0 -or -not $commandId -or $commandId -eq 'None') {
            throw "Could not schedule SSM deployment for $service."
        }
        aws ssm wait command-executed --command-id $commandId --instance-id $server.Id
        if ($LASTEXITCODE -ne 0) { throw "SSM deployment did not complete for $service. Command: $commandId" }
        $status = aws ssm get-command-invocation --command-id $commandId --instance-id $server.Id --query Status --output text
        if ($LASTEXITCODE -ne 0 -or $status -ne 'Success') {
            throw "SSM deployment failed for $service (status: $status, command: $commandId)."
        }
        Write-Host "Deployed $service on $hostName ($($server.Id))" -ForegroundColor Green
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}
