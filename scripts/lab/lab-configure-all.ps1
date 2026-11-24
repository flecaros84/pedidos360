param([string]$Directory='.env.generated')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$names=@('auth','cart','products','orders','notifications','payment','audit','analytics','dispatch','frontend','rabbitmq','kafka')
foreach($name in $names) {
  $file=Join-Path $Directory "$name.env"
  & "$PSScriptRoot/lab-configure.ps1" -Name $name -File $file
  if (-not $?) { throw "Failed to configure $name" }
}
