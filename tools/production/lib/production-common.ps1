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

function Assert-QherpDockerName {
    param([Parameter(Mandatory)][string] $Value, [string] $FieldName = "Docker 资源名")

    if ($Value -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') {
        throw "$FieldName 格式非法：$Value"
    }
}

function Assert-QherpDatabaseName {
    param([Parameter(Mandatory)][string] $Value, [string] $FieldName = "数据库名")

    if ($Value -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') {
        throw "$FieldName 格式非法：$Value"
    }
}

function Assert-QherpBucketName {
    param([Parameter(Mandatory)][string] $Value, [string] $FieldName = "bucket")

    if ($Value -notmatch '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$') {
        throw "$FieldName 格式非法：$Value"
    }
}

function Get-QherpContainerEnvironmentValue {
    param(
        [Parameter(Mandatory)][string] $ContainerName,
        [Parameter(Mandatory)][string] $VariableName
    )

    Assert-QherpDockerName -Value $ContainerName -FieldName "容器名"
    if ($VariableName -notmatch '^[A-Z][A-Z0-9_]*$') {
        throw "容器环境变量名格式非法：$VariableName"
    }
    $raw = & docker inspect $ContainerName 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "无法读取容器：$ContainerName"
    }
    $inspection = @($raw | ConvertFrom-Json)
    if ($inspection.Count -ne 1) {
        throw "容器检查结果不唯一：$ContainerName"
    }
    $prefix = "$VariableName="
    $entry = @($inspection[0].Config.Env | Where-Object { $_.StartsWith($prefix, [StringComparison]::Ordinal) })
    if ($entry.Count -ne 1) {
        throw "容器 $ContainerName 缺少环境变量 $VariableName。"
    }
    return $entry[0].Substring($prefix.Length)
}

function Get-QherpMinioCredentialShellPrefix {
    return 'if [ -r "${MINIO_ROOT_USER_FILE:-}" ]; then MINIO_ROOT_USER="$(cat "$MINIO_ROOT_USER_FILE")"; fi; if [ -r "${MINIO_ROOT_PASSWORD_FILE:-}" ]; then MINIO_ROOT_PASSWORD="$(cat "$MINIO_ROOT_PASSWORD_FILE")"; fi; : "${MINIO_ROOT_USER:?缺少 MinIO 根用户}"; : "${MINIO_ROOT_PASSWORD:?缺少 MinIO 根密码}"; export MINIO_ROOT_USER MINIO_ROOT_PASSWORD'
}

function Invoke-QherpPostgresScalar {
    param(
        [Parameter(Mandatory)][string] $ContainerName,
        [Parameter(Mandatory)][string] $DatabaseUsername,
        [Parameter(Mandatory)][string] $DatabaseName,
        [Parameter(Mandatory)][string] $Sql
    )

    Assert-QherpDockerName -Value $ContainerName -FieldName "PostgreSQL 容器名"
    Assert-QherpDatabaseName -Value $DatabaseUsername -FieldName "数据库用户名"
    Assert-QherpDatabaseName -Value $DatabaseName
    $output = @(& docker exec $ContainerName psql -X -v ON_ERROR_STOP=1 -U $DatabaseUsername -d $DatabaseName -Atc $Sql)
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL 查询失败：$ContainerName/$DatabaseName"
    }
    return @($output | ForEach-Object { [string]$_ })
}

