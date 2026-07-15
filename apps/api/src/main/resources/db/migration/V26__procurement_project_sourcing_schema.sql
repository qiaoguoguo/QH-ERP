alter table proc_purchase_order_line drop constraint if exists uk_proc_purchase_order_line_material;

create table proc_purchase_requisition (
	id bigserial primary key,
	requisition_no varchar(64) not null,
	purchase_mode varchar(32) not null,
	project_id bigint,
	required_date date not null,
	status varchar(32) not null,
	purpose varchar(500) not null,
	approval_instance_id bigint,
	closed_reason varchar(200),
	client_request_id varchar(120),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	approved_by varchar(64),
	approved_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	closed_by varchar(64),
	closed_at timestamptz,
	version bigint not null default 0,
	constraint uk_proc_purchase_requisition_no unique (requisition_no),
	constraint uk_proc_purchase_requisition_client unique (created_by, client_request_id),
	constraint fk_proc_purchase_requisition_project foreign key (project_id) references sal_project (id),
	constraint fk_proc_purchase_requisition_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_proc_purchase_requisition_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	constraint ck_proc_purchase_requisition_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	),
	constraint ck_proc_purchase_requisition_status check (
		status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_ORDERED', 'ORDERED', 'CLOSED', 'CANCELLED')
	),
	constraint ck_proc_purchase_requisition_closed_reason check (
		closed_reason is null or length(closed_reason) between 1 and 200
	)
);

create table proc_purchase_requisition_line (
	id bigserial primary key,
	requisition_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	ordered_quantity numeric(18, 6) not null default 0,
	required_date date not null,
	purpose varchar(500) not null,
	suggested_supplier_id bigint,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_proc_purchase_requisition_line_header foreign key (requisition_id) references proc_purchase_requisition (id) on delete cascade,
	constraint fk_proc_purchase_requisition_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_purchase_requisition_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_proc_purchase_requisition_line_supplier foreign key (suggested_supplier_id) references mst_supplier (id),
	constraint uk_proc_purchase_requisition_line_no unique (requisition_id, line_no),
	constraint ck_proc_purchase_requisition_line_quantity check (quantity > 0 and ordered_quantity >= 0 and ordered_quantity <= quantity)
);

create table proc_purchase_inquiry (
	id bigserial primary key,
	inquiry_no varchar(64) not null,
	purchase_mode varchar(32) not null,
	project_id bigint,
	status varchar(32) not null,
	title varchar(120) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_proc_purchase_inquiry_no unique (inquiry_no),
	constraint fk_proc_purchase_inquiry_project foreign key (project_id) references sal_project (id),
	constraint ck_proc_purchase_inquiry_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	constraint ck_proc_purchase_inquiry_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	),
	constraint ck_proc_purchase_inquiry_status check (
		status in ('DRAFT', 'RELEASED', 'COMPLETED', 'AWARDED', 'CANCELLED')
	)
);

create table proc_purchase_inquiry_line (
	id bigserial primary key,
	inquiry_id bigint not null,
	line_no integer not null,
	requisition_line_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	required_date date,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_proc_purchase_inquiry_line_header foreign key (inquiry_id) references proc_purchase_inquiry (id) on delete cascade,
	constraint fk_proc_purchase_inquiry_line_requisition foreign key (requisition_line_id) references proc_purchase_requisition_line (id),
	constraint fk_proc_purchase_inquiry_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_purchase_inquiry_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_proc_purchase_inquiry_line_no unique (inquiry_id, line_no),
	constraint ck_proc_purchase_inquiry_line_quantity check (quantity > 0)
);

create table proc_supplier_quote (
	id bigserial primary key,
	quote_no varchar(64) not null,
	inquiry_id bigint not null,
	supplier_id bigint not null,
	status varchar(32) not null,
	valid_from date not null,
	valid_to date not null,
	currency varchar(8) not null default 'CNY',
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_proc_supplier_quote_no unique (quote_no),
	constraint fk_proc_supplier_quote_inquiry foreign key (inquiry_id) references proc_purchase_inquiry (id),
	constraint fk_proc_supplier_quote_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint ck_proc_supplier_quote_status check (
		status in ('DRAFT', 'VALID', 'SELECTED', 'REJECTED', 'EXPIRED', 'CANCELLED')
	),
	constraint ck_proc_supplier_quote_currency check (currency = 'CNY'),
	constraint ck_proc_supplier_quote_valid_range check (valid_from <= valid_to)
);

