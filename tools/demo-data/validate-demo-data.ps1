[CmdletBinding()]
param(
    [string] $ApiBaseUrl = $env:QHERP_API_BASE_URL,
    [string] $PostgresContainer = $(if ($env:QHERP_POSTGRES_CONTAINER) { $env:QHERP_POSTGRES_CONTAINER } else { "qherp-postgres" }),
    [string] $Database = $(if ($env:QHERP_POSTGRES_DB) { $env:QHERP_POSTGRES_DB } else { "qherp" }),
    [string] $DbUser = $(if ($env:QHERP_POSTGRES_USER) { $env:QHERP_POSTGRES_USER } else { "qherp" }),
    [string] $DbPassword = $env:QHERP_POSTGRES_PASSWORD,
    [string] $MinioContainer = $(if ($env:QHERP_MINIO_CONTAINER) { $env:QHERP_MINIO_CONTAINER } else { "qherp-minio" }),
    [string] $MinioBucket = $(if ($env:QHERP_S3_BUCKET) { $env:QHERP_S3_BUCKET } else { "qherp-private" }),
    [string] $SqlPath,
    [string] $OutputJsonPath,
    [ValidateSet("Default", "Stage034ZeroFacts", "Stage034FullFacts")][string] $Stage034Profile = "Default",
    [switch] $SkipApiHealth,
    [switch] $SkipMinio
)

$ErrorActionPreference = "Stop"

$Utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $false
$OutputEncoding = $Utf8NoBomEncoding
try {
    [Console]::InputEncoding = $Utf8NoBomEncoding
    [Console]::OutputEncoding = $Utf8NoBomEncoding
}
catch {
    # 某些宿主不允许修改控制台编码；不影响 JSON 摘要和退出码。
}

$scriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
. (Join-Path $scriptRoot "lib/minio-credential-shell.ps1")

if (-not $SqlPath) {
    $SqlPath = Join-Path $scriptRoot "sql/validate-demo-data.sql"
}

if ($Stage034Profile -ne "Default") {
    . (Join-Path $scriptRoot "lib/stage034-isolation-strategy.ps1")
    Assert-Stage034ValidationTarget -Database $Database -MinioBucket $MinioBucket -ApiBaseUrl $ApiBaseUrl
}

function Write-Info {
    param([string] $Message)
    Write-Host "[demo-data-validator] $Message"
}

function New-Rule {
    param(
        [string] $RuleCode,
        [string] $Category,
        [string] $ActualValue,
        [string] $ExpectedValue,
        [bool] $Passed,
        [string] $Message
    )
    [pscustomobject]@{
        ruleCode = $RuleCode
        category = $Category
        actualValue = $ActualValue
        expectedValue = $ExpectedValue
        passed = $Passed
        message = $Message
    }
}

