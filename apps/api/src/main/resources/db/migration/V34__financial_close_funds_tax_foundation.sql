alter table gl_voucher
	drop constraint ck_gl_voucher_source_type;

alter table gl_voucher
	add constraint ck_gl_voucher_source_type check (
		source_type in ('MANUAL', 'FIN_VOUCHER_DRAFT', 'REVERSAL', 'PROFIT_LOSS_CARRYFORWARD', 'TAX_SUMMARY')
	);

create table fin_close_check_run (
	id bigserial primary key,
	ledger_id bigint not null references gl_ledger (id),
	period_id bigint not null references gl_accounting_period (id),
	status varchar(32) not null,
	close_version bigint not null default 0,
	source_fingerprint varchar(64) not null,
	blocking_count integer not null default 0,
	warning_count integer not null default 0,
	failure_message varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	completed_at timestamptz,
	version bigint not null default 0,
	constraint ck_fin_close_check_run_status check (
		status in ('CHECKING', 'BLOCKED', 'READY', 'STALE', 'CONSUMED', 'FAILED')
	),
	constraint ck_fin_close_check_run_counts check (blocking_count >= 0 and warning_count >= 0)
);

create unique index uk_fin_close_check_run_current_ready
	on fin_close_check_run (period_id)
	where status = 'READY';

create index idx_fin_close_check_run_period
	on fin_close_check_run (period_id, created_at desc, id desc);

create table fin_close_check_item (
	id bigserial primary key,
	check_run_id bigint not null references fin_close_check_run (id),
	check_code varchar(80) not null,
	severity varchar(32) not null,
	passed boolean not null,
	actual_value varchar(255),
	expected_value varchar(255),
	conclusion varchar(500) not null,
	source_type varchar(64),
	source_id bigint,
	source_no varchar(120),
	source_restricted boolean not null default false,
	created_at timestamptz not null default now(),
	constraint ck_fin_close_check_item_severity check (severity in ('BLOCKING', 'WARNING'))
);

create table fin_close_run (
	id bigserial primary key,
	ledger_id bigint not null references gl_ledger (id),
	period_id bigint not null references gl_accounting_period (id),
	check_run_id bigint not null references fin_close_check_run (id),
	close_version bigint not null default 1,
	status varchar(32) not null,
	source_fingerprint varchar(64) not null,
	snapshot_id bigint,
	closed_by varchar(64) not null,
	closed_at timestamptz not null,
	close_reason varchar(500) not null,
	reopened_by varchar(64),
	reopened_at timestamptz,
	reopen_reason varchar(500),
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_close_run_status check (status in ('CLOSED', 'REOPENED')),
	constraint ck_fin_close_run_reopen_reason check (status <> 'REOPENED' or reopen_reason is not null)
);

create unique index uk_fin_close_run_current_closed
	on fin_close_run (period_id)
	where status = 'CLOSED';

create index idx_fin_close_run_period
	on fin_close_run (period_id, close_version desc, id desc);

create table fin_close_snapshot (
	id bigserial primary key,
	close_run_id bigint not null references fin_close_run (id),
	period_id bigint not null references gl_accounting_period (id),
	close_version bigint not null,
	source_fingerprint varchar(64) not null,
	trial_balance_json jsonb not null default '{}'::jsonb,
	bank_reconciliation_json jsonb not null default '{}'::jsonb,
	tax_summary_json jsonb not null default '{}'::jsonb,
	profit_loss_transfer_id bigint,
	business_period_close_run_id bigint references biz_period_close_run (id),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint uk_fin_close_snapshot_run unique (close_run_id)
);

alter table fin_close_run
	add constraint fk_fin_close_run_snapshot foreign key (snapshot_id) references fin_close_snapshot (id);

