#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
try {
    $java = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'java-path.txt') -Raw).Trim()
    if (!(Test-Path -LiteralPath $java)) { throw 'Chemin Java invalide dans java-path.txt.' }
    $ruleName = 'ZombieRool-Minecraft-TCP-25565'
    if (!(Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name $ruleName -DisplayName 'ZombieRool Minecraft TCP 25565' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 25565 -Program $java -Profile Any | Out-Null
    }
    Write-Host 'Connexion entrante autorisee pour ce serveur Java sur TCP 25565.' -ForegroundColor Green
    Write-Host 'Sur la box : rediriger TCP 25565 vers l IPv4 locale de ce PC.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
