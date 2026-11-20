[CmdletBinding()]
param(
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\pgAdmin 4\runtime\psql.exe'
)

$ErrorActionPreference = 'Stop'

function Assert-AdministratorSession {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)

    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Initial administrator provisioning requires an elevated local Administrator session.'
    }
}

function Assert-RestrictedFileAcl {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [Security.Principal.SecurityIdentifier[]]$AllowedSids
    )

    $acl = Get-Acl -LiteralPath $Path
    if (-not $acl.AreAccessRulesProtected) {
        throw 'Temporary SQL file ACL is not protected.'
    }

    $currentUserSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $ownerSid = $acl.GetOwner([Security.Principal.SecurityIdentifier])
    if ($ownerSid.Value -ne $currentUserSid.Value) {
        throw 'Temporary SQL file owner verification failed.'
    }

    $allowedSidValues = @($AllowedSids | ForEach-Object { $_.Value })
    $rules = @(
        $acl.GetAccessRules(
            $true,
            $true,
            [Security.Principal.SecurityIdentifier]
        )
    )

    foreach ($rule in $rules) {
        $isExpectedRule =
            -not $rule.IsInherited -and
            $allowedSidValues -contains $rule.IdentityReference.Value -and
            $rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow -and
            $rule.FileSystemRights -eq [Security.AccessControl.FileSystemRights]::FullControl

        if (-not $isExpectedRule) {
            throw 'Temporary SQL file ACL contains an unexpected access rule.'
        }
    }

    foreach ($allowedSid in $AllowedSids) {
        $matchingRules = @(
            $rules | Where-Object {
                $_.IdentityReference.Value -eq $allowedSid.Value -and
                $_.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow -and
                $_.FileSystemRights -eq [Security.AccessControl.FileSystemRights]::FullControl
            }
        )

        if ($matchingRules.Count -ne 1) {
            throw 'Temporary SQL file ACL is missing an authorized principal or contains duplicates.'
        }
    }

    if ($rules.Count -ne $AllowedSids.Count) {
        throw 'Temporary SQL file ACL contains an unexpected number of access rules.'
    }
}

function New-RestrictedTemporarySqlFile {
    $currentUserSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $administratorsSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
    $allowedSids = @($currentUserSid, $systemSid, $administratorsSid)

    $fileSecurity = [Security.AccessControl.FileSecurity]::new()
    $fileSecurity.SetAccessRuleProtection($true, $false)
    $fileSecurity.SetOwner($currentUserSid)

    foreach ($allowedSid in $allowedSids) {
        $accessRule = [Security.AccessControl.FileSystemAccessRule]::new(
            $allowedSid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
        $null = $fileSecurity.AddAccessRule($accessRule)
    }

    $temporaryPath = [IO.Path]::Combine(
        [IO.Path]::GetTempPath(),
        [IO.Path]::GetRandomFileName() + '.sql'
    )
    $fileStream = $null

    try {
        $fileStream = [IO.FileStream]::new(
            $temporaryPath,
            [IO.FileMode]::CreateNew,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [IO.FileShare]::None,
            4096,
            [IO.FileOptions]::None,
            $fileSecurity
        )

        Assert-RestrictedFileAcl -Path $temporaryPath -AllowedSids $allowedSids

        return [pscustomobject]@{
            Path = $temporaryPath
            Stream = $fileStream
        }
    }
    catch {
        if ($fileStream) {
            $fileStream.Dispose()
        }
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
        }
        throw 'Unable to create and verify a protected temporary SQL file.'
    }
}

function Remove-SensitiveTemporarySqlFile {
    param(
        [AllowNull()]
        [psobject]$TemporaryFile
    )

    if (-not $TemporaryFile) {
        return
    }

    $cleanupFailed = $false
    $wipeStream = $null

    try {
        if ($TemporaryFile.Stream) {
            $TemporaryFile.Stream.Dispose()
        }
    }
    catch {
        $cleanupFailed = $true
    }

    if ($TemporaryFile.Path -and (Test-Path -LiteralPath $TemporaryFile.Path)) {
        try {
            $wipeStream = [IO.FileStream]::new(
                $TemporaryFile.Path,
                [IO.FileMode]::Open,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None
            )
            $wipeStream.SetLength(0)
            $wipeStream.Flush($true)
        }
        catch {
            $cleanupFailed = $true
        }
        finally {
            if ($wipeStream) {
                $wipeStream.Dispose()
            }
        }

        try {
            Remove-Item -LiteralPath $TemporaryFile.Path -Force -ErrorAction Stop
        }
        catch {
            $cleanupFailed = $true
        }
    }

    if ($cleanupFailed) {
        throw 'Protected temporary SQL file cleanup failed.'
    }
}

