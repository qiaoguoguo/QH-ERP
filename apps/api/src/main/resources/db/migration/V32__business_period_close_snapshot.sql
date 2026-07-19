create table biz_period_close_run (
	id bigserial primary key,
	period_id bigint not null references biz_business_period (id),
	revision_no integer not null,
	status varchar(32) not null,
	latest_check_run_id bigint,
	snapshot_id bigint,
	schema_version integer not null default 1,
	source_fingerprint varchar(64),
	inventory_fingerprint varchar(64),
	wip_fingerprint varchar(64),
	project_cost_fingerprint varchar(64),
	report_fingerprint varchar(64),
	blocking_count integer not null default 0,
	warning_count integer not null default 0,
	warning_acknowledged boolean not null default false,
	warning_reason varchar(500),
	closed_by varchar(64),
	closed_at timestamptz,
	close_reason varchar(500),
	reopened_by varchar(64),
	reopened_at timestamptz,
	reopen_reason varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_biz_period_close_run_period_revision unique (period_id, revision_no),
	constraint ck_biz_period_close_run_status check (
		status in ('PENDING_CHECK', 'BLOCKED', 'READY', 'CLOSED', 'REOPENED')
	),
	constraint ck_biz_period_close_run_counts check (blocking_count >= 0 and warning_count >= 0),
	constraint ck_biz_period_close_run_reason check (
		(status <> 'CLOSED' or close_reason is not null)
		and (status <> 'REOPENED' or reopen_reason is not null)
	)
);

create unique index uk_biz_period_close_run_current_closed
	on biz_period_close_run (period_id)
	where status = 'CLOSED';

create index idx_biz_period_close_run_period
	on biz_period_close_run (period_id, revision_no desc, id desc);

create table biz_period_close_check_run (
	id bigserial primary key,
	run_id bigint not null references biz_period_close_run (id),
	period_id bigint not null references biz_business_period (id),
	revision_no integer not null,
	status varchar(32) not null,
	schema_version integer not null default 1,
	source_fingerprint varchar(64) not null,
	inventory_fingerprint varchar(64) not null,
	wip_fingerprint varchar(64) not null,
	project_cost_fingerprint varchar(64) not null,
	report_fingerprint varchar(64) not null,
	blocking_count integer not null default 0,
	warning_count integer not null default 0,
	started_by varchar(64) not null,
	started_at timestamptz not null default now(),
	completed_at timestamptz not null default now(),
	constraint ck_biz_period_close_check_run_status check (status in ('BLOCKED', 'READY')),
	constraint ck_biz_period_close_check_run_counts check (blocking_count >= 0 and warning_count >= 0)
);

create index idx_biz_period_close_check_run_run
	on biz_period_close_check_run (run_id, id desc);

create table biz_period_close_check_item (
	id bigserial primary key,
	check_run_id bigint not null references biz_period_close_check_run (id),
	domain varchar(64) not null,
	check_code varchar(80) not null,
	severity varchar(32) not null,
	source_restricted boolean not null default false,
	object_type varchar(64),
	object_id bigint,
	object_no varchar(120),
	title varchar(160) not null,
	description varchar(1000) not null,
	suggestion varchar(500) not null,
	source_route jsonb not null default '{}'::jsonb,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_close_check_item_domain check (
		domain in ('PERIOD', 'INVENTORY', 'WIP', 'PROJECT_COST', 'REPORT')
	),
	constraint ck_biz_period_close_check_item_severity check (severity in ('BLOCKING', 'WARNING', 'INFO'))
);

create index idx_biz_period_close_check_item_run
	on biz_period_close_check_item (check_run_id, severity, id);

create table biz_period_snapshot (
	id bigserial primary key,
	run_id bigint not null unique references biz_period_close_run (id),
	period_id bigint not null references biz_business_period (id),
	revision_no integer not null,
	schema_version integer not null default 1,
	source_check_run_id bigint not null references biz_period_close_check_run (id),
	source_fingerprint varchar(64) not null,
	inventory_fingerprint varchar(64) not null,
	wip_fingerprint varchar(64) not null,
	project_cost_fingerprint varchar(64) not null,
	report_fingerprint varchar(64) not null,
	generated_by varchar(64) not null,
	generated_at timestamptz not null default now(),
	constraint uk_biz_period_snapshot_period_revision unique (period_id, revision_no)
);

