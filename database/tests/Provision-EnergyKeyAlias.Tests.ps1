$script:ProvisionScript = Join-Path $PSScriptRoot '..\scripts\Provision-EnergyKeyAlias.ps1'
$script:TestRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('smartcity360-provision-energy-key-alias-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $script:TestRoot | Out-Null

. $script:ProvisionScript -AliasName 'unit.alias' -Purpose MeterHmac -PayaraHome $script:TestRoot
$script:ActualProtectPasswordFile = (Get-Command Protect-PasswordFile -CommandType Function).ScriptBlock

function New-TestDomain {
    $script:FixtureHome = Join-Path $script:TestRoot 'payara'
    $domainConfig = Join-Path $script:FixtureHome 'glassfish\domains\domain1\config'
    $bin = Join-Path $script:FixtureHome 'bin'
    New-Item -ItemType Directory -Path $domainConfig -Force | Out-Null
    New-Item -ItemType Directory -Path $bin -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $bin 'asadmin.bat'), '')
    $xml = @'
<domain>
  <servers><server name="server" config-ref="server-config" /></servers>
  <configs><config name="server-config">
    <network-config><network-listeners>
      <network-listener name="admin-listener" address="127.0.0.1" port="4848" />
    </network-listeners></network-config>
  </config></configs>
</domain>
'@
    [IO.File]::WriteAllText((Join-Path $domainConfig 'domain.xml'), $xml)
}

function New-PasswordFile {
    $path = Join-Path $script:TestRoot ('password-' + [Guid]::NewGuid().ToString('N') + '.txt')
    [IO.File]::WriteAllText($path, '')
    $script:LastPasswordFile = $path
    $path
}

function Protect-PasswordFile {
    param([Parameter(Mandatory)][string]$Path)

    $script:AclAttempted = $true
    $script:AclSawEmptyFile = ([IO.File]::ReadAllText($Path) -ceq '')
    if ($script:FailAcl) { throw 'Mock ACL protection failure.' }
    & $script:ActualProtectPasswordFile -Path $Path
}

function Read-Host {
    $script:ConfirmationCalls++
    'PROVISION'
}

function Invoke-Asadmin {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $script:AsadminCalls += ,@($Arguments)
    if ($Arguments -contains 'list-domains') {
        return [pscustomobject]@{
            ExitCode = $script:DomainListExitCode
            Output = @($script:DomainListOutput)
        }
    }

    if ($Arguments -contains 'list-password-aliases') {
        $script:AliasListCalls++
        if ($script:FailAliasVerification -and $script:AliasListCalls -gt 1) {
            return [pscustomobject]@{ ExitCode = 1; Output = @('mock failure') }
        }
        if ($script:FailInitialAliasList -and $script:AliasListCalls -eq 1) {
            return [pscustomobject]@{ ExitCode = 0; Output = @('mock failure') }
        }
        return [pscustomobject]@{
            ExitCode = 0
            Output = @($script:AliasNames + 'Command list-password-aliases executed successfully.')
        }
    }

    if ($Arguments -contains 'create-password-alias') {
        $script:CreateCalls++
        $passwordFileIndex = [Array]::IndexOf($Arguments, '--passwordfile')
        $script:CapturedPasswordContent = [IO.File]::ReadAllText($Arguments[$passwordFileIndex + 1])
        if ($script:AddAliasAfterCreate) { $script:AliasNames += $Arguments[-1] }
        if ($script:ThrowAfterCreate) { throw 'Mocked command transport failure.' }
        return [pscustomobject]@{
            ExitCode = $script:CreateExitCode
            Output = @($script:CreateOutput)
        }
    }

    if ($Arguments -contains 'delete-password-alias') { $script:DeleteCalls++ }
    throw 'Unexpected mocked asadmin command.'
}

