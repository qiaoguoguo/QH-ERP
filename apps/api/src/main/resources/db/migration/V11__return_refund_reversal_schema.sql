alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;
alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING',
		'ADJUSTMENT_INCREASE',
		'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE',
		'PRODUCTION_RECEIPT',
		'PURCHASE_RECEIPT',
		'SALES_SHIPMENT',
		'SALES_RETURN_IN',
		'PURCHASE_RETURN_OUT',
		'PRODUCTION_MATERIAL_RETURN_IN',
		'PRODUCTION_MATERIAL_SUPPLEMENT_OUT',
		'BUSINESS_REVERSAL'
	));

alter table fin_receivable drop constraint ck_fin_receivable_amount_balance;
alter table fin_receivable drop constraint ck_fin_receivable_amount_non_negative;
alter table fin_receivable add column adjusted_amount numeric(18, 2) not null default 0;
alter table fin_receivable add constraint ck_fin_receivable_amount_non_negative check (
	total_amount >= 0 and received_amount >= 0 and adjusted_amount >= 0 and unreceived_amount >= 0
);
alter table fin_receivable add constraint ck_fin_receivable_amount_balance check (
	total_amount = received_amount + adjusted_amount + unreceived_amount
);

alter table fin_payable drop constraint ck_fin_payable_amount_balance;
alter table fin_payable drop constraint ck_fin_payable_amount_non_negative;
alter table fin_payable add column adjusted_amount numeric(18, 2) not null default 0;
alter table fin_payable add constraint ck_fin_payable_amount_non_negative check (
	total_amount >= 0 and paid_amount >= 0 and adjusted_amount >= 0 and unpaid_amount >= 0
);
alter table fin_payable add constraint ck_fin_payable_amount_balance check (
	total_amount = paid_amount + adjusted_amount + unpaid_amount
);

alter table mfg_cost_record drop constraint ck_mfg_cost_record_source_document_type;
alter table mfg_cost_record add constraint ck_mfg_cost_record_source_document_type check (source_document_type in (
	'PRODUCTION_MATERIAL_ISSUE',
	'PRODUCTION_WORK_REPORT',
	'PRODUCTION_COMPLETION_RECEIPT',
	'MANUAL_COST_RECORD',
	'PRODUCTION_MATERIAL_RETURN',
	'PRODUCTION_MATERIAL_SUPPLEMENT'
));

create table biz_reversal_link (
	id bigserial primary key,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null default 0,
	reverse_type varchar(64) not null,
	reverse_id bigint not null,
	reverse_line_id bigint not null default 0,
	business_date date not null,
	quantity numeric(18, 6),
	amount numeric(18, 2),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	constraint ck_biz_reversal_link_line_non_negative check (source_line_id >= 0 and reverse_line_id >= 0),
	constraint ck_biz_reversal_link_quantity_positive check (quantity is null or quantity > 0),
	constraint ck_biz_reversal_link_amount_positive check (amount is null or amount > 0),
	constraint ck_biz_reversal_link_has_quantity_or_amount check (quantity is not null or amount is not null)
);

create unique index uk_biz_reversal_link_reverse_line
	on biz_reversal_link (reverse_type, reverse_id, reverse_line_id);
create unique index uk_biz_reversal_link_source_reverse
	on biz_reversal_link (source_type, source_id, source_line_id, reverse_type, reverse_id, reverse_line_id);
create index idx_biz_reversal_link_source
	on biz_reversal_link (source_type, source_id, source_line_id);
create index idx_biz_reversal_link_reverse
	on biz_reversal_link (reverse_type, reverse_id);

create table sal_sales_return (
	id bigserial primary key,
	return_no varchar(64) not null,
	customer_id bigint not null,
	source_shipment_id bigint not null,
	source_shipment_no varchar(64) not null,
	warehouse_id bigint not null,
	business_date date not null,
	status varchar(32) not null,
	total_amount numeric(18, 2) not null default 0,
	client_request_id varchar(64),
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
	constraint uk_sal_sales_return_no unique (return_no),
	constraint fk_sal_sales_return_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_sal_sales_return_source_shipment foreign key (source_shipment_id) references sal_sales_shipment (id),
	constraint fk_sal_sales_return_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_sal_sales_return_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_sal_sales_return_total_amount_non_negative check (total_amount >= 0)
);

create table sal_sales_return_line (
	id bigserial primary key,
	return_id bigint not null,
	source_shipment_line_id bigint not null,
	sales_order_line_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	line_no integer not null,
	returned_quantity_before numeric(18, 6) not null default 0,
	returnable_quantity_before numeric(18, 6) not null,
	quantity numeric(18, 6) not null,
	unit_price numeric(18, 6) not null,
	amount numeric(18, 2) not null,
	reason varchar(200),
	stock_movement_id bigint,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_sal_sales_return_line_return foreign key (return_id) references sal_sales_return (id) on delete cascade,
	constraint fk_sal_sales_return_line_shipment_line foreign key (source_shipment_line_id) references sal_sales_shipment_line (id),
	constraint fk_sal_sales_return_line_order_line foreign key (sales_order_line_id) references sal_sales_order_line (id),
	constraint fk_sal_sales_return_line_material foreign key (material_id) references mst_material (id),
	constraint fk_sal_sales_return_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_sal_sales_return_line_stock_movement foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint uk_sal_sales_return_line_source unique (return_id, source_shipment_line_id),
	constraint ck_sal_sales_return_line_quantities check (
		returned_quantity_before >= 0 and returnable_quantity_before >= 0 and quantity > 0
	),
	constraint ck_sal_sales_return_line_amount_non_negative check (amount >= 0)
);

