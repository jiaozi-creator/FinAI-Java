param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Require-Tool([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing tool: $Name. $Hint Restart the terminal after installation."
    }
}

Require-Tool 'java' 'Install JDK 17 or newer and add its bin directory to PATH.'
Require-Tool 'javac' 'A full JDK is required, not just a JRE.'
Require-Tool 'mvn.cmd' 'Install Apache Maven 3.6 or newer and add its bin directory to PATH.'
Require-Tool 'node' 'Install Node.js 18 or newer (a supported LTS is recommended).'
Require-Tool 'npm.cmd' 'Install npm with Node.js.'

$jdkVersion = (& javac -version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $jdkVersion -notmatch 'javac (\d+)') {
    throw "Unable to read JDK version: $jdkVersion"
}
if ([int]$Matches[1] -lt 17) { throw "JDK 17 or newer required: $jdkVersion" }
$nodeVersion = (& node --version | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v(\d+)') {
    throw "Unable to read Node.js version: $nodeVersion"
}
if ([int]$Matches[1] -lt 18) { throw "Node.js 18 or newer required: $nodeVersion" }

Write-Host "Tools available: $jdkVersion; Node.js $nodeVersion."
if ($CheckOnly) { return }

Push-Location (Join-Path $projectRoot 'backend')
try {
    & mvn.cmd -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven dependency installation/build failed.' }
} finally { Pop-Location }

Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd ci --include=dev
    if ($LASTEXITCODE -ne 0) { throw 'npm dependency installation failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

Write-Host 'Dependencies installed and application packaged. Tests were not run by this installer.'
