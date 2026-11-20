$ErrorActionPreference = 'Stop'

$migrationRoot = Join-Path $PSScriptRoot '..\migrations'
$expected = @(
    'V001__create_migration_ledger.sql',
    'V002__create_application_schema.sql',
    'V003__seed_initial_roles.sql',
    'V004__enforce_zone_invariants.sql',
    'V005__add_energy_meter_credential_references.sql'
)

foreach ($name in $expected) {
    $path = Join-Path $migrationRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing migration: $name"
    }
    $sql = Get-Content -LiteralPath $path -Raw
    if ([string]::IsNullOrWhiteSpace($sql)) {
        throw "Empty migration: $name"
    }
}

$manifest = Get-Content -LiteralPath (Join-Path $migrationRoot 'schema-manifest.json') -Raw | ConvertFrom-Json
$manifestFiles = @($manifest.migrations | ForEach-Object file)
if (Compare-Object $expected $manifestFiles) {
    throw "Migration manifest file set differs: $($manifestFiles -join ', ')"
}

$credentialSql = Get-Content -LiteralPath (Join-Path $migrationRoot 'V005__add_energy_meter_credential_references.sql') -Raw
foreach ($required in 'credential_key_id', 'credential_key_version', 'ux_energy_meters_credential_key_id') {
    if ($credentialSql -notmatch [regex]::Escape($required)) {
        throw "V005 credential migration is missing: $required"
    }
}
if ($credentialSql -notmatch '(?is)credential_key_id\s+IS\s+NULL.*credential_key_version\s+IS\s+NULL.*OR.*credential_key_id\s+IS\s+NOT\s+NULL.*credential_key_version\s+IS\s+NOT\s+NULL') {
    throw 'V005 must enforce paired credential reference presence.'
}
if ($credentialSql -notmatch '(?is)credential_key_version\s+IS\s+NULL.*credential_key_version\s*>\s*0') {
    throw 'V005 must enforce a positive credential version when present.'
}

$schemaSql = Get-Content -LiteralPath (Join-Path $migrationRoot 'V002__create_application_schema.sql') -Raw
if ($schemaSql -match '(?i)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS') {
    throw 'Canonical schema must fail on pre-existing tables; CREATE TABLE IF NOT EXISTS is forbidden.'
}

$tableMatches = [regex]::Matches($schemaSql, '(?im)^CREATE TABLE public\.([a-z_]+)\s*\(')
$actualTables = @($tableMatches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$expectedTables = @(
    'audit_logs','building_power_states','complaint_events','complaints',
    'energy_meters','energy_readings','energy_stream_runs','lighting_profiles',
    'policies','policy_evaluations','policy_versions','roles','streetlights',
    'user_roles','users','zone_commands','zones'
) | Sort-Object
if (Compare-Object $expectedTables $actualTables) {
    throw "Canonical schema table set differs: $($actualTables -join ', ')"
}

$seedSql = Get-Content -LiteralPath (Join-Path $migrationRoot 'V003__seed_initial_roles.sql') -Raw
foreach ($role in 'ADMIN','CITY_MANAGER','LOCAL_STAFF','RESIDENT','SUPERVISOR') {
    if ($seedSql -notmatch "'$role'") { throw "Role seed missing: $role" }
}
if ($seedSql -match '(?i)ON\s+CONFLICT') {
    throw 'Versioned seed migration must not silently accept conflicting rows.'
}

'Migration file contract: PASS'
