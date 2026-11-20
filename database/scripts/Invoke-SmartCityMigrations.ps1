[CmdletBinding()]
param(
    [ValidateSet('ValidateFiles', 'Baseline', 'Migrate', 'Verify')]
    [string]$Mode = 'Verify',
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\psql.exe'
)

$ErrorActionPreference = 'Stop'
$migrationRoot = Join-Path $PSScriptRoot '..\migrations'
$manifestPath = Join-Path $migrationRoot 'schema-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

foreach ($migration in $manifest.migrations) {
    $path = Join-Path $migrationRoot $migration.file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Migration file is missing: $($migration.file)"
    }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $migration.sha256) {
        throw "Migration checksum mismatch for $($migration.file): expected $($migration.sha256), got $actual"
    }
}

if ($Mode -eq 'ValidateFiles') {
    'Migration files and manifest: VALID'
    exit 0
}

$domainXmlPath = Join-Path $PayaraHome 'glassfish\domains\domain1\config\domain.xml'
[xml]$domain = Get-Content -LiteralPath $domainXmlPath -Raw
$resource = @($domain.SelectNodes("//*[local-name()='jdbc-resource']")) |
    Where-Object { $_.GetAttribute('jndi-name') -eq 'jdbc/smartcity360' } |
    Select-Object -First 1
if (-not $resource) { throw 'Payara resource jdbc/smartcity360 was not found.' }
$poolName = $resource.GetAttribute('pool-name')
$pool = @($domain.SelectNodes("//*[local-name()='jdbc-connection-pool']")) |
    Where-Object { $_.GetAttribute('name') -eq $poolName } |
    Select-Object -First 1
if (-not $pool) { throw "Payara pool $poolName was not found." }
$properties = @{}
foreach ($property in @($pool.SelectNodes("./*[local-name()='property']"))) {
    $properties[$property.GetAttribute('name')] = $property.GetAttribute('value')
}
$url = $properties.URL
if (-not $url) { $url = $properties.url }
if ($url -notmatch '^jdbc:postgresql://(?<host>[^:/]+):(?<port>\d+)/(?<database>[^?]+)') {
    throw 'The configured JDBC URL is not a supported PostgreSQL URL.'
}
$databaseHost = $Matches.host
$databasePort = $Matches.port
$databaseName = $Matches.database
$databaseUser = $properties.user
$databasePassword = $properties.password

function Invoke-Psql {
    param([Parameter(Mandatory)][string]$Sql, [switch]$TuplesOnly)
    $temporaryPath = [System.IO.Path]::GetTempFileName()
    $previousPassword = $env:PGPASSWORD
    try {
        [System.IO.File]::WriteAllText($temporaryPath, $Sql, [System.Text.UTF8Encoding]::new($false))
        $env:PGPASSWORD = $databasePassword
        $arguments = @('-X', '-v', 'ON_ERROR_STOP=1', '-h', $databaseHost, '-p', $databasePort,
            '-U', $databaseUser, '-d', $databaseName, '-f', $temporaryPath)
        if ($TuplesOnly) { $arguments = @('-A', '-t', '-F', '|') + $arguments }
        $output = & $PsqlPath @arguments 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
        return $output
    } finally {
        $env:PGPASSWORD = $previousPassword
        Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
    }
}

$applicationTables = @(
    'audit_logs','building_power_states','complaint_events','complaints','energy_meters',
    'energy_readings','energy_stream_runs','lighting_profiles','policies','policy_evaluations',
    'policy_versions','roles','streetlights','user_roles','users','zone_commands','zones'
)
$tableValues = ($applicationTables | ForEach-Object { "('$_')" }) -join ','

function Get-CatalogVerificationSql {
    param([switch]$RequireNoInvariantObjects)
    $objectCheck = if ($RequireNoInvariantObjects) {
        @"
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public') THEN
        RAISE EXCEPTION 'Unexpected function exists in public schema before baseline';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_schema='public') THEN
        RAISE EXCEPTION 'Unexpected trigger exists in public schema before baseline';
    END IF;
"@
    } else { '' }
    return @"
DO `$verify`$
DECLARE
    tables_hash text;
    columns_hash text;
    constraints_hash text;
    indexes_hash text;
    roles_hash text;
    unexpected_rows bigint;
