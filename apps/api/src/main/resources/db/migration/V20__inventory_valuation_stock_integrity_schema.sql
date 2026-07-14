alter table inv_inventory_document_line
	add column unit_price numeric(18, 6);

alter table mfg_completion_receipt
	add column provisional_unit_cost numeric(18, 6),
	add column unit_cost numeric(18, 6),
	add column valuation_state varchar(32),
	add constraint ck_mfg_completion_receipt_provisional_unit_cost check (
		provisional_unit_cost is null or provisional_unit_cost >= 0
	),
	add constraint ck_mfg_completion_receipt_unit_cost check (
		unit_cost is null or unit_cost >= 0
	),
	add constraint ck_mfg_completion_receipt_valuation_state check (
		valuation_state is null or valuation_state in (
			'VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL'
		)
	);

alter table inv_stock_movement drop constraint ck_inv_stock_movement_type;
alter table inv_stock_movement add constraint ck_inv_stock_movement_type
	check (movement_type in (
		'OPENING', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE',
		'PRODUCTION_ISSUE', 'PRODUCTION_RECEIPT', 'PURCHASE_RECEIPT', 'SALES_SHIPMENT',
		'SALES_RETURN_IN', 'PURCHASE_RETURN_OUT', 'PRODUCTION_MATERIAL_RETURN_IN',
		'PRODUCTION_MATERIAL_SUPPLEMENT_OUT', 'BUSINESS_REVERSAL', 'QUALITY_STATUS_TRANSFER',
		'WAREHOUSE_TRANSFER_OUT', 'WAREHOUSE_TRANSFER_IN',
		'OWNERSHIP_CONVERSION_OUT', 'OWNERSHIP_CONVERSION_IN',
		'STOCKTAKE_VARIANCE_IN', 'STOCKTAKE_VARIANCE_OUT',
		'VALUATION_ADJUSTMENT'
	));

alter table inv_stock_balance
	add column ownership_type varchar(32) not null default 'PUBLIC',
	add column project_id bigint,
	add column valuation_state varchar(32) not null default 'NON_VALUED',
	add column inventory_amount numeric(18, 2),
	add column average_unit_cost numeric(18, 6),
	add column cost_layer_id bigint,
	add column public_pool_id bigint;

alter table inv_stock_balance
	add constraint fk_inv_stock_balance_project foreign key (project_id) references sal_project (id),
	add constraint ck_inv_stock_balance_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_inv_stock_balance_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	add constraint ck_inv_stock_balance_valuation_state check (
		valuation_state in ('VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL')
	);

update inv_stock_balance b
set valuation_state = case
	when coalesce(m.inventory_value_enabled, false) and b.quantity_on_hand > 0 then 'LEGACY_UNVALUED'
	else 'NON_VALUED'
end
from mst_material m
where m.id = b.material_id;

drop index if exists uk_inv_stock_balance_untracked;
drop index if exists uk_inv_stock_balance_batch;
drop index if exists uk_inv_stock_balance_serial;

create unique index uk_inv_stock_balance_untracked
	on inv_stock_balance (warehouse_id, material_id, quality_status, ownership_type, coalesce(project_id, 0))
	where batch_id is null and serial_id is null;
create unique index uk_inv_stock_balance_batch
	on inv_stock_balance (warehouse_id, material_id, quality_status, batch_id, ownership_type, coalesce(project_id, 0))
	where batch_id is not null and serial_id is null;
create unique index uk_inv_stock_balance_serial
	on inv_stock_balance (warehouse_id, material_id, quality_status, serial_id, ownership_type, coalesce(project_id, 0))
	where serial_id is not null;
create index idx_inv_stock_balance_ownership on inv_stock_balance (ownership_type, project_id, material_id);
create index idx_inv_stock_balance_valuation_state on inv_stock_balance (valuation_state);

alter table inv_stock_movement
	add column ownership_type varchar(32) not null default 'PUBLIC',
	add column project_id bigint,
	add column valuation_state varchar(32) not null default 'NON_VALUED',
	add column valuation_method varchar(64),
	add column unit_cost numeric(18, 6),
	add column inventory_amount numeric(18, 2),
	add column value_movement_id bigint,
	add column cost_layer_id bigint;