create table fin_close_reopen_request (
	id bigserial primary key,
	close_run_id bigint not null references fin_close_run (id),
	period_id bigint not null references gl_accounting_period (id),
	request_no varchar(80) not null,
	status varchar(32) not null,
	reason varchar(500) not null,
	approval_instance_id bigint references platform_approval_instance (id),
	requested_by_user_id bigint not null,
	requested_by_username varchar(64) not null,
	applied_by varchar(64),
	applied_at timestamptz,
	created_at timestamptz not null default now(),
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_fin_close_reopen_request_no unique (request_no),
	constraint ck_fin_close_reopen_request_status check (
		status in ('SUBMITTED', 'REJECTED', 'APPLIED', 'CANCELLED')
	)
);

create unique index uk_fin_close_reopen_request_submitted
	on fin_close_reopen_request (close_run_id)
	where status = 'SUBMITTED';

create table fin_close_profit_loss_transfer (
	id bigserial primary key,
	ledger_id bigint not null references gl_ledger (id),
	period_id bigint not null references gl_accounting_period (id),
	status varchar(32) not null,
	source_fingerprint varchar(64) not null,
	voucher_id bigint references gl_voucher (id),
	voucher_status varchar(32),
	debit_total numeric(18, 2) not null default 0,
	credit_total numeric(18, 2) not null default 0,
	line_json jsonb not null default '[]'::jsonb,
	reason varchar(500),
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_close_profit_loss_status check (
		status in ('PREVIEW', 'DRAFT', 'ZERO_BALANCE', 'SUBMITTED', 'POSTED', 'CANCELLED', 'STALE')
	)
);

create unique index uk_fin_close_profit_loss_idempotency
	on fin_close_profit_loss_transfer (period_id, idempotency_key)
	where idempotency_key is not null;

create unique index uk_fin_close_profit_loss_active_source
	on fin_close_profit_loss_transfer (period_id, source_fingerprint)
	where status in ('DRAFT', 'ZERO_BALANCE', 'SUBMITTED', 'POSTED');

create table fin_close_action_idempotency (
	id bigserial primary key,
	operator_user_id bigint not null,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id bigint,
	resource_version bigint,
	idempotency_key varchar(120) not null,
	request_fingerprint varchar(64) not null,
	result_resource_type varchar(64) not null,
	result_resource_id bigint not null,
	result_version bigint,
	created_at timestamptz not null default now()
);

create unique index uk_fin_close_action_idempotency
	on fin_close_action_idempotency (
		operator_user_id, action, resource_type, coalesce(resource_id, 0), idempotency_key
	);

create table fin_close_audit_event (
	id bigserial primary key,
	operator_user_id bigint,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	result varchar(32) not null,
	resource_type varchar(64) not null,
	resource_id bigint,
	resource_version bigint,
	reason varchar(500),
	error_code varchar(64),
	request_fingerprint varchar(64),
	source_fingerprint varchar(64),
	before_state jsonb,
	after_state jsonb,
	created_at timestamptz not null default now(),
	constraint ck_fin_close_audit_result check (result in ('SUCCESS', 'FAILURE'))
);

create index idx_fin_close_audit_resource
	on fin_close_audit_event (resource_type, resource_id, created_at desc);

create table fin_bank_account (
	id bigserial primary key,
	account_name varchar(120) not null,
	account_type varchar(32) not null,
	bank_name varchar(120) not null,
	currency varchar(8) not null default 'CNY',
	gl_account_id bigint not null references gl_account (id),
	account_fingerprint varchar(64) not null,
	account_last4 varchar(8) not null,
	account_masked varchar(64) not null,
	status varchar(32) not null,
	opened_on date,
	disabled_reason varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_bank_account_status check (status in ('ENABLED', 'DISABLED')),
	constraint ck_fin_bank_account_currency check (currency = 'CNY')
);

create unique index uk_fin_bank_account_fingerprint
	on fin_bank_account (account_fingerprint);

create table fin_bank_statement (
	id bigserial primary key,
	bank_account_id bigint not null references fin_bank_account (id),
	statement_no varchar(120),
	source_method varchar(32) not null,
	period_code varchar(7),
	import_fingerprint varchar(64) not null,
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_bank_statement_status check (status in ('IMPORTED', 'CANCELLED')),
	constraint ck_fin_bank_statement_source_method check (source_method in ('MANUAL', 'IMPORT'))
);

