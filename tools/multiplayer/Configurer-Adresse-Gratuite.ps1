param([string]$Domain='zombierool')
$ErrorActionPreference='Stop'
try {
    $Domain=$Domain.ToLowerInvariant().Trim() -replace '\.duckdns\.org$',''
    if ($Domain -notmatch '^[a-z0-9-]{1,63}$') { throw 'Nom de domaine invalide.' }
    Write-Host "Reserve d abord $Domain.duckdns.org sur https://www.duckdns.org/ avec ton propre compte."
    Write-Host 'Copie ensuite le token affiche par DuckDNS. Il sera stocke chiffre pour ton compte Windows.'
    $token=Read-Host 'Colle le token DuckDNS (saisie masquee)' -AsSecureString
    if ($token.Length -lt 10) { throw 'Token vide ou trop court.' }
    $folder=Join-Path $PSScriptRoot 'dynamic-dns'
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $token | Export-Clixml -LiteralPath (Join-Path $folder 'token.xml')
    @{domain=$Domain} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'config.json') -Encoding ASCII
    if (!(& (Join-Path $PSScriptRoot 'Update-ServerAddress.ps1') -ServerRoot $PSScriptRoot)) { throw 'DuckDNS n a pas confirme la mise a jour. Verifie que le nom est reserve sur ton compte et que le token est correct.' }
    Write-Host 'La mise a jour sera tentee au lancement du serveur puis toutes les 5 minutes pendant son fonctionnement.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