BEGIN
    WITH expected(name) AS (VALUES $tableValues)
    SELECT md5(string_agg(c.relname, E'\n' ORDER BY c.relname)) INTO tables_hash
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    JOIN expected e ON e.name=c.relname
    WHERE n.nspname='public' AND c.relkind='r';
    IF tables_hash <> '$($manifest.catalogFingerprint.tablesMd5)' OR
       (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' AND c.relname <> 'smartcity360_schema_migrations') <> 17 THEN
        RAISE EXCEPTION 'Application table catalog fingerprint mismatch: %', tables_hash;
    END IF;

    WITH expected(name) AS (VALUES $tableValues), lines AS (
        SELECT c.relname||'|'||a.attnum::text||'|'||a.attname||'|'||format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull::text||'|'||COALESCE(pg_get_expr(ad.adbin,ad.adrelid),'') AS line
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN expected e ON e.name=c.relname
        JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
        LEFT JOIN pg_attrdef ad ON ad.adrelid=c.oid AND ad.adnum=a.attnum WHERE n.nspname='public')
    SELECT md5(string_agg(line,E'\n' ORDER BY line)) INTO columns_hash FROM lines;
    IF columns_hash <> '$($manifest.catalogFingerprint.columnsMd5)' THEN RAISE EXCEPTION 'Column/default fingerprint mismatch: %', columns_hash; END IF;

    WITH expected(name) AS (VALUES $tableValues), lines AS (
        SELECT c.relname||'|'||con.conname||'|'||con.contype::text||'|'||pg_get_constraintdef(con.oid,true) AS line
        FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace JOIN expected e ON e.name=c.relname WHERE n.nspname='public')
    SELECT md5(string_agg(line,E'\n' ORDER BY line)) INTO constraints_hash FROM lines;
    IF constraints_hash <> '$($manifest.catalogFingerprint.constraintsMd5)' THEN RAISE EXCEPTION 'Constraint fingerprint mismatch: %', constraints_hash; END IF;

    WITH expected(name) AS (VALUES $tableValues), lines AS (
        SELECT tablename||'|'||indexname||'|'||indexdef AS line FROM pg_indexes i JOIN expected e ON e.name=i.tablename WHERE schemaname='public')
    SELECT md5(string_agg(line,E'\n' ORDER BY line)) INTO indexes_hash FROM lines;
    IF indexes_hash <> '$($manifest.catalogFingerprint.indexesMd5)' THEN RAISE EXCEPTION 'Index fingerprint mismatch: %', indexes_hash; END IF;

    SELECT md5(string_agg(code||'|'||display_name,E'\n' ORDER BY code)) INTO roles_hash FROM public.roles;
    IF roles_hash <> '$($manifest.catalogFingerprint.rolesMd5)' OR (SELECT count(*) FROM public.roles) <> 5 THEN
        RAISE EXCEPTION 'Initial role seed mismatch: %', roles_hash;
    END IF;

    SELECT sum(row_count) INTO unexpected_rows FROM (
        SELECT count(*) AS row_count FROM public.audit_logs UNION ALL
        SELECT count(*) FROM public.building_power_states UNION ALL SELECT count(*) FROM public.complaint_events UNION ALL
        SELECT count(*) FROM public.complaints UNION ALL SELECT count(*) FROM public.energy_meters UNION ALL
        SELECT count(*) FROM public.energy_readings UNION ALL SELECT count(*) FROM public.energy_stream_runs UNION ALL
        SELECT count(*) FROM public.lighting_profiles UNION ALL SELECT count(*) FROM public.policies UNION ALL
        SELECT count(*) FROM public.policy_evaluations UNION ALL SELECT count(*) FROM public.policy_versions UNION ALL
        SELECT count(*) FROM public.streetlights UNION ALL SELECT count(*) FROM public.user_roles UNION ALL
        SELECT count(*) FROM public.users UNION ALL SELECT count(*) FROM public.zone_commands UNION ALL SELECT count(*) FROM public.zones
    ) counts;
    IF unexpected_rows <> 0 THEN RAISE EXCEPTION 'Unexpected pre-existing application data: % non-role rows', unexpected_rows; END IF;
$objectCheck
END
`$verify`$;
"@
}

function Get-LedgerInsertSql {
    param($Migration, [bool]$Baselined)
    $flag = if ($Baselined) { 'true' } else { 'false' }
    return "INSERT INTO public.smartcity360_schema_migrations(version,description,checksum,baselined,execution_ms) VALUES ($($Migration.version),'$($Migration.description)','$($Migration.sha256)',$flag,0);"
}

