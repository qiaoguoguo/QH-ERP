param(
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot "../../..")).Path
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "demo-data-common.ps1")

$cases = New-Object System.Collections.Generic.List[string]

function Assert-True {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-ContainsInOrder {
    param(
        [string] $Text,
        [string[]] $Needles,
        [string] $Message
    )
    $position = -1
    foreach ($needle in $Needles) {
        $next = $Text.IndexOf($needle, $position + 1, [System.StringComparison]::Ordinal)
        if ($next -lt 0) {
            throw "$Message 缺少片段：$needle"
        }
        $position = $next
    }
}

$safe = Test-DemoResourceName -Value "qherp_demo_build_20260715" -Prefix "qherp_demo_build_"
Assert-True -Condition $safe -Message "临时数据库名称应被识别为安全资源。"
$unsafe = Test-DemoResourceName -Value "qherp" -Prefix "qherp_demo_build_"
Assert-True -Condition (-not $unsafe) -Message "正式数据库名称不得通过临时资源校验。"
$privateBucket = Test-DemoResourceName -Value "qherp-private" -Prefix "qherp-demo-build-"
Assert-True -Condition (-not $privateBucket) -Message "正式 MinIO bucket 不得通过临时资源校验。"

$body = ConvertTo-DemoJson -Value ([ordered]@{ materialCode = "RAW-CABLE-001"; quantity = "12.000000" })
$parsedBody = $body | ConvertFrom-Json
Assert-True -Condition ($parsedBody.materialCode -eq "RAW-CABLE-001") -Message "JSON 序列化应保留业务字段。"
Assert-True -Condition ($parsedBody.quantity -eq "12.000000") -Message "十进制字符串不得被转换成浮点数。"

$manifestPath = Join-Path $Root "apps/api/target/demo-data/self-test-manifest.json"
$manifest = New-DemoManifest -RunId "DEMO-ELEC-SELFTEST" -OutputPath $manifestPath -GitCommit "test"
$manifest.AddObject("unit", "PCS", 1)
$manifest.AddFile([ordered]@{ category = "attachment"; objectType = "UNIT"; objectId = 1; downloadableId = 99; downloadPath = "/api/admin/attachments/99/download"; fileName = "unit.txt" })
$manifest.Save()
Assert-True -Condition (Test-Path -LiteralPath $manifestPath) -Message "manifest 应写入 ignored target 目录。"
$manifestJson = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Assert-True -Condition ($manifestJson.runId -eq "DEMO-ELEC-SELFTEST") -Message "manifest 应保留运行 ID。"
Assert-True -Condition ($manifestJson.objects.unit.PCS -eq 1) -Message "manifest 应记录对象自然键与 ID。"
Assert-True -Condition ($manifestJson.files[0].downloadPath -eq "/api/admin/attachments/99/download" -and $manifestJson.files[0].downloadableId -eq 99) `
    -Message "manifest files 必须记录附件、导入源、导出或打印的可下载对象标识，不能只保留含糊字符串。"

$generatorPath = Join-Path $Root "tools/demo-data/generate-demo-data.ps1"
$generator = Get-Content -LiteralPath $generatorPath -Raw
$rebuildPath = Join-Path $Root "tools/demo-data/rebuild-acceptance.ps1"
$rebuild = Get-Content -LiteralPath $rebuildPath -Raw
$commonPath = Join-Path $Root "tools/demo-data/lib/demo-data-common.ps1"
$common = Get-Content -LiteralPath $commonPath -Raw
$validatorPath = Join-Path $Root "tools/demo-data/validate-demo-data.ps1"
$validator = Get-Content -LiteralPath $validatorPath -Raw
$validatorSqlPath = Join-Path $Root "tools/demo-data/sql/validate-demo-data.sql"
$validatorSql = Get-Content -LiteralPath $validatorSqlPath -Raw
$stage032IsolationPath = Join-Path $Root "tools/demo-data/lib/stage032-isolation-strategy.ps1"
$stage032Isolation = if (Test-Path -LiteralPath $stage032IsolationPath) { Get-Content -LiteralPath $stage032IsolationPath -Raw } else { "" }
$apiPomPath = Join-Path $Root "apps/api/pom.xml"
$apiPom = Get-Content -LiteralPath $apiPomPath -Raw
$stage023IntegrationPath = Join-Path $Root "apps/api/src/test/java/com/qherp/api/system/stage023/Stage023InventoryValuationIntegrationTests.java"
$stage023Integration = Get-Content -LiteralPath $stage023IntegrationPath -Raw

$firstByRemarkStart = $generator.IndexOf("function Get-FirstByRemark")
$firstByRemarkEnd = $generator.IndexOf("function Ensure-PurchaseOrder", $firstByRemarkStart)
Assert-True -Condition ($firstByRemarkStart -ge 0 -and $firstByRemarkEnd -gt $firstByRemarkStart) `
    -Message "自测无法定位 Get-FirstByRemark 函数边界。"
$firstByRemarkFunction = $generator.Substring($firstByRemarkStart, $firstByRemarkEnd - $firstByRemarkStart)
Assert-ContainsInOrder -Text $firstByRemarkFunction -Needles @(
    '$queryParameters = @{} + $Query',
    'if (-not $queryParameters.ContainsKey("keyword")) {',
    '$queryParameters["keyword"] = $Remark',
    'Invoke-DemoApiPage -Session $Session -Path $Path -Parameters $queryParameters',
    'Where-Object { $_.remark -eq $Remark }'
) -Message "按 remark 定位既有单据时必须同步传 keyword，避免正式副本重复运行只扫第一页而创建重复业务单据。"
$stage029WindowStart = $generator.IndexOf("function Ensure-Stage029InventoryMutationWindow")
$stage029WindowEnd = $generator.IndexOf("function Ensure-PurchaseOrder", $stage029WindowStart)
Assert-True -Condition ($stage029WindowStart -ge 0 -and $stage029WindowEnd -gt $stage029WindowStart) `
    -Message "Stage029Only 必须提供库存范围锁释放前置函数。"
$stage029WindowFunction = $generator.Substring($stage029WindowStart, $stage029WindowEnd - $stage029WindowStart)
Assert-ContainsInOrder -Text $stage029WindowFunction -Needles @(
    '$draftReason = "验收演示未盘草稿盘点"',
    'Path "/api/admin/inventory/stocktakes"',
    '$draft.status -in @("DRAFT", "COUNTING", "RECONCILED")',
    'Path "/api/admin/inventory/stocktakes/$($draft.id)/cancel"',
    'reason = "029 增量演示释放既有全仓盘点范围锁"',
    'idempotencyKey = "$RunId-STK-DRAFT-CANCEL-STAGE029"'
) -Message "Stage029Only 只能通过真实盘点取消 API 释放正式 V30 副本既有范围锁，不能 SQL 改锁。"

Assert-True -Condition ($apiPom -match '<artifactId>maven-surefire-plugin</artifactId>' `
        -and $apiPom -match '<qherp\.storage\.s3\.endpoint>http://127\.0\.0\.1:9</qherp\.storage\.s3\.endpoint>' `
        -and $apiPom -match '<qherp\.storage\.s3\.bucket>qherp-test-storage-unconfigured</qherp\.storage\.s3\.bucket>' `
        -and $apiPom -notmatch '<qherp\.storage\.s3\.bucket>qherp-private</qherp\.storage\.s3\.bucket>') `
    -Message "Maven 测试必须通过 Surefire 系统属性把未配置对象存储导向不可用测试端点和测试 bucket，禁止默认命中 qherp-private。"
Assert-True -Condition ($stage023Integration -match 'GenericContainer<\?> minio' `
        -and $stage023Integration -match '@DynamicPropertySource' `
        -and $stage023Integration -match 'qherp-test-private-stage023' `
        -and $stage023Integration -match 'qherp\.storage\.s3\.endpoint') `
    -Message "Stage023InventoryValuationIntegrationTests 必须注册专用 MinIO Testcontainer 和独立测试 bucket，不能继承正式默认对象存储配置。"
Assert-True -Condition ($validator -match 'FILE_OBJECTS_AVAILABLE_MIN_8' `
        -and $validator -match 'databaseAvailable' `
        -and $validator -match 'bucket=\{0\};databaseAvailable=\{1\}' `
        -and $validator -match 'bucket == database available and >= 8') `
    -Message "验证器必须把 MINIO_BUCKET_OBJECTS_MIN_8 升级为 bucket 对象数等于数据库 AVAILABLE 文件对象数且不少于 8。"
$expectedV34Checksum = "-177563574"
$expectedV33Checksum = "612501943"
$expectedV32Checksum = "249406902"
$expectedV31Checksum = "-2074547591"
$pendingV34ChecksumMarker = "V34_CHECKSUM_" + "PENDING"
$pendingFlywayV34Rule = "FLYWAY_V34_CHECKSUM_" + "PENDING"
$legacyAdjustedBankBalanceColumn = "adjusted_bank_" + "balance"
$legacyFinancialCloseAuditEventTargetColumn = "target_" + "type"

function Test-FlywayMigrationRulesAreStrict {
    param([string] $SqlText)

    $flywayLatestV34RuleIsStrict = ($SqlText.Contains("FLYWAY_LATEST_V34") `
            -and $SqlText.Contains("latest successful version = 34; checksum = $expectedV34Checksum") `
            -and $SqlText.Contains("= 34") `
            -and $SqlText.Contains("checksum = $expectedV34Checksum") `
            -and (-not $SqlText.Contains("FLYWAY_LATEST_V33")) `
            -and (-not $SqlText.Contains("FLYWAY_LATEST_V32")) `
            -and (-not $SqlText.Contains("FLYWAY_LATEST_V31")) `
            -and (-not $SqlText.Contains("FLYWAY_LATEST_V30")) `
            -and (-not $SqlText.Contains("FLYWAY_LATEST_V29")) `
            -and (-not ($SqlText -match "FLYWAY_LATEST_V(2[0-9]|3[0-3])")) `
            -and (-not $SqlText.Contains("latest successful version = 33; checksum = $expectedV33Checksum")) `
            -and (-not $SqlText.Contains("Flyway 最新成功版本必须为 V32")) `
            -and (-not $SqlText.Contains("Flyway 最新成功版本必须为 V31")) `
            -and (-not $SqlText.Contains("Flyway 最新成功版本必须为 V30")) `
            -and (-not $SqlText.Contains("Flyway 最新成功版本必须为 V29")) `
            -and (-not ($SqlText -match ">=\s*34")) `
            -and (-not ($SqlText -match "max\(version::int\)[^`r`n]*>=\s*34")) `
            -and (-not ($SqlText -match "version::int\s*>=\s*34")) `
            -and (-not $SqlText.Contains($pendingV34ChecksumMarker)))
    $flywayV34ChecksumRuleIsStrict = ($SqlText.Contains("FLYWAY_V34_CHECKSUM") `
            -and (-not $SqlText.Contains($pendingFlywayV34Rule)) `
            -and $SqlText.Contains("version 34 checksum = $expectedV34Checksum") `
            -and $SqlText.Contains("version = '34'") `
            -and $SqlText.Contains("checksum = $expectedV34Checksum") `
            -and $SqlText.Contains("Flyway V34 checksum 必须保持 $expectedV34Checksum。"))
    $flywayV33HistoricalChecksumIsStrict = ($SqlText.Contains("FLYWAY_V33_CHECKSUM") `
            -and $SqlText.Contains("version 33 checksum = $expectedV33Checksum") `
            -and $SqlText.Contains("version = '33'") `
            -and $SqlText.Contains("checksum = $expectedV33Checksum") `
            -and $SqlText.Contains("Flyway V33 checksum 必须保持 $expectedV33Checksum。"))
    $flywayV32HistoricalChecksumIsStrict = ($SqlText.Contains("FLYWAY_V32_CHECKSUM") `
            -and $SqlText.Contains("version 32 checksum = $expectedV32Checksum") `
            -and $SqlText.Contains("version = '32'") `
            -and $SqlText.Contains("checksum = $expectedV32Checksum") `
            -and $SqlText.Contains("Flyway V32 checksum 必须保持 $expectedV32Checksum。"))
    $flywayV31HistoricalChecksumIsStrict = ($SqlText.Contains("FLYWAY_V31_CHECKSUM") `
            -and $SqlText.Contains("version 31 checksum = $expectedV31Checksum") `
            -and $SqlText.Contains("version = '31'") `
            -and $SqlText.Contains("checksum = $expectedV31Checksum") `
            -and $SqlText.Contains("Flyway V31 checksum 必须保持 $expectedV31Checksum。"))
    $flywayV30HistoricalChecksumIsStrict = ($SqlText.Contains("FLYWAY_V30_CHECKSUM") `
            -and $SqlText.Contains("version 30 checksum = 2130342893") `
            -and $SqlText.Contains("version = '30'") `
            -and $SqlText.Contains("checksum = 2130342893") `
            -and $SqlText.Contains("Flyway V30 checksum 必须保持 2130342893。"))
    $flywayV29HistoricalChecksumIsStrict = ($SqlText.Contains("FLYWAY_V29_CHECKSUM") `
            -and $SqlText.Contains("version 29 checksum = 774334682") `
            -and $SqlText.Contains("version = '29'") `
            -and $SqlText.Contains("checksum = 774334682") `
            -and $SqlText.Contains("Flyway V29 checksum 必须保持 774334682。"))
    $flywayNoFailedRuleIsStrict = ($SqlText.Contains("FLYWAY_NO_FAILED") `
            -and $SqlText.Contains("'0', count(*) = 0") `
            -and $SqlText.Contains("Flyway 不能存在失败迁移记录。") `
            -and $SqlText.Contains("from flyway_schema_history where not success"))

    return ($flywayLatestV34RuleIsStrict `
        -and $flywayV34ChecksumRuleIsStrict `
        -and $flywayV33HistoricalChecksumIsStrict `
        -and $flywayV32HistoricalChecksumIsStrict `
        -and $flywayV31HistoricalChecksumIsStrict `
        -and $flywayV30HistoricalChecksumIsStrict `
        -and $flywayV29HistoricalChecksumIsStrict `
        -and $flywayNoFailedRuleIsStrict)
}

