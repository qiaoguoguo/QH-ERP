alter table sal_sales_order_line drop constraint if exists uk_sal_sales_order_line_material;

create table sal_sales_quote (
	id bigserial primary key,
	quote_no varchar(64) not null,
	customer_id bigint not null,
	project_id bigint,
	contract_id bigint,
	quote_date date not null,
	valid_until date not null,
	status varchar(32) not null,
	currency varchar(8) not null default 'CNY',
	tax_excluded_amount numeric(18, 2) not null default 0,
	tax_amount numeric(18, 2) not null default 0,
	tax_included_amount numeric(18, 2) not null default 0,
	approval_instance_id bigint,
	converted_order_id bigint,
	converted_contract_id bigint,
	converted_at timestamptz,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	approved_by varchar(64),
	approved_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_sal_sales_quote_no unique (quote_no),
	constraint fk_sal_sales_quote_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_sal_sales_quote_project foreign key (project_id) references sal_project (id),
	constraint fk_sal_sales_quote_contract foreign key (contract_id) references sal_project_contract (id),
	constraint fk_sal_sales_quote_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint fk_sal_sales_quote_order foreign key (converted_order_id) references sal_sales_order (id),
	constraint ck_sal_sales_quote_status check (status in ('DRAFT', 'APPROVED', 'CONVERTED', 'EXPIRED', 'CANCELLED')),
	constraint ck_sal_sales_quote_currency check (currency = 'CNY'),
	constraint ck_sal_sales_quote_valid_range check (quote_date <= valid_until),
	constraint ck_sal_sales_quote_amount check (
		tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
	)
);

create table sal_sales_quote_line (
	id bigserial primary key,
	quote_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	required_date date,
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	remark varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_sal_sales_quote_line_quote foreign key (quote_id) references sal_sales_quote (id) on delete cascade,
	constraint fk_sal_sales_quote_line_material foreign key (material_id) references mst_material (id),
	constraint fk_sal_sales_quote_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_sal_sales_quote_line_no unique (quote_id, line_no),
	constraint ck_sal_sales_quote_line_values check (
		quantity > 0 and tax_rate >= 0 and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
		and tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
	)
);

alter table sal_project_contract
	add column if not exists source_quote_id bigint,
	add column if not exists source_quote_no varchar(64),
	add column if not exists source_quote_version bigint;

do $$
begin
	if not exists (
		select 1
		from pg_constraint
		where conname = 'fk_sal_project_contract_source_quote'
	) then
		alter table sal_project_contract
			add constraint fk_sal_project_contract_source_quote foreign key (source_quote_id) references sal_sales_quote (id);
	end if;
	if not exists (
		select 1
		from pg_constraint
		where conname = 'fk_sal_sales_quote_contract_conversion'
	) then
		alter table sal_sales_quote
			add constraint fk_sal_sales_quote_contract_conversion foreign key (converted_contract_id) references sal_project_contract (id);
	end if;
end $$;

create table sal_sales_order_snapshot (
	id bigserial primary key,
	order_id bigint not null,
	order_no varchar(64) not null,
	customer_id bigint not null,
	customer_code varchar(64) not null,
	customer_name varchar(100) not null,
	project_id bigint,
	project_no varchar(64),
	project_name varchar(120),
	contract_id bigint,
	contract_no varchar(64),
	external_contract_no varchar(100),
	source_quote_id bigint,
	source_quote_no varchar(64),
	source_quote_version bigint,
	currency varchar(8) not null default 'CNY',
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	snapshot_at timestamptz not null,
	created_at timestamptz not null default now(),
	constraint uk_sal_sales_order_snapshot_order unique (order_id),
	constraint fk_sal_sales_order_snapshot_order foreign key (order_id) references sal_sales_order (id) on delete cascade,
	constraint ck_sal_sales_order_snapshot_currency check (currency = 'CNY')
);

