[CmdletBinding()]
param(
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\psql.exe'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$base = 'https://localhost:8181/smartcity360/api/v1'
[xml]$domain = Get-Content (Join-Path $PayaraHome 'glassfish\domains\domain1\config\domain.xml') -Raw
$resource = @($domain.SelectNodes("//*[local-name()='jdbc-resource']")) |
    Where-Object { $_.GetAttribute('jndi-name') -eq 'jdbc/smartcity360' } | Select-Object -First 1
$pool = @($domain.SelectNodes("//*[local-name()='jdbc-connection-pool']")) |
    Where-Object { $_.GetAttribute('name') -eq $resource.GetAttribute('pool-name') } | Select-Object -First 1
$properties = @{}
@($pool.SelectNodes("./*[local-name()='property']")) |
    ForEach-Object { $properties[$_.GetAttribute('name')] = $_.GetAttribute('value') }
$properties.URL -match '^jdbc:postgresql://(?<host>[^:/]+):(?<port>\d+)/(?<database>[^?]+)' | Out-Null
$dbHost = $Matches.host; $dbPort = $Matches.port; $dbName = $Matches.database

function Sql([string]$statement) {
    $old = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $properties.password
        $output = & $PsqlPath -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $properties.user -d $dbName -At -c $statement 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
        $output
    } finally { $env:PGPASSWORD = $old }
}
function Quote([string]$value) { "'" + $value.Replace("'", "''") + "'" }
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
function Bytes([int]$length) { $bytes = [byte[]]::new($length); $rng.GetBytes($bytes); $bytes }
function Hash([string]$plain) {
    $salt = Bytes 16
    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new(
        $plain, $salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256)
    $hash = $derive.GetBytes(32)
    'sc360$1$pbkdf2-sha256$600000$' +
        [Convert]::ToBase64String($salt).TrimEnd('=').Replace('+','-').Replace('/','_') + '$' +
        [Convert]::ToBase64String($hash).TrimEnd('=').Replace('+','-').Replace('/','_')
}