Assert-True -Condition (Test-FlywayMigrationRulesAreStrict -SqlText $validatorSql) `
    -Message "正式演示数据验证器必须要求 Flyway 最新成功版本为 V34，独立校验 V34/V33/V32/V31/V30/V29 checksum 和失败迁移 0，且不得保留 V34 pending 标记。"
$weakenedFlywaySql = @"
select 'FLYWAY_LATEST_V34'::text, 'migration'::text,
    'version=34;checksum=$expectedV34Checksum',
    'latest successful version = 34; checksum = $expectedV34Checksum',
    max(version::int) >= 34,
    'latest V34 must stay exact until checksum freeze'
from flyway_schema_history where success and version ~ '^[0-9]+$';
union all select 'FLYWAY_V34_CHECKSUM', 'migration', 'version=34;checksum=$expectedV34Checksum',
    'version 34 checksum = $expectedV34Checksum', checksum = $expectedV34Checksum,
    'Flyway V34 checksum 必须保持 $expectedV34Checksum。' from flyway_schema_history where success and version = '34';
union all select 'FLYWAY_V33_CHECKSUM', 'migration', 'version=33;checksum=$expectedV33Checksum',
    'version 33 checksum = $expectedV33Checksum', checksum = $expectedV33Checksum,
    'Flyway V33 checksum 必须保持 $expectedV33Checksum。' from flyway_schema_history where success and version = '33';
union all select 'FLYWAY_V32_CHECKSUM', 'migration', 'version=32;checksum=$expectedV32Checksum',
    'version 32 checksum = $expectedV32Checksum', checksum = $expectedV32Checksum,
    'Flyway V32 checksum 必须保持 $expectedV32Checksum。' from flyway_schema_history where success and version = '32';
union all select 'FLYWAY_V31_CHECKSUM', 'migration', 'version=31;checksum=$expectedV31Checksum',
    'version 31 checksum = $expectedV31Checksum', checksum = $expectedV31Checksum,
    'Flyway V31 checksum 必须保持 $expectedV31Checksum。' from flyway_schema_history where success and version = '31';
union all select 'FLYWAY_V30_CHECKSUM', 'migration', 'version=30;checksum=2130342893',
    'version 30 checksum = 2130342893', checksum = 2130342893,
    'Flyway V30 checksum 必须保持 2130342893。' from flyway_schema_history where success and version = '30';
union all select 'FLYWAY_V29_CHECKSUM', 'migration', 'version=29;checksum=774334682',
    'version 29 checksum = 774334682', checksum = 774334682,
    'Flyway V29 checksum 必须保持 774334682。' from flyway_schema_history where success and version = '29';
union all select 'FLYWAY_NO_FAILED', 'migration', count(*)::text, '0', count(*) = 0,
    'Flyway 不能存在失败迁移记录。' from flyway_schema_history where not success;
"@
Assert-True -Condition (-not (Test-FlywayMigrationRulesAreStrict -SqlText $weakenedFlywaySql)) `
    -Message "自测必须拒绝把最新迁移规则弱化为 >= 34 的实现。"
$missingV33ChecksumSql = @"
select 'FLYWAY_LATEST_V34'::text, 'migration'::text,
    'version=34;checksum=$expectedV34Checksum',
    'latest successful version = 34; checksum = $expectedV34Checksum',
    version::int = 34 and checksum is not null,
    'latest V34 must stay exact until checksum freeze'
from flyway_schema_history where success and version = '34';
union all select 'FLYWAY_V34_CHECKSUM', 'migration', 'version=34;checksum=$expectedV34Checksum',
    'version 34 checksum = $expectedV34Checksum', checksum = $expectedV34Checksum,
    'Flyway V34 checksum 必须保持 $expectedV34Checksum。' from flyway_schema_history where success and version = '34';
union all select 'FLYWAY_V32_CHECKSUM', 'migration', 'version=32;checksum=$expectedV32Checksum',
    'version 32 checksum = $expectedV32Checksum', checksum = $expectedV32Checksum,
    'Flyway V32 checksum 必须保持 $expectedV32Checksum。' from flyway_schema_history where success and version = '32';
union all select 'FLYWAY_V30_CHECKSUM', 'migration', 'version=30;checksum=2130342893',
    'version 30 checksum = 2130342893', checksum = 2130342893,
    'Flyway V30 checksum 必须保持 2130342893。' from flyway_schema_history where success and version = '30';
union all select 'FLYWAY_V29_CHECKSUM', 'migration', 'version=29;checksum=774334682',
    'version 29 checksum = 774334682', checksum = 774334682,
    'Flyway V29 checksum 必须保持 774334682。' from flyway_schema_history where success and version = '29';
union all select 'FLYWAY_NO_FAILED', 'migration', count(*)::text, '0', count(*) = 0,
    'Flyway 不能存在失败迁移记录。' from flyway_schema_history where not success;
