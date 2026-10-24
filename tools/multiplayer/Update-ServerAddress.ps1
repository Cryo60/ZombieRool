param([string]$ServerRoot=$PSScriptRoot, [bool]$Watch=$false, [int]$ServerProcessId=0)
$ErrorActionPreference='Stop'
function Update-ServerAddress {
    $configFile=Join-Path $ServerRoot 'dynamic-dns\config.json'
    if (!(Test-Path -LiteralPath $configFile)) { return }
    $plainToken=$null
    try {
        [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
        $config=Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
        if ($config.domain -notmatch '^[a-z0-9-]{1,63}$') { throw 'Invalid domain' }
        $secureToken=Import-Clixml -LiteralPath (Join-Path $ServerRoot 'dynamic-dns\token.xml')
        $credential=New-Object Management.Automation.PSCredential('duckdns',$secureToken)
        $plainToken=$credential.GetNetworkCredential().Password
        $url='https://www.duckdns.org/update?domains='+[Uri]::EscapeDataString($config.domain)+'&token='+[Uri]::EscapeDataString($plainToken)+'&ip='
        $response=Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 20
        $content=if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
        $result=$content.Trim()
        if ($result -ne 'OK') { throw 'DNS update rejected' }
        Write-Host "Adresse du serveur : $($config.domain).duckdns.org" -ForegroundColor Green
    } catch {
        # Do not expose a request URL or the account token in an exception/log.
        Write-Host 'Mise a jour DuckDNS impossible. Verifie ta connexion et la configuration de l adresse.' -ForegroundColor Yellow
        return $false
    } finally { $plainToken=$null; $credential=$null; $url=$null }
    return $true
}
if ($Watch) { while ($true) { Start-Sleep -Seconds 300; if ($ServerProcessId -and !(Get-Process -Id $ServerProcessId -ErrorAction SilentlyContinue)) { break }; $null=Update-ServerAddress } }
else { Update-ServerAddress }
