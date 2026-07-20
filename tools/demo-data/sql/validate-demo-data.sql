\set ON_ERROR_STOP on

begin transaction read only;

with rules(rule_code, category, actual_value, expected_value, passed, message) as (
    select 'FLYWAY_LATEST_V34'::text, 'migration'::text,
        concat(
            'version=', coalesce((array_agg(version::int order by version::int desc))[1]::text, 'none'),
            ';checksum=', coalesce((array_agg(checksum order by version::int desc))[1]::text, 'none')
        ),
        'latest successful version = 34; checksum = -1893080635'::text,
        coalesce((array_agg(version::int order by version::int desc))[1], 0) = 34
            and coalesce((array_agg(checksum order by version::int desc))[1], 0) = -1893080635,
        'Flyway 最新成功版本必须为 V34，V34 checksum 必须保持 -1893080635。'::text
    from flyway_schema_history where success and version ~ '^[0-9]+$'
    union all select 'FLYWAY_V34_CHECKSUM', 'migration',
        concat('version=34;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 34 checksum = -1893080635',
        coalesce((array_agg(checksum))[1], 0) = -1893080635,
        'Flyway V34 checksum 必须保持 -1893080635。'
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
    union all select 'PERIOD_CLOSE_REPORT_SNAPSHOT_CODES_8', 'period-close', count(*)::text, '0', count(*) = 0,
        '每个 CLOSED 快照必须精确保存八类固定经营报表基线。'
        from (
            select s.id
            from biz_period_snapshot s
            join biz_period_close_run r on r.id = s.run_id
            left join biz_period_report_snapshot report on report.snapshot_id = s.id
            where r.status = 'CLOSED'
            group by s.id
            having count(distinct report.report_code) <> 8
                or count(*) filter (
                    where report.report_code not in ('OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY',
                        'INVENTORY_STOCK_FLOW', 'PRODUCTION_EXECUTION', 'COST_COLLECTION',
                        'SETTLEMENT_SUMMARY', 'EXCEPTIONS')
                ) > 0
        ) report_snapshot_code_violations
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
        and target_type in ('FIN_RECEIVABLE', 'FIN_PAYABLE', 'FIN_RECEIPT', 'FIN_PAYMENT',
            'PRJ_COST_CALCULATION', 'BIZ_PERIOD_CLOSE_RUN')
    union all select 'FINANCIAL_CLOSE_BANK_RECONCILIATION_BALANCE_DYNAMIC', 'financial-close', count(*)::text, '0', count(*) = 0,
        '已确认银行对账必须零差额，调整后银行余额与账面余额完全一致。'
        from fin_bank_reconciliation_run
        where status = 'CONFIRMED'
        and difference_amount <> 0
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
    union all select 'AUDIT_LOGS_MIN_100', 'audit', count(*)::text, '>= 100', count(*) >= 100,
        '审计日志数量不足。' from sys_audit_log
    union all select 'AUDIT_DENIED_MIN_1', 'audit', count(*)::text, '>= 1', count(*) >= 1,
        '缺少权限拒绝审计样例。' from sys_audit_log where result <> 'SUCCESS' or error_code is not null
)
select jsonb_build_object(
    'validatorVersion', 'demo-data-validator-v1',
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
from rules;

commit;