alter table inv_stock_movement
	add constraint fk_inv_stock_movement_project foreign key (project_id) references sal_project (id),
	add constraint ck_inv_stock_movement_ownership_type check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_inv_stock_movement_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	add constraint ck_inv_stock_movement_valuation_state check (
		valuation_state in ('VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL')
	);

update inv_stock_movement mv
set valuation_state = case
	when coalesce(m.inventory_value_enabled, false) then 'LEGACY_UNVALUED'
	else 'NON_VALUED'
end
from mst_material m
where m.id = mv.material_id;

create index idx_inv_stock_movement_ownership on inv_stock_movement (ownership_type, project_id, material_id);
create index idx_inv_stock_movement_valuation_state on inv_stock_movement (valuation_state);

alter table inv_stock_reservation
	add column ownership_type varchar(32) not null default 'PUBLIC',
	add column project_id bigint;

alter table inv_stock_reservation
	add constraint fk_inv_stock_reservation_project foreign key (project_id) references sal_project (id),
	add constraint ck_inv_stock_reservation_ownership_type check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_inv_stock_reservation_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create index idx_inv_stock_reservation_ownership on inv_stock_reservation (ownership_type, project_id, material_id);

alter table inv_stock_tracking_allocation
	add column ownership_type varchar(32) not null default 'PUBLIC',
	add column project_id bigint,
	add column cost_layer_id bigint;

alter table inv_stock_tracking_allocation
	add constraint fk_inv_stock_tracking_allocation_project foreign key (project_id) references sal_project (id),
	add constraint ck_inv_stock_tracking_allocation_ownership_type check (ownership_type in ('PUBLIC', 'PROJECT')),
	add constraint ck_inv_stock_tracking_allocation_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	);

create table inv_public_valuation_pool (
	id bigserial primary key,
	material_id bigint not null,
	quantity numeric(18, 6) not null default 0,
	amount numeric(18, 2),
	average_unit_cost numeric(18, 6),
	valuation_state varchar(32) not null,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_inv_public_valuation_pool_material unique (material_id),
	constraint fk_inv_public_valuation_pool_material foreign key (material_id) references mst_material (id),
	constraint ck_inv_public_valuation_pool_quantity_non_negative check (quantity >= 0),
	constraint ck_inv_public_valuation_pool_amount_non_negative check (amount is null or amount >= 0),
	constraint ck_inv_public_valuation_pool_state check (
		valuation_state in ('VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL')
	)
);

insert into inv_public_valuation_pool (material_id, quantity, amount, average_unit_cost, valuation_state)
select b.material_id,
       sum(b.quantity_on_hand),
       null::numeric(18, 2),
       null::numeric(18, 6),
       'LEGACY_UNVALUED'
from inv_stock_balance b
join mst_material m on m.id = b.material_id
where coalesce(m.inventory_value_enabled, false)
and b.ownership_type = 'PUBLIC'
and b.quantity_on_hand > 0
group by b.material_id;

create table inv_project_cost_layer (
	id bigserial primary key,
	project_id bigint not null,
	material_id bigint not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	parent_layer_id bigint,
	batch_id bigint,
	serial_id bigint,
	original_quantity numeric(18, 6) not null,
	original_amount numeric(18, 2) not null,
	remaining_quantity numeric(18, 6) not null,
	remaining_amount numeric(18, 2) not null,
	unit_cost numeric(18, 6) not null,
	status varchar(32) not null default 'ACTIVE',
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_inv_project_cost_layer_project foreign key (project_id) references sal_project (id),
	constraint fk_inv_project_cost_layer_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_project_cost_layer_parent foreign key (parent_layer_id) references inv_project_cost_layer (id),
	constraint fk_inv_project_cost_layer_batch foreign key (batch_id) references inv_batch (id),
	constraint fk_inv_project_cost_layer_serial foreign key (serial_id) references inv_serial (id),
	constraint ck_inv_project_cost_layer_status check (status in ('ACTIVE', 'EXHAUSTED', 'CANCELLED')),
	constraint ck_inv_project_cost_layer_quantities check (
		original_quantity > 0 and remaining_quantity >= 0 and remaining_quantity <= original_quantity
	),
	constraint ck_inv_project_cost_layer_amounts check (
		original_amount >= 0 and remaining_amount >= 0
	)
);

