param(
    [string] $DatabaseName = "qherp_034_delivery_governance",
    [string] $MinioBucketName = "qherp-034-delivery-governance",
    [string] $ApiBaseUrl = "http://127.0.0.1:38080",
    [string] $WebBaseUrl = "http://127.0.0.1:35174",
    [string] $ProxyBaseUrl = "http://127.0.0.1:35173",
    [int] $PostgresPort = 35432,
    [int] $MinioApiPort = 39000,
    [int] $MinioConsolePort = 39001
)

$ErrorActionPreference = "Stop"

$script:Stage034DatabaseName = "qherp_034_delivery_governance"
$script:Stage034MinioBucketName = "qherp-034-delivery-governance"
$script:Stage034ApiPort = 38080
$script:Stage034WebPort = 35174
$script:Stage034ProxyPort = 35173
$script:Stage034PostgresPort = 35432
$script:Stage034MinioApiPort = 39000
$script:Stage034MinioConsolePort = 39001

function Test-Stage034LoopbackUri {
    param([Parameter(Mandatory = $true)][uri] $Uri)

    $hostName = $Uri.Host.ToLowerInvariant()
    return $hostName -in @("127.0.0.1", "localhost", "::1", "[::1]")
}

function Test-Stage034IsolationDatabaseName {
    param([Parameter(Mandatory = $true)][string] $Database)

    return $Database -eq $script:Stage034DatabaseName
}

function Test-Stage034IsolationBucketName {
    param([Parameter(Mandatory = $true)][string] $MinioBucket)

    return $MinioBucket -eq $script:Stage034MinioBucketName
}

function Test-Stage034ForbiddenDatabaseName {
    param([Parameter(Mandatory = $true)][string] $Database)

    $stage033ReviewDatabase = "qherp_033_" + "review"
    return $Database -eq "qherp" `
        -or $Database -eq $stage033ReviewDatabase `
        -or $Database.StartsWith("qherp_demo_build_033_full_", [StringComparison]::Ordinal) `
        -or $Database.StartsWith("qherp_demo_build_", [StringComparison]::Ordinal)
}

function Test-Stage034ForbiddenBucketName {
    param([Parameter(Mandatory = $true)][string] $MinioBucket)

    $stage033ReviewBucket = "qherp-033-" + "review"
    return $MinioBucket -eq "qherp-private" `
        -or $MinioBucket -eq $stage033ReviewBucket `
        -or $MinioBucket.StartsWith("qherp-demo-build-033-full-", [StringComparison]::Ordinal) `
        -or $MinioBucket.StartsWith("qherp-demo-build-", [StringComparison]::Ordinal)
}

function Assert-Stage034FormalAndStage033ResourceRejected {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket,
        [Parameter(Mandatory = $true)][uri] $ApiUri,
        [uri] $WebUri,
        [uri] $ProxyUri,
        [int] $PostgresPort = $script:Stage034PostgresPort,
        [int] $MinioApiPort = $script:Stage034MinioApiPort,
        [int] $MinioConsolePort = $script:Stage034MinioConsolePort
    )

    if (Test-Stage034ForbiddenDatabaseName -Database $Database) {
        throw "默认正式资源禁止写入，033 验收资源禁止写入：034 不允许使用数据库 $Database。"
    }
    if (Test-Stage034ForbiddenBucketName -MinioBucket $MinioBucket) {
        throw "默认正式资源禁止写入，033 验收资源禁止写入：034 不允许使用 bucket $MinioBucket。"
    }
    $forbiddenPorts = @(15432, 18080, 5173, 19000, 19001, 25432, 28080, 25174, 25173, 29000, 29001)
    $ports = @($ApiUri.Port, $PostgresPort, $MinioApiPort, $MinioConsolePort)
    if ($null -ne $WebUri) {
        $ports += $WebUri.Port
    }
    if ($null -ne $ProxyUri) {
        $ports += $ProxyUri.Port
    }
    foreach ($port in $ports) {
        if ($port -in $forbiddenPorts) {
            throw "默认正式资源禁止写入，033 验收资源禁止写入：034 不允许使用端口 $port。"
        }
    }
}