create table proc_supplier_quote_line (
	id bigserial primary key,
	quote_id bigint not null,
	inquiry_line_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	min_purchase_quantity numeric(18, 6) not null default 0,
	quantity numeric(18, 6) not null,
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	delivery_date date,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_proc_supplier_quote_line_quote foreign key (quote_id) references proc_supplier_quote (id) on delete cascade,
	constraint fk_proc_supplier_quote_line_inquiry foreign key (inquiry_line_id) references proc_purchase_inquiry_line (id),
	constraint fk_proc_supplier_quote_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_supplier_quote_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_proc_supplier_quote_line_no unique (quote_id, line_no),
	constraint ck_proc_supplier_quote_line_values check (
		quantity > 0 and min_purchase_quantity >= 0 and tax_rate >= 0
		and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
		and tax_excluded_amount >= 0 and tax_included_amount >= 0
	)
);

create table proc_price_agreement (
	id bigserial primary key,
	agreement_no varchar(64) not null,
	supplier_id bigint not null,
	purchase_mode varchar(32) not null,
	project_id bigint,
	status varchar(32) not null,
	currency varchar(8) not null default 'CNY',
	valid_from date not null,
	valid_to date not null,
	priority integer not null default 0,
	approval_instance_id bigint,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	activated_by varchar(64),
	activated_at timestamptz,
	version bigint not null default 0,
	constraint uk_proc_price_agreement_no unique (agreement_no),
	constraint fk_proc_price_agreement_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_proc_price_agreement_project foreign key (project_id) references sal_project (id),
	constraint fk_proc_price_agreement_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_proc_price_agreement_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	constraint ck_proc_price_agreement_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	),
	constraint ck_proc_price_agreement_status check (status in ('DRAFT', 'SUBMITTED', 'ACTIVE', 'DISABLED', 'EXPIRED', 'CANCELLED')),
	constraint ck_proc_price_agreement_currency check (currency = 'CNY'),
	constraint ck_proc_price_agreement_valid_range check (valid_from <= valid_to)
);

create table proc_price_agreement_line (
	id bigserial primary key,
	agreement_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	min_purchase_quantity numeric(18, 6) not null default 0,
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_proc_price_agreement_line_header foreign key (agreement_id) references proc_price_agreement (id) on delete cascade,
	constraint fk_proc_price_agreement_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_price_agreement_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_proc_price_agreement_line_no unique (agreement_id, line_no),
	constraint ck_proc_price_agreement_line_values check (
		min_purchase_quantity >= 0 and tax_rate >= 0
		and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
	)
);

alter table proc_purchase_order
	add column if not exists purchase_mode varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists currency varchar(8) not null default 'CNY',
	add column if not exists public_direct_reason varchar(200),
	add column if not exists exception_reason varchar(200),
	add column if not exists exception_approval_instance_id bigint,
	add column if not exists close_reason varchar(200);

alter table proc_purchase_order
	add constraint fk_proc_purchase_order_project foreign key (project_id) references sal_project (id),
	add constraint fk_proc_purchase_order_exception_approval foreign key (exception_approval_instance_id) references platform_approval_instance (id),
	add constraint ck_proc_purchase_order_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	add constraint ck_proc_purchase_order_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	),
	add constraint ck_proc_purchase_order_currency check (currency = 'CNY');

alter table proc_purchase_order_line
	add column if not exists source_requisition_line_id bigint,
	add column if not exists source_quote_line_id bigint,
	add column if not exists price_agreement_line_id bigint,
	add column if not exists price_source_type varchar(32) not null default 'MANUAL',
	add column if not exists tax_rate numeric(9, 6) not null default 0,
	add column if not exists tax_excluded_unit_price numeric(18, 6),
	add column if not exists tax_included_unit_price numeric(18, 6),
	add column if not exists tax_excluded_amount numeric(18, 2),
	add column if not exists tax_included_amount numeric(18, 2);

