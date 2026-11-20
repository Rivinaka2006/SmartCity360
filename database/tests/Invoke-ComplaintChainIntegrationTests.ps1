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
function Submit([string]$severity, [string]$title) {
    $reply = Http '/complaints' $resident1Cookie POST $resident1Csrf @{
        title = $title; description = "Disposable Phase 4.3 $severity complaint";
        severity = $severity; zoneId = $zoneId
    }
    if ($reply.Code -ne 201) { throw "Complaint submission failed: $($reply.Code) $($reply.Body)" }
    $reply.Body | ConvertFrom-Json
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$rolesByName = [ordered]@{
    resident1 = 'RESIDENT'; resident2 = 'RESIDENT'; local1 = 'LOCAL_STAFF'; local2 = 'LOCAL_STAFF';
    supervisor = 'SUPERVISOR'; manager = 'CITY_MANAGER'; admin = 'ADMIN'
}
$identities = @{}
foreach ($name in $rolesByName.Keys) {
    $identities[$name] = [pscustomobject]@{
        Id = [guid]::NewGuid(); Email = "phase43-$name-$suffix@test.invalid";
        Password = [Convert]::ToBase64String((Bytes 24)); Cookie = New-CookieFile
    }
}
$zoneId = [guid]::NewGuid()
$complaintIds = [Collections.Generic.List[string]]::new()

try {
    $userValues = foreach ($name in $rolesByName.Keys) {
        $identity = $identities[$name]
        "('$($identity.Id)','Phase 4.3 $name',$(Quote $identity.Email),$(Quote (Hash $identity.Password)),'ACTIVE')"
    }
    $roleValues = foreach ($name in $rolesByName.Keys) { "('$($identities[$name].Id)','$($rolesByName[$name])')" }
    Sql ("BEGIN; INSERT INTO users(id,full_name,email,password_hash,status) VALUES " +
        ($userValues -join ',') + "; INSERT INTO user_roles(user_id,role_code) VALUES " +
        ($roleValues -join ',') + "; INSERT INTO zones(id,parent_zone_id,zone_type,code,name,status) VALUES " +
        "('$zoneId',NULL,'CITY','P43-C-$suffix','Phase 4.3 City','ACTIVE'); COMMIT;") | Out-Null

    foreach ($name in $rolesByName.Keys) {
        $identities[$name] | Add-Member Csrf (Login $identities[$name].Email $identities[$name].Password $identities[$name].Cookie)
    }
    $resident1Cookie = $identities.resident1.Cookie; $resident1Csrf = $identities.resident1.Csrf

    $low = Submit LOW 'Low routing'; $complaintIds.Add($low.id)
    $medium = Submit MEDIUM 'Medium routing'; $complaintIds.Add($medium.id)
    $high = Submit HIGH 'High routing'; $complaintIds.Add($high.id)
    $critical = Submit CRITICAL 'Critical routing'; $complaintIds.Add($critical.id)
    $nullZone = Http '/complaints' $resident1Cookie POST $resident1Csrf @{
        title='Missing zone';description='Must return a controlled client error';severity='LOW';zoneId=$null
    }
    if ($low.currentHandlerLevel -ne 'LOCAL_STAFF' -or $medium.currentHandlerLevel -ne 'LOCAL_STAFF' -or
        $high.currentHandlerLevel -ne 'SUPERVISOR' -or $critical.currentHandlerLevel -ne 'CITY_MANAGER' -or
        $nullZone.Code -ne 400) {
        throw 'Severity routing does not match the Chain of Responsibility policy'
    }

    $noCsrf = Http '/complaints' $resident1Cookie POST $null @{
        title='No CSRF';description='Must be rejected';severity='LOW';zoneId=$zoneId
    }
    $otherResident = Http "/complaints/$($low.id)" $identities.resident2.Cookie
    $localOnSupervisor = Http "/complaints/$($high.id)/assign" $identities.local1.Cookie POST $identities.local1.Csrf @{ assigneeId = $null }
    if ($noCsrf.Code -ne 403 -or $otherResident.Code -ne 404 -or $localOnSupervisor.Code -ne 403) {
        throw "Security boundary failed: csrf=$($noCsrf.Code) ownership=$($otherResident.Code) role=$($localOnSupervisor.Code)"
    }

    $localQueue = Http '/complaints/queue?offset=0&limit=200' $identities.local1.Cookie
    $supervisorQueue = Http '/complaints/queue?offset=0&limit=200' $identities.supervisor.Cookie
    $managerQueue = Http '/complaints/queue?offset=0&limit=200' $identities.manager.Cookie
    $myComplaints = Http '/complaints/my?offset=0&limit=2' $resident1Cookie
    $highFilter = Http '/complaints?severity=HIGH&status=OPEN&offset=0&limit=10' $identities.admin.Cookie
    $localRows = $localQueue.Body | ConvertFrom-Json; $localCount = $localRows.Count
    $supervisorRows = $supervisorQueue.Body | ConvertFrom-Json; $supervisorCount = $supervisorRows.Count
    $managerRows = $managerQueue.Body | ConvertFrom-Json; $managerCount = $managerRows.Count
    $myRows = $myComplaints.Body | ConvertFrom-Json; $myCount = $myRows.Count
    $filterRows = $highFilter.Body | ConvertFrom-Json; $filterCount = $filterRows.Count
    $localIds = @($localRows | ForEach-Object id); $supervisorIds = @($supervisorRows | ForEach-Object id)
    $managerIds = @($managerRows | ForEach-Object id); $filterIds = @($filterRows | ForEach-Object id)
    if (-not ($localIds -contains $low.id) -or -not ($localIds -contains $medium.id) -or
        -not ($supervisorIds -contains $high.id) -or -not ($managerIds -contains $critical.id) -or
        $myCount -ne 2 -or -not ($filterIds -contains $high.id)) {
        throw "Queue, pagination, or filtering failed: local=$localCount supervisor=$supervisorCount manager=$managerCount my=$myCount filter=$filterCount"
    }

    $assignJob = {
        param($url,$cookie,$csrf)
        $json = '{"assigneeId":null}'
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    $job1 = Start-Job $assignJob -ArgumentList "$base/complaints/$($low.id)/assign",$identities.local1.Cookie,$identities.local1.Csrf
    $job2 = Start-Job $assignJob -ArgumentList "$base/complaints/$($low.id)/assign",$identities.local2.Cookie,$identities.local2.Csrf
    $assignmentCodes = @(Receive-Job -Wait $job1; Receive-Job -Wait $job2) | ForEach-Object { $_.ToString().Trim() } | Sort-Object
    Remove-Job $job1,$job2
    if (($assignmentCodes -join ',') -ne '200,409') { throw "Concurrent assignment result was $($assignmentCodes -join ',')" }
    $invalidResolution = Http "/complaints/$($low.id)/resolve" $identities.admin.Cookie POST $identities.admin.Csrf @{
        note='Must fail before IN_PROGRESS'
    }
    if ($invalidResolution.Code -ne 409) { throw "Invalid resolution transition returned $($invalidResolution.Code)" }

    $assignMediumLocal = Http "/complaints/$($medium.id)/assign" $identities.local1.Cookie POST $identities.local1.Csrf @{ assigneeId = $null }
    $escalate1 = Http "/complaints/$($medium.id)/escalate" $identities.local1.Cookie POST $identities.local1.Csrf @{
        expectedLevel='LOCAL_STAFF';reason='Requires supervisory review'
    }
    $duplicateEscalation = Http "/complaints/$($medium.id)/escalate" $identities.local1.Cookie POST $identities.local1.Csrf @{
        expectedLevel='LOCAL_STAFF';reason='Stale duplicate'
    }
    $assignMediumSupervisor = Http "/complaints/$($medium.id)/assign" $identities.supervisor.Cookie POST $identities.supervisor.Csrf @{ assigneeId = $null }
    $escalate2 = Http "/complaints/$($medium.id)/escalate" $identities.supervisor.Cookie POST $identities.supervisor.Csrf @{
        expectedLevel='SUPERVISOR';reason='Requires city management review'
    }
    $assignMediumManager = Http "/complaints/$($medium.id)/assign" $identities.manager.Cookie POST $identities.manager.Csrf @{ assigneeId = $null }
    $beyondManager = Http "/complaints/$($medium.id)/escalate" $identities.manager.Cookie POST $identities.manager.Csrf @{
        expectedLevel='CITY_MANAGER';reason='Must not advance'
    }
    if ($assignMediumLocal.Code -ne 200 -or $escalate1.Code -ne 200 -or $duplicateEscalation.Code -ne 409 -or
        $assignMediumSupervisor.Code -ne 200 -or $escalate2.Code -ne 200 -or
        $assignMediumManager.Code -ne 200 -or $beyondManager.Code -ne 409) {
        throw 'Escalation lifecycle failed'
    }

    $assignHigh = Http "/complaints/$($high.id)/assign" $identities.supervisor.Cookie POST $identities.supervisor.Csrf @{ assigneeId = $null }
    if ($assignHigh.Code -ne 200) { throw "Supervisor assignment failed: $($assignHigh.Code)" }
    $escalateJob = {
        param($url,$cookie,$csrf)
        $json = '{"expectedLevel":"SUPERVISOR","reason":"Concurrent escalation proof"}'
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    $job1 = Start-Job $escalateJob -ArgumentList "$base/complaints/$($high.id)/escalate",$identities.supervisor.Cookie,$identities.supervisor.Csrf
    $job2 = Start-Job $escalateJob -ArgumentList "$base/complaints/$($high.id)/escalate",$identities.supervisor.Cookie,$identities.supervisor.Csrf
    $escalationCodes = @(Receive-Job -Wait $job1; Receive-Job -Wait $job2) | ForEach-Object { $_.ToString().Trim() } | Sort-Object
    Remove-Job $job1,$job2
    if (($escalationCodes -join ',') -ne '200,409') { throw "Concurrent escalation result was $($escalationCodes -join ',')" }

    $adminStartUnassigned = Http "/complaints/$($critical.id)/start" $identities.admin.Cookie POST $identities.admin.Csrf
    if ($adminStartUnassigned.Code -ne 409) { throw "ADMIN bypassed assignment invariant: $($adminStartUnassigned.Code)" }
    $assignCritical = Http "/complaints/$($critical.id)/assign" $identities.manager.Cookie POST $identities.manager.Csrf @{ assigneeId = $null }
    $start = Http "/complaints/$($critical.id)/start" $identities.manager.Cookie POST $identities.manager.Csrf
    if ($start.Code -ne 200) { throw "Start failed: $($start.Code) $($start.Body)" }
    $resolveJob = {
        param($url,$cookie,$csrf)
        $json = '{"note":"Concurrent verified resolution"}'
        $json | & curl.exe -k -sS -o NUL -w '%{http_code}' -X POST -b $cookie `
            -H 'Origin: https://localhost:8181' -H "X-CSRF-Token: $csrf" `
            -H 'Content-Type: application/json' --data-binary '@-' $url
    }
    if ($assignCritical.Code -ne 200) { throw "City-manager assignment failed: $($assignCritical.Code)" }
    $job1 = Start-Job $resolveJob -ArgumentList "$base/complaints/$($critical.id)/resolve",$identities.manager.Cookie,$identities.manager.Csrf
    $job2 = Start-Job $resolveJob -ArgumentList "$base/complaints/$($critical.id)/resolve",$identities.manager.Cookie,$identities.manager.Csrf
    $resolutionCodes = @(Receive-Job -Wait $job1; Receive-Job -Wait $job2) | ForEach-Object { $_.ToString().Trim() } | Sort-Object
    Remove-Job $job1,$job2
    if (($resolutionCodes -join ',') -ne '200,409') { throw "Concurrent resolution result was $($resolutionCodes -join ',')" }

    $history = Http "/complaints/$($medium.id)/history" $identities.resident1.Cookie
    $historyRows = $history.Body | ConvertFrom-Json
    if ($history.Code -ne 200 -or ($historyRows.action -join ',') -ne 'SUBMITTED,ASSIGNED,ESCALATED,ASSIGNED,ESCALATED,ASSIGNED' -or
        ($historyRows.toLevel -join ',') -ne 'LOCAL_STAFF,LOCAL_STAFF,SUPERVISOR,SUPERVISOR,CITY_MANAGER,CITY_MANAGER' -or
        -not ($historyRows[2].note -match 'ROUTED:LOCAL_STAFF->SUPERVISOR') -or
        -not ($historyRows[4].note -match 'ROUTED:LOCAL_STAFF->SUPERVISOR->CITY_MANAGER')) {
        throw "Escalation history is incomplete or unordered: $($history.Body)"
    }

    $dbEvidence = Sql "SELECT count(*)||'|'||count(*) FILTER (WHERE status='RESOLVED')||'|'||(SELECT count(*) FROM complaint_events e WHERE e.complaint_id IN ('$($low.id)','$($medium.id)','$($high.id)','$($critical.id)')) FROM complaints WHERE id IN ('$($low.id)','$($medium.id)','$($high.id)','$($critical.id)');"
    $auditCount = [int](Sql "SELECT count(*) FROM audit_logs WHERE entity_id IN ('$($low.id)','$($medium.id)','$($high.id)','$($critical.id)') AND action LIKE 'COMPLAINT_%';" | Select-Object -First 1)
    $evidenceParts = ($dbEvidence | Select-Object -First 1).Split('|')
    if ([int]$evidenceParts[0] -ne 4 -or [int]$evidenceParts[1] -ne 1 -or
        [int]$evidenceParts[2] -lt 8 -or $auditCount -lt 12) {
        throw "Persistence evidence failed: complaints/events=$dbEvidence audit=$auditCount"
    }

    "Complaint routing: PASS (LOW/MEDIUM=LOCAL_STAFF, HIGH=SUPERVISOR, CRITICAL=CITY_MANAGER)"
    "Security: PASS (CSRF=403, resident ownership=404, wrong handler role=403)"
    "Concurrency: PASS (assignment=$($assignmentCodes -join ','), escalation=$($escalationCodes -join ','), resolution=$($resolutionCodes -join ','))"
    "Workflow persistence: PASS ($dbEvidence; complaint audit rows=$auditCount)"
} finally {
    try {
        $userIds = ($rolesByName.Keys | ForEach-Object { "'$($identities[$_].Id)'" }) -join ','
        $ids = ($complaintIds | ForEach-Object { "'$_'" }) -join ','
        if ($ids) {
            Sql "BEGIN; DELETE FROM audit_logs WHERE actor_user_id IN ($userIds) OR entity_id IN ($ids); DELETE FROM complaint_events WHERE complaint_id IN ($ids); DELETE FROM complaints WHERE id IN ($ids); DELETE FROM zones WHERE id='$zoneId'; DELETE FROM user_roles WHERE user_id IN ($userIds); DELETE FROM users WHERE id IN ($userIds); COMMIT;" | Out-Null
        } else {
            Sql "BEGIN; DELETE FROM audit_logs WHERE actor_user_id IN ($userIds); DELETE FROM zones WHERE id='$zoneId'; DELETE FROM user_roles WHERE user_id IN ($userIds); DELETE FROM users WHERE id IN ($userIds); COMMIT;" | Out-Null
        }
    } catch { Write-Warning "Disposable integration cleanup failed: $($_.Exception.Message)" }
    foreach ($file in $temporaryFiles) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
    foreach ($name in $rolesByName.Keys) { $identities[$name].Password = $null }
    $rng.Dispose()
}
