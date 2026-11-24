# Deploy the latest prebuilt container images via SSM; no SSH or static instance IDs required.
param([string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
$instances = @(Get-LabInstances | Where-Object { $_.State -eq 'running' -and $_.Name -ne "$Prefix-nat" })
if ($instances.Count -eq 0) { throw 'No running lab instances.' }
foreach ($server in $instances) {
    $service = $server.Name.Substring("$Prefix-".Length)
    $script = "sudo /opt/pedidos360/configure.sh && sudo systemctl restart pedidos360.service"
    $tmp = New-TemporaryFile
    try {
      @{ DocumentName = 'AWS-RunShellScript'; InstanceIds = @($server.Id); Parameters = @{commands = @($script)} } |
        ConvertTo-Json -Depth 5 | Set-Content $tmp
      aws ssm send-command --cli-input-json "file://$($tmp.FullName)" --output none
      if ($LASTEXITCODE -ne 0) { throw "SSM deploy failed for $service" }
      Write-Host "SSM deployment queued: $service"
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}