update proc_purchase_order_line
set tax_excluded_unit_price = coalesce(tax_excluded_unit_price, unit_price),
    tax_included_unit_price = coalesce(tax_included_unit_price, unit_price),
    tax_excluded_amount = coalesce(tax_excluded_amount, round(quantity * unit_price, 2)),
    tax_included_amount = coalesce(tax_included_amount, round(quantity * unit_price, 2));

alter table proc_purchase_order_line
	alter column tax_excluded_unit_price set not null,
	alter column tax_included_unit_price set not null,
	alter column tax_excluded_amount set not null,
	alter column tax_included_amount set not null,
	add constraint fk_proc_purchase_order_line_requisition foreign key (source_requisition_line_id) references proc_purchase_requisition_line (id),
	add constraint fk_proc_purchase_order_line_quote foreign key (source_quote_line_id) references proc_supplier_quote_line (id),
	add constraint fk_proc_purchase_order_line_agreement foreign key (price_agreement_line_id) references proc_price_agreement_line (id),
	add constraint ck_proc_purchase_order_line_price_source check (
		price_source_type in (
			'MANUAL', 'REQUISITION_APPROVED', 'QUOTE_SELECTION', 'PUBLIC_DIRECT', 'AGREEMENT'
		)
	),
	add constraint ck_proc_purchase_order_line_tax_values check (
		tax_rate >= 0 and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
		and tax_excluded_amount >= 0 and tax_included_amount >= 0
	);

create unique index uk_proc_purchase_order_line_source_combo
	on proc_purchase_order_line (
		order_id, material_id, coalesce(source_requisition_line_id, 0),
		coalesce(source_quote_line_id, 0), coalesce(price_agreement_line_id, 0)
	);

create table proc_purchase_order_schedule (
	id bigserial primary key,
	order_line_id bigint not null,
	line_no integer not null,
	planned_date date not null,
	planned_quantity numeric(18, 6) not null,
	received_quantity numeric(18, 6) not null default 0,
	closed_reason varchar(200),
	status varchar(32) not null default 'PLANNED',
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_proc_purchase_order_schedule_line foreign key (order_line_id) references proc_purchase_order_line (id) on delete cascade,
	constraint uk_proc_purchase_order_schedule_no unique (order_line_id, line_no),
	constraint ck_proc_purchase_order_schedule_status check (
		status in ('PLANNED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED')
	),
	constraint ck_proc_purchase_order_schedule_quantity check (
		planned_quantity > 0 and received_quantity >= 0 and received_quantity <= planned_quantity
	)
);

create table proc_purchase_order_change (
	id bigserial primary key,
	order_id bigint not null,
	change_type varchar(32) not null,
	reason varchar(200) not null,
	payload jsonb not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_proc_purchase_order_change_order foreign key (order_id) references proc_purchase_order (id),
	constraint ck_proc_purchase_order_change_type check (
		change_type in ('QUANTITY', 'PRICE', 'SCHEDULE', 'SUPPLIER', 'CLOSE_REMAINING')
	)
);

create table proc_purchase_price_selection (
	id bigserial primary key,
	order_line_id bigint not null,
	selection_type varchar(32) not null,
	source_quote_line_id bigint,
	price_agreement_line_id bigint,
	reason varchar(200),
	requires_exception_approval boolean not null default false,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_proc_purchase_price_selection_order_line foreign key (order_line_id) references proc_purchase_order_line (id) on delete cascade,
	constraint fk_proc_purchase_price_selection_quote foreign key (source_quote_line_id) references proc_supplier_quote_line (id),
	constraint fk_proc_purchase_price_selection_agreement foreign key (price_agreement_line_id) references proc_price_agreement_line (id),
	constraint ck_proc_purchase_price_selection_type check (
		selection_type in ('LOWEST_QUOTE', 'NON_LOWEST_QUOTE', 'PRICE_AGREEMENT', 'MANUAL_EXCEPTION')
	)
);

alter table proc_purchase_receipt_line
	add column if not exists purchase_mode varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists schedule_id bigint,
	add column if not exists cost_layer_id bigint,
	add column if not exists value_movement_id bigint;

