create table platform_data_repair_adapter_definition (
	id bigserial primary key,
	adapter_code varchar(80) not null,
	name varchar(120) not null,
	target_object_type varchar(64) not null,
	allowed_fields jsonb not null,
	description varchar(500) not null,
	required_permission_code varchar(120) not null,
	status varchar(32) not null,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_data_repair_adapter unique (adapter_code),
	constraint ck_platform_data_repair_adapter_status check (status in ('ENABLED', 'DISABLED'))
);

create table platform_data_repair_request (
	id bigserial primary key,
	request_no varchar(64) not null,
	adapter_code varchar(80) not null,
	target_object_type varchar(64) not null,
	target_object_id bigint not null,
	target_object_no varchar(120) not null,
	target_object_summary varchar(255) not null,
	target_object_version bigint not null,
	status varchar(32) not null,
	reason varchar(500) not null,
	risk_summary varchar(500),
	before_summary jsonb not null,
	after_summary jsonb not null,
	request_fingerprint varchar(64) not null,
	idempotency_key varchar(120) not null,
	approval_instance_id bigint,
	execution_task_id bigint,
	previous_request_id bigint,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	submitted_by_username varchar(64),
	submitted_at timestamptz,
	approved_at timestamptz,
	executed_by_username varchar(64),
	executed_at timestamptz,
	verified_by_username varchar(64),
	verified_at timestamptz,
	cancelled_by_username varchar(64),
	cancelled_at timestamptz,
	error_summary varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_data_repair_request_no unique (request_no),
	constraint uk_platform_data_repair_request_idempotency unique (created_by_user_id, adapter_code, idempotency_key),
	constraint fk_platform_data_repair_request_adapter foreign key (adapter_code)
		references platform_data_repair_adapter_definition (adapter_code),
	constraint fk_platform_data_repair_request_approval foreign key (approval_instance_id)
		references platform_approval_instance (id),
	constraint fk_platform_data_repair_request_task foreign key (execution_task_id)
		references platform_document_task (id),
	constraint fk_platform_data_repair_request_previous foreign key (previous_request_id)
		references platform_data_repair_request (id),
	constraint fk_platform_data_repair_request_creator foreign key (created_by_user_id)
		references sys_user (id),
	constraint ck_platform_data_repair_request_status check (
		status in (
			'DRAFT', 'PENDING_APPROVAL', 'READY_TO_EXECUTE', 'EXECUTING', 'EXECUTED', 'VERIFIED',
			'REJECTED', 'CANCELLED', 'FAILED', 'VERIFY_FAILED'
		)
	)
);

create index idx_platform_data_repair_request_target
	on platform_data_repair_request (target_object_type, target_object_id, created_at desc);

create index idx_platform_data_repair_request_status
	on platform_data_repair_request (status, created_at desc);

create table platform_data_repair_change (
	id bigserial primary key,
	request_id bigint not null,
	line_no integer not null,
	field_name varchar(80) not null,
	before_value_summary varchar(500),
	after_value_summary varchar(500),
	created_at timestamptz not null default now(),
	constraint fk_platform_data_repair_change_request foreign key (request_id)
		references platform_data_repair_request (id),
	constraint uk_platform_data_repair_change_field unique (request_id, field_name),
	constraint ck_platform_data_repair_change_line check (line_no > 0)
);

create table platform_data_repair_event (
	id bigserial primary key,
	request_id bigint not null,
	event_type varchar(64) not null,
	operator_user_id bigint,
	operator_username varchar(64) not null,
	status_before varchar(32),
	status_after varchar(32),
	detail_json jsonb,
	created_at timestamptz not null default now(),
	constraint fk_platform_data_repair_event_request foreign key (request_id)
		references platform_data_repair_request (id),
	constraint fk_platform_data_repair_event_operator foreign key (operator_user_id)
		references sys_user (id)
);

create index idx_platform_data_repair_event_request
	on platform_data_repair_event (request_id, id);

create table platform_data_repair_check (
	id bigserial primary key,
	request_id bigint not null,
	check_type varchar(64) not null,
	status varchar(32) not null,
	code varchar(80),
	message varchar(500) not null,
	detail_json jsonb,
	created_at timestamptz not null default now(),
	constraint fk_platform_data_repair_check_request foreign key (request_id)
		references platform_data_repair_request (id),
	constraint ck_platform_data_repair_check_status check (status in ('PASSED', 'WARNING', 'FAILED'))
);

create index idx_platform_data_repair_check_request
	on platform_data_repair_check (request_id, id);