"@
Assert-True -Condition (-not (Test-FlywayMigrationRulesAreStrict -SqlText $missingV33ChecksumSql)) `
    -Message "自测必须拒绝缺失 FLYWAY_V33_CHECKSUM 独立校验的实现。"

function Test-GeneralLedgerPostingRuleSeedGateIsStrict {
    param([string] $SqlText)

    return ($SqlText.Contains("GL_POSTING_RULES_V33") `
        -and $SqlText.Contains("activeLines=") `
        -and $SqlText.Contains("activeAuxMaps=") `
        -and $SqlText.Contains("activePairViolations=") `
        -and $SqlText.Contains("activeRuleViolations=") `
        -and $SqlText.Contains("active_rule_count = 7 and active_line_count = 17 and active_aux_map_count = 9") `
        -and $SqlText.Contains("active_pair_violation_count = 0 and active_rule_violation_count = 0") `
        -and $SqlText.Contains("join gl_posting_rule r on r.id = l.rule_id") `
        -and $SqlText.Contains("join gl_posting_rule_line l on l.id = m.rule_line_id") `
        -and $SqlText.Contains("where r.status = 'ACTIVE'") `
        -and $SqlText.Contains("having count(*) <> 1") `
        -and $SqlText.Contains("source_type not in ('SALES_INVOICE', 'PURCHASE_INVOICE', 'EXPENSE', 'RECEIPT', 'PAYMENT', 'SETTLEMENT_ALLOCATION')") `
        -and $SqlText.Contains("source_variant not in ('DEFAULT', 'RECEIVABLE', 'PAYABLE')") `
        -and $SqlText.Contains("r.rule_version < 1") `
        -and $SqlText.Contains("r.version < 0") `
        -and (-not $SqlText.Contains("(select count(*) from gl_posting_rule_line) as line_count")) `
        -and (-not $SqlText.Contains("(select count(*) from gl_posting_rule_line_aux_map) as aux_map_count")))
}

function Test-GeneralLedgerValidatorRulesAreStrict {
    param([string] $SqlText)

    $schemaRulesAreStrict = ($SqlText.Contains("GL_TABLES_V33") `
            -and $SqlText.Contains("count(*)::text, '19', count(*) = 19") `
            -and $SqlText.Contains("gl_voucher_source_claim") `
            -and $SqlText.Contains("gl_action_idempotency") `
            -and $SqlText.Contains("gl_audit_event"))
    $permissionRulesAreStrict = ($SqlText.Contains("GL_ACTION_PERMISSIONS_V33") `
            -and $SqlText.Contains("count(*)::text, '23', count(*) = 23") `
            -and $SqlText.Contains("gl:voucher:approve-post") `
            -and $SqlText.Contains("gl:amount:view") `
            -and $SqlText.Contains("gl:source:view") `
            -and $SqlText.Contains("GL_SYSTEM_ADMIN_PERMISSIONS_V33"))
    $setupRulesAreStrict = ($SqlText.Contains("GL_LEDGER_SINGLE_MAIN_CNY_V33") `
            -and $SqlText.Contains("GL_ACCOUNT_TEMPLATE_CODES_V33") `
            -and $SqlText.Contains("GL_AUX_DIMENSIONS_V33") `
            -and (Test-GeneralLedgerPostingRuleSeedGateIsStrict -SqlText $SqlText))
    $approvalAndTriggerRulesAreStrict = ($SqlText.Contains("GL_APPROVAL_DEFINITION_V33") `
            -and $SqlText.Contains("GL_VOUCHER_POST") `
            -and $SqlText.Contains("GL_IMMUTABLE_TRIGGERS_V33") `
            -and $SqlText.Contains("tr_gl_voucher_posted_immutable") `
            -and $SqlText.Contains("tr_gl_ledger_entry_immutable"))
    $dynamicRulesAreStrict = ($SqlText.Contains("GL_POSTED_VOUCHER_LEDGER_DYNAMIC") `
            -and $SqlText.Contains("GL_LEDGER_ENTRY_LINE_DYNAMIC") `
            -and $SqlText.Contains("GL_PERIOD_TOTALS_DYNAMIC") `
            -and $SqlText.Contains("GL_SOURCE_CLAIMS_DYNAMIC") `
            -and $SqlText.Contains("GL_VOUCHER_SEQUENCE_DYNAMIC") `
            -and $SqlText.Contains("v.source_claim_id is distinct from c.id") `
            -and $SqlText.Contains("coalesce(s.last_number, 0) <> coalesce(posted.max_number, 0)"))

    return ($schemaRulesAreStrict `
        -and $permissionRulesAreStrict `
        -and $setupRulesAreStrict `
        -and $approvalAndTriggerRulesAreStrict `
        -and $dynamicRulesAreStrict)
}

Assert-True -Condition (Test-GeneralLedgerValidatorRulesAreStrict -SqlText $validatorSql) `
    -Message "正式演示数据验证器必须保留 V33 总账表、权限、初始化、科目/辅助/规则、审批、不可变触发器和动态账簿/来源/号段门禁。"
$weakenedGeneralLedgerSql = @"
select 'GL_TABLES_V33'::text, 'general-ledger'::text, count(*)::text, '19', count(*) >= 19,
    '031 必须创建总账基础表。'
from information_schema.tables where table_schema = 'public';
union all select 'GL_ACTION_PERMISSIONS_V33', 'general-ledger', count(*)::text, '23', count(*) >= 23,
    '031 必须注册总账权限。'
from sys_permission where code like 'gl:%';
union all select 'GL_APPROVAL_DEFINITION_V33', 'general-ledger', count(*)::text, '1', count(*) >= 1,
    '031 必须注册 GL_VOUCHER_POST。'
from platform_approval_definition where scene_code = 'GL_VOUCHER_POST';
"@
Assert-True -Condition (-not (Test-GeneralLedgerValidatorRulesAreStrict -SqlText $weakenedGeneralLedgerSql)) `
    -Message "自测必须拒绝缺少 GL 动态一致性、来源占用、号段和不可变触发器门禁的弱验证器。"
$draftCountingGeneralLedgerSql = @"
select 'GL_LEDGER_SINGLE_MAIN_CNY_V33', 'general-ledger', count(*)::text, '1', count(*) = 1,
    '031 必须初始化单一人民币主账簿。' from gl_ledger where code = 'MAIN' and base_currency = 'CNY';
union all select 'GL_ACCOUNT_TEMPLATE_CODES_V33', 'general-ledger', count(*)::text, '>= 30', count(*) >= 30,
    'V33 必须预置制造业基础科目模板。' from gl_account;
union all select 'GL_AUX_DIMENSIONS_V33', 'general-ledger', count(*)::text, 'system=3;enabled=3',
    count(*)::text, count(*) = 3, 'V33 必须预置客户、供应商、项目三个启用的系统辅助核算维度。' from gl_aux_dimension;
union all select 'GL_POSTING_RULES_V33', 'general-ledger',
    concat('activeRules=', active_rule_count, ';lines=', line_count, ';auxMaps=', aux_map_count),
    'activeRules=7;lines=17;auxMaps=9',
    active_rule_count = 7 and line_count = 17 and aux_map_count = 9,
    'V33 必须预置六类 028 来源转换所需活动制证规则、规则行和辅助映射。'
    from (
        select
            (select count(*) from gl_posting_rule where status = 'ACTIVE') as active_rule_count,
            (select count(*) from gl_posting_rule_line) as line_count,
            (select count(*) from gl_posting_rule_line_aux_map) as aux_map_count
    ) posting_rule_gate;
union all select 'GL_ACTION_PERMISSIONS_V33', 'general-ledger', count(*)::text, '23', count(*) = 23,
    '031 总账动作权限必须精确种子化。' from sys_permission where code like 'gl:%';
union all select 'GL_SYSTEM_ADMIN_PERMISSIONS_V33', 'general-ledger', count(*)::text, '31', count(*) = 31,
    'SYSTEM_ADMIN 必须拥有 V33 会计核算菜单和动作权限。' from sys_role_permission rp;
union all select 'GL_APPROVAL_DEFINITION_V33', 'general-ledger', count(*)::text, '1', count(*) = 1,
    '031 必须注册 GL_VOUCHER_POST。' from platform_approval_definition where scene_code = 'GL_VOUCHER_POST';
union all select 'GL_IMMUTABLE_TRIGGERS_V33', 'general-ledger', count(*)::text, '2', count(*) = 2,
    'POSTED 总账数据必须存在数据库不可变触发器。' from pg_trigger;
union all select 'GL_POSTED_VOUCHER_LEDGER_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
    'POSTED 凭证必须有账簿分录。' from gl_voucher;
union all select 'GL_LEDGER_ENTRY_LINE_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
    '账簿分录必须和凭证明细一致。' from gl_ledger_entry;
union all select 'GL_PERIOD_TOTALS_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
    '期间借贷发生额必须平衡。' from gl_ledger_entry;
union all select 'GL_SOURCE_CLAIMS_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
    '来源占用必须和凭证一致。' from gl_voucher v where v.source_claim_id is distinct from c.id;
union all select 'GL_VOUCHER_SEQUENCE_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
    '凭证号段必须和已记账最大号一致。' from gl_voucher_sequence s where coalesce(s.last_number, 0) <> coalesce(posted.max_number, 0);
"@
Assert-True -Condition (-not (Test-GeneralLedgerPostingRuleSeedGateIsStrict -SqlText $draftCountingGeneralLedgerSql)) `
    -Message "自测必须拒绝把合法 DRAFT/INACTIVE 规则行和辅助映射计入 ACTIVE 种子数量的验证器。"
$versionBlindGeneralLedgerSql = @"
select 'GL_POSTING_RULES_V33', 'general-ledger',
    concat('activeRules=', active_rule_count, ';activeLines=', active_line_count,
        ';activeAuxMaps=', active_aux_map_count, ';activePairViolations=', active_pair_violation_count,
        ';activeRuleViolations=', active_rule_violation_count),
    'activeRules=7;activeLines=17;activeAuxMaps=9;activePairViolations=0;activeRuleViolations=0',
    active_rule_count = 7 and active_line_count = 17 and active_aux_map_count = 9
        and active_pair_violation_count = 0 and active_rule_violation_count = 0,
    'V33 必须精确预置七条活动制证规则及其 17 条活动规则行、9 条活动辅助映射；合法 DRAFT/INACTIVE 历史版本不得污染活动规则种子计数。'
    from (
        select
            (select count(*) from gl_posting_rule where status = 'ACTIVE') as active_rule_count,
            (select count(*)
                from gl_posting_rule_line l
                join gl_posting_rule r on r.id = l.rule_id
                where r.status = 'ACTIVE') as active_line_count,
            (select count(*)
                from gl_posting_rule_line_aux_map m
                join gl_posting_rule_line l on l.id = m.rule_line_id
                join gl_posting_rule r on r.id = l.rule_id
                where r.status = 'ACTIVE') as active_aux_map_count,
            (select count(*)
                from (
                    select source_type, source_variant, count(*) as active_count
                    from gl_posting_rule
                    where status = 'ACTIVE'
                    group by source_type, source_variant
                    having count(*) <> 1
                ) duplicated_active_rules) as active_pair_violation_count,
            (select count(*)
                from gl_posting_rule r
                where r.status = 'ACTIVE'
                and (r.activated_by is null
                    or r.activated_at is null
                    or r.effective_from is null
                    or r.source_type not in ('SALES_INVOICE', 'PURCHASE_INVOICE', 'EXPENSE', 'RECEIPT', 'PAYMENT', 'SETTLEMENT_ALLOCATION')
                    or r.source_variant not in ('DEFAULT', 'RECEIVABLE', 'PAYABLE'))) as active_rule_violation_count
    ) posting_rule_gate;
"@
Assert-True -Condition (-not (Test-GeneralLedgerPostingRuleSeedGateIsStrict -SqlText $versionBlindGeneralLedgerSql)) `
    -Message "自测必须拒绝缺少 rule_version >= 1 和乐观锁 version >= 0 检查的 ACTIVE 制证规则门禁。"

function Test-PeriodCloseValidatorRulesAreStrict {
    param([string] $SqlText)

    $permissionRulesAreStrict = ($SqlText.Contains("PERIOD_CLOSE_PERMISSIONS_V32") `
            -and $SqlText.Contains("system:business-period-close:view") `
            -and $SqlText.Contains("system:business-period-close:check") `
            -and $SqlText.Contains("system:business-period-close:close") `
            -and $SqlText.Contains("system:business-period-close:reopen") `
            -and $SqlText.Contains("system:business-period-close:snapshot-view") `
            -and $SqlText.Contains("PERIOD_CLOSE_NO_AMOUNT_PERMISSION"))
    $currentClosedRuleIsStrict = ($SqlText.Contains("PERIOD_CLOSE_CURRENT_CLOSED_UNIQUE") `
            -and $SqlText.Contains("where status = 'CLOSED'") `
            -and $SqlText.Contains("having count(*) > 1"))
    $lockAuditRuleIsStrict = ($SqlText.Contains("PERIOD_CLOSE_LOCK_AUDIT_COMPLETE") `
            -and $SqlText.Contains("p.status <> 'LOCKED'") `
            -and $SqlText.Contains("action = 'CLOSE'") `
            -and $SqlText.Contains("result = 'SUCCESS'"))
    $snapshotRulesAreStrict = ($SqlText.Contains("PERIOD_CLOSE_REPORT_SNAPSHOT_CODES_8") `
            -and $SqlText.Contains("count(distinct report.report_code) <> 8") `
            -and $SqlText.Contains("OVERVIEW") `
            -and $SqlText.Contains("SETTLEMENT_SUMMARY") `
            -and $SqlText.Contains("PERIOD_CLOSE_SNAPSHOT_FINGERPRINTS_LOCKED") `
            -and $SqlText.Contains("report.fingerprint is null") `
            -and $SqlText.Contains("PERIOD_CLOSE_SNAPSHOT_VERSION_IMMUTABLE") `
            -and $SqlText.Contains("PERIOD_CLOSE_REOPENED_KEEP_SNAPSHOT"))
    $blockingRulesAreStrict = ($SqlText.Contains("PERIOD_CLOSE_BLOCKERS_FAIL_CLOSED") `
            -and $SqlText.Contains("blocking_count <> 0") `
            -and $SqlText.Contains("snapshot_id is null") `
            -and $SqlText.Contains("source_fingerprint is null"))
    $forbiddenExistenceRulesAreAbsent = (-not $SqlText.Contains("PERIOD_CLOSE_CLOSED_RUN_MIN_1") `
            -and -not $SqlText.Contains("PERIOD_CLOSE_MASKED_READER_ROLE_MIN_1"))
    $sourcePermissionIdentifiersArePresent = ($SqlText.Contains("inventory:valuation:view") `
            -and $SqlText.Contains("cost:project-cost:amount-view") `
            -and $SqlText.Contains("report:sales:view") `
            -and $SqlText.Contains("report:procurement:view") `
            -and $SqlText.Contains("report:inventory:view") `
            -and $SqlText.Contains("report:production:view") `
            -and $SqlText.Contains("report:cost:view") `
            -and $SqlText.Contains("report:settlement:view") `
            -and $SqlText.Contains("report:exceptions:view"))

    return ($permissionRulesAreStrict `
        -and $currentClosedRuleIsStrict `
        -and $lockAuditRuleIsStrict `
        -and $snapshotRulesAreStrict `
        -and $blockingRulesAreStrict `
        -and $forbiddenExistenceRulesAreAbsent `
        -and $sourcePermissionIdentifiersArePresent)
}

Assert-True -Condition (Test-PeriodCloseValidatorRulesAreStrict -SqlText $validatorSql) `
    -Message "正式演示数据验证器必须失败关闭当前关闭唯一、期间锁定/审计、阻断不得关闭、快照对账/不变、重开保留旧快照；不得要求 CLOSED/脱敏角色硬存在，且必须保留金额/来源权限分离标识。"

function Test-SelfTestMinioFileObjectConsistency {
    param(
        [int] $BucketCount,
        [int] $DatabaseAvailableCount,
        [int] $MinioExitCode = 0
    )
    return ($MinioExitCode -eq 0 -and $BucketCount -eq $DatabaseAvailableCount -and $BucketCount -ge 8)
}

Assert-True -Condition (Test-SelfTestMinioFileObjectConsistency -BucketCount 17 -DatabaseAvailableCount 17) `
    -Message "MinIO 对象数与数据库 AVAILABLE 文件对象数一致且不少于 8 时应通过。"
Assert-True -Condition (-not (Test-SelfTestMinioFileObjectConsistency -BucketCount 19 -DatabaseAvailableCount 17)) `
    -Message "MinIO 对象数 19 但数据库 AVAILABLE 文件对象数 17 时必须失败。"
Assert-True -Condition (-not (Test-SelfTestMinioFileObjectConsistency -BucketCount 7 -DatabaseAvailableCount 7)) `
    -Message "MinIO 与数据库文件对象数即使一致，少于 8 也必须失败。"

$authorizationTestDirectory = New-DemoDirectory -Path (Join-Path $Root "apps/api/target/demo-data/self-test-authorization")
$authorizationChildPath = Join-Path $authorizationTestDirectory "assert-acceptance-authorization-child.ps1"
@'
param(
    [Parameter(Mandatory = $true)][string] $CommonPath,
    [Parameter(Mandatory = $true)][string] $AuthorizationPath
)
$ErrorActionPreference = "Stop"
. $CommonPath
Assert-DemoAcceptanceAuthorization -Path $AuthorizationPath -Token $env:DEMO_ACCEPTANCE_AUTH_TOKEN `
    -Database "qherp" -MinioBucket "qherp-private" -ApiBaseUrl "http://127.0.0.1:18080" `
    -RunId "DEMO-ELEC-SELFTEST"
if (Test-Path -LiteralPath $AuthorizationPath) {
    throw "授权材料校验后必须删除。"
}
'@ | Set-Content -LiteralPath $authorizationChildPath -Encoding UTF8
$validAuthorizationPath = Join-Path $authorizationTestDirectory "valid-authorization.json"
$validAuthorizationToken = New-AcceptanceAuthorization -Path $validAuthorizationPath -Database "qherp" `
    -MinioBucket "qherp-private" -ApiBaseUrl "http://127.0.0.1:18080" -RunId "DEMO-ELEC-SELFTEST" `
    -RepositoryRoot $Root -ExpectedGitCommit "self-test"
$previousAuthorizationToken = [Environment]::GetEnvironmentVariable("DEMO_ACCEPTANCE_AUTH_TOKEN", "Process")
try {
    [Environment]::SetEnvironmentVariable("DEMO_ACCEPTANCE_AUTH_TOKEN", $validAuthorizationToken, "Process")
    Invoke-DemoProcess -FilePath "pwsh" -ArgumentList @("-NoLogo", "-NoProfile", "-File", $authorizationChildPath,
        "-CommonPath", $commonPath, "-AuthorizationPath", $validAuthorizationPath) | Out-Null
}
finally {
    [Environment]::SetEnvironmentVariable("DEMO_ACCEPTANCE_AUTH_TOKEN", $previousAuthorizationToken, "Process")
}
Assert-True -Condition (-not (Test-Path -LiteralPath $validAuthorizationPath)) `
    -Message "有效 Acceptance 授权跨 pwsh 子进程校验后必须删除授权文件。"

function New-SelfTestAuthorizationFile {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        $ExpiresAtEpochSeconds
    )
    $now = [DateTimeOffset]::UtcNow
    $authorization = [ordered]@{
        database = "qherp"
        minioBucket = "qherp-private"
        apiBaseUrl = "http://127.0.0.1:18080"
        runId = "DEMO-ELEC-SELFTEST"
        repositoryRoot = $Root
        expectedGitCommit = "self-test"
        token = "self-test-token"
        issuedAt = $now.ToString("o")
        expiresAt = $now.AddMinutes(20).ToString("o")
        issuedAtEpochSeconds = $now.ToUnixTimeSeconds()
    }
    if ($null -ne $ExpiresAtEpochSeconds) {
        $authorization.expiresAtEpochSeconds = $ExpiresAtEpochSeconds
    }
    $authorization | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding UTF8
}

