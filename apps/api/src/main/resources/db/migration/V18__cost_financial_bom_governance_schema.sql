alter table mst_material
	add column cost_category varchar(32) default 'UNCLASSIFIED',
	add column inventory_valuation_category varchar(32) default 'UNCLASSIFIED',
	add column inventory_value_enabled boolean default false,
	add column project_cost_enabled boolean default false,
	add column cost_remark varchar(500);

update mst_material
set cost_category = 'UNCLASSIFIED',
	inventory_valuation_category = 'UNCLASSIFIED',
	inventory_value_enabled = false,
	project_cost_enabled = false
where cost_category is null
	or inventory_valuation_category is null
	or inventory_value_enabled is null
	or project_cost_enabled is null;

alter table mst_material
	alter column cost_category set not null,
	alter column inventory_valuation_category set not null,
	alter column inventory_value_enabled set not null,
	alter column project_cost_enabled set not null,
	add constraint ck_mst_material_cost_category check (
		cost_category in (
			'DIRECT_MATERIAL',
			'AUXILIARY_MATERIAL',
			'SEMI_FINISHED',
			'FINISHED_GOOD',
			'OUTSOURCING',
			'SERVICE',
			'UNCLASSIFIED'
		)
	),
	add constraint ck_mst_material_inventory_valuation_category check (
		inventory_valuation_category in (
			'VALUATED_MATERIAL',
			'NON_VALUATED_CONSUMABLE',
			'SERVICE_NON_STOCK',
			'UNCLASSIFIED'
		)
	);

create table mst_material_unit_conversion (
	id bigserial primary key,
	material_id bigint not null,
	base_unit_id bigint not null,
	business_unit_id bigint not null,
	conversion_rate numeric(18, 6) not null,
	quantity_scale integer not null,
	rounding_mode varchar(16) not null,
	effective_from date,
	effective_to date,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mst_material_unit_conversion_material foreign key (material_id) references mst_material (id),
	constraint fk_mst_material_unit_conversion_base_unit foreign key (base_unit_id) references mst_unit (id),
	constraint fk_mst_material_unit_conversion_business_unit foreign key (business_unit_id) references mst_unit (id),
	constraint ck_mst_material_unit_conversion_rate_positive check (conversion_rate > 0),
	constraint ck_mst_material_unit_conversion_scale_non_negative check (quantity_scale >= 0),
	constraint ck_mst_material_unit_conversion_rounding check (rounding_mode in ('HALF_UP', 'UP', 'DOWN')),
	constraint ck_mst_material_unit_conversion_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_mst_material_unit_conversion_date_range check (
		effective_from is null or effective_to is null or effective_from <= effective_to
	),
	constraint ck_mst_material_unit_conversion_not_base check (base_unit_id <> business_unit_id)
);

create index idx_mst_material_unit_conversion_material
	on mst_material_unit_conversion (material_id, business_unit_id, status, effective_from, effective_to);

create table sys_coding_rule (
	id bigserial primary key,
	rule_code varchar(64) not null,
	name varchar(128) not null,
	object_type varchar(32) not null,
	prefix varchar(32) not null,
	date_pattern varchar(16) not null,
	serial_length integer not null,
	reset_cycle varchar(16) not null,
	next_serial_no bigint not null,
	status varchar(32) not null,
	last_generated_code varchar(128),
	last_generated_at timestamptz,
	last_reset_key varchar(16),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_sys_coding_rule_code unique (rule_code),
	constraint ck_sys_coding_rule_object_type check (object_type in ('MATERIAL', 'CUSTOMER', 'SUPPLIER', 'BOM', 'BOM_ECO')),
	constraint ck_sys_coding_rule_date_pattern check (date_pattern in ('NONE', 'YYYY', 'YYYYMM', 'YYYYMMDD')),
	constraint ck_sys_coding_rule_reset_cycle check (reset_cycle in ('NEVER', 'YEAR', 'MONTH', 'DAY')),
	constraint ck_sys_coding_rule_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_sys_coding_rule_serial_length check (serial_length > 0 and serial_length <= 12),
	constraint ck_sys_coding_rule_next_serial check (next_serial_no > 0)
);

create unique index uk_sys_coding_rule_enabled_object
	on sys_coding_rule (object_type)
	where status = 'ENABLED';