create index idx_inv_project_cost_layer_project_material
	on inv_project_cost_layer (project_id, material_id, status, id);

alter table inv_stock_balance
	add constraint fk_inv_stock_balance_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint fk_inv_stock_balance_public_pool foreign key (public_pool_id) references inv_public_valuation_pool (id);

create table inv_cost_layer_allocation (
	id bigserial primary key,
	value_movement_id bigint,
	cost_layer_id bigint not null,
	quantity numeric(18, 6) not null,
	inventory_amount numeric(18, 2) not null,
	created_at timestamptz not null default now(),
	constraint fk_inv_cost_layer_allocation_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	constraint ck_inv_cost_layer_allocation_quantity check (quantity > 0),
	constraint ck_inv_cost_layer_allocation_amount check (inventory_amount >= 0)
);

create index idx_inv_cost_layer_allocation_layer on inv_cost_layer_allocation (cost_layer_id, id);

create table inv_value_movement (
	id bigserial primary key,
	stock_movement_id bigint not null,
	movement_no varchar(64) not null,
	movement_type varchar(32) not null,
	direction varchar(32) not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	cost_layer_id bigint,
	quantity numeric(18, 6) not null,
	unit_cost numeric(18, 6),
	inventory_amount numeric(18, 2),
	valuation_method varchar(64) not null,
	valuation_state varchar(32) not null,
	original_value_movement_id bigint,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_line_id bigint not null,
	business_date date not null,
	created_at timestamptz not null default now(),
	constraint uk_inv_value_movement_stock unique (stock_movement_id),
	constraint fk_inv_value_movement_stock foreign key (stock_movement_id) references inv_stock_movement (id),
	constraint fk_inv_value_movement_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_value_movement_project foreign key (project_id) references sal_project (id),
	constraint fk_inv_value_movement_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	constraint fk_inv_value_movement_original foreign key (original_value_movement_id) references inv_value_movement (id),
	constraint ck_inv_value_movement_direction check (direction in ('IN', 'OUT')),
	constraint ck_inv_value_movement_ownership_type check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_inv_value_movement_project_required check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_inv_value_movement_state check (
		valuation_state in ('VALUED', 'LEGACY_UNVALUED', 'NON_VALUED',
			'MANUAL_PROVISIONAL', 'CURRENT_AVERAGE_PROVISIONAL')
	)
);

create index idx_inv_value_movement_material_date on inv_value_movement (material_id, business_date desc, id desc);
create index idx_inv_value_movement_source on inv_value_movement (source_type, source_id, source_line_id);

alter table inv_stock_movement
	add constraint fk_inv_stock_movement_value foreign key (value_movement_id) references inv_value_movement (id),
	add constraint fk_inv_stock_movement_cost_layer foreign key (cost_layer_id) references inv_project_cost_layer (id);

alter table inv_cost_layer_allocation
	add constraint fk_inv_cost_layer_allocation_value foreign key (value_movement_id) references inv_value_movement (id);

update inv_stock_balance b
set public_pool_id = p.id
from inv_public_valuation_pool p
where p.material_id = b.material_id
and b.ownership_type = 'PUBLIC';

create table inv_warehouse_transfer (
	id bigserial primary key,
	transfer_no varchar(64) not null,
	business_date date not null,
	reason varchar(500) not null,
	status varchar(32) not null default 'DRAFT',
	idempotency_key varchar(120) not null,
	posted_at timestamptz,
	cancelled_at timestamptz,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by_username varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_inv_warehouse_transfer_no unique (transfer_no),
	constraint uk_inv_warehouse_transfer_idempotency unique (created_by_user_id, idempotency_key),
	constraint ck_inv_warehouse_transfer_status check (status in ('DRAFT', 'POSTED', 'CANCELLED'))
);

