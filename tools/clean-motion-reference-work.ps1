# Remove only this task's disposable, inventoried exports. No game or workspace resources.
$ErrorActionPreference='Stop'
$enemyWork=[IO.Path]::GetFullPath('C:\Users\ecorange1\AppData\Local\Temp\ZombieRool-motion-repair')
$expectedParent=[IO.Path]::GetFullPath('C:\Users\ecorange1\AppData\Local\Temp')
if ((Split-Path -Parent $enemyWork) -ne $expectedParent -or (Split-Path -Leaf $enemyWork) -ne 'ZombieRool-motion-repair') {throw 'Unexpected work folder'}
if (!(Test-Path -LiteralPath $enemyWork)) {Write-Output 'Enemy work folder already absent';exit 0}
$resolvedWork=(Resolve-Path -LiteralPath $enemyWork).ProviderPath
if ($resolvedWork -ne $enemyWork) {throw 'Unexpected resolved path'}
if ((Get-Item -LiteralPath $resolvedWork -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {throw 'Refuse a linked work root'}
$enemyFiles=@(Get-ChildItem -LiteralPath $resolvedWork -Recurse -Force)
foreach ($entry in $enemyFiles) {if (!$entry.FullName.StartsWith($resolvedWork+'\',[StringComparison]::OrdinalIgnoreCase) -or ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw 'Unexpected nested path'}}
$removedBytes=($enemyFiles | Where-Object {!$_.PSIsContainer} | Measure-Object Length -Sum).Sum
foreach ($entry in $enemyFiles | Where-Object {!$_.PSIsContainer}) {Remove-Item -LiteralPath $entry.FullName -Force}
foreach ($entry in $enemyFiles | Where-Object PSIsContainer | Sort-Object {$_.FullName.Length} -Descending) {Remove-Item -LiteralPath $entry.FullName}
Remove-Item -LiteralPath $resolvedWork
Write-Output "Motion reference exports removed: $removedBytes bytes."