alter table proc_purchase_receipt_line
	add constraint fk_proc_purchase_receipt_line_project foreign key (project_id) references sal_project (id),
	add constraint fk_proc_purchase_receipt_line_schedule foreign key (schedule_id) references proc_purchase_order_schedule (id),
	add constraint fk_proc_purchase_receipt_line_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_proc_purchase_receipt_line_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_proc_purchase_receipt_line_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	add constraint ck_proc_purchase_receipt_line_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	);

alter table proc_purchase_return
	add column if not exists purchase_mode varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint;

alter table proc_purchase_return
	add constraint fk_proc_purchase_return_project foreign key (project_id) references sal_project (id),
	add constraint ck_proc_purchase_return_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	add constraint ck_proc_purchase_return_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	);

alter table proc_purchase_return_line
	add column if not exists purchase_mode varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists source_cost_layer_id bigint,
	add column if not exists source_value_movement_id bigint,
	add column if not exists value_movement_id bigint;

alter table proc_purchase_return_line
	add constraint fk_proc_purchase_return_line_project foreign key (project_id) references sal_project (id),
	add constraint fk_proc_purchase_return_line_source_cost_layer foreign key (source_cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_proc_purchase_return_line_source_value foreign key (source_value_movement_id) references inv_value_movement (id),
	add constraint fk_proc_purchase_return_line_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_proc_purchase_return_line_mode check (purchase_mode in ('PUBLIC', 'PROJECT')),
	add constraint ck_proc_purchase_return_line_project check (
		(purchase_mode = 'PUBLIC' and project_id is null)
		or (purchase_mode = 'PROJECT' and project_id is not null)
	);

create or replace view proc_effective_purchase_supply as
select
	o.id as order_id,
	o.order_no,
	ol.id as order_line_id,
	s.id as schedule_id,
	o.purchase_mode,
	o.project_id,
	ol.material_id,
	ol.unit_id,
	coalesce(s.planned_date, ol.expected_arrival_date, o.expected_arrival_date) as expected_arrival_date,
	coalesce(s.planned_quantity - s.received_quantity, ol.quantity - ol.received_quantity) as remaining_quantity,
	o.status as order_status,
	coalesce(s.status, 'PLANNED') as schedule_status,
	'PURCHASE_ORDER' as source_type
from proc_purchase_order o
join proc_purchase_order_line ol on ol.order_id = o.id
left join proc_purchase_order_schedule s on s.order_line_id = ol.id
where o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
and coalesce(s.status, 'PLANNED') in ('PLANNED', 'PARTIALLY_RECEIVED')
and coalesce(s.planned_quantity - s.received_quantity, ol.quantity - ol.received_quantity) > 0;

create table proc_purchase_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint not null,
	resource_version bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(64) not null,
	result_resource_id bigint not null,
	result_version bigint,
	created_at timestamptz not null default now()
);

create unique index uk_proc_purchase_action_idempotency
	on proc_purchase_action_idempotency (operator_user_id, action, resource_type, resource_id, idempotency_key);

create index idx_proc_purchase_requisition_status on proc_purchase_requisition (status, updated_at desc, id desc);
create index idx_proc_purchase_requisition_project on proc_purchase_requisition (purchase_mode, project_id);
create index idx_proc_purchase_requisition_line_material on proc_purchase_requisition_line (material_id);
create index idx_proc_purchase_order_project on proc_purchase_order (purchase_mode, project_id);
create index idx_proc_purchase_order_line_requisition on proc_purchase_order_line (source_requisition_line_id);
create index idx_proc_purchase_order_schedule_line on proc_purchase_order_schedule (order_line_id, status);
create index idx_proc_purchase_receipt_line_value on proc_purchase_receipt_line (value_movement_id);
create index idx_proc_purchase_return_line_value on proc_purchase_return_line (value_movement_id);

