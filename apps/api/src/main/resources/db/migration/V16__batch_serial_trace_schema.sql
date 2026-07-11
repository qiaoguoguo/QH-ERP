alter table mst_material add column tracking_method varchar(32);
update mst_material set tracking_method = 'NONE' where tracking_method is null;
alter table mst_material alter column tracking_method set default 'NONE';
alter table mst_material alter column tracking_method set not null;
alter table mst_material add constraint ck_mst_material_tracking_method
	check (tracking_method in ('NONE', 'BATCH', 'SERIAL'));
create index idx_mst_material_tracking_method on mst_material (tracking_method);

create table inv_batch (
	id bigserial primary key,
	material_id bigint not null,
	batch_no varchar(100) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	business_date date not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint uk_inv_batch_material_no unique (material_id, batch_no),
	constraint fk_inv_batch_material foreign key (material_id) references mst_material (id),
	constraint ck_inv_batch_no_not_blank check (length(trim(batch_no)) > 0)
);

create index idx_inv_batch_material on inv_batch (material_id);
create index idx_inv_batch_source on inv_batch (source_type, source_id, source_line_id);

create table inv_serial (
	id bigserial primary key,
	material_id bigint not null,
	serial_no varchar(100) not null,
	batch_id bigint,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	warehouse_id bigint,
	quality_status varchar(32),
	stock_status varchar(32) not null default 'IN_STOCK',
	last_movement_id bigint,
	business_date date not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint uk_inv_serial_material_no unique (material_id, serial_no),
	constraint fk_inv_serial_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_serial_batch foreign key (batch_id) references inv_batch (id),
	constraint fk_inv_serial_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_serial_last_movement foreign key (last_movement_id) references inv_stock_movement (id),
	constraint ck_inv_serial_no_not_blank check (length(trim(serial_no)) > 0),
	constraint ck_inv_serial_quality_status check (
		quality_status is null or quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN')
	),
	constraint ck_inv_serial_stock_status check (
		stock_status in ('IN_STOCK', 'RESERVED', 'OCCUPIED', 'OUTBOUND', 'CANCELLED')
	)
);

create index idx_inv_serial_material on inv_serial (material_id);
create index idx_inv_serial_batch on inv_serial (batch_id);
create index idx_inv_serial_stock_status on inv_serial (stock_status);
create index idx_inv_serial_source on inv_serial (source_type, source_id, source_line_id);

alter table inv_stock_balance add column batch_id bigint;
alter table inv_stock_balance add column serial_id bigint;
alter table inv_stock_balance add constraint fk_inv_stock_balance_batch
	foreign key (batch_id) references inv_batch (id);
alter table inv_stock_balance add constraint fk_inv_stock_balance_serial
	foreign key (serial_id) references inv_serial (id);
alter table inv_stock_balance drop constraint uk_inv_stock_balance_warehouse_material_quality;
create unique index uk_inv_stock_balance_untracked
	on inv_stock_balance (warehouse_id, material_id, quality_status)
	where batch_id is null and serial_id is null;
create unique index uk_inv_stock_balance_batch
	on inv_stock_balance (warehouse_id, material_id, quality_status, batch_id)
	where batch_id is not null and serial_id is null;
create unique index uk_inv_stock_balance_serial
	on inv_stock_balance (warehouse_id, material_id, quality_status, serial_id)
	where serial_id is not null;
create index idx_inv_stock_balance_batch on inv_stock_balance (batch_id);
create index idx_inv_stock_balance_serial on inv_stock_balance (serial_id);
alter table inv_stock_balance add constraint ck_inv_stock_balance_serial_quantity
	check (serial_id is null or quantity_on_hand <= 1);

alter table inv_stock_movement add column batch_id bigint;
alter table inv_stock_movement add column serial_id bigint;
alter table inv_stock_movement add constraint fk_inv_stock_movement_batch
	foreign key (batch_id) references inv_batch (id);
alter table inv_stock_movement add constraint fk_inv_stock_movement_serial
	foreign key (serial_id) references inv_serial (id);
