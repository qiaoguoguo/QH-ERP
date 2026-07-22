$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-QherpRepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
}

function Get-QherpDefaultSecretPath {
    return Join-Path $env:USERPROFILE ".codex\secrets\qherp\035\runtime-secrets.clixml"
}

function Get-QherpProductionSecretFields {
    return @(
        "SchemaVersion",
        "CreatedAt",
        "ProjectName",
        "DatabaseName",
        "DatabaseUsername",
        "DatabasePassword",
        "MinioRootUser",
        "MinioRootPassword",
        "InitialAdminPassword",
        "S3Bucket"
    )
}

function Assert-QherpProductionSecrets {
    param([Parameter(Mandatory)] $Secrets)

    foreach ($field in Get-QherpProductionSecretFields) {
        $property = $Secrets.PSObject.Properties[$field]
        if ($null -eq $property -or $null -eq $property.Value) {
            throw "生产密钥载荷缺少字段：$field"
        }
    }
    if ([int]$Secrets.SchemaVersion -ne 1) {
        throw "不支持的生产密钥版本：$($Secrets.SchemaVersion)"
    }
    if ([string]$Secrets.ProjectName -ne "qherp035") {
        throw "生产密钥项目名必须为 qherp035。"
    }
    foreach ($field in @("DatabasePassword", "MinioRootPassword", "InitialAdminPassword")) {
        if ($Secrets.$field -isnot [Security.SecureString] -or $Secrets.$field.Length -lt 8) {
            throw "生产密钥字段 $field 必须是至少 8 位的 SecureString。"
        }
    }
    foreach ($field in @("DatabaseName", "DatabaseUsername", "MinioRootUser", "S3Bucket")) {
        if ([string]::IsNullOrWhiteSpace([string]$Secrets.$field)) {
            throw "生产密钥字段 $field 不能为空。"
        }
    }
}

function Import-QherpProductionSecrets {
    param([string] $SecretPath = (Get-QherpDefaultSecretPath))

    if (-not (Test-Path -LiteralPath $SecretPath -PathType Leaf)) {
        throw "生产密钥文件不存在：$SecretPath。请先运行 initialize-secrets.ps1。"
    }
    $secrets = Import-Clixml -LiteralPath $SecretPath
    Assert-QherpProductionSecrets -Secrets $secrets
    return $secrets
}

function ConvertFrom-QherpSecureString {
    param([Parameter(Mandatory)][Security.SecureString] $Value)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Get-QherpSourceCommit {
    param([string] $RepoRoot = (Get-QherpRepoRoot))

    $commit = (& git -C $RepoRoot rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {
        throw "无法读取当前 Git 提交。"
    }
    return $commit
}

function Set-QherpProductionEnvironment {
    param(
        [Parameter(Mandatory)] $Secrets,
        [string] $RepoRoot = (Get-QherpRepoRoot),
        [bool] $SecureCookie = $false
    )

    Assert-QherpProductionSecrets -Secrets $Secrets
    $commit = Get-QherpSourceCommit -RepoRoot $RepoRoot
    $values = [ordered]@{
        QHERP_POSTGRES_DB = [string]$Secrets.DatabaseName
        QHERP_POSTGRES_USER = [string]$Secrets.DatabaseUsername
        QHERP_POSTGRES_PASSWORD = ConvertFrom-QherpSecureString $Secrets.DatabasePassword
        QHERP_MINIO_ROOT_USER = [string]$Secrets.MinioRootUser
        QHERP_MINIO_ROOT_PASSWORD = ConvertFrom-QherpSecureString $Secrets.MinioRootPassword
        QHERP_INITIAL_ADMIN_PASSWORD = ConvertFrom-QherpSecureString $Secrets.InitialAdminPassword
        QHERP_S3_BUCKET = [string]$Secrets.S3Bucket
        QHERP_SESSION_COOKIE_SECURE = $SecureCookie.ToString().ToLowerInvariant()
        QHERP_SOURCE_COMMIT = $commit
        QHERP_IMAGE_TAG = "035-$($commit.Substring(0, 12))"
    }
    foreach ($item in $values.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($item.Key, [string]$item.Value, "Process")
    }

    return [pscustomobject]@{
        ProjectName = [string]$Secrets.ProjectName
        SourceCommit = $commit
        ImageTag = $values.QHERP_IMAGE_TAG
        DatabaseName = [string]$Secrets.DatabaseName
        S3Bucket = [string]$Secrets.S3Bucket
        SecureCookie = $SecureCookie
    }
}

function Invoke-QherpCompose {
    param(
        [Parameter(Mandatory)][string[]] $Arguments,
        [string] $RepoRoot = (Get-QherpRepoRoot)
    )

    $composePath = Join-Path $RepoRoot "compose.production.yaml"
    & docker compose --project-directory $RepoRoot -f $composePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose 执行失败：$($Arguments -join ' ')"
    }
}

function Protect-QherpSecretFile {
    param([Parameter(Mandatory)][string] $SecretPath)

    if (-not $IsWindows) {
        throw "035 DPAPI 密钥载体只支持 Windows 当前用户。"
    }
    $acl = Get-Acl -LiteralPath $SecretPath
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) {
        [void]$acl.RemoveAccessRuleAll($rule)
    }
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $rule = [Security.AccessControl.FileSystemAccessRule]::new(
        $identity,
        [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.AccessControlType]::Allow
    )
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $SecretPath -AclObject $acl
}
