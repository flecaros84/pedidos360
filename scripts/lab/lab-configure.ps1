# Upload per-service runtime .env files as encrypted SSM SecureString parameters (outside Git/Terraform state).
param([Parameter(Mandatory=$true)][string]$Name,
      [Parameter(Mandatory=$true)][string]$File,
      [string]$Prefix='pedidos360')
. "$PSScriptRoot/lab-common.ps1"
Assert-AwsSession
if (-not (Test-Path $File)) { throw "Missing env file: $File" }
if ($Name -notmatch '^[a-z][a-z0-9-]+$') { throw 'Invalid service name.' }
# Read a plain .NET string; Get-Content -Raw can attach PowerShell provider metadata
# which ConvertTo-Json serializes into an object instead of the required string.
$contents = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $File).Path, [System.Text.Encoding]::UTF8)
# Normalize Windows CRLF for Linux docker --env-file consumers.
$contents = $contents.Replace("`r`n", "`n")
if ([System.Text.Encoding]::UTF8.GetByteCount($contents) -gt 4096) { throw 'SSM Standard SecureString limit exceeded (4 KB). Split configuration.' }
# Secrets passed using a local value file to avoid exposing values on the process command line.
$tmp = New-TemporaryFile
try {
  @{ Name = "/$Prefix/lab/$Name/env"; Type = 'SecureString'; Value = $contents; Overwrite = $true } |
    ConvertTo-Json -Compress | ForEach-Object {
      [System.IO.File]::WriteAllText($tmp.FullName, $_, [System.Text.UTF8Encoding]::new($false))
    }
  aws ssm put-parameter --cli-input-json "file://$($tmp.FullName)" --output json > $null
  if ($LASTEXITCODE -ne 0) { throw 'SSM SecureString upload failed.' }
  Write-Host "Stored encrypted configuration for $Name"
} finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