foreach ($case in @(
        @{ name = "expired"; value = ([DateTimeOffset]::UtcNow.AddSeconds(-1).ToUnixTimeSeconds()) },
        @{ name = "missing"; value = $null },
        @{ name = "non-numeric"; value = "not-a-number" }
    )) {
    $path = Join-Path $authorizationTestDirectory "$($case.name)-authorization.json"
    New-SelfTestAuthorizationFile -Path $path -ExpiresAtEpochSeconds $case.value
    $rejected = $false
    try {
        Assert-DemoAcceptanceAuthorization -Path $path -Token "self-test-token" -Database "qherp" `
            -MinioBucket "qherp-private" -ApiBaseUrl "http://127.0.0.1:18080" -RunId "DEMO-ELEC-SELFTEST"
    }
    catch {
        $rejected = $true
    }
    Assert-True -Condition $rejected -Message "Acceptance 授权 $($case.name) expiresAtEpochSeconds 必须被拒绝。"
    Assert-True -Condition (-not (Test-Path -LiteralPath $path)) `
        -Message "被拒绝的 Acceptance 授权 $($case.name) 校验后必须删除授权文件。"
}

$sensitiveSentinel = "SELFTEST-SENSITIVE-" + ([guid]::NewGuid().ToString("N"))
$processFailureMessage = $null
try {
    Invoke-DemoProcess -FilePath "pwsh" -ArgumentList @("-NoLogo", "-NoProfile", "-Command", "exit 9",
        "-AcceptanceAuthorizationToken", $sensitiveSentinel, "-AdminPassword", $sensitiveSentinel,
        "-DemoSecret", $sensitiveSentinel, "-VisibleMode", "Temporary") | Out-Null
}
catch {
    $processFailureMessage = $_.Exception.Message
}
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($processFailureMessage)) `
    -Message "敏感参数脱敏测试必须触发一个子进程失败。"
Assert-True -Condition (-not $processFailureMessage.Contains($sensitiveSentinel)) `
    -Message "Invoke-DemoProcess 失败异常不得包含令牌、密码或 secret 参数值。"
Assert-True -Condition ($processFailureMessage -match '-AcceptanceAuthorizationToken <已脱敏>' `
        -and $processFailureMessage -match '-AdminPassword <已脱敏>' `
        -and $processFailureMessage -match '-DemoSecret <已脱敏>') `
    -Message "Invoke-DemoProcess 失败异常应只脱敏敏感参数值，保留敏感参数名便于定位。"
Assert-True -Condition ($processFailureMessage -match '-VisibleMode Temporary') `
    -Message "Invoke-DemoProcess 失败异常不得整体隐藏非敏感参数。"

Assert-True -Condition ($generator -match 'Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode" -FieldValue \$Code -Query @\{ periodCode = \$Code \}') `
    -Message "业务期间幂等查询必须使用真实 periodCode 参数，不能依赖 keyword。"
Assert-True -Condition ($generator -match 'contactPhone = "021-60000000"') `
    -Message "客户/供应商演示数据必须使用真实 DTO 字段 contactPhone。"
Assert-True -Condition ($generator -notmatch '(?m)^\s+phone = "021-60000000"') `
    -Message "客户/供应商演示数据不得发送未消费的 phone 字段。"
$warehouseStart = $generator.IndexOf("function Ensure-Warehouse")
$warehouseEnd = $generator.IndexOf("function Ensure-Category", $warehouseStart)
Assert-True -Condition ($warehouseStart -ge 0 -and $warehouseEnd -gt $warehouseStart) `
    -Message "自测无法定位 Ensure-Warehouse 函数边界。"
$warehouseFunction = $generator.Substring($warehouseStart, $warehouseEnd - $warehouseStart)
Assert-True -Condition ($warehouseFunction -notmatch 'sortOrder\s*=') `
    -Message "仓库 DTO 不消费 sortOrder，演示生成器不得发送或依赖该字段。"
Assert-True -Condition ($generator -notmatch 'Get-Date') `
    -Message "演示业务日期必须由 2026-07-15 固定锚点派生，不得使用运行时 Get-Date。"
$purchaseOrderStart = $generator.IndexOf("function Ensure-PurchaseOrder")
$purchaseOrderEnd = $generator.IndexOf("function Ensure-PurchaseReceipt", $purchaseOrderStart)
Assert-True -Condition ($purchaseOrderStart -ge 0 -and $purchaseOrderEnd -gt $purchaseOrderStart) `
    -Message "自测无法定位 Ensure-PurchaseOrder 函数边界。"
$purchaseOrderFunction = $generator.Substring($purchaseOrderStart, $purchaseOrderEnd - $purchaseOrderStart)
Assert-True -Condition ($purchaseOrderFunction -match 'publicDirectReason\s*=\s*"验收演示公共直采[^"]+"' `
        -and $purchaseOrderFunction -notmatch 'directPurchaseReason\s*=') `
    -Message "公共直采采购订单必须按 024 后端主契约发送 publicDirectReason 中文审计原因，不能遗漏或使用旧别名。"
Assert-ContainsInOrder -Text $purchaseOrderFunction -Needles @(
    'if ($PublicDirect) {',
    'Path "/api/admin/procurement/orders/$($existing.id)/submit-exception" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示公共直采例外审批"',
    'idempotencyKey = "$RunId-PO-$Key-EXCEPTION-SUBMIT"',
    'Invoke-ApprovalTaskAction -Approval $approval -Action "approve"',
    'Comment "同意验收演示公共直采确认"',
    'Key "$RunId-PO-$Key-EXCEPTION-APPROVE"',
    '$existing = Get-ItemById -Path "/api/admin/procurement/orders" -Id $existing.id',
    'if ($existing.status -ne "CONFIRMED")'
) -Message "公共直采采购订单必须先提交例外审批，审批通过后由回调确认并重新 GET 校验。"
$publicBranchStart = $purchaseOrderFunction.IndexOf('if ($PublicDirect) {')
$publicBranchElse = $purchaseOrderFunction.IndexOf('else {', $publicBranchStart)
Assert-True -Condition ($publicBranchStart -ge 0 -and $publicBranchElse -gt $publicBranchStart) `
    -Message "采购订单 helper 必须保留公共直采与非公共订单的清晰条件分支。"
$publicDirectBranch = $purchaseOrderFunction.Substring($publicBranchStart, $publicBranchElse - $publicBranchStart)
Assert-True -Condition ($publicDirectBranch -notmatch '/confirm') `
    -Message "公共直采分支不得直接调用普通 confirm，必须由例外审批回调自动确认。"
Assert-ContainsInOrder -Text $purchaseOrderFunction -Needles @(
    'else {',
    'Path "/api/admin/procurement/orders/$($existing.id)/confirm" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示确认采购订单"',
    'idempotencyKey = "$RunId-PO-$Key-CONFIRM"'
) -Message "非公共采购订单仍应保留普通确认动作请求契约。"
Assert-True -Condition (([regex]::Matches($generator, 'Ensure-PurchaseOrder -Key "\$DemoPrefix-PO-')).Count -eq 4) `
    -Message "四张采购订单样例必须统一走 Ensure-PurchaseOrder，含 029 同 FG 公共安全库存，避免公共直采审批链漏项。"
$purchaseReceiptStart = $generator.IndexOf("function Ensure-PurchaseReceipt")
$purchaseReceiptEnd = $generator.IndexOf("function Process-PendingQualityInspections", $purchaseReceiptStart)
Assert-True -Condition ($purchaseReceiptStart -ge 0 -and $purchaseReceiptEnd -gt $purchaseReceiptStart) `
    -Message "自测无法定位 Ensure-PurchaseReceipt 函数边界。"
$purchaseReceiptFunction = $generator.Substring($purchaseReceiptStart, $purchaseReceiptEnd - $purchaseReceiptStart)
Assert-ContainsInOrder -Text $purchaseReceiptFunction -Needles @(
    'Path "/api/admin/procurement/receipts/$($existing.id)/post" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示采购入库过账"',
    'idempotencyKey = "$RunId-PR-$Key-POST"'
) -Message "采购入库过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-True -Condition ($generator -match '\$script:DemoPurchaseReceiptIds\.Add\(\[long\]\$existing\.id\)') `
    -Message "质检处理必须限定本次演示采购入库来源，避免误处理非演示 PENDING。"
Assert-True -Condition ($generator -match 'BATCH-CU-Q' -and $generator -match 'BATCH-CU-R' -and $generator -match 'BATCH-CU-F' -and $generator -notmatch 'BATCH-CU-01') `
    -Message "铜排质检拆分必须依赖多个来源分配，不能重复使用同一 sourceAllocationId。"
Assert-True -Condition ($generator -match 'Submit-And-ActSalesContractApproval') `
    -Message "合同生效必须通过 022 固定审批提交和审批任务处理，不能直接调用业务 activate。"
Assert-True -Condition ($generator -notmatch '/api/admin/sales-project-contracts/[^`"]+/activate') `
    -Message "演示生成器不得直接调用合同 activate 绕过固定审批。"
Assert-True -Condition ($generator -match '\$approvalRole = Ensure-Role[\s\S]*procurement:order:view[\s\S]*procurement:order:exception-approve') `
    -Message "演示审批角色必须包含采购订单查看和公共直采例外审批权限，否则无法看到并审批 TODO。"
Assert-True -Condition ($generator -match '\$approvalRole = Ensure-Role[\s\S]*finance:expense:view') `
    -Message "029 公共费用分配审批回调需要查看公共费用来源，演示审批角色必须包含 finance:expense:view。"
$mainApprovalIndex = $generator.IndexOf('$mainContract = Submit-And-ActSalesContractApproval -Contract $mainContract -Action "approve" -Key "MAIN"')
$projectActivationIndex = $generator.IndexOf('/api/admin/sales-projects/$($projectA.id)/activate')
$supplementCreateIndex = $generator.IndexOf('$supplementContract = Ensure-SalesContract')
Assert-True -Condition ($mainApprovalIndex -ge 0 -and $projectActivationIndex -gt $mainApprovalIndex -and $supplementCreateIndex -gt $projectActivationIndex) `
    -Message "真实服务顺序必须是主合同审批生效、项目激活、再创建补充合同。"
Assert-True -Condition ($generator -match '/api/admin/users/\$\(\$existing\.id\)/password') `
    -Message "同库重跑时既有演示用户必须通过管理员 API 重置为本轮注入密码，保证审批用户可登录。"
Assert-True -Condition ($generator -notmatch 'CONTRACT-(REJECT|WITHDRAW|CANCEL).*?-ContractType "MAIN"') `
    -Message "驳回/撤回/取消审批样例不得在同一项目下重复创建多个非取消 MAIN 合同。"
Assert-True -Condition ($generator -match 'function Ensure-PeriodAuditSamples') `
    -Message "生成器必须通过真实业务期间解锁/锁定动作补足期间审计样例。"
$periodAuditStart = $generator.IndexOf("function Ensure-PeriodAuditSamples")
$periodAuditEnd = $generator.IndexOf("function Ensure-Bom", $periodAuditStart)
Assert-True -Condition ($periodAuditStart -ge 0 -and $periodAuditEnd -gt $periodAuditStart) `
    -Message "自测无法定位 Ensure-PeriodAuditSamples 函数边界。"
