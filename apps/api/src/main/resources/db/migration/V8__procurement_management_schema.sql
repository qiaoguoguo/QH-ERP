alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;

alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING',
		'ADJUSTMENT_INCREASE',
		'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE',
		'PRODUCTION_RECEIPT',
		'PURCHASE_RECEIPT'
	));

create table proc_purchase_order (
	id bigserial primary key,
	order_no varchar(64) not null,
	supplier_id bigint not null,
	order_date date not null,
	expected_arrival_date date,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	confirmed_by varchar(64),
	confirmed_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	closed_by varchar(64),
	closed_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_proc_purchase_order_no unique (order_no),
	constraint fk_proc_purchase_order_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint ck_proc_purchase_order_status check (status in (
		'DRAFT',
		'CONFIRMED',
		'PARTIALLY_RECEIVED',
		'RECEIVED',
		'CLOSED',
		'CANCELLED'
	))
);

create table proc_purchase_order_line (
	id bigserial primary key,
	order_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	received_quantity numeric(18, 6) not null default 0,
	unit_price numeric(18, 6) not null,
	expected_arrival_date date,
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_proc_purchase_order_line_order foreign key (order_id) references proc_purchase_order (id) on delete cascade,
	constraint fk_proc_purchase_order_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_purchase_order_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_proc_purchase_order_line_no unique (order_id, line_no),
	constraint uk_proc_purchase_order_line_material unique (order_id, material_id),
	constraint ck_proc_purchase_order_line_quantity_positive check (quantity > 0),
	constraint ck_proc_purchase_order_line_received_range check (received_quantity >= 0 and received_quantity <= quantity),
	constraint ck_proc_purchase_order_line_unit_price_non_negative check (unit_price >= 0)
);

create table proc_purchase_receipt (
	id bigserial primary key,
	receipt_no varchar(64) not null,
	order_id bigint not null,
	supplier_id bigint not null,
	warehouse_id bigint not null,
	business_date date not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_proc_purchase_receipt_no unique (receipt_no),
	constraint fk_proc_purchase_receipt_order foreign key (order_id) references proc_purchase_order (id),
	constraint fk_proc_purchase_receipt_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_proc_purchase_receipt_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_proc_purchase_receipt_status check (status in ('DRAFT', 'POSTED'))
);

create table proc_purchase_receipt_line (
	id bigserial primary key,
	receipt_id bigint not null,
	line_no integer not null,
	order_line_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	ordered_quantity numeric(18, 6) not null,
	received_quantity_before numeric(18, 6) not null,
	remaining_quantity_before numeric(18, 6) not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_proc_purchase_receipt_line_receipt foreign key (receipt_id) references proc_purchase_receipt (id) on delete cascade,
	constraint fk_proc_purchase_receipt_line_order_line foreign key (order_line_id) references proc_purchase_order_line (id),
	constraint fk_proc_purchase_receipt_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_purchase_receipt_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_proc_purchase_receipt_line_no unique (receipt_id, line_no),
	constraint uk_proc_purchase_receipt_line_order_line unique (receipt_id, order_line_id),
	constraint ck_proc_purchase_receipt_line_ordered_positive check (ordered_quantity > 0),
	constraint ck_proc_purchase_receipt_line_received_before_non_negative check (received_quantity_before >= 0),
	constraint ck_proc_purchase_receipt_line_remaining_before_non_negative check (remaining_quantity_before >= 0),
	constraint ck_proc_purchase_receipt_line_quantity_positive check (quantity > 0),
	constraint ck_proc_purchase_receipt_line_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_proc_purchase_receipt_line_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create index idx_proc_purchase_order_supplier on proc_purchase_order (supplier_id);
create index idx_proc_purchase_order_status_date on proc_purchase_order (status, order_date desc, id desc);
create index idx_proc_purchase_order_expected_date on proc_purchase_order (expected_arrival_date desc, id desc);
create index idx_proc_purchase_order_line_order on proc_purchase_order_line (order_id);
create index idx_proc_purchase_order_line_material on proc_purchase_order_line (material_id);
create index idx_proc_purchase_receipt_order on proc_purchase_receipt (order_id);
create index idx_proc_purchase_receipt_supplier on proc_purchase_receipt (supplier_id);
create index idx_proc_purchase_receipt_warehouse on proc_purchase_receipt (warehouse_id);
create index idx_proc_purchase_receipt_status_date on proc_purchase_receipt (status, business_date desc, id desc);
create index idx_proc_purchase_receipt_line_receipt on proc_purchase_receipt_line (receipt_id);
create index idx_proc_purchase_receipt_line_order_line_ref on proc_purchase_receipt_line (order_line_id);
