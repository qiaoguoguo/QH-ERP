[CmdletBinding()]
param(
    [string] $BackupDirectory,
    [string] $PostgresContainer = "qherp035-postgres-1",
    [string] $MinioContainer = "qherp035-minio-1",
    [string] $ApiContainer = "qherp035-api-1",
    [string] $WebContainer = "qherp035-web-1",
    [string] $DatabaseName,
    [string] $Bucket,
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

$secrets = Import-QherpProductionSecrets
if ([string]::IsNullOrWhiteSpace($DatabaseName)) {
    $DatabaseName = [string]$secrets.DatabaseName
}
if ([string]::IsNullOrWhiteSpace($Bucket)) {
    $Bucket = [string]$secrets.S3Bucket
}
Assert-QherpDatabaseName -Value $DatabaseName
Assert-QherpBucketName -Value $Bucket
$databaseUsername = Get-QherpContainerEnvironmentValue -ContainerName $PostgresContainer -VariableName "POSTGRES_USER"
$failures = [Collections.Generic.List[string]]::new()

foreach ($container in @($PostgresContainer, $MinioContainer, $ApiContainer, $WebContainer)) {
    try {
        Wait-QherpContainerHealthy -ContainerName $container -TimeoutSeconds 10
    }
    catch {
        $failures.Add($_.Exception.Message)
    }
}

$expectedChecksums = [ordered]@{
    "29" = "774334682"
    "30" = "2130342893"
    "31" = "-2074547591"
    "32" = "249406902"
    "33" = "612501943"
    "34" = "-629066235"
    "35" = "-82801719"
    "36" = "1030907058"
}
$flywayRows = @(Invoke-QherpPostgresScalar -ContainerName $PostgresContainer -DatabaseUsername $databaseUsername `
    -DatabaseName $DatabaseName -Sql "select version || '|' || coalesce(checksum::text,'') || '|' || success::text from flyway_schema_history order by installed_rank;")
$flywayMap = @{}
$failedMigrationCount = 0
foreach ($row in $flywayRows) {
    $parts = $row.Split('|')
    if ($parts.Count -eq 3) {
        $flywayMap[$parts[0]] = $parts[1]
        if ($parts[2] -ne "true") {
            $failedMigrationCount++
        }
    }
}
foreach ($item in $expectedChecksums.GetEnumerator()) {
    if (-not $flywayMap.ContainsKey($item.Key) -or [string]$flywayMap[$item.Key] -ne $item.Value) {
        $failures.Add("Flyway V$($item.Key) checksum 不一致。")
    }
}
$latestFlyway = @(Invoke-QherpPostgresScalar -ContainerName $PostgresContainer -DatabaseUsername $databaseUsername `
    -DatabaseName $DatabaseName -Sql "select version from flyway_schema_history where success order by installed_rank desc limit 1;")[0]
if ($latestFlyway -ne "36" -or $failedMigrationCount -ne 0) {
    $failures.Add("Flyway 必须为 V36 且失败迁移为 0。")
}

$databaseObjectRows = @(Invoke-QherpPostgresScalar -ContainerName $PostgresContainer -DatabaseUsername $databaseUsername `
    -DatabaseName $DatabaseName -Sql "select bucket || '|' || object_key || '|' || size_bytes || '|' || lower(sha256) from platform_file_object where status = 'AVAILABLE' order by object_key;")
$databaseObjects = [Collections.Generic.List[object]]::new()
foreach ($row in $databaseObjectRows) {
    $parts = $row.Split('|', 4)
    if ($parts.Count -ne 4) {
        $failures.Add("数据库文件对象记录格式无效。")
        continue
    }
    if ($parts[0] -ne $Bucket) {
        $failures.Add("数据库文件对象仍引用非目标 bucket：$($parts[0])")
    }
    $databaseObjects.Add([pscustomobject]@{
        Key = $parts[1]
        SizeBytes = [long]$parts[2]
        Sha256 = $parts[3]
    })
}

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "qherp-035-verify-$([guid]::NewGuid().ToString('N'))"
$remoteObjects = "/tmp/qherp-035-verify-$([guid]::NewGuid().ToString('N'))"
$storageObjects = [Collections.Generic.List[object]]::new()
try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    $mirrorCommand = 'set -eu; rm -rf "{0}"; mkdir -p "{0}"; mc alias set qherpverify http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; mc mirror --overwrite --remove qherpverify/{1} "{0}"' -f $remoteObjects, $Bucket
    & docker exec $MinioContainer sh -c $mirrorCommand | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "读取目标 MinIO bucket 失败。"
    }
    & docker cp "${MinioContainer}:$remoteObjects/." $tempRoot | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "复制目标 MinIO 对象用于核验失败。"
    }
    foreach ($file in @(Get-ChildItem -LiteralPath $tempRoot -Recurse -File | Sort-Object FullName)) {
        $storageObjects.Add([pscustomobject]@{
            Key = ([IO.Path]::GetRelativePath($tempRoot, $file.FullName)).Replace('\', '/')
            SizeBytes = [long]$file.Length
            Sha256 = Get-QherpFileSha256 -Path $file.FullName
        })
    }

    $storageByKey = @{}
    foreach ($object in $storageObjects) {
        $storageByKey[[string]$object.Key] = $object
    }
    foreach ($object in $databaseObjects) {
        if (-not $storageByKey.ContainsKey([string]$object.Key)) {
            $failures.Add("MinIO 缺少数据库 AVAILABLE 对象：$($object.Key)")
            continue
        }
        $stored = $storageByKey[[string]$object.Key]
        if ([long]$stored.SizeBytes -ne [long]$object.SizeBytes -or [string]$stored.Sha256 -ne [string]$object.Sha256) {
            $failures.Add("数据库与 MinIO 对象大小或 SHA256 不一致：$($object.Key)")
        }
    }
    if ($databaseObjects.Count -ne $storageObjects.Count) {
        $failures.Add("数据库 AVAILABLE 对象数 $($databaseObjects.Count) 与 MinIO 对象数 $($storageObjects.Count) 不一致。")
    }

    $manifest = $null
    if (-not [string]::IsNullOrWhiteSpace($BackupDirectory)) {
        $manifest = Test-QherpBackupManifest -BackupDirectory $BackupDirectory
        $expectedByKey = @{}
        foreach ($object in @($manifest.Objects)) {
            $expectedByKey[[string]$object.Key] = $object
        }
        if ($expectedByKey.Count -ne $storageObjects.Count) {
            $failures.Add("恢复后对象数与备份清单不一致。")
        }
        foreach ($object in $storageObjects) {
            if (-not $expectedByKey.ContainsKey([string]$object.Key)) {
                $failures.Add("恢复后出现备份清单之外的对象：$($object.Key)")
                continue
            }
            $expected = $expectedByKey[[string]$object.Key]
            if ([long]$expected.SizeBytes -ne [long]$object.SizeBytes -or
                ([string]$expected.Sha256).ToLowerInvariant() -ne [string]$object.Sha256) {
                $failures.Add("恢复对象与备份清单不一致：$($object.Key)")
            }
        }
    }
}
finally {
    & docker exec $MinioContainer rm -rf $remoteObjects 2>$null | Out-Null
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

$webStatus = 0
$healthStatus = 0
$loginSucceeded = $false
try {
    $webStatus = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:45173/").StatusCode
    $healthStatus = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:45173/api/health").StatusCode
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrf = Invoke-RestMethod -WebSession $session -Uri "http://127.0.0.1:45173/api/auth/csrf"
    $headers = @{}
    $headers[[string]$csrf.data.headerName] = [string]$csrf.data.token
    $adminPassword = ConvertFrom-QherpSecureString -Value $secrets.InitialAdminPassword
    try {
        $body = @{ username = "admin"; password = $adminPassword } | ConvertTo-Json -Compress
        $login = Invoke-RestMethod -WebSession $session -Uri "http://127.0.0.1:45173/api/auth/login" `
            -Method Post -Headers $headers -ContentType "application/json" -Body $body
        $loginSucceeded = [bool]$login.success -and [string]$login.data.user.username -eq "admin"
    }
    finally {
        $adminPassword = $null
        $body = $null
    }
}
catch {
    $failures.Add("Web、API 健康或管理员登录验证失败：$($_.Exception.Message)")
}
if ($webStatus -ne 200 -or $healthStatus -ne 200 -or -not $loginSucceeded) {
    $failures.Add("Web、API 健康与管理员登录必须全部成功。")
}

$report = [ordered]@{
    SchemaVersion = 1
    CheckedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    SourceCommit = Get-QherpSourceCommit
    DatabaseName = $DatabaseName
    Bucket = $Bucket
    FlywayVersion = $latestFlyway
    FailedMigrationCount = $failedMigrationCount
    DatabaseAvailableObjectCount = $databaseObjects.Count
    MinioObjectCount = $storageObjects.Count
    WebStatus = $webStatus
    HealthStatus = $healthStatus
    AdminLoginSucceeded = $loginSucceeded
    Passed = $failures.Count -eq 0
    Failures = @($failures)
}
if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $directory = Split-Path -Parent ([IO.Path]::GetFullPath($OutputPath))
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
}
if ($failures.Count -gt 0) {
    foreach ($failure in $failures) {
        Write-Host "- $failure" -ForegroundColor Red
    }
    throw "生产验证失败，共 $($failures.Count) 项。"
}
Write-Host "生产验证通过：Flyway V$latestFlyway，数据库/MinIO 对象 $($databaseObjects.Count)/$($storageObjects.Count)。" -ForegroundColor Green