create table platform_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	action varchar(80) not null,
	target_type varchar(64) not null,
	target_id bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_type varchar(64) not null,
	result_id bigint not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_action_idempotency_operator foreign key (operator_user_id)
		references sys_user (id),
	constraint uk_platform_action_idempotency unique (
		operator_user_id, action, target_type, target_id, idempotency_key
	)
);

create table platform_import_adapter_definition (
	id bigserial primary key,
	adapter_code varchar(80) not null,
	name varchar(120) not null,
	target_object_type varchar(64) not null,
	template_code varchar(80) not null,
	template_version integer not null,
	max_rows integer not null,
	required_permission_code varchar(120) not null,
	description varchar(500) not null,
	status varchar(32) not null,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_import_adapter unique (adapter_code),
	constraint ck_platform_import_adapter_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_platform_import_adapter_rows check (max_rows > 0)
);

create table platform_batch_tool_definition (
	id bigserial primary key,
	tool_code varchar(80) not null,
	name varchar(120) not null,
	target_object_type varchar(64) not null,
	action_code varchar(64) not null,
	max_items integer not null,
	required_permission_code varchar(120) not null,
	description varchar(500) not null,
	status varchar(32) not null,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_batch_tool unique (tool_code),
	constraint ck_platform_batch_tool_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_platform_batch_tool_items check (max_items > 0)
);

create table platform_batch_operation (
	id bigserial primary key,
	operation_no varchar(64) not null,
	tool_code varchar(80) not null,
	target_object_type varchar(64) not null,
	action_code varchar(64) not null,
	status varchar(32) not null,
	request_payload jsonb not null,
	request_fingerprint varchar(64) not null,
	idempotency_key varchar(120) not null,
	total_count integer not null default 0,
	blocked_count integer not null default 0,
	success_count integer not null default 0,
	error_count integer not null default 0,
	document_task_id bigint,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	executed_by_username varchar(64),
	executed_at timestamptz,
	error_summary varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_batch_operation_no unique (operation_no),
	constraint uk_platform_batch_operation_idempotency unique (created_by_user_id, tool_code, idempotency_key),
	constraint fk_platform_batch_operation_tool foreign key (tool_code)
		references platform_batch_tool_definition (tool_code),
	constraint fk_platform_batch_operation_task foreign key (document_task_id)
		references platform_document_task (id),
	constraint fk_platform_batch_operation_creator foreign key (created_by_user_id)
		references sys_user (id),
	constraint ck_platform_batch_operation_status check (
		status in ('PRECHECKED', 'PRECHECK_FAILED', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
	)
);

create index idx_platform_batch_operation_status
	on platform_batch_operation (status, created_at desc);

create table platform_batch_operation_item (
	id bigserial primary key,
	operation_id bigint not null,
	line_no integer not null,
	target_object_type varchar(64) not null,
	target_object_id bigint not null,
	target_object_no varchar(120),
	target_object_summary varchar(255),
	target_object_version bigint,
	status varchar(32) not null,
	message varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	constraint fk_platform_batch_operation_item_operation foreign key (operation_id)
		references platform_batch_operation (id),
	constraint uk_platform_batch_operation_item_line unique (operation_id, line_no),
	constraint ck_platform_batch_operation_item_status check (
		status in ('READY', 'BLOCKED', 'SUCCEEDED', 'FAILED', 'SKIPPED')
	)
);

create table platform_batch_operation_error (
	id bigserial primary key,
	operation_id bigint not null,
	item_id bigint,
	line_no integer,
	error_code varchar(80) not null,
	message varchar(500) not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_batch_operation_error_operation foreign key (operation_id)
		references platform_batch_operation (id),
	constraint fk_platform_batch_operation_error_item foreign key (item_id)
		references platform_batch_operation_item (id)
);

create index idx_platform_batch_operation_error_operation
	on platform_batch_operation_error (operation_id, line_no, id);

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
		'SALES_PROJECT',
		'DATA_REPAIR_REQUEST'
	)
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('platform', '平台治理', 'MENU', null, '/platform/document-tasks', null, null, 900,
	'system', now(), 'system', now())
