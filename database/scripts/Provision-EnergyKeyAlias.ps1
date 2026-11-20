[CmdletBinding(SupportsShouldProcess, ConfirmImpact='High')]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]{1,128}$')][string]$AliasName,
    [Parameter(Mandatory)][ValidateSet('MeterHmac','AesGcm')][string]$Purpose,
    [string]$PayaraHome = 'E:\Java Institute\IdeaProjects\payara6',
    [switch]$Apply
)

function Invoke-Asadmin {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $script:asadmin @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    catch {
        throw 'Payara command could not be completed; its output was suppressed.'
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { $_.ToString() })
    }
}

function Assert-LocalDomainPreflight {
    param([Parameter(Mandatory)][string]$PayaraRoot)

    if (-not (Test-Path -LiteralPath $PayaraRoot -PathType Container)) {
        throw 'Payara home was not found; refusing to provision.'
    }

    $script:asadmin = Join-Path $PayaraRoot 'bin\asadmin.bat'
    $domainsRoot = Join-Path $PayaraRoot 'glassfish\domains'
    $domainDirectory = Join-Path $domainsRoot 'domain1'
    $domainXml = Join-Path $domainDirectory 'config\domain.xml'
    if (-not (Test-Path -LiteralPath $script:asadmin -PathType Leaf) -or
        -not (Test-Path -LiteralPath $domainXml -PathType Leaf)) {
        throw 'The local Payara domain1 installation is incomplete; refusing to provision.'
    }

    $settings = New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = $null
    try {
        $reader = [System.Xml.XmlReader]::Create($domainXml, $settings)
        $domain = New-Object System.Xml.XmlDocument
        $domain.XmlResolver = $null
        $domain.Load($reader)
    }
    catch {
        throw 'The local domain1 configuration could not be read safely; refusing to provision.'
    }
    finally {
        if ($reader) { $reader.Dispose() }
    }

    $serverNodes = @($domain.SelectNodes("//*[local-name()='server']") | Where-Object {
        $_.GetAttribute('name') -ceq 'server'
    })
    if ($serverNodes.Count -ne 1) {
        throw 'The local domain1 server configuration is ambiguous; refusing to provision.'
    }
    $configName = $serverNodes[0].GetAttribute('config-ref')
    $configNodes = @($domain.SelectNodes("//*[local-name()='config']") | Where-Object {
        $_.GetAttribute('name') -ceq $configName
    })
    if ($configNodes.Count -ne 1) {
        throw 'The local domain1 server configuration is missing or ambiguous; refusing to provision.'
    }

    $listeners = @($configNodes[0].SelectNodes(".//*[local-name()='network-listener']") | Where-Object {
        $_.GetAttribute('name') -ceq 'admin-listener'
    })
    if ($listeners.Count -ne 1) {
        throw 'The local domain1 admin endpoint is missing or ambiguous; refusing to provision.'
    }
    $address = $listeners[0].GetAttribute('address')
    if ($address -and $address -notin @('localhost', '127.0.0.1', '::1')) {
        throw 'The domain1 admin endpoint is not bound to a local address; refusing to provision.'
    }
    $adminPort = 0
    if (-not [int]::TryParse($listeners[0].GetAttribute('port'), [ref]$adminPort) -or
        $adminPort -lt 1 -or $adminPort -gt 65535) {
        throw 'The local domain1 admin port is not a fixed valid port; refusing to provision.'
    }

    $domainsResult = Invoke-Asadmin -Arguments @(
        '--terse', 'list-domains', "--domaindir=$domainsRoot"
    )
    $runningDomain = @($domainsResult.Output | Where-Object {
        $_ -match '^\s*domain1\s*(?::\s*|\s+)running(?:\b.*)?$'
    })
    if ($domainsResult.ExitCode -ne 0 -or $runningDomain.Count -ne 1) {
        throw 'The local Payara domain1 is not confirmed running; refusing to provision.'
    }

    $script:adminPort = $adminPort
}

function Get-PasswordAliasNames {
    $arguments = @('--host', '127.0.0.1', '--port', "$script:adminPort", 'list-password-aliases')
    $result = Invoke-Asadmin -Arguments $arguments
    $successConfirmed = @($result.Output | Where-Object {
        $_ -match '(?i)^Command list-password-aliases executed successfully\.?$'
    }).Count -eq 1
    if ($result.ExitCode -ne 0 -or -not $successConfirmed) {
        throw 'Could not query password aliases on the local domain1 admin endpoint; refusing to provision.'
    }

    @($result.Output | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -match '^[A-Za-z0-9._-]{1,128}$'
    })
}

function New-PasswordFile {
    [IO.Path]::GetTempFileName()
}