create unique index uk_sal_sales_return_client_request
	on sal_sales_return (source_shipment_id, client_request_id)
	where client_request_id is not null;
create index idx_sal_sales_return_customer on sal_sales_return (customer_id);
create index idx_sal_sales_return_status_date on sal_sales_return (status, business_date desc, id desc);
create index idx_sal_sales_return_source on sal_sales_return (source_shipment_id);
create index idx_sal_sales_return_line_return on sal_sales_return_line (return_id);

create table proc_purchase_return (
	id bigserial primary key,
	return_no varchar(64) not null,
	supplier_id bigint not null,
	source_receipt_id bigint not null,
	source_receipt_no varchar(64) not null,
	warehouse_id bigint not null,
	business_date date not null,
	status varchar(32) not null,
	total_amount numeric(18, 2) not null default 0,
	client_request_id varchar(64),
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
	constraint uk_proc_purchase_return_no unique (return_no),
	constraint fk_proc_purchase_return_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_proc_purchase_return_source_receipt foreign key (source_receipt_id) references proc_purchase_receipt (id),
	constraint fk_proc_purchase_return_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_proc_purchase_return_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_proc_purchase_return_total_amount_non_negative check (total_amount >= 0)
);

create table proc_purchase_return_line (
	id bigserial primary key,
	return_id bigint not null,
	source_receipt_line_id bigint not null,
	purchase_order_line_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	line_no integer not null,
	returned_quantity_before numeric(18, 6) not null default 0,
	returnable_quantity_before numeric(18, 6) not null,
	quantity numeric(18, 6) not null,
	unit_price numeric(18, 6) not null,
	amount numeric(18, 2) not null,
	reason varchar(200),
	stock_movement_id bigint,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_proc_purchase_return_line_return foreign key (return_id) references proc_purchase_return (id) on delete cascade,
	constraint fk_proc_purchase_return_line_receipt_line foreign key (source_receipt_line_id) references proc_purchase_receipt_line (id),
	constraint fk_proc_purchase_return_line_order_line foreign key (purchase_order_line_id) references proc_purchase_order_line (id),
	constraint fk_proc_purchase_return_line_material foreign key (material_id) references mst_material (id),
	constraint fk_proc_purchase_return_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_proc_purchase_return_line_stock_movement foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint uk_proc_purchase_return_line_source unique (return_id, source_receipt_line_id),
	constraint ck_proc_purchase_return_line_quantities check (
		returned_quantity_before >= 0 and returnable_quantity_before >= 0 and quantity > 0
	),
	constraint ck_proc_purchase_return_line_amount_non_negative check (amount >= 0)
);

create unique index uk_proc_purchase_return_client_request
	on proc_purchase_return (source_receipt_id, client_request_id)
	where client_request_id is not null;
create index idx_proc_purchase_return_supplier on proc_purchase_return (supplier_id);
create index idx_proc_purchase_return_status_date on proc_purchase_return (status, business_date desc, id desc);
create index idx_proc_purchase_return_source on proc_purchase_return (source_receipt_id);
create index idx_proc_purchase_return_line_return on proc_purchase_return_line (return_id);

create table mfg_material_return (
	id bigserial primary key,
	return_no varchar(64) not null,
	work_order_id bigint not null,
	source_issue_id bigint not null,
	warehouse_id bigint not null,
	business_date date not null,
	status varchar(32) not null,
	client_request_id varchar(64),
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
	constraint uk_mfg_material_return_no unique (return_no),
	constraint fk_mfg_material_return_work_order foreign key (work_order_id) references mfg_work_order (id),
	constraint fk_mfg_material_return_source_issue foreign key (source_issue_id) references mfg_material_issue (id),
	constraint fk_mfg_material_return_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_material_return_status check (status in ('DRAFT', 'POSTED', 'CANCELLED'))
);