alter table platform_business_attachment drop constraint if exists ck_platform_business_attachment_object;
alter table platform_business_attachment add constraint ck_platform_business_attachment_object check (
	object_type in (
		'SALES_PROJECT_CONTRACT',
		'BOM_ENGINEERING_CHANGE',
		'INVENTORY_OWNERSHIP_CONVERSION',
		'INVENTORY_STOCKTAKE',
		'INVENTORY_VALUATION_ADJUSTMENT',
		'PROCUREMENT_REQUISITION',
		'PROCUREMENT_PRICE_AGREEMENT',
		'PROCUREMENT_ORDER'
	)
);

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
select seed.scene_code, seed.name, seed.business_object_type, seed.action_code, 1, 'ENABLED'
from (
	values
		('PROCUREMENT_REQUISITION_APPROVAL', '采购请购固定审批', 'PROCUREMENT_REQUISITION', 'APPROVE'),
		('PROCUREMENT_PRICE_AGREEMENT_ACTIVATION', '采购价格协议激活审批', 'PROCUREMENT_PRICE_AGREEMENT', 'ACTIVATE'),
		('PROCUREMENT_ORDER_EXCEPTION_CONFIRM', '采购订单例外确认审批', 'PROCUREMENT_ORDER', 'CONFIRM')
) as seed(scene_code, name, business_object_type, action_code)
where not exists (
	select 1 from platform_approval_definition d where d.scene_code = seed.scene_code
);

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select d.id, 1, seed.step_name, seed.permission_code
from (
	values
		('PROCUREMENT_REQUISITION_APPROVAL', '请购审批', 'procurement:requisition:approve'),
		('PROCUREMENT_PRICE_AGREEMENT_ACTIVATION', '价格协议激活审批', 'procurement:price-agreement:approve'),
		('PROCUREMENT_ORDER_EXCEPTION_CONFIRM', '订单例外确认审批', 'procurement:order:exception-approve')
) as seed(scene_code, step_name, permission_code)
join platform_approval_definition d on d.scene_code = seed.scene_code
where not exists (
	select 1 from platform_approval_definition_step s where s.definition_id = d.id and s.step_no = 1
);

