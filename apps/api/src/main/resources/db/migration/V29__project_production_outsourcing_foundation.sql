alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;

alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE', 'PRODUCTION_RECEIPT', 'PURCHASE_RECEIPT', 'SALES_SHIPMENT',
		'SALES_RETURN_IN', 'PURCHASE_RETURN_OUT', 'PRODUCTION_MATERIAL_RETURN_IN',
		'PRODUCTION_MATERIAL_SUPPLEMENT_OUT', 'QUALITY_STATUS_TRANSFER', 'BUSINESS_REVERSAL',
		'WAREHOUSE_TRANSFER_OUT', 'WAREHOUSE_TRANSFER_IN', 'OWNERSHIP_CONVERSION_OUT',
		'OWNERSHIP_CONVERSION_IN', 'STOCKTAKE_VARIANCE_IN', 'STOCKTAKE_VARIANCE_OUT',
		'VALUATION_ADJUSTMENT', 'OUTSOURCING_ISSUE', 'OUTSOURCING_RECEIPT'
	));

alter table mfg_work_order
	alter column bom_id drop not null,
	alter column issue_warehouse_id drop not null,
	alter column receipt_warehouse_id drop not null,
	alter column planned_start_date drop not null,
	alter column planned_finish_date drop not null,
	add column if not exists ownership_type varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists source_mrp_run_id bigint,
	add column if not exists source_mrp_requirement_line_id bigint,
	add column if not exists source_mrp_suggestion_id bigint;

alter table mfg_work_order
	add constraint fk_mfg_work_order_project foreign key (project_id) references sal_project (id),
	add constraint fk_mfg_work_order_mrp_run foreign key (source_mrp_run_id) references mrp_calculation_run (id),
	add constraint fk_mfg_work_order_mrp_requirement foreign key (source_mrp_requirement_line_id) references mrp_requirement_line (id),
	add constraint fk_mfg_work_order_mrp_suggestion foreign key (source_mrp_suggestion_id) references mrp_suggestion (id),
	add constraint ck_mfg_work_order_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_mfg_work_order_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create unique index uk_mfg_work_order_mrp_suggestion
	on mfg_work_order (source_mrp_suggestion_id)
	where source_mrp_suggestion_id is not null;

create index idx_mfg_work_order_project_status
	on mfg_work_order (ownership_type, project_id, status, planned_finish_date desc, id desc);

create index idx_mfg_work_order_mrp_run
	on mfg_work_order (source_mrp_run_id);

alter table mfg_material_issue_line
	add column if not exists ownership_type varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists cost_layer_id bigint,
	add column if not exists value_movement_id bigint;

alter table mfg_material_issue_line
	add constraint fk_mfg_material_issue_line_project foreign key (project_id) references sal_project (id),
	add constraint fk_mfg_material_issue_line_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_mfg_material_issue_line_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_mfg_material_issue_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_mfg_material_issue_line_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create index idx_mfg_material_issue_line_project
	on mfg_material_issue_line (ownership_type, project_id, material_id);

alter table mfg_completion_receipt
	add column if not exists ownership_type varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists value_movement_id bigint;

alter table mfg_completion_receipt
	add constraint fk_mfg_completion_receipt_project foreign key (project_id) references sal_project (id),
	add constraint fk_mfg_completion_receipt_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_mfg_completion_receipt_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_mfg_completion_receipt_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create index idx_mfg_completion_receipt_project
	on mfg_completion_receipt (ownership_type, project_id, receipt_warehouse_id);

alter table mfg_material_return_line
	add column if not exists ownership_type varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists cost_layer_id bigint,
	add column if not exists source_value_movement_id bigint,
	add column if not exists value_movement_id bigint;

alter table mfg_material_return_line
	add constraint fk_mfg_material_return_line_project foreign key (project_id) references sal_project (id),
	add constraint fk_mfg_material_return_line_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_mfg_material_return_line_source_value foreign key (source_value_movement_id) references inv_value_movement (id),
	add constraint fk_mfg_material_return_line_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_mfg_material_return_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_mfg_material_return_line_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

alter table mfg_material_supplement_line
	add column if not exists ownership_type varchar(32) not null default 'PUBLIC',
	add column if not exists project_id bigint,
	add column if not exists cost_layer_id bigint,
	add column if not exists value_movement_id bigint;

