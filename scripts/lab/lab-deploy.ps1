# Deploy all containers across five shared backend hosts, plus Kafka, RabbitMQ and frontend.
param(
    [string]$Prefix = 'pedidos360',
    [string]$ImageTag = 'integration'
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
$services = @('rabbitmq','kafka','products','cart','auth','orders',
              'notifications','payment','audit','analytics','dispatch','frontend')
$instances = @(Get-LabInstances | Where-Object { $_.State -eq 'running' })

foreach ($service in $services) {
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