function Get-ContainerHealth {
    param(
        [string] $ContainerName,
        [scriptblock] $FallbackProbe
    )
    $inspectOutput = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck:{{.State.Status}}{{end}}' $ContainerName 2>&1
    if ($LASTEXITCODE -ne 0) {
        return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue "inspect failed" -ExpectedValue "healthy" -Passed:$false -Message "容器不可检查：$ContainerName"
    }
    $health = (($inspectOutput | ForEach-Object { $_.ToString().Trim() }) -join "")
    if ($health -eq "healthy") {
        return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue $health -ExpectedValue "healthy" -Passed:$true -Message "容器必须处于健康状态：$ContainerName"
    }
    if ($health -eq "unhealthy") {
        return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue $health -ExpectedValue "healthy" -Passed:$false -Message "容器必须处于健康状态：$ContainerName"
    }
    if ($health -eq "no-healthcheck:running" -and $null -ne $FallbackProbe) {
        $probe = & $FallbackProbe
        return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" `
            -ActualValue ("running;{0}" -f $probe.actualValue) -ExpectedValue "healthy or running with successful probe" `
            -Passed:$probe.passed -Message "容器无 Docker healthcheck 时必须通过真实服务探测：$ContainerName"
    }
    return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue $health -ExpectedValue "healthy" -Passed:$false -Message "容器必须处于健康状态：$ContainerName"
}

function Test-PostgresContainerHealthy {
    param(
        [string] $ContainerName,
        [string] $User,
        [string] $DatabaseName,
        [string] $Password
    )
    $args = @("exec")
    if ($Password) {
        $args += @("-e", "PGPASSWORD=$Password")
    }
    $args += @($ContainerName, "pg_isready", "-q", "-U", $User, "-d", $DatabaseName)
    docker @args 2>&1 | Out-Null
    [pscustomobject]@{
        passed = ($LASTEXITCODE -eq 0)
        actualValue = $(if ($LASTEXITCODE -eq 0) { "pg_isready=accepting" } else { "pg_isready=failed" })
    }
}

function Get-DockerPublishedPort {
    param(
        [string] $ContainerName,
        [int] $ContainerPort
    )
    $portOutput = docker port $ContainerName "$ContainerPort/tcp" 2>&1
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    $binding = @($portOutput | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ } | Select-Object -First 1)
    if ($binding.Count -eq 0 -or $binding[0] -notmatch ':(\d+)$') {
        return $null
    }
    return [int]$Matches[1]
}

function Test-MinioContainerHealthy {
    param([string] $ContainerName)
    $publishedPort = Get-DockerPublishedPort -ContainerName $ContainerName -ContainerPort 9000
    if ($null -ne $publishedPort) {
        try {
            Invoke-WebRequest -Uri "http://127.0.0.1:$publishedPort/minio/health/live" -TimeoutSec 10 -UseBasicParsing | Out-Null
            return [pscustomobject]@{
                passed = $true
                actualValue = "minio=/minio/health/live"
            }
        }
        catch {
            return [pscustomobject]@{
                passed = $false
                actualValue = "minio=/minio/health/live failed"
            }
        }
    }
    $minioReadyCommand = "$(Get-DemoMinioCredentialShellPrefix); MC_HOST_qherplocal=`"http://`${MINIO_ROOT_USER}:`${MINIO_ROOT_PASSWORD}@127.0.0.1:9000`" mc ready qherplocal"
    docker exec $ContainerName sh -c $minioReadyCommand 2>&1 | Out-Null
    [pscustomobject]@{
        passed = ($LASTEXITCODE -eq 0)
        actualValue = $(if ($LASTEXITCODE -eq 0) { "minio=mc ready" } else { "minio=failed" })
    }
}

function Get-SqlRuleActualInt {
    param(
        [Parameter(Mandatory = $true)] $Summary,
        [Parameter(Mandatory = $true)][string] $RuleCode
    )
    $rule = @($Summary.rules | Where-Object { $_.ruleCode -eq $RuleCode } | Select-Object -First 1)
    if ($rule.Count -eq 0) {
        return $null
    }
    $parsedValue = 0
    if (-not [int]::TryParse([string]$rule[0].actualValue, [ref]$parsedValue)) {
        return $null
    }
    return $parsedValue
}

function New-MinioObjectConsistencyRule {
    param(
        [Parameter(Mandatory = $true)][int] $MinioExitCode,
        [string] $MinioCountText,
        [Parameter(Mandatory = $true)] $Summary
    )
    $minioCount = 0
    $minioParsed = [int]::TryParse([string]$MinioCountText, [ref]$minioCount)
    $databaseAvailableCount = Get-SqlRuleActualInt -Summary $Summary -RuleCode "FILE_OBJECTS_AVAILABLE_MIN_8"
    $bucketActual = if ($MinioExitCode -eq 0 -and $minioParsed) {
        $minioCount.ToString()
    }
    elseif ($MinioExitCode -eq 0) {
        "unparseable"
    }
    else {
        "check failed"
    }
    $databaseActual = if ($null -ne $databaseAvailableCount) { $databaseAvailableCount.ToString() } else { "unavailable" }
    if ($Stage034Profile -eq "Stage034ZeroFacts") {
        $zeroFactsPassed = $MinioExitCode -eq 0 -and $minioParsed -and $null -ne $databaseAvailableCount `
            -and $minioCount -eq 0 -and $databaseAvailableCount -eq 0
        return New-Rule -RuleCode "MINIO_BUCKET_OBJECTS_ZERO_FACTS" -Category "attachment" `
            -ActualValue ("bucket={0};databaseAvailable={1}" -f $bucketActual, $databaseActual) `
            -ExpectedValue "bucket == database available and = 0" -Passed:$zeroFactsPassed `
            -Message "034 零事实验证要求 MinIO bucket 和数据库 AVAILABLE 文件对象同时为 0。"
    }
    $passed = $MinioExitCode -eq 0 -and $minioParsed -and $null -ne $databaseAvailableCount `
        -and $minioCount -eq $databaseAvailableCount -and $minioCount -ge 8
    return New-Rule -RuleCode "MINIO_BUCKET_OBJECTS_MIN_8" -Category "attachment" `
        -ActualValue ("bucket={0};databaseAvailable={1}" -f $bucketActual, $databaseActual) `
        -ExpectedValue "bucket == database available and >= 8" -Passed:$passed `
        -Message "MinIO 私有 bucket 对象总数必须等于数据库 AVAILABLE 文件对象数且不少于 8。"
}

if (-not (Test-Path -LiteralPath $SqlPath)) {
    throw "SQL 校验文件不存在：$SqlPath"
}

$sql = Get-Content -LiteralPath $SqlPath -Raw -Encoding UTF8
if ($Stage034Profile -ne "Default") {
    $stage034ProfileLiteral = $Stage034Profile.Replace("'", "''")
    $sql = "set qherp.stage034_profile = '$stage034ProfileLiteral';`n" + $sql
}
$dockerArgs = @("exec", "-i")
if ($DbPassword) {
    $dockerArgs += @("-e", "PGPASSWORD=$DbPassword")
}
$dockerArgs += @($PostgresContainer, "psql", "-q", "-U", $DbUser, "-d", $Database, "-v", "ON_ERROR_STOP=1", "-t", "-A", "-f", "-")

Write-Info "执行只读 SQL 校验：容器=$PostgresContainer 数据库=$Database 用户=$DbUser"
$sqlOutput = $sql | docker @dockerArgs 2>&1
$sqlExitCode = $LASTEXITCODE
if ($sqlExitCode -ne 0) {
    Write-Error (($sqlOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine)
    exit 2
}

$jsonText = $sqlOutput |
    ForEach-Object { $_.ToString().Trim() } |
    Where-Object { $_ -like "{*" } |
    Select-Object -Last 1
if (-not $jsonText) {
    Write-Error (($sqlOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine)
    exit 2
}
$summary = $jsonText | ConvertFrom-Json

$environmentRules = New-Object System.Collections.Generic.List[object]
$environmentRules.Add((Get-ContainerHealth -ContainerName $PostgresContainer -FallbackProbe {
            Test-PostgresContainerHealthy -ContainerName $PostgresContainer -User $DbUser -DatabaseName $Database `
                -Password $DbPassword
        }))
if (-not $SkipMinio) {
    $environmentRules.Add((Get-ContainerHealth -ContainerName $MinioContainer -FallbackProbe {
                Test-MinioContainerHealthy -ContainerName $MinioContainer
            }))
    $minioCommand = "$(Get-DemoMinioCredentialShellPrefix); MC_HOST_qherplocal=`"http://`${MINIO_ROOT_USER}:`${MINIO_ROOT_PASSWORD}@127.0.0.1:9000`" mc find qherplocal/$MinioBucket | wc -l"
    $minioOutput = docker exec $MinioContainer sh -c $minioCommand 2>&1
    $minioExitCode = $LASTEXITCODE
    $minioCountText = (($minioOutput | ForEach-Object { $_.ToString().Trim() }) | Where-Object { $_ } | Select-Object -Last 1)
    $environmentRules.Add((New-MinioObjectConsistencyRule -MinioExitCode $minioExitCode -MinioCountText $minioCountText -Summary $summary))
}

if ($ApiBaseUrl -and -not $SkipApiHealth) {
    try {
        $health = Invoke-RestMethod -Uri (($ApiBaseUrl.TrimEnd("/")) + "/api/health") -TimeoutSec 10
        $actualHealth = "status=$($health.status);service=$($health.service)"
        $environmentRules.Add((New-Rule -RuleCode "API_HEALTH_UP" -Category "environment" -ActualValue $actualHealth -ExpectedValue "status=UP;service=qherp-api" -Passed:($health.status -eq "UP" -and $health.service -eq "qherp-api") -Message "后端只读健康检查必须返回 UP。"))
    }
    catch {
        $environmentRules.Add((New-Rule -RuleCode "API_HEALTH_UP" -Category "environment" -ActualValue $_.Exception.Message -ExpectedValue "status=UP;service=qherp-api" -Passed:$false -Message "后端只读健康检查不可用。"))
    }
}

$allRules = @($summary.rules)
$allRules += @($environmentRules.ToArray())
$failedRuleCount = @($allRules | Where-Object { -not $_.passed }).Count
$combinedSummary = [pscustomobject]@{
    validatorVersion = $summary.validatorVersion
    stage034Profile = $Stage034Profile
    checkedAt = (Get-Date).ToString("o")
    status = $(if ($failedRuleCount -eq 0) { "PASS" } else { "FAIL" })
    totalRules = $allRules.Count
    failedRules = $failedRuleCount
    sqlRules = $summary.totalRules
    environmentRules = $environmentRules.Count
    rules = $allRules
}

if ($OutputJsonPath) {
    $combinedSummary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputJsonPath -Encoding UTF8
}

$failedRules = @($combinedSummary.rules | Where-Object { -not $_.passed })
Write-Info ("规则总数={0} 失败={1} 状态={2}" -f $combinedSummary.totalRules, $combinedSummary.failedRules, $combinedSummary.status)

foreach ($rule in $failedRules) {
    Write-Host ("[FAIL] {0} | {1} | 实际={2} | 期望={3}" -f $rule.ruleCode, $rule.message, $rule.actualValue, $rule.expectedValue)
}

$compactSummary = [pscustomobject]@{
    validatorVersion = $combinedSummary.validatorVersion
    checkedAt = $combinedSummary.checkedAt
    status = $combinedSummary.status
    totalRules = $combinedSummary.totalRules
    failedRules = $combinedSummary.failedRules
    sqlRules = $combinedSummary.sqlRules
    environmentRules = $combinedSummary.environmentRules
    failedRuleCodes = @($failedRules | ForEach-Object { $_.ruleCode })
    failedRuleDetails = @($failedRules | Select-Object ruleCode, category, actualValue, expectedValue)
}
Write-Host ("[SUMMARY_JSON] " + ($compactSummary | ConvertTo-Json -Compress -Depth 8))

if ($failedRules.Count -gt 0) {
    exit 1
}

Write-Host ($combinedSummary | ConvertTo-Json -Depth 12)
exit 0
