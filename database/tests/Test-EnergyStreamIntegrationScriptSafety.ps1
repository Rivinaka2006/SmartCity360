[CmdletBinding()]
param(
    [string]$ScriptPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ScriptPath)) {
    $ScriptPath = Join-Path $PSScriptRoot 'Invoke-EnergyStreamIntegrationTests.ps1'
}

if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
    throw "Integration script was not found: $ScriptPath"
}

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path -LiteralPath $ScriptPath), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) {
    throw ('PowerShell parse errors:' + [Environment]::NewLine + ($parseErrors | ForEach-Object Message | Out-String))
}

$text = Get-Content -LiteralPath $ScriptPath -Raw
$requiredMarkers = @(
    'ConfirmLocalDevelopmentMutation',
    'Assert-Preflight',
    'Get-ConfigPropertyState',
    'Restore-OriginalConfigProperty',
    'RecoverStateFile',
    'Verify-Cleanup',
    'RequireManualRestart',
    'Invoke-ManualRestartCheckpoint',
    'list-password-aliases',
    'get-config-property',
    'Initialize-AuthenticatedSession',
    'Assert-ProtectedEnvelopeResponse',
    'Expand-GzipPayload',
    '/auth/csrf',
    '/auth/login',
    "-X', `$Method",
    'X-CSRF-Token',
    '--cookie-jar',
    '$apiBaseUri',
    '-SensitiveBody'
)
foreach ($marker in $requiredMarkers) {
    if ($text -notmatch [regex]::Escape($marker)) {
        throw "Missing required safety control: $marker"
    }
}

if (-not $text.Contains("Invoke-HttpJson '/verify' `$verifyJson `$session.Headers -Method POST -CookieJar `$session.CookieJar")) {
    throw 'Protected-envelope verification must POST to the Energy Streams verify URL with the authenticated session headers and cookie jar.'
}

if ($text -match '(?im)^\s*&\s*\$asadmin\s+redeploy\b') {
    throw 'The integration script must not redeploy the application automatically.'
}

if ($text -match "Invoke-Asadmin\s+@\('list-applications',\s*'--target'") {
    throw 'Payara list-applications accepts the target as a positional operand, not --target.'
}

if ($text -notmatch "Invoke-Asadmin\s+@\('list-applications',\s*'server'\)") {
    throw 'The deployment preflight must use the positional server target for list-applications.'
}

if ($text -match '(?im)DELETE\s+FROM\s+(energy_stream_runs|energy_meters|zones)\s*;') {
    throw 'Found an unscoped deletion of an energy-stream test table.'
}

if ($text -notmatch '(?is)DELETE\s+FROM\s+energy_stream_runs\s+WHERE\s+meter_id\s*=\s*\x27\$\(\$State\.MeterId\)\x27') {
    throw 'Run cleanup is not scoped to the invocation meter identifier.'
}

# Pin the fixes for the PowerShell 5.1 and Payara failures already observed.
$requiredCompatibilityMarkers = @(
    'Add-Type -AssemblyName System.Security',
    '[System.Security.Cryptography.ProtectedData]::Protect',
    '[System.Security.Cryptography.ProtectedData]::Unprotect',
    "`$deploymentName = 'smartcity360-ear-1.0-SNAPSHOT'",
    'Assert-RecoveryStateIdentifiers',
    'Protect-CurrentUserFile $passwordFile',
    'Restore-OriginalConfigProperty $State'
)
foreach ($marker in $requiredCompatibilityMarkers) {
    if (-not $text.Contains($marker)) {
        throw "Missing required compatibility or recovery safeguard: $marker"
    }
}

if ($text -match '\$CreatedAliases\b|\$createdAliases\b') {
    throw 'Alias creation must not pass a newly empty mandatory generic collection (PowerShell binding failure).'
}

if ($text -match '(?im)^\s*\$output\s*=\s*&\s*\$script:asadmin\b' -and
    $text -notmatch '\$ErrorActionPreference = ''Continue''') {
    throw 'Native asadmin stderr must be handled without aborting on expected nonzero exits.'
}

