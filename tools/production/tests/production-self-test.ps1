[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$script:Failures = [System.Collections.Generic.List[string]]::new()
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path

function Add-Failure {
    param([string] $Message)
    $script:Failures.Add($Message)
}

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) {
        Add-Failure $Message
    }
}

function Assert-Match {
    param([string] $Text, [string] $Pattern, [string] $Message)
    Assert-True -Condition ($Text -match $Pattern) -Message $Message
}

$expectedFiles = @(
    "compose.production.yaml",
    "apps/api/Dockerfile",
    "apps/api/.dockerignore",
    "apps/api/src/main/resources/application-production.properties",
    "apps/web/Dockerfile",
    "apps/web/.dockerignore",
    "apps/web/nginx.production.conf",
    "tools/production/lib/production-common.ps1",
    "tools/production/initialize-secrets.ps1",
    "tools/production/invoke-production.ps1",
    "tools/production/backup-production.ps1",
    "tools/production/backup-stage034-source.ps1",
    "tools/production/restore-production.ps1",
    "tools/production/invoke-migration-rehearsal.ps1",
    "tools/production/verify-production.ps1",
    "tools/production/invoke-performance-check.ps1",
    "tools/production/export-runtime-evidence.ps1",
    "docs/ops/production-deployment.md",
    "docs/ops/production-backup-recovery.md"
)

foreach ($relativePath in $expectedFiles) {
    Assert-True -Condition (Test-Path -LiteralPath (Join-Path $repoRoot $relativePath) -PathType Leaf) `
        -Message "缺少生产文件：$relativePath"
}

$composePath = Join-Path $repoRoot "compose.production.yaml"
if (Test-Path -LiteralPath $composePath -PathType Leaf) {
    $compose = Get-Content -LiteralPath $composePath -Raw
    Assert-Match $compose '(?m)^name:\s*qherp035\s*$' "生产 Compose 项目名必须固定为 qherp035。"
    foreach ($service in @("postgres", "minio", "api", "web")) {
        Assert-Match $compose "(?m)^  $service`:" "生产 Compose 缺少 $service 服务。"
    }
    foreach ($port in @(45173, 48080, 45432, 49000, 49001)) {
        Assert-Match $compose ([regex]::Escape("127.0.0.1:${port}:")) "端口 $port 必须只绑定 127.0.0.1。"
    }
    Assert-True -Condition ($compose -notmatch ':latest(?=[\s"'']|$)') -Message "生产 Compose 不得使用 latest 镜像。"
    Assert-True -Condition ($compose -notmatch 'qherp_dev_password|qherpminio123') `
        -Message "生产 Compose 不得含开发或管理员默认密码。"
    foreach ($variable in @("QHERP_POSTGRES_PASSWORD", "QHERP_MINIO_ROOT_PASSWORD", "QHERP_INITIAL_ADMIN_PASSWORD")) {
        Assert-Match $compose ([regex]::Escape("`${${variable}:?")) "生产 Compose 必须将 $variable 声明为必填变量。"
    }
    Assert-Match $compose 'SPRING_PROFILES_ACTIVE:\s*production' "API 必须使用 production profile。"
    Assert-Match $compose 'postgres:18\.4-alpine3\.24@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15' `
        "PostgreSQL 必须锁定已复扫的 18.4 Alpine 3.24 摘要。"
    Assert-Match $compose 'wget"\s*,\s*"-q"\s*,\s*"-O"\s*,\s*"/dev/null"' `
        "API 健康检查必须兼容 Alpine 运行时并使用 wget。"
    Assert-Match $compose '(?m)^\s+restart:\s*unless-stopped\s*$' "生产服务必须配置 unless-stopped 重启策略。"
    Assert-Match $compose '(?m)^\s+logging:\s*$' "生产服务必须配置日志轮转。"
    Assert-Match $compose '(?m)^\s+healthcheck:\s*$' "生产服务必须配置健康检查。"
}

