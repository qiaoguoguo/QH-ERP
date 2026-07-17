create table mrp_calculation_run (
	id bigserial primary key,
	run_no varchar(64) not null,
	scope_type varchar(32) not null,
	project_id bigint,
	customer_id bigint,
	contract_id bigint,
	sales_order_id bigint,
	material_id bigint,
	demand_date_to date not null,
	include_public_demand boolean not null default true,
	scope_hash varchar(64) not null,
	request_fingerprint varchar(64) not null,
	source_fingerprint varchar(64),
	source_snapshot jsonb not null default '{}'::jsonb,
	status varchar(32) not null,
	status_reason varchar(500),
	failure_code varchar(64),
	failure_summary varchar(500),
	calculated_at timestamptz,
	expires_at timestamptz,
	idempotency_key varchar(120),
	previous_run_id bigint,
	created_by_user_id bigint,
	created_by_username varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mrp_calculation_run_no unique (run_no),
	constraint fk_mrp_calculation_run_project foreign key (project_id) references sal_project (id),
	constraint fk_mrp_calculation_run_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_mrp_calculation_run_contract foreign key (contract_id) references sal_project_contract (id),
	constraint fk_mrp_calculation_run_sales_order foreign key (sales_order_id) references sal_sales_order (id),
	constraint fk_mrp_calculation_run_material foreign key (material_id) references mst_material (id),
	constraint fk_mrp_calculation_run_previous foreign key (previous_run_id) references mrp_calculation_run (id) on delete set null,
	constraint ck_mrp_calculation_run_scope check (scope_type in ('PROJECT', 'CUSTOMER', 'CONTRACT', 'SALES_ORDER', 'MATERIAL', 'GLOBAL')),
	constraint ck_mrp_calculation_run_status check (status in ('RUNNING', 'COMPLETED', 'FAILED', 'STALE', 'EXPIRED')),
	constraint ck_mrp_calculation_run_project_scope check (scope_type <> 'PROJECT' or project_id is not null)
);

create unique index uk_mrp_calculation_run_idempotency
	on mrp_calculation_run (created_by_user_id, idempotency_key)
	where idempotency_key is not null;

create index idx_mrp_calculation_run_scope
	on mrp_calculation_run (scope_hash, source_fingerprint, status, expires_at desc, id desc);

create index idx_mrp_calculation_run_status
	on mrp_calculation_run (status, updated_at desc, id desc);

create table mrp_requirement_line (
	id bigserial primary key,
	run_id bigint not null,
	line_no integer not null,
	demand_source_type varchar(64) not null,
	demand_source_id bigint,
	demand_source_line_id bigint,
	delivery_plan_id bigint,
	delivery_plan_no varchar(64),
	demand_type varchar(32) not null,
	project_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	demand_date date not null,
	required_quantity numeric(18, 6) not null,
	covered_quantity numeric(18, 6) not null default 0,
	shortage_quantity numeric(18, 6) not null default 0,
	bom_level integer not null default 0,
	bom_path varchar(500),
	source_snapshot jsonb not null default '{}'::jsonb,
	created_at timestamptz not null default now(),
	constraint fk_mrp_requirement_line_run foreign key (run_id) references mrp_calculation_run (id) on delete cascade,
	constraint fk_mrp_requirement_line_project foreign key (project_id) references sal_project (id),
	constraint fk_mrp_requirement_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mrp_requirement_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mrp_requirement_line_no unique (run_id, line_no),
	constraint ck_mrp_requirement_line_demand_type check (demand_type in ('SALES_DEMAND', 'BOM_COMPONENT')),
	constraint ck_mrp_requirement_line_quantity check (
		required_quantity >= 0 and covered_quantity >= 0 and shortage_quantity >= 0
	)
);

create index idx_mrp_requirement_line_run_material
	on mrp_requirement_line (run_id, material_id, id);

create index idx_mrp_requirement_line_delivery_plan
	on mrp_requirement_line (delivery_plan_id)
	where delivery_plan_id is not null;

create table mrp_supply_allocation (
	id bigserial primary key,
	run_id bigint not null,
	requirement_line_id bigint not null,
	supply_type varchar(32) not null,
	source_table varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	supply_date date,
	available_quantity numeric(18, 6) not null,
	allocated_quantity numeric(18, 6) not null,
	on_time boolean not null,
	allocation_rank integer not null,
	reason varchar(64),
	source_snapshot jsonb not null default '{}'::jsonb,
	created_at timestamptz not null default now(),
	constraint fk_mrp_supply_allocation_run foreign key (run_id) references mrp_calculation_run (id) on delete cascade,
	constraint fk_mrp_supply_allocation_requirement foreign key (requirement_line_id) references mrp_requirement_line (id) on delete cascade,
	constraint fk_mrp_supply_allocation_material foreign key (material_id) references mst_material (id),
	constraint fk_mrp_supply_allocation_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_mrp_supply_allocation_type check (
		supply_type in ('PROJECT_STOCK', 'PROJECT_PURCHASE', 'PUBLIC_STOCK', 'PUBLIC_PURCHASE', 'WORK_ORDER')
	),
	constraint ck_mrp_supply_allocation_ownership check (ownership_type in ('PROJECT', 'PUBLIC')),
	constraint ck_mrp_supply_allocation_quantity check (
		available_quantity >= 0 and allocated_quantity >= 0 and allocated_quantity <= available_quantity
	)
);

create index idx_mrp_supply_allocation_requirement
	on mrp_supply_allocation (requirement_line_id, allocation_rank, id);

