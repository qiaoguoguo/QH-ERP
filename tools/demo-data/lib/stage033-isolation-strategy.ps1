param(
    [string] $DatabaseName = "qherp_033_review",
    [string] $MinioBucketName = "qherp-033-review"
)

$ErrorActionPreference = "Stop"

function Test-Stage033IsolationDatabaseName {
    param([Parameter(Mandatory = $true)][string] $Database)

    return $Database -eq "qherp_033_review" -or $Database.StartsWith("qherp_demo_build_033_full_", [StringComparison]::Ordinal)
}

function Test-Stage033IsolationBucketName {
    param([Parameter(Mandatory = $true)][string] $MinioBucket)

    return $MinioBucket -eq "qherp-033-review" -or $MinioBucket.StartsWith("qherp-demo-build-033-full-", [StringComparison]::Ordinal)
}

function Assert-Stage033IsolationTarget {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket
    )

    if (-not (Test-Stage033IsolationDatabaseName -Database $Database)) {
        throw "033 隔离验收数据库必须使用 qherp_033_review、qherp_demo_build_033_full_* 或本轮 qherp_demo_build_033_full_close_* 安全资源，正式库禁止写入。"
    }
    if (-not (Test-Stage033IsolationBucketName -MinioBucket $MinioBucket)) {
        throw "033 隔离验收 bucket 必须使用 qherp-033-review、qherp-demo-build-033-full-* 或本轮 qherp-demo-build-033-full-close-* 安全资源，正式对象存储禁止写入。"
    }
}

function Assert-Stage033FormalResourceRejected {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket
    )

    if ($Database -eq "qherp" -or $MinioBucket -eq "qherp-private") {
        throw "正式库禁止写入：033 数据准备不得触碰 qherp/qherp-private。"
    }
}

function New-Stage033AcceptanceDataPlan {
    Assert-Stage033IsolationTarget -Database $DatabaseName -MinioBucket $MinioBucketName
    Assert-Stage033FormalResourceRejected -Database $DatabaseName -MinioBucket $MinioBucketName

    [pscustomobject]@{
        database = $DatabaseName
        minioBucket = $MinioBucketName
        source = "从正式 V34 只读副本恢复隔离库，先自然前迁 V35；正式 V34 副本前迁只做只读核验，不补造 033 数据。"
        safety = "正式库禁止写入；仅允许隔离 qherp_033_review/qherp-033-review、qherp_demo_build_033_full_*/qherp-demo-build-033-full-* 或本轮 qherp_demo_build_033_full_close_*/qherp-demo-build-033-full-close-*，所有正例通过真实 API 或业务服务构造。"
        apiData = @(
            "PROJECT_PROFIT、CONTRACT_COLLECTION、PROCUREMENT_VARIANCE、INVENTORY_CAPITAL、RECEIVABLE_PAYABLE 五个经营侧报表覆盖 LIVE 与 BUSINESS_SNAPSHOT。",
            "operating-accounting-reconciliation 和 financial-summary 请求 BUSINESS_SNAPSHOT 必须返回口径不支持或不可用，前端显示不可用，禁止实时结果冒充快照。",
            "管理/会计两套口径对账：029 经营毛利、031/032 POSTED 且带 PROJECT 辅助、公共金额单列和项目辅助缺失。",
            "跨期间、结转后发生额、旧快照 LEGACY_NOT_INCLUDED、反结账来源变化和来源不完整状态。",
            "项目与合同、采购差异、库存资金、往来账龄、未估值库存和未开票/未回款组合。",
            "分页完整汇总、候选池直达、追溯 returnTo、十进制字符串、负数、空分母和后端舍入。",
            "权限组合覆盖报表、金额、来源、总账、库存估值、银行敏感和税务敏感脱敏。",
            "对象一致性不少于 8 且不写死 18，MinIO 与数据库 AVAILABLE 文件对象数保持一致。"
        )
        migrationChecks = @(
            "V1→V35 空库迁移一次完成，V29-V34 checksum 不变。",
            "V34→V35 正式副本前迁只追加 033 权限、路由、快照代码和索引，不写上游事实。",
            "验证器要求 Flyway 最新成功版本为 V35，V35 checksum 已记录，失败迁移为 0。"
        )
        blockingConditions = @(
            "任一 033 端点不在 /api/admin/reports 或页面不在 /reports。",
            "BUSINESS_SNAPSHOT 被用于经营/会计对照或固定经营财务摘要并返回实时结果。",
            "旧 014 报表 summary + items、筛选、追溯、权限或十进制字符串契约被破坏。",
            "正式 V34 副本被补造经营、会计、银行、税务或对象事实。",
            "权限不足时仍泄露金额、来源、总账、库存、银行或税务敏感信息。"
        )
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    New-Stage033AcceptanceDataPlan | ConvertTo-Json -Depth 5
}
