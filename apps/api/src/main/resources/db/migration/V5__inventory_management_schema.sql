create table inv_stock_balance (
	id bigserial primary key,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity_on_hand numeric(18, 6) not null default 0,
	locked_quantity numeric(18, 6) not null default 0,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_inv_stock_balance_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_balance_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_balance_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_inv_stock_balance_warehouse_material unique (warehouse_id, material_id),
	constraint ck_inv_stock_balance_quantity_non_negative check (quantity_on_hand >= 0),
	constraint ck_inv_stock_balance_locked_non_negative check (locked_quantity >= 0)
);

create table inv_inventory_document (
	id bigserial primary key,
	document_no varchar(64) not null,
	document_type varchar(32) not null,
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
	constraint uk_inv_inventory_document_no unique (document_no),
	constraint ck_inv_inventory_document_type check (document_type in ('OPENING', 'ADJUSTMENT')),
	constraint ck_inv_inventory_document_status check (status in ('DRAFT', 'POSTED'))
);

create table inv_inventory_document_line (
	id bigserial primary key,
	document_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	adjustment_direction varchar(32),
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_inv_document_line_document foreign key (document_id) references inv_inventory_document (id) on delete cascade,
	constraint fk_inv_document_line_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_document_line_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_document_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_inv_document_line_no unique (document_id, line_no),
	constraint uk_inv_document_line_material unique (document_id, warehouse_id, material_id),
	constraint ck_inv_document_line_quantity_positive check (quantity > 0),
	constraint ck_inv_document_line_adjustment_direction check (adjustment_direction is null or adjustment_direction in ('INCREASE', 'DECREASE'))
);

create table inv_stock_movement (
	id bigserial primary key,
	movement_no varchar(64) not null,
	movement_type varchar(32) not null,
	direction varchar(32) not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6) not null,
	after_quantity numeric(18, 6) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	operator_name varchar(64) not null,
	occurred_at timestamp with time zone not null,
	constraint uk_inv_stock_movement_no unique (movement_no),
	constraint uk_inv_stock_movement_source unique (source_type, source_line_id),
	constraint fk_inv_stock_movement_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_movement_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_movement_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_inv_stock_movement_type check (movement_type in ('OPENING', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')),
	constraint ck_inv_stock_movement_direction check (direction in ('IN', 'OUT')),
	constraint ck_inv_stock_movement_quantity_positive check (quantity > 0),
	constraint ck_inv_stock_movement_before_non_negative check (before_quantity >= 0),
	constraint ck_inv_stock_movement_after_non_negative check (after_quantity >= 0)
);

create index idx_inv_stock_balance_warehouse on inv_stock_balance (warehouse_id);
create index idx_inv_stock_balance_material on inv_stock_balance (material_id);
create unique index uk_inv_stock_movement_opening_once on inv_stock_movement (warehouse_id, material_id) where movement_type = 'OPENING';
create index idx_inv_stock_movement_business_date on inv_stock_movement (business_date desc, id desc);
create index idx_inv_stock_movement_warehouse_material on inv_stock_movement (warehouse_id, material_id);
create index idx_inv_inventory_document_business_date on inv_inventory_document (business_date desc, id desc);
