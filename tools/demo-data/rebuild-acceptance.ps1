[CmdletBinding()]
param(
    [ValidateSet("Temporary", "Acceptance")][string] $Mode = "Temporary",
    [string] $ApiBaseUrl = $(if ($env:QHERP_API_BASE_URL) { $env:QHERP_API_BASE_URL } else { "http://127.0.0.1:18081" }),
    [string] $Database = $(if ($env:QHERP_POSTGRES_DB) { $env:QHERP_POSTGRES_DB } else { "qherp_demo_build_20260715" }),
    [string] $MinioBucket = $(if ($env:QHERP_S3_BUCKET) { $env:QHERP_S3_BUCKET } else { "qherp-demo-build-20260715" }),
    [string] $ConfirmPhrase,
    [string] $PostgresContainer = $(if ($env:QHERP_POSTGRES_CONTAINER) { $env:QHERP_POSTGRES_CONTAINER } else { "qherp-postgres" }),
    [string] $PostgresUser = $(if ($env:QHERP_POSTGRES_USER) { $env:QHERP_POSTGRES_USER } else { "qherp" }),
    [string] $MinioContainer = $(if ($env:QHERP_MINIO_CONTAINER) { $env:QHERP_MINIO_CONTAINER } else { "qherp-minio" }),
    [string] $RunId = "DEMO-ELEC-20260715-RUN",
    [string] $OutputDirectory = (Join-Path (Get-Location).Path "apps/api/target/demo-data/rebuild-acceptance"),
    [string] $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path,
    [string] $ExpectedGitCommit = $env:QHERP_EXPECTED_GIT_COMMIT,
    [string] $JavaHome = $env:JAVA_HOME,
    [int] $ApiStartTimeoutSeconds = 180,
    [switch] $RecreateTarget,
    [switch] $RunGeneratorAndValidate,
    [switch] $Stage034Only
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib/demo-data-common.ps1")
. (Join-Path $PSScriptRoot "lib/stage034-isolation-strategy.ps1")

function Write-RebuildInfo {
    param([string] $Message)
    Write-Host "[demo-data-rebuild] $Message"
}

function Assert-RebuildTarget {
    $uri = [uri]$ApiBaseUrl
    if ([string]::IsNullOrWhiteSpace($env:QHERP_INITIAL_ADMIN_PASSWORD)) {
        throw "缺少 QHERP_INITIAL_ADMIN_PASSWORD；重建入口不接受仓库内明文默认密码。"
    }
    if ([string]::IsNullOrWhiteSpace($env:QHERP_DEMO_USER_PASSWORD)) {
        throw "缺少 QHERP_DEMO_USER_PASSWORD；重建入口不接受仓库内明文默认密码。"
    }
    if ($Stage034Only) {
        if ($Mode -ne "Temporary") {
            throw "Stage034Only 模式只允许 Mode=Temporary，并且必须使用 034 隔离资源。"
        }
        Assert-Stage034IsolationTarget -Database $Database -MinioBucket $MinioBucket -ApiBaseUrl $ApiBaseUrl
        Assert-Stage034MinioCredentials
        Write-RebuildInfo "Stage034Only 隔离资源固定为 qherp_034_delivery_governance/qherp-034-delivery-governance，端口 35432/39000/39001/38080/35174/35173。"
        return
    }
    if ($Mode -eq "Temporary") {
        if (-not (Test-DemoResourceName -Value $Database -Prefix "qherp_demo_build_")) {
            throw "Temporary 模式只允许 qherp_demo_build_* 临时数据库，当前为 $Database。"
        }
        if (-not (Test-DemoResourceName -Value $MinioBucket -Prefix "qherp-demo-build-")) {
            throw "Temporary 模式只允许 qherp-demo-build-* 临时 bucket，当前为 $MinioBucket。"
        }
        if ($uri.Port -eq 18080) {
            throw "Temporary 模式禁止连接 18080 正式后端，当前 ApiBaseUrl=$ApiBaseUrl。"
        }
        return
    }

    $expected = "REBUILD qherp/qherp-private ON 18080"
    if ($ConfirmPhrase -ne $expected) {
        throw "Acceptance 模式需要精确确认词：$expected。"
    }
    if ($Database -ne "qherp") {
        throw "Acceptance 模式只允许 Database=qherp，当前为 $Database。"
    }
    if ($MinioBucket -ne "qherp-private") {
        throw "Acceptance 模式只允许 MinioBucket=qherp-private，当前为 $MinioBucket。"
    }
    if ($uri.Port -ne 18080) {
        throw "Acceptance 模式只允许 18080 正式验收后端，当前 ApiBaseUrl=$ApiBaseUrl。"
    }
    Assert-LoopbackApiBaseUrl -Uri $uri
    if ([string]::IsNullOrWhiteSpace($ExpectedGitCommit)) {
        throw "Acceptance 模式必须显式传入 ExpectedGitCommit，不能依赖未知旧进程或隐式提交。"
    }
    Assert-RepositoryReady
    Assert-PortAvailable -Port 18080
}

function Assert-Stage034MinioCredentials {
    $missing = New-Object System.Collections.Generic.List[string]
    if ([string]::IsNullOrWhiteSpace($env:QHERP_S3_ACCESS_KEY)) {
        $missing.Add("缺少 QHERP_S3_ACCESS_KEY")
    }
    if ([string]::IsNullOrWhiteSpace($env:QHERP_S3_SECRET_KEY)) {
        $missing.Add("缺少 QHERP_S3_SECRET_KEY")
    }
    if ($missing.Count -gt 0) {
        throw "Stage034Only $($missing -join '；')；MinIO access key/secret 必须通过显式环境变量或安全参数注入，重建入口不提供默认明文密钥。"
    }
}

function Assert-LoopbackApiBaseUrl {
    param([uri] $Uri)
    $hostName = $Uri.Host.ToLowerInvariant()
    if ($hostName -notin @("127.0.0.1", "localhost", "::1", "[::1]")) {
        throw "Acceptance 模式 ApiBaseUrl 必须限定 loopback，当前 Host=$($Uri.Host)。"
    }
}

function Quote-PostgresIdentifier {
    param([Parameter(Mandatory = $true)][string] $Value)
    return '"' + ($Value.Replace('"', '""')) + '"'
}

function Quote-PostgresLiteral {
    param([Parameter(Mandatory = $true)][string] $Value)
    return "'" + ($Value.Replace("'", "''")) + "'"
}

function Assert-PostgresOwner {
    $allowedOwners = @("qherp")
    if ($PostgresUser -notin $allowedOwners) {
        throw "PostgreSQL owner 只允许固定白名单 qherp，当前 PostgresUser=$PostgresUser。"
    }
    return $PostgresUser
}

function Assert-PortAvailable {
    param([int] $Port)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        $pids = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ","
        throw "Acceptance 模式拒绝复用已占用的 $Port 端口；请先停止旧 QH ERP 进程。监听 PID=$pids。"
    }
}