function New-SmartCityPasswordHash {
    param(
        [Parameter(Mandatory)]
        [Security.SecureString]$Password
    )

    $passwordPointer = [IntPtr]::Zero
    $plainTextPassword = $null
    $saltBytes = $null
    $hashBytes = $null
    $randomNumberGenerator = $null
    $keyDeriver = $null
    $encodedSalt = $null
    $encodedHash = $null

    try {
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
        $plainTextPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

        if ($plainTextPassword.Length -lt 14) {
            throw 'Password must contain at least 14 characters.'
        }

        $saltBytes = [byte[]]::new(16)
        $randomNumberGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
        $randomNumberGenerator.GetBytes($saltBytes)

        $keyDeriver = [Security.Cryptography.Rfc2898DeriveBytes]::new(
            $plainTextPassword,
            $saltBytes,
            600000,
            [Security.Cryptography.HashAlgorithmName]::SHA256
        )
        $hashBytes = $keyDeriver.GetBytes(32)

        $encodedSalt = [Convert]::ToBase64String($saltBytes).
            TrimEnd('=').
            Replace('+', '-').
            Replace('/', '_')
        $encodedHash = [Convert]::ToBase64String($hashBytes).
            TrimEnd('=').
            Replace('+', '-').
            Replace('/', '_')

        return "sc360`$1`$pbkdf2-sha256`$600000`$$encodedSalt`$$encodedHash"
    }
    finally {
        if ($keyDeriver) {
            $keyDeriver.Dispose()
        }
        if ($randomNumberGenerator) {
            $randomNumberGenerator.Dispose()
        }
        if ($hashBytes) {
            [Array]::Clear($hashBytes, 0, $hashBytes.Length)
        }
        if ($saltBytes) {
            [Array]::Clear($saltBytes, 0, $saltBytes.Length)
        }
        if ($passwordPointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
        }

        $plainTextPassword = $null
        $encodedSalt = $null
        $encodedHash = $null
    }
}

function ConvertTo-SqlLiteral {
    param(
        [Parameter(Mandatory)]
        [string]$Value
    )

    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-SmartCityDatabaseConfiguration {
    param(
        [Parameter(Mandatory)]
        [string]$PayaraHomePath
    )

    $domainXmlPath = Join-Path $PayaraHomePath 'glassfish\domains\domain1\config\domain.xml'
    if (-not (Test-Path -LiteralPath $domainXmlPath -PathType Leaf)) {
        throw 'The configured Payara domain.xml file was not found.'
    }

    try {
        [xml]$domain = Get-Content -LiteralPath $domainXmlPath -Raw
    }
    catch {
        throw 'The configured Payara domain.xml file could not be read.'
    }

    $resources = @(
        $domain.SelectNodes("//*[local-name()='jdbc-resource']") |
            Where-Object { $_.GetAttribute('jndi-name') -eq 'jdbc/smartcity360' }
    )
    if ($resources.Count -ne 1) {
        throw 'Payara resource jdbc/smartcity360 was not found uniquely.'
    }

    $poolName = $resources[0].GetAttribute('pool-name')
    $pools = @(
        $domain.SelectNodes("//*[local-name()='jdbc-connection-pool']") |
            Where-Object { $_.GetAttribute('name') -eq $poolName }
    )
    if ($pools.Count -ne 1) {
        throw 'The JDBC pool referenced by jdbc/smartcity360 was not found uniquely.'
    }

    $properties = @{}
    foreach ($property in @($pools[0].SelectNodes("./*[local-name()='property']"))) {
        $properties[$property.GetAttribute('name')] = $property.GetAttribute('value')
    }

    $jdbcUrl = $properties['URL']
    if (-not $jdbcUrl) {
        $jdbcUrl = $properties['url']
    }

    $urlMatch = [regex]::Match(
        $jdbcUrl,
        '^jdbc:postgresql://(?<host>[^:/]+):(?<port>\d+)/(?<database>[^?]+)'
    )
    if (-not $urlMatch.Success) {
        throw 'The configured JDBC URL is not a supported PostgreSQL URL.'
    }

    if (-not $properties['user'] -or -not $properties['password']) {
        throw 'The SmartCity360 JDBC pool is missing required credentials.'
    }

    return [pscustomobject]@{
        Host = $urlMatch.Groups['host'].Value
        Port = $urlMatch.Groups['port'].Value
        Database = $urlMatch.Groups['database'].Value
        User = $properties['user']
        Password = $properties['password']
    }
}

Assert-AdministratorSession

if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) {
    throw 'The configured PostgreSQL client executable was not found.'
}