function Assert-Stage034IsolationTarget {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket,
        [Parameter(Mandatory = $true)][string] $ApiBaseUrl,
        [string] $WebBaseUrl = "http://127.0.0.1:35174",
        [string] $ProxyBaseUrl = "http://127.0.0.1:35173",
        [int] $PostgresPort = 35432,
        [int] $MinioApiPort = 39000,
        [int] $MinioConsolePort = 39001
    )

    $apiUri = [uri]$ApiBaseUrl
    $webUri = if ([string]::IsNullOrWhiteSpace($WebBaseUrl)) { $null } else { [uri]$WebBaseUrl }
    $proxyUri = if ([string]::IsNullOrWhiteSpace($ProxyBaseUrl)) { $null } else { [uri]$ProxyBaseUrl }

    Assert-Stage034FormalAndStage033ResourceRejected -Database $Database -MinioBucket $MinioBucket `
        -ApiUri $apiUri -WebUri $webUri -ProxyUri $proxyUri -PostgresPort $PostgresPort `
        -MinioApiPort $MinioApiPort -MinioConsolePort $MinioConsolePort

    if (-not (Test-Stage034IsolationDatabaseName -Database $Database)) {
        throw "034 隔离验收数据库必须固定为 qherp_034_delivery_governance。"
    }
    if (-not (Test-Stage034IsolationBucketName -MinioBucket $MinioBucket)) {
        throw "034 隔离验收 bucket 必须固定为 qherp-034-delivery-governance。"
    }
    if ($PostgresPort -ne $script:Stage034PostgresPort -or $MinioApiPort -ne $script:Stage034MinioApiPort `
            -or $MinioConsolePort -ne $script:Stage034MinioConsolePort) {
        throw "034 隔离验收端口必须固定为 PostgreSQL 35432、MinIO 39000/39001。"
    }
    if ($apiUri.Port -ne $script:Stage034ApiPort -or -not (Test-Stage034LoopbackUri -Uri $apiUri)) {
        throw "034 隔离验收 API 必须固定为 loopback 38080。"
    }
    if ($null -ne $webUri -and ($webUri.Port -ne $script:Stage034WebPort -or -not (Test-Stage034LoopbackUri -Uri $webUri))) {
        throw "034 隔离验收 Web 直连必须固定为 loopback 35174。"
    }
    if ($null -ne $proxyUri -and ($proxyUri.Port -ne $script:Stage034ProxyPort -or -not (Test-Stage034LoopbackUri -Uri $proxyUri))) {
        throw "034 隔离验收代理必须固定为 loopback 35173。"
    }
}

function Assert-Stage034ValidationTarget {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket,
        [string] $ApiBaseUrl
    )

    $api = if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) { "http://127.0.0.1:38080" } else { $ApiBaseUrl }
    Assert-Stage034IsolationTarget -Database $Database -MinioBucket $MinioBucket -ApiBaseUrl $api
}

function New-Stage034AcceptanceDataPlan {
    Assert-Stage034IsolationTarget -Database $DatabaseName -MinioBucket $MinioBucketName `
        -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl -ProxyBaseUrl $ProxyBaseUrl `
        -PostgresPort $PostgresPort -MinioApiPort $MinioApiPort -MinioConsolePort $MinioConsolePort

    [pscustomobject]@{
        database = $DatabaseName
        minioBucket = $MinioBucketName
        apiBaseUrl = $ApiBaseUrl
        webBaseUrl = $WebBaseUrl
        proxyBaseUrl = $ProxyBaseUrl
        ports = [ordered]@{
            postgres = $PostgresPort
            minioApi = $MinioApiPort
            minioConsole = $MinioConsolePort
            api = $script:Stage034ApiPort
            web = $script:Stage034WebPort
            proxy = $script:Stage034ProxyPort
        }
        safety = "默认正式资源禁止写入；033 验收资源禁止写入；034 样本只能通过登录、CSRF、真实 API、固定审批、文档任务、worker、领域服务和 MinIO 产生。"
        validatorModes = @(
            "Stage034ZeroFacts：零事实合法，V36 固定定义存在且业务事实为 0 时合法。",
            "Stage034FullFacts：有事实完整，覆盖修复、五类导入、四类批量、十四模板、任务状态和文件一致性。"
        )
        migrationChecks = @(
            "V1/V34/V35 到 V36 定向迁移成功，失败迁移 0。",
            "V29-V35 checksum 固定为 774334682、2130342893、-2074547591、249406902、612501943、-629066235、-82801719。",
            "V36 checksum 精确固定为 1030907058。"
        )
        dataRepairAdapters = @(
            "MATERIAL_PROFILE_CORRECTION_V1",
            "CUSTOMER_PROFILE_CORRECTION_V1",
            "SUPPLIER_PROFILE_CORRECTION_V1"
        )
        historyImportAdapters = @(
            "CUSTOMER_MASTER_V1",
            "SUPPLIER_MASTER_V1",
            "MATERIAL_MASTER_V1",
            "BOM_DRAFT_V1",
            "SALES_PROJECT_DRAFT_V1"
        )
        batchTools = @(
            "CUSTOMER_STATUS_CHANGE_V1",
            "SUPPLIER_STATUS_CHANGE_V1",
            "MATERIAL_STATUS_CHANGE_V1",
            "FIXED_DOCUMENT_BATCH_PRINT_V1"
        )
        printTemplates = @(
            "CONTRACT_ACTIVATION_APPROVAL_V1",
            "BOM_ECO_APPLICATION_APPROVAL_V1",
            "PROCUREMENT_ORDER_V1",
            "SALES_QUOTE_V1",
            "SALES_ORDER_V1",
            "SALES_SHIPMENT_V1",
            "PROCUREMENT_RECEIPT_V1",
            "INVENTORY_TRANSFER_V1",
            "PRODUCTION_WORK_ORDER_V1",
            "PRODUCTION_MATERIAL_ISSUE_V1",
            "PRODUCTION_COMPLETION_RECEIPT_V1",
            "SALES_INVOICE_V1",
            "PURCHASE_INVOICE_V1",
            "ACCOUNTING_VOUCHER_V1"
        )
        sampleCoverage = @(
            "三类修复适配器至少各一条，至少一条 VERIFIED 和一条 REJECTED，职责分离不能单人闭环。",
            "五类历史导入至少各一批成功样本，并保留一批校验失败；导入原子性和幂等冲突必须可验证。",
            "四类批量工具至少各一项结果；状态批量全有或全无，批量打印父子结果清楚。",
            "十四模板全部可查，十个新增模板至少各生成一项成功任务，旧模板版本兼容。",
            "文档任务覆盖 READY_TO_COMMIT、VALIDATION_FAILED、SUCCEEDED、CANCELLED 或 EXPIRED，DB AVAILABLE 与隔离 bucket 一致。"
        )
        pendingDependencies = @(
            "后端 V36 迁移、固定目录、数据修复、历史导入、批量工具、打印模板和任务接口可用后执行真实生成。",
            "前端页面稳定后执行真实桌面验收，不截图，不测移动端。"
        )
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    New-Stage034AcceptanceDataPlan | ConvertTo-Json -Depth 8
}