if ($Mode -eq 'Baseline') {
    $ledgerExists = (Invoke-Psql -TuplesOnly -Sql "SELECT to_regclass('public.smartcity360_schema_migrations') IS NOT NULL;").Trim()
    if ($ledgerExists -ne 'f') { throw 'A migration ledger already exists; baseline registration is not allowed.' }
    $v1 = Get-Content -LiteralPath (Join-Path $migrationRoot $manifest.migrations[0].file) -Raw
    $v4 = Get-Content -LiteralPath (Join-Path $migrationRoot $manifest.migrations[3].file) -Raw
    $locks = ($applicationTables | ForEach-Object { "public.$_" }) -join ', '
    $sql = @"
BEGIN;
SELECT pg_advisory_lock(hashtextextended('smartcity360.migrations',0));
LOCK TABLE $locks IN ACCESS EXCLUSIVE MODE;
$(Get-CatalogVerificationSql -RequireNoInvariantObjects)
$v1
$(Get-LedgerInsertSql $manifest.migrations[0] $true)
$(Get-LedgerInsertSql $manifest.migrations[1] $true)
$(Get-LedgerInsertSql $manifest.migrations[2] $true)
$v4
$(Get-LedgerInsertSql $manifest.migrations[3] $false)
COMMIT;
"@
    Invoke-Psql -Sql $sql
}
elseif ($Mode -eq 'Migrate') {
    $ledgerExists = ((Invoke-Psql -TuplesOnly -Sql "SELECT to_regclass('public.smartcity360_schema_migrations') IS NOT NULL;") -join '').Trim()
    $parts = @('BEGIN;', "SELECT pg_advisory_xact_lock(hashtextextended('smartcity360.migrations',0));")
    if ($ledgerExists -eq 'f') {
        $existing = ((Invoke-Psql -TuplesOnly -Sql "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r';") -join '').Trim()
        if ([int]$existing -ne 0) { throw 'An application schema exists without a ledger; use Baseline after reconciliation.' }
        foreach ($migration in $manifest.migrations) {
            $parts += Get-Content -LiteralPath (Join-Path $migrationRoot $migration.file) -Raw
            $parts += Get-LedgerInsertSql $migration $false
        }
    } else {
        $installedRows = @(Invoke-Psql -TuplesOnly -Sql "SELECT version,checksum FROM public.smartcity360_schema_migrations ORDER BY version;") |
            Where-Object { $_ -match '^\d+\|' }
        $installed = @{}
        foreach ($row in $installedRows) {
            $fields = $row -split '\|', 2
            $installed[[int]$fields[0]] = $fields[1]
        }
        foreach ($version in $installed.Keys) {
            $known = $manifest.migrations | Where-Object { $_.version -eq $version } | Select-Object -First 1
            if (-not $known) { throw "Database contains migration version $version which is unknown to this project." }
            if ($installed[$version] -cne $known.sha256) { throw "Installed checksum differs for migration version $version." }
        }
        $pending = @($manifest.migrations | Where-Object { -not $installed.ContainsKey([int]$_.version) } | Sort-Object version)
        foreach ($migration in $pending) {
            $priorMissing = @($manifest.migrations | Where-Object { $_.version -lt $migration.version -and -not $installed.ContainsKey([int]$_.version) })
            if ($priorMissing.Count -ne 0) { throw "Migration history has a gap before version $($migration.version)." }
            $parts += Get-Content -LiteralPath (Join-Path $migrationRoot $migration.file) -Raw
            $parts += Get-LedgerInsertSql $migration $false
            $installed[[int]$migration.version] = $migration.sha256
        }
        if ($pending.Count -eq 0) { 'No pending migrations.' }
    }
    $parts += 'COMMIT;'
    Invoke-Psql -Sql ($parts -join [Environment]::NewLine)
}

if ($Mode -in 'Baseline','Migrate','Verify') {
    $verificationSql = @"
SELECT pg_advisory_lock(hashtextextended('smartcity360.migrations',0));
$(Get-CatalogVerificationSql)
SELECT version,description,checksum,baselined FROM public.smartcity360_schema_migrations ORDER BY version;
SELECT pg_advisory_unlock(hashtextextended('smartcity360.migrations',0));
"@
    $output = Invoke-Psql -TuplesOnly -Sql $verificationSql
    $ledgerRows = @($output | Where-Object { $_ -match '^\d+\|' })
    if ($ledgerRows.Count -ne $manifest.migrations.Count) { throw "Ledger contains $($ledgerRows.Count) entries; expected $($manifest.migrations.Count)." }
    foreach ($migration in $manifest.migrations) {
        $matching = $ledgerRows | Where-Object { $_ -like "$($migration.version)|*|$($migration.sha256)|*" }
        if (-not $matching) { throw "Ledger checksum/description mismatch for version $($migration.version)." }
    }
    $output
    'SmartCity360 migration verification: PASS'
}