$email = (Read-Host 'Initial administrator email').Trim().ToLowerInvariant()
$displayName = (Read-Host 'Display name').Trim()
$securePassword = Read-Host 'Initial password' -AsSecureString
$confirmation = Read-Host 'Type PROVISION to modify the configured SmartCity360 database'

if ($confirmation -cne 'PROVISION') {
    $securePassword.Dispose()
    throw 'Provisioning cancelled.'
}

$passwordHash = $null
$sql = $null
$databaseConfiguration = $null
$temporarySqlFile = $null
$previousPgPassword = $env:PGPASSWORD
$provisioningError = $null
$cleanupError = $null
$provisioningSucceeded = $false

try {
    $passwordHash = New-SmartCityPasswordHash -Password $securePassword
    $databaseConfiguration = Get-SmartCityDatabaseConfiguration -PayaraHomePath $PayaraHome

    $emailSql = ConvertTo-SqlLiteral -Value $email
    $displayNameSql = ConvertTo-SqlLiteral -Value $displayName
    $passwordHashSql = ConvertTo-SqlLiteral -Value $passwordHash

    $sql = @"
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('smartcity360.initial-admin', 0));
DO `$`$
BEGIN
    IF EXISTS (SELECT 1 FROM user_roles WHERE role_code = 'ADMIN') THEN
        RAISE EXCEPTION 'An administrator is already provisioned';
    END IF;
END
`$`$;
WITH new_user AS (
    INSERT INTO users (full_name, email, password_hash, status)
    VALUES ($displayNameSql, $emailSql, $passwordHashSql, 'ACTIVE')
    RETURNING id
)
INSERT INTO user_roles (user_id, role_code)
SELECT id, 'ADMIN'
FROM new_user;
COMMIT;
"@

    $temporarySqlFile = New-RestrictedTemporarySqlFile
    $writer = [IO.StreamWriter]::new(
        $temporarySqlFile.Stream,
        [Text.UTF8Encoding]::new($false)
    )

    try {
        $writer.Write($sql)
        $writer.Flush()
    }
    finally {
        $writer.Dispose()
    }

    $env:PGPASSWORD = $databaseConfiguration.Password
    $psqlArguments = @(
        '-X',
        '-v', 'ON_ERROR_STOP=1',
        '-h', $databaseConfiguration.Host,
        '-p', $databaseConfiguration.Port,
        '-U', $databaseConfiguration.User,
        '-d', $databaseConfiguration.Database,
        '-f', $temporarySqlFile.Path
    )

    & $PsqlPath @psqlArguments *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Administrator provisioning failed.'
    }

    $provisioningSucceeded = $true
}
catch {
    $provisioningError = $_
}
finally {
    $env:PGPASSWORD = $previousPgPassword

    try {
        Remove-SensitiveTemporarySqlFile -TemporaryFile $temporarySqlFile
    }
    catch {
        $cleanupError = $_
    }

    if ($securePassword) {
        $securePassword.Dispose()
    }
    if ($databaseConfiguration) {
        $databaseConfiguration.Password = $null
    }

    $previousPgPassword = $null
    $passwordHash = $null
    $passwordHashSql = $null
    $sql = $null
}

if ($cleanupError) {
    throw 'Administrator provisioning stopped and protected temporary SQL cleanup failed.'
}
if ($provisioningError) {
    throw $provisioningError
}
if ($provisioningSucceeded) {
    'Initial administrator provisioned.'
}