foreach ($dockerfile in @("apps/api/Dockerfile", "apps/web/Dockerfile")) {
    $dockerfilePath = Join-Path $repoRoot $dockerfile
    if (Test-Path -LiteralPath $dockerfilePath -PathType Leaf) {
        $fromLines = @(Get-Content -LiteralPath $dockerfilePath | Where-Object { $_ -match '^FROM\s+' })
        Assert-True -Condition ($fromLines.Count -ge 2) -Message "$dockerfile 必须使用多阶段构建。"
        foreach ($fromLine in $fromLines) {
            Assert-Match $fromLine '^FROM\s+\S+@sha256:[0-9a-f]{64}(?:\s+AS\s+\S+)?$' `
                "$dockerfile 的所有基础镜像必须使用 SHA256 摘要锁定：$fromLine"
        }
    }
}

$apiDockerfilePath = Join-Path $repoRoot "apps/api/Dockerfile"
if (Test-Path -LiteralPath $apiDockerfilePath -PathType Leaf) {
    $apiDockerfile = Get-Content -LiteralPath $apiDockerfilePath -Raw
    Assert-Match $apiDockerfile 'eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c' `
        "API 运行时必须锁定已复扫的 Temurin 21 Alpine 摘要。"
    Assert-Match $apiDockerfile 'apk upgrade --no-cache' "API 运行时必须安装基础镜像安全更新。"
    Assert-Match $apiDockerfile 'addgroup\s+--system|addgroup\s+-S' "API 运行时必须创建非特权组。"
}

$webDockerfilePath = Join-Path $repoRoot "apps/web/Dockerfile"
if (Test-Path -LiteralPath $webDockerfilePath -PathType Leaf) {
    $webDockerfile = Get-Content -LiteralPath $webDockerfilePath -Raw
    Assert-Match $webDockerfile 'nginx:1\.30\.4-alpine3\.24@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46' `
        "Web 运行时必须锁定已复扫且无高危漏洞的 Nginx 1.30.4 Alpine 摘要。"
}

$pomPath = Join-Path $repoRoot "apps/api/pom.xml"
if (Test-Path -LiteralPath $pomPath -PathType Leaf) {
    $pom = Get-Content -LiteralPath $pomPath -Raw
    Assert-Match $pom '<postgresql\.version>42\.7\.12</postgresql\.version>' `
        "PostgreSQL JDBC 驱动必须固定到修复 CVE-2026-54291 的 42.7.12。"
}

$productionPropertiesPath = Join-Path $repoRoot "apps/api/src/main/resources/application-production.properties"
if (Test-Path -LiteralPath $productionPropertiesPath -PathType Leaf) {
    $properties = Get-Content -LiteralPath $productionPropertiesPath -Raw
    foreach ($variable in @(
        "QHERP_DATASOURCE_URL",
        "QHERP_DATASOURCE_USERNAME",
        "QHERP_DATASOURCE_PASSWORD",
        "QHERP_INITIAL_ADMIN_PASSWORD",
        "QHERP_S3_ENDPOINT",
        "QHERP_S3_ACCESS_KEY",
        "QHERP_S3_SECRET_KEY"
    )) {
        Assert-Match $properties ([regex]::Escape("`${${variable}}")) "生产属性必须无默认值引用 $variable。"
        Assert-True -Condition ($properties -notmatch ([regex]::Escape("`${${variable}:"))) `
            -Message "生产属性中的 $variable 不得提供默认值。"
    }
    Assert-Match $properties 'server\.servlet\.session\.cookie\.http-only=true' "生产会话 Cookie 必须启用 HttpOnly。"
    Assert-Match $properties 'server\.servlet\.session\.cookie\.same-site=lax' "生产会话 Cookie 必须固定 SameSite=Lax。"
    Assert-Match $properties 'springdoc\.api-docs\.enabled=false' "生产环境必须关闭 OpenAPI 文档端点。"
    Assert-Match $properties 'springdoc\.swagger-ui\.enabled=false' "生产环境必须关闭 Swagger UI。"
}

$nginxPath = Join-Path $repoRoot "apps/web/nginx.production.conf"
if (Test-Path -LiteralPath $nginxPath -PathType Leaf) {
    $nginx = Get-Content -LiteralPath $nginxPath -Raw
    Assert-Match $nginx 'limit_req_zone' "Nginx 必须声明登录限流区。"
    Assert-Match $nginx 'limit_req_zone\s+\$binary_remote_addr\s+zone=qherp_login:10m\s+rate=10r/s;' `
        "登录限流必须允许 20 次合法顺序登录性能验证。"
    Assert-Match $nginx 'location\s*=\s*/api/auth/login' "Nginx 必须对登录接口单独配置限流。"
    Assert-Match $nginx 'limit_req\s+zone=qherp_login\s+burst=20\s+nodelay;' `
        "登录限流突发容量必须覆盖合法顺序登录性能验证。"
    Assert-Match $nginx 'Content-Security-Policy' "Nginx 必须设置内容安全策略。"
    Assert-Match $nginx 'X-Content-Type-Options' "Nginx 必须设置 X-Content-Type-Options。"
    Assert-Match $nginx 'Referrer-Policy' "Nginx 必须设置 Referrer-Policy。"
    Assert-Match $nginx 'map\s+\$uri\s+\$qherp_cache_control' `
        "Nginx 必须通过统一缓存变量避免局部 add_header 覆盖安全响应头。"
    Assert-Match $nginx 'add_header\s+Cache-Control\s+"\$qherp_cache_control"\s+always;' `
        "Nginx 必须在服务级统一输出缓存策略。"
    Assert-True -Condition (([regex]::Matches($nginx, '(?m)^\s+add_header\s+')).Count -eq 6) `
        -Message "Nginx 局部 location 不得声明 add_header，以免覆盖安全响应头继承。"
    Assert-Match $nginx 'proxy_pass\s+http://api:8080' "Nginx 必须只通过内部服务名代理 API。"
    Assert-True -Condition ($nginx -notmatch '/etc/nginx/proxy_params') `
        -Message "Nginx 不得引用官方镜像中不存在的 /etc/nginx/proxy_params。"
}

$commonPath = Join-Path $repoRoot "tools/production/lib/production-common.ps1"
if (Test-Path -LiteralPath $commonPath -PathType Leaf) {
    . $commonPath
    foreach ($functionName in @(
        "Get-QherpProductionSecretFields",
        "Assert-QherpProductionSecrets",
        "Import-QherpProductionSecrets",
        "Set-QherpProductionEnvironment",
        "Get-QherpContainerEnvironmentValue",
        "Invoke-QherpPostgresScalar",
        "Get-QherpFileSha256",
        "New-QherpBackupFileRecord",
        "Test-QherpBackupManifest",
        "Assert-QherpRestoreTarget",
        "Get-QherpPercentile",
        "New-QherpHttpSummary",
        "Test-QherpPerformanceThreshold"
    )) {
        Assert-True -Condition ([bool](Get-Command $functionName -ErrorAction SilentlyContinue)) `
            -Message "生产公共库缺少函数：$functionName"
    }

    if (Get-Command Get-QherpProductionSecretFields -ErrorAction SilentlyContinue) {
        $fields = @(Get-QherpProductionSecretFields)
        foreach ($field in @("DatabasePassword", "MinioRootPassword", "InitialAdminPassword")) {
            Assert-True -Condition ($fields -contains $field) -Message "密钥字段清单缺少 $field。"
        }
    }

    if (Get-Command Assert-QherpProductionSecrets -ErrorAction SilentlyContinue) {
        $validSecrets = [pscustomobject]@{
            SchemaVersion = 1
            CreatedAt = (Get-Date).ToString("o")
            ProjectName = "qherp035"
            DatabaseName = "qherp_035_candidate"
            DatabaseUsername = "qherp"
            DatabasePassword = ConvertTo-SecureString "db-secret-035" -AsPlainText -Force
            MinioRootUser = "qherp035"
            MinioRootPassword = ConvertTo-SecureString "minio-secret-035" -AsPlainText -Force
            InitialAdminPassword = ConvertTo-SecureString "admin-secret-035" -AsPlainText -Force
            S3Bucket = "qherp-035-candidate"
        }
        try {
            Assert-QherpProductionSecrets -Secrets $validSecrets
        }
        catch {
            Add-Failure "有效密钥载荷被拒绝：$($_.Exception.Message)"
        }

        $invalidSecrets = $validSecrets.PSObject.Copy()
        $invalidSecrets.DatabasePassword = $null
        $rejected = $false
        try {
            Assert-QherpProductionSecrets -Secrets $invalidSecrets
        }
        catch {
            $rejected = $true
        }
        Assert-True -Condition $rejected -Message "缺少数据库密码的密钥载荷必须被拒绝。"
    }

    $backupFunctionsAvailable = @(
        "Get-QherpFileSha256",
        "New-QherpBackupFileRecord",
        "Test-QherpBackupManifest",
        "Assert-QherpRestoreTarget"
    ) | ForEach-Object { [bool](Get-Command $_ -ErrorAction SilentlyContinue) }
    if ($backupFunctionsAvailable -notcontains $false) {
        $tempBackup = Join-Path ([IO.Path]::GetTempPath()) "qherp-035-self-test-$([guid]::NewGuid().ToString('N'))"
        try {
            New-Item -ItemType Directory -Path $tempBackup -Force | Out-Null
            $payloadPath = Join-Path $tempBackup "database.dump"
            Set-Content -LiteralPath $payloadPath -Value "qherp-backup-contract" -NoNewline
            $fileRecord = New-QherpBackupFileRecord -RootPath $tempBackup -FilePath $payloadPath -Kind "POSTGRES_CUSTOM"
            $manifest = [pscustomobject]@{
                SchemaVersion = 1
                BackupId = "self-test"
                Status = "COMPLETED"
                StartedAtUtc = "2026-07-22T00:00:00Z"
                CompletedAtUtc = "2026-07-22T00:00:01Z"
                DurationSeconds = 1
                Source = [pscustomobject]@{
                    SourceCommit = "ce2ae58ba8870368fac653881ec54e68e778ea85"
                    ComposeProject = "qherp034"
                    PostgresContainer = "source-postgres"
                    DatabaseName = "source_db"
                    PostgresVersion = "16.14"
                    FlywayVersion = "36"
                    MinioContainer = "source-minio"
                    Bucket = "source-bucket"
                }
                Files = @($fileRecord)
                Objects = @()
            }
            $manifestPath = Join-Path $tempBackup "manifest.json"
            $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
            (Get-QherpFileSha256 -Path $manifestPath) | Set-Content `
                -LiteralPath (Join-Path $tempBackup "manifest.sha256") -Encoding ascii
            try {
                [void](Test-QherpBackupManifest -BackupDirectory $tempBackup)
            }
            catch {
                Add-Failure "有效联合备份清单被拒绝：$($_.Exception.Message)"
            }

            Set-Content -LiteralPath $payloadPath -Value "tampered" -NoNewline
            $tamperRejected = $false
            try {
                [void](Test-QherpBackupManifest -BackupDirectory $tempBackup)
            }
            catch {
                $tamperRejected = $true
            }
            Assert-True -Condition $tamperRejected -Message "联合备份载荷被篡改时必须拒绝恢复。"

            Set-Content -LiteralPath $payloadPath -Value "qherp-backup-contract" -NoNewline
            ("0" * 64) | Set-Content -LiteralPath (Join-Path $tempBackup "manifest.sha256") -Encoding ascii
            $manifestTamperRejected = $false
            try {
                [void](Test-QherpBackupManifest -BackupDirectory $tempBackup)
            }
            catch {
                $manifestTamperRejected = $true
            }
            Assert-True -Condition $manifestTamperRejected -Message "联合备份清单 SHA256 被篡改时必须拒绝恢复。"

            $sameTargetRejected = $false
            try {
                Assert-QherpRestoreTarget -Manifest $manifest -TargetPostgresContainer "source-postgres" `
                    -TargetDatabaseName "source_db" -TargetMinioContainer "source-minio" `
                    -TargetBucket "source-bucket" -Confirmed
            }
            catch {
                $sameTargetRejected = $true
            }
            Assert-True -Condition $sameTargetRejected -Message "恢复必须拒绝覆盖来源数据库和 bucket。"

            $missingConfirmationRejected = $false
            try {
                Assert-QherpRestoreTarget -Manifest $manifest -TargetPostgresContainer "target-postgres" `
                    -TargetDatabaseName "target_db" -TargetMinioContainer "target-minio" `
                    -TargetBucket "target-bucket"
            }
            catch {
                $missingConfirmationRejected = $true
            }
            Assert-True -Condition $missingConfirmationRejected -Message "恢复缺少显式确认时必须拒绝执行。"
        }
        finally {
            Remove-Item -LiteralPath $tempBackup -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    $performanceFunctionsAvailable = @(
        "Get-QherpPercentile",
        "New-QherpHttpSummary",
        "Test-QherpPerformanceThreshold"
    ) | ForEach-Object { [bool](Get-Command $_ -ErrorAction SilentlyContinue) }
    if ($performanceFunctionsAvailable -notcontains $false) {
        $samples = @(
            [pscustomobject]@{ StatusCode = 200; DurationMs = 10.0 },
            [pscustomobject]@{ StatusCode = 200; DurationMs = 20.0 },
            [pscustomobject]@{ StatusCode = 200; DurationMs = 30.0 },
            [pscustomobject]@{ StatusCode = 500; DurationMs = 40.0 }
        )
        Assert-True -Condition ((Get-QherpPercentile -Values @(10, 20, 30, 40) -Percentile 50) -eq 20) `
            -Message "性能百分位必须使用可重复的最近秩算法。"
        Assert-True -Condition ((Get-QherpPercentile -Values @(10, 20, 30, 40) -Percentile 95) -eq 40) `
            -Message "P95 最近秩计算不正确。"
        $summary = New-QherpHttpSummary -Name "self-test" -Samples $samples -ExpectedStatusCodes @(200)
        Assert-True -Condition ($summary.SampleCount -eq 4 -and $summary.UnexpectedCount -eq 1 -and
            $summary.ServerErrorCount -eq 1 -and $summary.P95Ms -eq 40) `
            -Message "HTTP 性能汇总的状态分类或百分位不正确。"
        Assert-True -Condition (-not (Test-QherpPerformanceThreshold -Summary $summary `
            -MaxP95Ms 35 -MaxUnexpectedRate 0.1 -RequireZeroServerErrors)) `
            -Message "性能阈值判定必须拒绝超 P95、错误率或 5xx 样本。"
    }
}

$restorePath = Join-Path $repoRoot "tools/production/restore-production.ps1"
if (Test-Path -LiteralPath $restorePath -PathType Leaf) {
    $restore = Get-Content -LiteralPath $restorePath -Raw
    Assert-Match $restore '\[switch\]\s*\$ConfirmRestore' "恢复入口必须要求显式 ConfirmRestore 开关。"
    Assert-Match $restore 'Assert-QherpRestoreTarget' "恢复入口必须调用目标隔离保护。"
    Assert-Match $restore 'docker\s+stop.*api.*web|Stop-QherpApplicationContainers' `
        "恢复入口必须在写入数据库和 bucket 前停止 API/Web 写入口。"
}

$verifyPath = Join-Path $repoRoot "tools/production/verify-production.ps1"
if (Test-Path -LiteralPath $verifyPath -PathType Leaf) {
    $verify = Get-Content -LiteralPath $verifyPath -Raw
    Assert-True -Condition ($verify -notmatch '(?m)^\s*exit\s+1\s*$') `
        -Message "生产验证脚本必须以异常返回失败，确保恢复入口能够执行失败清理。"
}

$performancePath = Join-Path $repoRoot "tools/production/invoke-performance-check.ps1"
if (Test-Path -LiteralPath $performancePath -PathType Leaf) {
    $performance = Get-Content -LiteralPath $performancePath -Raw
    Assert-True -Condition ($performance -notmatch 'Qherp@2026!|qherp_dev_password|qherpminio123') `
        -Message "性能脚本不得包含管理员或开发默认密码明文。"
    foreach ($token in @("1000", "20", "300", "10", "1500", "250", "3000")) {
        Assert-Match $performance ([regex]::Escape($token)) "性能脚本缺少冻结指标：$token"
    }
}

$runtimeEvidencePath = Join-Path $repoRoot "tools/production/export-runtime-evidence.ps1"
if (Test-Path -LiteralPath $runtimeEvidencePath -PathType Leaf) {
    $runtimeEvidence = Get-Content -LiteralPath $runtimeEvidencePath -Raw
    Assert-Match $runtimeEvidence 'Config\.PSObject\.Properties\["Labels"\]' `
        "运行证据必须先安全判断镜像 Config 是否包含 Labels。"
    Assert-Match $runtimeEvidence 'PSObject\.Properties\["org\.opencontainers\.image\.revision"\]' `
        "运行证据必须安全读取仅 API/Web 才具备的可选 OCI revision 标签。"
}

if ($script:Failures.Count -gt 0) {
    Write-Host "生产基线自测失败（$($script:Failures.Count) 项）：" -ForegroundColor Red
    foreach ($failure in $script:Failures) {
        Write-Host "- $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "生产基线自测通过。" -ForegroundColor Green
