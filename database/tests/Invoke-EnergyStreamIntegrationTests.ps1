[CmdletBinding()]
param(
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\psql.exe',
    [string]$DomainName = 'domain1',
    [switch]$ConfirmLocalDevelopmentMutation,
    [switch]$RequireManualRestart,
    [string]$RecoverStateFile
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security -ErrorAction Stop
$expectedDatabase = 'smartcity360'; $expectedJndi = 'jdbc/smartcity360'; $deploymentName = 'smartcity360-ear-1.0-SNAPSHOT'
$energyProperty = 'smartcity360.energy.aes.current-key-id'
$apiBaseUri = 'https://localhost:8181/smartcity360/api/v1'
$baseUri = "$apiBaseUri/energy/streams"
$healthUri = 'https://localhost:8181/smartcity360/api/v1/health'
$temporaryFiles = New-Object Collections.Generic.List[string]; $cleanupFailures = New-Object Collections.Generic.List[string]
$originalConfig = $null; $preExistingAliases = @(); $recoveryStatePath = $null; $asadmin = $null
$dbHost = $null; $dbPort = $null; $dbName = $null; $dbUser = $null; $dbPassword = $null

function Invoke-Asadmin {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    # Permit native stderr to be captured without prematurely
    # terminating the script under Windows PowerShell 5.1.
    $previousErrorActionPreference = $ErrorActionPreference

    try {
        $ErrorActionPreference = 'Continue'

        $output = & $script:asadmin @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "Payara command '$($Arguments[0])' failed. Inspect local Payara logs; output is suppressed to avoid disclosing configuration values."
    }

    [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { $_.ToString() })
    }
}
function Invoke-Database {
    param([Parameter(Mandatory)][string]$Sql)

    $hadPassword = Test-Path Env:PGPASSWORD
    $previousPassword = $env:PGPASSWORD
    $previousErrorActionPreference = $ErrorActionPreference

    try {
        $env:PGPASSWORD = $script:dbPassword
        # Native tools may write diagnostic text to stderr on expected failures.
        # Always check their exit code; never return diagnostic output containing credentials.
        $ErrorActionPreference = 'Continue'
        $output = & $PsqlPath -X -A -t -v ON_ERROR_STOP=1 `
            -h $script:dbHost -p $script:dbPort -U $script:dbUser `
            -d $script:dbName -c $Sql 2>&1
        $exitCode = $LASTEXITCODE

        if ($exitCode -ne 0) {
            throw 'Database command failed. Inspect local PostgreSQL logs; diagnostic output is suppressed.'
        }

        @($output | ForEach-Object { $_.ToString() })
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        if ($hadPassword) {
            $env:PGPASSWORD = $previousPassword
        }
        else {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
    }
}
function Protect-CurrentUserFile {
    param([Parameter(Mandatory)][string]$Path)
    & icacls.exe $Path /inheritance:r /grant:r "$env:USERNAME`:F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to restrict permissions on '$Path'." }
}
function Get-ConfigPropertyState {
    param([Parameter(Mandatory)][string]$Name)

    $result = Invoke-Asadmin @(
        'get-config-property',
        "--propertyName=$Name",
        '--source=domain'
    ) -AllowFailure

    if ($result.ExitCode -eq 0) {
        # Payara 6 returns the bare property value on a separate line.
        # Other versions may return propertyName=value. Exclude only the
        # specific success footer and reject ambiguous/extra output.
        $valueLines = @(
            $result.Output |
                ForEach-Object { ([string]$_).Trim() } |
                Where-Object {
                    $_ -ne '' -and
                    $_ -notmatch '^Command get-config-property executed successfully\.?$'
                }
        )

        if ($valueLines.Count -ne 1) {
            throw "Could not unambiguously read existing '$Name' configuration."
        }

        $value = $valueLines[0]
        $prefix = "$Name="
        if ($value.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
            $value = $value.Substring($prefix.Length)
        }
        elseif ($value.Contains('=')) {
            # An unrelated name=value line must never be mistaken for a value.
            throw "Unexpected configuration output for '$Name'."
        }

        if ([string]::IsNullOrWhiteSpace($value) -or
            $value -notmatch '^[A-Za-z0-9._-]+$') {
            throw "Unexpected or empty key alias value for '$Name'."
        }

        return [pscustomobject]@{ Exists = $true; Value = $value }
    }

    if (($result.Output -join "`n") -match '(?i)(not found|does not exist|no such)') {
        return [pscustomobject]@{ Exists = $false; Value = $null }
    }

    throw "Could not determine whether '$Name' exists; refusing configuration mutation."
}
function Get-PasswordAliases { @( (Invoke-Asadmin @('list-password-aliases')).Output | Where-Object { $_ -match '^[A-Za-z0-9._-]+$' } ) }
function Test-PasswordAlias { param([Parameter(Mandatory)][string]$Name); return @(Get-PasswordAliases) -contains $Name }
function Assert-TemporaryAliasesExist {
    param([Parameter(Mandatory)]$State)

    foreach ($alias in @($State.TemporaryAliases)) {
        if (-not (Test-PasswordAlias $alias)) {
            throw "Required temporary password alias '$alias' was not found after the manual restart."
        }
    }
}
function Assert-Preflight {
    param([switch]$SkipDeploymentCheck)
    if (-not (Test-Path -LiteralPath $PayaraHome -PathType Container)) { throw "Payara home was not found: $PayaraHome" }
    $script:asadmin = Join-Path $PayaraHome 'bin\asadmin.bat'; $domainXml = Join-Path $PayaraHome "glassfish\domains\$DomainName\config\domain.xml"
    if (-not (Test-Path -LiteralPath $script:asadmin -PathType Leaf)) { throw "Payara asadmin was not found: $script:asadmin" }
    if (-not (Test-Path -LiteralPath $domainXml -PathType Leaf)) { throw "Payara domain '$DomainName' was not found under the supplied Payara home." }
    if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) { throw "psql was not found: $PsqlPath" }
    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe is required but was not found.' }
    if (-not (Get-Command icacls.exe -ErrorAction SilentlyContinue)) { throw 'icacls.exe is required to protect temporary secret files.' }
    [xml]$domain = Get-Content -LiteralPath $domainXml -Raw
    $resource = @($domain.SelectNodes("//*[local-name()='jdbc-resource']") | Where-Object { $_.GetAttribute('jndi-name') -eq $expectedJndi })
    if ($resource.Count -ne 1) { throw "Expected exactly one '$expectedJndi' datasource in local domain '$DomainName'." }
    $poolName = $resource[0].GetAttribute('pool-name'); $pool = @($domain.SelectNodes("//*[local-name()='jdbc-connection-pool']") | Where-Object { $_.GetAttribute('name') -eq $poolName })
    if ($pool.Count -ne 1) { throw "Datasource '$expectedJndi' does not resolve to exactly one connection pool." }
    $properties = @{}; foreach ($property in @($pool[0].SelectNodes("./*[local-name()='property']"))) { $properties[$property.GetAttribute('name')] = $property.GetAttribute('value') }
    $url = if ($properties.ContainsKey('URL')) { $properties['URL'] } else { $properties['url'] }
    if ([string]::IsNullOrWhiteSpace($url) -or $url -notmatch '^jdbc:postgresql://(?<host>[^:/]+):(?<port>\d+)/(?<database>[^?]+)') { throw 'Datasource JDBC URL is unsupported; refusing to infer a target.' }
    $script:dbHost = $Matches.host; $script:dbPort = $Matches.port; $script:dbName = $Matches.database
    if ($script:dbName -ne $expectedDatabase) { throw "Refusing database '$script:dbName'; expected '$expectedDatabase'." }
    if ($script:dbHost -notin @('localhost', '127.0.0.1', '::1')) { throw "Refusing non-local database host '$script:dbHost'." }
    if ([string]::IsNullOrWhiteSpace($properties['user']) -or -not $properties.ContainsKey('password')) { throw 'Expected datasource credentials are incomplete; refusing to continue.' }
    $script:dbUser = $properties['user']; $script:dbPassword = $properties['password']
    if (-not $SkipDeploymentCheck) {
        $applications = Invoke-Asadmin @('list-applications', 'server')
        if ((@($applications.Output | Where-Object { $_ -match "(?<![A-Za-z0-9_.-])$([regex]::Escape($deploymentName))(?![A-Za-z0-9_.-])" })).Count -lt 1) { throw "Expected deployed application '$deploymentName' was not found. Deploy it separately with explicit approval; this script never redeploys applications." }
    }
}
function Assert-OperatorConfirmation { if (-not $ConfirmLocalDevelopmentMutation) { throw 'Refusing to mutate local development without explicit confirmation. Re-run with -ConfirmLocalDevelopmentMutation after reviewing target and cleanup behavior.' } }
function Assert-PayaraHttpAvailable {
    $response = [IO.Path]::GetTempFileName()
    $script:temporaryFiles.Add($response)
    $previousErrorActionPreference = $ErrorActionPreference

    try {
        $ErrorActionPreference = 'Continue'
        $statusCode = & curl.exe -k -sS -o $response -w '%{http_code}' $healthUri
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw 'Payara did not accept the post-restart HTTPS health check.'
    }

    if ([int]$statusCode -ne 200) {
        throw "Payara health check returned HTTP $statusCode after the manual restart."
    }
}
function Invoke-ManualRestartCheckpoint {
    param(
        [Parameter(Mandatory)]$State,
        [switch]$RequireRestart
    )

    if (-not $RequireRestart) {
        return
    }

    Write-Host ''
    Write-Warning 'Manual Payara restart checkpoint reached. No Energy Streams HTTP request has been sent.'
    Write-Host "In a separate terminal, restart Payara domain '$DomainName':"
    Write-Host "  & '$script:asadmin' stop-domain '$DomainName'"
    Write-Host "  & '$script:asadmin' start-domain '$DomainName'"
    Write-Host 'Wait for the domain to finish starting. This script will not restart Payara automatically.'

    $confirmation = Read-Host 'Type RESTARTED after the manual restart is complete, or press Enter to fail safely and begin cleanup'
    if ($confirmation -cne 'RESTARTED') {
        throw 'Manual Payara restart was not explicitly confirmed. No Energy Streams HTTP request will be sent.'
    }

    Write-Host 'Validating the restarted domain, deployed SmartCity360 application, temporary aliases, and HTTPS health endpoint.'
    Assert-Preflight
    Assert-TemporaryAliasesExist -State $State
    Assert-PayaraHttpAvailable
    Write-Host 'Manual Payara restart checkpoint passed.'
}
function Save-RecoveryState {
    param([Parameter(Mandatory)]$State)

    $path = Join-Path ([IO.Path]::GetTempPath()) ("smartcity360-energy-stream-$($State.RunId).recovery")
    if (Test-Path -LiteralPath $path) {
        throw 'Unexpected recovery-state filename collision; refusing to overwrite it.'
    }

    $plain = [Text.Encoding]::UTF8.GetBytes(($State | ConvertTo-Json -Depth 5 -Compress))
    $entropy = [Text.Encoding]::UTF8.GetBytes('SmartCity360.EnergyStreamIntegrationRecovery.v1')
    try {
        $protected = [System.Security.Cryptography.ProtectedData]::Protect(
            $plain, $entropy, [System.Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        # Create an empty recovery file and restrict access before writing ciphertext.
        [IO.File]::WriteAllBytes($path, [byte[]]@())
        Protect-CurrentUserFile $path
        [IO.File]::WriteAllText(
            $path,
            [Convert]::ToBase64String($protected),
            [Text.UTF8Encoding]::new($false)
        )
        return $path
    }
    finally {
        [Array]::Clear($plain, 0, $plain.Length)
        if ($protected) {
            [Array]::Clear($protected, 0, $protected.Length)
        }
    }
}
function Read-RecoveryState {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Recovery state file was not found: $Path"
    }

    $entropy = [Text.Encoding]::UTF8.GetBytes('SmartCity360.EnergyStreamIntegrationRecovery.v1')
    $plain = $null
    try {
        $cipher = [Convert]::FromBase64String((Get-Content -LiteralPath $Path -Raw).Trim())
        $plain = [System.Security.Cryptography.ProtectedData]::Unprotect(
            $cipher, $entropy, [System.Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        return ([Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json)
    }
    finally {
        if ($plain) {
            [Array]::Clear($plain, 0, $plain.Length)
        }
    }
}
function Create-TestAlias {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][byte[]]$Bytes
    )

    # The recovery state already contains the exact temporary alias names.
    # No empty CreatedAliases collection needs to cross a mandatory parameter boundary.
    if ($script:preExistingAliases -contains $Name -or (Test-PasswordAlias $Name)) {
        throw "Refusing to overwrite pre-existing password alias '$Name'."
    }

    $passwordFile = [IO.Path]::GetTempFileName()
    $script:temporaryFiles.Add($passwordFile)
    Protect-CurrentUserFile $passwordFile
    [IO.File]::WriteAllText(
        $passwordFile,
        "AS_ADMIN_ALIASPASSWORD=$([Convert]::ToBase64String($Bytes))`n",
        [Text.UTF8Encoding]::new($false)
    )

    try {
        $result = Invoke-Asadmin @('--passwordfile', $passwordFile, 'create-password-alias', $Name) -AllowFailure
        if ($result.ExitCode -ne 0) {
            throw "Password alias creation failed for '$Name'. Recovery cleanup will check whether it was created."
        }
    }
    finally {
        # Do not leave the password file on disk for the rest of the integration run.
        Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
    }
}
function Restore-OriginalConfigProperty {
    param([Parameter(Mandatory)]$State)

    $current = Get-ConfigPropertyState $energyProperty
    $original = $State.OriginalConfig
    $expectedTemporaryValue = @($State.TemporaryAliases)[1]

    # Never overwrite an unrelated configuration change made during the test.
    if ($current.Exists -and
        $current.Value -cne $expectedTemporaryValue -and
        (-not $original.Exists -or $current.Value -cne $original.Value)) {
        throw 'AES configuration changed to an unexpected value; refusing to overwrite it.'
    }

    if ($original.Exists) {
        if (-not $current.Exists -or $current.Value -cne $original.Value) {
            Invoke-Asadmin @(
                'set-config-property',
                "--propertyName=$energyProperty",
                "--propertyValue=$($original.Value)",
                '--source=domain'
            ) | Out-Null
        }
    }
    elseif ($current.Exists) {
        Invoke-Asadmin @(
            'delete-config-property',
            "--propertyName=$energyProperty",
            '--source=domain'
        ) | Out-Null
    }
}
function Assert-RecoveryStateIdentifiers {
    param([Parameter(Mandatory)]$State)

    $parsedId = [guid]::Empty
    if ([string]$State.RunId -notmatch '^[a-fA-F0-9]{32}$') {
        throw 'Recovery state has an invalid run identifier.'
    }
    if (-not [guid]::TryParse([string]$State.MeterId, [ref]$parsedId)) {
        throw 'Recovery state has an invalid meter identifier.'
    }
    $zones = @($State.ZoneIds)
    if ($zones.Count -ne 4 -or @($zones | Select-Object -Unique).Count -ne 4) {
        throw 'Recovery state must contain exactly four distinct zone identifiers.'
    }
    foreach ($zoneId in $zones) {
        if (-not [guid]::TryParse([string]$zoneId, [ref]$parsedId)) {
            throw 'Recovery state contains an invalid zone identifier.'
        }
    }
    $expectedAliases = @(
        "energy-stream-it.$($State.RunId).meter.v1",
        "energy-stream-it.$($State.RunId).aes.v1"
    )
    $aliases = @($State.TemporaryAliases)
    if ($aliases.Count -ne 2 -or
        $aliases[0] -cne $expectedAliases[0] -or
        $aliases[1] -cne $expectedAliases[1]) {
        throw 'Recovery state has unexpected temporary password alias identifiers.'
    }
    if ($null -eq $State.OriginalConfig -or $null -eq $State.OriginalConfig.Exists) {
        throw 'Recovery state is missing its original AES configuration snapshot.'
    }
    if ($State.OriginalConfig.Exists -and [string]::IsNullOrWhiteSpace([string]$State.OriginalConfig.Value)) {
        throw 'Recovery state has an invalid original AES configuration value.'
    }
}

function Remove-TestDatabaseRecords {
    param([Parameter(Mandatory)]$State)
    $zoneList = (($State.ZoneIds | ForEach-Object { "'$_'" }) -join ',')
    Invoke-Database "BEGIN;DELETE FROM energy_stream_runs WHERE meter_id='$($State.MeterId)';DELETE FROM energy_readings WHERE meter_id='$($State.MeterId)';DELETE FROM energy_meters WHERE id='$($State.MeterId)';DELETE FROM zones WHERE id IN ($zoneList);COMMIT;" | Out-Null
}
function Remove-TestAliases {
    param([Parameter(Mandatory)]$State)

    # Do not delete a key still referenced by the live AES configuration.
    $current = Get-ConfigPropertyState $energyProperty
    if ($current.Exists -and @($State.TemporaryAliases) -ccontains $current.Value) {
        throw 'AES configuration still references a test alias; refusing alias deletion.'
    }

    foreach ($alias in @($State.TemporaryAliases)) {
        if (@($State.PreExistingAliases) -contains $alias) {
            throw "Refusing to delete alias '$alias' because it existed before this test."
        }
        if (Test-PasswordAlias $alias) {
            Invoke-Asadmin @('delete-password-alias', $alias) | Out-Null
        }
    }
}
function Verify-Cleanup {
    param([Parameter(Mandatory)]$State)
    $zoneList = (($State.ZoneIds | ForEach-Object { "'$_'" }) -join ',')
    $remaining = (Invoke-Database "SELECT (SELECT count(*) FROM energy_stream_runs WHERE meter_id='$($State.MeterId)')||'|'||(SELECT count(*) FROM energy_readings WHERE meter_id='$($State.MeterId)')||'|'||(SELECT count(*) FROM energy_meters WHERE id='$($State.MeterId)')||'|'||(SELECT count(*) FROM zones WHERE id IN ($zoneList));" | Where-Object { $_ -match '^\d+\|\d+\|\d+\|\d+$' } | Select-Object -First 1).Trim()
    if ($remaining -ne '0|0|0|0') { throw "Database cleanup verification failed (remaining test records: $remaining)." }
    foreach ($alias in @($State.TemporaryAliases)) { if (Test-PasswordAlias $alias) { throw "Temporary alias '$alias' remains after cleanup." } }
    $current = Get-ConfigPropertyState $energyProperty
    if ([bool]$current.Exists -ne [bool]$State.OriginalConfig.Exists -or ($current.Exists -and $current.Value -cne $State.OriginalConfig.Value)) { throw "Original '$energyProperty' configuration was not restored exactly." }
}
function Invoke-Cleanup {
    param([Parameter(Mandatory)]$State)
    foreach ($step in @(@{ Name = 'database records'; Action = { Remove-TestDatabaseRecords $State } }, @{ Name = 'MicroProfile configuration'; Action = { Restore-OriginalConfigProperty $State } }, @{ Name = 'temporary password aliases'; Action = { Remove-TestAliases $State } }, @{ Name = 'cleanup verification'; Action = { Verify-Cleanup $State } })) {
        try { & $step.Action } catch { $script:cleanupFailures.Add("$($step.Name): $($_.Exception.Message)") }
    }
}
function Write-RecoveryInstructions {
    param([Parameter(Mandatory)][string]$Path)
    Write-Warning "Cleanup was incomplete. No credentials or keys were printed. Preserve protected recovery state: $Path"
    Write-Warning "After resolving the local Payara/PostgreSQL issue, run this script with -RecoverStateFile '$Path' -ConfirmLocalDevelopmentMutation. Recovery is UUID-scoped and restores saved configuration."
}
function WriteInt([IO.Stream]$Stream, [int]$Value) { $bytes = [BitConverter]::GetBytes([Net.IPAddress]::HostToNetworkOrder($Value)); $Stream.Write($bytes, 0, $bytes.Length) }
function WriteLong([IO.Stream]$Stream, [long]$Value) { $bytes = [BitConverter]::GetBytes([Net.IPAddress]::HostToNetworkOrder($Value)); $Stream.Write($bytes, 0, $bytes.Length) }
function WriteText([IO.Stream]$Stream, [string]$Value) { $bytes = [Text.Encoding]::UTF8.GetBytes($Value); WriteInt $Stream $bytes.Length; $Stream.Write($bytes, 0, $bytes.Length) }
function New-Signature {
    param([byte[]]$Payload, [string]$MeterCode, [byte[]]$MeterKey, [string]$Nonce, [DateTimeOffset]$Time)
    $stream = [IO.MemoryStream]::new()
    try {
        WriteText $stream 'SC360-HMAC-1'; WriteText $stream $MeterCode; WriteInt $stream 1; WriteLong $stream $Time.ToUnixTimeMilliseconds(); WriteText $stream $Nonce; WriteText $stream 'POST'; WriteText $stream '/api/v1/energy/streams/process'
        $sha = [Security.Cryptography.SHA256]::Create(); try { $digest = $sha.ComputeHash($Payload) } finally { $sha.Dispose() }; WriteInt $stream $digest.Length; $stream.Write($digest, 0, $digest.Length)
        $hmac = [Security.Cryptography.HMACSHA256]::new($MeterKey); try { return [Convert]::ToBase64String($hmac.ComputeHash($stream.ToArray())) } finally { $hmac.Dispose() }
    } finally { $stream.Dispose() }
}
function Invoke-HttpJson {
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$Json,
        [hashtable]$Headers = @{},
        [Parameter(Mandatory)][ValidateSet('GET', 'POST')][string]$Method,
        [switch]$Cookie,
        [string]$CookieJar,
        [switch]$ApiRoot,
        [switch]$SensitiveBody
    )

    $response = [IO.Path]::GetTempFileName()
    $script:temporaryFiles.Add($response)
    $arguments = @('-k', '-sS', '-X', $Method, '-o', $response, '-w', '%{http_code}')
    foreach ($headerName in $Headers.Keys) {
        $arguments += @('-H', "$headerName`: $($Headers[$headerName])")
    }
    if ($Cookie) {
        $arguments += @('-H', 'Cookie: JSESSIONID=invalid')
    }
    if (-not [string]::IsNullOrWhiteSpace($CookieJar)) {
        $arguments += @('--cookie', $CookieJar, '--cookie-jar', $CookieJar)
    }

    $body = $null
    try {
        if ($null -ne $Json) {
            $body = [IO.Path]::GetTempFileName()
            if ($SensitiveBody) {
                Protect-CurrentUserFile $body
            }
            [IO.File]::WriteAllText($body, $Json, [Text.UTF8Encoding]::new($false))
            $arguments += @('-H', 'Content-Type: application/json', '--data-binary', "@$body")
        }

        $requestBaseUri = if ($ApiRoot) { $script:apiBaseUri } else { $script:baseUri }
        $requestUri = "$requestBaseUri$Path"
        $code = & curl.exe @arguments $requestUri
        if ($LASTEXITCODE -ne 0) {
            throw "HTTPS request to '$Path' could not be completed."
        }
        return [pscustomobject]@{
            Code = [int]$code
            Body = (Get-Content -LiteralPath $response -Raw)
        }
    }
    finally {
        if ($body) {
            Remove-Item -LiteralPath $body -Force -ErrorAction SilentlyContinue
        }
    }
}

function Initialize-AuthenticatedSession {
    param(
        [Parameter(Mandatory)][System.Management.Automation.PSCredential]$Credential,
        [Parameter(Mandatory)][string]$CookieJar,
        [Parameter(Mandatory)][string]$BaseUri
    )

    $csrfResponse = Invoke-HttpJson '/auth/csrf' $null @{} -Method GET -CookieJar $CookieJar -ApiRoot
    if ($csrfResponse.Code -ne 200) {
        throw "Could not obtain a CSRF token for the verification session (HTTP $($csrfResponse.Code))."
    }

    try {
        $csrfToken = ($csrfResponse.Body | ConvertFrom-Json).token
    }
    catch {
        throw 'The CSRF endpoint returned an invalid response.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$csrfToken)) {
        throw 'The CSRF endpoint response did not contain a token.'
    }

    $base = [Uri]$BaseUri
    $headers = @{
        'X-CSRF-Token' = [string]$csrfToken
        Origin = "$($base.Scheme)://$($base.Authority)"
    }
    $password = $null
    $loginJson = $null
    try {
        $password = $Credential.GetNetworkCredential().Password
        $loginJson = @{
            email = $Credential.UserName
            password = $password
        } | ConvertTo-Json -Compress
        $loginResponse = Invoke-HttpJson '/auth/login' $loginJson $headers -Method POST -CookieJar $CookieJar -ApiRoot -SensitiveBody
    }
    finally {
        $password = $null
        $loginJson = $null
    }

    if ($loginResponse.Code -eq 401) {
        throw 'Authentication failed for the supplied integration-test account (HTTP 401).'
    }
    if ($loginResponse.Code -eq 403) {
        throw 'Login was rejected by CSRF protection (HTTP 403).'
    }
    if ($loginResponse.Code -ne 200) {
        throw "Login for the verification session returned HTTP $($loginResponse.Code)."
    }

    $identityResponse = Invoke-HttpJson '/auth/me' $null @{} -Method GET -CookieJar $CookieJar -ApiRoot
    if ($identityResponse.Code -eq 401) {
        throw 'The verification session is not authenticated (HTTP 401).'
    }
    if ($identityResponse.Code -eq 403) {
        throw 'The verification account is not authorized (HTTP 403).'
    }
    if ($identityResponse.Code -ne 200) {
        throw "The verification account identity check returned HTTP $($identityResponse.Code)."
    }
    try {
        $roles = @(($identityResponse.Body | ConvertFrom-Json).roles)
    }
    catch {
        throw 'The authenticated identity endpoint returned an invalid response.'
    }
    if ($roles -notcontains 'ADMIN' -and $roles -notcontains 'CITY_MANAGER') {
        throw 'The authenticated account must have the ADMIN or CITY_MANAGER role for protected-envelope verification.'
    }

    return [pscustomobject]@{
        CookieJar = $CookieJar
        Headers = $headers
    }
}

function Expand-GzipPayload {
    param(
        [Parameter(Mandatory)][byte[]]$CompressedPayload,
        [int]$MaximumBytes = 1048576
    )

    if ($CompressedPayload.Length -gt $MaximumBytes) {
        throw 'Protected payload exceeds the compressed size limit.'
    }

    $input = [IO.MemoryStream]::new($CompressedPayload)
    $gzip = $null
    $output = [IO.MemoryStream]::new()
    try {
        $gzip = [IO.Compression.GZipStream]::new(
            $input,
            [IO.Compression.CompressionMode]::Decompress
        )
        $buffer = [byte[]]::new(4096)
        while (($read = $gzip.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($output.Length + $read -gt $MaximumBytes) {
                throw 'Protected payload exceeds the decompressed size limit.'
            }
            $output.Write($buffer, 0, $read)
        }
        $result = $output.ToArray()
        return ,$result
    }
    catch {
        if ($_.Exception.Message -like 'Protected payload exceeds*') {
            throw
        }
        throw 'The decrypted protected payload is not valid GZIP data.'
    }
    finally {
        if ($gzip) { $gzip.Dispose() }
        $input.Dispose()
        $output.Dispose()
    }
}

function Assert-ProtectedEnvelopeResponse {
    param(
        [Parameter(Mandatory)]$Response,
        [Parameter(Mandatory)][byte[]]$ExpectedPayload
    )

    if ($Response.Code -eq 401) {
        throw 'Protected-envelope verification was unauthenticated (HTTP 401).'
    }
    if ($Response.Code -eq 403) {
        throw 'Protected-envelope verification was forbidden (HTTP 403); confirm the account has ADMIN or CITY_MANAGER and the CSRF token/origin is valid.'
    }
    if ($Response.Code -ne 200) {
        throw "Protected-envelope verification returned HTTP $($Response.Code)."
    }

    try {
        $responseJson = $Response.Body | ConvertFrom-Json
    }
    catch {
        throw 'Protected-envelope verification returned invalid JSON.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$responseJson.payloadBase64)) {
        throw 'Protected-envelope verification response did not contain payloadBase64.'
    }

    try {
        $decryptedPayload = [Convert]::FromBase64String($responseJson.payloadBase64)
    }
    catch {
        throw 'Protected-envelope verification returned invalid Base64 payload data.'
    }
    $plainPayload = Expand-GzipPayload $decryptedPayload
    if ($plainPayload.Length -ne $ExpectedPayload.Length) {
        throw 'Protected-envelope verification payload mismatch after GZIP decompression.'
    }
    for ($index = 0; $index -lt $ExpectedPayload.Length; $index++) {
        if ($plainPayload[$index] -ne $ExpectedPayload[$index]) {
            throw 'Protected-envelope verification payload mismatch after GZIP decompression.'
        }
    }
}

if ($RecoverStateFile) {
    Assert-Preflight -SkipDeploymentCheck; Assert-OperatorConfirmation; $state = Read-RecoveryState $RecoverStateFile
    if ($state.DatabaseName -ne $dbName -or $state.DomainName -ne $DomainName) { throw 'Recovery state does not match the explicitly preflighted local target.' }
    Assert-RecoveryStateIdentifiers $state
    Invoke-Cleanup $state
    if ($cleanupFailures.Count -gt 0) { Write-RecoveryInstructions $RecoverStateFile; throw ('Recovery cleanup failed: ' + ($cleanupFailures -join '; ')) }
    Remove-Item -LiteralPath $RecoverStateFile -Force; 'Energy stream integration recovery: PASS'; return
}

$runId = [guid]::NewGuid().ToString('N'); $meterAlias = "energy-stream-it.$runId.meter.v1"; $aesAlias = "energy-stream-it.$runId.aes.v1"; $meterCode = "ESIT-$runId"
$zoneIds = 1..4 | ForEach-Object { [guid]::NewGuid().ToString() }; $meterId = [guid]::NewGuid().ToString()
$meterKey = [byte[]]::new(32)
$aesKey = [byte[]]::new(32)
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$mainFailure = $null
try {
    Assert-Preflight
    $originalConfig = Get-ConfigPropertyState $energyProperty
    $preExistingAliases = @(Get-PasswordAliases)
    Assert-OperatorConfirmation
    $state = [pscustomobject]@{ RunId = $runId; DomainName = $DomainName; DatabaseName = $dbName; MeterId = $meterId; ZoneIds = @($zoneIds); TemporaryAliases = @($meterAlias, $aesAlias); PreExistingAliases = @($preExistingAliases); OriginalConfig = $originalConfig }
    Assert-RecoveryStateIdentifiers $state
    $recoveryStatePath = Save-RecoveryState $state
    $rng.GetBytes($meterKey)
    $rng.GetBytes($aesKey)
    Create-TestAlias $meterAlias $meterKey
    Create-TestAlias $aesAlias $aesKey
    [Array]::Clear($aesKey, 0, $aesKey.Length)
    Invoke-Asadmin @('set-config-property', "--propertyName=$energyProperty", "--propertyValue=$aesAlias", '--source=domain') | Out-Null
    $sql = "BEGIN;INSERT INTO zones(id,zone_type,code,name)VALUES('$($zoneIds[0])','CITY','ESIT-C-$runId','Energy Stream Test City');INSERT INTO zones(id,parent_zone_id,zone_type,code,name)VALUES('$($zoneIds[1])','$($zoneIds[0])','DISTRICT','ESIT-D-$runId','Energy Stream Test District');INSERT INTO zones(id,parent_zone_id,zone_type,code,name)VALUES('$($zoneIds[2])','$($zoneIds[1])','STREET','ESIT-S-$runId','Energy Stream Test Street');INSERT INTO zones(id,parent_zone_id,zone_type,code,name)VALUES('$($zoneIds[3])','$($zoneIds[2])','BUILDING','ESIT-B-$runId','Energy Stream Test Building');INSERT INTO energy_meters(id,building_zone_id,meter_code,credential_key_id,credential_key_version)VALUES('$meterId','$($zoneIds[3])','$meterCode','$meterAlias',1);COMMIT;"; Invoke-Database $sql | Out-Null
    Invoke-ManualRestartCheckpoint -State $state -RequireRestart:$RequireManualRestart

    $payload = [Text.Encoding]::UTF8.GetBytes(
        '{"periodStart":"2026-11-01T03:00:00Z","periodEnd":"2026-11-01T03:15:00Z","consumptionKwh":"1.2500"}'
    )
    $requestTime = [DateTimeOffset]::UtcNow
    $nonce = "nonce-$([guid]::NewGuid().ToString('N'))"
    $signature = New-Signature $payload $meterCode $meterKey $nonce $requestTime
    $headers = @{
        'X-Meter-Id' = $meterCode
        'X-Credential-Version' = '1'
        'X-Request-Timestamp' = $requestTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        'X-Request-Nonce' = $nonce
        'X-Meter-Signature' = $signature
    }
    $processJson = @{
        payloadBase64 = [Convert]::ToBase64String($payload)
        layers = @('AUTHENTICATION', 'COMPRESSION', 'ENCRYPTION')
    } | ConvertTo-Json -Compress
    $ok = Invoke-HttpJson '/process' $processJson $headers -Method POST
    if ($ok.Code -ne 200) {
        $failureCode = "NOT_RECORDED"

        try {
            $recordedCodes = @(
                Invoke-Database "SELECT error_code FROM energy_stream_runs WHERE meter_id='$meterId' AND status='FAILED' ORDER BY executed_at DESC LIMIT 1;" |
                    Where-Object { $_ -match '^[A-Z0-9_]+$' }
            )

            if ($recordedCodes.Count -gt 0) {
                $failureCode = $recordedCodes[0]
            }
        }
        catch {
            $failureCode = "DIAGNOSTIC_QUERY_FAILED"
        }

        throw "Valid HMAC request failed: HTTP $($ok.Code); recorded error: $failureCode"
    } $okJson = $ok.Body | ConvertFrom-Json
    if (($okJson.layers -join ',') -ne 'RAW,AUTHENTICATION,COMPRESSION,ENCRYPTION') { throw 'Runtime decorator order mismatch.' }
    if ([int]$okJson.inputBytes -ne $payload.Length -or [int]$okJson.outputBytes -le 0 -or [string]::IsNullOrWhiteSpace($okJson.runId) -or [string]::IsNullOrWhiteSpace($okJson.protectedPayloadBase64)) { throw 'Successful processing metadata was incomplete or incorrect.' }
    if ($ok.Body -match '(?i)(secret|credential|password)') { throw 'Sensitive response field detected.' }

    $authCredential = Get-Credential -Message 'Enter an existing ADMIN or CITY_MANAGER account for protected-envelope verification.'
    if ($null -eq $authCredential) {
        throw 'An ADMIN or CITY_MANAGER credential is required for protected-envelope verification.'
    }
    $cookieJar = [IO.Path]::GetTempFileName()
    $temporaryFiles.Add($cookieJar)
    Protect-CurrentUserFile $cookieJar
    try {
        $session = Initialize-AuthenticatedSession $authCredential $cookieJar $apiBaseUri
    }
    finally {
        $authCredential.Password.Dispose()
        $authCredential = $null
    }

    $verifyJson = @{
        envelopeBase64 = $okJson.protectedPayloadBase64
    } | ConvertTo-Json -Compress
    $verified = Invoke-HttpJson '/verify' $verifyJson $session.Headers -Method POST -CookieJar $session.CookieJar
    Assert-ProtectedEnvelopeResponse $verified $payload

    $historyItem = Invoke-HttpJson "/runs/$($okJson.runId)" $null @{} -Method GET -CookieJar $session.CookieJar
    if ($historyItem.Code -ne 200 -or (($historyItem.Body | ConvertFrom-Json).id.ToString() -ne $okJson.runId.ToString())) { throw 'Processing history detail endpoint did not return successful run.' }
    $replay = Invoke-HttpJson '/process' $processJson $headers -Method POST; if ($replay.Code -ne 401) { throw "Replay was not rejected: HTTP $($replay.Code)." }
    $tamperedHeaders = $headers.Clone(); $tamperedHeaders['X-Request-Nonce'] = "nonce-tamper-$runId"; $tamperedJson = @{ payloadBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('tampered')); layers = @('AUTHENTICATION', 'COMPRESSION', 'ENCRYPTION') } | ConvertTo-Json -Compress; $tampered = Invoke-HttpJson '/process' $tamperedJson $tamperedHeaders -Method POST
    if ($tampered.Code -ne 401) { throw "Tampering was not rejected: HTTP $($tampered.Code)." }
    $browserHeaders = $headers.Clone(); $browserHeaders['X-Request-Nonce'] = "nonce-browser-$runId"; $browser = Invoke-HttpJson '/process' $processJson $browserHeaders -Method POST -Cookie
    if ($browser.Code -ne 403) { throw "Cookie-bearing machine request bypassed CSRF: HTTP $($browser.Code)." }
    $history = (Invoke-Database "SELECT count(*)||'|'||count(*)FILTER(WHERE status='SUCCESS')||'|'||count(*)FILTER(WHERE status='FAILED')||'|'||bool_and(jsonb_typeof(decorator_chain)='array') FROM energy_stream_runs WHERE meter_id='$meterId';" | Where-Object { $_ -match '^\d+\|\d+\|\d+\|(true|false)$' } | Select-Object -First 1).Trim()
    if ($history -notmatch '^3\|1\|2\|true$') { throw "Unexpected processing history for this invocation: $history" }
    # Report PASS only after the finally block has verified cleanup.
} catch { $mainFailure = $_ }
finally {
    try { if ($rng) { $rng.Dispose() } } catch {}
    if ($recoveryStatePath) { Invoke-Cleanup $state; if ($cleanupFailures.Count -eq 0) { Remove-Item -LiteralPath $recoveryStatePath -Force -ErrorAction SilentlyContinue } else { Write-RecoveryInstructions $recoveryStatePath } }
    foreach ($file in $temporaryFiles) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
    if ($authCredential -and $authCredential.Password) { $authCredential.Password.Dispose() }
    [Array]::Clear($meterKey, 0, $meterKey.Length); [Array]::Clear($aesKey, 0, $aesKey.Length); $dbPassword = $null
}
if ($mainFailure) {
    throw $mainFailure
}
if ($cleanupFailures.Count -gt 0) {
    throw ('Cleanup failed: ' + ($cleanupFailures -join '; '))
}
'Energy stream runtime: PASS (valid HMAC, authentication/compression/encryption, protected-envelope verification, replay rejection, tamper rejection, and CSRF boundary)'
'Processing persistence: PASS (run metadata, history detail endpoint, and invocation-scoped JPA/JSONB history)'
'Invocation-scoped cleanup: PASS (database records, temporary aliases, and original AES configuration restored)'