function Invoke-TestProvision {
    param(
        [string]$Name = 'unit.alias',
        [string]$KeyPurpose = 'MeterHmac'
    )

    Invoke-EnergyKeyAliasProvisioning `
        -Name $Name `
        -KeyPurpose $KeyPurpose `
        -PayaraRoot $script:FixtureHome `
        -Apply `
        -Proceed
}

function Reset-TestState {
    New-TestDomain
    $script:AliasNames = @()
    $script:DomainListExitCode = 0
    $script:DomainListOutput = @('domain1 running')
    $script:AliasListCalls = 0
    $script:CreateCalls = 0
    $script:DeleteCalls = 0
    $script:CreateExitCode = 0
    $script:CreateOutput = @('Command create-password-alias executed successfully.')
    $script:AddAliasAfterCreate = $true
    $script:ThrowAfterCreate = $false
    $script:FailAliasVerification = $false
    $script:FailInitialAliasList = $false
    $script:FailAcl = $false
    $script:AclAttempted = $false
    $script:AclSawEmptyFile = $false
    $script:LastPasswordFile = $null
    $script:CapturedPasswordContent = $null
    $script:AsadminCalls = @()
    $script:ConfirmationCalls = 0
}

function Remove-TestPasswordFile {
    if ($script:LastPasswordFile -and (Test-Path -LiteralPath $script:LastPasswordFile)) {
        Remove-Item -LiteralPath $script:LastPasswordFile -Force
    }
}

Describe 'Provision-EnergyKeyAlias' {
    BeforeEach { Reset-TestState }
    AfterEach { Remove-TestPasswordFile }
    AfterAll {
        if (Test-Path -LiteralPath $script:TestRoot) {
            Remove-Item -LiteralPath $script:TestRoot -Recurse -Force
        }
    }

    It 'rejects an exact existing alias before creating a password file' {
        $script:AliasNames = @('unit.alias', 'unit.alias.suffix')
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch {
            $thrown = $_.Exception.Message -match 'already exists'
        }
        $thrown | Should Be $true
        $script:AliasListCalls | Should Be 1
        $script:CreateCalls | Should Be 0
        $script:LastPasswordFile | Should Be $null
    }

    It 'fails closed when the local domain1 preflight does not confirm a running domain' {
        $script:DomainListOutput = @('domain10 running')
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch { $thrown = $true }
        $thrown | Should Be $true
        $script:AliasListCalls | Should Be 0
        $script:CreateCalls | Should Be 0
    }

    It 'rejects a non-local admin endpoint in domain1 configuration' {
        $domainXml = Join-Path $script:FixtureHome 'glassfish\domains\domain1\config\domain.xml'
        $xml = [IO.File]::ReadAllText($domainXml).Replace('address="127.0.0.1"', 'address="192.0.2.10"')
        [IO.File]::WriteAllText($domainXml, $xml)
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch { $thrown = $true }
        $thrown | Should Be $true
        $script:AsadminCalls.Count | Should Be 0
    }

    It 'fails closed when the initial alias-list command does not confirm success' {
        $script:FailInitialAliasList = $true
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch { $thrown = $true }
        $thrown | Should Be $true
        $script:AliasListCalls | Should Be 1
        $script:CreateCalls | Should Be 0
        $script:LastPasswordFile | Should Be $null
    }

    It 'does not write a secret when password-file ACL protection fails' {
        $script:FailAcl = $true
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch { $thrown = $true }
        $thrown | Should Be $true
        $script:AclAttempted | Should Be $true
        $script:AclSawEmptyFile | Should Be $true
        $script:CreateCalls | Should Be 0
        (Test-Path -LiteralPath $script:LastPasswordFile) | Should Be $false
    }

    It 'creates and verifies the exact alias using a protected 32-byte random key' {
        $script:AliasNames = @('unit.alias.suffix')
        $output = @(Invoke-TestProvision)
        $script:CreateCalls | Should Be 1
        $script:AliasListCalls | Should Be 2
        $script:DeleteCalls | Should Be 0
        $script:AclSawEmptyFile | Should Be $true
        $script:CapturedPasswordContent | Should Match '^AS_ADMIN_ALIASPASSWORD=[A-Za-z0-9+/]{43}=$\n$'
        $encoded = $script:CapturedPasswordContent -replace '^AS_ADMIN_ALIASPASSWORD=|\r?\n$', ''
        ([Convert]::FromBase64String($encoded)).Length | Should Be 32
        (($script:AsadminCalls | ForEach-Object { $_ -join ' ' }) -join "`n") -notmatch [regex]::Escape($encoded) | Should Be $true
        ($script:AsadminCalls[1] -join ' ') | Should Match '--host 127\.0\.0\.1 --port 4848 list-password-aliases'
        ($script:AsadminCalls[2] -join ' ') | Should Match '--host 127\.0\.0\.1 --port 4848 --passwordfile .* create-password-alias unit\.alias'
        (($output -join "`n") -match [regex]::Escape($encoded)) | Should Be $false
        (Test-Path -LiteralPath $script:LastPasswordFile) | Should Be $false
    }

    It 'does not delete an alias after an ambiguous create failure' {
        $script:CreateExitCode = 1
        $script:CreateOutput = @('Command failed.')
        $thrown = $false
        $errorMessage = ''
        try { Invoke-TestProvision | Out-Null } catch {
            $thrown = $true
            $errorMessage = $_.Exception.Message
        }
        $thrown | Should Be $true
        $script:DeleteCalls | Should Be 0
        (@($script:AliasNames) -contains 'unit.alias') | Should Be $true
        $errorMessage | Should Match 'Manually verify'
        ($errorMessage -match [regex]::Escape($script:CapturedPasswordContent)) | Should Be $false
        (Test-Path -LiteralPath $script:LastPasswordFile) | Should Be $false
    }

    It 'does not delete an alias when post-create verification is unavailable' {
        $script:FailAliasVerification = $true
        $thrown = $false
        try { Invoke-TestProvision | Out-Null } catch { $thrown = $true }
        $thrown | Should Be $true
        $script:DeleteCalls | Should Be 0
        (Test-Path -LiteralPath $script:LastPasswordFile) | Should Be $false
    }

    It 'verifies and preserves an alias after the create command throws ambiguously' {
        $script:ThrowAfterCreate = $true
        $thrown = $false
        $errorMessage = ''
        try { Invoke-TestProvision | Out-Null } catch {
            $thrown = $true
            $errorMessage = $_.Exception.Message
        }
        $thrown | Should Be $true
        $script:AliasListCalls | Should Be 2
        (@($script:AliasNames) -contains 'unit.alias') | Should Be $true
        $script:DeleteCalls | Should Be 0
        $errorMessage | Should Match 'ambiguous'
        (Test-Path -LiteralPath $script:LastPasswordFile) | Should Be $false
    }

    It 'keeps dry-run and ShouldProcess refusal non-mutating' {
        $dryRunOutput = @(Invoke-EnergyKeyAliasProvisioning `
            -Name 'unit.alias' `
            -KeyPurpose 'MeterHmac' `
            -PayaraRoot $script:FixtureHome)
        $dryRunOutput -join "`n" | Should Match 'no key was generated'
        $script:AsadminCalls.Count | Should Be 0
        $script:ConfirmationCalls | Should Be 0

        $missingHome = Join-Path $script:TestRoot 'missing-payara-home'
        & $script:ProvisionScript -AliasName 'unit.alias' -Purpose MeterHmac `
            -PayaraHome $missingHome -Apply -WhatIf | Out-Null
        $script:AsadminCalls.Count | Should Be 0
        $script:ConfirmationCalls | Should Be 0
    }

    It 'requires a supported purpose' {
        $thrown = $false
        try {
            Invoke-EnergyKeyAliasProvisioning -Name 'unit.alias' -PayaraRoot $script:FixtureHome `
                -Apply -Proceed | Out-Null
        }
        catch { $thrown = $true }
        $thrown | Should Be $true

        $thrown = $false
        try {
            Invoke-EnergyKeyAliasProvisioning -Name 'unit.alias' -KeyPurpose 'Other' `
                -PayaraRoot $script:FixtureHome -Apply -Proceed | Out-Null
        }
        catch { $thrown = $true }
        $thrown | Should Be $true
    }

    It 'applies and reads back a Windows ACL before a password file can be used' {
        $path = Join-Path $script:TestRoot ('acl-' + [Guid]::NewGuid().ToString('N') + '.txt')
        [IO.File]::WriteAllText($path, '')
        try {
            & $script:ActualProtectPasswordFile -Path $path
            $acl = Get-Acl -LiteralPath $path
            $acl.AreAccessRulesProtected | Should Be $true
            @($acl.Access).Count | Should Be 2
        }
        finally {
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
    }
}