alter table mfg_material_supplement_line
	add constraint fk_mfg_material_supplement_line_project foreign key (project_id) references sal_project (id),
	add constraint fk_mfg_material_supplement_line_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_mfg_material_supplement_line_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint ck_mfg_material_supplement_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_mfg_material_supplement_line_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create table mfg_outsourcing_order (
	id bigserial primary key,
	outsourcing_order_no varchar(64) not null,
	supplier_id bigint,
	product_material_id bigint not null,
	bom_id bigint,
	planned_quantity numeric(18, 6) not null,
	issued_quantity numeric(18, 6) not null default 0,
	received_quantity numeric(18, 6) not null default 0,
	issue_warehouse_id bigint,
	receipt_warehouse_id bigint,
	planned_issue_date date,
	planned_receipt_date date,
	status varchar(32) not null,
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	provisional_unit_cost numeric(18, 6),
	source_mrp_run_id bigint,
	source_mrp_requirement_line_id bigint,
	source_mrp_suggestion_id bigint,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	released_by varchar(64),
	released_at timestamptz,
	closed_by varchar(64),
	closed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_outsourcing_order_no unique (outsourcing_order_no),
	constraint fk_mfg_outsourcing_order_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_mfg_outsourcing_order_product foreign key (product_material_id) references mst_material (id),
	constraint fk_mfg_outsourcing_order_bom foreign key (bom_id) references mfg_bom (id),
	constraint fk_mfg_outsourcing_order_issue_warehouse foreign key (issue_warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_outsourcing_order_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_outsourcing_order_project foreign key (project_id) references sal_project (id),
	constraint fk_mfg_outsourcing_order_mrp_run foreign key (source_mrp_run_id) references mrp_calculation_run (id),
	constraint fk_mfg_outsourcing_order_mrp_requirement foreign key (source_mrp_requirement_line_id) references mrp_requirement_line (id),
	constraint fk_mfg_outsourcing_order_mrp_suggestion foreign key (source_mrp_suggestion_id) references mrp_suggestion (id),
	constraint ck_mfg_outsourcing_order_status check (status in ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED')),
	constraint ck_mfg_outsourcing_order_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_mfg_outsourcing_order_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_mfg_outsourcing_order_quantity check (
		planned_quantity > 0 and issued_quantity >= 0 and received_quantity >= 0
	),
	constraint ck_mfg_outsourcing_order_cost check (provisional_unit_cost is null or provisional_unit_cost >= 0)
);

create table mfg_outsourcing_order_material (
	id bigserial primary key,
	outsourcing_order_id bigint not null,
	line_no integer not null,
	bom_item_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	required_quantity numeric(18, 6) not null,
	issued_quantity numeric(18, 6) not null default 0,
	loss_rate numeric(9, 6) not null default 0,
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mfg_outsourcing_order_material_order foreign key (outsourcing_order_id) references mfg_outsourcing_order (id) on delete cascade,
	constraint fk_mfg_outsourcing_order_material_bom_item foreign key (bom_item_id) references mfg_bom_item (id),
	constraint fk_mfg_outsourcing_order_material_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_outsourcing_order_material_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_outsourcing_order_material_line unique (outsourcing_order_id, line_no),
	constraint ck_mfg_outsourcing_order_material_quantity check (
		required_quantity > 0 and issued_quantity >= 0 and loss_rate >= 0 and loss_rate < 1
	)
);

create table mfg_outsourcing_issue (
	id bigserial primary key,
	issue_no varchar(64) not null,
	outsourcing_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_outsourcing_issue_no unique (issue_no),
	constraint fk_mfg_outsourcing_issue_order foreign key (outsourcing_order_id) references mfg_outsourcing_order (id),
	constraint ck_mfg_outsourcing_issue_status check (status in ('DRAFT', 'POSTED', 'CANCELLED'))
);

create table mfg_outsourcing_issue_line (
	id bigserial primary key,
	issue_id bigint not null,
	order_material_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	cost_layer_id bigint,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	stock_movement_id bigint,
	value_movement_id bigint,
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_mfg_outsourcing_issue_line_issue foreign key (issue_id) references mfg_outsourcing_issue (id) on delete cascade,
	constraint fk_mfg_outsourcing_issue_line_order_material foreign key (order_material_id) references mfg_outsourcing_order_material (id),
	constraint fk_mfg_outsourcing_issue_line_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_outsourcing_issue_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_outsourcing_issue_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_mfg_outsourcing_issue_line_project foreign key (project_id) references sal_project (id),
	constraint fk_mfg_outsourcing_issue_line_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	constraint fk_mfg_outsourcing_issue_line_stock foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_mfg_outsourcing_issue_line_value foreign key (value_movement_id) references inv_value_movement (id),
	constraint uk_mfg_outsourcing_issue_line_material unique (issue_id, order_material_id),
	constraint ck_mfg_outsourcing_issue_line_quantity check (quantity > 0),
	constraint ck_mfg_outsourcing_issue_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_mfg_outsourcing_issue_line_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	)
);

create table mfg_outsourcing_receipt (
	id bigserial primary key,
	receipt_no varchar(64) not null,
	outsourcing_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	receipt_warehouse_id bigint not null,
	quantity numeric(18, 6) not null,
	rejected_quantity numeric(18, 6) not null default 0,
	provisional_unit_cost numeric(18, 6),
	unit_cost numeric(18, 6),
	valuation_state varchar(32),
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	stock_movement_id bigint,
	value_movement_id bigint,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_mfg_outsourcing_receipt_no unique (receipt_no),
	constraint fk_mfg_outsourcing_receipt_order foreign key (outsourcing_order_id) references mfg_outsourcing_order (id),
	constraint fk_mfg_outsourcing_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_outsourcing_receipt_project foreign key (project_id) references sal_project (id),
	constraint fk_mfg_outsourcing_receipt_stock foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_mfg_outsourcing_receipt_value foreign key (value_movement_id) references inv_value_movement (id),
	constraint ck_mfg_outsourcing_receipt_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_mfg_outsourcing_receipt_quantity check (quantity > 0 and rejected_quantity >= 0),
	constraint ck_mfg_outsourcing_receipt_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_mfg_outsourcing_receipt_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_mfg_outsourcing_receipt_cost check (
		(provisional_unit_cost is null or provisional_unit_cost >= 0)
		and (unit_cost is null or unit_cost >= 0)
	),
	constraint ck_mfg_outsourcing_receipt_valuation_state check (
		valuation_state is null or valuation_state in (
			'VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL'
		)
	)
);

create table mfg_outsourcing_receipt_line (
	id bigserial primary key,
	receipt_id bigint not null,
	line_no integer not null,
	accepted_quantity numeric(18, 6) not null,
	rejected_quantity numeric(18, 6) not null default 0,
	provisional_unit_cost numeric(18, 6),
	unit_cost numeric(18, 6),
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	stock_movement_id bigint,
	value_movement_id bigint,
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mfg_outsourcing_receipt_line_receipt foreign key (receipt_id) references mfg_outsourcing_receipt (id) on delete cascade,
	constraint fk_mfg_outsourcing_receipt_line_stock foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_mfg_outsourcing_receipt_line_value foreign key (value_movement_id) references inv_value_movement (id),
	constraint uk_mfg_outsourcing_receipt_line_no unique (receipt_id, line_no),
	constraint ck_mfg_outsourcing_receipt_line_quantity check (
		accepted_quantity >= 0 and rejected_quantity >= 0 and accepted_quantity + rejected_quantity > 0
	),
	constraint ck_mfg_outsourcing_receipt_line_cost check (
		(provisional_unit_cost is null or provisional_unit_cost >= 0)
		and (unit_cost is null or unit_cost >= 0)
	)
);

create index idx_mfg_outsourcing_order_status
	on mfg_outsourcing_order (status, updated_at desc, id desc);
create index idx_mfg_outsourcing_order_project
	on mfg_outsourcing_order (ownership_type, project_id, status, planned_receipt_date desc, id desc);
create unique index uk_mfg_outsourcing_order_mrp_suggestion
	on mfg_outsourcing_order (source_mrp_suggestion_id)
	where source_mrp_suggestion_id is not null;
create index idx_mfg_outsourcing_order_material_order
	on mfg_outsourcing_order_material (outsourcing_order_id);
create index idx_mfg_outsourcing_issue_order
	on mfg_outsourcing_issue (outsourcing_order_id, updated_at desc);
create index idx_mfg_outsourcing_receipt_order
	on mfg_outsourcing_receipt (outsourcing_order_id, updated_at desc);
create index idx_mfg_outsourcing_receipt_line_receipt
	on mfg_outsourcing_receipt_line (receipt_id, line_no);

create table mfg_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint not null,
	resource_version bigint,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(64) not null,
	result_resource_id bigint not null,
	result_version bigint,
	created_at timestamptz not null default now(),
	constraint uk_mfg_action_idempotency unique (
		operator_user_id, action, resource_type, resource_id, idempotency_key
	)
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, '/planning/material-requirements',
	seed.http_method, seed.api_path, seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('planning:material-requirement:convert-production', '缺料建议转生产工单', 'POST', '/api/admin/planning/material-requirement-suggestions/{id}/convert-work-order', 657),
		('planning:material-requirement:convert-outsourcing', '缺料建议转外协订单', 'POST', '/api/admin/planning/material-requirement-suggestions/{id}/convert-outsourcing-order', 658)
) as seed(code, name, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'planning:material-requirement'
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_path,
	seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('production:outsourcing:view', '查看外协订单', '/production/outsourcing-orders', 'GET', '/api/admin/production/outsourcing-orders/**', 429),
		('production:outsourcing:create', '创建外协订单', '/production/outsourcing-orders', 'POST', '/api/admin/production/outsourcing-orders', 430),
		('production:outsourcing:update', '更新外协订单', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}', 431),
		('production:outsourcing:release', '发布外协订单', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/release', 432),
		('production:outsourcing:close', '关闭外协订单', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/close', 433),
		('production:outsourcing:cancel', '取消外协订单', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/cancel', 434),
		('production:outsourcing-issue:view', '查看外协发料', '/production/outsourcing-orders', 'GET', '/api/admin/production/outsourcing-orders/{id}/material-issues/**', 435),
		('production:outsourcing-issue:create', '创建外协发料', '/production/outsourcing-orders', 'POST', '/api/admin/production/outsourcing-orders/{id}/material-issues', 436),
		('production:outsourcing-issue:update', '更新外协发料草稿', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/material-issues/{issueId}', 437),
		('production:outsourcing-issue:post', '过账外协发料', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/material-issues/{issueId}/post', 438),
		('production:outsourcing-issue:cancel', '取消外协发料草稿', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/material-issues/{issueId}/cancel', 439),
		('production:outsourcing-receipt:view', '查看外协收货', '/production/outsourcing-orders', 'GET', '/api/admin/production/outsourcing-orders/{id}/receipts/**', 440),
		('production:outsourcing-receipt:create', '创建外协收货', '/production/outsourcing-orders', 'POST', '/api/admin/production/outsourcing-orders/{id}/receipts', 441),
		('production:outsourcing-receipt:update', '更新外协收货草稿', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/receipts/{receiptId}', 442),
		('production:outsourcing-receipt:post', '过账外协收货', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/receipts/{receiptId}/post', 443),
		('production:outsourcing-receipt:cancel', '取消外协收货草稿', '/production/outsourcing-orders', 'PUT', '/api/admin/production/outsourcing-orders/{id}/receipts/{receiptId}/cancel', 444)
) as seed(code, name, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'production'
on conflict (code) do nothing;

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code in (
	'planning:material-requirement:convert-production',
	'planning:material-requirement:convert-outsourcing',
	'production:outsourcing:view',
	'production:outsourcing:create',
	'production:outsourcing:update',
	'production:outsourcing:release',
	'production:outsourcing:close',
	'production:outsourcing:cancel',
	'production:outsourcing-issue:view',
	'production:outsourcing-issue:create',
	'production:outsourcing-issue:update',
	'production:outsourcing-issue:post',
	'production:outsourcing-issue:cancel',
	'production:outsourcing-receipt:view',
	'production:outsourcing-receipt:create',
	'production:outsourcing-receipt:update',
	'production:outsourcing-receipt:post',
	'production:outsourcing-receipt:cancel'
)
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);
