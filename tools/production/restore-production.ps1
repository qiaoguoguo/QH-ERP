[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $BackupDirectory,
    [string] $TargetPostgresContainer = "qherp035-postgres-1",
    [string] $TargetMinioContainer = "qherp035-minio-1",
    [string] $TargetDatabaseName,
    [string] $TargetBucket,
    [string] $ApiContainer = "qherp035-api-1",
    [string] $WebContainer = "qherp035-web-1",
    [switch] $ConfirmRestore,
    [switch] $AllowSourceReplacement,
    [switch] $SkipVerification
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

$BackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)
$manifest = Test-QherpBackupManifest -BackupDirectory $BackupDirectory
$secrets = Import-QherpProductionSecrets
if ([string]::IsNullOrWhiteSpace($TargetDatabaseName)) {
    $TargetDatabaseName = [string]$secrets.DatabaseName
}
if ([string]::IsNullOrWhiteSpace($TargetBucket)) {
    $TargetBucket = [string]$secrets.S3Bucket
}

Assert-QherpRestoreTarget -Manifest $manifest -TargetPostgresContainer $TargetPostgresContainer `
    -TargetDatabaseName $TargetDatabaseName -TargetMinioContainer $TargetMinioContainer `
    -TargetBucket $TargetBucket -Confirmed:$ConfirmRestore -AllowSourceReplacement:$AllowSourceReplacement
Wait-QherpContainerHealthy -ContainerName $TargetPostgresContainer
Wait-QherpContainerHealthy -ContainerName $TargetMinioContainer

$databaseUsername = Get-QherpContainerEnvironmentValue -ContainerName $TargetPostgresContainer -VariableName "POSTGRES_USER"
Assert-QherpDatabaseName -Value $databaseUsername -FieldName "目标数据库用户名"
$dumpRecord = @($manifest.Files | Where-Object { [string]$_.Kind -eq "POSTGRES_CUSTOM" })
if ($dumpRecord.Count -ne 1) {
    throw "备份清单必须包含且只包含一个 PostgreSQL custom dump。"
}
$dumpPath = Join-Path $BackupDirectory (([string]$dumpRecord[0].Path).Replace('/', [IO.Path]::DirectorySeparatorChar))
$objectsDirectory = Join-Path $BackupDirectory "objects"
if (-not (Test-Path -LiteralPath $objectsDirectory -PathType Container)) {
    throw "备份缺少 MinIO 对象目录。"
}

$restoreId = "qherp-restore-$([guid]::NewGuid().ToString('N'))"
$remoteDump = "/tmp/$restoreId.dump"
$remoteObjects = "/tmp/$restoreId-objects"
$started = [DateTimeOffset]::UtcNow

Stop-QherpApplicationContainers -ApiContainer $ApiContainer -WebContainer $WebContainer
try {
    & docker cp $dumpPath "${TargetPostgresContainer}:$remoteDump" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "复制数据库备份到目标容器失败。"
    }
    $terminateSql = "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$TargetDatabaseName' and pid <> pg_backend_pid();"
    & docker exec $TargetPostgresContainer psql -X -v ON_ERROR_STOP=1 -U $databaseUsername -d postgres -c $terminateSql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "终止目标数据库连接失败。"
    }
    & docker exec $TargetPostgresContainer dropdb -U $databaseUsername --if-exists $TargetDatabaseName
    if ($LASTEXITCODE -ne 0) {
        throw "删除目标数据库失败。"
    }
    & docker exec $TargetPostgresContainer createdb -U $databaseUsername $TargetDatabaseName
    if ($LASTEXITCODE -ne 0) {
        throw "创建目标数据库失败。"
    }
    & docker exec $TargetPostgresContainer pg_restore -U $databaseUsername -d $TargetDatabaseName `
        --exit-on-error --no-owner --no-privileges $remoteDump
    if ($LASTEXITCODE -ne 0) {
        throw "恢复 PostgreSQL custom dump 失败。"
    }
    if ([string]$manifest.Source.Bucket -ne $TargetBucket) {
        $mapBucketSql = "update platform_file_object set bucket = '$TargetBucket' where bucket = '$($manifest.Source.Bucket)';"
        & docker exec $TargetPostgresContainer psql -X -v ON_ERROR_STOP=1 -U $databaseUsername `
            -d $TargetDatabaseName -c $mapBucketSql | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "更新恢复目标的对象 bucket 定位失败。"
        }
    }

    $prepareBucket = 'set -eu; mc alias set qherprestore http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; if mc stat qherprestore/{0} >/dev/null 2>&1; then mc rm --recursive --force qherprestore/{0} >/dev/null; mc rb qherprestore/{0} >/dev/null; fi; mc mb qherprestore/{0} >/dev/null; rm -rf "{1}"; mkdir -p "{1}"' -f $TargetBucket, $remoteObjects
    & docker exec $TargetMinioContainer sh -c $prepareBucket
    if ($LASTEXITCODE -ne 0) {
        throw "重建目标 bucket 失败。"
    }
    & docker cp "$objectsDirectory/." "${TargetMinioContainer}:$remoteObjects" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "复制对象备份到目标容器失败。"
    }
    $restoreObjects = 'set -eu; mc alias set qherprestore http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; mc mirror --overwrite --remove "{0}" qherprestore/{1}' -f $remoteObjects, $TargetBucket
    & docker exec $TargetMinioContainer sh -c $restoreObjects | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "恢复 MinIO 对象失败。"
    }

    Start-QherpApplicationContainers -ApiContainer $ApiContainer -WebContainer $WebContainer
    if (-not $SkipVerification) {
        & (Join-Path $PSScriptRoot "verify-production.ps1") -BackupDirectory $BackupDirectory `
            -PostgresContainer $TargetPostgresContainer -MinioContainer $TargetMinioContainer `
            -DatabaseName $TargetDatabaseName -Bucket $TargetBucket
        if ($LASTEXITCODE -ne 0) {
            throw "恢复后生产验证失败。"
        }
    }
    $completed = [DateTimeOffset]::UtcNow
    Write-Host "联合恢复完成；耗时 $([math]::Round(($completed - $started).TotalSeconds, 3)) 秒。" -ForegroundColor Green
}
catch {
    & docker stop --time 30 $WebContainer $ApiContainer 2>$null | Out-Null
    Write-Error "联合恢复失败，API/Web 保持停止以避免向不完整数据写入：$($_.Exception.Message)"
    throw
}
finally {
    & docker exec $TargetPostgresContainer rm -f $remoteDump 2>$null | Out-Null
    & docker exec $TargetMinioContainer rm -rf $remoteObjects 2>$null | Out-Null
}
