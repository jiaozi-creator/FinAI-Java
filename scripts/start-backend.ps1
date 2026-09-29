$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env file'
}
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    $value = $line.Trim()
    if ($value.Length -eq 0 -or $value.StartsWith('#')) { continue }
    if ($value -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        throw 'Invalid .env entry (expected KEY=value)'
    }
    $key = $Matches[1]
    $setting = $Matches[2].Trim()
    if ($setting.Length -ge 2 -and $setting[0] -eq $setting[-1] -and $setting[0] -in @('"', "'")) {
        $setting = $setting.Substring(1, $setting.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($key, $setting, 'Process')
}
Set-Location (Join-Path $root 'backend')
& java -jar 'target/finai-backend-1.0.0.jar'
exit $LASTEXITCODE
