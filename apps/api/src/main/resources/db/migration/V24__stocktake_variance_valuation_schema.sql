alter table inv_stocktake_line
	add column variance_unit_cost numeric(18, 6),
	add column variance_reason varchar(500),
	add constraint ck_inv_stocktake_line_variance_unit_cost check (
		variance_unit_cost is null or variance_unit_cost >= 0
	);

create index idx_inv_stocktake_line_stocktake_order
	on inv_stocktake_line (stocktake_id, line_no, id);