if ($text -notmatch 'Assert-RecoveryStateIdentifiers\s+\$state\s*\r?\n\s*\$recoveryStatePath\s*=\s*Save-RecoveryState') {
    throw 'Recovery identifiers must be validated before saving state and mutating Payara/PostgreSQL.'
}

if ($text -notmatch '(?s)function Remove-TestAliases.+?Get-ConfigPropertyState \$energyProperty.+?still references a test alias') {
    throw 'Alias cleanup must not delete a key referenced by the active AES property.'
}

# Detect unsafe unscoped deletions even if extra whitespace appears before a semicolon.
if ($text -match '(?im)DELETE\s+FROM\s+(energy_stream_runs|energy_readings|energy_meters|zones)\s*;') {
    throw 'Found unscoped deletion of an application table.'
}

if ($ast -eq $null) {
    throw 'PowerShell AST was not created.'
}

if ($text -match "Invoke-Asadmin\s+@\(\s*'(?:stop-domain|start-domain|restart-domain)'") {
    throw 'The integration script must never restart Payara automatically.'
}

$authenticatedSessionFunction = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Initialize-AuthenticatedSession'
}, $true)
$verifiedPayloadFunction = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Assert-ProtectedEnvelopeResponse'
}, $true)
$gzipPayloadFunction = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Expand-GzipPayload'
}, $true)
if ($null -eq $authenticatedSessionFunction -or $null -eq $verifiedPayloadFunction -or
    $null -eq $gzipPayloadFunction) {
    throw 'Authenticated verification helpers were not found for mocked regression checks.'
}

