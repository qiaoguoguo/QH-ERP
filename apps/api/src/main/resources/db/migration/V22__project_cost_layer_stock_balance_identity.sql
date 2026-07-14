drop index if exists uk_inv_stock_balance_untracked;
drop index if exists uk_inv_stock_balance_batch;
drop index if exists uk_inv_stock_balance_serial;

create unique index uk_inv_stock_balance_untracked
	on inv_stock_balance (
		warehouse_id, material_id, quality_status, ownership_type,
		coalesce(project_id, 0), coalesce(cost_layer_id, 0)
	)
	where batch_id is null and serial_id is null;

create unique index uk_inv_stock_balance_batch
	on inv_stock_balance (
		warehouse_id, material_id, quality_status, batch_id, ownership_type,
		coalesce(project_id, 0), coalesce(cost_layer_id, 0)
	)
	where batch_id is not null and serial_id is null;

create unique index uk_inv_stock_balance_serial
	on inv_stock_balance (
		warehouse_id, material_id, quality_status, serial_id, ownership_type,
		coalesce(project_id, 0), coalesce(cost_layer_id, 0)
	)
	where serial_id is not null;

create index if not exists idx_inv_stock_balance_project_cost_layer
	on inv_stock_balance (ownership_type, project_id, cost_layer_id, material_id, warehouse_id);