create table sal_sales_order_line_snapshot (
	id bigserial primary key,
	order_snapshot_id bigint not null,
	order_id bigint not null,
	order_line_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	material_code varchar(64) not null,
	material_name varchar(100) not null,
	unit_id bigint not null,
	unit_name varchar(100) not null,
	quantity numeric(18, 6) not null,
	price_source_type varchar(32) not null,
	source_quote_line_id bigint,
	source_no varchar(64),
	currency varchar(8) not null default 'CNY',
	tax_rate numeric(9, 6) not null,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	created_at timestamptz not null default now(),
	constraint fk_sal_sales_order_line_snapshot_header foreign key (order_snapshot_id) references sal_sales_order_snapshot (id) on delete cascade,
	constraint fk_sal_sales_order_line_snapshot_order foreign key (order_id) references sal_sales_order (id) on delete cascade,
	constraint fk_sal_sales_order_line_snapshot_line foreign key (order_line_id) references sal_sales_order_line (id) on delete cascade,
	constraint uk_sal_sales_order_line_snapshot_line unique (order_line_id),
	constraint ck_sal_sales_order_line_snapshot_currency check (currency = 'CNY')
);

alter table sal_sales_order
	add column if not exists source_quote_id bigint,
	add column if not exists source_quote_no varchar(64),
	add column if not exists source_quote_version bigint,
	add column if not exists currency varchar(8) not null default 'CNY',
	add column if not exists price_mode varchar(32) not null default 'TAX_INCLUDED',
	add column if not exists tax_excluded_amount numeric(18, 2) not null default 0,
	add column if not exists tax_amount numeric(18, 2) not null default 0,
	add column if not exists tax_included_amount numeric(18, 2) not null default 0,
	add column if not exists confirmed_snapshot_at timestamptz,
	add column if not exists close_reason varchar(200),
	add column if not exists sales_fulfillment_compatible boolean not null default false,
	add column if not exists credit_check_log_id bigint,
	add column if not exists credit_override_approval_instance_id bigint;

alter table sal_sales_order
	add constraint fk_sal_sales_order_source_quote foreign key (source_quote_id) references sal_sales_quote (id),
	add constraint ck_sal_sales_order_currency check (currency = 'CNY');

alter table sal_sales_order_line
	add column if not exists source_quote_line_id bigint,
	add column if not exists price_source_type varchar(32) not null default 'LEGACY_MANUAL',
	add column if not exists source_no varchar(64),
	add column if not exists currency varchar(8) not null default 'CNY',
	add column if not exists tax_rate numeric(9, 6) not null default 0,
	add column if not exists tax_excluded_unit_price numeric(18, 6),
	add column if not exists tax_included_unit_price numeric(18, 6),
	add column if not exists tax_excluded_amount numeric(18, 2),
	add column if not exists tax_amount numeric(18, 2),
	add column if not exists tax_included_amount numeric(18, 2);

update sal_sales_order_line
set tax_excluded_unit_price = coalesce(tax_excluded_unit_price, unit_price),
    tax_included_unit_price = coalesce(tax_included_unit_price, unit_price),
    tax_excluded_amount = coalesce(tax_excluded_amount, round(quantity * unit_price, 2)),
    tax_amount = coalesce(tax_amount, 0),
    tax_included_amount = coalesce(tax_included_amount, round(quantity * unit_price, 2));

alter table sal_sales_order_line
	alter column tax_excluded_unit_price set not null,
	alter column tax_included_unit_price set not null,
	alter column tax_excluded_amount set not null,
	alter column tax_amount set not null,
	alter column tax_included_amount set not null,
	add constraint fk_sal_sales_order_line_quote_line foreign key (source_quote_line_id) references sal_sales_quote_line (id),
	add constraint ck_sal_sales_order_line_price_source check (
		price_source_type in ('LEGACY_MANUAL', 'MANUAL', 'QUOTE', 'CONTRACT')
	),
	add constraint ck_sal_sales_order_line_currency check (currency = 'CNY');