create table biz_period_inventory_snapshot (
	id bigserial primary key,
	snapshot_id bigint not null references biz_period_snapshot (id),
	warehouse_id bigint,
	warehouse_name varchar(120),
	material_id bigint,
	material_code varchar(80),
	material_name varchar(160),
	quality_status varchar(32),
	ownership_type varchar(32) not null,
	project_id bigint,
	project_no varchar(80),
	batch_id bigint,
	serial_id bigint,
	cost_layer_id bigint,
	ending_quantity numeric(18, 6) not null,
	locked_quantity numeric(18, 6) not null,
	available_quantity numeric(18, 6) not null,
	valuation_state varchar(32) not null,
	unit_cost numeric(18, 6),
	ending_amount numeric(18, 2),
	in_quantity numeric(18, 6) not null default 0,
	out_quantity numeric(18, 6) not null default 0,
	adjustment_quantity numeric(18, 6) not null default 0,
	fingerprint varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_inventory_snapshot_qty check (
		ending_quantity >= 0 and locked_quantity >= 0 and in_quantity >= 0
		and out_quantity >= 0 and adjustment_quantity >= 0
	),
	constraint ck_biz_period_inventory_snapshot_amount check (
		unit_cost is null or unit_cost >= 0
	),
	constraint ck_biz_period_inventory_snapshot_ownership check (ownership_type in ('PUBLIC', 'PROJECT'))
);

create index idx_biz_period_inventory_snapshot_snapshot
	on biz_period_inventory_snapshot (snapshot_id, warehouse_id, material_id, id);

create table biz_period_inventory_summary (
	id bigserial primary key,
	snapshot_id bigint not null references biz_period_snapshot (id),
	summary_type varchar(64) not null,
	object_type varchar(64),
	object_id bigint,
	object_no varchar(120),
	quantity numeric(18, 6),
	amount numeric(18, 2),
	item_count integer not null default 0,
	risk_count integer not null default 0,
	fingerprint varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_inventory_summary_counts check (item_count >= 0 and risk_count >= 0),
	constraint ck_biz_period_inventory_summary_quantity check (quantity is null or quantity >= 0)
);

create index idx_biz_period_inventory_summary_snapshot
	on biz_period_inventory_summary (snapshot_id, summary_type, id);

create table biz_period_wip_snapshot (
	id bigserial primary key,
	snapshot_id bigint not null references biz_period_snapshot (id),
	project_id bigint,
	project_no varchar(80),
	work_order_id bigint,
	work_order_no varchar(80),
	product_material_id bigint,
	product_material_code varchar(80),
	product_material_name varchar(160),
	status varchar(32),
	planned_quantity numeric(18, 6) not null default 0,
	issued_quantity numeric(18, 6) not null default 0,
	reported_quantity numeric(18, 6) not null default 0,
	qualified_quantity numeric(18, 6) not null default 0,
	completed_quantity numeric(18, 6) not null default 0,
	wip_quantity numeric(18, 6) not null default 0,
	wip_cost numeric(18, 2) not null default 0,
	fingerprint varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_wip_snapshot_amount check (wip_cost >= 0)
);

create index idx_biz_period_wip_snapshot_snapshot
	on biz_period_wip_snapshot (snapshot_id, work_order_id, id);

create table biz_period_project_cost_snapshot (
	id bigserial primary key,
	snapshot_id bigint not null references biz_period_snapshot (id),
	project_id bigint not null,
	project_no varchar(80),
	project_name varchar(160),
	calculation_id bigint not null,
	calculation_no varchar(80) not null,
	source_fingerprint varchar(64) not null,
	freshness_status varchar(32) not null,
	completeness_status varchar(32) not null,
	project_cost_total numeric(18, 2) not null default 0,
	wip_cost numeric(18, 2) not null default 0,
	finished_cost numeric(18, 2) not null default 0,
	delivered_cost numeric(18, 2) not null default 0,
	direct_project_cost numeric(18, 2) not null default 0,
	shipment_revenue numeric(18, 2) not null default 0,
	shipment_gross_margin numeric(18, 2),
	blocking_variance_count integer not null default 0,
	warning_variance_count integer not null default 0,
	fingerprint varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_project_cost_snapshot_amounts check (
		project_cost_total >= 0 and wip_cost >= 0 and finished_cost >= 0
		and delivered_cost >= 0 and direct_project_cost >= 0 and shipment_revenue >= 0
	),
	constraint ck_biz_period_project_cost_snapshot_counts check (
		blocking_variance_count >= 0 and warning_variance_count >= 0
	)
);

create index idx_biz_period_project_cost_snapshot_snapshot
	on biz_period_project_cost_snapshot (snapshot_id, project_id, id);

create table biz_period_report_snapshot (
	id bigserial primary key,
	snapshot_id bigint not null references biz_period_snapshot (id),
	report_code varchar(64) not null,
	schema_version integer not null default 1,
	result_json jsonb not null,
	source_count integer not null default 0,
	fingerprint varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint uk_biz_period_report_snapshot_code unique (snapshot_id, report_code),
	constraint ck_biz_period_report_snapshot_code check (
		report_code in (
			'OVERVIEW', 'SALES_SUMMARY', 'PROCUREMENT_SUMMARY', 'INVENTORY_STOCK_FLOW',
			'PRODUCTION_EXECUTION', 'COST_COLLECTION', 'SETTLEMENT_SUMMARY', 'EXCEPTIONS'
		)
	),
	constraint ck_biz_period_report_snapshot_count check (source_count >= 0)
);