$periodAuditFunction = $generator.Substring($periodAuditStart, $periodAuditEnd - $periodAuditStart)
Assert-True -Condition ($periodAuditFunction -match 'Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode"') `
    -Message "期间审计刷新只能使用业务期间列表 periodCode 契约，不能调用不存在的详情端点。"
Assert-True -Condition ($periodAuditFunction -notmatch 'Get-ItemById -Path "/api/admin/system/business-periods"') `
    -Message "BusinessPeriodAdminController 没有 GET /api/admin/system/business-periods/{id}，生成器不得调用该详情端点。"
Assert-True -Condition ($generator -match 'function Ensure-DeniedAuditSample') `
    -Message "生成器必须通过低权限账号真实访问受保护接口生成权限拒绝审计样例。"
Assert-True -Condition ($generator -match 'function Ensure-AdditionalDemoAttachments') `
    -Message "生成器必须用真实附件 API 补足 8 个文件对象、业务附件和 MinIO 对象。"
Assert-True -Condition ($generator -match 'function Ensure-BomEcoDraft') `
    -Message "生成器必须通过真实 ECO API 补足至少 3 个工程变更样例。"
Assert-True -Condition ($generator -match 'SALES_CONTRACT_EFFECTIVE_MIN_1' -or $generator -notmatch 'SALES_CONTRACT_ACTIVE_MIN_1') `
    -Message "合同验收口径必须保持 EFFECTIVE，不得回退到 ACTIVE 合同规则。"
Assert-True -Condition ($generator -match 'function Ensure-OwnershipConversionPosted') `
    -Message "项目库存和项目成本层必须通过真实所有权转换单据与审批过账生成。"
$ownershipStart = $generator.IndexOf("function Ensure-OwnershipConversionPosted")
$ownershipEnd = $generator.IndexOf("function Ensure-WarehouseTransferPosted", $ownershipStart)
Assert-True -Condition ($ownershipStart -ge 0 -and $ownershipEnd -gt $ownershipStart) `
    -Message "自测无法定位 Ensure-OwnershipConversionPosted 函数边界。"
$ownershipFunction = $generator.Substring($ownershipStart, $ownershipEnd - $ownershipStart)
Assert-True -Condition ($ownershipFunction -match 'Submit-And-ApproveInventoryDocument' -and $generator -match '\$Path/\$\(\$latest\.id\)/submit-approval') `
    -Message "所有权转换必须通过通用审批提交端点 submit-approval，不能直接写项目库存。"
Assert-True -Condition ($generator -match 'function Ensure-WarehouseTransferPosted') `
    -Message "仓库调拨必须通过真实受控调拨单据过账生成。"
Assert-True -Condition ($generator -notmatch 'insert\\s+into\\s+inv_project_cost_layer' -and $generator -notmatch 'insert\\s+into\\s+inv_stock_balance') `
    -Message "演示生成器不得用 SQL 伪造项目成本层或库存余额。"
Assert-True -Condition ($generator -notmatch '\$projectCuLayer\.costLayerId') `
    -Message "成本层候选 DTO 主键字段是 id，sourceCostLayerId 不得读取不存在的 costLayerId。"
Assert-True -Condition ($generator -notmatch 'status = "OPEN"') `
    -Message "成本层接口返回状态为 ACTIVE，生成器不得用 OPEN 查询项目成本层。"
Assert-True -Condition ($generator -match 'PROJECT-CU-A' -and $generator -match 'sourceOwnershipType = "PUBLIC"' -and $generator -match 'targetOwnershipType = "PROJECT"') `
    -Message "所有权转换样例必须包含 PUBLIC_TO_PROJECT。"
Assert-True -Condition ($generator -match 'PROJECT-CABLE-REASSIGN' -and $generator -match 'sourceOwnershipType = "PROJECT"' -and $generator -match 'targetOwnershipType = "PROJECT"') `
    -Message "所有权转换样例必须包含 PROJECT_TO_PROJECT。"
Assert-True -Condition ($generator -match 'PROJECT-CU-RETURN-PUBLIC' -and $generator -match 'targetOwnershipType = "PUBLIC"') `
    -Message "所有权转换样例必须包含 PROJECT_TO_PUBLIC。"
Assert-True -Condition ($generator -match 'function Ensure-SalesOrderConfirmed' -and $generator -match '/api/admin/sales/orders/\$\(\$existing\.id\)/confirm') `
    -Message "销售订单必须通过真实销售 API 创建并确认，形成库存预留。"
Assert-True -Condition ($generator -match 'function Ensure-SalesShipmentPosted' -and $generator -match '/api/admin/sales/shipments/\$\(\$existing\.id\)/post') `
    -Message "销售出库必须通过真实发货单过账生成。"
Assert-True -Condition ($generator -match 'function Ensure-SalesCreditProfile') `
    -Message "销售订单确认前必须通过真实信用档案 API 确保客户信用档案，不能依赖确认失败后补救。"
Assert-ContainsInOrder -Text $generator -Needles @(
    'Write-Step "创建销售订单、预留和销售发货。"',
    'Ensure-SalesCreditProfile -Customer $customers[0] -CreditLimit "200000.00" -Remark "025A演示正常信用：项目订单额度内确认"',
    'Ensure-SalesCreditProfile -Customer $customers[3] -CreditLimit "50000.00" -Remark "025A演示正常信用：普通订单出库应收链路"',
    'Ensure-SalesCreditProfile -Customer $customers[4] -CreditLimit "100.00" -Remark "025A演示信用例外：超额经审批确认并保留有效需求"',
    '$salesOrderSemiA = Ensure-SalesOrderConfirmed',
    '$salesOrderSemiB = Ensure-SalesOrderConfirmed',
    '$salesOrderSemiReserved = Ensure-SalesOrderConfirmed'
) -Message "三张销售订单必须先按产品冻结额度创建或更新信用档案，再创建/确认订单。"
$creditProfileStart = $generator.IndexOf("function Ensure-SalesCreditProfile")
$creditProfileEnd = $generator.IndexOf("function Ensure-SalesOrderConfirmed", $creditProfileStart)
Assert-True -Condition ($creditProfileStart -ge 0 -and $creditProfileEnd -gt $creditProfileStart) `
    -Message "自测无法定位 Ensure-SalesCreditProfile 函数边界。"
$creditProfileFunction = $generator.Substring($creditProfileStart, $creditProfileEnd - $creditProfileStart)
Assert-True -Condition ($creditProfileFunction -match 'Invoke-DemoApiPage -Session \$Session -Path "/api/admin/sales/credit-profiles" -Parameters @\{' `
        -and $creditProfileFunction -match 'customerId = \$Customer\.id' `
        -and $creditProfileFunction -match '/api/admin/sales/credit-profiles' `
        -and $creditProfileFunction -match 'customerId = \$Customer\.id' `
        -and $creditProfileFunction -match 'creditLimit = \$CreditLimit' `
        -and $creditProfileFunction -match 'frozen = \$false' `
        -and $creditProfileFunction -match 'blockOverdue = \$false' `
        -and $creditProfileFunction -match 'reviewDate = "2026-07-10"' `
        -and $creditProfileFunction -match 'remark = \$Remark' `
        -and $creditProfileFunction -match 'version = \$current\.version') `
    -Message "信用档案 helper 必须消费 GET/POST/PUT 真实契约，更新时携带当前 version，且不得新增生产 DTO 不存在的字段。"
Assert-True -Condition ($generator -match '\$approvalRole = Ensure-Role[\s\S]*sales:order:view[\s\S]*sales:credit:view[\s\S]*sales:credit:override-approve') `
    -Message "演示审批角色必须包含销售订单查看、信用查看和信用覆盖审批权限，否则无法看到并审批 SALES_ORDER_CREDIT_OVERRIDE TODO。"
$salesOrderStart = $generator.IndexOf("function Ensure-SalesOrderConfirmed")
$salesOrderEnd = $generator.IndexOf("function Ensure-SalesShipmentPosted", $salesOrderStart)
Assert-True -Condition ($salesOrderStart -ge 0 -and $salesOrderEnd -gt $salesOrderStart) `
    -Message "自测无法定位 Ensure-SalesOrderConfirmed 函数边界。"
$salesOrderFunction = $generator.Substring($salesOrderStart, $salesOrderEnd - $salesOrderStart)
Assert-ContainsInOrder -Text $salesOrderFunction -Needles @(
    'Path "/api/admin/sales/orders/$($existing.id)/confirm" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示确认销售订单"',
    'idempotencyKey = "$RunId-SALES-ORDER-$Key-CONFIRM"'
) -Message "销售订单确认必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-ContainsInOrder -Text $salesOrderFunction -Needles @(
    'if ($CreditOverride) {',
    'Path "/api/admin/sales/orders/$($existing.id)/submit-credit-override" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示销售订单信用例外审批"',
    'idempotencyKey = "$RunId-SALES-ORDER-$Key-CREDIT-OVERRIDE-SUBMIT"',
    'Invoke-ApprovalTaskAction -Approval $approval -Action "approve"',
    'Comment "同意验收演示销售订单信用例外"',
    'Key "$RunId-SALES-ORDER-$Key-CREDIT-OVERRIDE-APPROVE"',
    '$existing = Get-ItemById -Path "/api/admin/sales/orders" -Id $existing.id',
    'if ($existing.status -ne "CONFIRMED")'
) -Message "超额销售订单必须走 SALES_ORDER_CREDIT_OVERRIDE 审批并由回调自动确认，不能直接普通 confirm。"
Assert-True -Condition ($salesOrderFunction -match 'else\s*\{\s*\$existing = Invoke-DemoApi -Session \$Session -Method Put -Path "/api/admin/sales/orders/\$\(\$existing\.id\)/confirm"') `
    -Message "额度内销售订单仍应保留普通确认动作请求契约。"
Assert-True -Condition ($generator -match 'Ensure-SalesOrderConfirmed -Key "SEMI-B-RESERVED"[\s\S]*-CreditOverride') `
    -Message "保留未发货订单必须作为唯一信用覆盖审批样例，不能抬高额度或删除样例。"
$salesShipmentStart = $generator.IndexOf("function Ensure-SalesShipmentPosted")
$salesShipmentEnd = $generator.IndexOf("function Ensure-WorkOrderReleased", $salesShipmentStart)
Assert-True -Condition ($salesShipmentStart -ge 0 -and $salesShipmentEnd -gt $salesShipmentStart) `
    -Message "自测无法定位 Ensure-SalesShipmentPosted 函数边界。"
$salesShipmentFunction = $generator.Substring($salesShipmentStart, $salesShipmentEnd - $salesShipmentStart)
Assert-ContainsInOrder -Text $salesShipmentFunction -Needles @(
    'Path "/api/admin/sales/orders/$($Order.id)/delivery-plans" -Parameters @{',
    'orderLineId -eq $line.orderLineId',
    '$line["deliveryPlanId"] = $plan.id',
    'Path "/api/admin/sales/orders/$($Order.id)/shipments"'
) -Message "销售出库创建前必须读取订单交付计划，按 orderLineId 匹配开放计划并把真实 deliveryPlanId 写入出库行。"
Assert-ContainsInOrder -Text $salesShipmentFunction -Needles @(
    'Path "/api/admin/sales/shipments/$($existing.id)/post" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示销售出库过账"',
    'idempotencyKey = "$RunId-SALES-SHIPMENT-$Key-POST"'
) -Message "销售出库过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-True -Condition ($generator -match 'Ensure-SalesOrderConfirmed -Key "SEMI-B-' -and $generator -notmatch 'Ensure-SalesOrderConfirmed -Key "(SCREW|RAIL|CABLE-RESERVED)"') `
    -Message "销售服务只允许成品/半成品，演示销售链不得用原料或辅料绕过真实可售物料约束。"
Assert-True -Condition ($generator -match 'Ensure-SalesOrderConfirmed -Key "SEMI-B-A" -Customer \$customers\[0\] -Project \$projectA -Contract \$mainContract') `
    -Message "带项目/合同的销售订单客户必须与项目客户一致。"
$workOrderReleasedStart = $generator.IndexOf("function Ensure-WorkOrderReleased")
$workOrderReleasedEnd = $generator.IndexOf("function Ensure-WorkOrderDraft", $workOrderReleasedStart)
Assert-True -Condition ($workOrderReleasedStart -ge 0 -and $workOrderReleasedEnd -gt $workOrderReleasedStart) `
    -Message "自测无法定位 Ensure-WorkOrderReleased 函数边界。"
