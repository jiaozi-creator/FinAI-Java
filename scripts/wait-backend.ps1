$ErrorActionPreference = 'SilentlyContinue'
$uri = 'http://127.0.0.1:8080/actuator/health'
1..45 | ForEach-Object {
    try {
        $response = Invoke-RestMethod -Uri $uri -TimeoutSec 1
        if ($response.status -eq 'UP') { exit 0 }
    } catch { }
    Start-Sleep -Seconds 1
}
Write-Host 'Backend health check timed out.'
Write-Host 'Check backend/logs/finai.log for the startup error.'
exit 1