create table sal_sales_delivery_plan (
	id bigserial primary key,
	order_id bigint not null,
	order_line_id bigint not null,
	line_no integer not null,
	planned_date date not null,
	planned_quantity numeric(18, 6) not null,
	shipped_quantity numeric(18, 6) not null default 0,
	status varchar(32) not null,
	close_reason varchar(200),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_sal_sales_delivery_plan_order foreign key (order_id) references sal_sales_order (id) on delete cascade,
	constraint fk_sal_sales_delivery_plan_line foreign key (order_line_id) references sal_sales_order_line (id) on delete cascade,
	constraint uk_sal_sales_delivery_plan_line_no unique (order_line_id, line_no),
	constraint ck_sal_sales_delivery_plan_status check (
		status in ('PLANNED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED', 'CANCELLED')
	),
	constraint ck_sal_sales_delivery_plan_quantity check (
		planned_quantity > 0 and shipped_quantity >= 0 and shipped_quantity <= planned_quantity
	)
);

alter table sal_sales_shipment_line
	add column if not exists delivery_plan_id bigint,
	add column if not exists legacy_snapshot boolean not null default false,
	add column if not exists price_source_type varchar(32) not null default 'LEGACY_MANUAL',
	add column if not exists source_no varchar(64),
	add column if not exists currency varchar(8) not null default 'CNY',
	add column if not exists tax_rate numeric(9, 6) not null default 0,
	add column if not exists tax_excluded_unit_price numeric(18, 6),
	add column if not exists tax_included_unit_price numeric(18, 6),
	add column if not exists tax_excluded_amount numeric(18, 2),
	add column if not exists tax_amount numeric(18, 2),
	add column if not exists tax_included_amount numeric(18, 2),
	add column if not exists legacy_delivery_plan_compatible boolean not null default false;

update sal_sales_shipment_line sl
set tax_excluded_unit_price = coalesce(sl.tax_excluded_unit_price, ol.unit_price),
    tax_included_unit_price = coalesce(sl.tax_included_unit_price, ol.unit_price),
    tax_excluded_amount = coalesce(sl.tax_excluded_amount, round(sl.quantity * ol.unit_price, 2)),
    tax_amount = coalesce(sl.tax_amount, 0),
    tax_included_amount = coalesce(sl.tax_included_amount, round(sl.quantity * ol.unit_price, 2)),
    price_source_type = coalesce(sl.price_source_type, ol.price_source_type),
    source_no = coalesce(sl.source_no, ol.source_no),
    legacy_snapshot = case when sh.status = 'POSTED' then true else sl.legacy_snapshot end
from sal_sales_order_line ol, sal_sales_shipment sh
where ol.id = sl.order_line_id
and sh.id = sl.shipment_id;

update sal_sales_order
set sales_fulfillment_compatible = true
where status in ('SHIPPED', 'CLOSED');

alter table sal_sales_shipment_line
	alter column tax_excluded_unit_price set not null,
	alter column tax_included_unit_price set not null,
	alter column tax_excluded_amount set not null,
	alter column tax_amount set not null,
	alter column tax_included_amount set not null,
	add constraint fk_sal_sales_shipment_line_delivery_plan foreign key (delivery_plan_id) references sal_sales_delivery_plan (id),
	add constraint ck_sal_sales_shipment_line_currency check (currency = 'CNY');

create or replace view sal_effective_sales_demand as
select ol.id,
       o.id as order_id,
       o.order_no,
       ol.id as order_line_id,
       o.customer_id,
       c.code as customer_code,
       c.name as customer_name,
       o.project_id,
       p.project_no,
       p.name as project_name,
       o.contract_id,
       pc.contract_no,
       ol.material_id,
       m.code as material_code,
       m.name as material_name,
       ol.unit_id,
       u.name as unit_name,
       ol.quantity as ordered_quantity,
       ol.shipped_quantity,
       coalesce(returned.returned_quantity, 0.000000) as returned_quantity,
       greatest(ol.quantity - ol.shipped_quantity, 0.000000) as open_demand_quantity,
       o.status as order_status,
       case when o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED') then true else false end as counted_as_effective_demand,
       case
           when o.status in ('CONFIRMED', 'PARTIALLY_SHIPPED') then null
           else 'ORDER_STATUS_NOT_COUNTED'
       end as excluded_reason_code
