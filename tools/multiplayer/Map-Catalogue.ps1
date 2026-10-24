# Same catalogues and approval rule as client/MapCommunityClient.java and MapCatalogue.java.
function Get-ZombieRoolMaps {
    param([string]$ServerRoot, [switch]$Offline)
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $cache = Join-Path $ServerRoot 'map-catalogue'
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $local = Get-Content -LiteralPath (Join-Path $ServerRoot 'maps-local.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $nacht = $local | Where-Object { $_.id -eq 'nacht-der-untoten' } | Select-Object -First 1
    if (!$nacht) { throw 'La reference locale de Nacht der Untoten est absente.' }
    $entries = New-Object System.Collections.Generic.List[object]
    $entries.Add([pscustomobject]@{ id=$nacht.id; name=$nacht.name; source='locale'; version='instance no mods'; downloadUrl=''; sha256=''; mods=@(); gunpacks=@(); description='Version locale demandee de ZombieRool (no mods).'; zrVersion='26.7'; localOverride=$true })
    foreach ($source in @('official','community')) {
        $repo = if ($source -eq 'official') { 'zombierool-maps' } else { 'zombierool-community-hub' }
        $file = Join-Path $cache "$source.json"
        if (!$Offline) {
            try {
                $url = "https://raw.githubusercontent.com/Cryo60/$repo/main/maps.json"
                $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 20 -Headers @{ 'User-Agent'='ZombieRool-Server-MapCatalogue' }
                $catalogue = $response.Content | ConvertFrom-Json
                if (!$catalogue.maps) { throw 'Catalogue sans maps.' }
                [IO.File]::WriteAllText($file, $response.Content, (New-Object Text.UTF8Encoding($false)))
            } catch { Write-Host "Catalogue $source inaccessible : utilisation du cache local." -ForegroundColor Yellow }
        }
        if (!(Test-Path -LiteralPath $file)) { continue }
        $catalogue = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
        $seen = @{}
        foreach ($map in $catalogue.maps) {
            if ($map.status -and $map.status -ne 'approved') { continue }
            if ($map.game_version -ne '1.20.1') { continue }
            if ($map.id -notmatch '^[a-zA-Z0-9_.-]{1,160}$' -or $seen.ContainsKey($map.id)) { continue }
            $seen[$map.id] = $true
            if ($source -eq 'official' -and $map.id -eq 'zr_nacht') { continue }
            $entries.Add([pscustomobject]@{ id="$source-$($map.id)"; name=$map.name.Trim(); source=$source; version=$map.version; downloadUrl=$map.download_url; sha256=$map.sha256; mods=@($map.mods); gunpacks=@($map.tacz_gunpacks)+@($map.gunpacks); description=$map.description; zrVersion=$map.zr_version; localOverride=$false })
        }
    }
    $snapshot = ConvertTo-Json -InputObject ($entries.ToArray()) -Depth 8
    [IO.File]::WriteAllText((Join-Path $ServerRoot 'maps.json'), $snapshot, (New-Object Text.UTF8Encoding($false)))
    return $entries.ToArray()
}

function Install-ZombieRoolMap {
    param([string]$ServerRoot, [object]$Map)
    if ($Map.id -notmatch '^[a-zA-Z0-9_.-]{1,180}$') { throw 'Identifiant de map invalide.' }
    $world = Join-Path $ServerRoot "worlds/$($Map.id)"
    if (Test-Path -LiteralPath (Join-Path $world 'level.dat')) { return }
    if ($Map.localOverride) { throw 'La copie locale de Nacht est absente. Elle ne sera pas remplacee par GitHub.' }
    if (Test-Path -LiteralPath $world) { throw 'Dossier de map incomplet deja present : deplace-le avant de reessayer.' }
    if ($Map.downloadUrl -notmatch '^https://github\.com/Cryo60/(zombierool-maps|zombierool-community-hub)/releases/download/') { throw 'URL de map non reconnue.' }
    if ($Map.sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Empreinte SHA-256 absente ou invalide dans le catalogue.' }
    Write-Host "Telechargement de $($Map.name) ($($Map.version))..." -ForegroundColor Cyan
    Write-Host "Version ZombieRool indiquee par l auteur : $($Map.zrVersion)"
    if ($Map.description) { Write-Host $Map.description }
    foreach ($requirement in @($Map.mods)+@($Map.gunpacks)) { if ($requirement) { Write-Host "Dependance indiquee par l auteur : $requirement" -ForegroundColor Yellow } }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $cache = Join-Path $ServerRoot 'map-downloads'
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $lock = [IO.File]::Open((Join-Path $cache "$($Map.id).lock"), 'OpenOrCreate', 'ReadWrite', 'None')
    $stage = Join-Path $cache ([guid]::NewGuid().ToString('N'))
    try {
        if (Test-Path -LiteralPath (Join-Path $world 'level.dat')) { return }
        $archive = Join-Path $cache "$($Map.id)-$($Map.sha256).zip"
        if (!(Test-Path -LiteralPath $archive)) {
            Invoke-WebRequest -UseBasicParsing -Uri $Map.downloadUrl -OutFile "$archive.partial" -TimeoutSec 300
            Move-Item -LiteralPath "$archive.partial" -Destination $archive -Force
        }
        $stream = [IO.File]::OpenRead($archive)
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { $actual = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
        finally { $stream.Dispose(); $algorithm.Dispose() }
        if ($actual -ne $Map.sha256) { Remove-Item -LiteralPath $archive; throw 'La verification SHA-256 de la map a echoue. Aucun monde installe.' }
        New-Item -ItemType Directory -Path $stage | Out-Null
        $zip = [IO.Compression.ZipFile]::OpenRead($archive)
        try {
            $root = [IO.Path]::GetFullPath($stage) + [IO.Path]::DirectorySeparatorChar
            [long]$bytes = 0
            if ($zip.Entries.Count -gt 100000) { throw 'Trop de fichiers dans la map.' }
            foreach ($entry in $zip.Entries) {
                $path = [IO.Path]::GetFullPath((Join-Path $stage $entry.FullName))
                if (!$path.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Chemin sortant du dossier dans l archive.' }
                if ((($entry.ExternalAttributes -shr 16) -band 61440) -eq 40960) { throw 'Lien symbolique interdit dans la map.' }
                $bytes += $entry.Length
                if ($bytes -gt 4GB) { throw 'Map decompressee trop volumineuse.' }
            }
        } finally { $zip.Dispose() }
        [IO.Compression.ZipFile]::ExtractToDirectory($archive, $stage)
        $levels = @(Get-ChildItem -LiteralPath $stage -Recurse -File -Filter 'level.dat')
        if ($levels.Count -ne 1) { throw 'L archive doit contenir exactement un monde Minecraft (level.dat).' }
        $source = $levels[0].Directory.FullName
        # Never overwrite an installed world or its progress.
        Move-Item -LiteralPath $source -Destination $world
        $Map | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $world 'server-map-source.json') -Encoding UTF8
        Write-Host "Map installee : $($Map.name)" -ForegroundColor Green
    } finally {
        $lock.Dispose()
        # Keep any staging leftovers for diagnosis; do not delete recursively.
    }
}