# Exercise the HTTP authentication flow with a mocked transport. No credentials
# or requests are sent to Payara by this safety harness.
& {
    param([string]$SessionFunction)

    Invoke-Expression $SessionFunction
    $requests = [Collections.Generic.List[object]]::new()
    $loginCode = 200
    $identityRoles = @('ADMIN')
    function Invoke-HttpJson {
        param(
            [string]$Path,
            [string]$Json,
            [hashtable]$Headers = @{},
            [Parameter(Mandatory)][ValidateSet('GET', 'POST')][string]$Method,
            [switch]$Cookie,
            [string]$CookieJar,
            [switch]$ApiRoot,
            [switch]$SensitiveBody
        )
        $expectedApiRoot = 'https://localhost:8181/smartcity360/api/v1'
        $streamBase = "$expectedApiRoot/energy/streams"
        $requests.Add([pscustomobject]@{
            Path = $Path
            Method = $Method
            Url = "$(if ($ApiRoot) { $expectedApiRoot } else { $streamBase })$Path"
            Json = $Json
            Headers = $Headers
            CookieJar = $CookieJar
            ApiRoot = [bool]$ApiRoot
            SensitiveBody = [bool]$SensitiveBody
        })
        if ($requests[-1].Url -eq "$expectedApiRoot/auth/csrf" -and $Method -eq 'GET') {
            return [pscustomobject]@{ Code = 200; Body = '{"token":"mock-csrf-token"}' }
        }
        if ($requests[-1].Url -eq "$expectedApiRoot/auth/csrf") {
            return [pscustomobject]@{ Code = 405; Body = '{}' }
        }
        if ($requests[-1].Url -eq "$expectedApiRoot/auth/me" -and $Method -eq 'GET') {
            return [pscustomobject]@{
                Code = 200
                Body = (@{ roles = $identityRoles } | ConvertTo-Json -Compress)
            }
        }
        return [pscustomobject]@{ Code = $loginCode; Body = '{}' }
    }

    $mockPassword = [guid]::NewGuid().ToString('N')
    $credential = [System.Management.Automation.PSCredential]::new(
        'admin@test.invalid',
        (ConvertTo-SecureString $mockPassword -AsPlainText -Force)
    )
    $session = Initialize-AuthenticatedSession $credential 'mock-cookie-jar' 'https://localhost:8181/smartcity360/api/v1/energy/streams'
    if ($requests.Count -ne 3 -or $requests[0].Path -ne '/auth/csrf' -or
        $requests[1].Path -ne '/auth/login' -or $requests[2].Path -ne '/auth/me' -or
        $requests[0].Method -ne 'GET' -or $requests[1].Method -ne 'POST' -or
        $requests[2].Method -ne 'GET') {
        throw 'Regression: verification login must obtain CSRF state, log in, then verify the authenticated identity.'
    }
    if ($requests[0].Url -ne 'https://localhost:8181/smartcity360/api/v1/auth/csrf' -or
        $requests[1].Url -ne 'https://localhost:8181/smartcity360/api/v1/auth/login' -or
        $requests[2].Url -ne 'https://localhost:8181/smartcity360/api/v1/auth/me') {
        throw 'Regression: authentication requests must use their exact API URLs.'
    }
    if (@($requests | Where-Object { $_.CookieJar -ne 'mock-cookie-jar' }).Count -ne 0 -or
        $session.CookieJar -ne 'mock-cookie-jar') {
        throw 'Regression: CSRF, login, and identity requests must share the authenticated cookie jar.'
    }
    if (@($requests | Where-Object { -not $_.ApiRoot }).Count -ne 0) {
        throw 'Regression: authentication endpoints must use the API root rather than the Energy Streams resource path.'
    }
    if ($requests[0].Headers.ContainsKey('X-CSRF-Token') -or
        $requests[0].Headers.ContainsKey('Origin') -or
        $requests[1].Headers['X-CSRF-Token'] -ne 'mock-csrf-token' -or
        $requests[1].Headers.Origin -ne 'https://localhost:8181' -or
        -not $requests[1].SensitiveBody) {
        throw 'Regression: GET must obtain the token without unsafe-request headers; login must submit that token, same origin, and protected credential body.'
    }
    $loginJson = $requests[1].Json | ConvertFrom-Json
    if ($loginJson.email -ne 'admin@test.invalid' -or
        $loginJson.password -ne $mockPassword) {
        throw 'Regression: login request did not use the supplied credential.'
    }

    $identityRoles = @('RESIDENT')
    $wrongRoleRejected = $false
    try {
        $null = Initialize-AuthenticatedSession $credential 'mock-cookie-jar' 'https://localhost:8181/smartcity360/api/v1/energy/streams'
    }
    catch {
        $wrongRoleRejected = $_.Exception.Message -match 'ADMIN or CITY_MANAGER'
    }
    if (-not $wrongRoleRejected) {
        throw 'Regression: authenticated accounts outside ADMIN and CITY_MANAGER must be rejected.'
    }

    foreach ($rejectedCode in @(401, 403)) {
        $loginCode = $rejectedCode
        $identityRoles = @('ADMIN')
        $rejected = $false
        try {
            $null = Initialize-AuthenticatedSession $credential 'mock-cookie-jar' 'https://localhost:8181/smartcity360/api/v1/energy/streams'
        }
        catch {
            $rejected = $_.Exception.Message -match "HTTP $rejectedCode"
        }
        if (-not $rejected) {
            throw "Regression: login rejection HTTP $rejectedCode must be reported distinctly."
        }
    }
} $authenticatedSessionFunction.Extent.Text

