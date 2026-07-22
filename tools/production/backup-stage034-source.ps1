[CmdletBinding()]
param([string] $OutputDirectory)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

$formalRoot = "F:\zhangqiao\AI-study\qherp"
$jar = Join-Path $formalRoot "apps\api\target\api-034-main-225f7cf8.jar"
$java = "F:\Program Files\JetBrains\IntelliJ IDEA 2025.1.3\jbr\bin\java.exe"
$sourceCommit = "225f7cf89b10fe90f4cf5bf0a4da47d65b283a15"
$postgresContainer = "qherp-034-gate-postgres-20260722044006"
$minioContainer = "qherp-034-gate-minio-20260722044006"
$deliveryMetadata = [ordered]@{
    QHERP_DELIVERY_ENVIRONMENT_CODE = "034"
    QHERP_DELIVERY_MANUAL_VERSION = "034-full-validation"
    QHERP_DELIVERY_MANUAL_UPDATED_AT = "2026-07-22T07:00:22+08:00"
    QHERP_DELIVERY_DEMO_DATA_VERSION = "STAGE034-FULL-FIX-20260722063618"
    QHERP_DELIVERY_DEMO_DATA_STATUS = "VALIDATED"
    QHERP_DELIVERY_DEMO_DATA_VERIFIED_AT = "2026-07-22T08:00:27.0930748+08:00"
}
$stamp = Get-Date -Format "yyyyMMddHHmmss"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $env:USERPROFILE ".codex\backups\qherp\035-v36-fullfacts-$stamp"
}

$connection = Get-NetTCPConnection -State Listen -LocalPort 38080 -ErrorAction Stop
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.OwningProcess)"
if ($process.ExecutablePath -ne $java -or $process.CommandLine -notlike "*$jar*" -or
    $process.CommandLine -notlike "*--server.port=38080*") {
    throw "034 API 进程身份与冻结记录不一致，拒绝停止。"
}

$dbUser = Get-QherpContainerEnvironmentValue -ContainerName $postgresContainer -VariableName "POSTGRES_USER"
$dbPassword = Get-QherpContainerEnvironmentValue -ContainerName $postgresContainer -VariableName "POSTGRES_PASSWORD"
$minioUser = Get-QherpContainerEnvironmentValue -ContainerName $minioContainer -VariableName "MINIO_ROOT_USER"
$minioPassword = Get-QherpContainerEnvironmentValue -ContainerName $minioContainer -VariableName "MINIO_ROOT_PASSWORD"
$productionSecrets = Import-QherpProductionSecrets
$adminPassword = ConvertFrom-QherpSecureString $productionSecrets.InitialAdminPassword
$stopped = $false
$newProcess = $null