$workOrderReleasedFunction = $generator.Substring($workOrderReleasedStart, $workOrderReleasedEnd - $workOrderReleasedStart)
Assert-ContainsInOrder -Text $workOrderReleasedFunction -Needles @(
    '$body = [ordered]@{',
    'remark = $remark',
    'idempotencyKey = "$RunId-WO-$Key-CREATE"',
    'if ($null -ne $Project) {',
    '$body.ownershipType = "PROJECT"',
    '$body.projectId = $Project.id',
    'Path "/api/admin/production/work-orders" -Body $body'
) -Message "生产工单创建必须携带基于 RunId 和业务 Key 的稳定幂等键，并在项目工单时通过真实 API 携带项目权属。"
Assert-ContainsInOrder -Text $workOrderReleasedFunction -Needles @(
    'Path "/api/admin/production/work-orders/$($existing.id)/release" -Body ([ordered]@{',
    'version = $existing.version',
    'reason = "验收演示生产工单释放"',
    'idempotencyKey = "$RunId-WO-$Key-RELEASE"'
) -Message "生产工单释放必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
$workOrderDraftStart = $generator.IndexOf("function Ensure-WorkOrderDraft")
$workOrderDraftEnd = $generator.IndexOf("function Ensure-WorkOrderCancelled", $workOrderDraftStart)
Assert-True -Condition ($workOrderDraftStart -ge 0 -and $workOrderDraftEnd -gt $workOrderDraftStart) `
    -Message "自测无法定位 Ensure-WorkOrderDraft 函数边界。"
$workOrderDraftFunction = $generator.Substring($workOrderDraftStart, $workOrderDraftEnd - $workOrderDraftStart)
Assert-ContainsInOrder -Text $workOrderDraftFunction -Needles @(
    'Path "/api/admin/production/work-orders" -Body ([ordered]@{',
    'remark = $remark',
    'idempotencyKey = "$RunId-WO-$Key-CREATE"'
) -Message "草稿生产工单创建也必须携带稳定幂等键，避免相同 helper 重放时冲突或缺键失败。"
Assert-True -Condition ($generator -match 'function Ensure-WorkOrderReleased' -and $generator -match '/api/admin/production/work-orders/\$\(\$existing\.id\)/release') `
    -Message "生产工单必须通过真实释放动作形成 BOM 用料快照和预留。"
Assert-True -Condition ($generator -match 'function Ensure-ProductionExecutionPosted' -and $generator -match '/material-issues/\$\(\$issue\.id\)/post' -and $generator -match '/reports/\$\(\$report\.id\)/post' -and $generator -match '/completion-receipts/\$\(\$receipt\.id\)/post') `
    -Message "生产领料、报工和完工必须通过真实子单过账，不能 SQL 造成本记录。"
Assert-True -Condition ($generator -match 'Process-PendingQualityInspections -SourceType "PRODUCTION_COMPLETION"') `
    -Message "半成品完工入库后必须通过真实质检接口转为合格库存，再进入销售发货链。"
$productionStepIndex = $generator.IndexOf('Write-Step "创建生产工单、领料、报工和完工入库。"')
$salesStepIndex = $generator.IndexOf('Write-Step "创建销售订单、预留和销售发货。"')
Assert-True -Condition ($productionStepIndex -ge 0 -and $salesStepIndex -gt $productionStepIndex) `
    -Message "可销售半成品库存必须先由生产完工和质检生成，销售链不能早于生产链执行。"
Assert-True -Condition ($generator -match 'function Ensure-FinanceSettlementPosted' -and $generator -match '/api/admin/finance/receivables/\$\([^)]*\.id\)/receipts' -and $generator -match '/api/admin/finance/payables/\$\([^)]*\.id\)/payments') `
    -Message "应收应付和收付款必须通过真实财务 API 生成。"
Assert-True -Condition ($generator -notmatch 'foreach \(\$[rp] in @\(\$(receivables|payables)\)\)') `
    -Message "财务 helper 不得用 @() 包装泛型列表遍历，避免 pwsh binder 类型错误。"
$stage029Start = $generator.IndexOf("function Ensure-Stage029ProjectCostDataset")
$stage029End = $generator.IndexOf("function Ensure-FinanceSettlementPosted", $stage029Start)
Assert-True -Condition ($stage029Start -ge 0 -and $stage029End -gt $stage029Start) `
    -Message "生成器必须封装 029 项目成本最小真实数据主链，不能只保留零散 helper。"
$stage029Function = $generator.Substring($stage029Start, $stage029End - $stage029Start)
Assert-True -Condition ($generator -match '\[switch\]\s*\$Stage029Only') `
    -Message "生成器必须提供 Stage029Only 增量模式，供正式 V30 副本只补齐 029 数据而不重放旧业务单据。"
Assert-ContainsInOrder -Text $generator -Needles @(
    '$bom029 = Ensure-Bom -Code "$DemoPrefix-BOM-029-FG-V1"',
    'if ($Stage029Only) {',
    'Ensure-Stage029InventoryMutationWindow | Out-Null',
    '$stage029Dataset = Ensure-Stage029ProjectCostDataset',
    'Stage029Only 模式仅通过真实 API 在既有 V30 演示副本增量补齐 029 最小项目成本数据',
    '$Manifest.Save()',
    'return',
    'Ensure-Substitute'
) -Message "Stage029Only 必须在 029 BOM 就绪后、旧采购/库存/生产链重放前完成 029 主链并退出。"
Assert-True -Condition ($generator -match '\$stage029Dataset = Ensure-Stage029ProjectCostDataset') `
    -Message "029 项目成本最小真实数据必须接入主生成流程，不能只定义未调用 helper。"
