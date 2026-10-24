param([string]$InstancePath, [switch]$Offline, [switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Check-File($Path, $Hash) {
    if (!(Test-Path -LiteralPath $Path)) { throw "Fichier absent : $Path" }
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { $actual = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
    finally { $stream.Dispose(); $algorithm.Dispose() }
    if ($actual -ne $Hash) { throw "Fichier corrompu : $Path" }
}
try {
    $manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if (!$InstancePath) {
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = 'Selectionne le dossier de ton instance CurseForge (Minecraft 1.20.1 / Forge 47.4.10). Dans CurseForge : clic droit sur instance > Ouvrir le dossier.'
        $default = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'
        if (Test-Path -LiteralPath $default) { $dialog.SelectedPath = $default }
        if ($dialog.ShowDialog() -ne 'OK') { return }
        $InstancePath = $dialog.SelectedPath
    }
    $InstancePath = (Resolve-Path -LiteralPath $InstancePath).Path
    $metadataPath = Join-Path $InstancePath 'minecraftinstance.json'
    if (!(Test-Path -LiteralPath $metadataPath)) { throw 'Selectionne une instance CurseForge existante, pas le dossier Instances. Cree-la avec Minecraft 1.20.1 et Forge 47.4.10.' }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.gameVersion -ne '1.20.1' -or $metadata.baseModLoader.name -ne 'forge-47.4.10') { throw 'Instance incompatible : Minecraft 1.20.1 et Forge 47.4.10 sont requis. Configure ces versions dans CurseForge puis relance.' }
    Check-File (Join-Path $PSScriptRoot 'client-payload.zip') $manifest.payloadSha256
    if ($ValidateOnly) { Write-Host 'Instance et archive valides. Aucun fichier modifie.'; return }
    if (Get-CimInstance Win32_Process -Filter "name = 'javaw.exe' OR name = 'java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($InstancePath) }) { throw 'Ferme Minecraft pour cette instance avant installation.' }
    $stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,6)
    $stage = Join-Path $InstancePath ".zombierool-stage-$stamp"
    $backup = Join-Path $InstancePath ".zombierool-backup-$stamp"
    New-Item -ItemType Directory -Path $stage,$backup | Out-Null
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $PSScriptRoot 'client-payload.zip'), $stage)
    foreach ($file in $manifest.files) {
        $target = Join-Path $stage $file.path
        if (!$Offline -and $file.url) {
            Write-Host "Telechargement : $($file.path)"
            try {
                Invoke-WebRequest -UseBasicParsing -Uri $file.url -OutFile "$target.download" -TimeoutSec 60
                Check-File "$target.download" $file.sha256
                Move-Item -LiteralPath "$target.download" -Destination $target -Force
            } catch { Write-Host 'Source indisponible ou version differente : utilisation de la copie incluse verifiee.' }
            if (Test-Path -LiteralPath "$target.download") { Remove-Item -LiteralPath "$target.download" }
        }
        Check-File $target $file.sha256
    }
    $changed = New-Object System.Collections.Generic.List[string]
    $moved = New-Object System.Collections.Generic.List[string]
    try {
        # Remplacer entierement les mods et gunpacks evite les anciens doublons.
        # Config et ressources sont fusionnees pour conserver les fichiers personnels.
        foreach ($folder in @('config','defaultconfigs','resourcepacks','shaderpacks')) {
            $old = Join-Path $InstancePath $folder
            $new = Join-Path $stage $folder
            if ((Test-Path -LiteralPath $old) -and (Test-Path -LiteralPath $new)) {
                Get-ChildItem -LiteralPath $old -File -Recurse | ForEach-Object {
                    $relative = $_.FullName.Substring($old.Length + 1)
                    $dest = Join-Path $new $relative
                    if (!(Test-Path -LiteralPath $dest)) {
                        New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
                        Copy-Item -LiteralPath $_.FullName -Destination $dest
                    }
                }
            }
        }
        foreach ($folder in $manifest.folders) {
            $old = Join-Path $InstancePath $folder
            if (Test-Path -LiteralPath $old) { Move-Item -LiteralPath $old -Destination (Join-Path $backup $folder); $moved.Add($folder) }
            Move-Item -LiteralPath (Join-Path $stage $folder) -Destination $old
            $changed.Add($folder)
        }
    } catch {
        foreach ($folder in $changed) { Move-Item -LiteralPath (Join-Path $InstancePath $folder) -Destination (Join-Path $stage $folder) }
        foreach ($folder in $moved) { Move-Item -LiteralPath (Join-Path $backup $folder) -Destination (Join-Path $InstancePath $folder) }
        throw
    }
    Write-Host "Installation terminee ! Lance cette instance dans CurseForge. Sauvegarde : $backup" -ForegroundColor Green
    Write-Host 'Multijoueur > Ajouter un serveur > adresse fournie par ton ami.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
