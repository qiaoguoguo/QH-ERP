[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet("Empty", "V34")][string] $Mode,
    [string] $SourceDumpPath,
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

if ($Mode -eq "V34") {
    if ([string]::IsNullOrWhiteSpace($SourceDumpPath) -or
        -not (Test-Path -LiteralPath $SourceDumpPath -PathType Leaf)) {
        throw "V34 演练必须提供可读的 -SourceDumpPath。"
    }
    $SourceDumpPath = [IO.Path]::GetFullPath($SourceDumpPath)
}

$commit = Get-QherpSourceCommit
$apiImage = "qherp/api:035-$($commit.Substring(0, 12))"
& docker image inspect $apiImage | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "迁移演练所需 API 镜像不存在：$apiImage"
}

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$network = "qherp035-migration-$suffix"
$postgresContainer = "qherp035-migration-postgres-$suffix"
$apiContainer = "qherp035-migration-api-$suffix"
$databaseName = "qherp_migration_$suffix"
$databaseUsername = "qherp"
$databasePassword = "MigAa!$([guid]::NewGuid().ToString('N'))"
$adminPassword = "AdminAa!$([guid]::NewGuid().ToString('N'))"
$s3AccessKey = "migration-$suffix"
$s3SecretKey = "MigS3Aa!$([guid]::NewGuid().ToString('N'))"
$secretDirectory = Join-Path ([IO.Path]::GetTempPath()) "qherp035-migration-secrets-$suffix"
$databasePasswordPath = Join-Path $secretDirectory "postgres-password"
$adminPasswordPath = Join-Path $secretDirectory "initial-admin-password"
$s3AccessKeyPath = Join-Path $secretDirectory "s3-access-key"
$s3SecretKeyPath = Join-Path $secretDirectory "s3-secret-key"
$remoteDump = "/tmp/source-v34.dump"
$started = [DateTimeOffset]::UtcNow
$preVersion = if ($Mode -eq "Empty") { "EMPTY" } else { "UNKNOWN" }
$postVersion = "UNKNOWN"
$postChecksums = @()