function Get-QherpFileSha256 {
    param([Parameter(Mandatory)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "待计算哈希的文件不存在：$Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-QherpBackupFileRecord {
    param(
        [Parameter(Mandatory)][string] $RootPath,
        [Parameter(Mandatory)][string] $FilePath,
        [Parameter(Mandatory)][string] $Kind
    )

    $root = [IO.Path]::GetFullPath($RootPath).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $file = [IO.Path]::GetFullPath($FilePath)
    if (-not $file.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "备份文件不在备份根目录内：$file"
    }
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "备份文件不存在：$file"
    }
    $item = Get-Item -LiteralPath $file
    return [pscustomobject]@{
        Kind = $Kind
        Path = [IO.Path]::GetRelativePath($root, $file).Replace('\', '/')
        SizeBytes = [long]$item.Length
        Sha256 = Get-QherpFileSha256 -Path $file
    }
}

function Test-QherpBackupManifest {
    param([Parameter(Mandatory)][string] $BackupDirectory)

    $root = [IO.Path]::GetFullPath($BackupDirectory)
    $manifestPath = Join-Path $root "manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "备份清单不存在：$manifestPath"
    }
    $manifestHashPath = Join-Path $root "manifest.sha256"
    if (-not (Test-Path -LiteralPath $manifestHashPath -PathType Leaf)) {
        throw "备份清单 SHA256 文件不存在：$manifestHashPath"
    }
    $expectedManifestHash = (Get-Content -LiteralPath $manifestHashPath -Raw).Trim().ToLowerInvariant()
    if ($expectedManifestHash -notmatch '^[0-9a-f]{64}$' -or
        (Get-QherpFileSha256 -Path $manifestPath) -ne $expectedManifestHash) {
        throw "备份清单 SHA256 不一致。"
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -Depth 16
    if ([int]$manifest.SchemaVersion -ne 1 -or [string]$manifest.Status -ne "COMPLETED") {
        throw "备份清单版本或状态无效。"
    }
    foreach ($field in @("BackupId", "StartedAtUtc", "CompletedAtUtc", "DurationSeconds", "Source", "Files", "Objects")) {
        if ($null -eq $manifest.PSObject.Properties[$field] -or $null -eq $manifest.$field) {
            throw "备份清单缺少字段：$field"
        }
    }
    $started = [DateTimeOffset]::Parse([string]$manifest.StartedAtUtc)
    $completed = [DateTimeOffset]::Parse([string]$manifest.CompletedAtUtc)
    if ($completed -lt $started -or [double]$manifest.DurationSeconds -lt 0) {
        throw "备份清单时间范围无效。"
    }
    foreach ($field in @(
        "SourceCommit", "ComposeProject", "PostgresContainer", "DatabaseName",
        "PostgresVersion", "FlywayVersion", "MinioContainer", "Bucket"
    )) {
        if ([string]::IsNullOrWhiteSpace([string]$manifest.Source.$field)) {
            throw "备份来源缺少字段：$field"
        }
    }
    $seenPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($record in @($manifest.Files)) {
        if ([string]::IsNullOrWhiteSpace([string]$record.Kind) -or
            [string]::IsNullOrWhiteSpace([string]$record.Path) -or
            [string]$record.Sha256 -notmatch '^[0-9a-fA-F]{64}$' -or
            [long]$record.SizeBytes -lt 0) {
            throw "备份文件记录无效。"
        }
        $relative = ([string]$record.Path).Replace('/', [IO.Path]::DirectorySeparatorChar)
        $fullPath = [IO.Path]::GetFullPath((Join-Path $root $relative))
        $rootPrefix = $root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if (-not $fullPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "备份文件路径越界：$($record.Path)"
        }
        if (-not $seenPaths.Add([string]$record.Path)) {
            throw "备份文件路径重复：$($record.Path)"
        }
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "备份文件缺失：$($record.Path)"
        }
        $item = Get-Item -LiteralPath $fullPath
        if ([long]$item.Length -ne [long]$record.SizeBytes -or
            (Get-QherpFileSha256 -Path $fullPath) -ne ([string]$record.Sha256).ToLowerInvariant()) {
            throw "备份文件大小或 SHA256 不一致：$($record.Path)"
        }
    }
    if (@($manifest.Files).Count -eq 0) {
        throw "备份清单没有载荷文件。"
    }
    foreach ($object in @($manifest.Objects)) {
        if ([string]::IsNullOrWhiteSpace([string]$object.Key) -or
            [string]$object.Sha256 -notmatch '^[0-9a-fA-F]{64}$' -or
            [long]$object.SizeBytes -lt 0) {
            throw "对象清单记录无效。"
        }
    }
    return $manifest
}

function Assert-QherpRestoreTarget {
    param(
        [Parameter(Mandatory)] $Manifest,
        [Parameter(Mandatory)][string] $TargetPostgresContainer,
        [Parameter(Mandatory)][string] $TargetDatabaseName,
        [Parameter(Mandatory)][string] $TargetMinioContainer,
        [Parameter(Mandatory)][string] $TargetBucket,
        [switch] $Confirmed,
        [switch] $AllowSourceReplacement
    )

    if (-not $Confirmed) {
        throw "恢复属于破坏性操作，必须显式传入 -ConfirmRestore。"
    }
    Assert-QherpDockerName -Value $TargetPostgresContainer -FieldName "目标 PostgreSQL 容器名"
    Assert-QherpDatabaseName -Value $TargetDatabaseName -FieldName "目标数据库名"
    Assert-QherpDockerName -Value $TargetMinioContainer -FieldName "目标 MinIO 容器名"
    Assert-QherpBucketName -Value $TargetBucket -FieldName "目标 bucket"
    $sameDatabase = [string]$Manifest.Source.PostgresContainer -eq $TargetPostgresContainer -and
        [string]$Manifest.Source.DatabaseName -eq $TargetDatabaseName
    $sameBucket = [string]$Manifest.Source.MinioContainer -eq $TargetMinioContainer -and
        [string]$Manifest.Source.Bucket -eq $TargetBucket
    if (($sameDatabase -or $sameBucket) -and -not $AllowSourceReplacement) {
        throw "恢复目标与备份来源重合；默认禁止覆盖来源。灾难恢复确需覆盖时必须另加 -AllowSourceReplacement。"
    }
}

function Wait-QherpContainerHealthy {
    param([Parameter(Mandatory)][string] $ContainerName, [int] $TimeoutSeconds = 180)

    Assert-QherpDockerName -Value $ContainerName -FieldName "容器名"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $status = (& docker inspect $ContainerName --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' 2>$null).Trim()
        if ($LASTEXITCODE -eq 0 -and $status -in @("healthy", "running")) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "容器未在 $TimeoutSeconds 秒内就绪：$ContainerName"
}

function Stop-QherpApplicationContainers {
    param([string] $ApiContainer = "qherp035-api-1", [string] $WebContainer = "qherp035-web-1")

    foreach ($container in @($WebContainer, $ApiContainer)) {
        Assert-QherpDockerName -Value $container -FieldName "应用容器名"
        & docker stop --time 30 $container | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "停止应用容器失败：$container"
        }
    }
}

function Start-QherpApplicationContainers {
    param([string] $ApiContainer = "qherp035-api-1", [string] $WebContainer = "qherp035-web-1")

    & docker start $ApiContainer | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "启动 API 容器失败：$ApiContainer"
    }
    Wait-QherpContainerHealthy -ContainerName $ApiContainer
    & docker start $WebContainer | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "启动 Web 容器失败：$WebContainer"
    }
    Wait-QherpContainerHealthy -ContainerName $WebContainer
}

