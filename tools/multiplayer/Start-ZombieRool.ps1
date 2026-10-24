param([string]$Map, [int]$MemoryGB = 6, [switch]$Check, [switch]$DownloadOnly)
$ErrorActionPreference = 'Stop'
try {
    Set-Location -LiteralPath $PSScriptRoot
    . (Join-Path $PSScriptRoot 'Map-Catalogue.ps1')
    $maps = @(Get-ZombieRoolMaps -ServerRoot $PSScriptRoot -Offline:$Check)
    if (!$Map) {
        Write-Host '=== ZombieRool : choix de la map ===' -ForegroundColor Cyan
        for ($i=0; $i -lt $maps.Count; $i++) {
            $entry = $maps[$i]
            $label = switch ($entry.source) { 'official' { 'Officielle' } 'community' { 'Communaute' } default { 'Instance no mods' } }
            $installed = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "worlds/$($entry.id)/level.dat")) { 'installee' } else { 'a telecharger' }
            Write-Host "$($i+1). [$label] $($entry.name) - $($entry.version) ($installed)"
        }
        $choice = 0
        $answer = Read-Host 'Numero de la map (0 pour quitter)'
        if (![int]::TryParse($answer, [ref]$choice) -or $choice -lt 0 -or $choice -gt $maps.Count) { throw 'Choix invalide.' }
        if ($choice -eq 0) { return }
        $Map = $maps[$choice-1].id
    }
    $selected = $maps | Where-Object { $_.id -eq $Map }
    if (!$selected) { throw "Map inconnue : $Map" }
    if (!$Check) { Install-ZombieRoolMap -ServerRoot $PSScriptRoot -Map $selected }
    if ($DownloadOnly) { Write-Host "OK : $($selected.name), map disponible pour le serveur."; return }
    if ($MemoryGB -lt 2 -or $MemoryGB -gt 32) { throw 'RAM : choisir entre 2 et 32 Go.' }
    $java = 'java.exe'
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $local = Join-Path $PSScriptRoot 'java-path.txt'
    if (Test-Path -LiteralPath $local) { $java = (Get-Content -LiteralPath $local -Raw).Trim() }
    $versionStart = New-Object System.Diagnostics.ProcessStartInfo
    $versionStart.FileName = $java
    $versionStart.Arguments = '-version'
    $versionStart.UseShellExecute = $false
    $versionStart.CreateNoWindow = $true
    $versionStart.RedirectStandardError = $true
    $versionProcess = [Diagnostics.Process]::Start($versionStart)
    $javaVersion = $versionProcess.StandardError.ReadToEnd()
    $versionProcess.WaitForExit()
    $versionProcess.Dispose()
    if ($javaVersion -notmatch 'version "17\.') { throw 'Java 17 requis. Renseigne le chemin de java.exe dans java-path.txt ou JAVA_HOME.' }
    $argsFile = Join-Path $PSScriptRoot 'libraries\net\minecraftforge\forge\1.20.1-47.4.10\win_args.txt'
    if (!(Test-Path -LiteralPath $argsFile)) { throw 'Installation Forge absente.' }
    $worldName = "worlds/$($selected.id)"
    $world = Join-Path $PSScriptRoot $worldName
    if (!(Test-Path -LiteralPath (Join-Path $world 'level.dat'))) { throw 'Sauvegarde de map absente.' }
    if ($Check) { Write-Host "OK : $($selected.name), Java 17, Forge 47.4.10, $MemoryGB Go"; return }
    # Verrou exclusif conserve pendant toute la vie de Java, meme entre deux maps.
    $lock = [IO.File]::Open((Join-Path $PSScriptRoot 'server.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $dnsJob = $null
    try {
        $port = 25565
        $properties = Get-Content -LiteralPath 'server.properties'
        $portLine = $properties | Where-Object { $_ -match '^server-port=' } | Select-Object -First 1
        if ($portLine) { $port = [int]($portLine -replace '^server-port=','') }
        if ([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq $port }) { throw "Le port $port est deja utilise." }
        if (!(Test-Path -LiteralPath 'eula.txt') -or (Get-Content -LiteralPath 'eula.txt' -Raw) -notmatch '(?m)^eula=true\s*$') {
            Write-Host 'Conditions Minecraft : https://www.minecraft.net/eula'
            if ((Read-Host 'Pour accepter les conditions et lancer ton serveur, saisis OUI') -cne 'OUI') { return }
            Set-Content -LiteralPath 'eula.txt' -Value 'eula=true' -Encoding ASCII
        }
        ($properties -replace '^level-name=.*$', "level-name=$worldName") | Set-Content -LiteralPath 'server.properties' -Encoding ASCII
        Write-Host "Map : $($selected.name). Arreter proprement avec stop dans la console."
        $dnsHelper = Join-Path $PSScriptRoot 'Update-ServerAddress.ps1'
        if ((Test-Path -LiteralPath $dnsHelper) -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'dynamic-dns\config.json'))) {
            $null = & $dnsHelper -ServerRoot $PSScriptRoot
            $dnsJob = Start-Job -FilePath $dnsHelper -ArgumentList @($PSScriptRoot,$true)
        }
        & $java '-Xms2G' "-Xmx$($MemoryGB)G" "@$argsFile" 'nogui'
        if ($LASTEXITCODE -ne 0) { throw "Arret Java ($LASTEXITCODE). Voir logs/latest.log." }
    } finally {
        if ($dnsJob) { Stop-Job -Job $dnsJob; Remove-Job -Job $dnsJob }
        $lock.Dispose()
    }
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
