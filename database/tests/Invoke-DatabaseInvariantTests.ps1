[CmdletBinding()]
param(
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\psql.exe'
)

$ErrorActionPreference = 'Stop'
[xml]$domain = Get-Content -LiteralPath (Join-Path $PayaraHome 'glassfish\domains\domain1\config\domain.xml') -Raw
$resource = @($domain.SelectNodes("//*[local-name()='jdbc-resource']")) | Where-Object { $_.GetAttribute('jndi-name') -eq 'jdbc/smartcity360' } | Select-Object -First 1
$poolName = $resource.GetAttribute('pool-name')
$pool = @($domain.SelectNodes("//*[local-name()='jdbc-connection-pool']")) | Where-Object { $_.GetAttribute('name') -eq $poolName } | Select-Object -First 1
$properties = @{}
foreach ($property in @($pool.SelectNodes("./*[local-name()='property']"))) { $properties[$property.GetAttribute('name')] = $property.GetAttribute('value') }
if ($properties.URL -notmatch '^jdbc:postgresql://(?<host>[^:/]+):(?<port>\d+)/(?<database>[^?]+)') { throw 'Unsupported JDBC URL.' }
$connection = @{ Host=$Matches.host; Port=$Matches.port; Database=$Matches.database; User=$properties.user; Password=$properties.password }

function Invoke-TestSql([string]$Sql) {
    $old = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $connection.Password
        $output = & $PsqlPath -X -v ON_ERROR_STOP=1 -h $connection.Host -p $connection.Port -U $connection.User -d $connection.Database -c $Sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
        $output
    } finally { $env:PGPASSWORD = $old }
}