create unique index uk_fin_bank_statement_import
	on fin_bank_statement (bank_account_id, import_fingerprint);

create table fin_bank_statement_line (
	id bigserial primary key,
	statement_id bigint references fin_bank_statement (id),
	bank_account_id bigint not null references fin_bank_account (id),
	transaction_date date not null,
	posting_date date not null,
	direction varchar(16) not null,
	amount numeric(18, 2) not null,
	counterparty_name varchar(160),
	summary varchar(500),
	bank_transaction_id varchar(120),
	reference_no varchar(120),
	dedupe_fingerprint varchar(64) not null,
	status varchar(32) not null,
	source_method varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_bank_statement_line_status check (
		status in ('UNMATCHED', 'PARTIALLY_MATCHED', 'MATCHED', 'IGNORED')
	),
	constraint ck_fin_bank_statement_line_direction check (direction in ('CREDIT', 'DEBIT')),
	constraint ck_fin_bank_statement_line_amount check (amount > 0)
);

create unique index uk_fin_bank_statement_line_dedupe
	on fin_bank_statement_line (bank_account_id, dedupe_fingerprint);

create table fin_bank_reconciliation_run (
	id bigserial primary key,
	period_id bigint not null references gl_accounting_period (id),
	bank_account_id bigint not null references fin_bank_account (id),
	status varchar(32) not null,
	statement_balance numeric(18, 2) not null default 0,
	ledger_balance numeric(18, 2) not null default 0,
	difference_amount numeric(18, 2) not null default 0,
	source_fingerprint varchar(64) not null,
	confirmed_by varchar(64),
	confirmed_at timestamptz,
	reopened_by varchar(64),
	reopened_at timestamptz,
	reopen_reason varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_bank_reconciliation_run_status check (
		status in ('DRAFT', 'RECONCILING', 'BALANCED', 'CONFIRMED', 'REOPENED')
	)
);

create index idx_fin_bank_reconciliation_run_period
	on fin_bank_reconciliation_run (period_id, bank_account_id, created_at desc);

create table fin_bank_reconciliation_match (
	id bigserial primary key,
	run_id bigint not null references fin_bank_reconciliation_run (id),
	match_group_no varchar(80) not null,
	statement_line_id bigint references fin_bank_statement_line (id),
	ledger_entry_id bigint references gl_ledger_entry (id),
	match_amount numeric(18, 2) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	constraint ck_fin_bank_reconciliation_match_amount check (match_amount > 0)
);

create table fin_bank_reconciliation_exception (
	id bigserial primary key,
	run_id bigint not null references fin_bank_reconciliation_run (id),
	statement_line_id bigint references fin_bank_statement_line (id),
	ledger_entry_id bigint references gl_ledger_entry (id),
	exception_type varchar(64) not null,
	amount numeric(18, 2) not null default 0,
	reason varchar(500) not null,
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_bank_reconciliation_exception_type check (
		exception_type in ('BANK_ONLY_CREDIT', 'BANK_ONLY_DEBIT', 'BOOK_ONLY_DEBIT', 'BOOK_ONLY_CREDIT')
	),
	constraint ck_fin_bank_reconciliation_exception_status check (status in ('OPEN', 'RESOLVED'))
);

create table fin_tax_profile (
	id bigserial primary key,
	taxpayer_type varchar(32) not null,
	credit_code varchar(64) not null,
	tax_authority varchar(160),
	vat_periodicity varchar(32) not null,
	income_tax_rate numeric(9, 4) not null default 0,
	urban_maintenance_rate numeric(9, 4) not null default 0,
	education_surcharge_rate numeric(9, 4) not null default 0,
	local_education_surcharge_rate numeric(9, 4) not null default 0,
	income_adjustment_increase numeric(18, 2) not null default 0,
	income_adjustment_decrease numeric(18, 2) not null default 0,
	loss_deduction numeric(18, 2) not null default 0,
	prepaid_income_tax numeric(18, 2) not null default 0,
	effective_from date not null,
	current_flag boolean not null default true,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_tax_profile_taxpayer check (taxpayer_type in ('GENERAL', 'SMALL_SCALE')),
	constraint ck_fin_tax_profile_vat_periodicity check (vat_periodicity in ('MONTHLY', 'QUARTERLY'))
);