from sal_sales_order_line ol
join sal_sales_order o on o.id = ol.order_id
join mst_customer c on c.id = o.customer_id
join mst_material m on m.id = ol.material_id
join mst_unit u on u.id = ol.unit_id
left join sal_project p on p.id = o.project_id
left join sal_project_contract pc on pc.id = o.contract_id
left join (
	select sl.order_line_id, sum(rl.quantity) as returned_quantity
	from sal_sales_return_line rl
	join sal_sales_return r on r.id = rl.return_id
	join sal_sales_shipment_line sl on sl.id = rl.source_shipment_line_id
	where r.status = 'POSTED'
	group by sl.order_line_id
) returned on returned.order_line_id = ol.id;

create table sal_sales_order_change (
	id bigserial primary key,
	change_no varchar(64) not null,
	order_id bigint not null,
	status varchar(32) not null,
	reason varchar(500) not null,
	approval_instance_id bigint,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	applied_by varchar(64),
	applied_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_sal_sales_order_change_no unique (change_no),
	constraint fk_sal_sales_order_change_order foreign key (order_id) references sal_sales_order (id),
	constraint fk_sal_sales_order_change_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_sal_sales_order_change_status check (status in ('DRAFT', 'APPLIED', 'CANCELLED'))
);

create table sal_sales_order_change_line (
	id bigserial primary key,
	change_id bigint not null,
	order_line_id bigint not null,
	line_no integer not null,
	new_quantity numeric(18, 6),
	new_tax_rate numeric(9, 6),
	new_tax_excluded_unit_price numeric(18, 6),
	new_planned_date date,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_sal_sales_order_change_line_header foreign key (change_id) references sal_sales_order_change (id) on delete cascade,
	constraint fk_sal_sales_order_change_line_order_line foreign key (order_line_id) references sal_sales_order_line (id),
	constraint uk_sal_sales_order_change_line_no unique (change_id, line_no)
);

create table sal_customer_credit_profile (
	id bigserial primary key,
	customer_id bigint not null,
	credit_limit numeric(18, 2) not null,
	status varchar(32) not null,
	frozen boolean not null default false,
	overdue_blocked boolean not null default false,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_sal_customer_credit_profile_customer unique (customer_id),
	constraint fk_sal_customer_credit_profile_customer foreign key (customer_id) references mst_customer (id),
	constraint ck_sal_customer_credit_profile_status check (status in ('ACTIVE', 'DISABLED')),
	constraint ck_sal_customer_credit_profile_limit check (credit_limit >= 0)
);

create table sal_credit_check_log (
	id bigserial primary key,
	customer_id bigint not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	check_result varchar(32) not null,
	credit_limit numeric(18, 2),
	used_credit numeric(18, 2) not null,
	new_amount numeric(18, 2) not null,
	reason varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_sal_credit_check_log_customer foreign key (customer_id) references mst_customer (id),
	constraint ck_sal_credit_check_log_result check (check_result in ('PASSED', 'BLOCKED', 'OVERRIDDEN'))
);

create table sal_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	action varchar(80) not null,
	resource_type varchar(80) not null,
	resource_id bigint not null,
	resource_version bigint,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(80) not null,
	result_resource_id bigint not null,
	result_version bigint,
	created_at timestamptz not null default now(),
	constraint uk_sal_action_idempotency_key unique (
		operator_user_id, action, resource_type, resource_id, idempotency_key
	)
);

