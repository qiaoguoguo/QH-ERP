create table prj_cost_calculation (
	id bigserial primary key,
	project_id bigint not null,
	calculation_no varchar(64) not null,
	cutoff_date date not null,
	status varchar(32) not null,
	is_current boolean not null default false,
	source_fingerprint varchar(64) not null,
	project_cost_total numeric(18, 2) not null default 0,
	wip_cost numeric(18, 2) not null default 0,
	finished_cost numeric(18, 2) not null default 0,
	delivered_cost numeric(18, 2) not null default 0,
	direct_project_cost numeric(18, 2) not null default 0,
	shipment_revenue numeric(18, 2) not null default 0,
	invoice_revenue numeric(18, 2) not null default 0,
	target_revenue numeric(18, 2) not null default 0,
	shipment_gross_margin numeric(18, 2),
	invoice_gross_margin numeric(18, 2),
	target_gross_margin numeric(18, 2),
	shipment_gross_margin_rate numeric(18, 6),
	invoice_gross_margin_rate numeric(18, 6),
	target_gross_margin_rate numeric(18, 6),
	margin_completeness varchar(32) not null,
	completeness_reason varchar(500),
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_prj_cost_calculation_no unique (calculation_no),
	constraint fk_prj_cost_calculation_project foreign key (project_id) references sal_project (id),
	constraint ck_prj_cost_calculation_status check (status in ('DRAFT', 'CALCULATED', 'CONFIRMED', 'CANCELLED')),
	constraint ck_prj_cost_calculation_completeness check (margin_completeness in ('COMPLETE', 'INCOMPLETE')),
	constraint ck_prj_cost_calculation_amounts check (
		project_cost_total >= 0 and wip_cost >= 0 and finished_cost >= 0
		and delivered_cost >= 0 and direct_project_cost >= 0
		and shipment_revenue >= 0 and invoice_revenue >= 0 and target_revenue >= 0
	)
);

create unique index uk_prj_cost_calculation_active_project
	on prj_cost_calculation (project_id)
	where status in ('DRAFT', 'CALCULATED');

create unique index uk_prj_cost_calculation_current_project
	on prj_cost_calculation (project_id)
	where is_current = true;

create index idx_prj_cost_calculation_project_date
	on prj_cost_calculation (project_id, cutoff_date desc, id desc);

create table prj_cost_source_line (
	id bigserial primary key,
	calculation_id bigint not null,
	project_id bigint not null,
	cost_category varchar(64) not null,
	cost_stage varchar(32) not null,
	entry_type varchar(64) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint,
	source_no varchar(120),
	source_status varchar(64),
	business_date date,
	quantity numeric(18, 6),
	unit_cost numeric(18, 6),
	source_amount numeric(18, 2),
	calculated_amount numeric(18, 2) not null,
	source_fingerprint varchar(64) not null,
	source_restricted boolean not null default false,
	created_at timestamptz not null default now(),
	constraint fk_prj_cost_source_line_calculation foreign key (calculation_id) references prj_cost_calculation (id) on delete cascade,
	constraint fk_prj_cost_source_line_project foreign key (project_id) references sal_project (id),
	constraint ck_prj_cost_source_line_category check (
		cost_category in ('MATERIAL', 'LABOR', 'OUTSOURCING', 'MANUFACTURING_OVERHEAD', 'PROJECT_EXPENSE', 'ADJUSTMENT')
	),
	constraint ck_prj_cost_source_line_stage check (cost_stage in ('WIP', 'FINISHED', 'DELIVERED', 'DIRECT_PROJECT')),
	constraint ck_prj_cost_source_line_entry check (
		entry_type in ('SOURCE_TO_WIP', 'WIP_TO_FINISHED', 'FINISHED_TO_DELIVERED', 'PROJECT_DIRECT', 'PROJECT_ADJUSTMENT', 'COST_VARIANCE')
	),
	constraint ck_prj_cost_source_line_status check (
		source_status is null or source_status in ('ACTUAL', 'PROVISIONAL', 'UNPRICED', 'ADJUSTED', 'RESTRICTED', 'EXCLUDED')
	)
);

create unique index uk_prj_cost_source_line_source
	on prj_cost_source_line (
		calculation_id,
		source_type,
		source_id,
		coalesce(source_line_id, 0),
		cost_category,
		entry_type
	);

create index idx_prj_cost_source_line_calculation
	on prj_cost_source_line (calculation_id, id);

create table prj_cost_entry (
	id bigserial primary key,
	calculation_id bigint not null,
	entry_type varchar(64) not null,
	cost_category varchar(64),
	cost_stage varchar(32) not null,
	direction varchar(32) not null,
	amount numeric(18, 2) not null,
	description varchar(255),
	created_at timestamptz not null default now(),
	constraint fk_prj_cost_entry_calculation foreign key (calculation_id) references prj_cost_calculation (id) on delete cascade,
	constraint ck_prj_cost_entry_type check (
		entry_type in ('SOURCE_TO_WIP', 'WIP_TO_FINISHED', 'FINISHED_TO_DELIVERED', 'PROJECT_DIRECT', 'PROJECT_ADJUSTMENT', 'COST_VARIANCE')
	),
	constraint ck_prj_cost_entry_stage check (cost_stage in ('WIP', 'FINISHED', 'DELIVERED', 'DIRECT_PROJECT')),
	constraint ck_prj_cost_entry_direction check (direction in ('INCREASE', 'DECREASE')),
	constraint ck_prj_cost_entry_amount check (amount >= 0)
);

