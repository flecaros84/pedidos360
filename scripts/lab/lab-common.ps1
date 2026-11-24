# Common AWS Academy helper functions; requires AWS CLI v2 configured for current session.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-AwsSession {
    $identity = aws sts get-caller-identity --output json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $identity.Account) { throw 'AWS Academy credentials are missing or expired.' }
    Write-Host "AWS account: $($identity.Account)" -ForegroundColor Cyan
}
function Get-LabInstances {
    $raw = aws ec2 describe-instances --filters 'Name=tag:Project,Values=Pedidos360' 'Name=tag:Environment,Values=lab' 'Name=tag:LabControl,Values=true' --output json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot describe lab EC2 instances.' }
    $response = $raw | ConvertFrom-Json
    foreach ($reservation in $response.Reservations) {
        foreach ($instance in $reservation.Instances) {
            $name = @($instance.Tags | Where-Object { $_.Key -eq 'Name' } | Select-Object -ExpandProperty Value | Select-Object -First 1)
            [pscustomobject]@{
                Id = $instance.InstanceId
                State = $instance.State.Name
                Name = if ($name.Count -gt 0) { $name[0] } else { '' }
                PublicIp = $instance.PublicIpAddress
            }
        }
    }
}
function Invoke-InstanceAction([string]$Action,[string[]]$Names) {
    $instances = @(Get-LabInstances | Where-Object { $Names -contains $_.Name })
    $toChange = if ($Action -eq 'start') {
        @($instances | Where-Object { $_.State -eq 'stopped' })
    } else {
        @($instances | Where-Object { $_.State -eq 'running' })
    }
    if ($toChange.Count -eq 0) { return }
    $ids = @($toChange | ForEach-Object { $_.Id })
    if ($Action -eq 'start') {
        aws ec2 start-instances --instance-ids $ids --output none
        if ($LASTEXITCODE -ne 0) { throw "EC2 start failed: $($ids -join ', ')" }
        aws ec2 wait instance-running --instance-ids $ids
    } else {
        aws ec2 stop-instances --instance-ids $ids --output none
        if ($LASTEXITCODE -ne 0) { throw "EC2 stop failed: $($ids -join ', ')" }
        aws ec2 wait instance-stopped --instance-ids $ids
    }
    if ($LASTEXITCODE -ne 0) { throw "EC2 $Action wait failed: $($ids -join ', ')" }
}
