$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot '..\scripts\Invoke-SmartCityMigrations.ps1'
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw 'Migration runner is missing.'
}
$result = & $runner -Mode ValidateFiles
if ($LASTEXITCODE -ne 0) { throw 'Migration runner file validation failed.' }
if ($result -notcontains 'Migration files and manifest: VALID') {
    throw "Unexpected validation result: $($result -join [Environment]::NewLine)"
}
$runnerSource = Get-Content -LiteralPath $runner -Raw
if ($runnerSource -match 'pg_advisory_xact_unlock') {
    throw 'Transaction advisory locks release automatically; the runner must not call pg_advisory_xact_unlock.'
}
'Migration runner contract: PASS'
