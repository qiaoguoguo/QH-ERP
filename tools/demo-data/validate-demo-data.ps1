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

if (-not $SqlPath) {
    $scriptRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $SqlPath = Join-Path $scriptRoot "sql/validate-demo-data.sql"
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
    param([string] $ContainerName)
    $inspectOutput = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>&1
    if ($LASTEXITCODE -ne 0) {
        return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue "inspect failed" -ExpectedValue "healthy" -Passed:$false -Message "容器不可检查：$ContainerName"
    }
    $health = (($inspectOutput | ForEach-Object { $_.ToString().Trim() }) -join "")
    return New-Rule -RuleCode "ENV_CONTAINER_$($ContainerName.ToUpperInvariant())" -Category "environment" -ActualValue $health -ExpectedValue "healthy" -Passed:($health -eq "healthy") -Message "容器必须处于健康状态：$ContainerName"
}

if (-not (Test-Path -LiteralPath $SqlPath)) {
    throw "SQL 校验文件不存在：$SqlPath"
}

$sql = Get-Content -LiteralPath $SqlPath -Raw -Encoding UTF8
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
$environmentRules.Add((Get-ContainerHealth -ContainerName $PostgresContainer))
if (-not $SkipMinio) {
    $environmentRules.Add((Get-ContainerHealth -ContainerName $MinioContainer))
    $minioCommand = "MC_HOST_qherplocal=`"http://`${MINIO_ROOT_USER}:`${MINIO_ROOT_PASSWORD}@127.0.0.1:9000`" mc find qherplocal/$MinioBucket | wc -l"
    $minioOutput = docker exec $MinioContainer sh -c $minioCommand 2>&1
    $minioExitCode = $LASTEXITCODE
    $minioCountText = (($minioOutput | ForEach-Object { $_.ToString().Trim() }) | Where-Object { $_ } | Select-Object -Last 1)
    $minioCount = 0
    [void][int]::TryParse($minioCountText, [ref]$minioCount)
    $environmentRules.Add((New-Rule -RuleCode "MINIO_BUCKET_OBJECTS_MIN_8" -Category "attachment" -ActualValue $(if ($minioExitCode -eq 0) { $minioCount.ToString() } else { "check failed" }) -ExpectedValue ">= 8" -Passed:($minioExitCode -eq 0 -and $minioCount -ge 8) -Message "MinIO 私有 bucket 必须可访问且至少包含 8 个演示对象。"))
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
