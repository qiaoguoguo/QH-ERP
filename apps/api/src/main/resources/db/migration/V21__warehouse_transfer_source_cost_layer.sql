alter table inv_warehouse_transfer_line
	add column source_cost_layer_id bigint;

alter table inv_warehouse_transfer_line
	add constraint fk_inv_warehouse_transfer_line_source_layer
	foreign key (source_cost_layer_id) references inv_project_cost_layer (id);

create index idx_inv_warehouse_transfer_line_source_layer
	on inv_warehouse_transfer_line (source_cost_layer_id);
