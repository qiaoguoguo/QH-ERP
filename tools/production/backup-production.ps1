[CmdletBinding()]
param(
    [string] $SourcePostgresContainer = "qherp035-postgres-1",
    [string] $SourceMinioContainer = "qherp035-minio-1",
    [string] $SourceDatabaseName,
    [string] $SourceBucket,
    [string] $ComposeProject = "qherp035",
    [string] $SourceCommit,
    [string] $OutputDirectory,
    [switch] $QuiesceApplications,
    [switch] $SourceWriteStopped,
    [string] $ApiContainer = "qherp035-api-1",
    [string] $WebContainer = "qherp035-web-1"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

if (-not $QuiesceApplications -and -not $SourceWriteStopped) {
    throw "联合备份前必须使用 -QuiesceApplications 停止写入口，或由调用方确认写入口已停止并传入 -SourceWriteStopped。"
}

Assert-QherpDockerName -Value $SourcePostgresContainer -FieldName "来源 PostgreSQL 容器名"
Assert-QherpDockerName -Value $SourceMinioContainer -FieldName "来源 MinIO 容器名"
Assert-QherpDockerName -Value $ComposeProject -FieldName "Compose 项目名"

if ([string]::IsNullOrWhiteSpace($SourceDatabaseName)) {
    $SourceDatabaseName = Get-QherpContainerEnvironmentValue -ContainerName $SourcePostgresContainer -VariableName "POSTGRES_DB"
}
if ([string]::IsNullOrWhiteSpace($SourceBucket)) {
    if ($ComposeProject -eq "qherp035") {
        $secrets = Import-QherpProductionSecrets
        $SourceBucket = [string]$secrets.S3Bucket
    }
    else {
        throw "非 035 来源必须显式提供 -SourceBucket。"
    }
}
if ([string]::IsNullOrWhiteSpace($SourceCommit)) {
    $SourceCommit = Get-QherpSourceCommit
}
Assert-QherpDatabaseName -Value $SourceDatabaseName
Assert-QherpBucketName -Value $SourceBucket
if ($SourceCommit -notmatch '^[0-9a-f]{40}$') {
    throw "来源提交必须是 40 位小写 Git 哈希。"
}

$databaseUsername = Get-QherpContainerEnvironmentValue -ContainerName $SourcePostgresContainer -VariableName "POSTGRES_USER"
Assert-QherpDatabaseName -Value $databaseUsername -FieldName "来源数据库用户名"
Wait-QherpContainerHealthy -ContainerName $SourcePostgresContainer
Wait-QherpContainerHealthy -ContainerName $SourceMinioContainer

$backupId = "035-$((Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss'))-$($SourceCommit.Substring(0, 8))"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $env:USERPROFILE ".codex\backups\qherp\$backupId"
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "备份目录已存在：$OutputDirectory"
}

