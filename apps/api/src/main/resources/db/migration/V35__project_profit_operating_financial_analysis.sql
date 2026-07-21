alter table biz_period_report_snapshot
	drop constraint ck_biz_period_report_snapshot_code;

alter table biz_period_report_snapshot
	add constraint ck_biz_period_report_snapshot_code check (
		report_code in (
			'OVERVIEW',
			'SALES_SUMMARY',
			'PROCUREMENT_SUMMARY',
			'INVENTORY_STOCK_FLOW',
			'PRODUCTION_EXECUTION',
			'COST_COLLECTION',
			'SETTLEMENT_SUMMARY',
			'EXCEPTIONS',
			'PROJECT_PROFIT',
			'CONTRACT_COLLECTION',
			'PROCUREMENT_VARIANCE',
			'INVENTORY_CAPITAL',
			'RECEIVABLE_PAYABLE'
		)
	);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('report', '经营报表', 'MENU', null, '/reports', null, null, 700, 'system', now(), 'system', now())
on conflict (code) do update
set name = excluded.name,
    type = excluded.type,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    api_method = excluded.api_method,
    api_path = excluded.api_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('report:operating-finance:view', '查看经营财务分析总览', '/reports/overview', 'GET',
			'/api/admin/reports/operating-finance-overview', 709),
		('report:project-profit:view', '查看项目利润分析', '/reports/project-profit', 'GET',
			'/api/admin/reports/project-profit/**', 710),
		('report:contract-collection:view', '查看合同回款分析', '/reports/contract-collection', 'GET',
			'/api/admin/reports/contract-collections/**', 711),
		('report:procurement-variance:view', '查看采购差异分析', '/reports/procurement-variance', 'GET',
			'/api/admin/reports/procurement-variances/**', 712),
		('report:inventory-capital:view', '查看库存资金分析', '/reports/inventory-capital', 'GET',
			'/api/admin/reports/inventory-capital/**', 713),
		('report:receivable-payable:view', '查看往来账龄分析', '/reports/receivable-payable', 'GET',
			'/api/admin/reports/receivable-payable/**', 714),
		('report:operating-accounting:view', '查看经营会计对照', '/reports/operating-accounting-reconciliation', 'GET',
			'/api/admin/reports/operating-accounting-reconciliation/**', 715),
		('report:financial-summary:view', '查看固定经营财务摘要', '/reports/financial-summary', 'GET',
			'/api/admin/reports/financial-summary/**', 716)
) as seed(code, name, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'report'
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    api_method = excluded.api_method,
    api_path = excluded.api_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code in (
	'report:operating-finance:view',
	'report:project-profit:view',
	'report:contract-collection:view',
	'report:procurement-variance:view',
	'report:inventory-capital:view',
	'report:receivable-payable:view',
	'report:operating-accounting:view',
	'report:financial-summary:view'
)
where r.code = 'SYSTEM_ADMIN'
on conflict (role_id, permission_id) do nothing;

create index idx_prj_cost_calculation_current_project_period
	on prj_cost_calculation (project_id, cutoff_date desc, id desc)
	where is_current = true and status in ('CALCULATED', 'CONFIRMED');

create index idx_gl_ledger_entry_period_account_code
	on gl_ledger_entry (period_id, account_code, voucher_date, id);

create index idx_gl_ledger_entry_auxiliary_snapshot
	on gl_ledger_entry using gin (auxiliary_snapshot);