alter table inv_stock_movement drop constraint uk_inv_stock_movement_source;
create unique index uk_inv_stock_movement_source_untracked
	on inv_stock_movement (source_type, source_line_id)
	where batch_id is null and serial_id is null;
create unique index uk_inv_stock_movement_source_batch
	on inv_stock_movement (source_type, source_line_id, batch_id)
	where batch_id is not null and serial_id is null;
create unique index uk_inv_stock_movement_source_serial
	on inv_stock_movement (source_type, source_line_id, serial_id)
	where serial_id is not null;
create index idx_inv_stock_movement_batch on inv_stock_movement (batch_id);
create index idx_inv_stock_movement_serial on inv_stock_movement (serial_id);

alter table inv_stock_reservation add column batch_id bigint;
alter table inv_stock_reservation add column serial_id bigint;
alter table inv_stock_reservation add column parent_reservation_id bigint;
alter table inv_stock_reservation add constraint fk_inv_stock_reservation_batch
	foreign key (batch_id) references inv_batch (id);
alter table inv_stock_reservation add constraint fk_inv_stock_reservation_serial
	foreign key (serial_id) references inv_serial (id);
alter table inv_stock_reservation add constraint fk_inv_stock_reservation_parent
	foreign key (parent_reservation_id) references inv_stock_reservation (id);
drop index if exists uk_inv_stock_reservation_active_source;
create unique index uk_inv_stock_reservation_active_source_untracked
	on inv_stock_reservation (reservation_type, source_type, source_line_id)
	where status = 'ACTIVE' and batch_id is null and serial_id is null;
create unique index uk_inv_stock_reservation_active_source_batch
	on inv_stock_reservation (reservation_type, source_type, source_line_id, batch_id)
	where status = 'ACTIVE' and batch_id is not null and serial_id is null;
create unique index uk_inv_stock_reservation_active_source_serial
	on inv_stock_reservation (reservation_type, source_type, source_line_id, serial_id)
	where status = 'ACTIVE' and serial_id is not null;
create index idx_inv_stock_reservation_batch on inv_stock_reservation (batch_id);
create index idx_inv_stock_reservation_serial on inv_stock_reservation (serial_id);
create index idx_inv_stock_reservation_parent on inv_stock_reservation (parent_reservation_id);

create table inv_stock_tracking_allocation (
	id bigserial primary key,
	allocation_type varchar(32) not null,
	document_type varchar(64) not null,
	document_id bigint not null,
	document_line_id bigint not null,
	source_type varchar(64),
	source_id bigint,
	source_line_id bigint,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quality_status varchar(32) not null,
	batch_id bigint,
	serial_id bigint,
	quantity numeric(18, 6) not null,
	movement_id bigint,
	reservation_id bigint,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_inv_stock_tracking_allocation_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_tracking_allocation_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_tracking_allocation_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_inv_stock_tracking_allocation_batch foreign key (batch_id) references inv_batch (id),
	constraint fk_inv_stock_tracking_allocation_serial foreign key (serial_id) references inv_serial (id),
	constraint fk_inv_stock_tracking_allocation_movement foreign key (movement_id) references inv_stock_movement (id),
	constraint fk_inv_stock_tracking_allocation_reservation
		foreign key (reservation_id) references inv_stock_reservation (id),
	constraint ck_inv_stock_tracking_allocation_type check (
		allocation_type in ('INBOUND', 'OUTBOUND', 'QUALITY_TRANSFER', 'SOURCE_INHERIT')
	),
	constraint ck_inv_stock_tracking_allocation_quality_status check (
		quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN')
	),
	constraint ck_inv_stock_tracking_allocation_quantity_positive check (quantity > 0),
	constraint ck_inv_stock_tracking_allocation_serial_quantity check (serial_id is null or quantity = 1)
);

create index idx_inv_stock_tracking_allocation_document
	on inv_stock_tracking_allocation (document_type, document_id, document_line_id);
create index idx_inv_stock_tracking_allocation_source
	on inv_stock_tracking_allocation (source_type, source_id, source_line_id);
create index idx_inv_stock_tracking_allocation_batch on inv_stock_tracking_allocation (batch_id);
create index idx_inv_stock_tracking_allocation_serial on inv_stock_tracking_allocation (serial_id);