$temporaryFiles = [Collections.Generic.List[string]]::new()
$bodyFile = [IO.Path]::GetTempFileName(); $temporaryFiles.Add($bodyFile)
function New-CookieFile { $file = [IO.Path]::GetTempFileName(); $temporaryFiles.Add($file); $file }
function Http([string]$path, [string]$cookie, [string]$method = 'GET', [string]$csrf = $null, $payload = $null) {
    $arguments = @('-k','-sS','-c',$cookie,'-b',$cookie,'-o',$bodyFile,'-w','%{http_code}','-X',$method)
    if ($method -ne 'GET') { $arguments += @('-H','Origin: https://localhost:8181') }
    if ($csrf) { $arguments += @('-H',"X-CSRF-Token: $csrf") }
    if ($null -ne $payload) {
        $json = if ($payload -is [string]) { $payload } else { $payload | ConvertTo-Json -Compress }
        $arguments += @('-H','Content-Type: application/json','--data-binary','@-')
        $code = $json | & curl.exe @arguments "$base$path"
    } else { $code = & curl.exe @arguments "$base$path" }
    [pscustomobject]@{ Code = [int]$code; Body = (Get-Content $bodyFile -Raw) }
}
function Login([string]$email, [string]$password, [string]$cookie) {
    $csrfReply = Http '/auth/csrf' $cookie
    $token = ($csrfReply.Body | ConvertFrom-Json).token
    $login = Http '/auth/login' $cookie POST $token @{ email = $email; password = $password }
    if ($login.Code -ne 200) { throw "Login failed for disposable identity: $($login.Code)" }
    $token
}
function New-Profile([string]$code, [string]$model, [string]$cookie, [string]$csrf) {
    $reply = Http '/lighting-profiles' $cookie POST $csrf @{
        profileCode=$code; manufacturer='SmartCity Test'; model=$model; wattage=75.25;
        defaultBrightnessPct=80; controllerType='DALI-2'; firmwareFamily='P44';
        configurationJson='{"dimmingCurve":"linear","protocol":"DALI-2"}'
    }
    if ($reply.Code -ne 201) { throw "Profile creation failed: $($reply.Code) $($reply.Body)" }
    $reply.Body | ConvertFrom-Json
}
function New-Light([string]$tag, [string]$profileId, [string]$zoneId, [string]$cookie, [string]$csrf) {
    $reply = Http '/streetlights' $cookie POST $csrf @{
        assetTag=$tag; profileId=$profileId; streetZoneId=$zoneId; latitude=6.927079;
        longitude=79.861244; addressLabel='Disposable Phase 4.4 fixture'; installedOn='2026-11-01'
    }
    if ($reply.Code -ne 201) { throw "Streetlight registration failed: $($reply.Code) $($reply.Body)" }
    $reply.Body | ConvertFrom-Json
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$roles = [ordered]@{ admin='ADMIN'; manager='CITY_MANAGER'; supervisor='SUPERVISOR'; local='LOCAL_STAFF'; resident='RESIDENT' }
$identities = @{}
foreach ($name in $roles.Keys) {
    $identities[$name] = [pscustomobject]@{
        Id=[guid]::NewGuid(); Email="phase44-$name-$suffix@test.invalid";
        Password=[Convert]::ToBase64String((Bytes 24)); Cookie=New-CookieFile
    }
}
$cityId=[guid]::NewGuid(); $districtId=[guid]::NewGuid(); $streetId=[guid]::NewGuid();
$buildingId=[guid]::NewGuid(); $inactiveStreetId=[guid]::NewGuid()
$profileIds = [Collections.Generic.List[string]]::new()
$lightIds = [Collections.Generic.List[string]]::new()

try {
    $userValues = foreach ($name in $roles.Keys) {
        $identity=$identities[$name]
        "('$($identity.Id)','Phase 4.4 $name',$(Quote $identity.Email),$(Quote (Hash $identity.Password)),'ACTIVE')"
    }
    $roleValues = foreach ($name in $roles.Keys) { "('$($identities[$name].Id)','$($roles[$name])')" }
    Sql ("BEGIN; INSERT INTO users(id,full_name,email,password_hash,status) VALUES " + ($userValues -join ',') +
        "; INSERT INTO user_roles(user_id,role_code) VALUES " + ($roleValues -join ',') +
        "; INSERT INTO zones(id,parent_zone_id,zone_type,code,name,status) VALUES " +
        "('$cityId',NULL,'CITY','P44-C-$suffix','Phase 4.4 City','ACTIVE')," +
        "('$districtId','$cityId','DISTRICT','P44-D-$suffix','Phase 4.4 District','ACTIVE')," +
        "('$streetId','$districtId','STREET','P44-S-$suffix','Phase 4.4 Street','ACTIVE')," +
        "('$buildingId','$streetId','BUILDING','P44-B-$suffix','Phase 4.4 Building','ACTIVE')," +
        "('$inactiveStreetId','$districtId','STREET','P44-I-$suffix','Phase 4.4 Inactive Street','INACTIVE'); COMMIT;") | Out-Null

    foreach ($name in $roles.Keys) {
        $identities[$name] | Add-Member Csrf (Login $identities[$name].Email $identities[$name].Password $identities[$name].Cookie)
    }
    $admin=$identities.admin
    $health = Http '/health' $admin.Cookie
    if ($health.Code -ne 200 -or ($health.Body | ConvertFrom-Json).status -ne 'UP') { throw 'Health endpoint regression' }

    $noCsrf = Http '/lighting-profiles' $admin.Cookie POST $null @{
        profileCode="P44-NOCSRF-$suffix";manufacturer='x';model='x';wattage=10;defaultBrightnessPct=50;
        controllerType='x';configurationJson='{}'
    }
    $residentRead = Http '/streetlights?offset=0&limit=10' $identities.resident.Cookie
    if ($noCsrf.Code -ne 403 -or $residentRead.Code -ne 403) {
        throw "Security boundary failed: csrf=$($noCsrf.Code) resident=$($residentRead.Code)"
    }

    $profile = New-Profile "P44-P1-$suffix" 'SL-100' $admin.Cookie $admin.Csrf
    $profileIds.Add($profile.id)
    $profileCreateJob = {
        param($url,$cookie,$csrf,$json)
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    $duplicateProfileJson = @{
        profileCode="P44-UNIQUE-$suffix";manufacturer='SmartCity Test';model='Concurrent';wattage=60;
        defaultBrightnessPct=70;controllerType='DALI-2';firmwareFamily='P44';configurationJson='{}'
    } | ConvertTo-Json -Compress
    $job1=Start-Job $profileCreateJob -ArgumentList "$base/lighting-profiles",$admin.Cookie,$admin.Csrf,$duplicateProfileJson
    $job2=Start-Job $profileCreateJob -ArgumentList "$base/lighting-profiles",$admin.Cookie,$admin.Csrf,$duplicateProfileJson
    $profileCreateCodes=@(Receive-Job -Wait $job1;Receive-Job -Wait $job2) | ForEach-Object {$_.ToString().Trim()} | Sort-Object
    Remove-Job $job1,$job2
    if (($profileCreateCodes -join ',') -ne '201,409') {
        throw "Concurrent profile uniqueness result was $($profileCreateCodes -join ',')"
    }
    $badJson = Http '/lighting-profiles' $admin.Cookie POST $admin.Csrf @{
        profileCode="P44-BAD-$suffix";manufacturer='x';model='x';wattage=10;defaultBrightnessPct=50;
        controllerType='x';configurationJson='[]'
    }
    $invalidZone = Http '/streetlights' $admin.Cookie POST $admin.Csrf @{
        assetTag="P44-BADZONE-$suffix";profileId=$profile.id;streetZoneId=$buildingId
    }
    $inactiveZone = Http '/streetlights' $admin.Cookie POST $admin.Csrf @{
        assetTag="P44-INACTIVE-$suffix";profileId=$profile.id;streetZoneId=$inactiveStreetId
    }
    if ($badJson.Code -ne 400 -or $invalidZone.Code -ne 409 -or $inactiveZone.Code -ne 409) {
        throw "Input/zone validation failed: json=$($badJson.Code) type=$($invalidZone.Code) inactive=$($inactiveZone.Code)"
    }

    $light1 = New-Light "P44-L1-$suffix" $profile.id $streetId $admin.Cookie $admin.Csrf; $lightIds.Add($light1.id)
    $light2 = New-Light "P44-L2-$suffix" $profile.id $streetId $admin.Cookie $admin.Csrf; $lightIds.Add($light2.id)
    $lightCreateJob = {
        param($url,$cookie,$csrf,$json)
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    $duplicateLightJson = @{
        assetTag="P44-UNIQUE-LIGHT-$suffix";profileId=$profile.id;streetZoneId=$streetId;
        latitude=6.927079;longitude=79.861244;addressLabel='Concurrent uniqueness fixture';installedOn='2026-11-01'
    } | ConvertTo-Json -Compress
    $job1=Start-Job $lightCreateJob -ArgumentList "$base/streetlights",$admin.Cookie,$admin.Csrf,$duplicateLightJson
    $job2=Start-Job $lightCreateJob -ArgumentList "$base/streetlights",$admin.Cookie,$admin.Csrf,$duplicateLightJson
    $lightCreateCodes=@(Receive-Job -Wait $job1;Receive-Job -Wait $job2) | ForEach-Object {$_.ToString().Trim()} | Sort-Object
    Remove-Job $job1,$job2
    if (($lightCreateCodes -join ',') -ne '201,409') {
        throw "Concurrent asset-tag uniqueness result was $($lightCreateCodes -join ',')"
    }
    $on = Http "/streetlights/$($light1.id)/on" $admin.Cookie POST $admin.Csrf
    $brightness = Http "/streetlights/$($light1.id)/brightness" $admin.Cookie POST $admin.Csrf @{brightnessPct=35}
    $offBrightness = Http "/streetlights/$($light2.id)/brightness" $admin.Cookie POST $admin.Csrf @{brightnessPct=20}
    $second = Http "/streetlights/$($light2.id)" $identities.local.Cookie
    $secondJson = $second.Body | ConvertFrom-Json
    if ($on.Code -ne 200 -or $brightness.Code -ne 200 -or $offBrightness.Code -ne 200 -or
        $second.Code -ne 200 -or $secondJson.operationalState -ne 'OFF' -or $secondJson.effectiveBrightnessPct -ne 20) {
        throw 'Independent extrinsic state behavior failed'
    }

    $auditBefore = [int](Sql "SELECT count(*) FROM audit_logs WHERE entity_id='$($light1.id)' AND action='STREETLIGHT_TURNED_ON';" | Select-Object -First 1)
    $lastSeenBefore = Sql "SELECT last_seen_at::text FROM streetlights WHERE id='$($light1.id)';" | Select-Object -First 1
    $repeat = Http "/streetlights/$($light1.id)/on" $admin.Cookie POST $admin.Csrf
    $auditAfter = [int](Sql "SELECT count(*) FROM audit_logs WHERE entity_id='$($light1.id)' AND action='STREETLIGHT_TURNED_ON';" | Select-Object -First 1)
    $lastSeenAfter = Sql "SELECT last_seen_at::text FROM streetlights WHERE id='$($light1.id)';" | Select-Object -First 1
    if ($repeat.Code -ne 200 -or ($repeat.Body | ConvertFrom-Json).changed -ne $false -or
        $auditBefore -ne $auditAfter -or $lastSeenBefore -ne $lastSeenAfter) {
        throw 'Idempotent operation produced a duplicate write or audit event'
    }

    $versionReply = Http "/lighting-profiles/$($profile.id)/versions" $admin.Cookie POST $admin.Csrf @{
        profileCode="P44-P1V2-$suffix";manufacturer='SmartCity Test';model='SL-100R';wattage=70.00;
        defaultBrightnessPct=75;controllerType='DALI-2';firmwareFamily='P44-R2';
        configurationJson='{"dimmingCurve":"logarithmic","protocol":"DALI-2"}'
    }
    if ($versionReply.Code -ne 201) { throw "Version creation failed: $($versionReply.Code) $($versionReply.Body)" }
    $version = $versionReply.Body | ConvertFrom-Json; $profileIds.Add($version.id)
    $reassign = Http "/streetlights/$($light2.id)/profile" $admin.Cookie POST $admin.Csrf @{profileId=$version.id}
    $inUse = Http "/lighting-profiles/$($profile.id)/deactivate" $admin.Cookie POST $admin.Csrf
    $unused = New-Profile "P44-UNUSED-$suffix" 'UNUSED' $admin.Cookie $admin.Csrf; $profileIds.Add($unused.id)
    $deactivate = Http "/lighting-profiles/$($unused.id)/deactivate" $admin.Cookie POST $admin.Csrf
    $versionEvidence = Sql "SELECT profile_version||'|'||model||'|'||(config_json->>'dimmingCurve') FROM lighting_profiles WHERE id IN('$($profile.id)','$($version.id)') ORDER BY profile_version;"
    if ($version.profileVersion -ne 2 -or $reassign.Code -ne 200 -or $inUse.Code -ne 409 -or
        $deactivate.Code -ne 200 -or ($versionEvidence -join ',') -ne '1|SL-100|linear,2|SL-100R|logarithmic') {
        throw "Profile version/invalidation behavior failed: $($versionEvidence -join ',')"
    }

    $registrationRace = New-Profile "P44-RACE-REG-$suffix" 'RACE-REG' $admin.Cookie $admin.Csrf
    $profileIds.Add($registrationRace.id)
    $raceLightJson = @{
        assetTag="P44-RACE-REG-LIGHT-$suffix";profileId=$registrationRace.id;streetZoneId=$streetId;
        latitude=6.927079;longitude=79.861244;addressLabel='Registration/deactivation race';installedOn='2026-11-01'
    } | ConvertTo-Json -Compress
    $deactivateJob = {
        param($url,$cookie,$csrf)
        & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" $url
    }
    $job1=Start-Job $lightCreateJob -ArgumentList "$base/streetlights",$admin.Cookie,$admin.Csrf,$raceLightJson
    $job2=Start-Job $deactivateJob -ArgumentList "$base/lighting-profiles/$($registrationRace.id)/deactivate",$admin.Cookie,$admin.Csrf
    $registrationRaceCodes=@(Receive-Job -Wait $job1;Receive-Job -Wait $job2) | ForEach-Object {$_.ToString().Trim()} | Sort-Object
    Remove-Job $job1,$job2

    $reassignmentRace = New-Profile "P44-RACE-REASSIGN-$suffix" 'RACE-REASSIGN' $admin.Cookie $admin.Csrf
    $profileIds.Add($reassignmentRace.id)
    $reassignJson = @{profileId=$reassignmentRace.id} | ConvertTo-Json -Compress
    $reassignJob = {
        param($url,$cookie,$csrf,$json)
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    $job1=Start-Job $reassignJob -ArgumentList "$base/streetlights/$($light1.id)/profile",$admin.Cookie,$admin.Csrf,$reassignJson
    $job2=Start-Job $deactivateJob -ArgumentList "$base/lighting-profiles/$($reassignmentRace.id)/deactivate",$admin.Cookie,$admin.Csrf
    $reassignmentRaceCodes=@(Receive-Job -Wait $job1;Receive-Job -Wait $job2) | ForEach-Object {$_.ToString().Trim()} | Sort-Object
    Remove-Job $job1,$job2
    $inactiveReferences = [int](Sql "SELECT count(*) FROM streetlights s JOIN lighting_profiles p ON p.id=s.profile_id WHERE p.is_active=false AND (p.id='$($registrationRace.id)' OR p.id='$($reassignmentRace.id)');" | Select-Object -First 1)
    if (($registrationRaceCodes -join ',') -notin @('200,409','201,409') -or
        ($reassignmentRaceCodes -join ',') -ne '200,409' -or $inactiveReferences -ne 0) {
        throw "Profile deactivation serialization failed: register=$($registrationRaceCodes -join ',') reassign=$($reassignmentRaceCodes -join ',') inactiveRefs=$inactiveReferences"
    }

    $managerRead = Http "/streetlights/$($light1.id)" $identities.manager.Cookie
    $managerOff = Http "/streetlights/$($light1.id)/off" $identities.manager.Cookie POST $identities.manager.Csrf
    $supervisorStats = Http '/streetlights/stats' $identities.supervisor.Cookie
    $supervisorWrite = Http "/streetlights/$($light1.id)/on" $identities.supervisor.Cookie POST $identities.supervisor.Csrf
    if ($managerRead.Code -ne 200 -or $managerOff.Code -ne 200 -or $supervisorStats.Code -ne 200 -or $supervisorWrite.Code -ne 403) {
        throw 'Role authorization behavior failed'
    }

    $jobScript = {
        param($url,$cookie,$csrf)
        & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" $url
    }
    $job1=Start-Job $jobScript -ArgumentList "$base/streetlights/$($light1.id)/on",$admin.Cookie,$admin.Csrf
    $job2=Start-Job $jobScript -ArgumentList "$base/streetlights/$($light1.id)/off",$admin.Cookie,$admin.Csrf
    $commandCodes=@(Receive-Job -Wait $job1;Receive-Job -Wait $job2) | ForEach-Object {$_.ToString().Trim()} | Sort-Object
    Remove-Job $job1,$job2
    $final = Http "/streetlights/$($light1.id)" $admin.Cookie
    $finalState = ($final.Body | ConvertFrom-Json).operationalState
    $databaseState = Sql "SELECT operational_state FROM streetlights WHERE id='$($light1.id)';" | Select-Object -First 1
    if (($commandCodes -join ',') -ne '200,200' -or $final.Code -ne 200 -or $finalState -ne $databaseState) {
        throw "Concurrent command serialization failed: $($commandCodes -join ',') final=$finalState db=$databaseState"
    }

    1..5 | ForEach-Object { Http "/streetlights/$($light1.id)" $admin.Cookie | Out-Null }
    $statsReply = Http '/streetlights/stats' $admin.Cookie
    $stats = $statsReply.Body | ConvertFrom-Json
    $dbEvidence = Sql "SELECT count(*)||'|'||count(DISTINCT profile_id)||'|'||count(*) FILTER(WHERE brightness_override_pct IS NOT NULL) FROM streetlights WHERE id IN('$($light1.id)','$($light2.id)');" | Select-Object -First 1
    $auditCount = [int](Sql "SELECT count(*) FROM audit_logs WHERE actor_user_id IN('$($admin.Id)','$($identities.manager.Id)') AND (entity_id IN('$($light1.id)','$($light2.id)') OR entity_id IN('$($profile.id)','$($version.id)','$($unused.id)'));" | Select-Object -First 1)
    if ($statsReply.Code -ne 200 -or $stats.persistedStreetlights -lt 2 -or $stats.cachedFlyweights -lt 2 -or
        $stats.cacheHits -lt 5 -or $stats.contextToFlyweightRatio -le 1 -or $dbEvidence -ne '2|2|2' -or $auditCount -lt 9) {
        throw "Stats/JPA/audit evidence failed: stats=$($statsReply.Body) db=$dbEvidence audit=$auditCount"
    }

    "Streetlight runtime: PASS (health=200 csrf=403 resident=403 supervisor-write=403 manager-operation=200)"
    "Flyweight/cache: PASS (cached=$($stats.cachedFlyweights) hits=$($stats.cacheHits) misses=$($stats.cacheMisses) contexts=$($stats.contextsProcessed) ratio=$($stats.contextToFlyweightRatio))"
    "State/concurrency: PASS (independent states, idempotent no-write, concurrent=$($commandCodes -join ',') final=$finalState)"
    "Creation/deactivation concurrency: PASS (profile=$($profileCreateCodes -join ',') asset=$($lightCreateCodes -join ',') register-vs-deactivate=$($registrationRaceCodes -join ',') reassign-vs-deactivate=$($reassignmentRaceCodes -join ','); inactive refs=0)"
    "JPA/JSONB/audit: PASS (streetlights/profiles/overrides=$dbEvidence; profile versions preserved; audit rows=$auditCount)"
} finally {
    $cleanupFailure = $null
    try {
        $userIds = ($roles.Keys | ForEach-Object { "'$($identities[$_].Id)'" }) -join ','
        $profilePattern = "P44-%-$suffix"
        $lightPattern = "P44-%-$suffix"
        $zonePattern = "P44-%-$suffix"
        $emailPattern = "phase44-%-$suffix@test.invalid"
        Sql "BEGIN;
            DELETE FROM audit_logs WHERE actor_user_id IN($userIds)
                OR entity_id IN (SELECT id FROM streetlights WHERE asset_tag LIKE '$lightPattern')
                OR entity_id IN (SELECT id FROM lighting_profiles WHERE profile_code LIKE '$profilePattern')
                OR entity_id IN (SELECT id FROM zones WHERE code LIKE '$zonePattern');
            DELETE FROM streetlights WHERE asset_tag LIKE '$lightPattern';
            DELETE FROM lighting_profiles WHERE profile_code LIKE '$profilePattern';
            DELETE FROM zones WHERE code LIKE '$zonePattern';
            DELETE FROM user_roles WHERE user_id IN($userIds);
            DELETE FROM users WHERE email LIKE '$emailPattern';
            COMMIT;" | Out-Null
        $residue = Sql "SELECT
            (SELECT count(*) FROM users WHERE email LIKE '$emailPattern')||'|'||
            (SELECT count(*) FROM user_roles WHERE user_id IN($userIds))||'|'||
            (SELECT count(*) FROM lighting_profiles WHERE profile_code LIKE '$profilePattern')||'|'||
            (SELECT count(*) FROM streetlights WHERE asset_tag LIKE '$lightPattern')||'|'||
            (SELECT count(*) FROM zones WHERE code LIKE '$zonePattern')||'|'||
            (SELECT count(*) FROM audit_logs WHERE actor_user_id IN($userIds));" | Select-Object -First 1
        if ($residue -ne '0|0|0|0|0|0') { throw "cleanup residue users|roles|profiles|lights|zones|audit=$residue" }
    } catch {
        $cleanupFailure = "Disposable integration cleanup failed: $($_.Exception.Message)"
    } finally {
        foreach ($file in $temporaryFiles) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
        foreach ($name in $roles.Keys) { $identities[$name].Password = $null }
        $rng.Dispose()
    }
    if ($cleanupFailure) { throw $cleanupFailure }
}
