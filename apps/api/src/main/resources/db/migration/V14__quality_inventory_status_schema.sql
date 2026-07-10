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