create table mst_customer_settlement_tax (
	customer_id bigint primary key,
	invoice_title varchar(200),
	tax_no varchar(64),
	registered_address varchar(255),
	registered_phone varchar(64),
	bank_name varchar(128),
	bank_account varchar(128),
	default_tax_rate numeric(9, 6),
	invoice_type varchar(32),
	settlement_method varchar(32),
	payment_term_days integer,
	payment_terms varchar(200),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mst_customer_settlement_tax_customer foreign key (customer_id) references mst_customer (id),
	constraint ck_mst_customer_settlement_tax_rate check (default_tax_rate is null or (default_tax_rate >= 0 and default_tax_rate <= 1)),
	constraint ck_mst_customer_settlement_tax_invoice_type check (invoice_type is null or invoice_type in ('GENERAL_VAT', 'SPECIAL_VAT', 'NONE')),
	constraint ck_mst_customer_settlement_method check (settlement_method is null or settlement_method in ('MONTHLY', 'CASH_ON_DELIVERY', 'ADVANCE', 'CUSTOM')),
	constraint ck_mst_customer_settlement_days check (payment_term_days is null or payment_term_days >= 0)
);

create table mst_supplier_settlement_tax (
	supplier_id bigint primary key,
	invoice_title varchar(200),
	tax_no varchar(64),
	registered_address varchar(255),
	registered_phone varchar(64),
	bank_name varchar(128),
	bank_account varchar(128),
	default_tax_rate numeric(9, 6),
	invoice_type varchar(32),
	settlement_method varchar(32),
	payment_term_days integer,
	payment_terms varchar(200),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mst_supplier_settlement_tax_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint ck_mst_supplier_settlement_tax_rate check (default_tax_rate is null or (default_tax_rate >= 0 and default_tax_rate <= 1)),
	constraint ck_mst_supplier_settlement_tax_invoice_type check (invoice_type is null or invoice_type in ('GENERAL_VAT', 'SPECIAL_VAT', 'NONE')),
	constraint ck_mst_supplier_settlement_method check (settlement_method is null or settlement_method in ('MONTHLY', 'CASH_ON_DELIVERY', 'ADVANCE', 'CUSTOM')),
	constraint ck_mst_supplier_settlement_days check (payment_term_days is null or payment_term_days >= 0)
);

drop index if exists uk_mfg_bom_enabled_parent;

alter table mfg_bom
	add constraint ck_mfg_bom_effective_date_range check (
		effective_from is null or effective_to is null or effective_from <= effective_to
	);

create index idx_mfg_bom_parent_status_effective
	on mfg_bom (parent_material_id, status, effective_from, effective_to);

alter table mfg_bom_item
	add column business_unit_id bigint,
	add column business_quantity numeric(18, 6),
	add column base_unit_id bigint,
	add column base_quantity numeric(18, 6),
	add column conversion_id bigint,
	add column conversion_rate_snapshot numeric(18, 6),
	add column quantity_scale_snapshot integer,
	add column rounding_mode_snapshot varchar(16),
	add column quantity_basis varchar(32);

update mfg_bom_item i
set business_unit_id = i.unit_id,
	business_quantity = i.quantity,
	base_unit_id = case when i.unit_id = m.unit_id then i.unit_id else null end,
	base_quantity = case when i.unit_id = m.unit_id then i.quantity else null end,
	quantity_basis = case when i.unit_id = m.unit_id then 'BASE_UNIT' else 'LEGACY_BUSINESS_UNIT' end
from mst_material m
where m.id = i.child_material_id;

alter table mfg_bom_item
	alter column business_unit_id set not null,
	alter column business_quantity set not null,
	alter column quantity_basis set not null,
	add constraint fk_mfg_bom_item_business_unit foreign key (business_unit_id) references mst_unit (id),
	add constraint fk_mfg_bom_item_base_unit foreign key (base_unit_id) references mst_unit (id),
	add constraint fk_mfg_bom_item_conversion foreign key (conversion_id) references mst_material_unit_conversion (id),
	add constraint ck_mfg_bom_item_business_quantity_positive check (business_quantity > 0),
	add constraint ck_mfg_bom_item_base_quantity_positive check (base_quantity is null or base_quantity > 0),
	add constraint ck_mfg_bom_item_quantity_basis check (
		quantity_basis in ('BASE_UNIT', 'CONVERTED_BUSINESS_UNIT', 'LEGACY_BUSINESS_UNIT')
	);

alter table mfg_work_order_material
	add column business_unit_id bigint,
	add column business_quantity numeric(18, 6),
	add column base_unit_id bigint,
	add column base_required_quantity numeric(18, 6),
	add column conversion_id bigint,
	add column conversion_rate_snapshot numeric(18, 6),
	add column quantity_scale_snapshot integer,
	add column rounding_mode_snapshot varchar(16),
	add column quantity_basis varchar(32);

update mfg_work_order_material wom
set business_unit_id = wom.unit_id,
	business_quantity = wom.required_quantity,
	base_unit_id = case when wom.unit_id = m.unit_id then wom.unit_id else null end,
	base_required_quantity = case when wom.unit_id = m.unit_id then wom.required_quantity else null end,
	quantity_basis = case when wom.unit_id = m.unit_id then 'BASE_UNIT' else 'LEGACY_BUSINESS_UNIT' end
from mst_material m
where m.id = wom.material_id;

