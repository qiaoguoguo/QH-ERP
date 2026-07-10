alter table inv_stock_balance add column quality_status varchar(32);
update inv_stock_balance set quality_status = 'QUALIFIED' where quality_status is null;
alter table inv_stock_balance alter column quality_status set not null;
alter table inv_stock_balance drop constraint uk_inv_stock_balance_warehouse_material;
alter table inv_stock_balance add constraint uk_inv_stock_balance_warehouse_material_quality
	unique (warehouse_id, material_id, quality_status);
alter table inv_stock_balance add constraint ck_inv_stock_balance_quality_status
	check (quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'));

alter table inv_stock_movement add column quality_status varchar(32);
update inv_stock_movement set quality_status = 'QUALIFIED' where quality_status is null;
alter table inv_stock_movement alter column quality_status set not null;
alter table inv_stock_movement add constraint ck_inv_stock_movement_quality_status
	check (quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'));

alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;
alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE', 'PRODUCTION_RECEIPT', 'PURCHASE_RECEIPT', 'SALES_SHIPMENT',
		'SALES_RETURN_IN', 'PURCHASE_RETURN_OUT', 'PRODUCTION_MATERIAL_RETURN_IN',
		'PRODUCTION_MATERIAL_SUPPLEMENT_OUT', 'BUSINESS_REVERSAL', 'QUALITY_STATUS_TRANSFER'
	));

create index idx_inv_stock_balance_quality_status on inv_stock_balance (quality_status);
create index idx_inv_stock_movement_quality_status on inv_stock_movement (quality_status);

drop index if exists uk_inv_stock_movement_opening_once;
create unique index uk_inv_stock_movement_opening_once
	on inv_stock_movement (warehouse_id, material_id, quality_status)
	where movement_type = 'OPENING';

create table qua_quality_inspection (
	id bigserial primary key,
	inspection_no varchar(64) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	business_date date not null,
	inspection_quantity numeric(18, 6) not null,
	qualified_quantity numeric(18, 6) not null default 0,
	rejected_quantity numeric(18, 6) not null default 0,
	frozen_quantity numeric(18, 6) not null default 0,
	status varchar(32) not null,
	reason varchar(200),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	completed_by varchar(64),
	completed_at timestamp with time zone,
	version bigint not null default 0,
	constraint uk_qua_quality_inspection_no unique (inspection_no),
	constraint uk_qua_quality_inspection_source unique (source_type, source_id, source_line_id),
	constraint fk_qua_quality_inspection_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_qua_quality_inspection_material foreign key (material_id) references mst_material (id),
	constraint fk_qua_quality_inspection_unit foreign key (unit_id) references mst_unit (id),
	constraint ck_qua_quality_inspection_source_type check (
		source_type in ('PURCHASE_RECEIPT', 'PRODUCTION_COMPLETION', 'SALES_RETURN', 'PRODUCTION_RETURN')
	),
	constraint ck_qua_quality_inspection_status check (status in ('PENDING', 'COMPLETED')),
	constraint ck_qua_quality_inspection_quantities check (
		inspection_quantity > 0
		and qualified_quantity >= 0
		and rejected_quantity >= 0
		and frozen_quantity >= 0
		and qualified_quantity + rejected_quantity + frozen_quantity <= inspection_quantity
	)
);

create index idx_qua_quality_inspection_status_date on qua_quality_inspection (status, business_date desc, id desc);
create index idx_qua_quality_inspection_source on qua_quality_inspection (source_type, source_id);
create index idx_qua_quality_inspection_warehouse_material
	on qua_quality_inspection (warehouse_id, material_id);

create sequence qua_quality_status_transfer_source_seq
	as bigint
	start with 9000000000000
	increment by 1;

alter table proc_purchase_return_line add column quality_status varchar(32);
update proc_purchase_return_line set quality_status = 'QUALIFIED' where quality_status is null;
alter table proc_purchase_return_line alter column quality_status set not null;
alter table proc_purchase_return_line add constraint ck_proc_purchase_return_line_quality_status
	check (quality_status in ('PENDING_INSPECTION', 'QUALIFIED', 'REJECTED', 'FROZEN'));
create index idx_proc_purchase_return_line_quality_status on proc_purchase_return_line (quality_status);