try {
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    Protect-QherpSecretFile -SecretPath $secretDirectory
    foreach ($secretFile in @(
        @{ Path = $databasePasswordPath; Value = $databasePassword },
        @{ Path = $adminPasswordPath; Value = $adminPassword },
        @{ Path = $s3AccessKeyPath; Value = $s3AccessKey },
        @{ Path = $s3SecretKeyPath; Value = $s3SecretKey }
    )) {
        [IO.File]::WriteAllText([string]$secretFile.Path, [string]$secretFile.Value, [Text.UTF8Encoding]::new($false))
        Protect-QherpSecretFile -SecretPath ([string]$secretFile.Path)
    }

    & docker network create $network | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "创建迁移演练网络失败。"
    }
    & docker run -d --name $postgresContainer --network $network `
        -e "POSTGRES_DB=$databaseName" -e "POSTGRES_USER=$databaseUsername" `
        -e "POSTGRES_PASSWORD_FILE=/run/secrets/postgres-password" `
        --mount "type=bind,source=$databasePasswordPath,target=/run/secrets/postgres-password,readonly" `
        "postgres:18-alpine@sha256:1b1689b20d16a014a3d195653381cf2caa75a41a92d93b255a9d6ea29fd353aa" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "启动迁移演练 PostgreSQL 失败。"
    }
    $deadline = (Get-Date).AddSeconds(120)
    do {
        & docker exec $postgresContainer pg_isready -U $databaseUsername -d $databaseName 2>$null | Out-Null
        $ready = $LASTEXITCODE -eq 0
        if (-not $ready) {
            Start-Sleep -Seconds 2
        }
    } while (-not $ready -and (Get-Date) -lt $deadline)
    if (-not $ready) {
        throw "迁移演练 PostgreSQL 未在 120 秒内就绪。"
    }

    if ($Mode -eq "Empty") {
        $emptyResult = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
            -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
            -Sql "select to_regclass('public.flyway_schema_history') is null;")[0]
        if ($emptyResult -ne "t") {
            throw "空库演练前置条件失败：目标并非空库。"
        }
    }
    else {
        & docker cp $SourceDumpPath "${postgresContainer}:$remoteDump" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "复制 V34 来源 dump 失败。"
        }
        & docker exec $postgresContainer pg_restore -U $databaseUsername -d $databaseName `
            --exit-on-error --no-owner --no-privileges $remoteDump
        if ($LASTEXITCODE -ne 0) {
            throw "恢复 V34 来源 dump 失败。"
        }
        $preVersion = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
            -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
            -Sql "select version from flyway_schema_history where success order by installed_rank desc limit 1;")[0]
        $preChecksum = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
            -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
            -Sql "select checksum from flyway_schema_history where success and version = '34';")[0]
        if ($preVersion -ne "34" -or $preChecksum -ne "-629066235") {
            throw "V34 来源 dump 版本或 checksum 不符合冻结契约。"
        }
    }

    & docker run -d --name $apiContainer --network $network `
        -e "SPRING_PROFILES_ACTIVE=production" `
        -e "QHERP_DATASOURCE_URL=jdbc:postgresql://${postgresContainer}:5432/$databaseName" `
        -e "QHERP_DATASOURCE_USERNAME=$databaseUsername" `
        -e "QHERP_S3_ENDPOINT=http://127.0.0.1:9000" `
        -e "QHERP_S3_REGION=us-east-1" -e "QHERP_S3_BUCKET=qherp-migration-$suffix" `
        -e "QHERP_S3_PATH_STYLE=true" -e "QHERP_SESSION_COOKIE_SECURE=false" `
        -e "QHERP_TASK_WORKER_ENABLED=false" -e "QHERP_APPROVAL_ENFORCE_DIRECT_ACTIONS=true" `
        --mount "type=bind,source=$databasePasswordPath,target=/run/secrets/spring.datasource.password,readonly" `
        --mount "type=bind,source=$adminPasswordPath,target=/run/secrets/qherp.account-permission.initial-admin-password,readonly" `
        --mount "type=bind,source=$s3AccessKeyPath,target=/run/secrets/qherp.storage.s3.access-key,readonly" `
        --mount "type=bind,source=$s3SecretKeyPath,target=/run/secrets/qherp.storage.s3.secret-key,readonly" `
        $apiImage | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "启动迁移演练 API 失败。"
    }
    $deadline = (Get-Date).AddSeconds(180)
    $healthy = $false
    do {
        & docker exec $apiContainer curl --fail --silent --show-error http://127.0.0.1:8080/api/health 2>$null | Out-Null
        $healthy = $LASTEXITCODE -eq 0
        if (-not $healthy) {
            $running = (& docker inspect $apiContainer --format '{{.State.Running}}' 2>$null).Trim()
            if ($running -eq "false") {
                break
            }
            Start-Sleep -Seconds 2
        }
    } while (-not $healthy -and (Get-Date) -lt $deadline)
    if (-not $healthy) {
        & docker logs --tail 120 $apiContainer 2>&1
        throw "迁移演练 API 未在 180 秒内健康。"
    }

    $postVersion = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
        -Sql "select version from flyway_schema_history where success order by installed_rank desc limit 1;")[0]
    $failedCount = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
        -Sql "select count(*) from flyway_schema_history where not success;")[0]
    $postChecksums = @(Invoke-QherpPostgresScalar -ContainerName $postgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $databaseName `
        -Sql "select version || '|' || checksum from flyway_schema_history where success and version in ('29','30','31','32','33','34','35','36') order by installed_rank;")
    if ($postVersion -ne "36" -or $failedCount -ne "0" -or
        $postChecksums -notcontains "35|-82801719" -or $postChecksums -notcontains "36|1030907058") {
        throw "迁移演练未得到 V36、失败迁移 0 和冻结 checksum。"
    }

    $completed = [DateTimeOffset]::UtcNow
    $report = [ordered]@{
        SchemaVersion = 1
        Mode = $Mode
        SourceCommit = $commit
        ApiImage = $apiImage
        StartedAtUtc = $started.ToString("o")
        CompletedAtUtc = $completed.ToString("o")
        DurationSeconds = [math]::Round(($completed - $started).TotalSeconds, 3)
        PreMigrationVersion = $preVersion
        PostMigrationVersion = $postVersion
        FailedMigrationCount = 0
        Checksums = $postChecksums
        Passed = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $OutputPath = [IO.Path]::GetFullPath($OutputPath)
        New-Item -ItemType Directory -Path (Split-Path -Parent $OutputPath) -Force | Out-Null
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
    }
    Write-Host "$Mode 迁移演练通过：$preVersion -> V$postVersion；耗时 $($report.DurationSeconds) 秒。" -ForegroundColor Green
}
finally {
    $databasePassword = $null
    $adminPassword = $null
    $s3AccessKey = $null
    $s3SecretKey = $null
    & docker rm -f $apiContainer $postgresContainer 2>$null | Out-Null
    & docker network rm $network 2>$null | Out-Null
    $resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $resolvedSecretDirectory = [IO.Path]::GetFullPath($secretDirectory)
    if ($resolvedSecretDirectory.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedSecretDirectory) -like "qherp035-migration-secrets-*") {
        Remove-Item -LiteralPath $resolvedSecretDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}