create index idx_biz_period_report_snapshot_snapshot
	on biz_period_report_snapshot (snapshot_id, report_code);

create table biz_period_close_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	action varchar(32) not null,
	resource_type varchar(32) not null,
	resource_id bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	response_run_id bigint not null references biz_period_close_run (id),
	response_status varchar(32) not null,
	created_at timestamptz not null default now(),
	constraint uk_biz_period_close_action_idempotency unique (
		operator_user_id, action, resource_type, resource_id, idempotency_key
	),
	constraint ck_biz_period_close_action_idempotency_action check (action in ('CHECK', 'CLOSE', 'REOPEN')),
	constraint ck_biz_period_close_action_idempotency_status check (
		response_status in ('PENDING_CHECK', 'BLOCKED', 'READY', 'CLOSED', 'REOPENED')
	)
);

create table biz_period_close_audit (
	id bigserial primary key,
	run_id bigint references biz_period_close_run (id),
	period_id bigint references biz_business_period (id),
	action varchar(64) not null,
	result varchar(32) not null,
	reason varchar(500),
	source_fingerprint varchar(64),
	error_code varchar(64),
	operator_user_id bigint,
	operator_username varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_biz_period_close_audit_result check (result in ('SUCCESS', 'FAILURE', 'REPLAY', 'CONFLICT'))
);

create index idx_biz_period_close_audit_run
	on biz_period_close_audit (run_id, created_at desc, id desc);

alter table biz_period_close_run
	add constraint fk_biz_period_close_run_latest_check
		foreign key (latest_check_run_id) references biz_period_close_check_run (id),
	add constraint fk_biz_period_close_run_snapshot
		foreign key (snapshot_id) references biz_period_snapshot (id);

create or replace function forbid_biz_period_snapshot_mutation()
returns trigger
language plpgsql
as $$
begin
	raise exception '业务月结快照不可更新或删除';
end;
$$;

create trigger tr_biz_period_snapshot_immutable
	before update or delete on biz_period_snapshot
	for each row execute function forbid_biz_period_snapshot_mutation();

create trigger tr_biz_period_inventory_snapshot_immutable
	before update or delete on biz_period_inventory_snapshot
	for each row execute function forbid_biz_period_snapshot_mutation();

create trigger tr_biz_period_inventory_summary_immutable
	before update or delete on biz_period_inventory_summary
	for each row execute function forbid_biz_period_snapshot_mutation();

create trigger tr_biz_period_wip_snapshot_immutable
	before update or delete on biz_period_wip_snapshot
	for each row execute function forbid_biz_period_snapshot_mutation();

create trigger tr_biz_period_project_cost_snapshot_immutable
	before update or delete on biz_period_project_cost_snapshot
	for each row execute function forbid_biz_period_snapshot_mutation();

create trigger tr_biz_period_report_snapshot_immutable
	before update or delete on biz_period_report_snapshot
	for each row execute function forbid_biz_period_snapshot_mutation();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('system', '系统管理', 'MENU', null, '/system', null, null, 900,
	'system', now(), 'system', now())
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select 'system:business-period-close', '业务月结', 'MENU', parent.id, '/period-close/runs',
       null, null, 940, 'system', now(), 'system', now()
from sys_permission parent
where parent.code = 'system'
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, '/period-close/runs', seed.api_method,
       seed.api_path, seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('system:business-period-close:view', '查看业务月结', 'GET', '/api/admin/period-closes/**', 941),
		('system:business-period-close:check', '执行业务月结检查', 'POST', '/api/admin/period-closes/checks', 942),
		('system:business-period-close:close', '关闭业务期间并生成快照', 'POST', '/api/admin/period-closes/{runId}/close', 943),
		('system:business-period-close:reopen', '重开业务月结期间', 'POST', '/api/admin/period-closes/{runId}/reopen', 944),
		('system:business-period-close:snapshot-view', '查看业务月结快照', 'GET', '/api/admin/period-closes/{runId}/snapshot/**', 945)
) as seed(code, name, api_method, api_path, sort_order)
join sys_permission parent on parent.code = 'system:business-period-close'
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
	'system:business-period-close:view',
	'system:business-period-close:check',
	'system:business-period-close:close',
	'system:business-period-close:reopen',
	'system:business-period-close:snapshot-view'
)
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1
	from sys_role_permission rp
	where rp.role_id = r.id
	and rp.permission_id = p.id
);
