param(
    [string] $DatabaseName = "qherp_032_review",
    [string] $MinioBucketName = "qherp-032-review"
)

$ErrorActionPreference = "Stop"

function Assert-Stage032IsolationTarget {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket
    )

    if ($Database -ne "qherp_032_review") {
        throw "032 隔离验收数据库必须固定为 qherp_032_review，正式库禁止写入。"
    }
    if ($MinioBucket -ne "qherp-032-review") {
        throw "032 隔离验收 bucket 必须固定为 qherp-032-review，正式对象存储禁止写入。"
    }
}

function Assert-Stage032FormalResourceRejected {
    param(
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket
    )

    if ($Database -eq "qherp" -or $MinioBucket -eq "qherp-private") {
        throw "正式库禁止写入：032 数据准备不得触碰 qherp/qherp-private。"
    }
}

function New-Stage032AcceptanceDataPlan {
    Assert-Stage032IsolationTarget -Database $DatabaseName -MinioBucket $MinioBucketName
    Assert-Stage032FormalResourceRejected -Database $DatabaseName -MinioBucket $MinioBucketName

    [pscustomobject]@{
        database = $DatabaseName
        minioBucket = $MinioBucketName
        source = "从正式 V33 只读备份恢复隔离副本，先自然前迁 V34。"
        safety = "正式库禁止写入；仅允许隔离 qherp_032_review/qherp-032-review。"
        apiData = @(
            "两个月会计期间，覆盖跨期间 OPEN/CLOSED/反结账后 OPEN。",
            "两名审批用户，验证 FINANCIAL_PERIOD_REOPEN 双人审批和申请人不得自审。",
            "反结账申请、审批通过、旧关闭版本保留和再次关闭版本递增。",
            "两类银行账户、CSV 流水、多对多匹配、四类未达和零差额确认。",
            "增值税、附加税费建议、所得税估算、零额税务汇总和税款缴纳台账。",
            "无金额、无来源、无银行敏感权限用户，覆盖后端 DTO 失败关闭。"
        )
        allowedTechnicalSeeds = @(
            "迁移前后 checksum 和约束核验。",
            "公共 API 无法构造但验收必须存在的技术前置，必须在报告中说明。"
        )
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    New-Stage032AcceptanceDataPlan | ConvertTo-Json -Depth 5
}
