# Upload per-service runtime .env files as encrypted SSM SecureString parameters (outside Git/Terraform state).
param([Parameter(Mandatory=$true)][string]$Name,
      [Parameter(Mandatory=$true)][string]$File,
      [string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
if (-not (Test-Path $File)) { throw "Missing env file: $File" }
if ($Name -notmatch '^[a-z][a-z0-9-]+$') { throw 'Invalid service name.' }
$contents = Get-Content $File -Raw
if ($contents.Length -gt 4096) { throw 'SSM Standard SecureString limit exceeded (4 KB). Split configuration.' }
# Secrets passed using a local value file to avoid exposing values on the process command line.
$tmp = New-TemporaryFile
try {
  @{ Name = "/$Prefix/lab/$Name/env"; Type = 'SecureString'; Value = $contents; Overwrite = $true } |
    ConvertTo-Json -Compress | Set-Content -Path $tmp -NoNewline
  aws ssm put-parameter --cli-input-json "file://$($tmp.FullName)" --output none
  if ($LASTEXITCODE -ne 0) { throw 'SSM SecureString upload failed.' }
  Write-Host "Stored encrypted configuration for $Name"
} finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