$started = [DateTimeOffset]::UtcNow
$applicationsStoppedHere = $false
$remoteDump = "/tmp/$backupId.dump"
$remoteObjects = "/tmp/$backupId-objects"
try {
    if ($QuiesceApplications) {
        Stop-QherpApplicationContainers -ApiContainer $ApiContainer -WebContainer $WebContainer
        $applicationsStoppedHere = $true
    }

    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $objectsDirectory = Join-Path $OutputDirectory "objects"
    New-Item -ItemType Directory -Path $objectsDirectory -Force | Out-Null
    $dumpPath = Join-Path $OutputDirectory "database.dump"

    & docker exec $SourcePostgresContainer pg_dump -U $databaseUsername -d $SourceDatabaseName -Fc -f $remoteDump
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL custom dump 失败。"
    }
    & docker exec $SourcePostgresContainer pg_restore -l $remoteDump | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL custom dump 目录不可读。"
    }
    & docker cp "${SourcePostgresContainer}:$remoteDump" $dumpPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "复制 PostgreSQL dump 失败。"
    }

    $mirrorCommand = 'set -eu; {0}; rm -rf "{1}"; mkdir -p "{1}"; mc alias set qherpbackup http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; mc mirror --overwrite --remove qherpbackup/{2} "{1}"' -f (Get-QherpMinioCredentialShellPrefix), $remoteObjects, $SourceBucket
    & docker exec $SourceMinioContainer sh -c $mirrorCommand | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "MinIO 对象镜像失败。"
    }
    & docker cp "${SourceMinioContainer}:$remoteObjects/." $objectsDirectory | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "复制 MinIO 对象镜像失败。"
    }

    $postgresVersion = @(Invoke-QherpPostgresScalar -ContainerName $SourcePostgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $SourceDatabaseName `
        -Sql "select current_setting('server_version');")[0]
    $flywayVersion = @(Invoke-QherpPostgresScalar -ContainerName $SourcePostgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $SourceDatabaseName `
        -Sql "select version from flyway_schema_history where success order by installed_rank desc limit 1;")[0]
    $flywayRows = @(Invoke-QherpPostgresScalar -ContainerName $SourcePostgresContainer `
        -DatabaseUsername $databaseUsername -DatabaseName $SourceDatabaseName `
        -Sql "select version || '|' || coalesce(checksum::text,'') || '|' || success::text from flyway_schema_history order by installed_rank;")

    $files = [Collections.Generic.List[object]]::new()
    $files.Add((New-QherpBackupFileRecord -RootPath $OutputDirectory -FilePath $dumpPath -Kind "POSTGRES_CUSTOM"))
    $objects = [Collections.Generic.List[object]]::new()
    foreach ($file in @(Get-ChildItem -LiteralPath $objectsDirectory -Recurse -File | Sort-Object FullName)) {
        $record = New-QherpBackupFileRecord -RootPath $OutputDirectory -FilePath $file.FullName -Kind "MINIO_OBJECT"
        $files.Add($record)
        $objects.Add([pscustomobject]@{
            Key = ([IO.Path]::GetRelativePath($objectsDirectory, $file.FullName)).Replace('\', '/')
            SizeBytes = [long]$file.Length
            Sha256 = [string]$record.Sha256
        })
    }

    $completed = [DateTimeOffset]::UtcNow
    $manifest = [ordered]@{
        SchemaVersion = 1
        BackupId = $backupId
        Status = "COMPLETED"
        StartedAtUtc = $started.ToString("o")
        CompletedAtUtc = $completed.ToString("o")
        DurationSeconds = [math]::Round(($completed - $started).TotalSeconds, 3)
        Source = [ordered]@{
            SourceCommit = $SourceCommit
            ComposeProject = $ComposeProject
            PostgresContainer = $SourcePostgresContainer
            DatabaseName = $SourceDatabaseName
            PostgresVersion = $postgresVersion
            FlywayVersion = $flywayVersion
            FlywayHistory = $flywayRows
            MinioContainer = $SourceMinioContainer
            Bucket = $SourceBucket
        }
        Summary = [ordered]@{
            DatabaseDumpBytes = (Get-Item -LiteralPath $dumpPath).Length
            ObjectCount = $objects.Count
            ObjectBytes = [long](($objects | Measure-Object -Property SizeBytes -Sum).Sum ?? 0)
        }
        Files = @($files)
        Objects = @($objects)
    }
    $manifestPath = Join-Path $OutputDirectory "manifest.json"
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    (Get-QherpFileSha256 -Path $manifestPath) | Set-Content -LiteralPath (Join-Path $OutputDirectory "manifest.sha256") -Encoding ascii
    [void](Test-QherpBackupManifest -BackupDirectory $OutputDirectory)

    Write-Host "联合备份完成：$OutputDirectory" -ForegroundColor Green
    Write-Host "数据库：$SourceDatabaseName；Flyway：V$flywayVersion；对象：$($objects.Count)；耗时：$($manifest.DurationSeconds) 秒。"
}
finally {
    & docker exec $SourcePostgresContainer rm -f $remoteDump 2>$null | Out-Null
    & docker exec $SourceMinioContainer rm -rf $remoteObjects 2>$null | Out-Null
    if ($applicationsStoppedHere) {
        Start-QherpApplicationContainers -ApiContainer $ApiContainer -WebContainer $WebContainer
    }
}
