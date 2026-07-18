alter table fin_receipt_allocation drop constraint if exists uk_fin_receipt_allocation_receipt;
alter table fin_payment_allocation drop constraint if exists uk_fin_payment_allocation_payment;

create unique index uk_fin_receipt_allocation_receipt_target
	on fin_receipt_allocation (receipt_id, receivable_id);

create unique index uk_fin_payment_allocation_payment_target
	on fin_payment_allocation (payment_id, payable_id);

alter table fin_payable drop constraint if exists fk_fin_payable_source_receipt;
alter table fin_payable drop constraint if exists ck_fin_payable_source_type;
alter table fin_payable add constraint ck_fin_payable_source_type check (
	source_type in ('PURCHASE_RECEIPT', 'EXPENSE', 'OUTSOURCING_SETTLEMENT')
);

create table fin_sales_invoice (
	id bigserial primary key,
	invoice_no varchar(64) not null,
	customer_id bigint not null,
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	invoice_date date not null,
	due_date date not null,
	external_invoice_no varchar(100),
	invoice_type varchar(32) not null,
	currency varchar(8) not null default 'CNY',
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	status varchar(32) not null,
	linked_receivable_id bigint,
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	party_snapshot jsonb not null default '{}'::jsonb,
	source_snapshot jsonb not null default '{}'::jsonb,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_fin_sales_invoice_no unique (invoice_no),
	constraint uk_fin_sales_invoice_external unique (customer_id, external_invoice_no),
	constraint fk_fin_sales_invoice_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_fin_sales_invoice_project foreign key (project_id) references sal_project (id),
	constraint fk_fin_sales_invoice_receivable foreign key (linked_receivable_id) references fin_receivable (id),
	constraint ck_fin_sales_invoice_source check (source_type = 'SALES_SHIPMENT'),
	constraint ck_fin_sales_invoice_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_sales_invoice_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_sales_invoice_status check (status in ('DRAFT', 'CONFIRMED', 'CANCELLED')),
	constraint ck_fin_sales_invoice_type check (invoice_type in ('GENERAL_VAT', 'SPECIAL_VAT', 'NONE')),
	constraint ck_fin_sales_invoice_currency check (currency = 'CNY'),
	constraint ck_fin_sales_invoice_amount check (
		tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create unique index uk_fin_sales_invoice_idempotency
	on fin_sales_invoice (created_by, idempotency_key)
	where idempotency_key is not null;
create index idx_fin_sales_invoice_source_active
	on fin_sales_invoice (source_type, source_id)
	where status <> 'CANCELLED';
create index idx_fin_sales_invoice_status_date
	on fin_sales_invoice (status, invoice_date desc, id desc);

create table fin_sales_invoice_line (
	id bigserial primary key,
	sales_invoice_id bigint not null,
	line_no integer not null,
	source_line_id bigint not null,
	sales_order_id bigint,
	sales_order_line_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	source_snapshot jsonb not null default '{}'::jsonb,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_fin_sales_invoice_line_invoice foreign key (sales_invoice_id) references fin_sales_invoice (id) on delete cascade,
	constraint fk_fin_sales_invoice_line_source foreign key (source_line_id) references sal_sales_shipment_line (id),
	constraint fk_fin_sales_invoice_line_order foreign key (sales_order_id) references sal_sales_order (id),
	constraint fk_fin_sales_invoice_line_order_line foreign key (sales_order_line_id) references sal_sales_order_line (id),
	constraint fk_fin_sales_invoice_line_material foreign key (material_id) references mst_material (id),
	constraint fk_fin_sales_invoice_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_fin_sales_invoice_line_no unique (sales_invoice_id, line_no),
	constraint ck_fin_sales_invoice_line_values check (
		quantity > 0 and tax_rate >= 0 and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
		and tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create index idx_fin_sales_invoice_line_source
	on fin_sales_invoice_line (source_line_id);

create table fin_sales_invoice_receivable_link (
	id bigserial primary key,
	sales_invoice_id bigint not null,
	receivable_id bigint not null,
	link_mode varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_fin_sales_invoice_link_invoice foreign key (sales_invoice_id) references fin_sales_invoice (id) on delete cascade,
	constraint fk_fin_sales_invoice_link_receivable foreign key (receivable_id) references fin_receivable (id),
	constraint uk_fin_sales_invoice_link_invoice unique (sales_invoice_id),
	constraint ck_fin_sales_invoice_link_mode check (link_mode in ('BIND_EXISTING', 'GENERATE_NEW'))
);

create table fin_purchase_invoice (
	id bigserial primary key,
	invoice_no varchar(64) not null,
	supplier_id bigint not null,
	settlement_kind varchar(32) not null,
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	source_type varchar(32) not null,
	source_id bigint not null,
	source_no varchar(64) not null,
	invoice_date date not null,
	due_date date not null,
	supplier_invoice_no varchar(100),
	invoice_type varchar(32) not null,
	currency varchar(8) not null default 'CNY',
	match_status varchar(32) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	status varchar(32) not null,
	linked_payable_id bigint,
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	party_snapshot jsonb not null default '{}'::jsonb,
	source_snapshot jsonb not null default '{}'::jsonb,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	matched_by varchar(64),
	matched_at timestamptz,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_fin_purchase_invoice_no unique (invoice_no),
	constraint uk_fin_purchase_invoice_supplier_no unique (supplier_id, supplier_invoice_no),
	constraint fk_fin_purchase_invoice_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_fin_purchase_invoice_project foreign key (project_id) references sal_project (id),
	constraint fk_fin_purchase_invoice_payable foreign key (linked_payable_id) references fin_payable (id),
	constraint ck_fin_purchase_invoice_kind check (settlement_kind in ('STANDARD_PURCHASE', 'OUTSOURCING')),
	constraint ck_fin_purchase_invoice_source check (
		(settlement_kind = 'STANDARD_PURCHASE' and source_type = 'PURCHASE_RECEIPT')
		or (settlement_kind = 'OUTSOURCING' and source_type = 'OUTSOURCING_RECEIPT')
	),
	constraint ck_fin_purchase_invoice_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_purchase_invoice_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_purchase_invoice_status check (status in ('DRAFT', 'CONFIRMED', 'CANCELLED')),
	constraint ck_fin_purchase_invoice_match check (match_status in ('UNMATCHED', 'MATCHED', 'EXCEPTION', 'NOT_APPLICABLE')),
	constraint ck_fin_purchase_invoice_type check (invoice_type in ('GENERAL_VAT', 'SPECIAL_VAT', 'NONE')),
	constraint ck_fin_purchase_invoice_currency check (currency = 'CNY'),
	constraint ck_fin_purchase_invoice_amount check (
		tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create unique index uk_fin_purchase_invoice_idempotency
	on fin_purchase_invoice (created_by, idempotency_key)
	where idempotency_key is not null;
create index idx_fin_purchase_invoice_source_active
	on fin_purchase_invoice (source_type, source_id)
	where status <> 'CANCELLED';
create index idx_fin_purchase_invoice_status_date
	on fin_purchase_invoice (status, invoice_date desc, id desc);

create table fin_purchase_invoice_line (
	id bigserial primary key,
	purchase_invoice_id bigint not null,
	line_no integer not null,
	source_line_id bigint not null,
	purchase_order_id bigint,
	purchase_order_line_id bigint,
	outsourcing_order_id bigint,
	material_id bigint not null,
	unit_id bigint not null,
	quantity numeric(18, 6) not null,
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_unit_price numeric(18, 6) not null,
	tax_included_unit_price numeric(18, 6) not null,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	match_status varchar(32) not null,
	source_snapshot jsonb not null default '{}'::jsonb,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_fin_purchase_invoice_line_invoice foreign key (purchase_invoice_id) references fin_purchase_invoice (id) on delete cascade,
	constraint fk_fin_purchase_invoice_line_po foreign key (purchase_order_id) references proc_purchase_order (id),
	constraint fk_fin_purchase_invoice_line_po_line foreign key (purchase_order_line_id) references proc_purchase_order_line (id),
	constraint fk_fin_purchase_invoice_line_outsourcing foreign key (outsourcing_order_id) references mfg_outsourcing_order (id),
	constraint fk_fin_purchase_invoice_line_material foreign key (material_id) references mst_material (id),
	constraint fk_fin_purchase_invoice_line_unit foreign key (unit_id) references mst_unit (id),
	constraint uk_fin_purchase_invoice_line_no unique (purchase_invoice_id, line_no),
	constraint uk_fin_purchase_invoice_line_source unique (purchase_invoice_id, source_line_id),
	constraint ck_fin_purchase_invoice_line_match check (match_status in ('MATCHED', 'EXCEPTION', 'NOT_APPLICABLE')),
	constraint ck_fin_purchase_invoice_line_values check (
		quantity > 0 and tax_rate >= 0 and tax_excluded_unit_price >= 0 and tax_included_unit_price >= 0
		and tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create table fin_purchase_invoice_match_difference (
	id bigserial primary key,
	purchase_invoice_id bigint not null,
	purchase_invoice_line_id bigint,
	difference_type varchar(64) not null,
	expected_value varchar(100) not null,
	actual_value varchar(100) not null,
	message varchar(255) not null,
	created_at timestamptz not null,
	constraint fk_fin_purchase_invoice_difference_invoice foreign key (purchase_invoice_id) references fin_purchase_invoice (id) on delete cascade,
	constraint fk_fin_purchase_invoice_difference_line foreign key (purchase_invoice_line_id) references fin_purchase_invoice_line (id) on delete cascade
);

create table fin_purchase_invoice_payable_link (
	id bigserial primary key,
	purchase_invoice_id bigint not null,
	payable_id bigint not null,
	link_mode varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_fin_purchase_invoice_link_invoice foreign key (purchase_invoice_id) references fin_purchase_invoice (id) on delete cascade,
	constraint fk_fin_purchase_invoice_link_payable foreign key (payable_id) references fin_payable (id),
	constraint uk_fin_purchase_invoice_link_invoice unique (purchase_invoice_id),
	constraint ck_fin_purchase_invoice_link_mode check (link_mode in ('BIND_EXISTING', 'GENERATE_NEW'))
);

create table fin_expense (
	id bigserial primary key,
	expense_no varchar(64) not null,
	supplier_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	expense_date date not null,
	due_date date not null,
	invoice_type varchar(32) not null,
	currency varchar(8) not null default 'CNY',
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	status varchar(32) not null,
	linked_payable_id bigint,
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	party_snapshot jsonb not null default '{}'::jsonb,
	source_snapshot jsonb not null default '{}'::jsonb,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_fin_expense_no unique (expense_no),
	constraint fk_fin_expense_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_fin_expense_project foreign key (project_id) references sal_project (id),
	constraint fk_fin_expense_payable foreign key (linked_payable_id) references fin_payable (id),
	constraint ck_fin_expense_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_expense_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_expense_status check (status in ('DRAFT', 'CONFIRMED', 'CANCELLED')),
	constraint ck_fin_expense_type check (invoice_type in ('GENERAL_VAT', 'SPECIAL_VAT', 'NONE')),
	constraint ck_fin_expense_currency check (currency = 'CNY'),
	constraint ck_fin_expense_amount check (
		tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create unique index uk_fin_expense_idempotency
	on fin_expense (created_by, idempotency_key)
	where idempotency_key is not null;

create table fin_expense_line (
	id bigserial primary key,
	expense_id bigint not null,
	line_no integer not null,
	expense_category varchar(64) not null,
	description varchar(255),
	source_type varchar(64),
	source_id bigint,
	source_no varchar(64),
	tax_rate numeric(9, 6) not null default 0,
	tax_excluded_amount numeric(18, 2) not null,
	tax_amount numeric(18, 2) not null,
	tax_included_amount numeric(18, 2) not null,
	source_snapshot jsonb not null default '{}'::jsonb,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_fin_expense_line_expense foreign key (expense_id) references fin_expense (id) on delete cascade,
	constraint uk_fin_expense_line_no unique (expense_id, line_no),
	constraint ck_fin_expense_line_values check (
		tax_rate >= 0 and tax_excluded_amount >= 0 and tax_amount >= 0 and tax_included_amount >= 0
		and tax_excluded_amount + tax_amount = tax_included_amount
	)
);

create table fin_receipt_balance (
	receipt_id bigint primary key,
	customer_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	original_amount numeric(18, 2) not null,
	allocated_amount numeric(18, 2) not null default 0,
	available_amount numeric(18, 2) not null,
	status varchar(32) not null,
	updated_at timestamptz not null,
	constraint fk_fin_receipt_balance_receipt foreign key (receipt_id) references fin_receipt (id) on delete cascade,
	constraint fk_fin_receipt_balance_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_fin_receipt_balance_project foreign key (project_id) references sal_project (id),
	constraint ck_fin_receipt_balance_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_receipt_balance_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_receipt_balance_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_receipt_balance_amount check (
		original_amount >= 0 and allocated_amount >= 0 and available_amount >= 0
		and original_amount = allocated_amount + available_amount
	)
);

create table fin_payment_balance (
	payment_id bigint primary key,
	supplier_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	original_amount numeric(18, 2) not null,
	allocated_amount numeric(18, 2) not null default 0,
	available_amount numeric(18, 2) not null,
	status varchar(32) not null,
	updated_at timestamptz not null,
	constraint fk_fin_payment_balance_payment foreign key (payment_id) references fin_payment (id) on delete cascade,
	constraint fk_fin_payment_balance_supplier foreign key (supplier_id) references mst_supplier (id),
	constraint fk_fin_payment_balance_project foreign key (project_id) references sal_project (id),
	constraint ck_fin_payment_balance_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_payment_balance_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_payment_balance_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_payment_balance_amount check (
		original_amount >= 0 and allocated_amount >= 0 and available_amount >= 0
		and original_amount = allocated_amount + available_amount
	)
);

create table fin_cash_idempotency (
	id bigserial primary key,
	document_type varchar(16) not null,
	document_id bigint not null,
	created_by varchar(64) not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	created_at timestamptz not null,
	constraint uk_fin_cash_idempotency unique (document_type, created_by, idempotency_key),
	constraint ck_fin_cash_idempotency_type check (document_type in ('RECEIPT', 'PAYMENT'))
);

create table fin_settlement_allocation (
	id bigserial primary key,
	allocation_no varchar(64) not null,
	settlement_side varchar(16) not null,
	cash_source_type varchar(16) not null,
	cash_source_id bigint not null,
	party_id bigint not null,
	ownership_type varchar(32) not null,
	project_id bigint,
	business_date date not null,
	total_amount numeric(18, 2) not null,
	status varchar(32) not null,
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	posted_by varchar(64),
	posted_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_fin_settlement_allocation_no unique (allocation_no),
	constraint ck_fin_settlement_allocation_side check (settlement_side in ('RECEIVABLE', 'PAYABLE')),
	constraint ck_fin_settlement_allocation_cash check (cash_source_type in ('RECEIPT', 'PAYMENT')),
	constraint ck_fin_settlement_allocation_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_settlement_allocation_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_settlement_allocation_status check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
	constraint ck_fin_settlement_allocation_amount check (total_amount > 0)
);

create unique index uk_fin_settlement_allocation_idempotency
	on fin_settlement_allocation (created_by, idempotency_key)
	where idempotency_key is not null;
create index idx_fin_settlement_allocation_cash
	on fin_settlement_allocation (cash_source_type, cash_source_id, status);

create table fin_settlement_allocation_line (
	id bigserial primary key,
	allocation_id bigint not null,
	line_no integer not null,
	target_type varchar(16) not null,
	target_id bigint not null,
	amount numeric(18, 2) not null,
	created_at timestamptz not null,
	constraint fk_fin_settlement_allocation_line_header foreign key (allocation_id) references fin_settlement_allocation (id) on delete cascade,
	constraint uk_fin_settlement_allocation_line_no unique (allocation_id, line_no),
	constraint uk_fin_settlement_allocation_line_target unique (allocation_id, target_type, target_id),
	constraint ck_fin_settlement_allocation_line_target check (target_type in ('RECEIVABLE', 'PAYABLE')),
	constraint ck_fin_settlement_allocation_line_amount check (amount > 0)
);

create table fin_voucher_draft (
	id bigserial primary key,
	draft_no varchar(64) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	status varchar(32) not null,
	business_date date not null,
	summary varchar(255) not null,
	party_type varchar(16),
	party_id bigint,
	party_name varchar(120),
	ownership_type varchar(32) not null default 'PUBLIC',
	project_id bigint,
	debit_amount numeric(18, 2) not null,
	credit_amount numeric(18, 2) not null,
	generation_version bigint not null default 0,
	formal_voucher_no varchar(64),
	posting_status varchar(32),
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	ready_by varchar(64),
	ready_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint uk_fin_voucher_draft_no unique (draft_no),
	constraint uk_fin_voucher_draft_source unique (source_type, source_id),
	constraint ck_fin_voucher_draft_status check (status in ('DRAFT', 'READY', 'CANCELLED')),
	constraint ck_fin_voucher_draft_ownership check (ownership_type in ('PUBLIC', 'PROJECT')),
	constraint ck_fin_voucher_draft_project check (
		(ownership_type = 'PUBLIC' and project_id is null)
		or (ownership_type = 'PROJECT' and project_id is not null)
	),
	constraint ck_fin_voucher_draft_balanced check (
		debit_amount >= 0 and credit_amount >= 0 and debit_amount = credit_amount
	),
	constraint ck_fin_voucher_draft_non_formal check (
		formal_voucher_no is null and posting_status is null
	)
);

create unique index uk_fin_voucher_draft_idempotency
	on fin_voucher_draft (created_by, idempotency_key)
	where idempotency_key is not null;

create table fin_stage028_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint not null,
	resource_version bigint not null,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(64) not null,
	result_resource_id bigint not null,
	result_version bigint not null,
	created_at timestamptz not null default now(),
	constraint uk_fin_stage028_action_idempotency unique (
		operator_user_id, action, resource_type, resource_id, idempotency_key
	)
);

create table fin_voucher_draft_line (
	id bigserial primary key,
	draft_id bigint not null,
	line_no integer not null,
	direction varchar(16) not null,
	business_category varchar(64) not null,
	amount numeric(18, 2) not null,
	source_type varchar(64) not null,
	source_id bigint not null,
	created_at timestamptz not null,
	constraint fk_fin_voucher_draft_line_draft foreign key (draft_id) references fin_voucher_draft (id) on delete cascade,
	constraint uk_fin_voucher_draft_line_no unique (draft_id, line_no),
	constraint ck_fin_voucher_draft_line_direction check (direction in ('DEBIT', 'CREDIT')),
	constraint ck_fin_voucher_draft_line_amount check (amount > 0)
);

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_path,
	seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('finance:sales-invoice:view', '查看销售发票', '/finance/sales-invoices', 'GET', '/api/admin/finance/sales-invoices/**', 628),
		('finance:sales-invoice:create', '创建销售发票', '/finance/sales-invoices', 'POST', '/api/admin/finance/sales-invoices', 629),
		('finance:sales-invoice:update', '更新销售发票草稿', '/finance/sales-invoices', 'PUT', '/api/admin/finance/sales-invoices/{id}', 630),
		('finance:sales-invoice:confirm', '确认销售发票', '/finance/sales-invoices', 'PUT', '/api/admin/finance/sales-invoices/{id}/confirm', 631),
		('finance:sales-invoice:cancel', '取消销售发票', '/finance/sales-invoices', 'PUT', '/api/admin/finance/sales-invoices/{id}/cancel', 632),
		('finance:purchase-invoice:view', '查看采购发票', '/finance/purchase-invoices', 'GET', '/api/admin/finance/purchase-invoices/**', 633),
		('finance:purchase-invoice:create', '创建采购发票', '/finance/purchase-invoices', 'POST', '/api/admin/finance/purchase-invoices', 634),
		('finance:purchase-invoice:update', '更新采购发票草稿', '/finance/purchase-invoices', 'PUT', '/api/admin/finance/purchase-invoices/{id}', 635),
		('finance:purchase-invoice:match', '执行采购发票匹配', '/finance/purchase-invoices', 'PUT', '/api/admin/finance/purchase-invoices/{id}/match', 636),
		('finance:purchase-invoice:confirm', '确认采购发票', '/finance/purchase-invoices', 'PUT', '/api/admin/finance/purchase-invoices/{id}/confirm', 637),
		('finance:purchase-invoice:cancel', '取消采购发票', '/finance/purchase-invoices', 'PUT', '/api/admin/finance/purchase-invoices/{id}/cancel', 638),
		('finance:expense:view', '查看费用单', '/finance/expenses', 'GET', '/api/admin/finance/expenses/**', 639),
		('finance:expense:create', '创建费用单', '/finance/expenses', 'POST', '/api/admin/finance/expenses', 640),
		('finance:expense:update', '更新费用单草稿', '/finance/expenses', 'PUT', '/api/admin/finance/expenses/{id}', 641),
		('finance:expense:confirm', '确认费用单', '/finance/expenses', 'PUT', '/api/admin/finance/expenses/{id}/confirm', 642),
		('finance:expense:cancel', '取消费用单', '/finance/expenses', 'PUT', '/api/admin/finance/expenses/{id}/cancel', 643),
		('finance:advance-receipt:view', '查看预收款', '/finance/advance-receipts', 'GET', '/api/admin/finance/advance-receipts/**', 644),
		('finance:advance-receipt:create', '创建预收款', '/finance/advance-receipts', 'POST', '/api/admin/finance/advance-receipts', 645),
		('finance:advance-receipt:update', '更新预收款草稿', '/finance/advance-receipts', 'PUT', '/api/admin/finance/advance-receipts/{id}', 646),
		('finance:advance-receipt:post', '过账预收款', '/finance/advance-receipts', 'PUT', '/api/admin/finance/advance-receipts/{id}/post', 647),
		('finance:advance-receipt:cancel', '取消预收款草稿', '/finance/advance-receipts', 'PUT', '/api/admin/finance/advance-receipts/{id}/cancel', 648),
		('finance:prepayment:view', '查看预付款', '/finance/prepayments', 'GET', '/api/admin/finance/prepayments/**', 649),
		('finance:prepayment:create', '创建预付款', '/finance/prepayments', 'POST', '/api/admin/finance/prepayments', 650),
		('finance:prepayment:update', '更新预付款草稿', '/finance/prepayments', 'PUT', '/api/admin/finance/prepayments/{id}', 651),
		('finance:prepayment:post', '过账预付款', '/finance/prepayments', 'PUT', '/api/admin/finance/prepayments/{id}/post', 652),
		('finance:prepayment:cancel', '取消预付款草稿', '/finance/prepayments', 'PUT', '/api/admin/finance/prepayments/{id}/cancel', 653),
		('finance:settlement-allocation:view', '查看多目标核销', '/finance/settlement-workbench', 'GET', '/api/admin/finance/settlement-workbench/**', 654),
		('finance:settlement-allocation:create', '创建多目标核销', '/finance/settlement-workbench', 'POST', '/api/admin/finance/settlement-workbench/allocations', 655),
		('finance:settlement-allocation:update', '更新多目标核销草稿', '/finance/settlement-workbench', 'PUT', '/api/admin/finance/settlement-workbench/allocations/{id}', 656),
		('finance:settlement-allocation:post', '过账多目标核销', '/finance/settlement-workbench', 'PUT', '/api/admin/finance/settlement-workbench/allocations/{id}/post', 657),
		('finance:settlement-allocation:cancel', '取消多目标核销草稿', '/finance/settlement-workbench', 'PUT', '/api/admin/finance/settlement-workbench/allocations/{id}/cancel', 658),
		('finance:voucher-draft:view', '查看凭证草稿', '/finance/voucher-drafts', 'GET', '/api/admin/finance/voucher-drafts/**', 659),
		('finance:voucher-draft:generate', '生成凭证草稿', '/finance/voucher-drafts', 'POST', '/api/admin/finance/voucher-drafts/generate', 660),
		('finance:voucher-draft:ready', '凭证草稿置为就绪', '/finance/voucher-drafts', 'PUT', '/api/admin/finance/voucher-drafts/{id}/ready', 661),
		('finance:voucher-draft:cancel', '取消凭证草稿', '/finance/voucher-drafts', 'PUT', '/api/admin/finance/voucher-drafts/{id}/cancel', 662),
		('finance:settlement-sensitive:view', '查看结算敏感资料', '/finance', null, null, 663),
		('finance:settlement-sensitive:update', '维护结算敏感资料', '/finance', null, null, 664)
) as seed(code, name, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'finance'
on conflict (code) do nothing;

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code in (
	'finance:sales-invoice:view',
	'finance:sales-invoice:create',
	'finance:sales-invoice:update',
	'finance:sales-invoice:confirm',
	'finance:sales-invoice:cancel',
	'finance:purchase-invoice:view',
	'finance:purchase-invoice:create',
	'finance:purchase-invoice:update',
	'finance:purchase-invoice:match',
	'finance:purchase-invoice:confirm',
	'finance:purchase-invoice:cancel',
	'finance:expense:view',
	'finance:expense:create',
	'finance:expense:update',
	'finance:expense:confirm',
	'finance:expense:cancel',
	'finance:advance-receipt:view',
	'finance:advance-receipt:create',
	'finance:advance-receipt:update',
	'finance:advance-receipt:post',
	'finance:advance-receipt:cancel',
	'finance:prepayment:view',
	'finance:prepayment:create',
	'finance:prepayment:update',
	'finance:prepayment:post',
	'finance:prepayment:cancel',
	'finance:settlement-allocation:view',
	'finance:settlement-allocation:create',
	'finance:settlement-allocation:update',
	'finance:settlement-allocation:post',
	'finance:settlement-allocation:cancel',
	'finance:voucher-draft:view',
	'finance:voucher-draft:generate',
	'finance:voucher-draft:ready',
	'finance:voucher-draft:cancel',
	'finance:settlement-sensitive:view',
	'finance:settlement-sensitive:update'
)
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);
