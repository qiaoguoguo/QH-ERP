\set ON_ERROR_STOP on

begin transaction read only;

with rules(rule_code, category, actual_value, expected_value, passed, message) as (
    select 'FLYWAY_LATEST_V31'::text, 'migration'::text,
        concat(
            'version=', coalesce((array_agg(version::int order by version::int desc))[1]::text, 'none'),
            ';checksum=', coalesce((array_agg(checksum order by version::int desc))[1]::text, 'none')
        ),
        'latest successful version = 31; checksum = -1120716708'::text,
        (coalesce((array_agg(version::int order by version::int desc))[1], 0) = 31
            and coalesce((array_agg(checksum order by version::int desc))[1], 0) = -1120716708),
        'Flyway 最新成功版本必须为 V31，checksum 必须为 -1120716708。'::text
    from flyway_schema_history where success and version ~ '^[0-9]+$'
    union all select 'FLYWAY_V31_CHECKSUM', 'migration',
        concat('version=31;checksum=', coalesce((array_agg(checksum))[1]::text, 'none')),
        'version 31 checksum = -1120716708',
        coalesce((array_agg(checksum))[1], 0) = -1120716708,
        'Flyway V31 checksum 必须保持 -1120716708。'
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