alter table sal_project
	add column if not exists sales_fulfillment_status varchar(32) not null default 'OPEN',
	add column if not exists sales_fulfillment_closed_by varchar(64),
	add column if not exists sales_fulfillment_closed_at timestamptz,
	add column if not exists sales_fulfillment_close_reason varchar(200),
	add constraint ck_sal_project_sales_fulfillment_status check (
		sales_fulfillment_status in ('OPEN', 'CLOSED')
	);

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
		'PROCUREMENT_ORDER',
		'SALES_QUOTE',
		'SALES_ORDER_CHANGE',
		'SALES_PROJECT'
	)
);

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
select seed.scene_code, seed.name, seed.business_object_type, seed.action_code, 1, 'ENABLED'
from (
	values
		('SALES_QUOTE_APPROVAL', '销售报价固定审批', 'SALES_QUOTE', 'APPROVE'),
		('SALES_ORDER_CHANGE_APPROVAL', '销售订单变更审批', 'SALES_ORDER_CHANGE', 'APPLY'),
		('SALES_ORDER_CHANGE_CREDIT_OVERRIDE', '销售订单变更信用例外审批', 'SALES_ORDER_CHANGE', 'OVERRIDE'),
		('SALES_ORDER_CREDIT_OVERRIDE', '销售订单信用例外审批', 'SALES_ORDER', 'OVERRIDE'),
		('SALES_ORDER_SHORT_CLOSE', '销售订单短交关闭审批', 'SALES_ORDER', 'CLOSE')
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
		('SALES_QUOTE_APPROVAL', '销售报价审批', 'sales:quote:approve'),
		('SALES_ORDER_CHANGE_APPROVAL', '销售订单变更审批', 'sales:order-change:approve'),
		('SALES_ORDER_CHANGE_CREDIT_OVERRIDE', '销售变更信用例外审批', 'sales:credit:override-approve'),
		('SALES_ORDER_CREDIT_OVERRIDE', '销售信用例外审批', 'sales:credit:override-approve'),
		('SALES_ORDER_SHORT_CLOSE', '销售短交关闭审批', 'sales:order:short-close-approve')
) as seed(scene_code, step_name, permission_code)
join platform_approval_definition d on d.scene_code = seed.scene_code
where not exists (
	select 1 from platform_approval_definition_step s where s.definition_id = d.id and s.step_no = 1
);