create unique index uk_fin_tax_profile_current
	on fin_tax_profile (current_flag)
	where current_flag = true;

create table fin_tax_rate_rule (
	id bigserial primary key,
	tax_type varchar(32) not null,
	rate_code varchar(64) not null,
	rate_value numeric(9, 4) not null,
	effective_from date not null,
	effective_to date,
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_fin_tax_rate_rule unique (tax_type, rate_code, effective_from),
	constraint ck_fin_tax_rate_rule_status check (status in ('ENABLED', 'DISABLED'))
);

create table fin_tax_invoice_type (
	id bigserial primary key,
	code varchar(64) not null,
	name varchar(120) not null,
	direction varchar(16) not null,
	deductible boolean not null default false,
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint uk_fin_tax_invoice_type_code unique (code),
	constraint ck_fin_tax_invoice_type_direction check (direction in ('OUTPUT', 'INPUT')),
	constraint ck_fin_tax_invoice_type_status check (status in ('ENABLED', 'DISABLED'))
);

create table fin_tax_period_summary (
	id bigserial primary key,
	period_id bigint not null references gl_accounting_period (id),
	period_code varchar(7) not null,
	tax_type varchar(32) not null,
	status varchar(32) not null,
	source_fingerprint varchar(64) not null,
	output_vat numeric(18, 2) not null default 0,
	input_vat numeric(18, 2) not null default 0,
	transfer_out_vat numeric(18, 2) not null default 0,
	adjustment_amount numeric(18, 2) not null default 0,
	opening_credit_vat numeric(18, 2) not null default 0,
	vat_payable numeric(18, 2) not null default 0,
	urban_maintenance_tax numeric(18, 2) not null default 0,
	education_surcharge_tax numeric(18, 2) not null default 0,
	local_education_surcharge_tax numeric(18, 2) not null default 0,
	additional_tax_total numeric(18, 2) not null default 0,
	ending_credit_vat numeric(18, 2) not null default 0,
	income_adjustment_increase numeric(18, 2) not null default 0,
	income_adjustment_decrease numeric(18, 2) not null default 0,
	loss_deduction numeric(18, 2) not null default 0,
	prepaid_income_tax numeric(18, 2) not null default 0,
	income_tax_estimated numeric(18, 2) not null default 0,
	income_tax_payable numeric(18, 2) not null default 0,
	disclaimer varchar(500) not null,
	stale boolean not null default false,
	current_flag boolean not null default true,
	voucher_id bigint references gl_voucher (id),
	idempotency_key varchar(120),
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_tax_period_summary_status check (status in ('DRAFT', 'CALCULATED', 'CONFIRMED')),
	constraint ck_fin_tax_period_summary_tax_type check (tax_type in ('VAT', 'INCOME_TAX'))
);

create unique index uk_fin_tax_period_summary_current
	on fin_tax_period_summary (period_id, tax_type, source_fingerprint)
	where current_flag = true;

create table fin_tax_summary_line (
	id bigserial primary key,
	summary_id bigint not null references fin_tax_period_summary (id),
	source_type varchar(64) not null,
	source_id bigint not null,
	source_no varchar(120) not null,
	direction varchar(16) not null,
	amount numeric(18, 2) not null default 0,
	tax_amount numeric(18, 2) not null default 0,
	line_json jsonb not null default '{}'::jsonb,
	created_at timestamptz not null default now(),
	constraint ck_fin_tax_summary_line_direction check (direction in ('OUTPUT', 'INPUT', 'ADJUSTMENT'))
);

