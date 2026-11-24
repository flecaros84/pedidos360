param([string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
Get-LabInstances | Sort-Object Name | Format-Table Name, State, Id, PublicIp -AutoSize
$db = aws rds describe-db-instances --db-instance-identifier "$Prefix-postgres" --query 'DBInstances[0].[DBInstanceStatus,Endpoint.Address]' --output json 2>$null
if ($LASTEXITCODE -eq 0) { Write-Host 'RDS:'; $db | ConvertFrom-Json | Format-Table }
$rb = Get-LabInstances | Where-Object Name -eq "$Prefix-rabbitmq" | Select-Object -First 1
if ($rb -and $rb.PublicIp) { Write-Host "RabbitMQ console (CIDR-restricted): http://$($rb.PublicIp):15672" }
Write-Host 'Note: if an EC2 dynamic IP changes after restart, update Entra redirect origins and API CORS as needed.'