create table mrp_suggestion (
	id bigserial primary key,
	run_id bigint not null,
	requirement_line_id bigint not null,
	suggestion_type varchar(32) not null,
	status varchar(32) not null,
	material_id bigint not null,
	unit_id bigint not null,
	project_id bigint,
	ownership_type varchar(32) not null,
	required_date date not null,
	suggested_quantity numeric(18, 6) not null,
	material_source_type varchar(32),
	conversion_allowed boolean not null default false,
	action_disabled_reason varchar(200),
	reason varchar(200),
	target_object_type varchar(64),
	target_object_id bigint,
	target_object_line_id bigint,
	target_object_no varchar(64),
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	dismissed_by varchar(64),
	dismissed_at timestamptz,
	converted_by varchar(64),
	converted_at timestamptz,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mrp_suggestion_run foreign key (run_id) references mrp_calculation_run (id) on delete cascade,
	constraint fk_mrp_suggestion_requirement foreign key (requirement_line_id) references mrp_requirement_line (id) on delete cascade,
	constraint fk_mrp_suggestion_material foreign key (material_id) references mst_material (id),
	constraint fk_mrp_suggestion_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_mrp_suggestion_project foreign key (project_id) references sal_project (id),
	constraint ck_mrp_suggestion_type check (
		suggestion_type in ('PURCHASE_REQUISITION', 'PRODUCTION_ORDER', 'USE_PUBLIC_STOCK', 'USE_EXISTING_SUPPLY')
	),
	constraint ck_mrp_suggestion_status check (status in ('OPEN', 'CONFIRMED', 'CONVERTED', 'DISMISSED')),
	constraint ck_mrp_suggestion_ownership check (ownership_type in ('PROJECT', 'PUBLIC')),
	constraint ck_mrp_suggestion_quantity check (suggested_quantity > 0)
);

create index idx_mrp_suggestion_run_status
	on mrp_suggestion (run_id, status, id);

create unique index uk_mrp_suggestion_target
	on mrp_suggestion (target_object_type, target_object_id)
	where target_object_type is not null and target_object_id is not null;

create table mrp_substitute_hint (
	id bigserial primary key,
	run_id bigint not null,
	requirement_line_id bigint not null,
	main_material_id bigint not null,
	substitute_material_id bigint not null,
	priority integer not null,
	substitute_rate numeric(18, 6) not null,
	scope_type varchar(32) not null,
	effective_from date not null,
	effective_to date,
	created_at timestamptz not null default now(),
	constraint fk_mrp_substitute_hint_run foreign key (run_id) references mrp_calculation_run (id) on delete cascade,
	constraint fk_mrp_substitute_hint_requirement foreign key (requirement_line_id) references mrp_requirement_line (id) on delete cascade,
	constraint fk_mrp_substitute_hint_main foreign key (main_material_id) references mst_material (id),
	constraint fk_mrp_substitute_hint_substitute foreign key (substitute_material_id) references mst_material (id)
);

create index idx_mrp_substitute_hint_requirement
	on mrp_substitute_hint (requirement_line_id, priority, id);

create table mrp_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(64) not null,
	result_resource_id bigint not null,
	result_version bigint,
	created_at timestamptz not null default now(),
	constraint uk_mrp_action_idempotency unique (
		operator_user_id, action, resource_type, resource_id, idempotency_key
	)
);

alter table proc_purchase_requisition
	add column if not exists source_mrp_run_id bigint,
	add column if not exists source_mrp_suggestion_id bigint;

alter table proc_purchase_requisition_line
	add column if not exists source_mrp_run_id bigint,
	add column if not exists source_mrp_requirement_line_id bigint,
	add column if not exists source_mrp_suggestion_id bigint;

create unique index if not exists uk_proc_purchase_requisition_mrp_suggestion
	on proc_purchase_requisition (source_mrp_suggestion_id)
	where source_mrp_suggestion_id is not null;

create index if not exists idx_proc_purchase_requisition_mrp_run
	on proc_purchase_requisition (source_mrp_run_id);

create index if not exists idx_proc_purchase_requisition_line_mrp_suggestion
	on proc_purchase_requisition_line (source_mrp_suggestion_id);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('planning', '计划管理', 'MENU', null, '/planning/material-requirements', null, null, 650,
	'system', now(), 'system', now())
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select 'planning:material-requirement', '订单缺料与供给建议', 'MENU', parent.id,
	'/planning/material-requirements', null, null, 651, 'system', now(), 'system', now()
from sys_permission parent
where parent.code = 'planning'
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, '/planning/material-requirements',
	seed.http_method, seed.api_path, seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('planning:material-requirement:view', '查看订单缺料与供给建议', 'GET', '/api/admin/planning/material-requirement-runs/**', 652),
		('planning:material-requirement:calculate', '计算订单缺料与供给建议', 'POST', '/api/admin/planning/material-requirement-runs/**', 653),
		('planning:material-requirement:manage-suggestion', '确认或驳回缺料建议', 'PUT', '/api/admin/planning/material-requirement-suggestions/{id}/**', 654),
		('planning:material-requirement:convert-requisition', '缺料建议转采购请购', 'POST', '/api/admin/planning/material-requirement-suggestions/{id}/convert-requisition', 655),
		('planning:material-requirement:export', '导出订单缺料与供给建议', 'POST', '/api/admin/export-tasks', 656)
) as seed(code, name, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'planning:material-requirement'
on conflict (code) do nothing;

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code = 'planning' or p.code like 'planning:%'
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);