create table inv_warehouse_transfer_line (
	id bigserial primary key,
	transfer_id bigint not null,
	line_no integer not null,
	source_warehouse_id bigint not null,
	target_warehouse_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	quality_status varchar(32) not null,
	batch_id bigint,
	serial_id bigint,
	quantity numeric(18, 6) not null,
	source_movement_id bigint,
	target_movement_id bigint,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_inv_warehouse_transfer_line_header foreign key (transfer_id) references inv_warehouse_transfer (id),
	constraint fk_inv_warehouse_transfer_line_source_warehouse foreign key (source_warehouse_id) references mst_warehouse (id),
	constraint fk_inv_warehouse_transfer_line_target_warehouse foreign key (target_warehouse_id) references mst_warehouse (id),
	constraint fk_inv_warehouse_transfer_line_project foreign key (project_id) references sal_project (id),
	constraint fk_inv_warehouse_transfer_line_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_warehouse_transfer_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_inv_warehouse_transfer_line_batch foreign key (batch_id) references inv_batch (id),
	constraint fk_inv_warehouse_transfer_line_serial foreign key (serial_id) references inv_serial (id),
	constraint fk_inv_warehouse_transfer_line_source_movement foreign key (source_movement_id) references inv_stock_movement (id),
	constraint fk_inv_warehouse_transfer_line_target_movement foreign key (target_movement_id) references inv_stock_movement (id),
	constraint uk_inv_warehouse_transfer_line_no unique (transfer_id, line_no),
	constraint ck_inv_warehouse_transfer_line_quantity check (quantity > 0),
	constraint ck_inv_warehouse_transfer_line_warehouse check (source_warehouse_id <> target_warehouse_id),
	constraint ck_inv_warehouse_transfer_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_inv_warehouse_transfer_line_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	)
);

create table inv_ownership_conversion (
	id bigserial primary key,
	conversion_no varchar(64) not null,
	business_date date not null,
	reason varchar(500) not null,
	status varchar(32) not null default 'DRAFT',
	idempotency_key varchar(120) not null,
	approval_instance_id bigint,
	posted_at timestamptz,
	cancelled_at timestamptz,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by_username varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_inv_ownership_conversion_no unique (conversion_no),
	constraint uk_inv_ownership_conversion_idempotency unique (created_by_user_id, idempotency_key),
	constraint fk_inv_ownership_conversion_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_inv_ownership_conversion_status check (status in ('DRAFT', 'SUBMITTED', 'POSTED', 'CANCELLED'))
);

