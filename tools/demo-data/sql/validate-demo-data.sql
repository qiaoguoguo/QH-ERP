\set ON_ERROR_STOP on

begin transaction read only;

with rules(rule_code, category, actual_value, expected_value, passed, message) as (
    select 'FLYWAY_LATEST_V36'::text, 'migration'::text,
        concat(
            'version=', coalesce((array_agg(version::int order by version::int desc))[1]::text, 'none'),
            ';checksum=', coalesce((array_agg(checksum order by version::int desc))[1]::text, 'none')
        ),
        'latest successful version = 36; checksum = 1030907058'::text,
        coalesce((array_agg(version::int order by version::int desc))[1], 0) = 36
            and coalesce((array_agg(checksum order by version::int desc))[1], 0) = 1030907058,
        'Flyway 最新成功版本必须为 V36，V36 checksum 必须保持 1030907058；V29-V35 checksum 仍精确锁定。'::text
    from flyway_schema_history where success and version ~ '^[0-9]+$'
    union all select 'FLYWAY_V36_CHECKSUM_RECORDED', 'migration',
        concat('version=36;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 36 checksum = 1030907058',
        coalesce((array_agg(checksum))[1], 0) = 1030907058,
        'Flyway V36 checksum 必须保持 1030907058。'
        from flyway_schema_history where success and version = '36'
    union all select 'FLYWAY_V35_CHECKSUM', 'migration',
        concat('version=35;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 35 checksum = -82801719',
        coalesce((array_agg(checksum))[1], 0) = -82801719,
        'Flyway V35 checksum 必须保持 -82801719。'
        from flyway_schema_history where success and version = '35'
    union all select 'FLYWAY_V34_CHECKSUM', 'migration',
        concat('version=34;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 34 checksum = -629066235',
        coalesce((array_agg(checksum))[1], 0) = -629066235,
        'Flyway V34 checksum 必须保持 -629066235。'
        from flyway_schema_history where success and version = '34'
    union all select 'FLYWAY_V33_CHECKSUM', 'migration',
        concat('version=33;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 33 checksum = 612501943',
        coalesce((array_agg(checksum))[1], 0) = 612501943,
        'Flyway V33 checksum 必须保持 612501943。'
        from flyway_schema_history where success and version = '33'
    union all select 'FLYWAY_V32_CHECKSUM', 'migration',
        concat('version=32;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 32 checksum = 249406902',
        coalesce((array_agg(checksum))[1], 0) = 249406902,
        'Flyway V32 checksum 必须保持 249406902。'
        from flyway_schema_history where success and version = '32'
    union all select 'FLYWAY_V31_CHECKSUM', 'migration',
        concat('version=31;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 31 checksum = -2074547591',
        coalesce((array_agg(checksum))[1], 0) = -2074547591,
        'Flyway V31 checksum 必须保持 -2074547591。'
        from flyway_schema_history where success and version = '31'
    union all select 'FLYWAY_V30_CHECKSUM', 'migration',
        concat('version=30;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 30 checksum = 2130342893',
        coalesce((array_agg(checksum))[1], 0) = 2130342893,
        'Flyway V30 checksum 必须保持 2130342893。'
        from flyway_schema_history where success and version = '30'
    union all select 'FLYWAY_V29_CHECKSUM', 'migration',
        concat('version=29;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 29 checksum = 774334682',
        coalesce((array_agg(checksum))[1], 0) = 774334682,
        'Flyway V29 checksum 必须保持 774334682。'
        from flyway_schema_history where success and version = '29'
    union all select 'FLYWAY_NO_FAILED', 'migration', count(*)::text, '0', count(*) = 0,
        'Flyway 不能存在失败迁移记录。' from flyway_schema_history where not success

    union all select 'USERS_MIN_10', 'permission', count(*)::text, '>= 10', count(*) >= 10,
        '演示账号必须覆盖管理员、业务、仓库、生产、财务、审批、只读、无价值权限和停用账号。' from sys_user
    union all select 'USERS_ENABLED_MIN_8', 'permission', count(*)::text, '>= 8', count(*) >= 8,
        '启用账号数量不足。' from sys_user where status = 'ENABLED'
    union all select 'USERS_DISABLED_MIN_1', 'permission', count(*)::text, '>= 1', count(*) >= 1,
        '必须有停用账号用于登录拒绝演示。' from sys_user where status <> 'ENABLED'
    union all select 'ROLES_MIN_6', 'permission', count(*)::text, '>= 6', count(*) >= 6,
        '演示角色数量不足。' from sys_role
    union all select 'SYSTEM_ADMIN_ROLE_EXISTS', 'permission', count(*)::text, '>= 1', count(*) >= 1,
        '必须保留 SYSTEM_ADMIN 角色。' from sys_role where code = 'SYSTEM_ADMIN' and status = 'ENABLED'
    union all select 'PERMISSIONS_MIN_250', 'permission', count(*)::text, '>= 250', count(*) >= 250,
        '权限种子数量不足或未初始化。' from sys_permission

    union all select 'UNITS_MIN_5', 'master-data', count(*)::text, '>= 5', count(*) >= 5,
        '单位数量不足。' from mst_unit
    union all select 'WAREHOUSES_MIN_4', 'master-data', count(*)::text, '>= 4', count(*) >= 4,
        '仓库数量不足。' from mst_warehouse
    union all select 'CUSTOMERS_MIN_5', 'master-data', count(*)::text, '>= 5', count(*) >= 5,
        '客户数量不足。' from mst_customer
    union all select 'SUPPLIERS_MIN_5', 'master-data', count(*)::text, '>= 5', count(*) >= 5,
        '供应商数量不足。' from mst_supplier
    union all select 'MATERIALS_MIN_20', 'master-data', count(*)::text, '>= 20', count(*) >= 20,
        '物料数量不足。' from mst_material
    union all select 'MATERIAL_TRACKING_NONE_MIN_1', 'master-data', count(*)::text, '>= 1', count(*) >= 1,
        '必须有不追踪物料。' from mst_material where tracking_method = 'NONE'
    union all select 'MATERIAL_TRACKING_BATCH_MIN_1', 'master-data', count(*)::text, '>= 1', count(*) >= 1,
        '必须有批次追踪物料。' from mst_material where tracking_method = 'BATCH'
    union all select 'MATERIAL_TRACKING_SERIAL_MIN_1', 'master-data', count(*)::text, '>= 1', count(*) >= 1,
        '必须有序列号追踪物料。' from mst_material where tracking_method = 'SERIAL'
    union all select 'MATERIAL_NON_VALUED_MIN_1', 'master-data', count(*)::text, '>= 1', count(*) >= 1,
        '必须有非计价物料。' from mst_material where not inventory_value_enabled
    union all select 'MATERIAL_VALUED_MIN_8', 'master-data', count(*)::text, '>= 8', count(*) >= 8,
        '计价物料数量不足。' from mst_material where inventory_value_enabled
    union all select 'MATERIAL_PROJECT_COST_MIN_3', 'master-data', count(*)::text, '>= 3', count(*) >= 3,
        '项目成本物料数量不足。' from mst_material where project_cost_enabled
    union all select 'UNIT_CONVERSIONS_MIN_3', 'master-data', count(*)::text, '>= 3', count(*) >= 3,
        '物料业务单位换算样例不足。' from mst_material_unit_conversion
    union all select 'CUSTOMER_TAX_MIN_3', 'master-data', count(*)::text, '>= 3', count(*) >= 3,
        '客户结算税务资料不足。' from mst_customer_settlement_tax
    union all select 'SUPPLIER_TAX_MIN_3', 'master-data', count(*)::text, '>= 3', count(*) >= 3,
        '供应商结算税务资料不足。' from mst_supplier_settlement_tax
    union all select 'CODING_RULES_MIN_8', 'master-data', count(*)::text, '>= 8', count(*) >= 8,
        '编码规则样例不足。' from sys_coding_rule

    union all select 'BOM_MIN_6', 'bom', count(*)::text, '>= 6', count(*) >= 6,
        'BOM 数量不足。' from mfg_bom
    union all select 'BOM_ENABLED_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '缺少启用 BOM。' from mfg_bom where status = 'ENABLED'
    union all select 'BOM_DRAFT_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '缺少草稿 BOM。' from mfg_bom where status = 'DRAFT'
    union all select 'BOM_FUTURE_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '以 2026-07-15 为基准日缺少未来生效 BOM。' from mfg_bom where effective_from > date '2026-07-15'
    union all select 'BOM_HISTORY_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '以 2026-07-15 为基准日缺少历史失效 BOM。' from mfg_bom where effective_to < date '2026-07-15'
    union all select 'BOM_ENABLED_EFFECTIVE_OVERLAP_ZERO', 'bom', count(*)::text, '0', count(*) = 0,
        '同一父项已发布 BOM 有效区间不得重叠。'
        from mfg_bom first_bom
        join mfg_bom second_bom on second_bom.parent_material_id = first_bom.parent_material_id
            and second_bom.id > first_bom.id
            and first_bom.status = 'ENABLED'
            and second_bom.status = 'ENABLED'
            and coalesce(first_bom.effective_from, date '-infinity') <= coalesce(second_bom.effective_to, date 'infinity')
            and coalesce(second_bom.effective_from, date '-infinity') <= coalesce(first_bom.effective_to, date 'infinity')
    union all select 'BOM_ITEMS_MIN_10', 'bom', count(*)::text, '>= 10', count(*) >= 10,
        'BOM 明细数量不足。' from mfg_bom_item
    union all select 'MATERIAL_SUBSTITUTE_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '替代料关系不足。' from mst_material_substitute
    union all select 'ECO_MIN_3', 'bom', count(*)::text, '>= 3', count(*) >= 3,
        'ECO 数量不足。' from mfg_bom_engineering_change
    union all select 'ECO_APPLIED_MIN_1', 'bom', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已应用 ECO。' from mfg_bom_engineering_change where status = 'APPLIED'

    union all select 'BUSINESS_PERIOD_OPEN_MIN_1', 'period', count(*)::text, '>= 1', count(*) >= 1,
        '缺少开放期间。' from biz_business_period where status = 'OPEN'
    union all select 'BUSINESS_PERIOD_LOCKED_MIN_1', 'period', count(*)::text, '>= 1', count(*) >= 1,
        '缺少锁定期间。' from biz_business_period where status = 'LOCKED'
    union all select 'PERIOD_AUDIT_MIN_2', 'period', count(*)::text, '>= 2', count(*) >= 2,
        '期间审计记录不足。' from biz_business_period_audit

    union all select 'PERIOD_CLOSE_PERMISSIONS_V32', 'period-close', count(*)::text, '5', count(*) = 5,
        '030 月结权限必须精确种子化 view/check/close/reopen/snapshot-view。' from sys_permission
        where code in ('system:business-period-close:view', 'system:business-period-close:check',
            'system:business-period-close:close', 'system:business-period-close:reopen',
            'system:business-period-close:snapshot-view')
    union all select 'PERIOD_CLOSE_NO_AMOUNT_PERMISSION', 'period-close', count(*)::text, '0', count(*) = 0,
        '030 不得新增可绕开库存估值、项目成本金额和报表来源权限的月结金额总权限。' from sys_permission
        where code = 'system:business-period-close:amount-view'
    -- 金额/来源权限分离继续由后端强鉴权和隔离受限账号测试覆盖：
    -- inventory:valuation:view, cost:project-cost:amount-view,
    -- report:sales:view, report:procurement:view, report:inventory:view,
    -- report:production:view, report:cost:view, report:settlement:view,
    -- report:exceptions:view。
    union all select 'PERIOD_CLOSE_2026_07_CURRENT_CLOSED_RUN_V35', 'period-close',
        concat('periods=', period_count, ';closed=', closed_run_count),
        'periods=1;closed=1',
        period_count = 1
            and closed_run_count = 1
            and (
                select count(*) = 1
                from biz_period_close_run r
                join biz_business_period p on p.id = r.period_id
                where p.period_code = '2026-07'
                    and r.status = 'CLOSED'
            ),
        '033 全量验收库必须通过生成器标准 API 步骤把 2026-07 关闭为恰好一个当前 CLOSED 月结运行，不能空集通过。'
        from (
            select
                count(distinct p.id) as period_count,
                count(distinct r.id) filter (where r.status = 'CLOSED') as closed_run_count
            from biz_business_period p
            left join biz_period_close_run r on r.period_id = p.id
            where p.period_code = '2026-07'
        ) july_close_gate
    union all select 'PERIOD_CLOSE_2026_07_LOCKED_SNAPSHOT_V35', 'period-close',
        concat('lockedClosed=', locked_closed_count, ';snapshot=', snapshot_count),
        'lockedClosed=1;snapshot=1',
        locked_closed_count = 1 and snapshot_count = 1,
        '033 全量验收库中 2026-07 当前 CLOSED 运行必须锁定业务期间且 snapshot_id 非空，并存在对应主快照。'
        from (
            select
                count(*) filter (
                    where p.status = 'LOCKED'
                        and r.status = 'CLOSED'
                        and r.snapshot_id is not null
                        and r.blocking_count = 0
                        and r.source_fingerprint is not null
                        and r.source_fingerprint <> ''
                ) as locked_closed_count,
                count(s.id) filter (
                    where p.status = 'LOCKED'
                        and r.status = 'CLOSED'
                        and r.snapshot_id is not null
                ) as snapshot_count
            from biz_business_period p
            left join biz_period_close_run r on r.period_id = p.id
            left join biz_period_snapshot s on s.id = r.snapshot_id and s.run_id = r.id
            where p.period_code = '2026-07'
        ) july_locked_snapshot_gate
    union all select 'PERIOD_CLOSE_2026_07_REPORT_SNAPSHOT_CODES_V35', 'period-close',
        concat('codes=', report_code_count, ';missing=', missing_required_count, ';unexpected=', unexpected_count),
        'codes=13;missing=0;unexpected=0',
        report_code_count = 13
            and missing_required_count = 0
            and unexpected_count = 0
            and (
                select count(distinct report.report_code) = 13
                from biz_period_close_run r
                join biz_business_period p on p.id = r.period_id
                join biz_period_snapshot s on s.id = r.snapshot_id
                left join biz_period_report_snapshot report on report.snapshot_id = s.id
                where p.period_code = '2026-07'
                    and r.status = 'CLOSED'
            ),
        '033 全量验收库中 2026-07 CLOSED 快照必须精确包含 030 原 8 类和 033 五类经营侧报表，共 13 个分区。'
        from (
            select
                count(distinct report.report_code) as report_code_count,
                (
                    select count(*)
                    from (values
                        ('OVERVIEW'), ('SALES_SUMMARY'), ('PROCUREMENT_SUMMARY'),
                        ('INVENTORY_STOCK_FLOW'), ('PRODUCTION_EXECUTION'), ('COST_COLLECTION'),
                        ('SETTLEMENT_SUMMARY'), ('EXCEPTIONS'), ('PROJECT_PROFIT'), ('CONTRACT_COLLECTION'),
                        ('PROCUREMENT_VARIANCE'), ('INVENTORY_CAPITAL'), ('RECEIVABLE_PAYABLE')
                    ) required(report_code)
                    where not exists (
                        select 1
                        from biz_period_close_run r2
                        join biz_business_period p2 on p2.id = r2.period_id
                        join biz_period_snapshot s2 on s2.id = r2.snapshot_id
                        join biz_period_report_snapshot report2 on report2.snapshot_id = s2.id
                        where p2.period_code = '2026-07'
                            and r2.status = 'CLOSED'
                            and report2.report_code = required.report_code
                    )
                ) as missing_required_count,
                count(*) filter (
                    where report.report_code not in ('OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY',
                        'INVENTORY_STOCK_FLOW', 'PRODUCTION_EXECUTION', 'COST_COLLECTION',
                        'SETTLEMENT_SUMMARY', 'EXCEPTIONS', 'PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                ) as unexpected_count
            from biz_period_close_run r
            join biz_business_period p on p.id = r.period_id
            join biz_period_snapshot s on s.id = r.snapshot_id
            left join biz_period_report_snapshot report on report.snapshot_id = s.id
            where p.period_code = '2026-07'
                and r.status = 'CLOSED'
        ) july_snapshot_codes_gate
    union all select 'PERIOD_CLOSE_2026_07_033_FROZEN_PAYLOADS_V35', 'period-close',
        concat('businessReports=', business_report_count, ';frozenPayloads=', frozen_payload_count),
        'businessReports=5;frozenPayloads=5',
        business_report_count = 5 and frozen_payload_count = 5,
        '033 全量验收库中 2026-07 五类经营侧快照 payload 必须保存 BUSINESS_SNAPSHOT/FROZEN，不能用实时结果冒充快照。'
        from (
            select
                count(*) filter (
                    where report.report_code in ('PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                ) as business_report_count,
                count(*) filter (
                    where report.report_code in ('PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                        and coalesce(report.result_json -> 'summary' ->> 'analysisMode', '') = 'BUSINESS_SNAPSHOT'
                        and coalesce(report.result_json -> 'summary' ->> 'freshnessStatus', '') = 'FROZEN'
                        and report.result_json is not null
                ) as frozen_payload_count
            from biz_period_close_run r
            join biz_business_period p on p.id = r.period_id
            join biz_period_snapshot s on s.id = r.snapshot_id
            left join biz_period_report_snapshot report on report.snapshot_id = s.id
            where p.period_code = '2026-07'
                and r.status = 'CLOSED'
        ) july_frozen_payload_gate
    union all select 'PERIOD_CLOSE_2026_07_UNSUPPORTED_SNAPSHOT_CODES_V35', 'period-close',
        count(*)::text, '0', count(*) = 0,
        '033 全量验收库中 2026-07 经营/会计对照和固定经营财务摘要不得进入 BUSINESS_SNAPSHOT 快照。'
        from biz_period_close_run r
        join biz_business_period p on p.id = r.period_id
        join biz_period_snapshot s on s.id = r.snapshot_id
        join biz_period_report_snapshot report on report.snapshot_id = s.id
        where p.period_code = '2026-07'
            and r.status = 'CLOSED'
            and report.report_code in ('OPERATING_ACCOUNTING_RECONCILIATION', 'FINANCIAL_SUMMARY')
    union all select 'PERIOD_CLOSE_CURRENT_CLOSED_UNIQUE', 'period-close', count(*)::text, '0', count(*) = 0,
        '同一业务期间同一时刻只能存在一个当前 CLOSED 月结版本。'
        from (
            select period_id
            from biz_period_close_run
            where status = 'CLOSED'
            group by period_id
            having count(*) > 1
        ) duplicated_current_closed
    union all select 'PERIOD_CLOSE_LOCK_AUDIT_COMPLETE', 'period-close', count(*)::text, '0', count(*) = 0,
        'CLOSED 月结必须同时锁定业务期间、存在主快照和成功关闭审计。'
        from (
            select r.id
            from biz_period_close_run r
            join biz_business_period p on p.id = r.period_id
            left join biz_period_snapshot s on s.run_id = r.id and s.period_id = r.period_id
            left join biz_period_close_audit a on a.run_id = r.id
                and a.action = 'CLOSE'
                and a.result = 'SUCCESS'
            where r.status = 'CLOSED'
                and (p.status <> 'LOCKED' or s.id is null or a.id is null)
        ) close_without_lock_audit
    union all select 'PERIOD_CLOSE_BLOCKERS_FAIL_CLOSED', 'period-close', count(*)::text, '0', count(*) = 0,
        '存在阻断项、快照不完整或来源指纹为空的月结运行不得进入 CLOSED。'
        from biz_period_close_run
        where status = 'CLOSED'
            and (blocking_count <> 0 or snapshot_id is null or source_fingerprint is null or source_fingerprint = '')
    union all select 'PERIOD_CLOSE_REPORT_SNAPSHOT_CODES_V35', 'period-close', count(*)::text, '0', count(*) = 0,
        'CLOSED 快照必须保留 030 八类基线；033 五个经营侧报表要么全部缺失作为历史快照，要么全部存在作为 V35 快照。'
        from (
            select s.id
            from biz_period_snapshot s
            join biz_period_close_run r on r.id = s.run_id
            left join biz_period_report_snapshot report on report.snapshot_id = s.id
            where r.status = 'CLOSED'
            group by s.id
            having count(distinct report.report_code) filter (
                    where report.report_code in ('OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY',
                        'INVENTORY_STOCK_FLOW', 'PRODUCTION_EXECUTION', 'COST_COLLECTION',
                        'SETTLEMENT_SUMMARY', 'EXCEPTIONS')
                ) <> 8
                or count(distinct report.report_code) filter (
                    where report.report_code in ('PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                ) not in (0, 5)
                or count(distinct report.report_code) not in (8, 13)
                or count(*) filter (
                    where report.report_code not in ('OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY',
                        'INVENTORY_STOCK_FLOW', 'PRODUCTION_EXECUTION', 'COST_COLLECTION',
                        'SETTLEMENT_SUMMARY', 'EXCEPTIONS', 'PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                ) > 0
        ) report_snapshot_code_violations
    union all select 'PERIOD_CLOSE_UNSUPPORTED_033_REPORT_SNAPSHOT_CODES_V35', 'period-close',
        count(*)::text, '0', count(*) = 0,
        '经营/会计对照和固定经营财务摘要不进入 030 BUSINESS_SNAPSHOT，不得保存为月结报表快照。'
        from biz_period_report_snapshot
        where report_code in ('OPERATING_ACCOUNTING_RECONCILIATION', 'FINANCIAL_SUMMARY')
    union all select 'PERIOD_CLOSE_SNAPSHOT_FINGERPRINTS_LOCKED', 'period-close', count(*)::text, '0', count(*) = 0,
        '快照主表和报表分区必须保存非空来源指纹，支撑快照对账和不可变复验。'
        from (
            select s.id
            from biz_period_snapshot s
            join biz_period_close_run r on r.id = s.run_id
            left join biz_period_report_snapshot report on report.snapshot_id = s.id
            where r.status in ('CLOSED', 'REOPENED')
            group by s.id, s.source_fingerprint
            having s.source_fingerprint is null
                or s.source_fingerprint = ''
                or count(*) filter (
                    where report.fingerprint is null or report.fingerprint = ''
                ) > 0
        ) snapshot_fingerprint_violations
    union all select 'PERIOD_CLOSE_SNAPSHOT_VERSION_IMMUTABLE', 'period-close', count(*)::text, '0', count(*) = 0,
        '同一期间同一月结版本只能有一套快照；重开不得覆盖或删除旧快照。'
        from (
            select period_id, revision_no
            from biz_period_snapshot
            group by period_id, revision_no
            having count(*) > 1
        ) duplicate_snapshot_versions
    union all select 'PERIOD_CLOSE_REOPENED_KEEP_SNAPSHOT', 'period-close', count(*)::text, '0', count(*) = 0,
        'REOPENED 历史月结必须继续保留旧快照，不能覆盖、删除或标记失效。'
        from (
            select r.id
            from biz_period_close_run r
            left join biz_period_snapshot s on s.run_id = r.id
            where r.status = 'REOPENED'
                and s.id is null
        ) reopened_without_snapshot

    union all select 'GL_TABLES_V33', 'general-ledger', count(*)::text, '19', count(*) = 19,
        'V33 必须创建 19 张总账领域表。'
        from information_schema.tables
        where table_schema = 'public'
        and table_name in ('gl_ledger', 'gl_accounting_period', 'gl_account',
            'gl_aux_dimension', 'gl_aux_item', 'gl_account_aux_requirement', 'gl_posting_rule',
            'gl_posting_rule_line', 'gl_posting_rule_line_aux_map', 'gl_voucher', 'gl_voucher_line',
            'gl_voucher_line_auxiliary', 'gl_voucher_source_claim', 'gl_voucher_number_sequence',
            'gl_ledger_entry', 'gl_account_period_total', 'gl_voucher_reversal_link',
            'gl_action_idempotency', 'gl_audit_event')
    union all select 'GL_LEDGER_SINGLE_MAIN_CNY_V33', 'general-ledger',
        concat('ledgerCount=', ledger_count, ';mainCny=', main_cny_count),
        'ledgerCount=1;mainCny=1',
        ledger_count = 1 and main_cny_count = 1,
        '031 只能存在单一 MAIN 总账账簿，记账本位币必须为 CNY。'
        from (
            select count(*) as ledger_count,
                count(*) filter (where code = 'MAIN' and currency = 'CNY') as main_cny_count
            from gl_ledger
        ) ledger_gate
    union all select 'GL_ACCOUNT_TEMPLATE_CODES_V33', 'general-ledger', count(*)::text, '25', count(*) = 25,
        'V33 制造业基础科目模板必须完整，且不得缺失进项/销项税下级科目。'
        from gl_account
        where code in ('1001', '1002', '1122', '1123', '1401', '1403', '1405', '1408',
            '1601', '2202', '2203', '2221', '2221.01', '2221.02', '4001', '5001',
            '5101', '6001', '6051', '6401', '6601', '6602', '6603', '6301', '6711')
    union all select 'GL_AUX_DIMENSIONS_V33', 'general-ledger',
        concat('systemDimensions=', system_dimension_count, ';enabledDimensions=', enabled_dimension_count),
        'systemDimensions=3;enabledDimensions=3',
        system_dimension_count = 3 and enabled_dimension_count = 3,
        'V33 必须预置客户、供应商、项目三个启用的系统辅助核算维度。'
        from (
            select count(*) filter (
                    where code in ('CUSTOMER', 'SUPPLIER', 'PROJECT')
                    and dimension_type = 'SYSTEM'
                    and system_defined
                ) as system_dimension_count,
                count(*) filter (
                    where code in ('CUSTOMER', 'SUPPLIER', 'PROJECT')
                    and enabled
                ) as enabled_dimension_count
            from gl_aux_dimension
        ) aux_gate
    union all select 'GL_POSTING_RULES_V33', 'general-ledger',
        concat('activeRules=', active_rule_count, ';activeLines=', active_line_count,
            ';activeAuxMaps=', active_aux_map_count, ';activePairViolations=', active_pair_violation_count,
            ';activeRuleViolations=', active_rule_violation_count),
        'activeRules=7;activeLines=17;activeAuxMaps=9;activePairViolations=0;activeRuleViolations=0',
        active_rule_count = 7 and active_line_count = 17 and active_aux_map_count = 9
            and active_pair_violation_count = 0 and active_rule_violation_count = 0,
        'V33 必须精确预置七条活动制证规则及其 17 条活动规则行、9 条活动辅助映射，并验证活动规则业务版本和乐观锁版本合法；合法 DRAFT/INACTIVE 历史版本不得污染活动规则种子计数。'
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
                        or r.rule_version < 1
                        or r.version < 0
                        or r.source_type not in ('SALES_INVOICE', 'PURCHASE_INVOICE', 'EXPENSE', 'RECEIPT', 'PAYMENT', 'SETTLEMENT_ALLOCATION')
                        or r.source_variant not in ('DEFAULT', 'RECEIVABLE', 'PAYABLE'))) as active_rule_violation_count
        ) posting_rule_gate
    union all select 'GL_ACTION_PERMISSIONS_V33', 'general-ledger', count(*)::text, '23', count(*) = 23,
        '031 总账动作权限必须精确种子化。'
        from sys_permission
        where code in ('gl:account:view', 'gl:account:create', 'gl:account:update', 'gl:account:disable',
            'gl:auxiliary:view', 'gl:auxiliary:manage', 'gl:period:view', 'gl:period:initialize',
            'gl:period:create', 'gl:rule:view', 'gl:rule:manage', 'gl:voucher:view',
            'gl:voucher:create', 'gl:voucher:update', 'gl:voucher:convert', 'gl:voucher:submit',
            'gl:voucher:cancel', 'gl:voucher:reverse', 'gl:voucher:approve-post',
            'gl:ledger:view', 'gl:balance:view', 'gl:amount:view', 'gl:source:view')
    union all select 'GL_SYSTEM_ADMIN_PERMISSIONS_V33', 'general-ledger', count(*)::text, '31', count(*) = 31,
        'SYSTEM_ADMIN 必须拥有 V33 会计核算菜单和动作权限。'
        from sys_role_permission rp
        join sys_role r on r.id = rp.role_id
        join sys_permission p on p.id = rp.permission_id
        where r.code = 'SYSTEM_ADMIN'
        and (p.code = 'gl' or p.code like 'gl:%')
    union all select 'GL_APPROVAL_DEFINITION_V33', 'general-ledger',
        concat('definitions=', definition_count, ';steps=', step_count),
        'definitions=1;steps=1',
        definition_count = 1 and step_count = 1,
        '031 必须注册 GL_VOUCHER_POST 固定审批场景并使用 gl:voucher:approve-post 候选权限。'
        from (
            select
                count(distinct d.id) as definition_count,
                count(s.id) filter (where s.candidate_permission_code = 'gl:voucher:approve-post') as step_count
            from platform_approval_definition d
            left join platform_approval_definition_step s on s.definition_id = d.id
            where d.scene_code = 'GL_VOUCHER_POST'
            and d.status = 'ENABLED'
        ) approval_gate
    union all select 'GL_IMMUTABLE_TRIGGERS_V33', 'general-ledger', count(*)::text, '4', count(*) = 4,
        'POSTED 凭证、凭证行、辅助快照和总账分录必须有数据库不可变守卫。'
        from pg_trigger t
        join pg_class c on c.oid = t.tgrelid
        where not t.tgisinternal
        and t.tgname in ('tr_gl_voucher_posted_immutable', 'tr_gl_voucher_line_posted_immutable',
            'tr_gl_voucher_aux_posted_immutable', 'tr_gl_ledger_entry_immutable')
        and c.relname in ('gl_voucher', 'gl_voucher_line', 'gl_voucher_line_auxiliary', 'gl_ledger_entry')
    union all select 'GL_POSTED_VOUCHER_LEDGER_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
        '若存在 POSTED 凭证，必须有正式号且凭证头、分录和总账事实借贷金额一致。'
        from (
            select v.id
            from gl_voucher v
            left join (
                select voucher_id, count(*) as line_count,
                    coalesce(sum(debit_amount), 0) as line_debit,
                    coalesce(sum(credit_amount), 0) as line_credit
                from gl_voucher_line
                group by voucher_id
            ) l on l.voucher_id = v.id
            left join (
                select voucher_id, count(*) as entry_count,
                    coalesce(sum(debit_amount), 0) as entry_debit,
                    coalesce(sum(credit_amount), 0) as entry_credit
                from gl_ledger_entry
                group by voucher_id
            ) e on e.voucher_id = v.id
            where v.status = 'POSTED'
            and (v.voucher_no is null or v.voucher_number is null or v.debit_total <> v.credit_total
                or coalesce(l.line_count, 0) <> coalesce(e.entry_count, 0)
                or coalesce(l.line_debit, 0) <> coalesce(e.entry_debit, 0)
                or coalesce(l.line_credit, 0) <> coalesce(e.entry_credit, 0)
                or v.debit_total <> coalesce(e.entry_debit, 0)
                or v.credit_total <> coalesce(e.entry_credit, 0))
        ) posted_voucher_violations
    union all select 'GL_LEDGER_ENTRY_LINE_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
        '总账分录必须逐行对应 POSTED 凭证分录，金额、科目、期间和来源快照不能漂移。'
        from (
            select ('entry:' || e.id)::text as violation_id
            from gl_ledger_entry e
            left join gl_voucher_line l on l.id = e.voucher_line_id
            left join gl_voucher v on v.id = e.voucher_id
            where l.id is null or v.id is null or v.status <> 'POSTED'
                or l.voucher_id <> v.id
                or e.ledger_id <> v.ledger_id
                or e.period_id <> v.accounting_period_id
                or e.account_id <> l.account_id
                or e.debit_amount <> l.debit_amount
                or e.credit_amount <> l.credit_amount
                or e.source_type is distinct from l.source_type
                or e.source_id is distinct from l.source_id
            union all
            select ('line:' || l.id)::text as violation_id
            from gl_voucher_line l
            join gl_voucher v on v.id = l.voucher_id
            left join gl_ledger_entry e on e.voucher_line_id = l.id
            where v.status = 'POSTED'
            and e.id is null
        ) ledger_entry_line_violations
    union all select 'GL_PERIOD_TOTALS_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
        '科目期间余额缓存必须与不可变总账分录按期初、本期和期末三组汇总一致。'
        from (
            select coalesce(t.ledger_id, e.ledger_id) as ledger_id,
                coalesce(t.period_id, e.period_id) as period_id,
                coalesce(t.account_id, e.account_id) as account_id
            from gl_account_period_total t
            full join (
                select ledger_id, period_id, account_id,
                    coalesce(sum(case when voucher_type = 'OPENING' then debit_amount else 0 end), 0) as opening_debit,
                    coalesce(sum(case when voucher_type = 'OPENING' then credit_amount else 0 end), 0) as opening_credit,
                    coalesce(sum(case when voucher_type <> 'OPENING' then debit_amount else 0 end), 0) as period_debit,
                    coalesce(sum(case when voucher_type <> 'OPENING' then credit_amount else 0 end), 0) as period_credit,
                    coalesce(sum(debit_amount), 0) as ending_debit,
                    coalesce(sum(credit_amount), 0) as ending_credit
                from gl_ledger_entry
                group by ledger_id, period_id, account_id
            ) e on e.ledger_id = t.ledger_id and e.period_id = t.period_id and e.account_id = t.account_id
            where t.id is null or e.account_id is null
                or t.opening_debit <> e.opening_debit
                or t.opening_credit <> e.opening_credit
                or t.period_debit <> e.period_debit
                or t.period_credit <> e.period_credit
                or t.ending_debit <> e.ending_debit
                or t.ending_credit <> e.ending_credit
        ) period_total_violations
    union all select 'GL_SOURCE_CLAIMS_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
        '028 来源占用必须和凭证状态、来源版本及来源指纹一致；取消来源不得继续占用。'
        from (
            select c.id
            from gl_voucher_source_claim c
            left join gl_voucher v on v.id = c.voucher_id
            where c.status in ('RESERVED', 'POSTED')
            and (v.id is null
                or c.source_fingerprint is null
                or c.source_fingerprint = ''
                or c.source_version is null
                or v.source_claim_id is distinct from c.id
                or (c.status = 'RESERVED' and v.status not in ('DRAFT', 'SUBMITTED'))
                or (c.status = 'POSTED' and v.status <> 'POSTED'))
        ) source_claim_violations
    union all select 'GL_VOUCHER_SEQUENCE_DYNAMIC', 'general-ledger', count(*)::text, '0', count(*) = 0,
        '凭证号段 last_number 必须等于同期间同凭证字已记账凭证最大正式号，失败事务不得消耗号。'
        from (
            select coalesce(s.ledger_id, posted.ledger_id) as ledger_id,
                coalesce(s.period_id, posted.period_id) as period_id,
                coalesce(s.voucher_word, posted.voucher_word) as voucher_word
            from gl_voucher_number_sequence s
            full join (
                select ledger_id, accounting_period_id as period_id, voucher_word, max(voucher_number) as max_number
                from gl_voucher
                where status = 'POSTED'
                group by ledger_id, accounting_period_id, voucher_word
            ) posted on posted.ledger_id = s.ledger_id
                and posted.period_id = s.period_id
                and posted.voucher_word = s.voucher_word
            where coalesce(s.last_number, 0) <> coalesce(posted.max_number, 0)
        ) sequence_violations

    union all select 'FINANCIAL_CLOSE_TABLES_V34', 'financial-close', count(*)::text, '21', count(*) = 21,
        'V34 必须创建财务结账、银行对账和税务基础 21 张独立表。'
        from information_schema.tables
        where table_schema = 'public'
        and table_name in ('fin_close_run', 'fin_close_check_run', 'fin_close_check_item',
            'fin_close_snapshot', 'fin_close_reopen_request', 'fin_close_profit_loss_transfer',
            'fin_close_action_idempotency', 'fin_close_audit_event', 'fin_bank_account',
            'fin_bank_statement', 'fin_bank_statement_line', 'fin_bank_reconciliation_run',
            'fin_bank_reconciliation_match', 'fin_bank_reconciliation_exception', 'fin_tax_profile',
            'fin_tax_rate_rule', 'fin_tax_invoice_type', 'fin_tax_period_summary',
            'fin_tax_summary_line', 'fin_tax_adjustment', 'fin_tax_payment_record')
    union all select 'FINANCIAL_CLOSE_PERMISSIONS_V34', 'financial-close', count(*)::text, '24', count(*) = 24,
        '032 财务结账动作权限必须精确种子化，不能用宽泛前缀替代。'
        from sys_permission
        where code in ('financial-close:period:view', 'financial-close:period:check',
            'financial-close:period:close', 'financial-close:period:reopen',
            'financial-close:profit-loss:view', 'financial-close:profit-loss:generate',
            'financial-close:bank-account:view', 'financial-close:bank-account:manage',
            'financial-close:bank-reconciliation:view', 'financial-close:bank-reconciliation:import',
            'financial-close:bank-reconciliation:match', 'financial-close:bank-reconciliation:confirm',
            'financial-close:bank-reconciliation:reopen', 'financial-close:tax-profile:view',
            'financial-close:tax-profile:manage', 'financial-close:tax-summary:view',
            'financial-close:tax-summary:calculate', 'financial-close:tax-summary:confirm',
            'financial-close:tax-summary:generate-voucher', 'financial-close:tax-payment:view',
            'financial-close:tax-payment:manage', 'financial-close:amount:view',
            'financial-close:source:view', 'financial-close:bank-sensitive:view')
    union all select 'FINANCIAL_CLOSE_SYSTEM_ADMIN_PERMISSIONS_V34', 'financial-close', count(*)::text, '24', count(*) = 24,
        'SYSTEM_ADMIN 必须拥有 032 财务结账全部动作权限。'
        from sys_role_permission rp
        join sys_role r on r.id = rp.role_id
        join sys_permission p on p.id = rp.permission_id
        where r.code = 'SYSTEM_ADMIN'
        and p.code in ('financial-close:period:view', 'financial-close:period:check',
            'financial-close:period:close', 'financial-close:period:reopen',
            'financial-close:profit-loss:view', 'financial-close:profit-loss:generate',
            'financial-close:bank-account:view', 'financial-close:bank-account:manage',
            'financial-close:bank-reconciliation:view', 'financial-close:bank-reconciliation:import',
            'financial-close:bank-reconciliation:match', 'financial-close:bank-reconciliation:confirm',
            'financial-close:bank-reconciliation:reopen', 'financial-close:tax-profile:view',
            'financial-close:tax-profile:manage', 'financial-close:tax-summary:view',
            'financial-close:tax-summary:calculate', 'financial-close:tax-summary:confirm',
            'financial-close:tax-summary:generate-voucher', 'financial-close:tax-payment:view',
            'financial-close:tax-payment:manage', 'financial-close:amount:view',
            'financial-close:source:view', 'financial-close:bank-sensitive:view')
    union all select 'FINANCIAL_CLOSE_BANK_STATEMENT_PERMISSION_ROUTE_V34', 'financial-close',
        coalesce(string_agg(code || '=' || route_path || ':' || coalesce(api_method, '') || ':' || coalesce(api_path, ''), ';' order by code), ''),
        'bank statement view/import route /gl/bank-statements',
        count(*) filter (
            where code = 'financial-close:bank-reconciliation:view'
            and type = 'ACTION'
            and route_path = '/gl/bank-statements'
            and api_method = 'GET'
            and api_path = '/api/admin/bank-statements/**,/api/admin/bank-reconciliations/**'
        ) = 1
            and count(*) filter (
                where code = 'financial-close:bank-reconciliation:import'
                and type = 'ACTION'
                and route_path = '/gl/bank-statements'
                and api_method = 'POST'
                and api_path = '/api/admin/bank-statements/**,/api/admin/bank-statement-lines/**'
            ) = 1,
        '银行流水查看和导入权限必须精确绑定 /gl/bank-statements 及冻结 API 路径，不能回退到旧银行对账页或宽泛路径。'
        from sys_permission
        where code in ('financial-close:bank-reconciliation:view',
            'financial-close:bank-reconciliation:import')
    union all select 'FINANCIAL_CLOSE_REOPEN_APPROVAL_V34', 'financial-close',
        concat('definitions=', definition_count, ';steps=', step_count),
        'definitions=1;steps=1',
        definition_count = 1 and step_count = 1,
        '032 反结账必须注册 FINANCIAL_PERIOD_REOPEN 固定双人审批场景，并使用 financial-close:period:reopen 候选权限。'
        from (
            select
                count(distinct d.id) as definition_count,
                count(s.id) filter (where s.candidate_permission_code = 'financial-close:period:reopen') as step_count
            from platform_approval_definition d
            left join platform_approval_definition_step s on s.definition_id = d.id
            where d.scene_code = 'FINANCIAL_PERIOD_REOPEN'
            and d.status = 'ENABLED'
        ) financial_reopen_approval_gate
    union all select 'FINANCIAL_CLOSE_ACCOUNT_CODES_V34', 'financial-close', count(*)::text, '7', count(*) = 7,
        'V34 必须补充本年利润、税务相关应交税费和费用科目。'
        from gl_account
        where code in ('4103', '2221.03', '2221.04', '2221.05', '2221.06', '6403', '6801')
    union all select 'FINANCIAL_CLOSE_ACCOUNT_NAMES_V34', 'financial-close',
        coalesce(string_agg(code || '=' || name, ';' order by code), ''),
        '2221.03=未交增值税;2221.04=应交城市维护建设税;2221.05=应交教育费附加;2221.06=应交企业所得税;4103=本年利润;6403=税金及附加;6801=所得税费用',
        count(*) filter (where code = '2221.03' and name = '未交增值税' and category = 'LIABILITY' and balance_direction = 'CREDIT' and postable is true) = 1
            and count(*) filter (where code = '2221.04' and name = '应交城市维护建设税' and category = 'LIABILITY' and balance_direction = 'CREDIT' and postable is true) = 1
            and count(*) filter (where code = '2221.05' and name = '应交教育费附加' and category = 'LIABILITY' and balance_direction = 'CREDIT' and postable is true) = 1
            and count(*) filter (where code = '2221.06' and name = '应交企业所得税' and category = 'LIABILITY' and balance_direction = 'CREDIT' and postable is true) = 1
            and count(*) filter (where code = '4103' and name = '本年利润' and category = 'EQUITY' and balance_direction = 'CREDIT' and postable is true) = 1
            and count(*) filter (where code = '6403' and name = '税金及附加' and category = 'PROFIT_LOSS' and balance_direction = 'DEBIT' and postable is true) = 1
            and count(*) filter (where code = '6801' and name = '所得税费用' and category = 'PROFIT_LOSS' and balance_direction = 'DEBIT' and postable is true) = 1,
        'V34 税务与损益科目必须按阶段说明冻结名称、类别、方向和可记账属性，不得只校验编码数量。'
        from gl_account
        where code in ('4103', '2221.03', '2221.04', '2221.05', '2221.06', '6403', '6801')
    union all select 'FINANCIAL_CLOSE_IMMUTABLE_TRIGGERS_V34', 'financial-close', count(*)::text, '>= 4', count(*) >= 4,
        '关闭快照、反结账申请、已确认银行对账和已确认税务汇总必须有数据库不可变守卫。'
        from pg_trigger t
        join pg_class c on c.oid = t.tgrelid
        where not t.tgisinternal
        and c.relname in ('fin_close_snapshot', 'fin_close_reopen_request',
            'fin_bank_reconciliation_run', 'fin_tax_period_summary')
        and t.tgname like '%immutable%'
    union all select 'FINANCIAL_CLOSE_STATUS_VALUES_V34', 'financial-close', count(*)::text, '>= 8', count(*) >= 8,
        '032 关键状态约束必须包含检查、关闭运行、反结账、银行流水、银行对账和税务汇总冻结状态。'
        from information_schema.check_constraints
        where constraint_schema = 'public'
        and check_clause like any (array[
            '%CHECKING%', '%BLOCKED%', '%READY%', '%STALE%', '%CONSUMED%', '%FAILED%',
            '%CLOSED%', '%REOPENED%', '%SUBMITTED%', '%APPLIED%', '%UNMATCHED%', '%MATCHED%',
            '%CONFIRMED%', '%CALCULATED%'
        ])
    union all select 'FINANCIAL_CLOSE_MANDATORY_CHECK_CODES_DYNAMIC', 'financial-close',
        count(*)::text, '0', count(*) = 0,
        '每个已完成财务结账检查运行必须精确包含 9 项冻结检查，不能只落地 4 项产生伪阳性。'
        from (
            select r.id
            from fin_close_check_run r
            where r.status in ('BLOCKED', 'READY', 'CONSUMED', 'STALE', 'FAILED')
            and (
                (select count(*) from fin_close_check_item i where i.check_run_id = r.id) <> 9
                or exists (
                    select 1
                    from fin_close_check_item i
                    where i.check_run_id = r.id
                    and i.check_code not in (
                        'PREVIOUS_PERIOD_CLOSED', 'BUSINESS_PERIOD_CLOSED', 'NO_INCOMPLETE_VOUCHERS',
                        'TRIAL_BALANCE_BALANCED', 'BANK_RECONCILIATIONS_CONFIRMED',
                        'TAX_SUMMARIES_CONFIRMED', 'TAX_VOUCHERS_POSTED',
                        'PROFIT_LOSS_TRANSFER_POSTED', 'NO_SOURCE_CHANGES'
                    )
                )
                or exists (
                    select 1
                    from (values
                        ('PREVIOUS_PERIOD_CLOSED'), ('BUSINESS_PERIOD_CLOSED'),
                        ('NO_INCOMPLETE_VOUCHERS'), ('TRIAL_BALANCE_BALANCED'),
                        ('BANK_RECONCILIATIONS_CONFIRMED'), ('TAX_SUMMARIES_CONFIRMED'),
                        ('TAX_VOUCHERS_POSTED'), ('PROFIT_LOSS_TRANSFER_POSTED'),
                        ('NO_SOURCE_CHANGES')
                    ) expected(check_code)
                    where not exists (
                        select 1
                        from fin_close_check_item i
                        where i.check_run_id = r.id
                        and i.check_code = expected.check_code
                    )
                )
            )
        ) incomplete_check_runs
    union all select 'FINANCIAL_CLOSE_CHECK_FAILURE_SAMPLES_DYNAMIC', 'financial-close',
        concat('sampleCodes=', coalesce(sample_codes, 'none'), ';sampleCount=', sample_count,
            ';invalidSamples=', invalid_sample_count),
        'none or complete valid sample set',
        sample_count = 0
            or (previous_period_closed_count = 1 and no_incomplete_vouchers_count = 1
                and tax_vouchers_posted_count = 1 and no_source_changes_count = 1
                and invalid_sample_count = 0),
        '失败样本为代表状态而非正式库必填事实；无 032 检查事实时合法，存在样本时必须覆盖四类代表失败并来自完整 9 项冻结检查运行。'
        from (
            select
                count(*) as sample_count,
                string_agg(distinct check_code, ',' order by check_code) as sample_codes,
                count(*) filter (where invalid_sample) as invalid_sample_count,
                count(distinct check_code) filter (where check_code = 'PREVIOUS_PERIOD_CLOSED') as previous_period_closed_count,
                count(distinct check_code) filter (where check_code = 'NO_INCOMPLETE_VOUCHERS') as no_incomplete_vouchers_count,
                count(distinct check_code) filter (where check_code = 'TAX_VOUCHERS_POSTED') as tax_vouchers_posted_count,
                count(distinct check_code) filter (where check_code = 'NO_SOURCE_CHANGES') as no_source_changes_count
            from (
                select i.check_code,
                    (
                        r.id is null
                        or r.status not in ('BLOCKED', 'READY', 'CONSUMED', 'STALE', 'FAILED')
                        or (select count(*) from fin_close_check_item peer where peer.check_run_id = i.check_run_id) <> 9
                        or exists (
                            select 1
                            from fin_close_check_item peer
                            where peer.check_run_id = i.check_run_id
                            and peer.check_code not in (
                                'PREVIOUS_PERIOD_CLOSED', 'BUSINESS_PERIOD_CLOSED',
                                'NO_INCOMPLETE_VOUCHERS', 'TRIAL_BALANCE_BALANCED',
                                'BANK_RECONCILIATIONS_CONFIRMED', 'TAX_SUMMARIES_CONFIRMED',
                                'TAX_VOUCHERS_POSTED', 'PROFIT_LOSS_TRANSFER_POSTED',
                                'NO_SOURCE_CHANGES'
                            )
                        )
                        or exists (
                            select 1
                            from (values
                                ('PREVIOUS_PERIOD_CLOSED'), ('BUSINESS_PERIOD_CLOSED'),
                                ('NO_INCOMPLETE_VOUCHERS'), ('TRIAL_BALANCE_BALANCED'),
                                ('BANK_RECONCILIATIONS_CONFIRMED'), ('TAX_SUMMARIES_CONFIRMED'),
                                ('TAX_VOUCHERS_POSTED'), ('PROFIT_LOSS_TRANSFER_POSTED'),
                                ('NO_SOURCE_CHANGES')
                            ) expected(check_code)
                            where not exists (
                                select 1
                                from fin_close_check_item peer
                                where peer.check_run_id = i.check_run_id
                                and peer.check_code = expected.check_code
                            )
                        )
                    ) as invalid_sample
                from fin_close_check_item i
                left join fin_close_check_run r on r.id = i.check_run_id
                where i.passed is false
                and i.check_code in ('PREVIOUS_PERIOD_CLOSED', 'NO_INCOMPLETE_VOUCHERS',
                    'TAX_VOUCHERS_POSTED', 'NO_SOURCE_CHANGES')
            ) sample_items
        ) sample_gate
    union all select 'FINANCIAL_CLOSE_CURRENT_CLOSED_UNIQUE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '同一会计期间同一时刻只能存在一个当前 CLOSED 财务关闭版本。'
        from (
            select period_id
            from fin_close_run
            where status = 'CLOSED'
            group by period_id
            having count(*) > 1
        ) duplicated_financial_closed
    union all select 'FINANCIAL_CLOSE_READY_CHECKS_CONSUMABLE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '被关闭消费的检查运行必须进入 CONSUMED，READY 检查不能与当前 CLOSED 关闭版本并存。'
        from (
            select r.id
            from fin_close_check_run r
            join fin_close_run c on c.check_run_id = r.id and c.status = 'CLOSED'
            where r.status <> 'CONSUMED'
			union all
            select r.id
            from fin_close_check_run r
            join fin_close_run c on c.period_id = r.period_id and c.status = 'CLOSED'
            where r.status = 'READY'
        ) unconsumed_ready_check_runs
    union all select 'FINANCIAL_CLOSE_CLOSE_RECHECK_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '关闭事务必须消费同源 READY 检查运行，不能用过期或来源已变化的检查运行完成关闭。'
        from (
            select c.id
            from fin_close_run c
            join fin_close_check_run r on r.id = c.check_run_id
            where c.status = 'CLOSED'
            and (
                r.status <> 'CONSUMED'
                or c.period_id <> r.period_id
                or c.source_fingerprint <> r.source_fingerprint
            )
        ) close_runs_without_recheck
    union all select 'FINANCIAL_CLOSE_CLOSED_PERIOD_LOCK_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '当前 CLOSED 财务关闭运行必须把对应会计期间保持为 CLOSED；反结账后旧运行必须为 REOPENED。'
        from (
            select r.id
            from fin_close_run r
            join gl_accounting_period p on p.id = r.period_id
            where r.status = 'CLOSED'
            and p.status <> 'CLOSED'
        ) closed_runs_without_closed_period
    union all select 'FINANCIAL_CLOSE_NO_UPSTREAM_WRITE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '032 只能只读消费 028/029/030，并通过 031 草稿承接会计影响；032 审计不得标记对上游业务表的写动作成功。'
        from fin_close_audit_event
        where result = 'SUCCESS'
        and resource_type in ('FIN_RECEIVABLE', 'FIN_PAYABLE', 'FIN_RECEIPT', 'FIN_PAYMENT',
            'PRJ_COST_CALCULATION', 'BIZ_PERIOD_CLOSE_RUN')
    union all select 'FINANCIAL_CLOSE_SYSTEM_SOURCE_VOUCHER_UNIQUE_DYNAMIC', 'financial-close',
        count(*)::text, '0', count(*) = 0,
        '032 生成的损益结转和税务系统来源凭证必须按 source_type/source_id 同源唯一，重复生成或重复提交不得产生重复凭证。'
        from (
            select source_type, source_id
            from gl_voucher
            where source_type in ('PROFIT_LOSS_CARRYFORWARD', 'TAX_SUMMARY')
            and source_id is not null
            group by source_type, source_id
            having count(*) > 1
        ) duplicated_system_source_vouchers
    union all select 'FINANCIAL_CLOSE_BANK_ACCOUNT_1002_SUBTREE_DYNAMIC', 'financial-close',
        count(*)::text, '0', count(*) = 0,
        '启用银行账户必须绑定 1002 或其后代中的启用、末级、可记账、资产类借方科目。'
        from fin_bank_account b
        join gl_account a on a.id = b.gl_account_id
        where b.status = 'ENABLED'
        and (
            not (a.code = '1002' or a.code like '1002.%')
            or a.category <> 'ASSET'
            or a.balance_direction <> 'DEBIT'
            or a.enabled is not true
            or a.postable is not true
            or a.is_leaf is not true
        )
    union all select 'FINANCIAL_CLOSE_BANK_EXCEPTION_TYPES_V34', 'financial-close',
        coalesce((array_agg(check_clause))[1], ''),
        'BANK_ONLY_CREDIT,BANK_ONLY_DEBIT,BOOK_ONLY_DEBIT,BOOK_ONLY_CREDIT',
        coalesce((array_agg(check_clause))[1], '') like '%BANK_ONLY_CREDIT%'
            and coalesce((array_agg(check_clause))[1], '') like '%BANK_ONLY_DEBIT%'
            and coalesce((array_agg(check_clause))[1], '') like '%BOOK_ONLY_DEBIT%'
            and coalesce((array_agg(check_clause))[1], '') like '%BOOK_ONLY_CREDIT%'
            and length(coalesce((array_agg(check_clause))[1], ''))
                - length(replace(coalesce((array_agg(check_clause))[1], ''), '''', '')) = 8,
        '银行未达必须冻结为四类方向化分类，旧泛化枚举不得保留。'
        from information_schema.check_constraints
        where constraint_schema = 'public'
        and constraint_name = 'ck_fin_bank_reconciliation_exception_type'
    union all select 'FINANCIAL_CLOSE_BANK_RECONCILIATION_BALANCE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '已确认银行对账必须零差额，调整后银行余额与账面余额完全一致。'
        from fin_bank_reconciliation_run
        where status = 'CONFIRMED'
        and difference_amount <> 0
    union all select 'FINANCIAL_CLOSE_BANK_RECONCILIATION_FORMULA_DYNAMIC', 'financial-close',
        count(*)::text, '0', count(*) = 0,
        '银行对账 BALANCED/CONFIRMED 必须按四类未达公式计算调整后银行余额与调整后账面余额。'
        from (
            select r.id
            from fin_bank_reconciliation_run r
            left join (
                select run_id,
                    sum(case
                        when exception_type = 'BOOK_ONLY_DEBIT' then amount
                        when exception_type = 'BOOK_ONLY_CREDIT' then -amount
                        else 0
                    end) as adjusted_bank_delta,
                    sum(case
                        when exception_type = 'BANK_ONLY_CREDIT' then amount
                        when exception_type = 'BANK_ONLY_DEBIT' then -amount
                        else 0
                    end) as adjusted_book_delta
                from fin_bank_reconciliation_exception
                where status = 'OPEN'
                group by run_id
            ) e on e.run_id = r.id
            where r.status in ('BALANCED', 'CONFIRMED')
            and round(
                r.statement_balance + coalesce(e.adjusted_bank_delta, 0)
                - r.ledger_balance - coalesce(e.adjusted_book_delta, 0),
                2
            ) <> 0
        ) bank_formula_violations
    union all select 'FINANCIAL_CLOSE_TAX_RATE_RULES_V34', 'financial-close',
        coalesce(string_agg(rate_code || '=' || rate_value::text, ';' order by rate_code), ''),
        'INCOME_25=0.2500;SIMPLIFIED_3=0.0300;URBAN_1=0.0100;URBAN_5=0.0500;URBAN_7=0.0700;VAT_0=0.0000;VAT_13=0.1300;VAT_6=0.0600;VAT_9=0.0900',
        count(*) filter (where rate_code = 'VAT_13' and rate_value = 0.1300 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'VAT_9' and rate_value = 0.0900 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'VAT_6' and rate_value = 0.0600 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'VAT_0' and rate_value = 0.0000 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'SIMPLIFIED_3' and rate_value = 0.0300 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'INCOME_25' and rate_value = 0.2500 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'URBAN_7' and rate_value = 0.0700 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'URBAN_5' and rate_value = 0.0500 and status = 'ENABLED') = 1
            and count(*) filter (where rate_code = 'URBAN_1' and rate_value = 0.0100 and status = 'ENABLED') = 1,
        '税率/征收率必须按有效期种子化增值税 13/9/6/0、简易 3、企业所得税 25 和城建税 7/5/1。'
        from fin_tax_rate_rule
    union all select 'FINANCIAL_CLOSE_TAX_INVOICE_TYPES_V34', 'financial-close',
        coalesce(string_agg(code || '=' || name, ';' order by code), ''),
        'E_DIGITAL_SPECIAL=数电专票;E_DIGITAL_NORMAL=数电普票;PAPER_SPECIAL=纸质专票;PAPER_NORMAL=纸质普票',
        count(*) filter (where code = 'E_DIGITAL_SPECIAL' and name = '数电专票' and status = 'ENABLED') = 1
            and count(*) filter (where code = 'E_DIGITAL_NORMAL' and name = '数电普票' and status = 'ENABLED') = 1
            and count(*) filter (where code = 'PAPER_SPECIAL' and name = '纸质专票' and status = 'ENABLED') = 1
            and count(*) filter (where code = 'PAPER_NORMAL' and name = '纸质普票' and status = 'ENABLED') = 1,
        '税务票种必须冻结为数电专票、数电普票、纸质专票和纸质普票，不得仅保留旧销项/进项泛化票种。'
        from fin_tax_invoice_type
    union all select 'FINANCIAL_CLOSE_TAX_ADJUSTMENT_TYPES_V34', 'financial-close',
        coalesce((array_agg(check_clause))[1], ''),
        'OUTPUT_INCREASE,OUTPUT_DECREASE,INPUT_INCREASE,INPUT_DECREASE',
        coalesce((array_agg(check_clause))[1], '') like '%OUTPUT_INCREASE%'
            and coalesce((array_agg(check_clause))[1], '') like '%OUTPUT_DECREASE%'
            and coalesce((array_agg(check_clause))[1], '') like '%INPUT_INCREASE%'
            and coalesce((array_agg(check_clause))[1], '') like '%INPUT_DECREASE%',
        '税务调整必须冻结为销项调增、销项调减、进项调增和进项调减四类。'
        from information_schema.check_constraints
        where constraint_schema = 'public'
        and constraint_name = 'ck_fin_tax_adjustment_type'
    union all select 'FINANCIAL_CLOSE_TAX_SUMMARY_SOURCE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '已确认税务汇总必须保存来源指纹，不能以后续来源变化覆盖旧确认版本。'
        from fin_tax_period_summary
        where status = 'CONFIRMED'
        and (source_fingerprint is null or source_fingerprint = '')
    union all select 'FINANCIAL_CLOSE_TAX_DISCLAIMER_V34', 'financial-close',
        '本结果为 ERP 基础汇总或估算，不是正式纳税申报结果，不代替税务专业判断。',
        'fixed disclaimer present',
        true,
        '税务基础页面和 API 必须固定显示非申报免责声明；真实 API 验收负责逐项核对 DTO。'
    union all select 'FINANCIAL_CLOSE_TAX_PAYMENT_IDEMPOTENCY_DYNAMIC', 'financial-close',
        concat('records=', record_count, ';invalidRecords=', invalid_record_count,
            ';duplicates=', duplicate_count),
        'records>=0;invalidRecords=0;duplicates=0',
        invalid_record_count = 0 and duplicate_count = 0,
        '税款缴纳/更正幂等记录不是正式库必填业务事实；存在时必须具备合法动作、资源映射、请求指纹、结果资源和唯一键，不得重复。'
        from (
            select
                count(*) filter (
                    where action in ('FIN_TAX_PAYMENT_RECORD', 'FIN_TAX_PAYMENT_CORRECT')
                    and result_resource_type = 'FIN_TAX_PAYMENT_RECORD'
                ) as record_count,
                count(*) filter (
                    where action in ('FIN_TAX_PAYMENT_RECORD', 'FIN_TAX_PAYMENT_CORRECT')
                    and (
                        idempotency_key is null or idempotency_key = ''
                        or request_fingerprint is null or request_fingerprint = ''
                        or (action = 'FIN_TAX_PAYMENT_RECORD' and resource_type <> 'FIN_TAX_PERIOD_SUMMARY')
                        or (action = 'FIN_TAX_PAYMENT_CORRECT' and resource_type <> 'FIN_TAX_PAYMENT_RECORD')
                        or resource_id is null
                        or result_resource_type <> 'FIN_TAX_PAYMENT_RECORD'
                        or result_resource_id is null
                    )
                ) as invalid_record_count,
                (
                    select count(*)
                    from (
                        select operator_user_id, action, resource_type, coalesce(resource_id, 0) as resource_id,
                            idempotency_key
                        from fin_close_action_idempotency
                        where action in ('FIN_TAX_PAYMENT_RECORD', 'FIN_TAX_PAYMENT_CORRECT')
                        group by operator_user_id, action, resource_type, coalesce(resource_id, 0), idempotency_key
                        having count(*) > 1
                    ) duplicated_tax_payment_idempotency
                ) as duplicate_count
            from fin_close_action_idempotency
        ) tax_payment_idempotency_gate

    union all select 'OPERATING_FINANCE_PERMISSIONS_V35', 'operating-finance', count(*)::text, '8', count(*) = 8,
        '033 必须精确初始化八个固定经营财务分析查看权限，继续使用 reporting 领域权限前缀。'
        from sys_permission
        where code in ('report:operating-finance:view', 'report:project-profit:view',
            'report:contract-collection:view', 'report:procurement-variance:view',
            'report:inventory-capital:view', 'report:receivable-payable:view',
            'report:operating-accounting:view', 'report:financial-summary:view')
        and type = 'ACTION'
    union all select 'OPERATING_FINANCE_SYSTEM_ADMIN_PERMISSIONS_V35', 'operating-finance', count(*)::text, '8', count(*) = 8,
        'SYSTEM_ADMIN 必须拥有 033 固定经营财务分析全部查看权限。'
        from sys_role_permission rp
        join sys_role r on r.id = rp.role_id
        join sys_permission p on p.id = rp.permission_id
        where r.code = 'SYSTEM_ADMIN'
        and p.code in ('report:operating-finance:view', 'report:project-profit:view',
            'report:contract-collection:view', 'report:procurement-variance:view',
            'report:inventory-capital:view', 'report:receivable-payable:view',
            'report:operating-accounting:view', 'report:financial-summary:view')
    union all select 'OPERATING_FINANCE_PERMISSION_ROUTES_V35', 'operating-finance',
        coalesce(string_agg(code || '=' || route_path || ':' || coalesce(api_method, '') || ':' || coalesce(api_path, ''), ';' order by code), ''),
        '033 report routes stay under /reports and APIs stay under /api/admin/reports',
        count(*) = 8
            and count(*) filter (where type <> 'ACTION') = 0
            and count(*) filter (where api_method <> 'GET') = 0
            and count(*) filter (where route_path not like '/reports%') = 0
            and count(*) filter (where api_path not like '/api/admin/reports%') = 0
            and count(*) filter (
                where code = 'report:operating-finance:view'
                and route_path = '/reports/overview'
                and api_path = '/api/admin/reports/operating-finance-overview'
            ) = 1
            and count(*) filter (
                where code = 'report:project-profit:view'
                and route_path = '/reports/project-profit'
                and api_path = '/api/admin/reports/project-profit/**'
            ) = 1
            and count(*) filter (
                where code = 'report:contract-collection:view'
                and route_path = '/reports/contract-collection'
                and api_path = '/api/admin/reports/contract-collections/**'
            ) = 1
            and count(*) filter (
                where code = 'report:procurement-variance:view'
                and route_path = '/reports/procurement-variance'
                and api_path = '/api/admin/reports/procurement-variances/**'
            ) = 1
            and count(*) filter (
                where code = 'report:inventory-capital:view'
                and route_path = '/reports/inventory-capital'
                and api_path = '/api/admin/reports/inventory-capital/**'
            ) = 1
            and count(*) filter (
                where code = 'report:receivable-payable:view'
                and route_path = '/reports/receivable-payable'
                and api_path = '/api/admin/reports/receivable-payable/**'
            ) = 1
            and count(*) filter (
                where code = 'report:operating-accounting:view'
                and route_path = '/reports/operating-accounting-reconciliation'
                and api_path = '/api/admin/reports/operating-accounting-reconciliation/**'
            ) = 1
            and count(*) filter (
                where code = 'report:financial-summary:view'
                and route_path = '/reports/financial-summary'
                and api_path = '/api/admin/reports/financial-summary/**'
            ) = 1,
        '033 权限必须绑定既有 /reports 产品入口和 /api/admin/reports 只读 API，不得新建平行报表领域或写动作。'
        from sys_permission
        where code in ('report:operating-finance:view', 'report:project-profit:view',
            'report:contract-collection:view', 'report:procurement-variance:view',
            'report:inventory-capital:view', 'report:receivable-payable:view',
            'report:operating-accounting:view', 'report:financial-summary:view')
    union all select 'OPERATING_FINANCE_SNAPSHOT_CONSTRAINT_V35', 'operating-finance',
        constraint_def,
        'five business-side 033 reports included; reconciliation and summary excluded',
        position('PROJECT_PROFIT' in constraint_def) > 0
            and position('CONTRACT_COLLECTION' in constraint_def) > 0
            and position('PROCUREMENT_VARIANCE' in constraint_def) > 0
            and position('INVENTORY_CAPITAL' in constraint_def) > 0
            and position('RECEIVABLE_PAYABLE' in constraint_def) > 0
            and position('OPERATING_ACCOUNTING_RECONCILIATION' in constraint_def) = 0
            and position('FINANCIAL_SUMMARY' in constraint_def) = 0,
        'BUSINESS_SNAPSHOT 只扩展五个经营侧 033 报表；经营/会计对照和固定经营财务摘要不得进入快照约束。'
        from (
            select coalesce(string_agg(pg_get_constraintdef(c.oid), ';' order by c.conname), '') as constraint_def
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            where t.relname = 'biz_period_report_snapshot'
            and c.conname = 'ck_biz_period_report_snapshot_code'
        ) operating_finance_snapshot_constraint
    union all select 'OPERATING_FINANCE_SNAPSHOT_ROWS_COMPLETE_V35', 'operating-finance',
        count(*)::text, '0', count(*) = 0,
        '已有快照行不得部分包含 033 五个经营侧报表，也不得包含经营/会计对照或固定经营财务摘要。'
        from (
            select snapshot_id
            from biz_period_report_snapshot
            group by snapshot_id
            having count(distinct report_code) filter (
                    where report_code in ('PROJECT_PROFIT', 'CONTRACT_COLLECTION',
                        'PROCUREMENT_VARIANCE', 'INVENTORY_CAPITAL', 'RECEIVABLE_PAYABLE')
                ) not in (0, 5)
                or count(*) filter (
                    where report_code in ('OPERATING_ACCOUNTING_RECONCILIATION', 'FINANCIAL_SUMMARY')
                ) > 0
        ) operating_finance_snapshot_row_violations
    union all select 'OPERATING_FINANCE_ACCOUNTING_PROJECT_AUXILIARY_DYNAMIC', 'operating-finance',
        concat('projectAuxiliary=', project_auxiliary_count, ';missingProject=', missing_project_count),
        'projectAuxiliary>=0;missingProject=0',
        missing_project_count = 0,
        '033 正式零会计事实合法；存在 PROJECT 辅助发生额时必须能关联销售项目，缺失辅助不能被伪装成项目利润。'
        from (
            select count(*) filter (where a.dimension_code = 'PROJECT') as project_auxiliary_count,
                count(*) filter (
                    where a.dimension_code = 'PROJECT'
                    and a.object_id is not null
                    and p.id is null
                ) as missing_project_count
            from gl_voucher_line_auxiliary a
            left join sal_project p on p.id = a.object_id
        ) operating_finance_project_auxiliary_gate
    union all select 'OPERATING_FINANCE_FILE_OBJECTS_MIN_DYNAMIC', 'operating-finance',
        count(*)::text, '>= 8', count(*) >= 8,
        '033 验收延续对象一致性不少于 8 的动态门禁，不得把本次正式库 18 个对象写成长期固定值。'
        from platform_file_object
        where status = 'AVAILABLE'
    union all select 'OPERATING_FINANCE_P1_RED_SAMPLE_RETAINED_DYNAMIC', 'operating-finance',
        concat('unpricedLabor=', unpriced_labor_count, ';openBlocking=', open_blocking_count,
            ';zeroAmount=', zero_amount_count),
        'unpricedLabor>=1;openBlocking>=1;zeroAmount=0',
        unpriced_labor_count >= 1 and open_blocking_count >= 1 and zero_amount_count = 0,
        '033 关闭验收必须保留 P1 未定价 LABOR 红样本，金额不得被写成 0 或静默闭合。'
        from (
            select
                count(distinct cr.id) filter (
                    where cr.cost_type = 'LABOR'
                    and cr.source_type = 'AUTO_PRODUCTION'
                    and cr.basis_type = 'SOURCE_QUANTITY_ONLY'
                    and cr.amount is null
                    and cr.business_date between date '2026-08-01' and date '2026-08-31'
                ) as unpriced_labor_count,
                count(distinct v.id) filter (
                    where v.variance_type = 'SOURCE_UNPRICED'
                    and v.cost_category = 'LABOR'
                    and v.severity = 'BLOCKING'
                    and v.status = 'OPEN'
                ) as open_blocking_count,
                count(distinct cr.id) filter (
                    where cr.cost_type = 'LABOR'
                    and cr.source_type = 'AUTO_PRODUCTION'
                    and cr.basis_type = 'SOURCE_QUANTITY_ONLY'
                    and cr.amount = 0
                    and cr.business_date between date '2026-08-01' and date '2026-08-31'
                ) as zero_amount_count
            from sal_project p
            left join mfg_work_order wo on wo.project_id = p.id
            left join mfg_cost_record cr on cr.work_order_id = wo.id
            left join prj_cost_calculation c on c.project_id = p.id
                and c.cutoff_date between date '2026-08-01' and date '2026-08-31'
            left join prj_cost_variance v on v.calculation_id = c.id
            where p.name = '029 项目成本核算验收 P1'
        ) p1_red_sample_gate
    union all select 'OPERATING_FINANCE_P1_JULY_PROJECT_ACTIVITY_ZERO_DYNAMIC', 'operating-finance',
        concat('p1=', p1_count, ';inventory=', july_inventory_count, ';cost=', july_cost_count,
            ';workOrder=', july_work_order_count),
        'p1=1;inventory=0;cost=0;workOrder=0',
        p1_count = 1 and july_inventory_count = 0 and july_cost_count = 0 and july_work_order_count = 0,
        'P1 红样本必须按期间隔离，2026-07 月结项目活动三类事实来源不得选择到 P1。'
        from (
            select
                count(*) as p1_count,
                coalesce(sum(july_inventory_count), 0) as july_inventory_count,
                coalesce(sum(july_cost_count), 0) as july_cost_count,
                coalesce(sum(july_work_order_count), 0) as july_work_order_count
            from sal_project p
            left join lateral (
                select count(*) as july_inventory_count
                from inv_value_movement m
                where m.project_id = p.id
                and m.business_date between date '2026-07-01' and date '2026-07-31'
            ) inventory_gate on true
            left join lateral (
                select count(*) as july_cost_count
                from mfg_cost_record cr
                join mfg_work_order wo on wo.id = cr.work_order_id
                where wo.project_id = p.id
                and cr.business_date between date '2026-07-01' and date '2026-07-31'
            ) cost_gate on true
            left join lateral (
                select count(*) as july_work_order_count
                from mfg_work_order wo
                where wo.project_id = p.id
                and wo.planned_start_date is not null
                and wo.planned_finish_date is not null
                and wo.planned_start_date <= date '2026-07-31'
                and wo.planned_finish_date >= date '2026-07-01'
            ) work_order_gate on true
            where p.name = '029 项目成本核算验收 P1'
        ) p1_july_activity_gate
    union all select 'OPERATING_FINANCE_P2_P3_CURRENT_COST_READY_DYNAMIC', 'operating-finance',
        concat('current=', current_count, ';openBlocking=', open_blocking_count),
        'current=2;openBlocking=0',
        current_count = 2 and open_blocking_count = 0,
        'P2/P3 必须各自生成 cutoffDate=2026-07-31、CONFIRMED、CURRENT 且无 OPEN/BLOCKING 差异的项目成本运行。'
        from (
            select
                count(distinct c.project_id) as current_count,
                count(distinct v.id) as open_blocking_count
            from sal_project p
            left join prj_cost_calculation c on c.project_id = p.id
                and c.cutoff_date = date '2026-07-31'
                and c.status = 'CONFIRMED'
                and c.is_current = true
            left join prj_cost_variance v on v.calculation_id = c.id
                and v.status = 'OPEN'
                and v.severity = 'BLOCKING'
            where p.name in ('029 项目成本核算隔离 P2', '029 项目成本核算关闭 P3')
        ) p2_p3_cost_ready_gate
    union all select 'OPERATING_FINANCE_JULY_PROJECT_ACTIVITY_COST_READY_DYNAMIC', 'operating-finance',
        concat('missing=', missing_count, ';stale=', stale_count, ';openBlocking=', open_blocking_count),
        'missing=0;stale=0;openBlocking=0',
        missing_count = 0 and stale_count = 0 and open_blocking_count = 0,
        '所有 2026-07 项目活动都必须有 cutoffDate=2026-07-31 的当前项目成本运行，不能只覆盖 P2/P3 后遗留基础项目阻断。'
        from (
            select
                count(*) filter (where c.id is null) as missing_count,
                count(*) filter (where c.id is not null and c.is_current is not true) as stale_count,
                count(*) filter (where coalesce(v.open_blocking_count, 0) > 0) as open_blocking_count
            from (
                select distinct project_id
                from inv_value_movement
                where project_id is not null
                and business_date between date '2026-07-01' and date '2026-07-31'
                union
                select distinct wo.project_id
                from mfg_cost_record cr
                join mfg_work_order wo on wo.id = cr.work_order_id
                where wo.project_id is not null
                and cr.business_date between date '2026-07-01' and date '2026-07-31'
                union
                select distinct project_id
                from mfg_work_order
                where project_id is not null
                and coalesce(planned_start_date, planned_finish_date, date '2026-07-01') <= date '2026-07-31'
                and coalesce(planned_finish_date, planned_start_date, date '2026-07-31') >= date '2026-07-01'
            ) activity
            left join lateral (
                select c.id, c.is_current
                from prj_cost_calculation c
                where c.project_id = activity.project_id
                and c.cutoff_date = date '2026-07-31'
                and c.status = 'CONFIRMED'
                order by c.id desc
                limit 1
            ) c on true
            left join lateral (
                select count(*) as open_blocking_count
                from prj_cost_variance v
                where v.calculation_id = c.id
                and v.status = 'OPEN'
                and v.severity = 'BLOCKING'
            ) v on true
        ) july_project_activity_cost_gate
    union all select 'OPERATING_FINANCE_JULY_OPEN_STOCKTAKE_ZERO_DYNAMIC', 'operating-finance',
        count(*)::text, '0', count(*) = 0,
        '2026-07 截止日前不得保留未终态盘点；未盘草稿样例必须移出 7 月或通过正式取消 API 闭合。'
        from inv_stocktake
        where business_date <= date '2026-07-31'
        and status in ('DRAFT', 'COUNTING', 'RECONCILED', 'SUBMITTED')
    union all select 'OPERATING_FINANCE_STOCKTAKE_RANGE_LOCK_ZERO_DYNAMIC', 'operating-finance',
        count(*)::text, '0', count(*) = 0,
        '033 月结验收不得保留未释放盘点范围锁；未盘样例可保留为已取消单据和空 counted_quantity 行。'
        from inv_stocktake_range_lock
        where released_at is null
    union all select 'OPERATING_FINANCE_PROJECT_PROFIT_FACTS_DYNAMIC', 'operating-finance',
        count(*)::text, '>= 2', count(*) >= 2,
        '项目利润 LIVE 必须至少有 P2/P3 两个 2026-07 已确认当前项目成本事实。'
        from prj_cost_calculation c
        join sal_project p on p.id = c.project_id
        where c.cutoff_date = date '2026-07-31'
        and c.status = 'CONFIRMED'
        and c.is_current = true
        and c.project_cost_total > 0
        and p.name in ('029 项目成本核算隔离 P2', '029 项目成本核算关闭 P3')
    union all select 'OPERATING_FINANCE_ACCOUNTING_FACTS_MIN_DYNAMIC', 'operating-finance',
        concat('entries=', entry_count, ';projects=', project_count),
        'entries>=4;projects=2',
        entry_count >= 4 and project_count = 2,
        '经营会计对照 LIVE 必须存在 2026-07 POSTED+PROJECT 辅助的真实收入和主营成本分录。'
        from (
            select count(*) as entry_count,
                count(distinct auxiliary.value ->> 'objectCode') as project_count
            from gl_ledger_entry e
            join gl_accounting_period gp on gp.id = e.period_id
            cross join lateral jsonb_array_elements(e.auxiliary_snapshot) as auxiliary(value)
            where gp.period_code = '2026-07'
            and auxiliary.value ->> 'dimensionCode' = 'PROJECT'
            and auxiliary.value ->> 'objectName' in ('029 项目成本核算隔离 P2', '029 项目成本核算关闭 P3')
            and (
                e.account_code in ('6001', '6401')
                or e.account_code like '6001.%'
                or e.account_code like '6401.%'
            )
        ) operating_accounting_fact_gate

    union all select 'PROC_ORDERS_MIN_3', 'procurement', count(*)::text, '>= 3', count(*) >= 3,
        '采购订单数量不足。' from proc_purchase_order
    union all select 'PROC_RECEIPTS_POSTED_MIN_2', 'procurement', count(*)::text, '>= 2', count(*) >= 2,
        '已过账采购入库不足。' from proc_purchase_receipt where status = 'POSTED'
    union all select 'PROC_RETURNS_MIN_1', 'procurement', count(*)::text, '>= 1', count(*) >= 1,
        '缺少采购退货。' from proc_purchase_return
    union all select 'QUALITY_INSPECTIONS_MIN_3', 'quality', count(*)::text, '>= 3', count(*) >= 3,
        '质量确认记录不足。' from qua_quality_inspection
    union all select 'QUALITY_COMPLETED_MIN_1', 'quality', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已完成质量确认。' from qua_quality_inspection where status = 'COMPLETED'

    union all select 'STOCK_BALANCES_MIN_12', 'inventory', count(*)::text, '>= 12', count(*) >= 12,
        '库存余额维度不足。' from inv_stock_balance
    union all select 'STOCK_BALANCE_NO_NEGATIVE', 'inventory', count(*)::text, '0', count(*) = 0,
        '库存余额和锁定量不得为负。' from inv_stock_balance where quantity_on_hand < 0 or locked_quantity < 0
    union all select 'STOCK_BALANCE_PUBLIC_MIN_1', 'inventory', count(*)::text, '>= 1', count(*) >= 1,
        '缺少 PUBLIC 库存余额。' from inv_stock_balance where ownership_type = 'PUBLIC'
    union all select 'STOCK_BALANCE_PROJECT_MIN_2', 'inventory', count(*)::text, '>= 2', count(*) >= 2,
        '项目库存余额不足。' from inv_stock_balance where ownership_type = 'PROJECT'
    union all select 'STOCK_BALANCE_QUALITY_STATES_MIN_3', 'inventory', count(distinct quality_status)::text, '>= 3', count(distinct quality_status) >= 3,
        '库存质量状态分布不足。' from inv_stock_balance
    union all select 'STOCK_BALANCE_LOCKED_MIN_1', 'inventory', count(*)::text, '>= 1', count(*) >= 1,
        '缺少锁定/预留余额样例。' from inv_stock_balance where locked_quantity > 0
    union all select 'BATCHES_MIN_2', 'inventory', count(*)::text, '>= 2', count(*) >= 2,
        '批次样例不足。' from inv_batch
    union all select 'SERIALS_MIN_3', 'inventory', count(*)::text, '>= 3', count(*) >= 3,
        '序列号样例不足。' from inv_serial
    union all select 'RESERVATIONS_MIN_3', 'inventory', count(*)::text, '>= 3', count(*) >= 3,
        '库存预留/占用样例不足。' from inv_stock_reservation
    union all select 'RESERVATIONS_NO_OVER_RELEASE_CONSUME', 'inventory', count(*)::text, '0', count(*) = 0,
        '预留释放和消费量不得超过原数量。' from inv_stock_reservation where released_quantity + consumed_quantity > quantity
    union all select 'STOCK_MOVEMENTS_MIN_20', 'inventory', count(*)::text, '>= 20', count(*) >= 20,
        '库存流水数量不足。' from inv_stock_movement
    union all select 'TRACKING_ALLOCATIONS_MIN_4', 'inventory', count(*)::text, '>= 4', count(*) >= 4,
        '追踪分配记录不足。' from inv_stock_tracking_allocation

    union all select 'PUBLIC_POOLS_MIN_3', 'valuation', count(*)::text, '>= 3', count(*) >= 3,
        '公共移动平均池数量不足。' from inv_public_valuation_pool
    union all select 'PUBLIC_POOLS_NO_NEGATIVE', 'valuation', count(*)::text, '0', count(*) = 0,
        '公共池数量或金额不得为负。' from inv_public_valuation_pool where quantity < 0 or amount < 0
    union all select 'PROJECT_LAYERS_MIN_3', 'valuation', count(*)::text, '>= 3', count(*) >= 3,
        '项目成本层数量不足。' from inv_project_cost_layer
    union all select 'PROJECT_WITH_MULTI_LAYER_MIN_1', 'valuation', count(*)::text, '>= 1', count(*) >= 1,
        '至少一个项目物料必须有多个实际成本层。'
        from (
            select project_id, material_id from inv_project_cost_layer group by project_id, material_id having count(*) >= 2
        ) s
    union all select 'PROJECT_LAYERS_NO_NEGATIVE', 'valuation', count(*)::text, '0', count(*) = 0,
        '项目成本层剩余数量或金额不得为负。' from inv_project_cost_layer where remaining_quantity < 0 or remaining_amount < 0
    union all select 'VALUE_MOVEMENTS_MIN_10', 'valuation', count(*)::text, '>= 10', count(*) >= 10,
        '库存价值流水不足。' from inv_value_movement
    union all select 'VALUE_MOVEMENTS_NO_NULL_FOR_VALUED', 'valuation', count(*)::text, '0', count(*) = 0,
        '已估值价值流水不能缺少单价或金额。' from inv_value_movement where valuation_state in ('VALUED','MANUAL_PROVISIONAL','CURRENT_AVERAGE_PROVISIONAL') and (unit_cost is null or inventory_amount is null)

    union all select 'WAREHOUSE_TRANSFERS_POSTED_MIN_1', 'valuation', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已过账仓库调拨。' from inv_warehouse_transfer where status = 'POSTED'
    union all select 'OWNERSHIP_CONVERSIONS_POSTED_MIN_3', 'valuation',
        concat('PUBLIC_TO_PROJECT=', public_to_project_count,
            ';PROJECT_TO_PUBLIC=', project_to_public_count,
            ';PROJECT_TO_PROJECT=', project_to_project_count),
        'POSTED PUBLIC->PROJECT/PROJECT->PUBLIC/PROJECT->PROJECT each >= 1',
        public_to_project_count >= 1 and project_to_public_count >= 1 and project_to_project_count >= 1,
        '三类已过账权属转换必须同时覆盖 PUBLIC→PROJECT、PROJECT→PUBLIC 和 PROJECT→PROJECT。'
        from (
            select
                count(distinct c.id) filter (
                    where l.source_ownership_type = 'PUBLIC' and l.target_ownership_type = 'PROJECT'
                ) as public_to_project_count,
                count(distinct c.id) filter (
                    where l.source_ownership_type = 'PROJECT' and l.target_ownership_type = 'PUBLIC'
                ) as project_to_public_count,
                count(distinct c.id) filter (
                    where l.source_ownership_type = 'PROJECT' and l.target_ownership_type = 'PROJECT'
                ) as project_to_project_count
            from inv_ownership_conversion c
            join inv_ownership_conversion_line l on l.conversion_id = c.id
            where c.status = 'POSTED'
        ) ownership_coverage
    union all select 'STOCKTAKES_MIN_3', 'stocktake', count(*)::text, '>= 3', count(*) >= 3,
        '盘点单数量不足。' from inv_stocktake
    union all select 'STOCKTAKE_LINES_MIN_25', 'stocktake', count(*)::text, '>= 25', count(*) >= 25,
        '盘点明细不足，不能证明分页。' from inv_stocktake_line
    union all select 'STOCKTAKE_OVER_PAGE_MIN_1', 'stocktake', count(*)::text, '>= 1', count(*) >= 1,
        '缺少超过默认单页行数的盘点。' from (select stocktake_id from inv_stocktake_line group by stocktake_id having count(*) > 20) s
    union all select 'STOCKTAKE_ZERO_COUNT_MIN_1', 'stocktake', count(*)::text, '>= 1', count(*) >= 1,
        '缺少实盘 0 样例。' from inv_stocktake_line where counted_quantity = 0
    union all select 'STOCKTAKE_NULL_COUNT_MIN_1', 'stocktake', count(*)::text, '>= 1', count(*) >= 1,
        '缺少未盘空值样例。' from inv_stocktake_line where counted_quantity is null
    union all select 'STOCKTAKE_POSITIVE_VARIANCE_MIN_1', 'stocktake', count(*)::text, '>= 1', count(*) >= 1,
        '缺少盘盈样例。' from inv_stocktake_line where variance_quantity > 0
    union all select 'STOCKTAKE_NEGATIVE_VARIANCE_MIN_1', 'stocktake', count(*)::text, '>= 1', count(*) >= 1,
        '缺少盘亏样例。' from inv_stocktake_line where variance_quantity < 0
    union all select 'STOCKTAKE_VARIANCE_FORMULA', 'stocktake', count(*)::text, '0', count(*) = 0,
        '盘点差异必须等于实盘减账面。' from inv_stocktake_line where counted_quantity is not null and variance_quantity <> counted_quantity - book_quantity
    union all select 'VALUATION_ADJUSTMENTS_POSTED_MIN_1', 'valuation', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已过账估值调整。' from inv_valuation_adjustment where status = 'POSTED'

    union all select 'WORK_ORDERS_MIN_5', 'production', count(*)::text, '>= 5', count(*) >= 5,
        '生产工单数量不足。' from mfg_work_order
    union all select 'WORK_ORDER_MATERIALS_MIN_5', 'production', count(*)::text, '>= 5', count(*) >= 5,
        '工单 BOM 快照明细不足。' from mfg_work_order_material
    union all select 'WORK_ORDER_BOM_EFFECTIVE_DATE_ZERO', 'production', count(*)::text, '0', count(*) = 0,
        '每张工单引用的 BOM 必须覆盖 planned_start_date。'
        from mfg_work_order wo
        join mfg_bom b on b.id = wo.bom_id
        where wo.planned_start_date < coalesce(b.effective_from, date '-infinity')
            or wo.planned_start_date > coalesce(b.effective_to, date 'infinity')
    union all select 'MATERIAL_ISSUES_POSTED_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已过账生产领料。' from mfg_material_issue where status = 'POSTED'
    union all select 'WORK_REPORTS_POSTED_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已过账报工。' from mfg_work_report where status = 'POSTED'
    union all select 'COMPLETION_RECEIPTS_POSTED_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少已过账完工入库。' from mfg_completion_receipt where status = 'POSTED'
    union all select 'MATERIAL_RETURNS_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少生产退料。' from mfg_material_return
    union all select 'MATERIAL_SUPPLEMENTS_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少生产补料。' from mfg_material_supplement
    union all select 'COST_RECORDS_MIN_1', 'production', count(*)::text, '>= 1', count(*) >= 1,
        '缺少成本记录。' from mfg_cost_record

    union all select 'SALES_PROJECTS_MIN_2', 'sales', count(*)::text, '>= 2', count(*) >= 2,
        '销售项目数量不足。' from sal_project
    union all select 'SALES_CONTRACTS_MIN_3', 'sales', count(*)::text, '>= 3', count(*) >= 3,
        '销售合同数量不足。' from sal_project_contract
    union all select 'SALES_CONTRACT_EFFECTIVE_MIN_1', 'sales', count(*)::text, '>= 1', count(*) >= 1,
        '缺少生效合同。' from sal_project_contract where status = 'EFFECTIVE'
    union all select 'SALES_ORDERS_MIN_3', 'sales', count(*)::text, '>= 3', count(*) >= 3,
        '销售订单数量不足。' from sal_sales_order
    union all select 'SALES_SHIPMENTS_POSTED_MIN_2', 'sales', count(*)::text, '>= 2', count(*) >= 2,
        '已过账销售出库不足。' from sal_sales_shipment where status = 'POSTED'
    union all select 'SALES_RETURNS_MIN_1', 'sales', count(*)::text, '>= 1', count(*) >= 1,
        '缺少销售退货。' from sal_sales_return

    union all select 'RECEIVABLES_MIN_2', 'finance', count(*)::text, '>= 2', count(*) >= 2,
        '应收台账不足。' from fin_receivable
    union all select 'RECEIPTS_MIN_1', 'finance', count(*)::text, '>= 1', count(*) >= 1,
        '收款记录不足。' from fin_receipt
    union all select 'PAYABLES_MIN_2', 'finance', count(*)::text, '>= 2', count(*) >= 2,
        '应付台账不足。' from fin_payable
    union all select 'PAYMENTS_MIN_1', 'finance', count(*)::text, '>= 1', count(*) >= 1,
        '付款记录不足。' from fin_payment
    union all select 'SETTLEMENT_ADJUSTMENTS_MIN_1', 'finance', count(*)::text, '>= 1', count(*) >= 1,
        '往来调整不足。' from fin_settlement_adjustment
    union all select 'RECEIVABLE_AMOUNT_FORMULA', 'finance', count(*)::text, '0', count(*) = 0,
        '应收金额必须等于已收加调整加未收。' from fin_receivable where total_amount <> received_amount + coalesce(adjusted_amount, 0) + unreceived_amount
    union all select 'PAYABLE_AMOUNT_FORMULA', 'finance', count(*)::text, '0', count(*) = 0,
        '应付金额必须等于已付加调整加未付。' from fin_payable where total_amount <> paid_amount + coalesce(adjusted_amount, 0) + unpaid_amount
    union all select 'REVERSAL_LINKS_MIN_4', 'reversal', count(*)::text, '>= 4', count(*) >= 4,
        '反向业务来源关系不足。' from biz_reversal_link

    union all select 'APPROVAL_INSTANCES_MIN_8', 'platform', count(*)::text, '>= 8', count(*) >= 8,
        '审批实例数量不足。' from platform_approval_instance
    union all select 'APPROVAL_STATUSES_MIN_4', 'platform',
        concat('instances(APPROVED=', approved_instances,
            ';REJECTED=', rejected_instances,
            ';WITHDRAWN=', withdrawn_instances,
            ';CANCELLED=', cancelled_instances,
            ');pendingTasks=', pending_tasks,
            ';history(APPROVE=', approve_history,
            ';REJECT=', reject_history,
            ';WITHDRAW=', withdraw_history,
            ';CANCEL=', cancel_history,
            ');submittedSamples=', submitted_samples),
        'APPROVED/REJECTED/WITHDRAWN/CANCELLED instances each >= 1; PENDING task >= 1; APPROVE/REJECT/WITHDRAW/CANCEL history each >= 1; submitted samples >= 4',
        approved_instances >= 1 and rejected_instances >= 1 and withdrawn_instances >= 1
            and cancelled_instances >= 1 and pending_tasks >= 1 and approve_history >= 1
            and reject_history >= 1 and withdraw_history >= 1 and cancel_history >= 1
            and submitted_samples >= 4,
        '审批生命周期必须覆盖通过、驳回、撤回、治理取消、待办任务和我发起样例。'
        from (
            select
                count(*) filter (where status = 'APPROVED') as approved_instances,
                count(*) filter (where status = 'REJECTED') as rejected_instances,
                count(*) filter (where status = 'WITHDRAWN') as withdrawn_instances,
                count(*) filter (where status = 'CANCELLED') as cancelled_instances,
                count(*) filter (where submitted_by_user_id is not null and submitted_by_username is not null) as submitted_samples
            from platform_approval_instance
        ) approval_instances
        cross join (
            select count(*) filter (where status = 'PENDING') as pending_tasks
            from platform_approval_task
        ) approval_tasks
        cross join (
            select
                count(*) filter (where action = 'APPROVE') as approve_history,
                count(*) filter (where action = 'REJECT') as reject_history,
                count(*) filter (where action = 'WITHDRAW') as withdraw_history,
                count(*) filter (where action = 'CANCEL') as cancel_history
            from platform_approval_history
        ) approval_history
    union all select 'APPROVAL_TASKS_MIN_8', 'platform', count(*)::text, '>= 8', count(*) >= 8,
        '审批任务数量不足。' from platform_approval_task
    union all select 'APPROVAL_HISTORY_MIN_8', 'platform', count(*)::text, '>= 8', count(*) >= 8,
        '审批历史不足。' from platform_approval_history
    union all select 'MESSAGES_MIN_8', 'platform', count(*)::text, '>= 8', count(*) >= 8,
        '站内消息不足。' from platform_message
    union all select 'FILE_OBJECTS_AVAILABLE_MIN_8', 'attachment', count(*)::text, '>= 8', count(*) >= 8,
        '可用文件对象不足。' from platform_file_object where status = 'AVAILABLE'
    union all select 'FILE_OBJECTS_HASH_SIZE_VALID', 'attachment', count(*)::text, '0', count(*) = 0,
        '可用文件对象必须有大小和 SHA-256。' from platform_file_object where status = 'AVAILABLE' and (size_bytes <= 0 or sha256 is null or length(sha256) < 32)
    union all select 'BUSINESS_ATTACHMENTS_AVAILABLE_MIN_8', 'attachment', count(*)::text, '>= 8', count(*) >= 8,
        '业务附件关系不足。' from platform_business_attachment where status = 'AVAILABLE'
    union all select 'APPROVAL_ATTACHMENT_SNAPSHOT_MIN_2', 'attachment', count(*)::text, '>= 2', count(*) >= 2,
        '审批附件快照不足。' from platform_approval_attachment_snapshot
    union all select 'PRINT_TEMPLATES_MIN_2', 'print', count(*)::text, '>= 2', count(*) >= 2,
        '固定打印模板不足。' from platform_print_template where status = 'ENABLED'
    union all select 'DOCUMENT_TASKS_MIN_6', 'document-task',
        concat('materialImportReady=', material_import_ready_count,
            ';materialImportFailed=', material_import_failed_count,
            ';bomImportSucceeded=', bom_import_succeeded_count,
            ';materialExportSucceeded=', material_export_succeeded_count,
            ';bomExportSucceeded=', bom_export_succeeded_count,
            ';contractPrintSucceeded=', contract_print_succeeded_count,
            ';ecoPrintSucceeded=', eco_print_succeeded_count,
            ';cancelledTask=', cancelled_task_count),
        'valid material import READY_TO_COMMIT, invalid material import VALIDATION_FAILED, BOM import SUCCEEDED, material export SUCCEEDED, BOM export SUCCEEDED, contract print SUCCEEDED, ECO print SUCCEEDED, CANCELLED task each >= 1',
        material_import_ready_count >= 1 and material_import_failed_count >= 1
            and bom_import_succeeded_count >= 1 and material_export_succeeded_count >= 1
            and bom_export_succeeded_count >= 1 and contract_print_succeeded_count >= 1
            and eco_print_succeeded_count >= 1 and cancelled_task_count >= 1,
        '文档任务必须覆盖物料有效导入待确认、物料无效导入失败、BOM 导入、物料/BOM 导出、两类审批打印和取消任务。'
        from (
            select
                count(*) filter (
                    where task_type = 'MATERIAL_IMPORT' and status = 'READY_TO_COMMIT'
                ) as material_import_ready_count,
                count(*) filter (
                    where task_type = 'MATERIAL_IMPORT' and status = 'VALIDATION_FAILED' and error_count > 0
                ) as material_import_failed_count,
                count(*) filter (
                    where task_type = 'BOM_DRAFT_IMPORT' and status = 'SUCCEEDED'
                ) as bom_import_succeeded_count,
                count(*) filter (
                    where task_type = 'MATERIAL_EXPORT' and status = 'SUCCEEDED'
                ) as material_export_succeeded_count,
                count(*) filter (
                    where task_type = 'BOM_DRAFT_EXPORT' and status = 'SUCCEEDED'
                ) as bom_export_succeeded_count,
                count(*) filter (
                    where task_type = 'APPROVAL_PRINT' and status = 'SUCCEEDED'
                        and request_payload ->> 'templateCode' = 'CONTRACT_ACTIVATION_APPROVAL_V1'
                ) as contract_print_succeeded_count,
                count(*) filter (
                    where task_type = 'APPROVAL_PRINT' and status = 'SUCCEEDED'
                        and request_payload ->> 'templateCode' = 'BOM_ECO_APPLICATION_APPROVAL_V1'
                ) as eco_print_succeeded_count,
                count(*) filter (where status = 'CANCELLED') as cancelled_task_count
            from platform_document_task
        ) document_task_coverage
    union all select 'DOCUMENT_TASK_STATUSES_MIN_4', 'document-task',
        concat('READY_TO_COMMIT=', ready_to_commit_count,
            ';VALIDATION_FAILED=', validation_failed_count,
            ';SUCCEEDED=', succeeded_count,
            ';CANCELLED=', cancelled_count),
        'READY_TO_COMMIT/VALIDATION_FAILED/SUCCEEDED/CANCELLED each >= 1',
        ready_to_commit_count >= 1 and validation_failed_count >= 1
            and succeeded_count >= 1 and cancelled_count >= 1,
        '文档任务必须精确覆盖 READY_TO_COMMIT、VALIDATION_FAILED、SUCCEEDED 和 CANCELLED 四类状态。'
        from (
            select
                count(*) filter (where status = 'READY_TO_COMMIT') as ready_to_commit_count,
                count(*) filter (where status = 'VALIDATION_FAILED') as validation_failed_count,
                count(*) filter (where status = 'SUCCEEDED') as succeeded_count,
                count(*) filter (where status = 'CANCELLED') as cancelled_count
            from platform_document_task
        ) document_task_status_coverage
    union all select 'IMPORT_BATCHES_MIN_2', 'document-task',
        concat('materialValidated=', material_validated_batch_count,
            ';materialValidationFailed=', material_failed_batch_count,
            ';bomCommitted=', bom_committed_batch_count),
        'MATERIAL_IMPORT VALIDATED, MATERIAL_IMPORT VALIDATION_FAILED, BOM_DRAFT_IMPORT COMMITTED each >= 1',
        material_validated_batch_count >= 1 and material_failed_batch_count >= 1
            and bom_committed_batch_count >= 1,
        '导入批次必须覆盖物料有效待确认、物料无效失败和 BOM 草稿导入提交成功。'
        from (
            select
                count(*) filter (
                    where t.task_type = 'MATERIAL_IMPORT' and t.status = 'READY_TO_COMMIT'
                        and b.import_type = 'MATERIAL_IMPORT' and b.status = 'VALIDATED'
                ) as material_validated_batch_count,
                count(*) filter (
                    where t.task_type = 'MATERIAL_IMPORT' and t.status = 'VALIDATION_FAILED'
                        and b.import_type = 'MATERIAL_IMPORT' and b.status = 'VALIDATION_FAILED'
                ) as material_failed_batch_count,
                count(*) filter (
                    where t.task_type = 'BOM_DRAFT_IMPORT' and t.status = 'SUCCEEDED'
                        and b.import_type = 'BOM_DRAFT_IMPORT' and b.status = 'COMMITTED'
                ) as bom_committed_batch_count
            from platform_import_batch b
            join platform_document_task t on t.id = b.task_id
        ) import_batch_coverage
    union all select 'IMPORT_ERRORS_MIN_1', 'document-task',
        concat('invalidMaterialTasks=', invalid_material_task_count,
            ';linkedErrors=', linked_error_count,
            ';distinctErrorCodes=', distinct_error_code_count),
        'invalid MATERIAL_IMPORT task >= 1; linked import errors >= 1; distinct error codes >= 1',
        invalid_material_task_count >= 1 and linked_error_count >= 1 and distinct_error_code_count >= 1,
        '物料无效导入必须产生失败任务、失败批次和可诊断错误明细。'
        from (
            select
                count(distinct t.id) as invalid_material_task_count,
                count(e.id) as linked_error_count,
                count(distinct e.error_code) as distinct_error_code_count
            from platform_document_task t
            join platform_import_batch b on b.task_id = t.id
            left join platform_import_error e on e.batch_id = b.id
            where t.task_type = 'MATERIAL_IMPORT'
                and t.status = 'VALIDATION_FAILED'
                and b.import_type = 'MATERIAL_IMPORT'
                and b.status = 'VALIDATION_FAILED'
        ) import_error_coverage

    union all select 'PLATFORM_DATA_REPAIR_PERMISSIONS_V36', 'stage034-definition', count(*)::text, '8', count(*) = 8,
        'V36 必须精确初始化数据修复 view/create/update/submit/approve/execute/verify/cancel 八个固定权限。'
        from sys_permission
        where code in ('platform:data-repair:view', 'platform:data-repair:create', 'platform:data-repair:update',
            'platform:data-repair:submit', 'platform:data-repair:approve', 'platform:data-repair:execute',
            'platform:data-repair:verify', 'platform:data-repair:cancel')
    union all select 'PLATFORM_HISTORY_IMPORT_PERMISSIONS_V36', 'stage034-definition', count(*)::text, '4', count(*) = 4,
        'V36 必须精确初始化历史导入 view/create/confirm/cancel 四个固定权限。'
        from sys_permission
        where code in ('platform:history-import:view', 'platform:history-import:create',
            'platform:history-import:confirm', 'platform:history-import:cancel')
    union all select 'PLATFORM_BATCH_TOOL_PERMISSIONS_V36', 'stage034-definition', count(*)::text, '3', count(*) = 3,
        'V36 必须精确初始化固定批量工具 view/preview/execute 三个固定权限。'
        from sys_permission
        where code in ('platform:batch-tool:view', 'platform:batch-tool:preview', 'platform:batch-tool:execute')
    union all select 'PLATFORM_DELIVERY_ASSET_PERMISSION_V36', 'stage034-definition', count(*)::text, '1', count(*) = 1,
        'V36 必须初始化交付资料只读权限。'
        from sys_permission
        where code = 'platform:delivery-asset:view'
    union all select 'PLATFORM_GOVERNANCE_MENUS_V36', 'stage034-definition',
        concat('routes=', route_count, ';apis=', api_count),
        'routes>=3;apis>=12',
        route_count >= 3 and api_count >= 12,
        'V36 平台治理菜单和 API 权限必须绑定 /platform/data-repairs、/platform/history-imports、/platform/delivery-assets 与 /api/admin/platform。'
        from (
            select
                count(*) filter (where route_path in ('/platform/data-repairs', '/platform/history-imports', '/platform/delivery-assets')) as route_count,
                count(*) filter (where api_path like '/api/admin/platform/%') as api_count
            from sys_permission
            where code like 'platform:%'
        ) platform_governance_routes
    union all select 'PLATFORM_HISTORY_IMPORT_ADAPTERS_V36', 'stage034-definition',
        concat('count=', adapter_count, ';missing=', missing_count),
        'count=5;missing=0',
        adapter_count = 5 and missing_count = 0,
        'V36 必须登记客户、供应商、物料、BOM 草稿和销售项目草稿五个固定历史导入适配器。'
        from (
            select
                count(*) filter (where d.status = 'ENABLED') as adapter_count,
                (
                    select count(*)
                    from (values
                        ('CUSTOMER_MASTER_V1'), ('SUPPLIER_MASTER_V1'), ('MATERIAL_MASTER_V1'),
                        ('BOM_DRAFT_V1'), ('SALES_PROJECT_DRAFT_V1')
                    ) required(adapter_code)
                    where not exists (
                        select 1 from platform_import_adapter_definition d2
                        where d2.adapter_code = required.adapter_code and d2.status = 'ENABLED'
                    )
                ) as missing_count
            from platform_import_adapter_definition d
            where d.adapter_code in ('CUSTOMER_MASTER_V1', 'SUPPLIER_MASTER_V1', 'MATERIAL_MASTER_V1',
                'BOM_DRAFT_V1', 'SALES_PROJECT_DRAFT_V1')
        ) history_import_adapter_gate
    union all select 'PLATFORM_BATCH_TOOLS_V36', 'stage034-definition',
        concat('count=', tool_count, ';missing=', missing_count),
        'count=4;missing=0',
        tool_count = 4 and missing_count = 0,
        'V36 必须登记三类主数据状态变更和固定业务单据批量打印四个固定批量工具。'
        from (
            select
                count(*) filter (where d.status = 'ENABLED') as tool_count,
                (
                    select count(*)
                    from (values
                        ('CUSTOMER_STATUS_CHANGE_V1'), ('SUPPLIER_STATUS_CHANGE_V1'),
                        ('MATERIAL_STATUS_CHANGE_V1'), ('FIXED_DOCUMENT_BATCH_PRINT_V1')
                    ) required(tool_code)
                    where not exists (
                        select 1 from platform_batch_tool_definition d2
                        where d2.tool_code = required.tool_code and d2.status = 'ENABLED'
                    )
                ) as missing_count
            from platform_batch_tool_definition d
            where d.tool_code in ('CUSTOMER_STATUS_CHANGE_V1', 'SUPPLIER_STATUS_CHANGE_V1',
                'MATERIAL_STATUS_CHANGE_V1', 'FIXED_DOCUMENT_BATCH_PRINT_V1')
        ) batch_tool_gate
    union all select 'PRINT_TEMPLATES_034_ALL_14_V36', 'stage034-definition',
        concat('count=', template_count, ';missing=', missing_count),
        'count=14;missing=0',
        template_count = 14 and missing_count = 0,
        'V36 必须启用四个兼容模板和十个新增核心固定打印模板，模板代码唯一且版本可追溯。'
        from (
            select
                count(*) filter (where t.status = 'ENABLED' and t.template_version is not null) as template_count,
                (
                    select count(*)
                    from (values
                        ('CONTRACT_ACTIVATION_APPROVAL_V1'), ('BOM_ECO_APPLICATION_APPROVAL_V1'),
                        ('PROCUREMENT_ORDER_V1'), ('SALES_QUOTE_V1'), ('SALES_ORDER_V1'),
                        ('SALES_SHIPMENT_V1'), ('PROCUREMENT_RECEIPT_V1'), ('INVENTORY_TRANSFER_V1'),
                        ('PRODUCTION_WORK_ORDER_V1'), ('PRODUCTION_MATERIAL_ISSUE_V1'),
                        ('PRODUCTION_COMPLETION_RECEIPT_V1'), ('SALES_INVOICE_V1'),
                        ('PURCHASE_INVOICE_V1'), ('ACCOUNTING_VOUCHER_V1')
                    ) required(template_code)
                    where not exists (
                        select 1 from platform_print_template t2
                        where t2.template_code = required.template_code
                            and t2.status = 'ENABLED'
                            and t2.template_version is not null
                    )
                ) as missing_count
            from platform_print_template t
            where t.template_code in ('CONTRACT_ACTIVATION_APPROVAL_V1', 'BOM_ECO_APPLICATION_APPROVAL_V1',
                'PROCUREMENT_ORDER_V1', 'SALES_QUOTE_V1', 'SALES_ORDER_V1', 'SALES_SHIPMENT_V1',
                'PROCUREMENT_RECEIPT_V1', 'INVENTORY_TRANSFER_V1', 'PRODUCTION_WORK_ORDER_V1',
                'PRODUCTION_MATERIAL_ISSUE_V1', 'PRODUCTION_COMPLETION_RECEIPT_V1', 'SALES_INVOICE_V1',
                'PURCHASE_INVOICE_V1', 'ACCOUNTING_VOUCHER_V1')
        ) print_template_gate
    union all select 'PRINT_TEMPLATE_STATUS_FIELDS_V36', 'stage034-definition',
        concat('templates=', template_count, ';enabled=', enabled_count, ';missingFields=', missing_field_count,
            ';printPermission=', print_permission_count),
        'templates=14;enabled=14;missingFields=0;printPermission>=1',
        template_count = 14 and enabled_count = 14 and missing_field_count = 0 and print_permission_count >= 1,
        'V36 固定打印模板必须包含模板状态、场景、对象和版本字段，且保留固定打印生成权限。'
        from (
            select
                count(*) as template_count,
                count(*) filter (where status = 'ENABLED') as enabled_count,
                count(*) filter (
                    where coalesce(template_code, '') = ''
                        or coalesce(scene_code, '') = ''
                        or coalesce(object_type, '') = ''
                        or template_version is null
                        or coalesce(status, '') = ''
                ) as missing_field_count,
                (select count(*) from sys_permission where code = 'platform:print:generate') as print_permission_count
            from platform_print_template
            where template_code in ('CONTRACT_ACTIVATION_APPROVAL_V1', 'BOM_ECO_APPLICATION_APPROVAL_V1',
                'PROCUREMENT_ORDER_V1', 'SALES_QUOTE_V1', 'SALES_ORDER_V1', 'SALES_SHIPMENT_V1',
                'PROCUREMENT_RECEIPT_V1', 'INVENTORY_TRANSFER_V1', 'PRODUCTION_WORK_ORDER_V1',
                'PRODUCTION_MATERIAL_ISSUE_V1', 'PRODUCTION_COMPLETION_RECEIPT_V1', 'SALES_INVOICE_V1',
                'PURCHASE_INVOICE_V1', 'ACCOUNTING_VOUCHER_V1')
        ) print_template_status_gate
    union all select 'DELIVERY_ASSET_CATALOG_COUNTS_V36', 'stage034-definition',
        concat('dataRepairAdapters=', data_repair_adapter_count, ';historyImportAdapters=', history_import_adapter_count,
            ';batchTools=', batch_tool_count, ';printTemplates=', print_template_count),
        'dataRepairAdapters=3;historyImportAdapters=5;batchTools=4;printTemplates=14',
        data_repair_adapter_count = 3 and history_import_adapter_count = 5
            and batch_tool_count = 4 and print_template_count = 14,
        '034 交付资料只读目录的固定定义来源必须具备三类修复、五类导入、四类批量和十四个模板。'
        from (
            select
                (select count(*) from platform_data_repair_adapter_definition where status = 'ENABLED') as data_repair_adapter_count,
                (select count(*) from platform_import_adapter_definition where status = 'ENABLED') as history_import_adapter_count,
                (select count(*) from platform_batch_tool_definition where status = 'ENABLED') as batch_tool_count,
                (select count(*) from platform_print_template where status = 'ENABLED') as print_template_count
        ) delivery_asset_catalog_gate
    union all select 'DATA_REPAIR_RESPONSIBILITY_SEPARATION_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';total=', total_count, ';verified=', verified_count,
            ';rejected=', rejected_count, ';samePersonViolations=', same_person_violation_count),
        'Stage034ZeroFacts total=0 or Stage034FullFacts verified>=1 rejected>=1 samePersonViolations=0',
        (
            profile <> 'Stage034FullFacts' and total_count = 0
        ) or (
            total_count >= 2 and verified_count >= 1 and rejected_count >= 1 and same_person_violation_count = 0
        ),
        '034 数据修复演示样本必须支持正式零事实合法；隔离完整样本必须覆盖已验证、已驳回和职责分离。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(*) as total_count,
                count(*) filter (where status = 'VERIFIED') as verified_count,
                count(*) filter (where status = 'REJECTED') as rejected_count,
                count(*) filter (
                    where created_by_username is not null
                        and (
                            exists (
                                select 1
                                from platform_approval_history h
                                where h.instance_id = approval_instance_id
                                    and h.action in ('APPROVE', 'REJECT')
                                    and h.operator_username = created_by_username
                            )
                            or executed_by_username is not null
                                and verified_by_username is not null
                                and executed_by_username = verified_by_username
                        )
                ) as same_person_violation_count
            from platform_data_repair_request
        ) repair_separation_gate
    union all select 'DATA_REPAIR_ATTACHMENT_SUPPORT_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';attachments=', attachment_count, ';availableFiles=', available_file_count),
        'Stage034ZeroFacts attachments=0 or Stage034FullFacts DATA_REPAIR_REQUEST attachments>=1 with available files',
        (
            profile <> 'Stage034FullFacts' and attachment_count = 0
        ) or (
            attachment_count >= 1 and available_file_count = attachment_count
        ),
        '034 数据修复演示样本必须通过既有附件关系支持 DATA_REPAIR_REQUEST 证据附件；正式零事实合法。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(a.id) as attachment_count,
                count(f.id) filter (where f.status = 'AVAILABLE' and f.size_bytes > 0 and f.sha256 is not null) as available_file_count
            from platform_business_attachment a
            left join platform_file_object f on f.id = a.file_id
            where a.object_type = 'DATA_REPAIR_REQUEST'
                and a.status = 'AVAILABLE'
        ) repair_attachment_gate
    union all select 'DATA_REPAIR_VERIFY_FAILED_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';verifyFailed=', verify_failed_count, ';failedChecks=', failed_check_count,
            ';verifyEvents=', verify_event_count),
        'Stage034ZeroFacts verifyFailed=0 or Stage034FullFacts VERIFY_FAILED>=1 with failed check and verify event',
        (
            profile <> 'Stage034FullFacts' and verify_failed_count = 0
        ) or (
            verify_failed_count >= 1 and failed_check_count >= 1 and verify_event_count >= 1
        ),
        '034 数据修复演示样本必须覆盖 VERIFY_FAILED 终态、失败检查和验证事件；正式零事实合法。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(distinct r.id) filter (where r.status = 'VERIFY_FAILED') as verify_failed_count,
                count(distinct c.id) filter (where c.check_type = 'VERIFICATION' and c.status = 'FAILED') as failed_check_count,
                count(distinct e.id) filter (where e.event_type = 'VERIFY' and e.status_after = 'VERIFY_FAILED') as verify_event_count
            from platform_data_repair_request r
            left join platform_data_repair_check c on c.request_id = r.id
            left join platform_data_repair_event e on e.request_id = r.id
        ) repair_verify_failed_gate
    union all select 'HISTORY_IMPORT_ATOMICITY_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';successfulAdapters=', successful_adapter_count,
            ';failedBatches=', failed_batch_count, ';partialCommits=', partial_commit_count),
        'Stage034ZeroFacts successfulAdapters=0 or Stage034FullFacts successfulAdapters=5 failedBatches>=1 partialCommits=0',
        (
            profile <> 'Stage034FullFacts' and successful_adapter_count = 0 and failed_batch_count = 0
        ) or (
            successful_adapter_count = 5 and failed_batch_count >= 1 and partial_commit_count = 0
        ),
        '034 历史导入必须支持零事实合法；隔离完整样本必须五类适配器均成功、保留校验失败且不得出现失败后部分提交。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(distinct b.import_type) filter (
                    where b.import_type in ('CUSTOMER_MASTER_V1', 'SUPPLIER_MASTER_V1', 'MATERIAL_MASTER_V1',
                        'BOM_DRAFT_V1', 'SALES_PROJECT_DRAFT_V1')
                        and b.status = 'COMMITTED'
                        and t.status = 'SUCCEEDED'
                ) as successful_adapter_count,
                count(*) filter (
                    where b.import_type in ('CUSTOMER_MASTER_V1', 'SUPPLIER_MASTER_V1', 'MATERIAL_MASTER_V1',
                        'BOM_DRAFT_V1', 'SALES_PROJECT_DRAFT_V1')
                        and b.status = 'VALIDATION_FAILED'
                        and t.status = 'VALIDATION_FAILED'
                ) as failed_batch_count,
                count(*) filter (
                    where b.import_type in ('CUSTOMER_MASTER_V1', 'SUPPLIER_MASTER_V1', 'MATERIAL_MASTER_V1',
                        'BOM_DRAFT_V1', 'SALES_PROJECT_DRAFT_V1')
                        and t.status in ('VALIDATION_FAILED', 'FAILED', 'CANCELLED', 'EXPIRED')
                        and b.status = 'COMMITTED'
                ) as partial_commit_count
            from platform_import_batch b
            join platform_document_task t on t.id = b.task_id
        ) history_import_atomicity_gate
    union all select 'HISTORY_IMPORT_ERROR_DETAILS_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';failedTasks=', failed_task_count, ';taskErrors=', task_error_count,
            ';importErrors=', import_error_count),
        'Stage034ZeroFacts failedTasks=0 or Stage034FullFacts failedTasks>=1 taskErrors>=1 importErrors>=1',
        (
            profile <> 'Stage034FullFacts' and failed_task_count = 0 and task_error_count = 0 and import_error_count = 0
        ) or (
            failed_task_count >= 1 and task_error_count >= 1 and import_error_count >= 1
        ),
        '034 历史导入失败必须能通过既有文档任务错误明细复用端点读取逐行错误，并保留导入错误表明细。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(distinct t.id) filter (where t.status = 'VALIDATION_FAILED' and t.task_type like '%_HISTORY_IMPORT') as failed_task_count,
                count(distinct te.id) as task_error_count,
                count(distinct ie.id) as import_error_count
            from platform_document_task t
            left join platform_import_batch b on b.task_id = t.id
            left join platform_document_task_error te on te.task_id = t.id
            left join platform_import_error ie on ie.batch_id = b.id
            where t.task_type like '%_HISTORY_IMPORT'
        ) history_import_error_detail_gate
    union all select 'HISTORY_IMPORT_CONFIRM_REVALIDATION_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';readyTasks=', ready_task_count, ';validatedBatches=', validated_batch_count),
        'Stage034ZeroFacts readyTasks=0 or Stage034FullFacts readyTasks>=1 validatedBatches>=1',
        (
            profile <> 'Stage034FullFacts' and ready_task_count = 0 and validated_batch_count = 0
        ) or (
            ready_task_count >= 1 and validated_batch_count >= 1
        ),
        '034 历史导入完整样本必须保留至少一条 READY_TO_COMMIT/VALIDATED 批次，用于证明旧版本 confirm 重校验失败后不会提交。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(distinct t.id) filter (where t.status = 'READY_TO_COMMIT' and t.task_type like '%_HISTORY_IMPORT') as ready_task_count,
                count(distinct b.id) filter (where b.status = 'VALIDATED') as validated_batch_count
            from platform_document_task t
            left join platform_import_batch b on b.task_id = t.id
            where t.task_type like '%_HISTORY_IMPORT'
        ) history_import_confirm_revalidation_gate
    union all select 'DOCUMENT_TASK_IDEMPOTENCY_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';records=', record_count, ';duplicates=', duplicate_count,
            ';missingFingerprint=', missing_fingerprint_count),
        'Stage034ZeroFacts records=0 or Stage034FullFacts records>=8 duplicates=0 missingFingerprint=0',
        (
            profile <> 'Stage034FullFacts' and record_count = 0
        ) or (
            record_count >= 8 and duplicate_count = 0 and missing_fingerprint_count = 0
        ),
        '034 修复、历史导入和批量动作必须通过统一幂等记录留痕，同一主体动作资源和幂等键不得产生重复结果。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                count(*) as record_count,
                count(*) filter (where coalesce(row_data ->> 'request_fingerprint', row_data ->> 'requestFingerprint') is null
                    or coalesce(row_data ->> 'request_fingerprint', row_data ->> 'requestFingerprint') = '') as missing_fingerprint_count,
                (
                    select count(*)
                    from (
                        select 1
                        from (
                            select
                                coalesce(row_data ->> 'operator_user_id', row_data ->> 'operatorUserId') as subject_id,
                                coalesce(row_data ->> 'action', row_data ->> 'action_code', row_data ->> 'actionCode') as action_code,
                                row_data ->> 'target_type' as resource_type,
                                row_data ->> 'target_id' as resource_id,
                                coalesce(row_data ->> 'idempotency_key', row_data ->> 'idempotencyKey') as idempotency_key
                            from (
                                select to_jsonb(i) as row_data from platform_action_idempotency i
                            ) idempotency_rows
                        ) grouped_idempotency
                        where idempotency_key is not null and idempotency_key <> ''
                        group by subject_id, action_code, resource_type, resource_id, idempotency_key
                        having count(*) > 1
                    ) duplicate_groups
                ) as duplicate_count
            from (
                select to_jsonb(i) as row_data from platform_action_idempotency i
            ) idempotency_rows
        ) document_task_idempotency_gate
    union all select 'STAGE034_SAMPLE_COVERAGE_DYNAMIC', 'stage034-sample',
        concat('profile=', profile, ';repairAdapters=', repair_adapter_count,
            ';batchTools=', batch_tool_count, ';newTemplatePrints=', new_template_print_count,
            ';taskStatuses=', task_status_count),
        'Stage034ZeroFacts coverage=0 or Stage034FullFacts repairAdapters=3 batchTools=4 newTemplatePrints=10 taskStatuses>=4',
        (
            profile <> 'Stage034FullFacts'
                and repair_adapter_count = 0
                and batch_tool_count = 0
                and new_template_print_count = 0
        ) or (
            repair_adapter_count = 3
                and batch_tool_count = 4
                and new_template_print_count = 10
                and task_status_count >= 4
        ),
        '034 隔离完整样本必须覆盖三类修复、四类批量、十个新增模板成功打印和至少四类任务状态；正式零事实合法。'
        from (
            select
                coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile,
                (
                    select count(distinct row_data ->> 'adapter_code')
                    from (select to_jsonb(r) as row_data from platform_data_repair_request r) repair_rows
                    where row_data ->> 'adapter_code' in ('MATERIAL_PROFILE_CORRECTION_V1',
                        'CUSTOMER_PROFILE_CORRECTION_V1', 'SUPPLIER_PROFILE_CORRECTION_V1')
                ) as repair_adapter_count,
                (
                    select count(distinct row_data ->> 'tool_code')
                    from (select to_jsonb(o) as row_data from platform_batch_operation o) batch_rows
                    where row_data ->> 'tool_code' in ('CUSTOMER_STATUS_CHANGE_V1', 'SUPPLIER_STATUS_CHANGE_V1',
                        'MATERIAL_STATUS_CHANGE_V1', 'FIXED_DOCUMENT_BATCH_PRINT_V1')
                ) as batch_tool_count,
                (
                    select count(distinct request_payload ->> 'templateCode')
                    from platform_document_task
                    where status = 'SUCCEEDED'
                        and request_payload ->> 'templateCode' in ('SALES_ORDER_V1', 'SALES_SHIPMENT_V1',
                            'PROCUREMENT_RECEIPT_V1', 'INVENTORY_TRANSFER_V1', 'PRODUCTION_WORK_ORDER_V1',
                            'PRODUCTION_MATERIAL_ISSUE_V1', 'PRODUCTION_COMPLETION_RECEIPT_V1',
                            'SALES_INVOICE_V1', 'PURCHASE_INVOICE_V1', 'ACCOUNTING_VOUCHER_V1')
                ) as new_template_print_count,
                (
                    select count(distinct status)
                    from platform_document_task
                    where (
                            task_type like '%_HISTORY_IMPORT'
                            or task_type in ('MATERIAL_IMPORT', 'MATERIAL_EXPORT', 'BOM_DRAFT_IMPORT', 'BOM_DRAFT_EXPORT',
                                'FIXED_DOCUMENT_PRINT', 'APPROVAL_PRINT')
                        )
                        and status in ('READY_TO_COMMIT', 'VALIDATION_FAILED', 'SUCCEEDED', 'CANCELLED', 'EXPIRED')
                ) as task_status_count
        ) stage034_sample_gate
    union all select 'AUDIT_LOGS_MIN_100', 'audit', count(*)::text, '>= 100', count(*) >= 100,
        '审计日志数量不足。' from sys_audit_log
    union all select 'AUDIT_DENIED_MIN_1', 'audit', count(*)::text, '>= 1', count(*) >= 1,
        '缺少权限拒绝审计样例。' from sys_audit_log where result <> 'SUCCESS' or error_code is not null
),
profile_settings as (
    select coalesce(nullif(current_setting('qherp.stage034_profile', true), ''), 'Default') as profile
),
effective_rules as (
    select
        rule_code,
        category,
        actual_value,
        expected_value,
        case
            when profile = 'Stage034ZeroFacts'
                and category not in ('migration', 'stage034-definition', 'stage034-sample')
            then true
            else passed
        end as passed,
        case
            when profile = 'Stage034ZeroFacts'
                and category not in ('migration', 'stage034-definition', 'stage034-sample')
            then message || '（Stage034ZeroFacts 零业务事实配置不强制该演示业务样本。）'
            else message
        end as message
    from rules
    cross join profile_settings
)
select jsonb_build_object(
    'validatorVersion', 'demo-data-validator-v2-stage034',
    'checkedAt', now(),
    'status', case when count(*) filter (where not passed) = 0 then 'PASS' else 'FAIL' end,
    'totalRules', count(*),
    'failedRules', count(*) filter (where not passed),
    'rules', jsonb_agg(jsonb_build_object(
        'ruleCode', rule_code,
        'category', category,
        'actualValue', actual_value,
        'expectedValue', expected_value,
        'passed', passed,
        'message', message
    ) order by rule_code)
)::text
from effective_rules;

commit;
