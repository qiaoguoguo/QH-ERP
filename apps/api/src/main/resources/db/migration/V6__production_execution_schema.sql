alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;

alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING',
		'ADJUSTMENT_INCREASE',
		'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE',
		'PRODUCTION_RECEIPT'
	));

create table mfg_work_order (
	id bigserial primary key,
	work_order_no varchar(64) not null,
	product_material_id bigint not null,
	bom_id bigint not null,
	planned_quantity numeric(18, 6) not null,
	reported_quantity numeric(18, 6) not null default 0,
	qualified_quantity numeric(18, 6) not null default 0,
	defective_quantity numeric(18, 6) not null default 0,
	received_quantity numeric(18, 6) not null default 0,
	issue_warehouse_id bigint not null,
	receipt_warehouse_id bigint not null,
	planned_start_date date not null,
	planned_finish_date date not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	released_by varchar(64),
	released_at timestamp with time zone,
	completed_by varchar(64),
	completed_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_mfg_work_order_no unique (work_order_no),
	constraint fk_mfg_work_order_product foreign key (product_material_id) references mst_material (id),
	constraint fk_mfg_work_order_bom foreign key (bom_id) references mfg_bom (id),
	constraint fk_mfg_work_order_issue_warehouse foreign key (issue_warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_work_order_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_work_order_status check (status in ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
	constraint ck_mfg_work_order_planned_quantity_positive check (planned_quantity > 0),
	constraint ck_mfg_work_order_reported_non_negative check (reported_quantity >= 0),
	constraint ck_mfg_work_order_qualified_non_negative check (qualified_quantity >= 0),
	constraint ck_mfg_work_order_defective_non_negative check (defective_quantity >= 0),
	constraint ck_mfg_work_order_received_non_negative check (received_quantity >= 0)
);

create table mfg_work_order_material (
	id bigserial primary key,
	work_order_id bigint not null,
	line_no integer not null,
	bom_item_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	required_quantity numeric(18, 6) not null,
	issued_quantity numeric(18, 6) not null default 0,
	loss_rate numeric(9, 6) not null default 0,
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_mfg_work_order_material_order foreign key (work_order_id) references mfg_work_order (id) on delete cascade,
	constraint fk_mfg_work_order_material_bom_item foreign key (bom_item_id) references mfg_bom_item (id),
	constraint fk_mfg_work_order_material_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_work_order_material_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_work_order_material_line unique (work_order_id, line_no),
	constraint uk_mfg_work_order_material_bom_item unique (work_order_id, bom_item_id),
	constraint ck_mfg_work_order_material_required_positive check (required_quantity > 0),
	constraint ck_mfg_work_order_material_issued_non_negative check (issued_quantity >= 0),
	constraint ck_mfg_work_order_material_loss_rate_range check (loss_rate >= 0 and loss_rate < 1)
);

create table mfg_material_issue (
	id bigserial primary key,
	issue_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_mfg_material_issue_no unique (issue_no),
	constraint fk_mfg_material_issue_order foreign key (work_order_id) references mfg_work_order (id),
	constraint ck_mfg_material_issue_status check (status in ('DRAFT', 'POSTED'))
);

create table mfg_material_issue_line (
	id bigserial primary key,
	issue_id bigint not null,
	work_order_material_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_mfg_material_issue_line_issue foreign key (issue_id) references mfg_material_issue (id) on delete cascade,
	constraint fk_mfg_material_issue_line_order_material foreign key (work_order_material_id) references mfg_work_order_material (id),
	constraint fk_mfg_material_issue_line_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_mfg_material_issue_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_material_issue_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_mfg_material_issue_line_no unique (issue_id, line_no),
	constraint uk_mfg_material_issue_line_order_material unique (issue_id, work_order_material_id),
	constraint ck_mfg_material_issue_line_quantity_positive check (quantity > 0),
	constraint ck_mfg_material_issue_line_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_mfg_material_issue_line_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create table mfg_work_report (
	id bigserial primary key,
	report_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	qualified_quantity numeric(18, 6) not null,
	defective_quantity numeric(18, 6) not null,
	reporter_name varchar(64) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_mfg_work_report_no unique (report_no),
	constraint fk_mfg_work_report_order foreign key (work_order_id) references mfg_work_order (id),
	constraint ck_mfg_work_report_status check (status in ('DRAFT', 'POSTED')),
	constraint ck_mfg_work_report_qualified_non_negative check (qualified_quantity >= 0),
	constraint ck_mfg_work_report_defective_non_negative check (defective_quantity >= 0),
	constraint ck_mfg_work_report_total_positive check ((qualified_quantity + defective_quantity) > 0)
);

create table mfg_completion_receipt (
	id bigserial primary key,
	receipt_no varchar(64) not null,
	work_order_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	receipt_warehouse_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_mfg_completion_receipt_no unique (receipt_no),
	constraint fk_mfg_completion_receipt_order foreign key (work_order_id) references mfg_work_order (id),
	constraint fk_mfg_completion_receipt_warehouse foreign key (receipt_warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_completion_receipt_status check (status in ('DRAFT', 'POSTED')),
	constraint ck_mfg_completion_receipt_quantity_positive check (quantity > 0),
	constraint ck_mfg_completion_receipt_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_mfg_completion_receipt_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create index idx_mfg_work_order_status on mfg_work_order (status, updated_at desc);
create index idx_mfg_work_order_product on mfg_work_order (product_material_id);
create index idx_mfg_work_order_material_order on mfg_work_order_material (work_order_id);
create index idx_mfg_material_issue_order on mfg_material_issue (work_order_id, updated_at desc);
create index idx_mfg_work_report_order on mfg_work_report (work_order_id, updated_at desc);
create index idx_mfg_completion_receipt_order on mfg_completion_receipt (work_order_id, updated_at desc);