$behaviorSql = @'
BEGIN;
INSERT INTO public.zones(id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000001','CITY','T-CITY','Test City');
INSERT INTO public.zones(id,parent_zone_id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','DISTRICT','T-DISTRICT','Test District');
INSERT INTO public.zones(id,parent_zone_id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000002','STREET','T-STREET','Test Street');
INSERT INTO public.zones(id,parent_zone_id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003','BUILDING','T-BUILDING','Test Building');

DO $test$
BEGIN
    BEGIN
        INSERT INTO public.zones(id,parent_zone_id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000002','DISTRICT','T-BAD-PARENT','Bad Parent');
        RAISE EXCEPTION 'Expected invalid parent type to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.zones(id,parent_zone_id,zone_type,code,name) VALUES ('10000000-0000-0000-0000-000000000011','10000000-0000-0000-0000-000000000004','STREET','T-BUILDING-CHILD','Building Child');
        RAISE EXCEPTION 'Expected building child to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.zones SET parent_zone_id='10000000-0000-0000-0000-000000000004', zone_type='DISTRICT' WHERE id='10000000-0000-0000-0000-000000000001';
        RAISE EXCEPTION 'Expected ancestor-cycle operation to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.energy_meters(id,building_zone_id,meter_code) VALUES ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000003','T-BAD-METER');
        RAISE EXCEPTION 'Expected non-building meter zone to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
END
$test$;

INSERT INTO public.energy_meters(id,building_zone_id,meter_code) VALUES ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000004','T-METER');
UPDATE public.energy_meters SET credential_key_id='meter.hmac.test.v1', credential_key_version=1 WHERE id='20000000-0000-0000-0000-000000000002';
INSERT INTO public.users(id,full_name,email,password_hash) VALUES ('60000000-0000-0000-0000-000000000001','Constraint Test','Constraint@Test.invalid','not-a-real-password');
INSERT INTO public.lighting_profiles(id,profile_code,manufacturer,model,wattage,default_brightness_pct,controller_type)
VALUES ('30000000-0000-0000-0000-000000000001','T-PROFILE','Test','Test',10.00,50,'TEST');
INSERT INTO public.streetlights(id,asset_tag,profile_id,street_zone_id)
VALUES ('40000000-0000-0000-0000-000000000001','T-LIGHT','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000003');

DO $test$
BEGIN
    BEGIN
        INSERT INTO public.energy_meters(id,building_zone_id,meter_code,credential_key_id)
        VALUES ('20000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000004','T-BAD-CREDENTIAL-PAIR','meter.hmac.bad.v1');
        RAISE EXCEPTION 'Expected unpaired credential reference to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.energy_meters(id,building_zone_id,meter_code,credential_key_id,credential_key_version)
        VALUES ('20000000-0000-0000-0000-000000000011','10000000-0000-0000-0000-000000000004','T-BAD-CREDENTIAL-VERSION','meter.hmac.bad.v0',0);
        RAISE EXCEPTION 'Expected nonpositive credential version to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.energy_meters(id,building_zone_id,meter_code,credential_key_id,credential_key_version)
        VALUES ('20000000-0000-0000-0000-000000000012','10000000-0000-0000-0000-000000000004','T-DUP-CREDENTIAL','meter.hmac.test.v1',1);
        RAISE EXCEPTION 'Expected duplicate credential key ID to fail';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.streetlights(id,asset_tag,profile_id,street_zone_id)
        VALUES ('40000000-0000-0000-0000-000000000002','T-BAD-LIGHT','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004');
        RAISE EXCEPTION 'Expected non-street streetlight zone to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE public.zones SET zone_type='STREET' WHERE id='10000000-0000-0000-0000-000000000004';
        RAISE EXCEPTION 'Expected meter-referenced BUILDING type change to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.users(id,full_name,email,password_hash) VALUES ('60000000-0000-0000-0000-000000000002','Duplicate Email','constraint@test.invalid','not-a-real-password');
        RAISE EXCEPTION 'Expected case-insensitive duplicate email to fail';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.energy_readings(id,meter_id,period_start_at,period_end_at,consumption_kwh)
        VALUES ('70000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002',now(),now()+interval '1 hour',-1);
        RAISE EXCEPTION 'Expected negative energy consumption to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.energy_readings(id,meter_id,period_start_at,period_end_at,consumption_kwh)
        VALUES ('70000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002',now(),now(),1);
        RAISE EXCEPTION 'Expected invalid reading interval to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.lighting_profiles(id,profile_code,manufacturer,model,wattage,default_brightness_pct,controller_type)
        VALUES ('30000000-0000-0000-0000-000000000002','T-BAD-BRIGHTNESS','Test','Test',10.00,101,'TEST');
        RAISE EXCEPTION 'Expected invalid brightness to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        INSERT INTO public.streetlights(id,asset_tag,profile_id,street_zone_id,latitude,longitude)
        VALUES ('40000000-0000-0000-0000-000000000003','T-BAD-COORD','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000003',91,20);
        RAISE EXCEPTION 'Expected invalid latitude to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
END
$test$;

INSERT INTO public.policies(id,policy_code,name,domain,created_by)
VALUES ('80000000-0000-0000-0000-000000000001','T-POLICY','Test Policy','GENERAL','60000000-0000-0000-0000-000000000001');
INSERT INTO public.policy_versions(id,policy_id,version_no,dsl_expression,is_valid,is_active,created_by)
VALUES ('81000000-0000-0000-0000-000000000001','80000000-0000-0000-0000-000000000001',1,'true',true,true,'60000000-0000-0000-0000-000000000001');
DO $test$
BEGIN
    BEGIN
        INSERT INTO public.policy_versions(id,policy_id,version_no,dsl_expression,is_valid,is_active,created_by)
        VALUES ('81000000-0000-0000-0000-000000000002','80000000-0000-0000-0000-000000000001',2,'true',true,true,'60000000-0000-0000-0000-000000000001');
        RAISE EXCEPTION 'Expected second active policy version to fail';
    EXCEPTION WHEN unique_violation THEN NULL; END;
END
$test$;
ROLLBACK;
'@
Invoke-TestSql $behaviorSql | Out-Null

$jobSql = "BEGIN; INSERT INTO public.zones(id,zone_type,code,name) VALUES ('50000000-0000-0000-0000-000000000001','CITY','T-CONCURRENT-1','Concurrent One'); SELECT pg_sleep(3); ROLLBACK;"
$contenderSql = "BEGIN; INSERT INTO public.zones(id,zone_type,code,name) VALUES ('50000000-0000-0000-0000-000000000002','CITY','T-CONCURRENT-2','Concurrent Two'); ROLLBACK;"
$job = Start-Job -ScriptBlock {
    param($Psql,$Connection,$Sql)
    $env:PGPASSWORD=$Connection.Password
    & $Psql -X -v ON_ERROR_STOP=1 -h $Connection.Host -p $Connection.Port -U $Connection.User -d $Connection.Database -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'First concurrency transaction failed.' }
} -ArgumentList $PsqlPath,$connection,$jobSql
try {
    Start-Sleep -Milliseconds 700
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-TestSql $contenderSql | Out-Null
    $stopwatch.Stop()
    if ($stopwatch.Elapsed.TotalSeconds -lt 2.0) {
        throw "Concurrent hierarchy write was not serialized; wait was $($stopwatch.Elapsed.TotalSeconds) seconds."
    }
    Wait-Job -Job $job -Timeout 10 | Out-Null
    Receive-Job -Job $job -ErrorAction Stop | Out-Null
} finally {
    Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
}

$counts = Invoke-TestSql "SELECT (SELECT count(*) FROM public.zones),(SELECT count(*) FROM public.energy_meters),(SELECT count(*) FROM public.streetlights),(SELECT count(*) FROM public.lighting_profiles),(SELECT count(*) FROM public.roles);"
if (($counts -join "`n") -notmatch '\b0 \|\s+0 \|\s+0 \|\s+0 \|\s+5\b') { throw 'Test cleanup or role preservation verification failed.' }

'Database invariant behavior: PASS'
"Concurrency serialization: PASS ($([math]::Round($stopwatch.Elapsed.TotalSeconds,2))s wait)"
'Rollback cleanup and five-role preservation: PASS'