create index idx_prj_cost_entry_calculation
	on prj_cost_entry (calculation_id, id);

create table prj_cost_entry_line (
	id bigserial primary key,
	entry_id bigint not null,
	source_line_id bigint,
	amount numeric(18, 2) not null,
	description varchar(255),
	created_at timestamptz not null default now(),
	constraint fk_prj_cost_entry_line_entry foreign key (entry_id) references prj_cost_entry (id) on delete cascade,
	constraint fk_prj_cost_entry_line_source foreign key (source_line_id) references prj_cost_source_line (id),
	constraint ck_prj_cost_entry_line_amount check (amount >= 0)
);

create table prj_cost_adjustment (
	id bigserial primary key,
	adjustment_no varchar(64) not null,
	adjustment_type varchar(64) not null,
	business_date date not null,
	status varchar(32) not null,
	reason varchar(500) not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	approval_instance_id bigint,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	submitted_by varchar(64),
	submitted_at timestamptz,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_prj_cost_adjustment_no unique (adjustment_no),
	constraint fk_prj_cost_adjustment_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_prj_cost_adjustment_type check (adjustment_type in ('PROJECT_ADJUSTMENT', 'PUBLIC_EXPENSE_ALLOCATION', 'VARIANCE_SETTLEMENT')),
	constraint ck_prj_cost_adjustment_status check (status in ('DRAFT', 'SUBMITTED', 'CONFIRMED', 'REJECTED', 'CANCELLED'))
);

create unique index uk_prj_cost_adjustment_idempotency
	on prj_cost_adjustment (created_by, idempotency_key);

create table prj_cost_adjustment_line (
	id bigserial primary key,
	adjustment_id bigint not null,
	line_no integer not null,
	project_id bigint not null,
	cost_category varchar(64) not null,
	cost_stage varchar(32) not null,
	direction varchar(32) not null,
	amount numeric(18, 2) not null,
	public_expense_line_id bigint,
	reason varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_prj_cost_adjustment_line_adjustment foreign key (adjustment_id) references prj_cost_adjustment (id) on delete cascade,
	constraint fk_prj_cost_adjustment_line_project foreign key (project_id) references sal_project (id),
	constraint fk_prj_cost_adjustment_line_expense_line foreign key (public_expense_line_id) references fin_expense_line (id),
	constraint uk_prj_cost_adjustment_line_no unique (adjustment_id, line_no),
	constraint ck_prj_cost_adjustment_line_category check (
		cost_category in ('MATERIAL', 'LABOR', 'OUTSOURCING', 'MANUFACTURING_OVERHEAD', 'PROJECT_EXPENSE', 'ADJUSTMENT')
	),
	constraint ck_prj_cost_adjustment_line_stage check (cost_stage in ('WIP', 'FINISHED', 'DELIVERED', 'DIRECT_PROJECT')),
	constraint ck_prj_cost_adjustment_line_direction check (direction in ('INCREASE', 'DECREASE')),
	constraint ck_prj_cost_adjustment_line_amount check (amount > 0)
);

create index idx_prj_cost_adjustment_line_project
	on prj_cost_adjustment_line (project_id, adjustment_id);

create index idx_prj_cost_adjustment_line_expense
	on prj_cost_adjustment_line (public_expense_line_id);

create table prj_cost_variance (
	id bigserial primary key,
	calculation_id bigint not null,
	project_id bigint not null,
	variance_type varchar(64) not null,
	severity varchar(32) not null,
	status varchar(32) not null,
	source_restricted boolean not null default false,
	variance_amount numeric(18, 2),
	description varchar(500) not null,
	source_type varchar(64),
	source_id bigint,
	source_line_id bigint,
	cost_category varchar(64),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_prj_cost_variance_calculation foreign key (calculation_id) references prj_cost_calculation (id) on delete cascade,
	constraint fk_prj_cost_variance_project foreign key (project_id) references sal_project (id),
	constraint ck_prj_cost_variance_severity check (severity in ('INFO', 'WARNING', 'BLOCKING')),
	constraint ck_prj_cost_variance_status check (status in ('OPEN', 'RESOLVED', 'SUPERSEDED')),
	constraint ck_prj_cost_variance_amount check (variance_amount is null or variance_amount >= 0)
);

create index idx_prj_cost_variance_global
	on prj_cost_variance (project_id, severity, variance_type, status, source_restricted, id desc);

create index idx_prj_cost_variance_calculation
	on prj_cost_variance (calculation_id, id);

