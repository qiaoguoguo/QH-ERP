alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;

alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING',
		'ADJUSTMENT_INCREASE',
		'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE',
		'PRODUCTION_RECEIPT',
		'PURCHASE_RECEIPT',
		'SALES_SHIPMENT'
	));

create table sal_sales_order (
	id bigserial primary key,
	order_no varchar(64) not null,
	customer_id bigint not null,
	order_date date not null,
	expected_ship_date date,
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
	constraint uk_sal_sales_order_no unique (order_no),
	constraint fk_sal_sales_order_customer foreign key (customer_id) references mst_customer (id),
	constraint ck_sal_sales_order_status check (status in (
		'DRAFT',
		'CONFIRMED',
		'PARTIALLY_SHIPPED',
		'SHIPPED',
		'CLOSED',
		'CANCELLED'
	))
);

create table sal_sales_order_line (
	id bigserial primary key,
	order_id bigint not null,
	line_no integer not null,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	shipped_quantity numeric(18, 6) not null default 0,
	unit_price numeric(18, 6) not null,
	expected_ship_date date,
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint fk_sal_sales_order_line_order foreign key (order_id) references sal_sales_order (id) on delete cascade,
	constraint fk_sal_sales_order_line_material foreign key (material_id) references mst_material (id),
	constraint fk_sal_sales_order_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_sal_sales_order_line_no unique (order_id, line_no),
	constraint uk_sal_sales_order_line_material unique (order_id, material_id),
	constraint ck_sal_sales_order_line_quantity_positive check (quantity > 0),
	constraint ck_sal_sales_order_line_shipped_range check (shipped_quantity >= 0 and shipped_quantity <= quantity),
	constraint ck_sal_sales_order_line_unit_price_non_negative check (unit_price >= 0)
);

create table sal_sales_shipment (
	id bigserial primary key,
	shipment_no varchar(64) not null,
	order_id bigint not null,
	customer_id bigint not null,
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
	constraint uk_sal_sales_shipment_no unique (shipment_no),
	constraint fk_sal_sales_shipment_order foreign key (order_id) references sal_sales_order (id),
	constraint fk_sal_sales_shipment_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_sal_sales_shipment_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_sal_sales_shipment_status check (status in ('DRAFT', 'POSTED'))
);

create table sal_sales_shipment_line (
	id bigserial primary key,
	shipment_id bigint not null,
	line_no integer not null,
	order_line_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	ordered_quantity numeric(18, 6) not null,
	shipped_quantity_before numeric(18, 6) not null,
	remaining_quantity_before numeric(18, 6) not null,
	quantity numeric(18, 6) not null,
	before_quantity numeric(18, 6),
	after_quantity numeric(18, 6),
	remark varchar(500),
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_sal_sales_shipment_line_shipment foreign key (shipment_id) references sal_sales_shipment (id) on delete cascade,
	constraint fk_sal_sales_shipment_line_order_line foreign key (order_line_id) references sal_sales_order_line (id),
	constraint fk_sal_sales_shipment_line_material foreign key (material_id) references mst_material (id),
	constraint fk_sal_sales_shipment_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_sal_sales_shipment_line_no unique (shipment_id, line_no),
	constraint uk_sal_sales_shipment_line_order_line unique (shipment_id, order_line_id),
	constraint ck_sal_sales_shipment_line_ordered_positive check (ordered_quantity > 0),
	constraint ck_sal_sales_shipment_line_shipped_before_range check (
		shipped_quantity_before >= 0 and shipped_quantity_before <= ordered_quantity
	),
	constraint ck_sal_sales_shipment_line_remaining_before_non_negative check (remaining_quantity_before >= 0),
	constraint ck_sal_sales_shipment_line_quantity_positive check (quantity > 0),
	constraint ck_sal_sales_shipment_line_quantity_within_remaining check (quantity <= remaining_quantity_before),
	constraint ck_sal_sales_shipment_line_before_non_negative check (before_quantity is null or before_quantity >= 0),
	constraint ck_sal_sales_shipment_line_after_non_negative check (after_quantity is null or after_quantity >= 0)
);

create index idx_sal_sales_order_customer on sal_sales_order (customer_id);
create index idx_sal_sales_order_status_date on sal_sales_order (status, order_date desc, id desc);
create index idx_sal_sales_order_expected_date on sal_sales_order (expected_ship_date desc, id desc);
create index idx_sal_sales_order_line_order on sal_sales_order_line (order_id);
create index idx_sal_sales_order_line_material on sal_sales_order_line (material_id);
create index idx_sal_sales_shipment_order on sal_sales_shipment (order_id);
create index idx_sal_sales_shipment_customer on sal_sales_shipment (customer_id);
create index idx_sal_sales_shipment_warehouse on sal_sales_shipment (warehouse_id);
create index idx_sal_sales_shipment_status_date on sal_sales_shipment (status, business_date desc, id desc);
create index idx_sal_sales_shipment_line_shipment on sal_sales_shipment_line (shipment_id);
create index idx_sal_sales_shipment_line_order_line_ref on sal_sales_shipment_line (order_line_id);