on conflict (code) do update
set name = excluded.name,
    type = excluded.type,
    route_path = excluded.route_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_path,
	seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('platform:data-repair:view', '查看数据修复记录', '/platform/data-repairs', 'GET',
			'/api/admin/platform/data-repairs/**', 941),
		('platform:data-repair:create', '创建数据修复申请', '/platform/data-repairs', 'POST',
			'/api/admin/platform/data-repairs', 942),
		('platform:data-repair:update', '更新数据修复草稿', '/platform/data-repairs', 'PUT',
			'/api/admin/platform/data-repairs/{id}', 943),
		('platform:data-repair:submit', '提交数据修复审批', '/platform/data-repairs', 'POST',
			'/api/admin/platform/data-repairs/{id}/submit', 944),
		('platform:data-repair:approve', '审批数据修复', '/platform/approvals', 'POST',
			'/api/admin/approval-tasks/{id}/approve', 945),
		('platform:data-repair:execute', '执行数据修复', '/platform/data-repairs', 'POST',
			'/api/admin/platform/data-repairs/{id}/execute', 946),
		('platform:data-repair:verify', '验证数据修复', '/platform/data-repairs', 'POST',
			'/api/admin/platform/data-repairs/{id}/verify', 947),
		('platform:data-repair:cancel', '取消数据修复申请', '/platform/data-repairs', 'POST',
			'/api/admin/platform/data-repairs/{id}/cancel', 948),
		('platform:history-import:view', '查看历史数据导入', '/platform/history-imports', 'GET',
			'/api/admin/platform/history-imports/**', 949),
		('platform:history-import:create', '创建历史数据导入', '/platform/history-imports', 'POST',
			'/api/admin/platform/history-imports/**', 950),
		('platform:history-import:confirm', '确认历史数据导入', '/platform/history-imports', 'POST',
			'/api/admin/platform/history-imports/{taskId}/confirm', 951),
		('platform:history-import:cancel', '取消历史数据导入', '/platform/history-imports', 'POST',
			'/api/admin/platform/history-imports/{taskId}/cancel', 952),
		('platform:batch-tool:view', '查看固定批量工具', '/platform/delivery-assets', 'GET',
			'/api/admin/platform/batch-tools/**', 953),
		('platform:batch-tool:preview', '预检固定批量工具', '/platform/delivery-assets', 'POST',
			'/api/admin/platform/batch-tools/{code}/preview', 954),
		('platform:batch-tool:execute', '执行固定批量工具', '/platform/delivery-assets', 'POST',
			'/api/admin/platform/batch-operations/{id}/execute', 955),
		('platform:delivery-asset:view', '查看交付资料', '/platform/delivery-assets', 'GET',
			'/api/admin/platform/delivery-assets', 956)
) as seed(code, name, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'platform'
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
	'platform:data-repair:view',
	'platform:data-repair:create',
	'platform:data-repair:update',
	'platform:data-repair:submit',
	'platform:data-repair:approve',
	'platform:data-repair:execute',
	'platform:data-repair:verify',
	'platform:data-repair:cancel',
	'platform:history-import:view',
	'platform:history-import:create',
	'platform:history-import:confirm',
	'platform:history-import:cancel',
	'platform:batch-tool:view',
	'platform:batch-tool:preview',
	'platform:batch-tool:execute',
	'platform:delivery-asset:view'
)
where r.code = 'SYSTEM_ADMIN'
on conflict (role_id, permission_id) do nothing;

insert into platform_data_repair_adapter_definition (
	adapter_code, name, target_object_type, allowed_fields, description, required_permission_code, status
)
values
	('MATERIAL_PROFILE_CORRECTION_V1', '物料资料修复', 'MATERIAL',
		'["name", "specification", "remark"]'::jsonb,
		'仅允许修复物料非标识描述字段', 'master:material:update', 'ENABLED'),
	('CUSTOMER_PROFILE_CORRECTION_V1', '客户资料修复', 'CUSTOMER',
		'["name", "contactName", "contactPhone", "remark"]'::jsonb,
		'仅允许修复客户基础联系资料', 'master:customer:update', 'ENABLED'),
	('SUPPLIER_PROFILE_CORRECTION_V1', '供应商资料修复', 'SUPPLIER',
		'["name", "contactName", "contactPhone", "remark"]'::jsonb,
		'仅允许修复供应商基础联系资料', 'master:supplier:update', 'ENABLED');

insert into platform_import_adapter_definition (
	adapter_code, name, target_object_type, template_code, template_version, max_rows,
	required_permission_code, description, status
)
values
	('CUSTOMER_MASTER_V1', '客户历史主数据导入', 'CUSTOMER', 'CUSTOMER_MASTER_V1', 1, 10000,
		'master:customer:create', '新增客户基础资料，不更新已有编码', 'ENABLED'),
	('SUPPLIER_MASTER_V1', '供应商历史主数据导入', 'SUPPLIER', 'SUPPLIER_MASTER_V1', 1, 10000,
		'master:supplier:create', '新增供应商基础资料，不更新已有编码', 'ENABLED'),
	('MATERIAL_MASTER_V1', '物料历史主数据导入', 'MATERIAL', 'MATERIAL_MASTER_V1', 1, 10000,
		'master:material:import', '复用物料新增导入语义', 'ENABLED'),
	('BOM_DRAFT_V1', 'BOM 草稿历史导入', 'BOM_DRAFT', 'BOM_DRAFT_V1', 1, 5000,
		'material:bom:import', '创建或更新单个 BOM 草稿，不发布或应用 ECO', 'ENABLED'),
	('SALES_PROJECT_DRAFT_V1', '销售项目草稿历史导入', 'SALES_PROJECT', 'SALES_PROJECT_DRAFT_V1', 1, 10000,
		'sales:project:create', '仅新增销售项目草稿基础档案', 'ENABLED');