create table prj_cost_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_id bigint not null,
	created_at timestamptz not null default now(),
	constraint fk_prj_cost_action_idempotency_user foreign key (operator_user_id) references sys_user (id)
);

create unique index uk_prj_cost_action_idempotency
	on prj_cost_action_idempotency (operator_user_id, action, resource_type, resource_id, idempotency_key);

insert into sys_role (code, name, description, status, sort_order, created_by, created_at, updated_by, updated_at)
values ('SYSTEM_ADMIN', '系统管理员', null, 'ENABLED', 0, 'system', now(), 'system', now())
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('cost', '成本管理', 'MENU', null, '/cost/records', null, null, 500, 'system', now(), 'system', now())
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, seed.type, parent.id, seed.route_path, seed.api_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('cost:project-cost', '项目成本核算', 'MENU', 'cost', '/cost/project-costs', null, null, 510),
		('cost:project-cost-adjustment', '项目成本调整', 'MENU', 'cost', '/cost/project-cost-adjustments', null, null, 520)
) as seed(code, name, type, parent_code, route_path, api_method, api_path, sort_order)
join sys_permission parent on parent.code = seed.parent_code
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    api_method = excluded.api_method,
    api_path = excluded.api_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, seed.type, parent.id, seed.route_path, seed.api_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('cost:project-cost:view', '查看项目成本', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'GET', '/api/admin/cost/project-costs/**', 511),
		('cost:project-cost:source-view', '查看项目成本来源', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'GET', '/api/admin/cost/project-cost-calculations/{id}/sources', 512),
		('cost:project-cost:amount-view', '查看项目成本金额', 'ACTION', 'cost:project-cost', '/cost/project-costs', null, null, 513),
		('cost:project-cost:calculate', '计算项目成本', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'POST', '/api/admin/cost/project-costs/projects/{projectId}/calculations', 514),
		('cost:project-cost:confirm', '确认项目成本', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'PUT', '/api/admin/cost/project-cost-calculations/{id}/confirm', 515),
		('cost:project-cost:cancel', '取消项目成本计算', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'PUT', '/api/admin/cost/project-cost-calculations/{id}/cancel', 516),
		('cost:project-cost-adjustment:view', '查看项目成本调整', 'ACTION', 'cost:project-cost-adjustment', '/cost/project-cost-adjustments', 'GET', '/api/admin/cost/project-cost-adjustments/**', 521),
		('cost:project-cost-adjustment:create', '创建项目成本调整', 'ACTION', 'cost:project-cost-adjustment', '/cost/project-cost-adjustments', 'POST', '/api/admin/cost/project-cost-adjustments', 522),
		('cost:project-cost-adjustment:update', '更新项目成本调整', 'ACTION', 'cost:project-cost-adjustment', '/cost/project-cost-adjustments', 'PUT', '/api/admin/cost/project-cost-adjustments/{id}', 523),
		('cost:project-cost-adjustment:submit', '提交项目成本调整审批', 'ACTION', 'cost:project-cost-adjustment', '/cost/project-cost-adjustments', 'PUT', '/api/admin/cost/project-cost-adjustments/{id}/submit', 524),
		('cost:project-cost-adjustment:cancel', '取消项目成本调整', 'ACTION', 'cost:project-cost-adjustment', '/cost/project-cost-adjustments', 'PUT', '/api/admin/cost/project-cost-adjustments/{id}/cancel', 525),
		('cost:project-cost-variance:view', '查看项目成本差异', 'ACTION', 'cost:project-cost', '/cost/project-costs', 'GET', '/api/admin/cost/project-cost-variances/**', 526)
) as seed(code, name, type, parent_code, route_path, api_method, api_path, sort_order)
join sys_permission parent on parent.code = seed.parent_code
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
	'cost:project-cost:view',
	'cost:project-cost:source-view',
	'cost:project-cost:amount-view',
	'cost:project-cost:calculate',
	'cost:project-cost:confirm',
	'cost:project-cost:cancel',
	'cost:project-cost-adjustment:view',
	'cost:project-cost-adjustment:create',
	'cost:project-cost-adjustment:update',
	'cost:project-cost-adjustment:submit',
	'cost:project-cost-adjustment:cancel',
	'cost:project-cost-variance:view'
)
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1
	from sys_role_permission rp
	where rp.role_id = r.id
	and rp.permission_id = p.id
);

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
values ('PROJECT_COST_ADJUSTMENT_CONFIRM', '项目成本调整确认审批', 'PROJECT_COST_ADJUSTMENT', 'CONFIRM', 1, 'ENABLED')
on conflict (scene_code) do update
set name = excluded.name,
    business_object_type = excluded.business_object_type,
    action_code = excluded.action_code,
    definition_version = excluded.definition_version,
    status = excluded.status,
    updated_at = now();

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select d.id, 1, '固定审批', 'cost:project-cost-adjustment:submit'
from platform_approval_definition d
where d.scene_code = 'PROJECT_COST_ADJUSTMENT_CONFIRM'
on conflict (definition_id, step_no) do update
set name = excluded.name,
    candidate_permission_code = excluded.candidate_permission_code;