function Assert-RepositoryReady {
    if (-not (Test-Path -LiteralPath $RepositoryRoot)) {
        throw "RepositoryRoot 不存在：$RepositoryRoot"
    }
    $actualCommit = (Invoke-CheckedProcess -FilePath "git" -ArgumentList @("rev-parse", "HEAD") `
        -InputText $null).stdout.Trim()
    if ($actualCommit -ne $ExpectedGitCommit) {
        throw "RepositoryRoot 提交不匹配：expected=$ExpectedGitCommit actual=$actualCommit root=$RepositoryRoot。"
    }
    $status = (Invoke-CheckedProcess -FilePath "git" -ArgumentList @("status", "--porcelain") `
        -InputText $null).stdout.Trim()
    if (-not [string]::IsNullOrWhiteSpace($status)) {
        throw "Acceptance 模式要求工作树干净后启动正式重建 API。当前未提交变更：$status"
    }
}

function Invoke-CheckedProcess {
    param(
        [string] $FilePath,
        [string[]] $ArgumentList,
        [string] $InputText = $null,
        [switch] $AllowFailure
    )
    return Invoke-DemoProcess -FilePath $FilePath -ArgumentList $ArgumentList -InputText $InputText `
        -WorkingDirectory $RepositoryRoot -AllowFailure:$AllowFailure
}

function Test-DatabaseExists {
    $sql = "select 1 from pg_database where datname = $(Quote-PostgresLiteral -Value $Database);"
    $result = Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", "-i", $PostgresContainer,
        "psql", "-q", "-U", $PostgresUser, "-d", "postgres", "-t", "-A", "-c", $sql) -AllowFailure
    return $result.exitCode -eq 0 -and $result.stdout.Trim() -eq "1"
}

function Backup-PostgresDatabase {
    param([string] $BackupDirectory)
    if (-not (Test-DatabaseExists)) {
        Write-RebuildInfo "数据库 $Database 不存在，跳过 PostgreSQL 备份。"
        return $null
    }
    $stamp = Get-Date -Format "yyyyMMddHHmmss"
    $containerPath = "/tmp/qherp-demo-$Database-$stamp.dump"
    $hostPath = Join-Path $BackupDirectory "$Database-$stamp.dump"
    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", $PostgresContainer, "pg_dump", "-U",
        $PostgresUser, "-Fc", "-d", $Database, "-f", $containerPath) | Out-Null
    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("cp", "$($PostgresContainer):$containerPath", $hostPath) | Out-Null
    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", $PostgresContainer, "rm", "-f", $containerPath) | Out-Null
    return $hostPath
}

function Invoke-MinioShell {
    param([string] $Command, [switch] $AllowFailure)
    $wrapped = "MC_HOST_qherplocal=`"http://`${MINIO_ROOT_USER}:`${MINIO_ROOT_PASSWORD}@127.0.0.1:9000`"; export MC_HOST_qherplocal; $Command"
    return Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", $MinioContainer, "sh", "-c", $wrapped) `
        -AllowFailure:$AllowFailure
}

