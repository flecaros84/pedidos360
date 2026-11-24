# Deploy the latest prebuilt container images via SSM; no SSH or static instance IDs required.
param([string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
$instances = @(Get-LabInstances | Where-Object { $_.State -eq 'running' -and $_.Name -like "$Prefix-*" })
$services = @('rabbitmq','kafka','products','cart','auth','orders','notifications','payment','audit','analytics','dispatch','frontend')
if ($instances.Count -eq 0) { throw 'No running lab instances.' }
foreach ($service in $services) {
    $server = $instances | Where-Object { $_.Name -eq "$Prefix-$service" } | Select-Object -First 1
    if (-not $server) { throw "No running instance found for $service" }
    $script = "sudo /opt/pedidos360/configure.sh && sudo systemctl restart pedidos360.service"
    $tmp = New-TemporaryFile
    try {
      @{ DocumentName = 'AWS-RunShellScript'; InstanceIds = @($server.Id); Parameters = @{commands = @($script)} } |
        ConvertTo-Json -Depth 5 | Set-Content $tmp
       $commandId = aws ssm send-command --cli-input-json "file://$($tmp.FullName)" --query Command.CommandId --output text
      if ($LASTEXITCODE -ne 0 -or -not $commandId) { throw "SSM request failed for $service" }
      aws ssm wait command-executed --command-id $commandId --instance-id $server.Id
      if ($LASTEXITCODE -ne 0) { throw "SSM deployment failed or timed out for $service (command $commandId)" }
      $status = aws ssm get-command-invocation --command-id $commandId --instance-id $server.Id --query Status --output text
      if ($LASTEXITCODE -ne 0 -or $status -ne "Success") { throw "SSM deployment unsuccessful for $service ($status)" }
      Write-Host "SSM deployment succeeded: $service"
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}