create table mfg_material_return_line (
	id bigserial primary key,
	return_id bigint not null,
	source_issue_line_id bigint not null,
	work_order_material_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	line_no integer not null,
	returned_quantity_before numeric(18, 6) not null default 0,
	returnable_quantity_before numeric(18, 6) not null,
	quantity numeric(18, 6) not null,
	reason varchar(200),
	stock_movement_id bigint,
	cost_record_id bigint,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_mfg_material_return_line_return foreign key (return_id) references mfg_material_return (id) on delete cascade,
	constraint fk_mfg_material_return_line_issue_line foreign key (source_issue_line_id) references mfg_material_issue_line (id),
	constraint fk_mfg_material_return_line_work_order_material foreign key (work_order_material_id) references mfg_work_order_material (id),
	constraint fk_mfg_material_return_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_material_return_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_mfg_material_return_line_stock_movement foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_mfg_material_return_line_cost_record foreign key (cost_record_id) references mfg_cost_record (id),
	constraint uk_mfg_material_return_line_source unique (return_id, source_issue_line_id),
	constraint ck_mfg_material_return_line_quantities check (
		returned_quantity_before >= 0 and returnable_quantity_before >= 0 and quantity > 0
	)
);

create unique index uk_mfg_material_return_client_request
	on mfg_material_return (source_issue_id, client_request_id)
	where client_request_id is not null;
create index idx_mfg_material_return_work_order on mfg_material_return (work_order_id);
create index idx_mfg_material_return_status_date on mfg_material_return (status, business_date desc, id desc);
create index idx_mfg_material_return_source on mfg_material_return (source_issue_id);
create index idx_mfg_material_return_line_return on mfg_material_return_line (return_id);

create table mfg_material_supplement (
	id bigserial primary key,
	supplement_no varchar(64) not null,
	work_order_id bigint not null,
	warehouse_id bigint not null,
	business_date date not null,
	status varchar(32) not null,
	client_request_id varchar(64),
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
	constraint uk_mfg_material_supplement_no unique (supplement_no),
	constraint fk_mfg_material_supplement_work_order foreign key (work_order_id) references mfg_work_order (id),
	constraint fk_mfg_material_supplement_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint ck_mfg_material_supplement_status check (status in ('DRAFT', 'POSTED', 'CANCELLED'))
);

create table mfg_material_supplement_line (
	id bigserial primary key,
	supplement_id bigint not null,
	work_order_material_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	line_no integer not null,
	issued_quantity_before numeric(18, 6) not null default 0,
	supplemented_quantity_before numeric(18, 6) not null default 0,
	available_stock_quantity_before numeric(18, 6) not null default 0,
	quantity numeric(18, 6) not null,
	reason varchar(200),
	stock_movement_id bigint,
	cost_record_id bigint,
	created_at timestamp with time zone not null,
	updated_at timestamp with time zone not null,
	constraint fk_mfg_material_supplement_line_supplement foreign key (supplement_id) references mfg_material_supplement (id) on delete cascade,
	constraint fk_mfg_material_supplement_line_work_order_material foreign key (work_order_material_id) references mfg_work_order_material (id),
	constraint fk_mfg_material_supplement_line_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_material_supplement_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_mfg_material_supplement_line_stock_movement foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_mfg_material_supplement_line_cost_record foreign key (cost_record_id) references mfg_cost_record (id),
	constraint uk_mfg_material_supplement_line_material unique (supplement_id, work_order_material_id),
	constraint ck_mfg_material_supplement_line_quantity check (
		issued_quantity_before >= 0 and supplemented_quantity_before >= 0
		and available_stock_quantity_before >= 0 and quantity > 0
	)
);

create unique index uk_mfg_material_supplement_client_request
	on mfg_material_supplement (work_order_id, client_request_id)
	where client_request_id is not null;
create index idx_mfg_material_supplement_work_order on mfg_material_supplement (work_order_id);
create index idx_mfg_material_supplement_status_date on mfg_material_supplement (status, business_date desc, id desc);
create index idx_mfg_material_supplement_warehouse on mfg_material_supplement (warehouse_id);
create index idx_mfg_material_supplement_line_supplement on mfg_material_supplement_line (supplement_id);

create table fin_settlement_adjustment (
	id bigserial primary key,
	adjustment_no varchar(64) not null,
	settlement_side varchar(16) not null,
	adjustment_type varchar(32) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	target_id bigint not null,
	business_date date not null,
	amount numeric(18, 2) not null,
	status varchar(32) not null,
	remark varchar(500),
	client_request_id varchar(64),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	posted_by varchar(64),
	posted_at timestamp with time zone,
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_fin_settlement_adjustment_no unique (adjustment_no),
	constraint ck_fin_settlement_adjustment_side check (settlement_side in ('RECEIVABLE', 'PAYABLE')),
	constraint ck_fin_settlement_adjustment_type check (adjustment_type in ('RETURN_OFFSET', 'REFUND', 'PAYMENT_OFFSET')),
	constraint ck_fin_settlement_adjustment_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_settlement_adjustment_amount_positive check (amount > 0)
);

create unique index uk_fin_settlement_adjustment_client_request
	on fin_settlement_adjustment (source_type, source_id, target_id, client_request_id)
	where client_request_id is not null;
create index idx_fin_settlement_adjustment_target on fin_settlement_adjustment (target_id);
create index idx_fin_settlement_adjustment_source on fin_settlement_adjustment (source_type, source_id);
create index idx_fin_settlement_adjustment_status_date on fin_settlement_adjustment (status, business_date desc, id desc);