Assert-True -Condition ($stage029Function -match 'Ensure-PurchaseOrder -Key "\$DemoPrefix-PO-029-FG-SAFETY"' `
        -and $stage029Function -match 'Ensure-PurchaseReceipt -Key "\$DemoPrefix-PR-029-FG-SAFETY"' `
        -and $stage029Function -match 'Process-PendingQualityInspections -SourceType "PURCHASE_RECEIPT"' `
        -and $stage029Function -match 'Ensure-WarehouseTransferPosted -Key "029-FG-SAFETY-TO-FG"' `
        -and $stage029Function -match '029 同 FG 公共发货安全库存' `
        -and $stage029Function -match 'quantity = "2\.000000"') `
    -Message "029 同 FG 公共安全库存必须以 2 件通过采购订单、收货过账、质检合格和调拨真实 API 建立，不能用 SQL 或期初绕过。"
Assert-True -Condition ($stage029Function -notmatch 'Ensure-OutsourcingIssuePosted') `
    -Message "029 精确 168 主链不得调用外协发料 helper，避免外协发料额外进入 MATERIAL。"
Assert-True -Condition ($stage029Function -notmatch '029-P1-RAW-110' -and $stage029Function -notmatch '\$raw110Layer') `
    -Message "029 材料 110 必须保留为公共库存被项目工单显式领用，不得先转项目成本层。"
Assert-True -Condition ($generator -match 'function Get-InventoryBatch[\s\S]*\[bool\] \$OnlyAvailable = \$true[\s\S]*onlyAvailable = \$OnlyAvailable\.ToString\(\)\.ToLowerInvariant\(\)' `
        -and $stage029Function -match 'Get-InventoryBatch -BatchNo "\$DemoPrefix-BATCH-029-RAW-110" -OnlyAvailable \$false') `
    -Message "029 材料 110 首次运行后可用量为 0，重跑只能通过 onlyAvailable=false 取批次主键，不能要求仍有可用库存。"
Assert-True -Condition ($stage029Function -match 'targetWarehouseId = \$RawWarehouse\.id' `
        -and $stage029Function -match 'Get-ProjectCostLayerForBatch -Project \$projectP1 -Material \$Raw48 -Warehouse \$RawWarehouse' `
        -and $stage029Function -match 'IssueWarehouse \$RawWarehouse' `
        -and $stage029Function -match 'MaterialCode \$Raw48\.code -WarehouseId \$RawWarehouse\.id') `
    -Message "029 材料 48 必须在 RawWarehouse 形成项目成本层，保证同一工单能同时合法领用 PROJECT 48 和 PUBLIC 110。"
Assert-True -Condition ($stage029Function -match '(?s)MaterialCode \$Raw110\.code.*WarehouseId \$RawWarehouse\.id.*OwnershipType "PUBLIC"' `
        -and $stage029Function -match '029 材料 110 领料来源必须是原料仓 PUBLIC 库存' `
        -and $stage029Function -match '\$null -ne \$raw110IssueLine\.projectId' `
        -and $stage029Function -match '\$null -ne \$raw110IssueLine\.costLayerId') `
    -Message "029 材料 110 领料行必须从 RawWarehouse 以 PUBLIC 权属提交，并在运行期断言 projectId/costLayerId 为空。"
Assert-True -Condition ($stage029Function -match 'unitPrice = "5000\.00"' `
        -and $stage029Function -match 'quantity = "2\.000000"' `
        -and $stage029Function -match 'Ensure-ProjectCostCalculationVerified') `
    -Message "029 必须通过真实销售发货 2 件、单价 5000 和项目成本核算 API 证明发货收入 10000。"
Assert-True -Condition ($stage029Function -match 'ExpectedCompleteness "INCOMPLETE"' `
        -and $stage029Function -match 'PROJECT_COST_LABOR_UNPRICED' `
        -and $stage029Function -match 'ExpectedTotalCost "838\.00"' `
        -and $stage029Function -match 'ExpectedShipmentGrossMargin "9162\.00"' `
        -and $generator -match 'DELIVERY_WITHOUT_FINISHED_COST') `
    -Message "029 主链必须精确断言 838/9162，同时把自动报工未定价识别为 INCOMPLETE 和确认阻断。"
$costRecordStart = $generator.IndexOf("function Ensure-CostRecord")
$costRecordEnd = $generator.IndexOf("function Ensure-SalesInvoiceConfirmed", $costRecordStart)
Assert-True -Condition ($costRecordStart -ge 0 -and $costRecordEnd -gt $costRecordStart) `
    -Message "自测无法定位 Ensure-CostRecord 函数边界。"
$costRecordFunction = $generator.Substring($costRecordStart, $costRecordEnd - $costRecordStart)
Assert-ContainsInOrder -Text $costRecordFunction -Needles @(
    'Get-FirstByRemark -Path "/api/admin/cost/records" -Remark $remark -Query @{',
    'workOrderId = $WorkOrder.id',
    'costType = $CostType',
    'sourceType = "MANUAL_ENTRY"',
    'dateFrom = $BusinessDate',
    'dateTo = $BusinessDate',
    'keyword = ""'
) -Message "029 手工人工成本记录复跑必须用成本记录 API 的工单/类型/手工来源/日期过滤后再按 remark 匹配，不能依赖不检索 remark 的 keyword。"
$outsourcingInvoiceStart = $generator.IndexOf("function Ensure-OutsourcingPurchaseInvoiceConfirmed")
$outsourcingInvoiceEnd = $generator.IndexOf("function Ensure-ExpenseConfirmed", $outsourcingInvoiceStart)
Assert-True -Condition ($outsourcingInvoiceStart -ge 0 -and $outsourcingInvoiceEnd -gt $outsourcingInvoiceStart) `
    -Message "自测无法定位 Ensure-OutsourcingPurchaseInvoiceConfirmed 函数边界。"
$outsourcingInvoiceFunction = $generator.Substring($outsourcingInvoiceStart, $outsourcingInvoiceEnd - $outsourcingInvoiceStart)
Assert-ContainsInOrder -Text $outsourcingInvoiceFunction -Needles @(
    '$externalInvoiceNo = "$DemoPrefix-PI-$Key"',
    'Path "/api/admin/finance/purchase-invoices"',
    'keyword = $externalInvoiceNo',
    'Path "/api/admin/finance/purchase-invoices/candidates"',
    'settlementKind = "OUTSOURCING"',
    'sourceType = "OUTSOURCING_RECEIPT"',
    'keyword = $OutsourcingReceipt.receiptNo',
    '$_.sourceId -eq $OutsourcingReceipt.id',
    '$candidate.sourceLineId',
    'ownershipType = "PROJECT"',
    'projectId = $OutsourcingReceipt.projectId',
    'New-PurchaseInvoiceLine -SourceLineId $candidate.sourceLineId'
) -Message "029 外协实际发票必须通过真实采购发票候选 API 获取外协收货来源行，不能读取详情 DTO 不暴露的 line.id。"
Assert-True -Condition ($outsourcingInvoiceFunction -notmatch '\$receiptLine\.id') `
    -Message "外协收货详情不返回行 id，生成器不得把 receipt.lines.id 当作采购发票来源行。"
Assert-True -Condition ($generator -match 'function Ensure-ReversalDocumentsPosted' -and $generator -match '/api/admin/sales/returns' -and $generator -match '/api/admin/procurement/returns' -and $generator -match '/api/admin/production/material-returns' -and $generator -match '/api/admin/production/material-supplements') `
    -Message "销售退货、采购退货、生产退料和补料必须通过真实冲销 API 生成。"
$reversalStart = $generator.IndexOf("function Ensure-ReversalDocumentsPosted")
$reversalEnd = $generator.IndexOf("function Ensure-StocktakeDocuments", $reversalStart)
Assert-True -Condition ($reversalStart -ge 0 -and $reversalEnd -gt $reversalStart) `
    -Message "自测无法定位 Ensure-ReversalDocumentsPosted 函数边界。"
$reversalFunction = $generator.Substring($reversalStart, $reversalEnd - $reversalStart)
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/sales/returns/$($salesReturn.id)/post" -Body ([ordered]@{',
    'version = $salesReturn.version',
    'reason = "验收演示销售退货过账"',
    'idempotencyKey = "$RunId-SALES-RETURN-POST"'
) -Message "销售退货过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/procurement/returns/$($purchaseReturn.id)/post" -Body ([ordered]@{',
    'version = $purchaseReturn.version',
    'reason = "验收演示采购退货过账"',
    'idempotencyKey = "$RunId-PURCHASE-RETURN-POST"'
) -Message "采购退货过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-True -Condition ($generator -match 'function Get-SalesReturnBySourceShipment' -and $generator -match 'source\.sourceId -eq \$Shipment\.id') `
    -Message "销售退货列表摘要不返回 remark，同库复跑必须按源发货单定位既有退货单。"
Assert-True -Condition ($generator -match '/api/admin/sales/return-sources' -and $generator -match 'sourceAllocationId = \$returnAllocation\.sourceAllocationId') `
    -Message "批次销售退货必须先读取 return-sources 候选并提交 sourceAllocationId，不能直接复用普通批次字段。"
Assert-True -Condition ($generator -match 'sourceShipmentLineId = \$sourceLine\.id\s+quantity = "0\.500000"') `
    -Message "收款样例之后的销售退货只能做部分退货，避免退货金额超过当前可冲金额。"
Assert-True -Condition ($generator -match 'elseif \(\$salesReturn\.status -eq "DRAFT"\)\s*\{\s*\$salesReturn = Invoke-DemoApi -Session \$Session -Method Put -Path "/api/admin/sales/returns/\$\(\$salesReturn\.id\)"') `
    -Message "同库复跑遇到旧销售退货 DRAFT 时，必须用真实 PUT 契约更新为当前行数量和追踪来源后再过账。"
Assert-True -Condition ($generator -match '/api/admin/production/material-return-sources' -and $generator -match 'sourceAllocationId = \$materialReturnAllocation\.sourceAllocationId') `
    -Message "批次生产退料必须先读取 material-return-sources 候选并提交 sourceAllocationId。"
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/production/material-returns" -Body ([ordered]@{',
    'clientRequestId = "$RunId-MATERIAL-RETURN"',
    'idempotencyKey = "$RunId-MATERIAL-RETURN-CREATE"'
) -Message "生产退料创建必须同时保留自然去重 clientRequestId 和稳定幂等键。"
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/production/material-returns/$($materialReturn.id)/post" -Body ([ordered]@{',
    'version = $materialReturn.version',
    'reason = "验收演示生产退料过账"',
    'idempotencyKey = "$RunId-MATERIAL-RETURN-POST"'
) -Message "生产退料过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/production/material-supplements" -Body ([ordered]@{',
    'clientRequestId = "$RunId-MATERIAL-SUPPLEMENT"',
    'idempotencyKey = "$RunId-MATERIAL-SUPPLEMENT-CREATE"'
) -Message "生产补料创建必须同时保留自然去重 clientRequestId 和稳定幂等键。"
Assert-ContainsInOrder -Text $reversalFunction -Needles @(
    'Path "/api/admin/production/material-supplements/$($supplement.id)/post" -Body ([ordered]@{',
    'version = $supplement.version',
    'reason = "验收演示生产补料过账"',
    'idempotencyKey = "$RunId-MATERIAL-SUPPLEMENT-POST"'
) -Message "生产补料过账必须按 VersionedActionRequest 携带当前版本、中文原因和稳定幂等键。"
Assert-True -Condition ($generator -match 'Ensure-ReversalDocumentsPosted -SalesShipment \$shipmentSemiA -PurchaseReceipt \$receiptMain `\s+-ProductionIssue \$productionExecution\.issue -WorkOrder \$workOrderReleased') `
    -Message "生产补料样例必须挂到 RELEASED/IN_PROGRESS 工单，不能使用已完成工单作为来源。"
Assert-True -Condition ($generator -match 'function Ensure-ProductionIssuePosted') `
    -Message "生产补料样例必须先通过同一工单真实领料过账形成成本来源。"
$supplementSourceIssueIndex = $generator.IndexOf('$supplementSourceIssue = Ensure-ProductionIssuePosted -Key "SUPPLEMENT-SOURCE"')
$reversalDocsIndex = $generator.IndexOf('$reversalDocs = Ensure-ReversalDocumentsPosted')
Assert-True -Condition ($supplementSourceIssueIndex -ge 0 -and $reversalDocsIndex -gt $supplementSourceIssueIndex) `
    -Message "同一工单领料成本来源必须在生产补料创建和过账之前生成。"
Assert-True -Condition ($generator -match 'DEMO-RESERVATION-SAMPLE' -and $generator -match 'MAT-AUX-SCREW' -and $generator -match '\$woScrewLine\.requiredQuantity') `
    -Message "生产样例必须包含非追踪螺钉用料并保留已释放工单，形成额外 BOM 快照和真实锁定余额。"
Assert-True -Condition ($generator -match 'adjustmentReceipt' -and $generator -match 'sourceType = "RECEIPT"\s+sourceId = \$AdjustmentReceipt\.id\s+targetId = \$ReceivableForAdjustment\.id' -and $generator -notmatch 'sourceType = "SALES_RETURN"\s+sourceId = \$salesReturn\.id\s+targetId = \$ReceivableForAdjustment\.id') `
    -Message "往来调整必须使用独立应收上的真实收款源，不能用销售退货单二次冲减同一往来。"
Assert-True -Condition ($generator -match '验收演示未盘草稿盘点' -and $generator -match 'STK-DRAFT-START' -and $generator -notmatch 'countedQuantity = \$null') `
    -Message "未盘样例必须启动盘点形成真实快照行，并保持 countedQuantity 为空而不是提交 null。"
Assert-True -Condition ($generator -match 'function Ensure-StocktakeDocuments' -and $generator -match 'function Get-StocktakeLinesPage' -and $generator -match '/api/admin/inventory/stocktakes/\$\(\$Stocktake\.id\)/lines\?page=\$Page&pageSize=\$PageSize') `
    -Message "盘点必须消费分页行接口，不能依赖详情无界 lines。"
Assert-True -Condition ($generator -match 'function Get-AllStocktakeLines' -and $generator -match 'while \(\$page -le \$data\.totalPages\)' -and $generator -notmatch '\$firstPage = Get-StocktakeLinesPage[\s\S]*\$secondPage = Get-StocktakeLinesPage') `
    -Message "盘点行读取必须按 totalPages 循环或精确 scope，不得固定只读第 1、2 页。"
Assert-True -Condition ($generator -match 'varianceUnitCost' -and $generator -match 'varianceReason' -and $generator -match 'INVENTORY_STOCKTAKE') `
    -Message "项目正差异盘盈必须提交单位成本、行级原因和有效附件证据。"
Assert-True -Condition ($generator -match 'function Ensure-ValuationAdjustmentPosted' -and $generator -match 'Submit-And-ApproveInventoryDocument -Document \$existing -Path "/api/admin/inventory/valuation-adjustments"') `
    -Message "估值调整必须通过真实单据和固定审批过账。"
Assert-True -Condition ($generator -match '\$latest\.status -eq "DRAFT" -or \$latest\.status -eq "RECONCILED"') `
    -Message "库存审批 helper 必须支持盘点 RECONCILED 状态提交审批，避免范围锁永久停留。"
Assert-True -Condition ($generator -match 'function Ensure-DocumentTaskSamples' -and $generator -match '/api/admin/imports/materials' -and $generator -match '/api/admin/imports/bom-drafts' -and $generator -match '/api/admin/exports/materials' -and $generator -match '/api/admin/print-tasks' -and $generator -match '/api/admin/document-tasks/\$\([^)]*\.id\)/cancel') `
    -Message "文档任务必须覆盖导入、导出、打印和取消任务真实 API。"
Assert-True -Condition ($generator -match 'MISSING-CATEGORY' -and $generator -match 'MISSING-UNIT') `
    -Message "无效物料导入必须使用当前服务端会拒绝的真实引用错误，不能依赖空编码自动占号路径。"
Assert-True -Condition ($generator -match '\$itemHeader = @\("lineNo", "childMaterialCode", "businessUnit", "businessQuantity", "lossRate", "warehouse", "remark"\)' -and $generator -notmatch 'items\.lineNo') `
    -Message "BOM 草稿导入 items 工作表必须使用后端真实冻结表头，不能带 items. 前缀。"
Assert-True -Condition ($common -match '\[hashtable\] \$Headers = @\{\}' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-EXPORT-MATERIAL' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-EXPORT-BOM' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-PRINT') `
    -Message "文档任务 JSON 端点必须通过真实 Idempotency-Key 头创建任务，不能被服务端幂等校验拒绝。"
Assert-True -Condition ($generator -match 'Get-FileSha256' -and $generator -match 'Invoke-DemoMultipart -Path "/api/admin/imports/materials"[\s\S]*"Idempotency-Key" = "\$RunId-DOC-IMPORT-MATERIAL-VALID"' -and $generator -notmatch 'Find-DemoDocumentTask -TaskType "MATERIAL_IMPORT"') `
    -Message "文档导入任务必须依赖真实幂等键和文件 sha256/文件名等价校验，不得按 taskType+status 宽泛复用旧任务。"
Assert-True -Condition ($generator -match 'StableXlsxEntryTime' -and $generator -match '\$entry\.LastWriteTime = \$StableXlsxEntryTime' -and $generator -match '\$sheetNames = @\(\$Sheets\.Keys \| Sort-Object\)') `
    -Message "XLSX 导入源必须固定 ZIP entry 时间和工作表顺序，确保同文件同幂等键复跑时 SHA 稳定。"
Assert-True -Condition ($generator -match 'QHERP_TASK_WORKER_ENABLED=false' -or $generator -match 'DocumentWorkerMode') `
    -Message "生成器或入口必须显式区分 worker 关闭窗口和 worker 处理窗口。"
Assert-True -Condition ($generator -match 'Ensure-PendingApprovalTaskSample' -and $generator -match 'PENDING-CONTRACT' -and $generator -match 'pendingApproval') `
    -Message "生成器必须保留一个真实 PENDING 审批任务样例，同时不影响通过/驳回/撤回/取消链。"
Assert-True -Condition ($generator -match 'AcceptanceAuthorizationPath' -and $generator -match 'Assert-DemoAcceptanceAuthorization' -and $generator -notmatch 'AcceptanceConfirmPhrase') `
    -Message "生成器不得绕过备份入口直接 Acceptance；正式模式必须校验由重建入口创建的短时目标绑定授权材料。"
Assert-True -Condition ($rebuild -match '\[ValidateSet\("Temporary", "Acceptance"\)\]' -and $rebuild -match 'REBUILD qherp/qherp-private ON 18080') `
    -Message "安全重建入口必须显式区分 Temporary/Acceptance，并要求正式重建精确确认词。"
Assert-True -Condition ($rebuild -match 'Database -ne "qherp"' -and $rebuild -match 'MinioBucket -ne "qherp-private"' -and $rebuild -match 'Port -ne 18080' -and $rebuild -match 'Assert-LoopbackApiBaseUrl') `
    -Message "Acceptance 模式只能允许 qherp、qherp-private、loopback 18080。"
Assert-True -Condition ($rebuild -match 'qherp_demo_build_' -and $rebuild -match 'qherp-demo-build-' -and $rebuild -match 'Temporary 模式禁止连接 18080') `
    -Message "Temporary 模式只能允许临时库、临时 bucket，并必须禁止 18080。"
Assert-True -Condition ($rebuild -match 'Backup-PostgresDatabase' -and $rebuild -match 'Backup-MinioBucket' -and $rebuild.IndexOf('Backup-PostgresDatabase') -lt $rebuild.IndexOf('Reset-PostgresDatabase') -and $rebuild.IndexOf('Backup-MinioBucket') -lt $rebuild.IndexOf('Reset-MinioBucket')) `
    -Message "安全重建入口必须在任何重建动作前先备份 PostgreSQL 和 MinIO。"
Assert-True -Condition ($rebuild -match 'QHERP_INITIAL_ADMIN_PASSWORD' -and $rebuild -match 'QHERP_DEMO_USER_PASSWORD' -and $rebuild -notmatch 'Qherp@2026!') `
    -Message "安全重建入口不得保留明文默认密码，凭据必须由环境变量注入。"
Assert-True -Condition ($rebuild -match 'Assert-PortAvailable' -and $rebuild -match 'Start-ManagedAcceptanceApi' -and $rebuild -match 'ExpectedGitCommit' -and $rebuild -match 'RepositoryRoot' -and $rebuild -match 'QHERP_DATASOURCE_URL' -and $rebuild -match 'qherp-private') `
    -Message "Acceptance 入口必须拒绝占用中的 18080，并从指定仓库/提交启动自己管理的隐藏 API 进程。"
Assert-True -Condition ($rebuild -match 'WorkerDisabled' -and $rebuild -match 'WorkerEnabled' -and $rebuild -match 'workerDisabledApi' -and $rebuild -match 'workerEnabledApi' -and $rebuild -match 'Stop-ManagedApi' -and $rebuild -match 'disabledManifestPath' -and $rebuild -match 'enabledManifestPath') `
    -Message "重建入口必须编排 worker-disabled 到 worker-enabled 两阶段受管 API，不能只用 WorkerEnabled 跳过取消任务窗口。"
Assert-True -Condition ($rebuild -match 'Get-NetTCPConnection[\s\S]*OwningProcess' -and $rebuild -match 'launcherPid') `
    -Message "受管 API 必须记录并停止实际监听端口的 Java PID，不能只记录 Maven wrapper 启动器 PID。"
Assert-True -Condition ($rebuild -match 'Wait-ManagedApiLoginReady' -and $rebuild.IndexOf('Wait-ManagedApiLoginReady') -gt $rebuild.IndexOf('Wait-ApiHealth')) `
    -Message "受管 API 健康 UP 后必须等待初始管理员可登录，再运行生成器，避免账号权限初始化竞态。"
Assert-True -Condition ($rebuild -match 'function Stop-StartedManagedApi' -and $rebuild -match 'catch\s*\{[\s\S]*Stop-StartedManagedApi') `
    -Message "受管 API 启动过程中健康或登录就绪失败时必须停止已启动进程，不能等调用方拿到对象后才清理。"
Assert-True -Condition ($rebuild -match 'New-AcceptanceAuthorization' -and $rebuild.IndexOf('New-AcceptanceAuthorization') -gt $rebuild.IndexOf('Backup-MinioBucket') -and $rebuild -match 'AcceptanceAuthorizationPath') `
    -Message "Acceptance 授权材料必须由重建入口在备份成功后创建并传给生成器。"
Assert-True -Condition ($rebuild -match 'Assert-PostgresOwner' -and $rebuild -match 'Quote-PostgresIdentifier' -and $rebuild -notmatch 'create database \$quoted owner \$PostgresUser') `
    -Message "Reset-PostgresDatabase 必须固定/白名单校验 owner 并安全引用，不能直接拼接 PostgresUser。"
Assert-True -Condition ($rebuild -match 'manifestPath' -and $rebuild -match 'validationPath' -and $rebuild -match 'generatedAndValidated = \$validationSucceeded') `
    -Message "重建 summary 必须在实际生成和验证成功后写 generatedAndValidated=true，并记录最终 manifest/validation 路径。"

function Test-FinancialCloseAuditEventRuleUsesV34ResourceType {
    param([string] $SqlText)

    $auditEventRuleStart = $SqlText.IndexOf("from fin_close_audit_event")
    if ($auditEventRuleStart -lt 0) {
        return $false
    }
    $auditEventRuleEnd = $SqlText.IndexOf("union all select 'FINANCIAL_CLOSE_BANK_RECONCILIATION_BALANCE_DYNAMIC'", $auditEventRuleStart)
    if ($auditEventRuleEnd -le $auditEventRuleStart) {
        return $false
    }
    $auditEventRule = $SqlText.Substring($auditEventRuleStart, $auditEventRuleEnd - $auditEventRuleStart)

    return ($auditEventRule.Contains("resource_type in ('FIN_RECEIVABLE', 'FIN_PAYABLE', 'FIN_RECEIPT', 'FIN_PAYMENT',") `
        -and $auditEventRule.Contains("'PRJ_COST_CALCULATION', 'BIZ_PERIOD_CLOSE_RUN'") `
        -and (-not $auditEventRule.Contains($legacyFinancialCloseAuditEventTargetColumn)))
}

function Test-FinancialCloseValidatorRulesAreStrict {
    param([string] $SqlText)

    $tableRulesAreStrict = ($SqlText.Contains("FINANCIAL_CLOSE_TABLES_V34") `
        -and $SqlText.Contains("count(*)::text, '21', count(*) = 21") `
        -and $SqlText.Contains("fin_close_run") `
        -and $SqlText.Contains("fin_close_check_run") `
        -and $SqlText.Contains("fin_close_snapshot") `
        -and $SqlText.Contains("fin_close_reopen_request") `
        -and $SqlText.Contains("fin_bank_account") `
        -and $SqlText.Contains("fin_bank_reconciliation_match") `
        -and $SqlText.Contains("fin_tax_period_summary") `
        -and $SqlText.Contains("fin_tax_payment_record"))
    $permissionRulesAreStrict = ($SqlText.Contains("FINANCIAL_CLOSE_PERMISSIONS_V34") `
        -and $SqlText.Contains("count(*)::text, '24', count(*) = 24") `
        -and $SqlText.Contains("financial-close:period:close") `
        -and $SqlText.Contains("financial-close:period:reopen") `
        -and $SqlText.Contains("financial-close:bank-sensitive:view") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_SYSTEM_ADMIN_PERMISSIONS_V34"))
    $approvalAndAccountRulesAreStrict = ($SqlText.Contains("FINANCIAL_CLOSE_REOPEN_APPROVAL_V34") `
        -and $SqlText.Contains("FINANCIAL_PERIOD_REOPEN") `
        -and $SqlText.Contains("financial-close:period:reopen") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_ACCOUNT_CODES_V34") `
        -and $SqlText.Contains("4103") `
        -and $SqlText.Contains("2221.03") `
        -and $SqlText.Contains("2221.06") `
        -and $SqlText.Contains("6801"))
    $constraintRulesAreStrict = ($SqlText.Contains("FINANCIAL_CLOSE_IMMUTABLE_TRIGGERS_V34") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_STATUS_VALUES_V34") `
        -and $SqlText.Contains("CHECKING") `
        -and $SqlText.Contains("BLOCKED") `
        -and $SqlText.Contains("CONSUMED") `
        -and $SqlText.Contains("REOPENED") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_CURRENT_CLOSED_UNIQUE_DYNAMIC") `
        -and $SqlText.Contains("having count(*) > 1"))
    $dynamicRulesAreStrict = ($SqlText.Contains("FINANCIAL_CLOSE_READY_CHECKS_CONSUMABLE_DYNAMIC") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_CLOSED_PERIOD_LOCK_DYNAMIC") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_NO_UPSTREAM_WRITE_DYNAMIC") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_BANK_RECONCILIATION_BALANCE_DYNAMIC") `
        -and $SqlText.Contains("join fin_close_run c on c.period_id = r.period_id") `
        -and $SqlText.Contains("join gl_accounting_period p on p.id = r.period_id") `
        -and (Test-FinancialCloseAuditEventRuleUsesV34ResourceType -SqlText $SqlText) `
        -and $SqlText.Contains("difference_amount <> 0") `
        -and (-not $SqlText.Contains($legacyAdjustedBankBalanceColumn)) `
        -and $SqlText.Contains("FINANCIAL_CLOSE_TAX_SUMMARY_SOURCE_DYNAMIC") `
        -and $SqlText.Contains("FINANCIAL_CLOSE_TAX_DISCLAIMER_V34"))
    $objectRuleIsDynamic = ($SqlText.Contains("FILE_OBJECTS_AVAILABLE_MIN_8") `
        -and (-not $SqlText.Contains("count(*) = 18")) `
        -and (-not $SqlText.Contains("MINIO_BUCKET_OBJECTS_18")))

    return ($tableRulesAreStrict `
        -and $permissionRulesAreStrict `
        -and $approvalAndAccountRulesAreStrict `
        -and $constraintRulesAreStrict `
        -and $dynamicRulesAreStrict `
        -and $objectRuleIsDynamic)
}

Assert-True -Condition (Test-FinancialCloseAuditEventRuleUsesV34ResourceType -SqlText $validatorSql) `
    -Message "032 验证器必须在 fin_close_audit_event 使用 V34 列 resource_type，不得引用旧目标列。"

Assert-True -Condition (Test-FinancialCloseValidatorRulesAreStrict -SqlText $validatorSql) `
    -Message "正式演示数据验证器必须新增 032 表、权限、反结账审批、科目、状态/约束、不可变、动态业务事实和动态对象一致性门禁。"

$legacyAuditEventSql = @"
union all select 'FINANCIAL_CLOSE_NO_UPSTREAM_WRITE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
    '032 只能只读消费 028/029/030，并通过 031 草稿承接会计影响；032 审计不得标记对上游业务表的写动作成功。'
    from fin_close_audit_event
    where result = 'SUCCESS'
    and $legacyFinancialCloseAuditEventTargetColumn in ('FIN_RECEIVABLE', 'FIN_PAYABLE', 'FIN_RECEIPT', 'FIN_PAYMENT',
        'PRJ_COST_CALCULATION', 'BIZ_PERIOD_CLOSE_RUN')
union all select 'FINANCIAL_CLOSE_BANK_RECONCILIATION_BALANCE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
    '已确认银行对账必须零差额，调整后银行余额与账面余额完全一致。'
    from fin_bank_reconciliation_run
    where status = 'CONFIRMED'
    and difference_amount <> 0
"@
Assert-True -Condition (-not (Test-FinancialCloseAuditEventRuleUsesV34ResourceType -SqlText $legacyAuditEventSql)) `
    -Message "自测必须拒绝 fin_close_audit_event 旧目标列引用。"

$weakenedFinancialCloseSql = @"
select 'FINANCIAL_CLOSE_TABLES_V34'::text, 'financial-close'::text, count(*)::text, '>= 21', count(*) >= 21,
    'V34 必须创建财务结账相关表。' from information_schema.tables where table_schema = 'public' and table_name like 'fin_%';
union all select 'FINANCIAL_CLOSE_PERMISSIONS_V34', 'financial-close', count(*)::text, '>= 24', count(*) >= 24,
    '032 权限必须初始化。' from sys_permission where code like 'financial-close:%';
union all select 'FILE_OBJECTS_AVAILABLE_MIN_8', 'attachment', count(*)::text, '18', count(*) = 18,
    '对象数量必须为 18。' from platform_file_object where status = 'AVAILABLE';
"@
Assert-True -Condition (-not (Test-FinancialCloseValidatorRulesAreStrict -SqlText $weakenedFinancialCloseSql)) `
    -Message "自测必须拒绝只按前缀宽泛计数、缺少动态事实门禁或写死 18 个对象的 032 验证器。"

function Test-Stage032IsolationStrategyIsStrict {
    param([string] $ScriptText)

    return ($ScriptText.Contains("qherp_032_review") `
        -and $ScriptText.Contains("qherp-032-review") `
        -and $ScriptText.Contains("qherp/qherp-private") `
        -and $ScriptText.Contains("Assert-Stage032IsolationTarget") `
        -and $ScriptText.Contains("Assert-Stage032FormalResourceRejected") `
        -and $ScriptText.Contains("New-Stage032AcceptanceDataPlan") `
        -and $ScriptText.Contains("两个月会计期间") `
        -and $ScriptText.Contains("双人审批") `
        -and $ScriptText.Contains("反结账") `
        -and $ScriptText.Contains("多对多") `
        -and $ScriptText.Contains("增值税") `
        -and $ScriptText.Contains("所得税") `
        -and $ScriptText.Contains("正式库禁止写入") `
        -and (-not $ScriptText.Contains('Database = "qherp"')) `
        -and (-not $ScriptText.Contains('MinioBucket = "qherp-private"')))
}

Assert-True -Condition ((Test-Path -LiteralPath $stage032IsolationPath) -and (Test-Stage032IsolationStrategyIsStrict -ScriptText $stage032Isolation)) `
    -Message "032 隔离数据准备策略必须固定 qherp_032_review/qherp-032-review，拒绝正式 qherp/qherp-private，并覆盖跨期间、双人审批/反结账、对账和税额汇总。"

Write-Host "demo-data-self-test 通过"
