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
        $output = & $PsqlPath -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $properties.user -d $dbName -At -F '|' -c $statement 2>&1
        if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
        $output
    } finally { $env:PGPASSWORD = $old }
}
function Quote([string]$value) { "'" + $value.Replace("'", "''") + "'" }
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
function Bytes([int]$length) { $bytes = [byte[]]::new($length); $rng.GetBytes($bytes); $bytes }
function Hash([string]$plain) {
    $salt = Bytes 16
    $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new($plain, $salt, 600000,
        [Security.Cryptography.HashAlgorithmName]::SHA256)
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
        $json = if ($payload -is [string]) { $payload } else { $payload | ConvertTo-Json -Depth 8 -Compress }
        $arguments += @('-H','Content-Type: application/json','--data-binary','@-')
        $code = $json | & curl.exe @arguments "$base$path"
    } else { $code = & curl.exe @arguments "$base$path" }
    [pscustomobject]@{ Code = [int]$code; Body = (Get-Content $bodyFile -Raw) }
}
function Login($identity) {
    $csrfReply = Http '/auth/csrf' $identity.Cookie
    $token = ($csrfReply.Body | ConvertFrom-Json).token
    $reply = Http '/auth/login' $identity.Cookie POST $token @{ email = $identity.Email; password = $identity.Password }
    if ($reply.Code -ne 200) { throw "Disposable login failed with HTTP $($reply.Code)" }
    $identity | Add-Member Csrf $token
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$roles = [ordered]@{ admin='ADMIN'; manager='CITY_MANAGER'; supervisor='SUPERVISOR'; local='LOCAL_STAFF'; resident='RESIDENT' }
$identities = @{}
foreach ($name in $roles.Keys) {
    $identities[$name] = [pscustomobject]@{
        Id=[guid]::NewGuid(); Email="phase45-$name-$suffix@test.invalid";
        Password=[Convert]::ToBase64String((Bytes 24)); Cookie=New-CookieFile
    }
}
$policyIds = [Collections.Generic.List[string]]::new()

try {
    $userValues = foreach ($name in $roles.Keys) {
        $identity = $identities[$name]
        "('$($identity.Id)','Phase 4.5 $name',$(Quote $identity.Email),$(Quote (Hash $identity.Password)),'ACTIVE')"
    }
    $roleValues = foreach ($name in $roles.Keys) { "('$($identities[$name].Id)','$($roles[$name])')" }
    Sql ("BEGIN; INSERT INTO users(id,full_name,email,password_hash,status) VALUES " + ($userValues -join ',') +
        "; INSERT INTO user_roles(user_id,role_code) VALUES " + ($roleValues -join ',') + '; COMMIT;') | Out-Null
    foreach ($name in $roles.Keys) { Login $identities[$name] }

    $residentRead = Http '/policies' $identities.resident.Cookie
    $withoutCsrf = Http '/policies' $identities.admin.Cookie POST $null @{
        policyCode="P45_NO_CSRF_$suffix"; name='Rejected'; domain='GENERAL'
    }
    if ($residentRead.Code -ne 403 -or $withoutCsrf.Code -ne 403) {
        throw "Security boundary failed: resident=$($residentRead.Code), csrf=$($withoutCsrf.Code)"
    }

    $noiseReply = Http '/policies' $identities.admin.Cookie POST $identities.admin.Csrf @{
        policyCode="P45_NOISE_$suffix"; name='Disposable noise policy'; domain='NOISE'; description='Phase 4.5 integration fixture'
    }
    if ($noiseReply.Code -ne 201) { throw "Noise policy creation failed: $($noiseReply.Code) $($noiseReply.Body)" }
    $noise = $noiseReply.Body | ConvertFrom-Json; $policyIds.Add($noise.id)

    $validReply = Http "/policies/$($noise.id)/versions" $identities.admin.Cookie POST $identities.admin.Csrf @{
        dslExpression='NOISE_DB > 65 AND HOUR >= 22'
    }
    $invalidReply = Http "/policies/$($noise.id)/versions" $identities.admin.Cookie POST $identities.admin.Csrf @{
        dslExpression='NOISE_DB > AND HOUR'
    }
    if ($validReply.Code -ne 201 -or $invalidReply.Code -ne 201) { throw 'Version creation failed' }
    $valid = $validReply.Body | ConvertFrom-Json; $invalid = $invalidReply.Body | ConvertFrom-Json
    if (-not $valid.valid -or $invalid.valid) { throw 'Version validation metadata is incorrect' }
    $invalidActivation = Http "/policies/$($noise.id)/versions/$($invalid.id)/activate" $identities.admin.Cookie POST $identities.admin.Csrf
    if ($invalidActivation.Code -ne 409) { throw "Invalid version activation returned $($invalidActivation.Code)" }
    $activation = Http "/policies/$($noise.id)/versions/$($valid.id)/activate" $identities.manager.Cookie POST $identities.manager.Csrf
    if ($activation.Code -ne 200) { throw "Valid activation failed: $($activation.Code) $($activation.Body)" }

    $trueReply = Http "/policies/$($noise.id)/evaluate" $identities.supervisor.Cookie POST $identities.supervisor.Csrf @{
        context=@{ NOISE_DB=70.5; HOUR=23 }
    }
    $falseReply = Http "/policies/$($noise.id)/evaluate" $identities.local.Cookie POST $identities.local.Csrf @{
        context=@{ NOISE_DB=60; HOUR=23 }
    }
    $badContext = Http "/policies/$($noise.id)/evaluate" $identities.local.Cookie POST $identities.local.Csrf @{
        context=@{ NOISE_DB=70; HOUR=24 }
    }
    $trueEvaluation = $trueReply.Body | ConvertFrom-Json; $falseEvaluation = $falseReply.Body | ConvertFrom-Json
    if ($trueReply.Code -ne 200 -or $trueEvaluation.result -ne $true -or
        $falseReply.Code -ne 200 -or $falseEvaluation.result -ne $false -or $badContext.Code -ne 400) {
        throw "Evaluation outcomes failed: true=$($trueReply.Code) false=$($falseReply.Code) invalid=$($badContext.Code)"
    }

    $updatedReply = Http "/policies/$($noise.id)/versions" $identities.manager.Cookie POST $identities.manager.Csrf @{
        dslExpression='NOISE_DB >= 80 OR HOUR >= 23'
    }
    if ($updatedReply.Code -ne 201) { throw 'Updated immutable version creation failed' }
    $updated = $updatedReply.Body | ConvertFrom-Json

    $activationScript = {
        param($url,$cookie,$csrf)
        & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" $url
    }
    $job1 = Start-Job $activationScript -ArgumentList "$base/policies/$($noise.id)/versions/$($valid.id)/activate",$identities.manager.Cookie,$identities.manager.Csrf
    $job2 = Start-Job $activationScript -ArgumentList "$base/policies/$($noise.id)/versions/$($updated.id)/activate",$identities.manager.Cookie,$identities.manager.Csrf
    $activationCodes = @(Receive-Job -Wait $job1; Receive-Job -Wait $job2) | ForEach-Object { $_.ToString().Trim() } | Sort-Object
    Remove-Job $job1,$job2
    if (@($activationCodes | Where-Object { $_ -ne '200' }).Count -gt 0) { throw "Concurrent activation failed: $($activationCodes -join ',')" }

    $parkingReply = Http '/policies' $identities.manager.Cookie POST $identities.manager.Csrf @{
        policyCode="P45_PARK_$suffix"; name='Disposable parking policy'; domain='PARKING'
    }
    if ($parkingReply.Code -ne 201) { throw 'CITY_MANAGER policy creation failed' }
    $parking = $parkingReply.Body | ConvertFrom-Json; $policyIds.Add($parking.id)
    $parkingVersionReply = Http "/policies/$($parking.id)/versions" $identities.manager.Cookie POST $identities.manager.Csrf @{
        dslExpression='(PARKING_MINUTES > 120 OR HOUR >= 20) AND ZONE == "A"'
    }
    $parkingVersion = $parkingVersionReply.Body | ConvertFrom-Json
    $null = Http "/policies/$($parking.id)/versions/$($parkingVersion.id)/validate" $identities.manager.Cookie POST $identities.manager.Csrf
    $null = Http "/policies/$($parking.id)/versions/$($parkingVersion.id)/activate" $identities.manager.Cookie POST $identities.manager.Csrf
    $parkingEval = Http "/policies/$($parking.id)/evaluate" $identities.manager.Cookie POST $identities.manager.Csrf @{
        context=@{ PARKING_MINUTES=90; HOUR=21; ZONE='A' }
    }
    if ($parkingEval.Code -ne 200 -or -not (($parkingEval.Body | ConvertFrom-Json).result)) { throw 'Parking policy evaluation failed' }

    $activeCounts = Sql "SELECT policy_id,count(*) FROM policy_versions WHERE policy_id IN ('$($noise.id)','$($parking.id)') AND is_active GROUP BY policy_id ORDER BY policy_id;"
    $historyCheck = Sql "SELECT count(*),count(DISTINCT policy_version_id),count(*) FILTER (WHERE context_json ? 'NOISE_DB') FROM policy_evaluations WHERE policy_version_id IN (SELECT id FROM policy_versions WHERE policy_id='$($noise.id)');"
    $immutableCheck = Sql "SELECT dsl_expression FROM policy_versions WHERE id='$($valid.id)';"
    $auditCheck = Sql "SELECT count(*) FROM audit_logs WHERE actor_user_id IN ('$($identities.admin.Id)','$($identities.manager.Id)') AND action LIKE 'POLICY_%';"
    if ($activeCounts.Count -ne 2 -or ($activeCounts | Where-Object { ($_ -split '\|')[1] -ne '1' })) { throw 'Single-active-version invariant failed' }
    if ($immutableCheck -ne 'NOISE_DB > 65 AND HOUR >= 22') { throw 'Historical DSL version was modified' }
    $historyParts = $historyCheck -split '\|'
    if ([int]$historyParts[0] -lt 3 -or [int]$historyParts[1] -lt 1 -or [int]$historyParts[2] -lt 3) { throw "Evaluation history/JSONB verification failed: $historyCheck" }
    if ([int]$auditCheck -lt 6) { throw "Policy audit events missing: $auditCheck" }

    $historyReply = Http "/policies/$($noise.id)/evaluations?offset=0&limit=20" $identities.admin.Cookie
    $versionsReply = Http "/policies/$($noise.id)/versions?offset=0&limit=20" $identities.admin.Cookie
    $supervisorVersions = Http "/policies/$($noise.id)/versions?offset=0&limit=20" $identities.supervisor.Cookie
    $versionRows = $versionsReply.Body | ConvertFrom-Json
    if ($historyReply.Code -ne 200 -or $versionsReply.Code -ne 200 -or $versionRows.Count -ne 3 -or $supervisorVersions.Code -ne 403) {
        throw "History endpoint verification failed: evaluations=$($historyReply.Code) versions=$($versionsReply.Code) versionCount=$($versionRows.Count) supervisor=$($supervisorVersions.Code) versionsBody=$($versionsReply.Body)"
    }
    $deactivate = Http "/policies/$($parking.id)/deactivate" $identities.admin.Cookie POST $identities.admin.Csrf
    if ($deactivate.Code -ne 200 -or ($deactivate.Body | ConvertFrom-Json).status -ne 'INACTIVE') { throw 'Policy deactivation failed' }

    "POLICY_PHASE45_PASS security=resident403/csrf403 versions=valid+invalid activation=serialized " +
        "evaluations=true,false,error jsonb=verified audit=$auditCheck activeVersions=1-per-policy"
} finally {
    $cleanupError = $null
    try {
        $policyList = @($policyIds) -join "','"
        $userList = @($identities.Values | ForEach-Object { $_.Id }) -join "','"
        $policyCleanup = if ($policyList) {
            "DELETE FROM policy_evaluations WHERE policy_version_id IN (SELECT id FROM policy_versions WHERE policy_id IN ('$policyList')); DELETE FROM policy_versions WHERE policy_id IN ('$policyList'); DELETE FROM policies WHERE id IN ('$policyList');"
        } else { '' }
        Sql "BEGIN; $policyCleanup DELETE FROM audit_logs WHERE actor_user_id IN ('$userList')$(if($policyList){" OR entity_id IN ('$policyList')"}); DELETE FROM user_roles WHERE user_id IN ('$userList'); DELETE FROM users WHERE id IN ('$userList'); COMMIT;" | Out-Null
        $residue = Sql "SELECT (SELECT count(*) FROM users WHERE id IN ('$userList')) + (SELECT count(*) FROM policies WHERE policy_code LIKE 'P45_%$suffix');"
        if ([int]$residue -ne 0) { throw "Disposable Phase 4.5 residue remains: $residue" }
    } catch { $cleanupError = $_ }
    finally {
        foreach ($identity in $identities.Values) { $identity.Password = $null }
        Remove-Item $temporaryFiles -Force -ErrorAction SilentlyContinue
    }
    if ($cleanupError) { throw $cleanupError }
}
