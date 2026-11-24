# Repair the original EC2 user-data bootstrap after the AL2023 curl-minimal conflict.
# Does not create, terminate, or restart EC2 instances, and never touches the NAT host.
param([string]$Prefix = 'pedidos360')

. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession

$serviceHosts = @('cart','products','audit','analytics','dispatch','frontend','rabbitmq','kafka')
$expectedNames = @($serviceHosts | ForEach-Object { "$Prefix-$_" })
$instances = @(Get-LabInstances | Where-Object { $_.State -eq 'running' -and $_.Name -in $expectedNames })
if ($instances.Count -ne $expectedNames.Count) {
    $found = @($instances | ForEach-Object { $_.Name }) -join ', '
    throw "Expected 8 running application hosts; found $($instances.Count): $found"
}
$commands = @(
    'set -eu',
    'init_script="$(find /var/lib/cloud/instance/scripts -maxdepth 1 -type f -name "part-*" -print -quit)"',
    'test -n "$init_script" && test -f "$init_script"',
    'if grep -qF "dnf install -y docker curl jq" "$init_script"; then',
    '  sed -i "s/dnf install -y docker curl jq/dnf install -y docker jq/" "$init_script"',
    'fi',
    'if ! command -v docker >/dev/null 2>&1 || [ ! -x /opt/pedidos360/configure.sh ]; then',
    '  cloud-init single --name scripts-user --frequency always',
    'fi',
    'docker --version',
    'aws --version',
    'test -x /opt/pedidos360/configure.sh',
    'test -x /opt/pedidos360/start.sh',
    'systemctl is-active --quiet docker',
    'echo BOOTSTRAP_OK'
)
$request = @{
    DocumentName = 'AWS-RunShellScript'
    InstanceIds = @($instances | ForEach-Object { $_.Id })
    Parameters = @{ commands = @($commands) }
    TimeoutSeconds = 900
} | ConvertTo-Json -Depth 8
$tmp = New-TemporaryFile
try {
    [System.IO.File]::WriteAllText($tmp.FullName, $request, [System.Text.UTF8Encoding]::new($false))
    $commandId = aws ssm send-command --cli-input-json "file://$($tmp.FullName)" --query Command.CommandId --output text
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commandId) -or $commandId -eq 'None') {
        throw 'Could not send the repair command to SSM.'
    }
} finally {
    Remove-Item $tmp.FullName -Force -ErrorAction SilentlyContinue
}
Write-Host "SSM command: $commandId" -ForegroundColor Cyan
$failed = @()
foreach ($instance in ($instances | Sort-Object Name)) {
    aws ssm wait command-executed --command-id $commandId --instance-id $instance.Id 2>$null
    $raw = aws ssm get-command-invocation --command-id $commandId --instance-id $instance.Id --output json
    if ($LASTEXITCODE -ne 0) {
        $failed += $instance.Name
        Write-Host "FAILED: $($instance.Name): Unable to read SSM invocation" -ForegroundColor Red
        continue
    }
    $result = $raw | ConvertFrom-Json
    if ($result.Status -eq 'Success' -and $result.StandardOutputContent -match 'BOOTSTRAP_OK') {
        Write-Host "OK: $($instance.Name)" -ForegroundColor Green
    } else {
        $failed += $instance.Name
        Write-Host "FAILED: $($instance.Name) [$($result.Status)]" -ForegroundColor Red
        Write-Host (($result.StandardOutputContent.Split([char]10) | Select-Object -Last 12) -join [Environment]::NewLine)
        Write-Host (($result.StandardErrorContent.Split([char]10) | Select-Object -Last 12) -join [Environment]::NewLine)
    }
}
if ($failed.Count -gt 0) {
    throw "Bootstrap failed for: $($failed -join ', '). Inspect SSM command $commandId."
}
Write-Host "BOOTSTRAP_OK: all eight application hosts are ready." -ForegroundColor Green
