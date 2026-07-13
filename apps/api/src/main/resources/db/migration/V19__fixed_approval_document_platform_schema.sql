alter table sys_audit_log
	add column if not exists detail_json jsonb;

create table platform_approval_definition (
	id bigserial primary key,
	scene_code varchar(80) not null,
	name varchar(120) not null,
	business_object_type varchar(64) not null,
	action_code varchar(64) not null,
	definition_version integer not null,
	status varchar(32) not null,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_platform_approval_definition_scene unique (scene_code),
	constraint ck_platform_approval_definition_status check (status in ('ENABLED', 'DISABLED'))
);

create table platform_approval_definition_step (
	id bigserial primary key,
	definition_id bigint not null,
	step_no integer not null,
	name varchar(120) not null,
	candidate_permission_code varchar(120) not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_approval_step_definition foreign key (definition_id) references platform_approval_definition (id),
	constraint uk_platform_approval_step_no unique (definition_id, step_no),
	constraint ck_platform_approval_step_no check (step_no > 0)
);

create table platform_approval_instance (
	id bigserial primary key,
	scene_code varchar(80) not null,
	definition_id bigint not null,
	definition_version integer not null,
	business_object_type varchar(64) not null,
	business_object_id bigint not null,
	business_object_no varchar(120) not null,
	business_object_summary varchar(255) not null,
	business_object_version bigint not null,
	status varchar(32) not null,
	submit_reason varchar(500) not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	submitted_by_user_id bigint not null,
	submitted_by_username varchar(64) not null,
	submitted_at timestamptz not null,
	completed_by_username varchar(64),
	completed_at timestamptz,
	completed_comment varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_platform_approval_instance_definition foreign key (definition_id) references platform_approval_definition (id),
	constraint ck_platform_approval_instance_status check (
		status in ('SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
	)
);

create unique index uk_platform_approval_instance_idempotency
	on platform_approval_instance (submitted_by_user_id, scene_code, idempotency_key);

create unique index uk_platform_approval_instance_open_object
	on platform_approval_instance (scene_code, business_object_type, business_object_id)
	where status = 'SUBMITTED';

create index idx_platform_approval_instance_object
	on platform_approval_instance (business_object_type, business_object_id, created_at desc);

create table platform_approval_task (
	id bigserial primary key,
	instance_id bigint not null,
	step_id bigint not null,
	step_no integer not null,
	candidate_permission_code varchar(120) not null,
	status varchar(32) not null,
	handled_by_user_id bigint,
	handled_by_username varchar(64),
	handled_at timestamptz,
	comment varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_platform_approval_task_instance foreign key (instance_id) references platform_approval_instance (id),
	constraint fk_platform_approval_task_step foreign key (step_id) references platform_approval_definition_step (id),
	constraint ck_platform_approval_task_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

create index idx_platform_approval_task_pending
	on platform_approval_task (status, candidate_permission_code, created_at desc);

create table platform_approval_history (
	id bigserial primary key,
	instance_id bigint not null,
	action varchar(64) not null,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	comment varchar(500),
	created_at timestamptz not null default now(),
	constraint fk_platform_approval_history_instance foreign key (instance_id) references platform_approval_instance (id)
);

create table platform_approval_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	action varchar(64) not null,
	resource_type varchar(32) not null,
	resource_id bigint not null,
	resource_version bigint not null,
	comment varchar(500),
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_instance_id bigint not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_approval_action_idempotency_instance foreign key (result_instance_id) references platform_approval_instance (id),
	constraint ck_platform_approval_action_resource check (resource_type in ('TASK', 'INSTANCE'))
);

create unique index uk_platform_approval_action_idempotency
	on platform_approval_action_idempotency (operator_user_id, action, resource_type, resource_id, idempotency_key);

create table platform_message (
	id bigserial primary key,
	recipient_user_id bigint not null,
	title varchar(160) not null,
	content varchar(500) not null,
	message_type varchar(64) not null,
	status varchar(32) not null,
	related_object_type varchar(64),
	related_object_id bigint,
	created_at timestamptz not null default now(),
	read_at timestamptz,
	version bigint not null default 0,
	constraint fk_platform_message_recipient foreign key (recipient_user_id) references sys_user (id),
	constraint ck_platform_message_status check (status in ('UNREAD', 'READ'))
);

create index idx_platform_message_recipient_status
	on platform_message (recipient_user_id, status, created_at desc);

create table platform_file_object (
	id bigserial primary key,
	bucket varchar(120) not null,
	object_key varchar(500) not null,
	original_filename varchar(255) not null,
	content_type varchar(120) not null,
	size_bytes bigint not null,
	sha256 varchar(64) not null,
	etag varchar(160),
	file_usage varchar(64) not null,
	status varchar(32) not null,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	deleted_by_username varchar(64),
	deleted_at timestamptz,
	version bigint not null default 0,
	constraint uk_platform_file_object_key unique (bucket, object_key),
	constraint ck_platform_file_object_status check (status in ('AVAILABLE', 'DELETED', 'EXPIRED')),
	constraint ck_platform_file_object_size check (size_bytes >= 0)
);

create index idx_platform_file_object_hash
	on platform_file_object (sha256, status);

create table platform_business_attachment (
	id bigserial primary key,
	object_type varchar(64) not null,
	object_id bigint not null,
	file_id bigint not null,
	description varchar(500),
	status varchar(32) not null,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	deleted_by_username varchar(64),
	deleted_at timestamptz,
	version bigint not null default 0,
	constraint fk_platform_business_attachment_file foreign key (file_id) references platform_file_object (id),
	constraint ck_platform_business_attachment_object check (
		object_type in ('SALES_PROJECT_CONTRACT', 'BOM_ENGINEERING_CHANGE')
	),
	constraint ck_platform_business_attachment_status check (status in ('AVAILABLE', 'DELETED'))
);

create index idx_platform_business_attachment_object
	on platform_business_attachment (object_type, object_id, status, created_at desc);

create table platform_approval_attachment_snapshot (
	id bigserial primary key,
	instance_id bigint not null,
	attachment_id bigint not null,
	file_id bigint not null,
	file_name varchar(255) not null,
	content_type varchar(120) not null,
	file_size bigint not null,
	sha256 varchar(64) not null,
	uploaded_by_username varchar(64) not null,
	uploaded_at timestamptz not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_approval_attachment_snapshot_instance foreign key (instance_id) references platform_approval_instance (id),
	constraint fk_platform_approval_attachment_snapshot_attachment foreign key (attachment_id) references platform_business_attachment (id),
	constraint fk_platform_approval_attachment_snapshot_file foreign key (file_id) references platform_file_object (id),
	constraint uk_platform_approval_attachment_snapshot unique (instance_id, attachment_id)
);

create index idx_platform_approval_attachment_snapshot_instance
	on platform_approval_attachment_snapshot (instance_id, id);

create table platform_document_task (
	id bigserial primary key,
	task_no varchar(64) not null,
	task_type varchar(64) not null,
	stage varchar(32) not null,
	status varchar(32) not null,
	request_payload jsonb,
	idempotency_key varchar(120) not null,
	source_file_id bigint,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	total_count integer not null default 0,
	success_count integer not null default 0,
	error_count integer not null default 0,
	result_file_id bigint,
	error_file_id bigint,
	error_summary varchar(500),
	lease_owner varchar(120),
	lease_until timestamptz,
	heartbeat_at timestamptz,
	attempt_count integer not null default 0,
	max_attempts integer not null default 3,
	next_run_at timestamptz,
	commit_idempotency_key varchar(120),
	commit_requested_at timestamptz,
	committed_at timestamptz,
	expires_at timestamptz,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	started_at timestamptz,
	finished_at timestamptz,
	version bigint not null default 0,
	constraint uk_platform_document_task_no unique (task_no),
	constraint uk_platform_document_task_idempotency unique (created_by_user_id, task_type, idempotency_key),
	constraint fk_platform_document_task_source_file foreign key (source_file_id) references platform_file_object (id),
	constraint fk_platform_document_task_result_file foreign key (result_file_id) references platform_file_object (id),
	constraint fk_platform_document_task_error_file foreign key (error_file_id) references platform_file_object (id),
	constraint ck_platform_document_task_status check (
		status in ('QUEUED', 'RUNNING', 'READY_TO_COMMIT', 'VALIDATION_FAILED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')
	),
	constraint ck_platform_document_task_stage check (stage in ('VALIDATE', 'COMMIT', 'EXPORT', 'PRINT'))
);

create index idx_platform_document_task_owner
	on platform_document_task (created_by_user_id, created_at desc);

create index idx_platform_document_task_lease
	on platform_document_task (status, next_run_at, lease_until);

create table platform_import_batch (
	id bigserial primary key,
	task_id bigint not null,
	import_type varchar(64) not null,
	mode varchar(32),
	source_file_id bigint not null,
	source_sha256 varchar(64) not null,
	status varchar(32) not null,
	target_object_id bigint,
	target_version bigint,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	commit_idempotency_key varchar(120),
	committed_at timestamptz,
	version bigint not null default 0,
	constraint uk_platform_import_batch_task unique (task_id),
	constraint fk_platform_import_batch_task foreign key (task_id) references platform_document_task (id),
	constraint fk_platform_import_batch_source_file foreign key (source_file_id) references platform_file_object (id),
	constraint ck_platform_import_batch_status check (
		status in ('QUEUED', 'VALIDATED', 'VALIDATION_FAILED', 'COMMITTING', 'COMMITTED', 'FAILED', 'CANCELLED')
	)
);

create table platform_import_row (
	id bigserial primary key,
	batch_id bigint not null,
	row_no integer not null,
	payload jsonb not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_import_row_batch foreign key (batch_id) references platform_import_batch (id)
);

create unique index uk_platform_import_row_no
	on platform_import_row (batch_id, row_no);

create table platform_import_error (
	id bigserial primary key,
	batch_id bigint not null,
	row_no integer,
	column_name varchar(120),
	error_code varchar(80) not null,
	message varchar(500) not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_import_error_batch foreign key (batch_id) references platform_import_batch (id)
);

create index idx_platform_import_error_batch
	on platform_import_error (batch_id, row_no, id);

create table platform_document_task_error (
	id bigserial primary key,
	task_id bigint not null,
	row_no integer,
	column_name varchar(120),
	error_code varchar(80) not null,
	message varchar(500) not null,
	created_at timestamptz not null default now(),
	constraint fk_platform_document_task_error_task foreign key (task_id) references platform_document_task (id)
);

create index idx_platform_document_task_error_task
	on platform_document_task_error (task_id, row_no, id);

create table platform_print_template (
	id bigserial primary key,
	template_code varchar(80) not null,
	scene_code varchar(80) not null,
	name varchar(120) not null,
	object_type varchar(64) not null,
	template_version integer not null,
	status varchar(32) not null,
	created_at timestamptz not null default now(),
	constraint uk_platform_print_template_code unique (template_code),
	constraint ck_platform_print_template_status check (status in ('ENABLED', 'DISABLED'))
);

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
values
	('SALES_PROJECT_CONTRACT_ACTIVATION', '销售项目合同生效审批', 'SALES_PROJECT_CONTRACT', 'ACTIVATE', 1, 'ENABLED'),
	('BOM_ECO_APPLICATION', 'BOM 工程变更应用审批', 'BOM_ENGINEERING_CHANGE', 'APPLY', 1, 'ENABLED');

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select id, 1, '固定审批', 'sales:contract:activate-approve'
from platform_approval_definition
where scene_code = 'SALES_PROJECT_CONTRACT_ACTIVATION';

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select id, 1, '固定审批', 'material:bom-eco:apply-approve'
from platform_approval_definition
where scene_code = 'BOM_ECO_APPLICATION';

insert into platform_print_template (
	template_code, scene_code, name, object_type, template_version, status
)
values
	('CONTRACT_ACTIVATION_APPROVAL_V1', 'SALES_PROJECT_CONTRACT_ACTIVATION', '合同生效审批单', 'APPROVAL_INSTANCE', 1, 'ENABLED'),
	('BOM_ECO_APPLICATION_APPROVAL_V1', 'BOM_ECO_APPLICATION', 'BOM ECO 应用审批单', 'APPROVAL_INSTANCE', 1, 'ENABLED');