function Test-MinioBucketExists {
    $result = Invoke-MinioShell -Command "mc ls qherplocal/$MinioBucket >/dev/null 2>&1" -AllowFailure
    return $result.exitCode -eq 0
}

function Backup-MinioBucket {
    param([string] $BackupDirectory)
    if (-not (Test-MinioBucketExists)) {
        Write-RebuildInfo "MinIO bucket $MinioBucket 不存在，跳过对象备份。"
        return $null
    }
    $stamp = Get-Date -Format "yyyyMMddHHmmss"
    $containerPath = "/tmp/qherp-demo-$MinioBucket-$stamp"
    $hostPath = Join-Path $BackupDirectory "$MinioBucket-$stamp"
    Invoke-MinioShell -Command "rm -rf '$containerPath' && mkdir -p '$containerPath' && mc mirror --overwrite qherplocal/$MinioBucket '$containerPath'" | Out-Null
    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("cp", "$($MinioContainer):$containerPath", $hostPath) | Out-Null
    Invoke-MinioShell -Command "rm -rf '$containerPath'" | Out-Null
    return $hostPath
}

function Reset-PostgresDatabase {
    $quoted = Quote-PostgresIdentifier -Value $Database
    $owner = Quote-PostgresIdentifier -Value (Assert-PostgresOwner)
    $databaseLiteral = Quote-PostgresLiteral -Value $Database
    $sql = @"
select pg_terminate_backend(pid) from pg_stat_activity where datname = $databaseLiteral and pid <> pg_backend_pid();
drop database if exists $quoted;
create database $quoted owner $owner;
"@
    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", "-i", $PostgresContainer, "psql", "-U",
        $PostgresUser, "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-f", "-") -InputText $sql | Out-Null
}

