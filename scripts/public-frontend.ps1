$ErrorActionPreference = 'Stop'
$frontend = 'http://127.0.0.1:5173'
try {
    Invoke-WebRequest -Uri $frontend -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    throw 'Frontend is not reachable at http://127.0.0.1:5173. Start FinAI first (run_all.bat), then rerun this script.'
}

$ngrok = Get-Command ngrok -ErrorAction SilentlyContinue
if (-not $ngrok) {
    throw 'ngrok was not found. Install ngrok and sign in (ngrok config add-authtoken ...), then rerun.'
}

$existing = $null
try {
    $existing = Invoke-RestMethod -Uri 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2
} catch {
    $existing = $null
}
if ($existing -and $existing.tunnels) {
    $https = @($existing.tunnels | Where-Object { $_.public_url -like 'https://*' } | Select-Object -First 1)
    if ($https) {
        Write-Output $https.public_url
        exit 0
    }
}

Start-Process -FilePath $ngrok.Source -ArgumentList @('http', '127.0.0.1:5173') -WindowStyle Normal
$deadline = (Get-Date).AddSeconds(25)
do {
    Start-Sleep -Seconds 1
    try {
        $tunnels = Invoke-RestMethod -Uri 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2
        $https = @($tunnels.tunnels | Where-Object { $_.public_url -like 'https://*' } | Select-Object -First 1)
        if ($https) {
            Write-Output $https.public_url
            exit 0
        }
    } catch {
        # ngrok inspector not up yet
    }
} while ((Get-Date) -lt $deadline)

throw 'ngrok started but no public HTTPS URL was ready. Open http://127.0.0.1:4040 and check the tunnel, or run: ngrok config add-authtoken <token>'
