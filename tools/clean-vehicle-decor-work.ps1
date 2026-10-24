# Remove only this task's disposable, inventoried exports. No game or workspace resources.
$ErrorActionPreference='Stop'
$vehicleWork=[IO.Path]::GetFullPath('C:\Users\ecorange1\AppData\Local\Temp\ZombieRool-vehicle-decor')
$expectedParent=[IO.Path]::GetFullPath('C:\Users\ecorange1\AppData\Local\Temp')
if ((Split-Path -Parent $vehicleWork) -ne $expectedParent -or (Split-Path -Leaf $vehicleWork) -ne 'ZombieRool-vehicle-decor') {throw 'Unexpected work folder'}
if (!(Test-Path -LiteralPath $vehicleWork)) {Write-Output 'Vehicle work folder already absent';exit 0}
$resolvedWork=(Resolve-Path -LiteralPath $vehicleWork).ProviderPath
if ($resolvedWork -ne $vehicleWork) {throw 'Unexpected resolved path'}
$vehicleFiles=@(Get-ChildItem -LiteralPath $resolvedWork -Recurse -Force)
foreach ($entry in $vehicleFiles) {if (!$entry.FullName.StartsWith($resolvedWork+'\',[StringComparison]::OrdinalIgnoreCase) -or ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Unexpected nested path'}}
$removedBytes=($vehicleFiles | Where-Object {!$_.PSIsContainer} | Measure-Object Length -Sum).Sum
foreach ($entry in $vehicleFiles | Where-Object {!$_.PSIsContainer}) {Remove-Item -LiteralPath $entry.FullName -Force}
foreach ($entry in $vehicleFiles | Where-Object PSIsContainer | Sort-Object {$_.FullName.Length} -Descending) {Remove-Item -LiteralPath $entry.FullName}
Remove-Item -LiteralPath $resolvedWork
Write-Output "Vehicle temporary exports removed: $removedBytes bytes."