alter table mfg_work_order_material
	alter column business_unit_id set not null,
	alter column business_quantity set not null,
	alter column quantity_basis set not null,
	add constraint fk_mfg_work_order_material_business_unit foreign key (business_unit_id) references mst_unit (id),
	add constraint fk_mfg_work_order_material_base_unit foreign key (base_unit_id) references mst_unit (id),
	add constraint fk_mfg_work_order_material_conversion foreign key (conversion_id) references mst_material_unit_conversion (id),
	add constraint ck_mfg_work_order_material_business_quantity_positive check (business_quantity > 0),
	add constraint ck_mfg_work_order_material_base_required_positive check (base_required_quantity is null or base_required_quantity > 0),
	add constraint ck_mfg_work_order_material_quantity_basis check (
		quantity_basis in ('BASE_UNIT', 'CONVERTED_BUSINESS_UNIT', 'LEGACY_BUSINESS_UNIT')
	);

create table mfg_bom_engineering_change (
	id bigserial primary key,
	eco_no varchar(64) not null,
	source_bom_id bigint not null,
	target_bom_id bigint not null,
	parent_material_id bigint not null,
	effective_from date not null,
	effective_to date,
	change_reason varchar(200) not null,
	impact_scope varchar(200) not null,
	change_summary varchar(1000) not null,
	status varchar(32) not null,
	applied_by varchar(64),
	applied_at timestamptz,
	cancel_reason varchar(500),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_mfg_bom_engineering_change_no unique (eco_no),
	constraint uk_mfg_bom_engineering_change_target unique (target_bom_id),
	constraint fk_mfg_bom_engineering_change_source foreign key (source_bom_id) references mfg_bom (id),
	constraint fk_mfg_bom_engineering_change_target foreign key (target_bom_id) references mfg_bom (id),
	constraint fk_mfg_bom_engineering_change_parent foreign key (parent_material_id) references mst_material (id),
	constraint ck_mfg_bom_engineering_change_status check (status in ('DRAFT', 'APPLIED', 'CANCELLED')),
	constraint ck_mfg_bom_engineering_change_effective_range check (
		effective_to is null or effective_from <= effective_to
	),
	constraint ck_mfg_bom_engineering_change_distinct_bom check (source_bom_id <> target_bom_id)
);

create index idx_mfg_bom_engineering_change_parent_status
	on mfg_bom_engineering_change (parent_material_id, status, effective_from desc, id desc);

create table mst_material_substitute (
	id bigserial primary key,
	main_material_id bigint not null,
	substitute_material_id bigint not null,
	scope_type varchar(32) not null,
	scope_id bigint,
	priority integer not null,
	substitute_rate numeric(18, 6) not null,
	effective_from date,
	effective_to date,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_mst_material_substitute_main foreign key (main_material_id) references mst_material (id),
	constraint fk_mst_material_substitute_substitute foreign key (substitute_material_id) references mst_material (id),
	constraint ck_mst_material_substitute_scope_type check (scope_type in ('GLOBAL', 'PARENT_MATERIAL', 'BOM')),
	constraint ck_mst_material_substitute_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_mst_material_substitute_distinct check (main_material_id <> substitute_material_id),
	constraint ck_mst_material_substitute_priority check (priority > 0),
	constraint ck_mst_material_substitute_rate_positive check (substitute_rate > 0),
	constraint ck_mst_material_substitute_effective_range check (
		effective_from is null or effective_to is null or effective_from <= effective_to
	)
);

create index idx_mst_material_substitute_lookup
	on mst_material_substitute (
		main_material_id,
		substitute_material_id,
		scope_type,
		scope_id,
		status,
		effective_from,
		effective_to
	);

do $$
begin
	if exists (
		select 1
		from inv_stock_balance b
		join mst_material m on m.id = b.material_id
		where b.unit_id <> m.unit_id
	) then
		raise exception '021 unit assertion failed: inv_stock_balance.unit_id must equal mst_material.unit_id';
	end if;
	if exists (
		select 1
		from inv_stock_movement mv
		join mst_material m on m.id = mv.material_id
		where mv.unit_id <> m.unit_id
	) then
		raise exception '021 unit assertion failed: inv_stock_movement.unit_id must equal mst_material.unit_id';
	end if;
	if exists (
		select 1
		from inv_stock_reservation r
		join mst_material m on m.id = r.material_id
		where r.unit_id <> m.unit_id
	) then
		raise exception '021 unit assertion failed: inv_stock_reservation.unit_id must equal mst_material.unit_id';
	end if;
	if exists (
		select 1
		from inv_stock_tracking_allocation a
		join mst_material m on m.id = a.material_id
		where a.unit_id <> m.unit_id
	) then
		raise exception '021 unit assertion failed: inv_stock_tracking_allocation.unit_id must equal mst_material.unit_id';
	end if;
end $$;