# Exercise the response contract with in-memory GZIP bytes and HTTP stubs.
& {
    param([string]$ResponseFunction, [string]$GzipFunction)

    Invoke-Expression $GzipFunction
    Invoke-Expression $ResponseFunction
    $plain = [Text.Encoding]::UTF8.GetBytes('{"period":"test","kwh":"1.25"}')
    $memory = [IO.MemoryStream]::new()
    $gzip = [IO.Compression.GZipStream]::new(
        $memory,
        [IO.Compression.CompressionMode]::Compress,
        $true
    )
    $gzip.Write($plain, 0, $plain.Length)
    $gzip.Dispose()
    $compressed = $memory.ToArray()
    $memory.Dispose()
    $success = [pscustomobject]@{
        Code = 200
        Body = (@{
            payloadBase64 = [Convert]::ToBase64String($compressed)
        } | ConvertTo-Json -Compress)
    }
    Assert-ProtectedEnvelopeResponse $success $plain

    foreach ($rejectedCode in @(401, 403, 503)) {
        $rejected = $false
        try {
            Assert-ProtectedEnvelopeResponse ([pscustomobject]@{
                Code = $rejectedCode
                Body = '{}'
            }) $plain
        }
        catch {
            $rejected = $_.Exception.Message -match "HTTP $rejectedCode"
        }
        if (-not $rejected) {
            throw "Regression: verification HTTP $rejectedCode must be reported distinctly."
        }
    }

    $mismatch = [pscustomobject]@{
        Code = 200
        Body = $success.Body
    }
    $mismatchDetected = $false
    try {
        Assert-ProtectedEnvelopeResponse $mismatch ([Text.Encoding]::UTF8.GetBytes('different expected payload'))
    }
    catch {
        $mismatchDetected = $_.Exception.Message -match 'payload mismatch'
    }
    if (-not $mismatchDetected) {
        throw 'Regression: GZIP-decompressed payload differences must be reported as a mismatch.'
    }

    $invalidGzip = [pscustomobject]@{
        Code = 200
        Body = (@{
            payloadBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('not-gzip'))
        } | ConvertTo-Json -Compress)
    }
    $invalidGzipDetected = $false
    try {
        Assert-ProtectedEnvelopeResponse $invalidGzip $plain
    }
    catch {
        $invalidGzipDetected = $_.Exception.Message -match 'GZIP'
    }
    if (-not $invalidGzipDetected) {
        throw 'Regression: invalid decrypted GZIP data must fail clearly.'
    }
} $verifiedPayloadFunction.Extent.Text $gzipPayloadFunction.Extent.Text

$checkpointCommand = @($ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
    $node.GetCommandName() -eq 'Invoke-ManualRestartCheckpoint'
}, $true))
$signatureCommand = @($ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
    $node.GetCommandName() -eq 'New-Signature'
}, $true))
$streamRequestCommand = @($ast.FindAll({
    param($node)
    if ($node -isnot [System.Management.Automation.Language.CommandAst] -or
        $node.GetCommandName() -ne 'Invoke-HttpJson') {
        return $false
    }

    $parent = $node.Parent
    while ($null -ne $parent -and
        $parent -isnot [System.Management.Automation.Language.FunctionDefinitionAst]) {
        $parent = $parent.Parent
    }
    return $null -eq $parent
}, $true))
if ($checkpointCommand.Count -ne 1 -or $signatureCommand.Count -ne 1 -or
    $streamRequestCommand.Count -lt 1) {
    throw 'Restart checkpoint, signing, or stream request command could not be identified safely.'
}
if ($checkpointCommand[0].Extent.StartOffset -ge $signatureCommand[0].Extent.StartOffset -or
    $checkpointCommand[0].Extent.StartOffset -ge $streamRequestCommand[0].Extent.StartOffset) {
    throw 'The restart checkpoint must occur before HMAC signing and every Energy Streams request.'
}

# Exercise only the parsed property-reader function with a mocked asadmin.
# Importing the entire integration script would execute its main routine, so
# load just this function definition from the AST into an isolated scope.
$propertyReader = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Get-ConfigPropertyState'
}, $true)
if ($null -eq $propertyReader) {
    throw 'Get-ConfigPropertyState function not found for parser regression checks.'
}