create table inv_ownership_conversion_line (
	id bigserial primary key,
	conversion_id bigint not null,
	line_no integer not null,
	source_ownership_type varchar(32) not null,
	source_project_id bigint,
	target_ownership_type varchar(32) not null,
	target_project_id bigint,
	source_warehouse_id bigint not null,
	target_warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quality_status varchar(32) not null,
	batch_id bigint,
	serial_id bigint,
	quantity numeric(18, 6) not null,
	source_unit_cost numeric(18, 6),
	source_cost_layer_id bigint,
	source_movement_id bigint,
	target_movement_id bigint,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_inv_ownership_conversion_line_header foreign key (conversion_id) references inv_ownership_conversion (id),
	constraint fk_inv_ownership_conversion_line_source_project foreign key (source_project_id) references sal_project (id),
	constraint fk_inv_ownership_conversion_line_target_project foreign key (target_project_id) references sal_project (id),
	constraint fk_inv_ownership_conversion_line_source_warehouse foreign key (source_warehouse_id) references mst_warehouse (id),
	constraint fk_inv_ownership_conversion_line_target_warehouse foreign key (target_warehouse_id) references mst_warehouse (id),
	constraint fk_inv_ownership_conversion_line_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_ownership_conversion_line_unit foreign key (unit_id) references mst_unit (id),
	constraint fk_inv_ownership_conversion_line_batch foreign key (batch_id) references inv_batch (id),
	constraint fk_inv_ownership_conversion_line_serial foreign key (serial_id) references inv_serial (id),
	constraint fk_inv_ownership_conversion_line_layer foreign key (source_cost_layer_id) references inv_project_cost_layer (id),
	constraint fk_inv_ownership_conversion_line_source_movement foreign key (source_movement_id) references inv_stock_movement (id),
	constraint fk_inv_ownership_conversion_line_target_movement foreign key (target_movement_id) references inv_stock_movement (id),
	constraint uk_inv_ownership_conversion_line_no unique (conversion_id, line_no),
	constraint ck_inv_ownership_conversion_line_quantity check (quantity > 0),
	constraint ck_inv_ownership_conversion_line_source_ownership check (source_ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_inv_ownership_conversion_line_target_ownership check (target_ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_inv_ownership_conversion_line_source_project check (
		(source_ownership_type = 'PUBLIC' and source_project_id is null)
		or (source_ownership_type = 'PROJECT' and source_project_id is not null)
	),
	constraint ck_inv_ownership_conversion_line_target_project check (
		(target_ownership_type = 'PUBLIC' and target_project_id is null)
		or (target_ownership_type = 'PROJECT' and target_project_id is not null)
	)
);

create table inv_stocktake (
	id bigserial primary key,
	stocktake_no varchar(64) not null,
	business_date date not null,
	scope_type varchar(32) not null,
	warehouse_id bigint,
	material_id bigint,
	reason varchar(500) not null,
	status varchar(32) not null default 'DRAFT',
	idempotency_key varchar(120) not null,
	approval_instance_id bigint,
	started_at timestamptz,
	posted_at timestamptz,
	cancelled_at timestamptz,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by_username varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_inv_stocktake_no unique (stocktake_no),
	constraint uk_inv_stocktake_idempotency unique (created_by_user_id, idempotency_key),
	constraint fk_inv_stocktake_warehouse foreign key (warehouse_id) references mst_warehouse (id),
	constraint fk_inv_stocktake_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_stocktake_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_inv_stocktake_scope check (scope_type in ('WAREHOUSE', 'MATERIAL')),
	constraint ck_inv_stocktake_status check (status in ('DRAFT', 'COUNTING', 'RECONCILED', 'SUBMITTED', 'POSTED', 'CANCELLED'))
);

create table inv_stocktake_line (
	id bigserial primary key,
	stocktake_id bigint not null,
	balance_id bigint not null,
	line_no integer not null,
	warehouse_id bigint not null,
	material_id bigint not null,
	unit_id bigint not null,
	quality_status varchar(32) not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	batch_id bigint,
	serial_id bigint,
	book_quantity numeric(18, 6) not null,
	counted_quantity numeric(18, 6),
	variance_quantity numeric(18, 6),
	variance_movement_id bigint,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_inv_stocktake_line_header foreign key (stocktake_id) references inv_stocktake (id),
	constraint fk_inv_stocktake_line_balance foreign key (balance_id) references inv_stock_balance (id),
	constraint fk_inv_stocktake_line_variance_movement foreign key (variance_movement_id) references inv_stock_movement (id),
	constraint uk_inv_stocktake_line_balance unique (stocktake_id, balance_id)
);

create table inv_stocktake_range_lock (
	id bigserial primary key,
	stocktake_id bigint not null,
	warehouse_id bigint,
	material_id bigint,
	locked_at timestamptz not null default now(),
	released_at timestamptz,
	constraint fk_inv_stocktake_range_lock_stocktake foreign key (stocktake_id) references inv_stocktake (id)
);

create index idx_inv_stocktake_range_lock_active
	on inv_stocktake_range_lock (warehouse_id, material_id)
	where released_at is null;

create table inv_valuation_adjustment (
	id bigserial primary key,
	adjustment_no varchar(64) not null,
	adjustment_type varchar(32) not null,
	business_date date not null,
	reason varchar(500) not null,
	status varchar(32) not null default 'DRAFT',
	idempotency_key varchar(120) not null,
	approval_instance_id bigint,
	posted_at timestamptz,
	cancelled_at timestamptz,
	created_by_user_id bigint not null,
	created_by_username varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by_username varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_inv_valuation_adjustment_no unique (adjustment_no),
	constraint uk_inv_valuation_adjustment_idempotency unique (created_by_user_id, idempotency_key),
	constraint fk_inv_valuation_adjustment_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint ck_inv_valuation_adjustment_type check (adjustment_type in ('LEGACY_OPENING', 'PROVISIONAL_REVALUATION')),
	constraint ck_inv_valuation_adjustment_status check (status in ('DRAFT', 'SUBMITTED', 'POSTED', 'CANCELLED'))
);

create table inv_valuation_adjustment_line (
	id bigserial primary key,
	adjustment_id bigint not null,
	line_no integer not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	material_id bigint not null,
	quantity numeric(18, 6),
	unit_cost numeric(18, 6),
	adjustment_amount numeric(18, 2) not null,
	cost_layer_id bigint,
	value_movement_id bigint,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint fk_inv_valuation_adjustment_line_header foreign key (adjustment_id) references inv_valuation_adjustment (id),
	constraint fk_inv_valuation_adjustment_line_project foreign key (project_id) references sal_project (id),
	constraint fk_inv_valuation_adjustment_line_material foreign key (material_id) references mst_material (id),
	constraint fk_inv_valuation_adjustment_line_layer foreign key (cost_layer_id) references inv_project_cost_layer (id),
	constraint fk_inv_valuation_adjustment_line_value foreign key (value_movement_id) references inv_value_movement (id),
	constraint uk_inv_valuation_adjustment_line_no unique (adjustment_id, line_no),
	constraint ck_inv_valuation_adjustment_line_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_inv_valuation_adjustment_line_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	)
);

alter table platform_business_attachment drop constraint ck_platform_business_attachment_object;
alter table platform_business_attachment add constraint ck_platform_business_attachment_object check (
	object_type in (
		'SALES_PROJECT_CONTRACT',
		'BOM_ENGINEERING_CHANGE',
		'INVENTORY_OWNERSHIP_CONVERSION',
		'INVENTORY_STOCKTAKE',
		'INVENTORY_VALUATION_ADJUSTMENT'
	)
);

insert into sys_permission (code, name, type, parent_id, route_path, sort_order, created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('inventory:valuation:view', '查看库存成本金额', '/inventory/balances', 302),
		('inventory:cost-layer:view', '查看库存成本层', '/inventory/cost-layers', 312),
		('inventory:reconciliation:view', '查看库存价值对账', '/inventory/reconciliations', 313),
		('inventory:warehouse-transfer:view', '查看仓库调拨', '/inventory/warehouse-transfers', 314),
		('inventory:warehouse-transfer:create', '创建仓库调拨', '/inventory/warehouse-transfers', 315),
		('inventory:warehouse-transfer:update', '更新仓库调拨', '/inventory/warehouse-transfers', 316),
		('inventory:warehouse-transfer:post', '过账仓库调拨', '/inventory/warehouse-transfers', 317),
		('inventory:warehouse-transfer:cancel', '取消仓库调拨', '/inventory/warehouse-transfers', 318),
		('inventory:ownership-conversion:view', '查看库存所有权转换', '/inventory/ownership-conversions', 319),
		('inventory:ownership-conversion:create', '创建库存所有权转换', '/inventory/ownership-conversions', 320),
		('inventory:ownership-conversion:update', '更新库存所有权转换', '/inventory/ownership-conversions', 321),
		('inventory:ownership-conversion:submit', '提交库存所有权转换审批', '/inventory/ownership-conversions', 322),
		('inventory:ownership-conversion:withdraw', '撤回库存所有权转换审批', '/inventory/ownership-conversions', 323),
		('inventory:ownership-conversion:cancel', '取消库存所有权转换', '/inventory/ownership-conversions', 323),
		('inventory:ownership-conversion:post-approve', '审批库存所有权转换过账', '/platform/approvals', 324),
		('inventory:stocktake:view', '查看库存盘点', '/inventory/stocktakes', 325),
		('inventory:stocktake:create', '创建库存盘点', '/inventory/stocktakes', 326),
		('inventory:stocktake:update', '更新库存盘点', '/inventory/stocktakes', 327),
		('inventory:stocktake:submit', '提交库存盘点审批', '/inventory/stocktakes', 328),
		('inventory:stocktake:cancel', '取消库存盘点', '/inventory/stocktakes', 329),
		('inventory:stocktake:variance-approve', '审批库存盘点盘差', '/platform/approvals', 330),
		('inventory:valuation-adjustment:view', '查看库存估值调整', '/inventory/valuation-adjustments', 331),
		('inventory:valuation-adjustment:create', '创建库存估值调整', '/inventory/valuation-adjustments', 332),
		('inventory:valuation-adjustment:update', '更新库存估值调整', '/inventory/valuation-adjustments', 333),
		('inventory:valuation-adjustment:submit', '提交库存估值调整审批', '/inventory/valuation-adjustments', 334),
		('inventory:valuation-adjustment:withdraw', '撤回库存估值调整审批', '/inventory/valuation-adjustments', 335),
		('inventory:valuation-adjustment:cancel', '取消库存估值调整', '/inventory/valuation-adjustments', 335),
		('inventory:valuation-adjustment:post-approve', '审批库存估值调整过账', '/platform/approvals', 336)
) as seed(code, name, route_path, sort_order)
left join sys_permission parent on parent.code = 'inventory'
where not exists (select 1 from sys_permission p where p.code = seed.code);

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code in (
	'inventory:valuation:view',
	'inventory:cost-layer:view',
	'inventory:reconciliation:view',
	'inventory:warehouse-transfer:view',
	'inventory:warehouse-transfer:create',
	'inventory:warehouse-transfer:update',
	'inventory:warehouse-transfer:post',
	'inventory:warehouse-transfer:cancel',
	'inventory:ownership-conversion:view',
	'inventory:ownership-conversion:create',
	'inventory:ownership-conversion:update',
	'inventory:ownership-conversion:submit',
	'inventory:ownership-conversion:withdraw',
	'inventory:ownership-conversion:cancel',
	'inventory:ownership-conversion:post-approve',
	'inventory:stocktake:view',
	'inventory:stocktake:create',
	'inventory:stocktake:update',
	'inventory:stocktake:submit',
	'inventory:stocktake:cancel',
	'inventory:stocktake:variance-approve',
	'inventory:valuation-adjustment:view',
	'inventory:valuation-adjustment:create',
	'inventory:valuation-adjustment:update',
	'inventory:valuation-adjustment:submit',
	'inventory:valuation-adjustment:withdraw',
	'inventory:valuation-adjustment:cancel',
	'inventory:valuation-adjustment:post-approve'
)
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp
	where rp.role_id = r.id and rp.permission_id = p.id
);

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
select seed.scene_code, seed.name, seed.business_object_type, 'POST', 1, 'ENABLED'
from (
	values
		('INVENTORY_OWNERSHIP_CONVERSION_POST', '库存所有权转换过账审批', 'INVENTORY_OWNERSHIP_CONVERSION'),
		('INVENTORY_STOCKTAKE_VARIANCE_POST', '库存盘点盘差过账审批', 'INVENTORY_STOCKTAKE'),
		('INVENTORY_VALUATION_ADJUSTMENT_POST', '库存估值调整过账审批', 'INVENTORY_VALUATION_ADJUSTMENT')
) as seed(scene_code, name, business_object_type)
where not exists (
	select 1 from platform_approval_definition d where d.scene_code = seed.scene_code
);

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select d.id, 1, seed.step_name, seed.permission_code
from (
	values
		('INVENTORY_OWNERSHIP_CONVERSION_POST', '所有权转换过账审批', 'inventory:ownership-conversion:post-approve'),
		('INVENTORY_STOCKTAKE_VARIANCE_POST', '盘点盘差过账审批', 'inventory:stocktake:variance-approve'),
		('INVENTORY_VALUATION_ADJUSTMENT_POST', '估值调整过账审批', 'inventory:valuation-adjustment:post-approve')
) as seed(scene_code, step_name, permission_code)
join platform_approval_definition d on d.scene_code = seed.scene_code
where not exists (
	select 1 from platform_approval_definition_step s
	where s.definition_id = d.id and s.step_no = 1
);