create table fin_tax_adjustment (
	id bigserial primary key,
	summary_id bigint not null references fin_tax_period_summary (id),
	adjustment_type varchar(64) not null,
	amount numeric(18, 2) not null,
	reason varchar(500) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_tax_adjustment_type check (adjustment_type in ('OUTPUT_INCREASE', 'OUTPUT_DECREASE', 'INPUT_INCREASE', 'INPUT_DECREASE'))
);

create table fin_tax_payment_record (
	id bigserial primary key,
	summary_id bigint not null references fin_tax_period_summary (id),
	tax_type varchar(32) not null,
	payment_date date not null,
	amount numeric(18, 2) not null,
	payment_method varchar(64),
	reference_no varchar(120),
	voucher_id bigint references gl_voucher (id),
	payment_id bigint references fin_payment (id),
	bank_account_id bigint references fin_bank_account (id),
	reason varchar(500),
	correction_of_id bigint references fin_tax_payment_record (id),
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null default now(),
	updated_by varchar(64) not null,
	updated_at timestamptz not null default now(),
	version bigint not null default 0,
	constraint ck_fin_tax_payment_record_status check (status in ('RECORDED', 'CORRECTED')),
	constraint ck_fin_tax_payment_record_amount check (amount <> 0)
);

create or replace function fin_close_immutable_guard()
returns trigger language plpgsql as $$
begin
	if tg_table_name = 'fin_close_snapshot' then
		raise exception '财务结账快照不可更新或删除';
	end if;
	if tg_table_name = 'fin_tax_period_summary' and old.status = 'CONFIRMED' then
		raise exception '已确认税务汇总不可更新或删除';
	end if;
	if tg_table_name = 'fin_bank_reconciliation_run' and old.status = 'CONFIRMED' then
		raise exception '已确认银行对账不可更新或删除';
	end if;
	if tg_table_name = 'fin_close_profit_loss_transfer' and old.status = 'POSTED' then
		raise exception '已记账损益结转不可更新或删除';
	end if;
	if tg_table_name = 'fin_close_reopen_request' and old.status = 'APPLIED' then
		raise exception '已应用反结账申请不可更新或删除';
	end if;
	if tg_op = 'DELETE' then
		return old;
	end if;
	return new;
end;
$$;

create trigger tr_fin_close_snapshot_immutable
	before update or delete on fin_close_snapshot
	for each row execute function fin_close_immutable_guard();

create trigger tr_fin_tax_period_summary_immutable
	before update or delete on fin_tax_period_summary
	for each row execute function fin_close_immutable_guard();

create trigger tr_fin_bank_reconciliation_run_immutable
	before update or delete on fin_bank_reconciliation_run
	for each row execute function fin_close_immutable_guard();

create trigger tr_fin_close_profit_loss_transfer_immutable
	before update or delete on fin_close_profit_loss_transfer
	for each row execute function fin_close_immutable_guard();

create trigger tr_fin_close_reopen_request_immutable
	before update or delete on fin_close_reopen_request
	for each row execute function fin_close_immutable_guard();

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
values ('FINANCIAL_PERIOD_REOPEN', '财务期间反结账', 'FIN_CLOSE_REOPEN_REQUEST', 'APPLY', 1, 'ENABLED')
on conflict (scene_code) do update
set name = excluded.name,
    business_object_type = excluded.business_object_type,
    action_code = excluded.action_code,
    definition_version = excluded.definition_version,
    status = excluded.status,
    updated_at = now();

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select id, 1, '反结账审批', 'financial-close:period:reopen'
from platform_approval_definition
where scene_code = 'FINANCIAL_PERIOD_REOPEN'
on conflict (definition_id, step_no) do update
set name = excluded.name,
    candidate_permission_code = excluded.candidate_permission_code;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select 'financial-close', '财务结账', 'MENU', gl.id, '/gl/financial-close', null, null, 881,
       'system', now(), 'system', now()
