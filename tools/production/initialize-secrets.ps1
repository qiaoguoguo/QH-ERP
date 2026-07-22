[CmdletBinding()]
param(
    [string] $SecretPath,
    [Security.SecureString] $InitialAdminPassword,
    [switch] $Force
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

if ([string]::IsNullOrWhiteSpace($SecretPath)) {
    $SecretPath = Get-QherpDefaultSecretPath
}
if ((Test-Path -LiteralPath $SecretPath) -and -not $Force) {
    throw "生产密钥文件已存在：$SecretPath。确需轮换时使用 -Force。"
}

function New-RandomSecret {
    param([int] $ByteCount = 32)
    $bytes = [byte[]]::new($ByteCount)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $plain = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return ConvertTo-SecureString $plain -AsPlainText -Force
}

if ($null -eq $InitialAdminPassword) {
    if (-not [string]::IsNullOrWhiteSpace($env:QHERP_INITIAL_ADMIN_PASSWORD)) {
        $InitialAdminPassword = ConvertTo-SecureString $env:QHERP_INITIAL_ADMIN_PASSWORD -AsPlainText -Force
    }
    else {
        $InitialAdminPassword = Read-Host "请输入 035 超级管理员密码" -AsSecureString
    }
}
if ($InitialAdminPassword.Length -lt 8) {
    throw "超级管理员密码至少需要 8 位。"
}

$directory = Split-Path -Parent $SecretPath
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$secrets = [pscustomobject]@{
    SchemaVersion = 1
    CreatedAt = (Get-Date).ToString("o")
    ProjectName = "qherp035"
    DatabaseName = "qherp_035_candidate"
    DatabaseUsername = "qherp"
    DatabasePassword = New-RandomSecret
    MinioRootUser = "qherp035"
    MinioRootPassword = New-RandomSecret -ByteCount 36
    InitialAdminPassword = $InitialAdminPassword
    S3Bucket = "qherp-035-candidate"
}

Assert-QherpProductionSecrets -Secrets $secrets
$secrets | Export-Clixml -LiteralPath $SecretPath -Force
Protect-QherpSecretFile -SecretPath $SecretPath
[void](Import-QherpProductionSecrets -SecretPath $SecretPath)

Write-Host "035 DPAPI 生产密钥已初始化：$SecretPath" -ForegroundColor Green
Write-Host "密钥版本：1；项目：qherp035；明文未写入仓库或输出。"