function Protect-PasswordFile {
    param([Parameter(Mandatory)][string]$Path)

    if ($env:OS -ne 'Windows_NT') {
        throw 'Windows ACL protection is required for the temporary password file.'
    }

    $userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $systemSid = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) {
        [void]$acl.RemoveAccessRuleAll($rule)
    }

    foreach ($sid in @($userSid, $systemSid)) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $sid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.AccessControlType]::Allow
        )
        [void]$acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $Path -AclObject $acl

    $verified = Get-Acl -LiteralPath $Path
    $expectedSids = @($userSid.Value, $systemSid.Value) | Sort-Object -Unique
    $actualRules = @($verified.Access)
    $actualSids = @($actualRules | ForEach-Object {
        $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
    } | Sort-Object -Unique)
    if (-not $verified.AreAccessRulesProtected -or
        $actualRules.Count -ne 2 -or
        ($actualSids -join ',') -cne ($expectedSids -join ',') -or
        @($actualRules | Where-Object {
            $_.IsInherited -or
            $_.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow -or
            $_.FileSystemRights -ne [Security.AccessControl.FileSystemRights]::FullControl
        }).Count -ne 0) {
        throw 'Could not verify the protected ACL on the temporary password file.'
    }
}

function Invoke-EnergyKeyAliasProvisioning {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][ValidateSet('MeterHmac','AesGcm')][string]$KeyPurpose,
        [Parameter(Mandatory)][string]$PayaraRoot,
        [switch]$Apply,
        [switch]$Proceed
    )

    $secretBytes = $null
    $generator = $null
    $encoded = $null
    try {
        if (-not $Apply) {
            "Dry run for $KeyPurpose alias '$Name'; no key was generated and no persistent change was made."
            return
        }
        if (-not $Proceed) { return }

        Assert-LocalDomainPreflight -PayaraRoot $PayaraRoot
        $existingAliases = @(Get-PasswordAliasNames)
        if (@($existingAliases | Where-Object {
            [string]::Equals($_, $Name, [StringComparison]::Ordinal)
        }).Count -gt 0) {
            throw "Password alias '$Name' already exists in local Payara domain1; no changes were made."
        }

        $secretBytes = New-Object byte[] 32
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        $generator.GetBytes($secretBytes)
        $encoded = [Convert]::ToBase64String($secretBytes)

        $passwordFile = New-PasswordFile
        try {
            Protect-PasswordFile -Path $passwordFile
            [IO.File]::WriteAllText(
                $passwordFile,
                "AS_ADMIN_ALIASPASSWORD=$encoded`n",
                [Text.UTF8Encoding]::new($false)
            )

            $createArguments = @(
                '--host', '127.0.0.1',
                '--port', "$script:adminPort",
                '--passwordfile', $passwordFile,
                'create-password-alias', $Name
            )
            $createResult = $null
            $createInvocationFailed = $false
            try {
                $createResult = Invoke-Asadmin -Arguments $createArguments
            }
            catch {
                $createInvocationFailed = $true
            }

            try {
                $verifiedAliases = @(Get-PasswordAliasNames)
            }
            catch {
                throw 'Alias creation outcome is ambiguous because local domain1 verification failed. Manually verify the alias in the intended development domain before retrying; no alias was automatically deleted.'
            }
            $aliasExists = @($verifiedAliases | Where-Object {
                [string]::Equals($_, $Name, [StringComparison]::Ordinal)
            }).Count -gt 0
            $successConfirmed = -not $createInvocationFailed -and @($createResult.Output | Where-Object {
                $_ -match '(?i)executed successfully\.?$'
            }).Count -gt 0

            if ($createInvocationFailed -or $createResult.ExitCode -ne 0 -or
                -not $successConfirmed -or -not $aliasExists) {
                if ($aliasExists) {
                    throw 'Alias creation outcome is ambiguous; the alias now exists but the command result was not conclusive. Manually verify the alias in the intended development domain before retrying; no alias was automatically deleted.'
                }
                throw 'Payara did not conclusively create and verify the alias. Manually verify the intended development domain before retrying; no alias was automatically deleted.'
            }
        }
        finally {
            if ($passwordFile) {
                Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
                if (Test-Path -LiteralPath $passwordFile) {
                    throw 'The protected temporary password file could not be removed; remove it securely before continuing.'
                }
            }
        }
        "Provisioned Payara alias '$Name'. The value was not displayed or written to project files."
    }
    finally {
        if ($generator) { $generator.Dispose() }
        if ($secretBytes) { [Array]::Clear($secretBytes, 0, $secretBytes.Length) }
        $encoded = $null
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    if ($Apply) {
        if (-not $PSCmdlet.ShouldProcess("Payara domain1 alias $AliasName", 'Create password alias')) {
            return
        }
        $confirmation = Read-Host "Type PROVISION to write alias '$AliasName' to Payara domain1"
        if ($confirmation -cne 'PROVISION') { throw 'Provisioning cancelled.' }
    }

    Invoke-EnergyKeyAliasProvisioning `
        -Name $AliasName `
        -KeyPurpose $Purpose `
        -PayaraRoot $PayaraHome `
        -Apply:$Apply `
        -Proceed:$Apply
}
