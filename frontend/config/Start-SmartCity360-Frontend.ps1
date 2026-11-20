
# SmartCity360 - Frontend Startup

$ErrorActionPreference = 'Stop'
$frontend = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageJson = Join-Path $frontend 'package.json'

if (-not (Test-Path -LiteralPath $frontend -PathType Container)) {
    throw "Frontend directory was not found: $frontend"
}
if (-not (Test-Path -LiteralPath $packageJson -PathType Leaf)) {
    throw "Frontend package.json was not found: $packageJson"
}
if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    throw 'LOCALAPPDATA is not set; cannot resolve the local TLS directory.'
}

$tlsDir = Join-Path $env:LOCALAPPDATA 'SmartCity360\tls'
$tlsFiles = @{
    SMARTCITY360_VITE_CERT_FILE = Join-Path $tlsDir 'vite-localhost.pem'
    SMARTCITY360_VITE_KEY_FILE = Join-Path $tlsDir 'vite-localhost-key.pem'
    SMARTCITY360_PAYARA_CA_FILE = Join-Path $tlsDir 'payara-ca.pem'
}

foreach ($name in $tlsFiles.Keys) {
    $path = $tlsFiles[$name]
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required TLS file for $name was not found: $path"
    }
}

$npmCommand = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue
if (-not $npmCommand) {
    throw 'npm.cmd was not found on PATH. Install Node.js 24 or later with npm.'
}

$previousTlsValues = @{}
foreach ($name in $tlsFiles.Keys) {
    $previousTlsValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

Push-Location $frontend
try {
    foreach ($name in $tlsFiles.Keys) {
        [Environment]::SetEnvironmentVariable($name, $tlsFiles[$name], 'Process')
    }

    & $npmCommand.Definition run verify:tls
    if ($LASTEXITCODE -ne 0) {
        throw 'TLS verification failed. Vite was not started.'
    }

    & $npmCommand.Definition run dev
}
finally {
    Pop-Location
    foreach ($name in $tlsFiles.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousTlsValues[$name], 'Process')
    }
}
