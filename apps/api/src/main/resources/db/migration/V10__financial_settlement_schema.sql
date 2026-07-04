create table fin_receivable (
	id bigserial primary key,
	receivable_no varchar(64) not null,
	customer_id bigint not null,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	business_date date not null,
	due_date date not null,
	total_amount numeric(18, 2) not null,
	received_amount numeric(18, 2) not null default 0,
	unreceived_amount numeric(18, 2) not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	confirmed_by varchar(64),
	confirmed_at timestamp with time zone,
	closed_by varchar(64),
	closed_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_fin_receivable_no unique (receivable_no),
	constraint fk_fin_receivable_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_fin_receivable_source_shipment foreign key (source_id) references sal_sales_shipment (id),
	constraint ck_fin_receivable_source_type check (source_type = 'SALES_SHIPMENT'),
	constraint ck_fin_receivable_status check (status in (
		'DRAFT',
		'CONFIRMED',
		'PARTIALLY_RECEIVED',
		'RECEIVED',
		'CLOSED',
		'CANCELLED'
	)),
	constraint ck_fin_receivable_amount_non_negative check (
		total_amount >= 0 and received_amount >= 0 and unreceived_amount >= 0
	),
	constraint ck_fin_receivable_amount_balance check (total_amount = received_amount + unreceived_amount)
);

create table fin_receivable_source (
	id bigserial primary key,
	receivable_id bigint not null,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	source_line_id bigint not null,
	source_line_no integer not null,
	source_amount numeric(18, 2) not null,
	constraint fk_fin_receivable_source_receivable foreign key (receivable_id) references fin_receivable (id) on delete cascade,
	constraint fk_fin_receivable_source_shipment foreign key (source_id) references sal_sales_shipment (id),
	constraint fk_fin_receivable_source_shipment_line foreign key (source_line_id) references sal_sales_shipment_line (id),
	constraint uk_fin_receivable_source_line unique (source_type, source_id, source_line_id),
	constraint ck_fin_receivable_source_type check (source_type = 'SALES_SHIPMENT'),
	constraint ck_fin_receivable_source_amount_positive check (source_amount > 0)
);

create table fin_receipt (
	id bigserial primary key,
	receipt_no varchar(64) not null,
	customer_id bigint not null,
	receipt_date date not null,
	amount numeric(18, 2) not null,
	method varchar(32) not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_fin_receipt_no unique (receipt_no),
	constraint fk_fin_receipt_customer foreign key (customer_id) references mst_customer (id),
	constraint ck_fin_receipt_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_receipt_amount_positive check (amount > 0)
);

create table fin_receipt_allocation (
	id bigserial primary key,
	receipt_id bigint not null,
	receivable_id bigint not null,
	allocated_amount numeric(18, 2) not null,
	constraint fk_fin_receipt_allocation_receipt foreign key (receipt_id) references fin_receipt (id) on delete cascade,
	constraint fk_fin_receipt_allocation_receivable foreign key (receivable_id) references fin_receivable (id),
	constraint uk_fin_receipt_allocation_receipt unique (receipt_id),
	constraint ck_fin_receipt_allocation_amount_positive check (allocated_amount > 0)
);

create table fin_payable (
	id bigserial primary key,
	payable_no varchar(64) not null,
	supplier_id bigint not null,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	business_date date not null,
	due_date date not null,
	total_amount numeric(18, 2) not null,
	paid_amount numeric(18, 2) not null default 0,
	unpaid_amount numeric(18, 2) not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	confirmed_by varchar(64),
	confirmed_at timestamp with time zone,
	closed_by varchar(64),
	closed_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_fin_payable_no unique (payable_no),
	constraint fk_fin_payable_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_fin_payable_source_receipt foreign key (source_id) references proc_purchase_receipt (id),
	constraint ck_fin_payable_source_type check (source_type = 'PURCHASE_RECEIPT'),
	constraint ck_fin_payable_status check (status in (
		'DRAFT',
		'CONFIRMED',
		'PARTIALLY_PAID',
		'PAID',
		'CLOSED',
		'CANCELLED'
	)),
	constraint ck_fin_payable_amount_non_negative check (
		total_amount >= 0 and paid_amount >= 0 and unpaid_amount >= 0
	),
	constraint ck_fin_payable_amount_balance check (total_amount = paid_amount + unpaid_amount)
);

create table fin_payable_source (
	id bigserial primary key,
	payable_id bigint not null,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	source_line_id bigint not null,
	source_line_no integer not null,
	source_amount numeric(18, 2) not null,
	constraint fk_fin_payable_source_payable foreign key (payable_id) references fin_payable (id) on delete cascade,
	constraint fk_fin_payable_source_receipt foreign key (source_id) references proc_purchase_receipt (id),
	constraint fk_fin_payable_source_receipt_line foreign key (source_line_id) references proc_purchase_receipt_line (id),
	constraint uk_fin_payable_source_line unique (source_type, source_id, source_line_id),
	constraint ck_fin_payable_source_type check (source_type = 'PURCHASE_RECEIPT'),
	constraint ck_fin_payable_source_amount_positive check (source_amount > 0)
);

create table fin_payment (
	id bigserial primary key,
	payment_no varchar(64) not null,
	supplier_id bigint not null,
	payment_date date not null,
	amount numeric(18, 2) not null,
	method varchar(32) not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_fin_payment_no unique (payment_no),
	constraint fk_fin_payment_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint ck_fin_payment_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_payment_amount_positive check (amount > 0)
);

create table fin_payment_allocation (
	id bigserial primary key,
	payment_id bigint not null,
	payable_id bigint not null,
	allocated_amount numeric(18, 2) not null,
	constraint fk_fin_payment_allocation_payment foreign key (payment_id) references fin_payment (id) on delete cascade,
	constraint fk_fin_payment_allocation_payable foreign key (payable_id) references fin_payable (id),
	constraint uk_fin_payment_allocation_payment unique (payment_id),
	constraint ck_fin_payment_allocation_amount_positive check (allocated_amount > 0)
);

create index idx_fin_receivable_customer on fin_receivable (customer_id);
create index idx_fin_receivable_status_date on fin_receivable (status, business_date desc, id desc);
create index idx_fin_receivable_due_date on fin_receivable (due_date desc, id desc);
create index idx_fin_receivable_source_no on fin_receivable (source_no);
create index idx_fin_receivable_source_receivable on fin_receivable_source (receivable_id);
create index idx_fin_receivable_source_source on fin_receivable_source (source_type, source_id);
create index idx_fin_receipt_customer on fin_receipt (customer_id);
create index idx_fin_receipt_status_date on fin_receipt (status, receipt_date desc, id desc);
create index idx_fin_receipt_allocation_receivable on fin_receipt_allocation (receivable_id);

create index idx_fin_payable_supplier on fin_payable (supplier_id);
create index idx_fin_payable_status_date on fin_payable (status, business_date desc, id desc);
create index idx_fin_payable_due_date on fin_payable (due_date desc, id desc);
create index idx_fin_payable_source_no on fin_payable (source_no);
create index idx_fin_payable_source_payable on fin_payable_source (payable_id);
create index idx_fin_payable_source_source on fin_payable_source (source_type, source_id);
create index idx_fin_payment_supplier on fin_payment (supplier_id);
create index idx_fin_payment_status_date on fin_payment (status, payment_date desc, id desc);
create index idx_fin_payment_allocation_payable on fin_payment_allocation (payable_id);
