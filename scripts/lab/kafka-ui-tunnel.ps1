# Open a private SSM tunnel to the Kafka UI running on the Kafka EC2 host.
param([string]$Prefix='pedidos360',[int]$LocalPort=18080)
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
$instance = Get-LabInstances |
    Where-Object { $_.Name -eq "$Prefix-kafka" } |
    Select-Object -First 1
if (-not $instance -or $instance.State -ne 'running') {
    throw "Start the $Prefix-kafka instance before opening Kafka UI."
}
Write-Host "Kafka UI: http://127.0.0.1:$LocalPort" -ForegroundColor Green
Write-Host "Leave this PowerShell window open. Press Ctrl+C to close the tunnel."
aws ssm start-session `
    --target $instance.Id `
    --document-name AWS-StartPortForwardingSession `
    --parameters "portNumber=8080,localPortNumber=$LocalPort" `
    --region us-east-1
if ($LASTEXITCODE -ne 0) { throw 'SSM port forwarding failed; check AWS credentials and Session Manager Plugin.' }