function Wait-ApiHealth {
    param([string] $Url, [int] $TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri (($Url.TrimEnd("/")) + "/api/health") -TimeoutSec 5
            if ($health.status -eq "UP" -and $health.service -eq "qherp-api") {
                return $health
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Acceptance 后端在 $TimeoutSeconds 秒内未返回健康 UP：$Url。"
}

function Wait-ManagedApiLoginReady {
    param([string] $Url, [int] $TimeoutSeconds)
    if ([string]::IsNullOrWhiteSpace($env:QHERP_INITIAL_ADMIN_PASSWORD)) {
        throw "缺少 QHERP_INITIAL_ADMIN_PASSWORD；无法确认受管 API 初始管理员登录就绪。"
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempts = 0
    $lastError = $null
    do {
        $attempts++
        try {
            New-DemoApiSession -BaseUrl $Url -Username "admin" -Password $env:QHERP_INITIAL_ADMIN_PASSWORD | Out-Null
            return [ordered]@{
                username = "admin"
                attempts = $attempts
                checkedAt = (Get-Date).ToString("o")
            }
        }
        catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "受管 API 初始管理员登录就绪检查超时：$Url；最后错误=$lastError"
}

function Stop-StartedManagedApi {
    param([int] $Port, $LauncherProcess)
    $candidatePids = @()
    $listener = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object { $_.State -eq "Listen" } | Select-Object -First 1
    if ($null -ne $listener -and $listener.OwningProcess -gt 0) {
        $candidatePids += [int]$listener.OwningProcess
    }
    if ($null -ne $LauncherProcess -and $LauncherProcess.Id -gt 0) {
        $candidatePids += [int]$LauncherProcess.Id
    }
    foreach ($processId in ($candidatePids | Select-Object -Unique)) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -ne $process) {
            Stop-Process -Id $processId -Force
        }
    }
    if ($candidatePids.Count -gt 0) {
        Start-Sleep -Seconds 2
    }
}

function Get-Stage034UtcNow {
    return [DateTimeOffset]::UtcNow.ToString("o")
}

function New-Stage034DeliveryMetadata {
    param(
        [ValidateSet("BUILDING", "VALIDATED")][string] $DemoDataStatus,
        [string] $DemoDataVerifiedAt
    )

    return [ordered]@{
        QHERP_DELIVERY_ENVIRONMENT_CODE = "034"
        QHERP_DELIVERY_MANUAL_VERSION = $(if ($env:QHERP_DELIVERY_MANUAL_VERSION) { $env:QHERP_DELIVERY_MANUAL_VERSION } else { "034" })
        QHERP_DELIVERY_MANUAL_UPDATED_AT = $(if ($env:QHERP_DELIVERY_MANUAL_UPDATED_AT) { $env:QHERP_DELIVERY_MANUAL_UPDATED_AT } else { Get-Stage034UtcNow })
        QHERP_DELIVERY_DEMO_DATA_VERSION = $RunId
        QHERP_DELIVERY_DEMO_DATA_STATUS = $DemoDataStatus
        QHERP_DELIVERY_DEMO_DATA_VERIFIED_AT = $(if ($DemoDataStatus -eq "VALIDATED") { $DemoDataVerifiedAt } else { "" })
    }
}

function Start-ManagedAcceptanceApi {
    param(
        [string] $OutputDirectory,
        [ValidateSet("WorkerDisabled", "WorkerEnabled")][string] $DocumentWorkerMode,
        [string] $StageName,
        [System.Collections.IDictionary] $DeliveryMetadata
    )
    if (-not $RunGeneratorAndValidate) {
        return $null
    }
    $uri = [uri]$ApiBaseUrl
    Assert-PortAvailable -Port $uri.Port
    if ($Mode -eq "Acceptance") {
        Assert-RepositoryReady
    }
    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        throw "启动受管 API 必须显式提供 JAVA_HOME。"
    }
    $apiDirectory = Join-Path $RepositoryRoot "apps/api"
    $stdoutPath = Join-Path $OutputDirectory "$StageName-api.out.log"
    $stderrPath = Join-Path $OutputDirectory "$StageName-api.err.log"
    $workerEnabled = $DocumentWorkerMode -eq "WorkerEnabled"
    $datasourceUrl = if ($Stage034Only) { "jdbc:postgresql://localhost:35432/$Database" } else { "jdbc:postgresql://localhost:15432/$Database" }
    if ($Stage034Only -and $null -eq $DeliveryMetadata) {
        throw "Stage034Only 受管 API 启动必须显式注入交付元数据。"
    }
    $process = $null
    $previous = @{}
    foreach ($name in @("JAVA_HOME", "Path", "SERVER_PORT", "SPRING_PROFILES_ACTIVE", "QHERP_DATASOURCE_URL",
            "QHERP_S3_ENDPOINT", "QHERP_S3_BUCKET", "QHERP_S3_ACCESS_KEY", "QHERP_S3_SECRET_KEY",
            "QHERP_TASK_WORKER_ENABLED", "QHERP_DELIVERY_ENVIRONMENT_CODE", "QHERP_DELIVERY_MANUAL_VERSION",
            "QHERP_DELIVERY_MANUAL_UPDATED_AT", "QHERP_DELIVERY_DEMO_DATA_VERSION",
            "QHERP_DELIVERY_DEMO_DATA_STATUS", "QHERP_DELIVERY_DEMO_DATA_VERIFIED_AT")) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }
    try {
        [Environment]::SetEnvironmentVariable("JAVA_HOME", $JavaHome, "Process")
        [Environment]::SetEnvironmentVariable("Path", "$JavaHome\bin;$($previous["Path"])", "Process")
        [Environment]::SetEnvironmentVariable("SERVER_PORT", "$($uri.Port)", "Process")
        [Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "dev", "Process")
        [Environment]::SetEnvironmentVariable("QHERP_DATASOURCE_URL", $datasourceUrl, "Process")
        if ($Stage034Only) {
            [Environment]::SetEnvironmentVariable("QHERP_S3_ENDPOINT", "http://127.0.0.1:39000", "Process")
            foreach ($entry in $DeliveryMetadata.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, "Process")
            }
        }
        [Environment]::SetEnvironmentVariable("QHERP_S3_BUCKET", $MinioBucket, "Process")
        [Environment]::SetEnvironmentVariable("QHERP_TASK_WORKER_ENABLED", $(if ($workerEnabled) { "true" } else { "false" }), "Process")
        $process = Start-Process -FilePath (Join-Path $apiDirectory "mvnw.cmd") -ArgumentList @("spring-boot:run") `
            -WorkingDirectory $apiDirectory -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath `
            -WindowStyle Hidden -PassThru
    }
    finally {
        foreach ($entry in $previous.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
        }
    }
    try {
        $health = Wait-ApiHealth -Url $ApiBaseUrl -TimeoutSeconds $ApiStartTimeoutSeconds
        $loginReady = Wait-ManagedApiLoginReady -Url $ApiBaseUrl -TimeoutSeconds $ApiStartTimeoutSeconds
        $listener = Get-NetTCPConnection -LocalPort $uri.Port -ErrorAction SilentlyContinue |
            Where-Object { $_.State -eq "Listen" } | Select-Object -First 1
        $apiPid = if ($null -ne $listener) { [int]$listener.OwningProcess } else { $process.Id }
    }
    catch {
        Stop-StartedManagedApi -Port $uri.Port -LauncherProcess $process
        throw
    }
    $metadata = [ordered]@{
        pid = $apiPid
        launcherPid = $process.Id
        startedAt = (Get-Date).ToString("o")
        repositoryRoot = $RepositoryRoot
        gitCommit = $ExpectedGitCommit
        database = $Database
        minioBucket = $MinioBucket
        apiBaseUrl = $ApiBaseUrl
        stage = $StageName
        documentWorkerMode = $DocumentWorkerMode
        datasourceUrl = $datasourceUrl
        taskWorkerEnabled = $workerEnabled
        stdout = $stdoutPath
        stderr = $stderrPath
        health = $health
        loginReady = $loginReady
    }
    $metadataPath = Join-Path $OutputDirectory "$StageName-api.json"
    $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding UTF8
    return [pscustomobject]@{
        pid = $apiPid
        launcherPid = $process.Id
        stage = $StageName
        documentWorkerMode = $DocumentWorkerMode
        taskWorkerEnabled = $workerEnabled
        metadataPath = $metadataPath
        stoppedAt = $null
    }
}