function Get-QherpPercentile {
    param(
        [Parameter(Mandatory)][double[]] $Values,
        [Parameter(Mandatory)][ValidateRange(0, 100)][double] $Percentile
    )

    if ($Values.Count -eq 0) {
        throw "百分位计算至少需要一个样本。"
    }
    $ordered = @($Values | Sort-Object)
    $rank = [math]::Ceiling(($Percentile / 100.0) * $ordered.Count)
    $index = [math]::Max(0, [math]::Min($ordered.Count - 1, $rank - 1))
    return [double]$ordered[$index]
}

function New-QherpHttpSummary {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][object[]] $Samples,
        [int[]] $ExpectedStatusCodes = @(200)
    )

    if ($Samples.Count -eq 0) {
        throw "HTTP 性能汇总至少需要一个样本。"
    }
    $durations = @($Samples | ForEach-Object { [double]$_.DurationMs })
    $unexpected = @($Samples | Where-Object { [int]$_.StatusCode -notin $ExpectedStatusCodes }).Count
    $serverErrors = @($Samples | Where-Object { [int]$_.StatusCode -ge 500 }).Count
    $distribution = @($Samples | Group-Object StatusCode | Sort-Object { [int]$_.Name } | ForEach-Object {
        [pscustomobject]@{ StatusCode = [int]$_.Name; Count = [int]$_.Count }
    })
    return [pscustomobject]@{
        Name = $Name
        SampleCount = $Samples.Count
        ExpectedStatusCodes = @($ExpectedStatusCodes)
        UnexpectedCount = $unexpected
        UnexpectedRate = [math]::Round($unexpected / [double]$Samples.Count, 6)
        ServerErrorCount = $serverErrors
        AverageMs = [math]::Round((($durations | Measure-Object -Average).Average), 3)
        P50Ms = [math]::Round((Get-QherpPercentile -Values $durations -Percentile 50), 3)
        P95Ms = [math]::Round((Get-QherpPercentile -Values $durations -Percentile 95), 3)
        P99Ms = [math]::Round((Get-QherpPercentile -Values $durations -Percentile 99), 3)
        MinMs = [math]::Round((($durations | Measure-Object -Minimum).Minimum), 3)
        MaxMs = [math]::Round((($durations | Measure-Object -Maximum).Maximum), 3)
        StatusDistribution = $distribution
    }
}

function Test-QherpPerformanceThreshold {
    param(
        [Parameter(Mandatory)] $Summary,
        [Parameter(Mandatory)][double] $MaxP95Ms,
        [Parameter(Mandatory)][double] $MaxUnexpectedRate,
        [switch] $RequireZeroServerErrors
    )

    if ([double]$Summary.P95Ms -gt $MaxP95Ms -or
        [double]$Summary.UnexpectedRate -gt $MaxUnexpectedRate) {
        return $false
    }
    if ($RequireZeroServerErrors -and [int]$Summary.ServerErrorCount -ne 0) {
        return $false
    }
    return $true
}
