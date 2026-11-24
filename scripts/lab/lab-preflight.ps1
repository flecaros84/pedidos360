# Run before terraform apply. Cannot guarantee all account permissions; checks critical access and quota visibility.
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
aws ec2 describe-vpcs --max-results 5 --output json > $null
if ($LASTEXITCODE -ne 0) { throw 'EC2 DescribeVpcs check failed; see the AWS CLI error above.' }
aws ec2 describe-instance-type-offerings --location-type region --filters 'Name=instance-type,Values=t3.micro,t3.small,t3.medium,t3.large' --max-results 30 --output table
if ($LASTEXITCODE -ne 0) { throw 'Instance types are not queryable in this account/region.' }
Write-Warning 'Check AWS Academy credits and available vCPU quotas manually before apply.'
Write-Warning 'Verify VPC Link, API Gateway, DynamoDB, RDS, Lambda, SSM and LabInstanceProfile permissions.'
