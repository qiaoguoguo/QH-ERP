[CmdletBinding()]
param(
    [ValidateSet("Config", "Build", "Up", "Down", "Restart", "Status", "Logs")]
    [string] $Action = "Status",
    [string] $SecretPath,
    [switch] $SecureCookie,
    [switch] $RemoveVolumes
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

if ([string]::IsNullOrWhiteSpace($SecretPath)) {
    $SecretPath = Get-QherpDefaultSecretPath
}
$repoRoot = Get-QherpRepoRoot
$secrets = Import-QherpProductionSecrets -SecretPath $SecretPath
$context = Set-QherpProductionEnvironment -Secrets $secrets -RepoRoot $repoRoot -SecureCookie:$SecureCookie

switch ($Action) {
    "Config" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("config", "--quiet")
        Write-Host "生产 Compose 配置有效；提交 $($context.SourceCommit)，镜像标签 $($context.ImageTag)。" -ForegroundColor Green
    }
    "Build" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("config", "--quiet")
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("build", "api", "web")
    }
    "Up" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("config", "--quiet")
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("up", "-d", "--remove-orphans", "--wait", "--wait-timeout", "180")
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("ps")
    }
    "Down" {
        $arguments = @("down", "--remove-orphans")
        if ($RemoveVolumes) {
            $arguments += "--volumes"
        }
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments $arguments
    }
    "Restart" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("restart")
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("up", "-d", "--wait", "--wait-timeout", "180")
    }
    "Status" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("ps")
    }
    "Logs" {
        Invoke-QherpCompose -RepoRoot $repoRoot -Arguments @("logs", "--tail", "200", "api", "web", "postgres", "minio")
    }
}