insert into platform_batch_tool_definition (
	tool_code, name, target_object_type, action_code, max_items, required_permission_code, description, status
)
values
	('CUSTOMER_STATUS_CHANGE_V1', '客户状态批量变更', 'CUSTOMER', 'STATUS_CHANGE', 1000,
		'master:customer:update', '客户启用或停用全有或全无批量工具', 'ENABLED'),
	('SUPPLIER_STATUS_CHANGE_V1', '供应商状态批量变更', 'SUPPLIER', 'STATUS_CHANGE', 1000,
		'master:supplier:update', '供应商启用或停用全有或全无批量工具', 'ENABLED'),
	('MATERIAL_STATUS_CHANGE_V1', '物料状态批量变更', 'MATERIAL', 'STATUS_CHANGE', 1000,
		'master:material:update', '物料启用或停用全有或全无批量工具', 'ENABLED'),
	('FIXED_DOCUMENT_BATCH_PRINT_V1', '固定单据批量打印', 'FIXED_DOCUMENT', 'BATCH_PRINT', 100,
		'platform:print:generate', '同一固定模板最多一百个对象的批量打印工具', 'ENABLED');

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
values ('PLATFORM_DATA_REPAIR_EXECUTION', '数据修复执行审批', 'DATA_REPAIR_REQUEST', 'EXECUTE', 1, 'ENABLED')
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
select d.id, 1, '固定审批', 'platform:data-repair:approve'
from platform_approval_definition d
where d.scene_code = 'PLATFORM_DATA_REPAIR_EXECUTION'
and not exists (
	select 1
	from platform_approval_definition_step s
	where s.definition_id = d.id
	  and s.step_no = 1
);

insert into platform_print_template (
	template_code, scene_code, name, object_type, template_version, status
)
values
	('SALES_ORDER_V1', 'SALES_ORDER_PRINT', '销售订单固定打印', 'SALES_ORDER', 1, 'ENABLED'),
	('SALES_SHIPMENT_V1', 'SALES_SHIPMENT_PRINT', '销售出库单固定打印', 'SALES_SHIPMENT', 1, 'ENABLED'),
	('PROCUREMENT_RECEIPT_V1', 'PROCUREMENT_RECEIPT_PRINT', '采购入库单固定打印', 'PROCUREMENT_RECEIPT', 1, 'ENABLED'),
	('INVENTORY_TRANSFER_V1', 'INVENTORY_TRANSFER_PRINT', '仓库调拨单固定打印', 'INVENTORY_TRANSFER', 1, 'ENABLED'),
	('PRODUCTION_WORK_ORDER_V1', 'PRODUCTION_WORK_ORDER_PRINT', '生产工单固定打印', 'PRODUCTION_WORK_ORDER', 1, 'ENABLED'),
	('PRODUCTION_MATERIAL_ISSUE_V1', 'PRODUCTION_MATERIAL_ISSUE_PRINT', '生产领料单固定打印', 'PRODUCTION_MATERIAL_ISSUE', 1, 'ENABLED'),
	('PRODUCTION_COMPLETION_RECEIPT_V1', 'PRODUCTION_COMPLETION_RECEIPT_PRINT', '完工入库单固定打印', 'PRODUCTION_COMPLETION_RECEIPT', 1, 'ENABLED'),
	('SALES_INVOICE_V1', 'SALES_INVOICE_PRINT', '销售发票固定打印', 'SALES_INVOICE', 1, 'ENABLED'),
	('PURCHASE_INVOICE_V1', 'PURCHASE_INVOICE_PRINT', '采购发票固定打印', 'PURCHASE_INVOICE', 1, 'ENABLED'),
	('ACCOUNTING_VOUCHER_V1', 'ACCOUNTING_VOUCHER_PRINT', '会计凭证固定打印', 'ACCOUNTING_VOUCHER', 1, 'ENABLED')
on conflict (template_code) do update
set scene_code = excluded.scene_code,
    name = excluded.name,
    object_type = excluded.object_type,
    template_version = excluded.template_version,
    status = excluded.status;