insert into platform_print_template (
	template_code, scene_code, name, object_type, template_version, status
)
select 'PROCUREMENT_ORDER_V1', 'PROCUREMENT_ORDER_PRINT', '采购订单固定打印', 'PROCUREMENT_ORDER', 1, 'ENABLED'
where not exists (
	select 1 from platform_print_template where template_code = 'PROCUREMENT_ORDER_V1'
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select 'platform:document-task:create', '创建文件任务', 'ACTION', parent.id, '/platform/document-tasks',
	'POST', '/api/admin/export-tasks', 940, 'system', now(), 'system', now()
from sys_permission parent
where parent.code = 'platform'
and not exists (
	select 1 from sys_permission p where p.code = 'platform:document-task:create'
);

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code = 'platform:document-task:create'
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_pattern,
	seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('procurement:requisition:view', '查看采购请购', '/procurement/requisitions', 'GET', '/api/admin/procurement/requisitions/**', 366),
		('procurement:requisition:create', '创建采购请购', '/procurement/requisitions', 'POST', '/api/admin/procurement/requisitions', 367),
		('procurement:requisition:update', '更新采购请购', '/procurement/requisitions', 'PUT', '/api/admin/procurement/requisitions/{id}', 368),
		('procurement:requisition:submit', '提交采购请购审批', '/procurement/requisitions', 'POST', '/api/admin/procurement/requisitions/{id}/submit-approval', 369),
		('procurement:requisition:approve', '审批采购请购', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 370),
		('procurement:requisition:cancel', '取消采购请购', '/procurement/requisitions', 'PUT', '/api/admin/procurement/requisitions/{id}/cancel', 370),
		('procurement:requisition:close', '结案采购请购', '/procurement/requisitions', 'PUT', '/api/admin/procurement/requisitions/{id}/close', 370),
		('procurement:inquiry:view', '查看采购询价', '/procurement/inquiries', 'GET', '/api/admin/procurement/inquiries/**', 371),
		('procurement:inquiry:create', '创建采购询价', '/procurement/inquiries', 'POST', '/api/admin/procurement/inquiries', 372),
		('procurement:inquiry:update', '更新采购询价', '/procurement/inquiries', 'PUT', '/api/admin/procurement/inquiries/{id}', 373),
		('procurement:inquiry:release', '发布采购询价', '/procurement/inquiries', 'PUT', '/api/admin/procurement/inquiries/{id}/release', 373),
		('procurement:inquiry:complete', '完成采购询价', '/procurement/inquiries', 'PUT', '/api/admin/procurement/inquiries/{id}/complete', 373),
		('procurement:inquiry:cancel', '取消采购询价', '/procurement/inquiries', 'PUT', '/api/admin/procurement/inquiries/{id}/cancel', 373),
		('procurement:quote:view', '查看供应商报价', '/procurement/quotes', 'GET', '/api/admin/procurement/inquiries/{id}/quotes/**', 374),
		('procurement:quote:create', '创建供应商报价', '/procurement/quotes', 'POST', '/api/admin/procurement/inquiries/{id}/quotes', 375),
		('procurement:quote:import', '导入供应商报价', '/procurement/quotes', 'POST', '/api/admin/procurement/inquiries/{id}/quote-imports', 376),
		('procurement:quote:update', '更新供应商报价', '/procurement/quotes', 'PUT', '/api/admin/procurement/inquiries/{id}/quotes/{quoteId}', 376),
		('procurement:quote:export', '导出供应商报价', '/procurement/quotes', 'POST', '/api/admin/export-tasks', 376),
		('procurement:quote:select', '选择供应商报价', '/procurement/quotes', 'PUT', '/api/admin/procurement/inquiries/{id}/quotes/{quoteId}/select', 376),
		('procurement:quote:cancel', '取消供应商报价', '/procurement/quotes', 'PUT', '/api/admin/procurement/inquiries/{id}/quotes/{quoteId}/cancel', 376),
		('procurement:price-agreement:view', '查看采购价格协议', '/procurement/price-agreements', 'GET', '/api/admin/procurement/price-agreements/**', 377),
		('procurement:price-agreement:create', '创建采购价格协议', '/procurement/price-agreements', 'POST', '/api/admin/procurement/price-agreements', 378),
		('procurement:price-agreement:update', '更新采购价格协议', '/procurement/price-agreements', 'PUT', '/api/admin/procurement/price-agreements/{id}', 379),
		('procurement:price-agreement:submit', '提交价格协议审批', '/procurement/price-agreements', 'POST', '/api/admin/procurement/price-agreements/{id}/submit-activation', 380),
		('procurement:price-agreement:approve', '审批价格协议激活', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 381),
		('procurement:price-agreement:disable', '停用采购价格协议', '/procurement/price-agreements', 'PUT', '/api/admin/procurement/price-agreements/{id}/disable', 381),
		('procurement:price-agreement:cancel', '取消采购价格协议', '/procurement/price-agreements', 'PUT', '/api/admin/procurement/price-agreements/{id}/cancel', 381),
		('procurement:order:public-direct', '公共直采', '/procurement/orders', 'POST', '/api/admin/procurement/orders', 382),
		('procurement:order:exception-submit', '提交采购订单例外审批', '/procurement/orders', 'POST', '/api/admin/procurement/orders/{id}/submit-exception', 383),
		('procurement:order:exception-approve', '审批采购订单例外', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 384),
		('procurement:order:change', '变更采购订单', '/procurement/orders', 'POST', '/api/admin/procurement/orders/{id}/changes', 384),
		('procurement:supply:view', '查看有效采购供给', '/procurement/effective-supplies', 'GET', '/api/admin/procurement/effective-supplies/**', 385),
		('procurement:supply:export', '导出有效采购供给', '/procurement/effective-supplies', 'POST', '/api/admin/export-tasks', 385),
		('procurement:document:export', '导出采购单据', '/platform/tasks', 'POST', '/api/admin/export-tasks', 386),
		('procurement:document:import', '导入采购单据', '/platform/tasks', 'POST', '/api/admin/procurement/**/imports', 387),
		('procurement:order:print', '打印采购订单', '/procurement/orders', 'POST', '/api/admin/print-tasks', 388)
) as seed(code, name, route_path, http_method, api_pattern, sort_order)
left join sys_permission parent on parent.code = 'procurement'
where not exists (select 1 from sys_permission p where p.code = seed.code);

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code like 'procurement:%'
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);