try {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
    $stopped = $true
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 500
        $listener = Get-NetTCPConnection -State Listen -LocalPort 38080 -ErrorAction SilentlyContinue
    } while ($listener -and (Get-Date) -lt $deadline)
    if ($listener) {
        throw "034 API 写入口未在 30 秒内停止。"
    }

    & (Join-Path $PSScriptRoot "backup-production.ps1") `
        -SourcePostgresContainer $postgresContainer `
        -SourceMinioContainer $minioContainer `
        -SourceDatabaseName "qherp_034_delivery_governance" `
        -SourceBucket "qherp-034-delivery-governance" `
        -ComposeProject "qherp034" `
        -SourceCommit $sourceCommit `
        -OutputDirectory $OutputDirectory `
        -SourceWriteStopped
    if ($LASTEXITCODE -ne 0) {
        throw "034 联合备份脚本失败。"
    }
}
finally {
    if ($stopped) {
        $env:QHERP_DATASOURCE_URL = "jdbc:postgresql://localhost:35432/qherp_034_delivery_governance"
        $env:QHERP_DATASOURCE_USERNAME = $dbUser
        $env:QHERP_DATASOURCE_PASSWORD = $dbPassword
        $env:QHERP_INITIAL_ADMIN_PASSWORD = $adminPassword
        $env:QHERP_S3_ENDPOINT = "http://localhost:39000"
        $env:QHERP_S3_REGION = "us-east-1"
        $env:QHERP_S3_BUCKET = "qherp-034-delivery-governance"
        $env:QHERP_S3_ACCESS_KEY = $minioUser
        $env:QHERP_S3_SECRET_KEY = $minioPassword
        $env:QHERP_S3_PATH_STYLE = "true"
        $env:QHERP_TASK_WORKER_ENABLED = "true"
        $env:QHERP_APPROVAL_ENFORCE_DIRECT_ACTIONS = "true"
        foreach ($entry in $deliveryMetadata.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, "Process")
        }
        $logDirectory = Join-Path $env:USERPROFILE ".codex\logs\qherp"
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
        $stdout = Join-Path $logDirectory "034-api-$stamp.out.log"
        $stderr = Join-Path $logDirectory "034-api-$stamp.err.log"
        $newProcess = Start-Process -FilePath $java `
            -ArgumentList @("-XX:TieredStopAtLevel=1", "-jar", $jar, "--server.port=38080", "--spring.profiles.active=dev") `
            -WorkingDirectory (Join-Path $formalRoot "apps\api") `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru
        $deadline = (Get-Date).AddSeconds(180)
        $healthy = $false
        do {
            try {
                $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:38080/api/health" -TimeoutSec 3
                $healthy = $health.StatusCode -eq 200
            }
            catch {
                $healthy = $false
            }
            if (-not $healthy) {
                if ($newProcess.HasExited) {
                    Get-Content -LiteralPath $stderr -Tail 100
                    throw "034 API 重启后提前退出。"
                }
                Start-Sleep -Seconds 2
            }
        } while (-not $healthy -and (Get-Date) -lt $deadline)
        if (-not $healthy) {
            throw "034 API 未在 180 秒内恢复健康。"
        }

        $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
        $csrf = Invoke-RestMethod -WebSession $session -Uri "http://127.0.0.1:38080/api/auth/csrf"
        $headers = @{}
        $headers[[string]$csrf.data.headerName] = [string]$csrf.data.token
        $loginBody = @{ username = "admin"; password = $adminPassword } | ConvertTo-Json -Compress
        $login = Invoke-RestMethod -WebSession $session -Uri "http://127.0.0.1:38080/api/auth/login" `
            -Method Post -Headers $headers -ContentType "application/json" -Body $loginBody
        if (-not [bool]$login.success) {
            throw "034 API 重启后管理员登录失败。"
        }
        $deliveryResponse = Invoke-RestMethod -WebSession $session `
            -Uri "http://127.0.0.1:38080/api/admin/platform/delivery-assets"
        $delivery = $deliveryResponse.data
        $metadataMatches = [bool]$deliveryResponse.success -and [string]$delivery.stageCode -eq "034" -and
            [string]$delivery.environmentCode -eq [string]$deliveryMetadata.QHERP_DELIVERY_ENVIRONMENT_CODE -and
            [string]$delivery.manual.version -eq [string]$deliveryMetadata.QHERP_DELIVERY_MANUAL_VERSION -and
            [DateTimeOffset]::Parse([string]$delivery.manual.updatedAt) -eq
                [DateTimeOffset]::Parse([string]$deliveryMetadata.QHERP_DELIVERY_MANUAL_UPDATED_AT) -and
            [string]$delivery.demoData.version -eq [string]$deliveryMetadata.QHERP_DELIVERY_DEMO_DATA_VERSION -and
            [string]$delivery.demoData.status -eq [string]$deliveryMetadata.QHERP_DELIVERY_DEMO_DATA_STATUS -and
            [math]::Abs(([DateTimeOffset]::Parse([string]$delivery.demoData.verifiedAt) -
                [DateTimeOffset]::Parse([string]$deliveryMetadata.QHERP_DELIVERY_DEMO_DATA_VERIFIED_AT)).TotalSeconds) -lt 1
        if (-not $metadataMatches) {
            throw "034 API 重启后交付资料元数据未保持冻结验收状态。"
        }

        $sourceValidationPath = Join-Path $OutputDirectory "source-post-backup-validation.json"
        & (Join-Path $formalRoot "tools\demo-data\validate-demo-data.ps1") `
            -ApiBaseUrl "http://127.0.0.1:38080" `
            -PostgresContainer $postgresContainer -Database "qherp_034_delivery_governance" `
            -DbUser $dbUser -DbPassword $dbPassword `
            -MinioContainer $minioContainer -MinioBucket "qherp-034-delivery-governance" `
            -Stage034Profile "Stage034FullFacts" -OutputJsonPath $sourceValidationPath
        if ($LASTEXITCODE -ne 0) {
            throw "034 API 重启后 Stage034FullFacts 复验失败。"
        }

        $dbPassword = $null
        $minioPassword = $null
        $adminPassword = $null
        $loginBody = $null
    }
}

$proxyHealth = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:35173/api/health" -TimeoutSec 5).StatusCode
$manifest = Get-Content -LiteralPath (Join-Path $OutputDirectory "manifest.json") -Raw | ConvertFrom-Json
[pscustomobject]@{
    BackupDirectory = $OutputDirectory
    BackupStatus = $manifest.Status
    FlywayVersion = $manifest.Source.FlywayVersion
    ObjectCount = $manifest.Summary.ObjectCount
    ApiPid = $newProcess.Id
    ApiHealth = 200
    ProxyHealth = $proxyHealth
} | Format-List