from sys_permission gl
where gl.code = 'gl'
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'MENU', parent.id, seed.route_path, null, null, seed.sort_order,
       'system', now(), 'system', now()
from (
	values
		('financial-close:period', '财务结账期间', '/gl/financial-close', 882),
		('financial-close:profit-loss', '期末损益结转', '/gl/profit-loss-carryforward', 883),
		('financial-close:bank-account', '银行账户', '/gl/bank-accounts', 884),
		('financial-close:bank-reconciliation', '银行对账', '/gl/bank-reconciliation', 885),
		('financial-close:tax-profile', '税务基础设置', '/gl/tax-settings', 886),
		('financial-close:tax-summary', '税务期间汇总', '/gl/tax-summary', 887),
		('financial-close:tax-payment', '税款缴纳记录', '/gl/tax-payments', 888)
) as seed(code, name, route_path, sort_order)
join sys_permission parent on parent.code = 'financial-close'
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.api_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('financial-close:period:view', '查看财务结账期间', 'financial-close:period', '/gl/financial-close', 'GET', '/api/admin/financial-closes/**', 889),
		('financial-close:period:check', '执行财务结账检查', 'financial-close:period', '/gl/financial-close', 'POST', '/api/admin/financial-closes/periods/{id}/checks', 890),
		('financial-close:period:close', '关闭财务期间', 'financial-close:period', '/gl/financial-close', 'POST', '/api/admin/financial-closes/check-runs/{id}/close', 891),
		('financial-close:period:reopen', '申请与审批财务反结账', 'financial-close:period', '/gl/financial-close', 'POST', '/api/admin/financial-closes/close-runs/{id}/reopen-requests', 892),
		('financial-close:profit-loss:view', '查看期末损益结转', 'financial-close:profit-loss', '/gl/profit-loss-carryforward', 'GET', '/api/admin/financial-closes/periods/{id}/profit-loss-transfers/**', 893),
		('financial-close:profit-loss:generate', '生成期末损益结转凭证草稿', 'financial-close:profit-loss', '/gl/profit-loss-carryforward', 'POST', '/api/admin/financial-closes/periods/{id}/profit-loss-transfers/**', 894),
		('financial-close:bank-account:view', '查看银行账户', 'financial-close:bank-account', '/gl/bank-accounts', 'GET', '/api/admin/bank-accounts/**', 895),
		('financial-close:bank-account:manage', '维护银行账户', 'financial-close:bank-account', '/gl/bank-accounts', null, '/api/admin/bank-accounts/**', 896),
		('financial-close:bank-reconciliation:view', '查看银行对账', 'financial-close:bank-reconciliation', '/gl/bank-reconciliation', 'GET', '/api/admin/bank-reconciliations/**', 897),
		('financial-close:bank-reconciliation:import', '导入银行流水', 'financial-close:bank-reconciliation', '/gl/bank-reconciliation', 'POST', '/api/admin/bank-statements/**,/api/admin/bank-statement-lines/**', 898),
		('financial-close:bank-reconciliation:match', '执行银行对账匹配', 'financial-close:bank-reconciliation', '/gl/bank-reconciliation', 'POST', '/api/admin/bank-reconciliations/**', 899),
		('financial-close:bank-reconciliation:confirm', '确认银行对账', 'financial-close:bank-reconciliation', '/gl/bank-reconciliation', 'POST', '/api/admin/bank-reconciliations/{id}/confirm', 900),
		('financial-close:bank-reconciliation:reopen', '重开银行对账', 'financial-close:bank-reconciliation', '/gl/bank-reconciliation', 'POST', '/api/admin/bank-reconciliations/{id}/reopen', 901),
		('financial-close:tax-profile:view', '查看税务基础设置', 'financial-close:tax-profile', '/gl/tax-settings', 'GET', '/api/admin/tax-profiles/**', 902),
		('financial-close:tax-profile:manage', '维护税务基础设置', 'financial-close:tax-profile', '/gl/tax-settings', null, '/api/admin/tax-profiles/**', 903),
		('financial-close:tax-summary:view', '查看税务汇总', 'financial-close:tax-summary', '/gl/tax-summary', 'GET', '/api/admin/tax-summaries/**', 904),
		('financial-close:tax-summary:calculate', '计算税务汇总', 'financial-close:tax-summary', '/gl/tax-summary', 'POST', '/api/admin/tax-summaries/**', 905),
		('financial-close:tax-summary:confirm', '确认税务汇总', 'financial-close:tax-summary', '/gl/tax-summary', 'POST', '/api/admin/tax-summaries/{id}/confirm', 906),
		('financial-close:tax-summary:generate-voucher', '生成税务凭证草稿', 'financial-close:tax-summary', '/gl/tax-summary', 'POST', '/api/admin/tax-summaries/{id}/voucher-drafts', 907),
		('financial-close:tax-payment:view', '查看税款缴纳记录', 'financial-close:tax-payment', '/gl/tax-payments', 'GET', '/api/admin/tax-payments/**', 908),
		('financial-close:tax-payment:manage', '维护税款缴纳记录', 'financial-close:tax-payment', '/gl/tax-payments', null, '/api/admin/tax-payments/**', 909),
		('financial-close:amount:view', '查看财务结账金额', 'financial-close', '/gl/financial-close', null, null, 910),
		('financial-close:source:view', '查看财务结账来源', 'financial-close', '/gl/financial-close', null, null, 911),
		('financial-close:bank-sensitive:view', '查看银行敏感字段', 'financial-close:bank-account', '/gl/bank-accounts', null, null, 912)
) as seed(code, name, parent_code, route_path, api_method, api_path, sort_order)
join sys_permission parent on parent.code = seed.parent_code
on conflict (code) do update
set name = excluded.name,
    parent_id = excluded.parent_id,
    route_path = excluded.route_path,
    api_method = excluded.api_method,
    api_path = excluded.api_path,
    sort_order = excluded.sort_order,
    updated_by = 'system',
    updated_at = now();

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code in (
	'financial-close:period:view',
	'financial-close:period:check',
	'financial-close:period:close',
	'financial-close:period:reopen',
	'financial-close:profit-loss:view',
	'financial-close:profit-loss:generate',
	'financial-close:bank-account:view',
	'financial-close:bank-account:manage',
	'financial-close:bank-reconciliation:view',
	'financial-close:bank-reconciliation:import',
	'financial-close:bank-reconciliation:match',
	'financial-close:bank-reconciliation:confirm',
	'financial-close:bank-reconciliation:reopen',
	'financial-close:tax-profile:view',
	'financial-close:tax-profile:manage',
	'financial-close:tax-summary:view',
	'financial-close:tax-summary:calculate',
	'financial-close:tax-summary:confirm',
	'financial-close:tax-summary:generate-voucher',
	'financial-close:tax-payment:view',
	'financial-close:tax-payment:manage',
	'financial-close:amount:view',
	'financial-close:source:view',
	'financial-close:bank-sensitive:view'
)
where r.code = 'SYSTEM_ADMIN'
on conflict (role_id, permission_id) do nothing;