& {
    param([string]$FunctionDefinition)

    Invoke-Expression $FunctionDefinition

    function Invoke-Asadmin {
        param([string[]]$Arguments, [switch]$AllowFailure)
        return $mockResult
    }

    $propertyName = 'smartcity360.energy.aes.current-key-id'
    $alias = 'energy-stream-it.83e3dd67814340388a2731502345c6b2.aes.v1'
    $footer = 'Command get-config-property executed successfully.'

    $mockResult = [pscustomobject]@{ ExitCode = 0; Output = @($alias, $footer) }
    $state = Get-ConfigPropertyState $propertyName
    if (-not $state.Exists -or $state.Value -cne $alias) {
        throw 'Regression: Payara 6 bare-value configuration output was not parsed.'
    }

    $mockResult = [pscustomobject]@{ ExitCode = 0; Output = @("$propertyName=$alias", $footer) }
    $state = Get-ConfigPropertyState $propertyName
    if (-not $state.Exists -or $state.Value -cne $alias) {
        throw 'Regression: name=value configuration output was not parsed.'
    }

    $mockResult = [pscustomobject]@{ ExitCode = 1; Output = @(
        'remote failure: smartcity360.energy.aes.current-key-id Not Found in source domain',
        'Command get-config-property failed.'
    ) }
    $state = Get-ConfigPropertyState $propertyName
    if ($state.Exists) {
        throw 'Regression: missing configuration property should be treated as absent.'
    }

    $mockResult = [pscustomobject]@{ ExitCode = 0; Output = @($alias, 'unexpected extra value', $footer) }
    $rejected = $false
    try {
        $null = Get-ConfigPropertyState $propertyName
    }
    catch {
        $rejected = $true
    }
    if (-not $rejected) {
        throw 'Regression: ambiguous configuration response must fail closed.'
    }
} $propertyReader.Extent.Text

# The checkpoint is tested as an extracted function so the integration
# routine, Payara, PostgreSQL, and HTTP endpoints are never invoked.
$restartCheckpoint = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Invoke-ManualRestartCheckpoint'
}, $true)
if ($null -eq $restartCheckpoint) {
    throw 'Invoke-ManualRestartCheckpoint function not found for restart checkpoint regression checks.'
}

& {
    param([string]$FunctionDefinition)

    Invoke-Expression $FunctionDefinition

    $script:PayaraHome = 'C:\payara6'
    $script:DomainName = 'domain1'
    $script:asadmin = 'C:\payara6\bin\asadmin.bat'
    $validationOrder = [System.Collections.Generic.List[string]]::new()

    function Read-Host {
        param([string]$Prompt)
        return 'RESTARTED'
    }

    function Assert-Preflight {
        $validationOrder.Add('deployment')
    }

    function Assert-TemporaryAliasesExist {
        param($State)
        $validationOrder.Add('aliases')
    }

    function Assert-PayaraHttpAvailable {
        $validationOrder.Add('http')
    }

    Invoke-ManualRestartCheckpoint -State ([pscustomobject]@{
        TemporaryAliases = @('energy-stream-it.1234567890abcdef1234567890abcdef.meter.v1')
    }) -RequireRestart

    if (($validationOrder -join ',') -ne 'deployment,aliases,http') {
        throw 'Regression: confirmed restart must validate deployment, aliases, and HTTP availability in that order.'
    }
} $restartCheckpoint.Extent.Text

& {
    param([string]$FunctionDefinition)

    Invoke-Expression $FunctionDefinition

    $script:PayaraHome = 'C:\payara6'
    $script:DomainName = 'domain1'
    $script:asadmin = 'C:\payara6\bin\asadmin.bat'
    $validationCalled = $false

    function Read-Host {
        param([string]$Prompt)
        return 'no'
    }

    function Assert-Preflight {
        $script:validationCalled = $true
    }

    function Assert-TemporaryAliasesExist {
        param($State)
        $script:validationCalled = $true
    }

    function Assert-PayaraHttpAvailable {
        $script:validationCalled = $true
    }

    $rejected = $false
    try {
        Invoke-ManualRestartCheckpoint -State ([pscustomobject]@{
            TemporaryAliases = @('energy-stream-it.1234567890abcdef1234567890abcdef.meter.v1')
        }) -RequireRestart
    }
    catch {
        $rejected = $true
    }

    if (-not $rejected -or $validationCalled) {
        throw 'Regression: an unconfirmed restart must fail before post-restart validation.'
    }
} $restartCheckpoint.Extent.Text

'Energy stream integration script static safety, authentication, and protected-payload mock checks: PASS'
