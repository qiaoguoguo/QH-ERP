create table mfg_cost_record (
	id bigserial primary key,
	record_no varchar(64) not null,
	work_order_id bigint not null,
	product_material_id bigint not null,
	cost_type varchar(32) not null,
	source_type varchar(32) not null,
	source_document_type varchar(64) not null,
	source_document_no varchar(64),
	source_document_id bigint,
	source_line_id bigint,
	work_order_material_id bigint,
	material_id bigint,
	unit_id bigint,
	quantity numeric(18, 6),
	unit_price numeric(18, 6),
	amount numeric(18, 6),
	basis_type varchar(32) not null,
	business_date date not null,
	status varchar(32) not null,
	remark varchar(500),
	recorded_by varchar(64) not null,
	recorded_at timestamp with time zone not null,
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	version bigint not null default 0,
	constraint uk_mfg_cost_record_no unique (record_no),
	constraint fk_mfg_cost_record_work_order foreign key (work_order_id) references mfg_work_order (id),
	constraint fk_mfg_cost_record_product foreign key (product_material_id) references mst_material (id),
	constraint fk_mfg_cost_record_work_order_material foreign key (work_order_material_id) references mfg_work_order_material (id),
	constraint fk_mfg_cost_record_material foreign key (material_id) references mst_material (id),
	constraint fk_mfg_cost_record_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_mfg_cost_record_cost_type check (cost_type in ('MATERIAL', 'LABOR', 'MANUFACTURING_OVERHEAD', 'OTHER')),
	constraint ck_mfg_cost_record_source_type check (source_type in ('AUTO_PRODUCTION', 'MANUAL_ENTRY')),
	constraint ck_mfg_cost_record_source_document_type check (source_document_type in (
		'PRODUCTION_MATERIAL_ISSUE',
		'PRODUCTION_WORK_REPORT',
		'PRODUCTION_COMPLETION_RECEIPT',
		'MANUAL_COST_RECORD'
	)),
	constraint ck_mfg_cost_record_basis_type check (basis_type in (
		'SOURCE_QUANTITY_ONLY',
		'MANUAL_AMOUNT',
		'MANUAL_UNIT_PRICE_QUANTITY',
		'OUTPUT_QUANTITY_TRACE'
	)),
	constraint ck_mfg_cost_record_status check (status in ('ACTIVE', 'VOIDED')),
	constraint ck_mfg_cost_record_quantity_positive check (quantity is null or quantity > 0),
	constraint ck_mfg_cost_record_unit_price_non_negative check (unit_price is null or unit_price >= 0),
	constraint ck_mfg_cost_record_amount_non_negative check (amount is null or amount >= 0),
	constraint ck_mfg_cost_record_has_quantity_or_amount check (quantity is not null or amount is not null)
);

create unique index uk_mfg_cost_record_source_line
	on mfg_cost_record (source_document_type, source_line_id, cost_type)
	where source_type = 'AUTO_PRODUCTION'
	and source_line_id is not null
	and basis_type <> 'OUTPUT_QUANTITY_TRACE';

create unique index uk_mfg_cost_record_source_document
	on mfg_cost_record (source_document_type, source_document_id, cost_type)
	where source_type = 'AUTO_PRODUCTION'
	and source_document_id is not null
	and source_line_id is null
	and basis_type <> 'OUTPUT_QUANTITY_TRACE';

create unique index uk_mfg_cost_record_output_trace
	on mfg_cost_record (source_document_type, source_document_id, basis_type)
	where source_type = 'AUTO_PRODUCTION'
	and source_document_id is not null
	and basis_type = 'OUTPUT_QUANTITY_TRACE';

create index idx_mfg_cost_record_work_order on mfg_cost_record (work_order_id, business_date desc, id desc);
create index idx_mfg_cost_record_product on mfg_cost_record (product_material_id, business_date desc, id desc);
create index idx_mfg_cost_record_business_date on mfg_cost_record (business_date desc, id desc);
create index idx_mfg_cost_record_cost_type on mfg_cost_record (cost_type, business_date desc, id desc);
create index idx_mfg_cost_record_source on mfg_cost_record (source_document_type, source_document_id, source_line_id);