insert into fin_tax_rate_rule (
	tax_type, rate_code, rate_value, effective_from, status, created_by, created_at, updated_by, updated_at
)
values
	('VAT', 'VAT_13', 0.1300, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('VAT', 'VAT_9', 0.0900, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('VAT', 'VAT_6', 0.0600, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('VAT', 'VAT_0', 0.0000, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('VAT', 'SIMPLIFIED_3', 0.0300, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('SURCHARGE', 'URBAN_7', 0.0700, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('SURCHARGE', 'URBAN_5', 0.0500, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('SURCHARGE', 'URBAN_1', 0.0100, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now()),
	('INCOME_TAX', 'INCOME_25', 0.2500, date '2026-01-01', 'ENABLED', 'system', now(), 'system', now())
on conflict (tax_type, rate_code, effective_from) do nothing;

insert into fin_tax_invoice_type (
	code, name, direction, deductible, status, created_by, created_at, updated_by, updated_at
)
values
	('E_DIGITAL_SPECIAL', '数电专票', 'OUTPUT', true, 'ENABLED', 'system', now(), 'system', now()),
	('E_DIGITAL_NORMAL', '数电普票', 'OUTPUT', false, 'ENABLED', 'system', now(), 'system', now()),
	('PAPER_SPECIAL', '纸质专票', 'OUTPUT', true, 'ENABLED', 'system', now(), 'system', now()),
	('PAPER_NORMAL', '纸质普票', 'OUTPUT', false, 'ENABLED', 'system', now(), 'system', now())
on conflict (code) do nothing;

do $$
declare
	v_ledger_id bigint;
	v_tax_parent_id bigint;
begin
	select id into v_ledger_id from gl_ledger where code = 'MAIN';
	select id into v_tax_parent_id from gl_account where ledger_id = v_ledger_id and code = '2221';
	if v_ledger_id is null or v_tax_parent_id is null then
		raise exception 'V34 科目种子要求 MAIN 账簿和 2221 应交税费已存在';
	end if;

	update gl_account
	set is_leaf = false,
	    postable = false,
	    updated_by = 'system',
	    updated_at = now()
	where id = v_tax_parent_id;

	if exists (
		select 1 from gl_account
		where ledger_id = v_ledger_id
		and code in ('4103', '6403', '6801')
		and (
			(code = '4103' and (category <> 'EQUITY' or balance_direction <> 'CREDIT' or postable is not true))
			or (code in ('6403', '6801') and (category <> 'PROFIT_LOSS' or balance_direction <> 'DEBIT' or postable is not true))
		)
	) then
		raise exception 'V34 科目种子发现既有科目属性冲突';
	end if;

	if exists (
		select 1 from gl_account
		where ledger_id = v_ledger_id
		and code in ('2221.03', '2221.04', '2221.05', '2221.06')
		and (parent_id <> v_tax_parent_id or category <> 'LIABILITY' or balance_direction <> 'CREDIT' or postable is not true)
	) then
		raise exception 'V34 税费明细科目属性冲突';
	end if;

	insert into gl_account (
		ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable, enabled,
		created_by, created_at, updated_by, updated_at
	)
	select v_ledger_id, null, '4103', '本年利润', 'EQUITY', 'CREDIT', 1, true, true, true,
	       'system', now(), 'system', now()
	where not exists (select 1 from gl_account where ledger_id = v_ledger_id and code = '4103');

	insert into gl_account (
		ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable, enabled,
		created_by, created_at, updated_by, updated_at
	)
	values
		(v_ledger_id, v_tax_parent_id, '2221.03', '未交增值税', 'LIABILITY', 'CREDIT', 2, true, true, true, 'system', now(), 'system', now()),
		(v_ledger_id, v_tax_parent_id, '2221.04', '应交城市维护建设税', 'LIABILITY', 'CREDIT', 2, true, true, true, 'system', now(), 'system', now()),
		(v_ledger_id, v_tax_parent_id, '2221.05', '应交教育费附加', 'LIABILITY', 'CREDIT', 2, true, true, true, 'system', now(), 'system', now()),
		(v_ledger_id, v_tax_parent_id, '2221.06', '应交企业所得税', 'LIABILITY', 'CREDIT', 2, true, true, true, 'system', now(), 'system', now())
	on conflict (ledger_id, code) do nothing;

	insert into gl_account (
		ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable, enabled,
		created_by, created_at, updated_by, updated_at
	)
	values
		(v_ledger_id, null, '6403', '税金及附加', 'PROFIT_LOSS', 'DEBIT', 1, true, true, true, 'system', now(), 'system', now()),
		(v_ledger_id, null, '6801', '所得税费用', 'PROFIT_LOSS', 'DEBIT', 1, true, true, true, 'system', now(), 'system', now())
	on conflict (ledger_id, code) do nothing;
end $$;