insert into platform_print_template (
	template_code, scene_code, name, object_type, template_version, status
)
select 'SALES_QUOTE_V1', 'SALES_QUOTE_PRINT', '销售报价固定打印', 'SALES_QUOTE', 1, 'ENABLED'
where not exists (
	select 1 from platform_print_template where template_code = 'SALES_QUOTE_V1'
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select 'sales', '销售管理', 'MENU', null, '/sales/orders', null, null, 370,
	'system', now(), 'system', now()
where not exists (select 1 from sys_permission p where p.code = 'sales');

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_pattern,
	seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('sales:quote:view', '查看销售报价', '/sales/quotes', 'GET', '/api/admin/sales/quotes/**', 401),
		('sales:quote:create', '创建销售报价', '/sales/quotes', 'POST', '/api/admin/sales/quotes', 402),
		('sales:quote:update', '更新销售报价草稿', '/sales/quotes', 'PUT', '/api/admin/sales/quotes/{id}', 403),
		('sales:quote:submit', '提交销售报价审批', '/sales/quotes', 'POST', '/api/admin/sales/quotes/{id}/submit-approval', 404),
		('sales:quote:approve', '审批销售报价', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 405),
		('sales:quote:cancel', '取消销售报价草稿', '/sales/quotes', 'POST', '/api/admin/sales/quotes/{id}/cancel', 406),
		('sales:quote:convert', '销售报价转换', '/sales/quotes', 'POST', '/api/admin/sales/quotes/{id}/convert-order', 407),
		('sales:quote:print', '打印销售报价', '/sales/quotes', 'POST', '/api/admin/print-tasks', 408),
		('sales:quote:export', '导出销售报价', '/sales/quotes', 'POST', '/api/admin/export-tasks', 409),
		('sales:delivery-plan:view', '查看销售交付计划', '/sales/delivery-plans', 'GET', '/api/admin/sales/delivery-plans/**', 410),
		('sales:delivery-plan:manage', '管理销售交付计划', '/sales/delivery-plans', 'PUT', '/api/admin/sales/orders/{id}/delivery-plans/**', 411),
		('sales:order-change:view', '查看销售订单变更', '/sales/order-changes', 'GET', '/api/admin/sales/order-changes/**', 412),
		('sales:order-change:create', '创建销售订单变更', '/sales/order-changes', 'POST', '/api/admin/sales/orders/{id}/changes', 413),
		('sales:order-change:update', '更新销售订单变更', '/sales/order-changes', 'PUT', '/api/admin/sales/order-changes/{id}', 414),
		('sales:order-change:submit', '提交销售订单变更审批', '/sales/order-changes', 'POST', '/api/admin/sales/order-changes/{id}/submit-approval', 415),
		('sales:order-change:cancel', '取消销售订单变更草稿', '/sales/order-changes', 'POST', '/api/admin/sales/order-changes/{id}/cancel', 416),
		('sales:order-change:approve', '审批销售订单变更', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 417),
		('sales:credit:view', '查看客户信用占用', '/sales/credit', 'GET', '/api/admin/sales/credit-profiles/**', 418),
		('sales:credit:manage', '维护客户信用档案', '/sales/credit', 'PUT', '/api/admin/sales/credit-profiles/{id}', 419),
		('sales:credit:override-submit', '提交销售信用例外审批', '/sales/credit', 'POST', '/api/admin/sales/orders/{id}/submit-credit-override', 420),
		('sales:credit:override-approve', '审批销售信用例外', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 421),
		('sales:order:short-close-submit', '提交销售短交关闭审批', '/sales/orders', 'POST', '/api/admin/sales/orders/{id}/submit-short-close', 422),
		('sales:order:short-close-approve', '审批销售短交关闭', '/platform/approvals', 'POST', '/api/admin/approval-tasks/{id}/approve', 423),
		('sales:fulfillment:view', '查看项目销售履约', '/sales/projects', 'GET', '/api/admin/sales-projects/{id}/fulfillment', 423),
		('sales:fulfillment:close', '关闭项目销售履约', '/sales/projects', 'POST', '/api/admin/sales-projects/{id}/close-sales-fulfillment', 424),
		('sales:effective-demand:view', '查看有效销售需求', '/sales/effective-demands', 'GET', '/api/admin/sales/effective-demands/**', 425),
		('sales:effective-demand:export', '导出有效销售需求', '/sales/effective-demands', 'POST', '/api/admin/export-tasks', 426),
		('sales:document:print', '打印销售单据', '/sales/quotes', 'POST', '/api/admin/print-tasks', 427),
		('sales:document:export', '导出销售单据', '/sales/quotes', 'POST', '/api/admin/export-tasks', 428)
) as seed(code, name, route_path, http_method, api_pattern, sort_order)
join sys_permission parent on parent.code = 'sales'
where not exists (select 1 from sys_permission p where p.code = seed.code);

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code like 'sales:%'
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);

create index idx_sal_sales_quote_customer_status on sal_sales_quote (customer_id, status, updated_at desc, id desc);
create index idx_sal_sales_quote_project on sal_sales_quote (project_id, contract_id);
create index idx_sal_sales_quote_line_quote on sal_sales_quote_line (quote_id, line_no);
create index idx_sal_sales_order_snapshot_order on sal_sales_order_snapshot (order_id);
create index idx_sal_sales_order_source_quote on sal_sales_order (source_quote_id);
create index idx_sal_sales_order_line_quote_line on sal_sales_order_line (source_quote_line_id);
create index idx_sal_sales_delivery_plan_order on sal_sales_delivery_plan (order_id, status, planned_date, id);
create index idx_sal_sales_delivery_plan_line on sal_sales_delivery_plan (order_line_id, status);
create index idx_sal_sales_order_change_order on sal_sales_order_change (order_id, status, updated_at desc, id desc);
create index idx_sal_credit_check_customer on sal_credit_check_log (customer_id, created_at desc, id desc);
