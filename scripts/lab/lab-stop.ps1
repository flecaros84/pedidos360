# Stop application first, then brokers/NAT and database. EBS/RDS storage, ALB, and other resources still cost money.
param([string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
Write-Host 'Stopping application EC2 ...'
$others = @(Get-LabInstances | Where-Object { $_.Name -notin @("$Prefix-nat", "$Prefix-rabbitmq", "$Prefix-kafka") })
Invoke-InstanceAction stop @($others | ForEach-Object { $_.Name })
Write-Host 'Stopping messaging EC2 and NAT ...'
Invoke-InstanceAction stop @("$Prefix-rabbitmq", "$Prefix-kafka", "$Prefix-nat")
$db = aws rds describe-db-instances --db-instance-identifier "$Prefix-postgres" --query 'DBInstances[0].DBInstanceStatus' --output text 2>$null
if ($LASTEXITCODE -eq 0 -and $db -eq 'available') {
    aws rds stop-db-instance --db-instance-identifier "$Prefix-postgres" --output json > $null
    if ($LASTEXITCODE -ne 0) { throw 'RDS stop failed.' }
}
Write-Host 'EC2 stop requested. EBS, ALB, IP and RDS storage may continue to accrue charges.'
Write-Warning 'Stopped RDS automatically restarts after 7 days: revisit lab before that date.'