function Stop-ManagedApi {
    param($ManagedApi)
    if ($null -eq $ManagedApi -or $null -eq $ManagedApi.pid) {
        return
    }
    $process = Get-Process -Id $ManagedApi.pid -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        Stop-Process -Id $ManagedApi.pid -Force
        Start-Sleep -Seconds 2
    }
    $ManagedApi.stoppedAt = (Get-Date).ToString("o")
}

function Restart-Stage034ValidatedApi {
    param(
        [string] $OutputDirectory,
        $ManagedApi,
        [string] $VerifiedAt
    )
    if (-not $Stage034Only) {
        return $ManagedApi
    }
    Stop-ManagedApi -ManagedApi $ManagedApi
    $validatedMetadata = New-Stage034DeliveryMetadata -DemoDataStatus "VALIDATED" -DemoDataVerifiedAt $VerifiedAt
    return Start-ManagedAcceptanceApi -OutputDirectory $OutputDirectory `
        -DocumentWorkerMode "WorkerEnabled" -StageName "worker-enabled" -DeliveryMetadata $validatedMetadata
}

function Reset-MinioBucket {
    Invoke-MinioShell -Command "mc rb --force qherplocal/$MinioBucket >/dev/null 2>&1 || true; mc mb qherplocal/$MinioBucket >/dev/null" | Out-Null
}

function Invoke-Generator {
    param(
        [string] $ManifestPath,
        [ValidateSet("WorkerDisabled", "WorkerEnabled")][string] $DocumentWorkerMode
    )
    $authorizationPath = $null
    $authorizationToken = $null
    if ($Mode -eq "Acceptance") {
        $authorizationPath = Join-Path (Split-Path -Parent $ManifestPath) "acceptance-generator-authorization.json"
        $authorizationToken = New-AcceptanceAuthorization -Path $authorizationPath -Database $Database `
            -MinioBucket $MinioBucket -ApiBaseUrl $ApiBaseUrl -RunId $RunId -RepositoryRoot $RepositoryRoot `
            -ExpectedGitCommit $ExpectedGitCommit
    }
    $generatorArguments = @("-NoLogo", "-NoProfile", "-File",
        (Join-Path $PSScriptRoot "generate-demo-data.ps1"), "-Mode", $Mode, "-ApiBaseUrl", $ApiBaseUrl,
        "-Database", $Database, "-MinioBucket", $MinioBucket, "-DocumentWorkerMode", $DocumentWorkerMode,
        "-RunId", $RunId, "-OutputManifestPath", $ManifestPath)
    if ($Stage034Only) {
        $generatorArguments += @("-Stage034Only", "-PostgresContainer", $PostgresContainer, "-PostgresUser", $PostgresUser)
    }
    if ($Mode -eq "Acceptance") {
        $generatorArguments += @("-AcceptanceAuthorizationPath", $authorizationPath,
            "-AcceptanceAuthorizationToken", $authorizationToken)
    }
    Invoke-CheckedProcess -FilePath "pwsh" -ArgumentList $generatorArguments | Out-Null
}

function Invoke-Validator {
    param([string] $ValidationPath)
    $validatorArguments = @("-NoLogo", "-NoProfile", "-File",
        (Join-Path $PSScriptRoot "validate-demo-data.ps1"), "-ApiBaseUrl", $ApiBaseUrl,
        "-PostgresContainer", $PostgresContainer, "-Database", $Database, "-MinioContainer", $MinioContainer,
        "-MinioBucket", $MinioBucket, "-OutputJsonPath", $ValidationPath)
    if ($Stage034Only) {
        $validatorArguments += @("-Stage034Profile", "Stage034FullFacts")
    }
    Invoke-CheckedProcess -FilePath "pwsh" -ArgumentList $validatorArguments | Out-Null
    $validation = Get-Content -LiteralPath $ValidationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($validation.failedRules -ne 0 -or $validation.status -ne "PASS") {
        $target = if ($Stage034Only) { "Stage034FullFacts" } else { "当前演示数据规则" }
        throw "验证器未达到 $target 全通过：status=$($validation.status) total=$($validation.totalRules) failed=$($validation.failedRules)。"
    }
}

Assert-RebuildTarget
$output = New-DemoDirectory -Path $OutputDirectory
$backupDirectory = New-DemoDirectory -Path (Join-Path $output "backups")
$summaryPath = Join-Path $output "rebuild-summary.json"
$pgBackup = $null
$bucketBackup = $null
$workerDisabledApi = $null
$workerEnabledApi = $null
$disabledManifestPath = Join-Path $output "generate-demo-data-worker-disabled-manifest.json"
$enabledManifestPath = Join-Path $output "generate-demo-data-manifest.json"
$validationPath = Join-Path $output "validate-demo-data.json"
$validationSucceeded = $false
$errorMessage = $null

try {
    Write-RebuildInfo "开始备份目标资源：Database=$Database Bucket=$MinioBucket Mode=$Mode。"
    $pgBackup = Backup-PostgresDatabase -BackupDirectory $backupDirectory
    $bucketBackup = Backup-MinioBucket -BackupDirectory $backupDirectory

    if ($RecreateTarget) {
        Write-RebuildInfo "重建目标数据库和 bucket。"
        Reset-PostgresDatabase
        Reset-MinioBucket
    }

    if ($RunGeneratorAndValidate) {
        $buildingDeliveryMetadata = $(if ($Stage034Only) { New-Stage034DeliveryMetadata -DemoDataStatus "BUILDING" -DemoDataVerifiedAt "" } else { $null })
        Write-RebuildInfo "启动 worker-disabled 受管 API 并生成取消任务窗口。"
        $workerDisabledApi = Start-ManagedAcceptanceApi -OutputDirectory $output `
            -DocumentWorkerMode "WorkerDisabled" -StageName "worker-disabled" -DeliveryMetadata $buildingDeliveryMetadata
        Invoke-Generator -ManifestPath $disabledManifestPath -DocumentWorkerMode "WorkerDisabled"
        Stop-ManagedApi -ManagedApi $workerDisabledApi

        Write-RebuildInfo "启动 worker-enabled 受管 API，完成导入、导出、打印并执行验证器。"
        $workerEnabledApi = Start-ManagedAcceptanceApi -OutputDirectory $output `
            -DocumentWorkerMode "WorkerEnabled" -StageName "worker-enabled" -DeliveryMetadata $buildingDeliveryMetadata
        Invoke-Generator -ManifestPath $enabledManifestPath -DocumentWorkerMode "WorkerEnabled"
        Invoke-Validator -ValidationPath $validationPath
        $workerEnabledApi = Restart-Stage034ValidatedApi -OutputDirectory $output `
            -ManagedApi $workerEnabledApi -VerifiedAt (Get-Stage034UtcNow)
        $validationSucceeded = $true
    }
}
catch {
    $errorMessage = $_.Exception.Message
    if ($null -ne $workerDisabledApi -and $null -eq $workerDisabledApi.stoppedAt) {
        Stop-ManagedApi -ManagedApi $workerDisabledApi
    }
    if ($null -ne $workerEnabledApi -and $validationSucceeded -ne $true) {
        Stop-ManagedApi -ManagedApi $workerEnabledApi
    }
}
finally {
    $summary = [ordered]@{
        mode = $Mode
        database = $Database
        minioBucket = $MinioBucket
        apiBaseUrl = $ApiBaseUrl
        postgresBackup = $pgBackup
        minioBackup = $bucketBackup
        recreated = [bool]$RecreateTarget
        generatedAndValidated = $validationSucceeded
        disabledManifestPath = $(if (Test-Path -LiteralPath $disabledManifestPath) { $disabledManifestPath } else { $null })
        enabledManifestPath = $(if ($validationSucceeded -and (Test-Path -LiteralPath $enabledManifestPath)) { $enabledManifestPath } else { $null })
        manifestPath = $(if ($validationSucceeded -and (Test-Path -LiteralPath $enabledManifestPath)) { $enabledManifestPath } else { $null })
        validationPath = $(if ($validationSucceeded -and (Test-Path -LiteralPath $validationPath)) { $validationPath } else { $null })
        workerDisabledApi = $workerDisabledApi
        workerEnabledApi = $workerEnabledApi
        error = $errorMessage
        completedAt = (Get-Date).ToString("o")
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
}

if ($errorMessage) {
    Write-RebuildInfo "安全入口失败。摘要=$summaryPath"
    throw $errorMessage
}
Write-RebuildInfo "安全入口完成。摘要=$summaryPath"
