create table inv_stock_reservation (
	id bigserial primary key,
	reservation_no varchar(64) not null,
	reservation_type varchar(32) not null,
	status varchar(32) not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quality_status varchar(32) not null,
	quantity numeric(18, 6) not null,
	released_quantity numeric(18, 6) not null default 0,
	consumed_quantity numeric(18, 6) not null default 0,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	source_document_no varchar(64) not null,
	business_date date not null,
	reason varchar(200) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	released_by varchar(64),
	released_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_inv_stock_reservation_no unique (reservation_no),
	constraint fk_inv_stock_reservation_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stock_reservation_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stock_reservation_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_inv_stock_reservation_type check (reservation_type in ('RESERVATION', 'OCCUPATION')),
	constraint ck_inv_stock_reservation_status check (status in ('ACTIVE', 'RELEASED', 'CONSUMED', 'CANCELLED')),
	constraint ck_inv_stock_reservation_quality_status check (
		quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN')
	),
	constraint ck_inv_stock_reservation_quantities check (
		quantity > 0
		and released_quantity >= 0
		and consumed_quantity >= 0
		and released_quantity + consumed_quantity <= quantity
	)
);

create unique index uk_inv_stock_reservation_active_source
	on inv_stock_reservation (reservation_type, source_type, source_line_id)
	where status = 'ACTIVE';

create index idx_inv_stock_reservation_warehouse_material
	on inv_stock_reservation (warehouse_id, material_id, status);

create index idx_inv_stock_reservation_source
	on inv_stock_reservation (source_type, source_id, source_line_id);

create index idx_inv_stock_reservation_status_date
	on inv_stock_reservation (status, business_date desc, id desc);

alter table sal_sales_order_line
	add column reservation_warehouse_id bigint,
	add constraint fk_sal_sales_order_line_reservation_warehouse
		foreign key (reservation_warehouse_id) references mst_warehouse (id);

create index idx_sal_sales_order_line_reservation_warehouse
	on sal_sales_order_line (reservation_warehouse_id);
