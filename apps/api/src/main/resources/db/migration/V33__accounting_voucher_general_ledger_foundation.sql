create table gl_ledger (
	id bigserial primary key,
	code varchar(32) not null,
	name varchar(120) not null,
	currency varchar(8) not null,
	initialized boolean not null default false,
	start_period_id bigint,
	start_year_month varchar(7),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_gl_ledger_code unique (code),
	constraint ck_gl_ledger_code check (code = 'MAIN'),
	constraint ck_gl_ledger_currency check (currency = 'CNY')
);

create table gl_accounting_period (
	id bigserial primary key,
	ledger_id bigint not null,
	period_code varchar(7) not null,
	start_date date not null,
	end_date date not null,
	status varchar(32) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_gl_accounting_period_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint uk_gl_accounting_period_code unique (ledger_id, period_code),
	constraint ck_gl_accounting_period_status check (status in ('OPEN', 'CLOSED')),
	constraint ck_gl_accounting_period_range check (start_date <= end_date),
	constraint ex_gl_accounting_period_no_overlap exclude using gist (daterange(start_date, end_date, '[]') with &&)
);

alter table gl_ledger
	add constraint fk_gl_ledger_start_period foreign key (start_period_id) references gl_accounting_period (id);

create table gl_account (
	id bigserial primary key,
	ledger_id bigint not null,
	parent_id bigint,
	code varchar(64) not null,
	name varchar(120) not null,
	category varchar(32) not null,
	balance_direction varchar(16) not null,
	level_no integer not null,
	is_leaf boolean not null default true,
	postable boolean not null,
	enabled boolean not null default true,
	template_source varchar(32) not null default 'MANUFACTURING_MINIMUM',
	description varchar(500),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_gl_account_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint fk_gl_account_parent foreign key (parent_id) references gl_account (id),
	constraint uk_gl_account_code unique (ledger_id, code),
	constraint ck_gl_account_category check (category in ('ASSET', 'LIABILITY', 'COMMON', 'EQUITY', 'COST', 'PROFIT_LOSS')),
	constraint ck_gl_account_direction check (balance_direction in ('DEBIT', 'CREDIT')),
	constraint ck_gl_account_level check (level_no > 0)
);

create index idx_gl_account_parent on gl_account (parent_id, code);
create index idx_gl_account_enabled on gl_account (ledger_id, enabled, code);

create table gl_aux_dimension (
	id bigserial primary key,
	code varchar(32) not null,
	name varchar(120) not null,
	dimension_type varchar(16) not null,
	object_source varchar(64),
	system_defined boolean not null default true,
	enabled boolean not null default true,
	sort_order integer not null default 0,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint uk_gl_aux_dimension_code unique (code),
	constraint ck_gl_aux_dimension_type check (dimension_type in ('SYSTEM', 'CUSTOM'))
);

create table gl_aux_item (
	id bigserial primary key,
	dimension_id bigint not null,
	code varchar(64) not null,
	name varchar(120) not null,
	enabled boolean not null default true,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	version bigint not null default 0,
	constraint fk_gl_aux_item_dimension foreign key (dimension_id) references gl_aux_dimension (id),
	constraint uk_gl_aux_item_code unique (dimension_id, code)
);

create table gl_account_aux_requirement (
	id bigserial primary key,
	account_id bigint not null,
	dimension_id bigint not null,
	requirement varchar(16) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	constraint fk_gl_account_aux_account foreign key (account_id) references gl_account (id),
	constraint fk_gl_account_aux_dimension foreign key (dimension_id) references gl_aux_dimension (id),
	constraint uk_gl_account_aux_requirement unique (account_id, dimension_id),
	constraint ck_gl_account_aux_requirement check (requirement in ('REQUIRED', 'OPTIONAL'))
);

create table gl_posting_rule (
	id bigserial primary key,
	source_type varchar(64) not null,
	source_variant varchar(64) not null,
	rule_version integer not null,
	status varchar(32) not null,
	name varchar(120) not null,
	description varchar(500),
	effective_from date,
	effective_to date,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	activated_by varchar(64),
	activated_at timestamptz,
	version bigint not null default 0,
	constraint uk_gl_posting_rule_version unique (source_type, source_variant, rule_version),
	constraint ck_gl_posting_rule_status check (status in ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'DISABLED'))
);

create unique index uk_gl_posting_rule_active
	on gl_posting_rule (source_type, source_variant)
	where status = 'ACTIVE';

create table gl_posting_rule_line (
	id bigserial primary key,
	rule_id bigint not null,
	line_no integer not null,
	normalized_fact_code varchar(64) not null,
	direction varchar(16) not null,
	account_id bigint not null,
	summary_template varchar(255) not null,
	created_at timestamptz not null,
	constraint fk_gl_posting_rule_line_rule foreign key (rule_id) references gl_posting_rule (id) on delete cascade,
	constraint fk_gl_posting_rule_line_account foreign key (account_id) references gl_account (id),
	constraint uk_gl_posting_rule_line_fact unique (rule_id, normalized_fact_code, direction),
	constraint ck_gl_posting_rule_line_direction check (direction in ('DEBIT', 'CREDIT'))
);

create table gl_posting_rule_line_aux_map (
	id bigserial primary key,
	rule_line_id bigint not null,
	dimension_id bigint not null,
	mapping_type varchar(32) not null,
	fixed_aux_item_id bigint,
	created_at timestamptz not null,
	constraint fk_gl_posting_rule_aux_line foreign key (rule_line_id) references gl_posting_rule_line (id) on delete cascade,
	constraint fk_gl_posting_rule_aux_dimension foreign key (dimension_id) references gl_aux_dimension (id),
	constraint fk_gl_posting_rule_aux_item foreign key (fixed_aux_item_id) references gl_aux_item (id),
	constraint uk_gl_posting_rule_aux_map unique (rule_line_id, dimension_id),
	constraint ck_gl_posting_rule_aux_source check (
		mapping_type in ('SOURCE_CUSTOMER', 'SOURCE_SUPPLIER', 'SOURCE_PROJECT', 'FIXED_CUSTOM_ITEM')
	)
);

create table gl_voucher (
	id bigserial primary key,
	ledger_id bigint not null,
	accounting_period_id bigint not null,
	draft_no varchar(64) not null,
	voucher_type varchar(16) not null,
	voucher_date date not null,
	status varchar(32) not null,
	summary varchar(255) not null,
	source_type varchar(64) not null,
	source_id bigint,
	source_no varchar(120),
	source_fingerprint varchar(64),
	source_version bigint,
	source_original_type varchar(64),
	source_original_id bigint,
	source_original_no varchar(120),
	source_original_version bigint,
	source_original_fingerprint varchar(64),
	source_payload jsonb not null default '{}'::jsonb,
	source_claim_id bigint,
	rule_id bigint,
	rule_version integer,
	currency varchar(8) not null default 'CNY',
	debit_total numeric(18, 2) not null default 0,
	credit_total numeric(18, 2) not null default 0,
	voucher_word varchar(8) not null default '记',
	voucher_number integer,
	voucher_no varchar(64),
	approval_instance_id bigint,
	reversal_original_voucher_id bigint,
	reversal_reason varchar(500),
	idempotency_key varchar(120),
	request_fingerprint varchar(64),
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	submitted_by varchar(64),
	submitted_at timestamptz,
	posted_by varchar(64),
	posted_at timestamptz,
	cancelled_by varchar(64),
	cancelled_at timestamptz,
	version bigint not null default 0,
	constraint fk_gl_voucher_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint fk_gl_voucher_period foreign key (accounting_period_id) references gl_accounting_period (id),
	constraint fk_gl_voucher_rule foreign key (rule_id) references gl_posting_rule (id),
	constraint fk_gl_voucher_approval foreign key (approval_instance_id) references platform_approval_instance (id),
	constraint fk_gl_voucher_reversal_original foreign key (reversal_original_voucher_id) references gl_voucher (id),
	constraint uk_gl_voucher_draft_no unique (draft_no),
	constraint ck_gl_voucher_type check (voucher_type in ('GENERAL', 'OPENING')),
	constraint ck_gl_voucher_source_type check (source_type in ('MANUAL', 'FIN_VOUCHER_DRAFT', 'REVERSAL')),
	constraint ck_gl_voucher_status check (status in ('DRAFT', 'SUBMITTED', 'POSTED', 'CANCELLED')),
	constraint ck_gl_voucher_currency check (currency = 'CNY'),
	constraint ck_gl_voucher_totals check (debit_total >= 0 and credit_total >= 0),
	constraint ck_gl_voucher_number_state check (
		(status = 'POSTED' and voucher_number is not null and voucher_no is not null)
		or (status <> 'POSTED' and voucher_number is null and voucher_no is null)
	)
);

create unique index uk_gl_voucher_formal_no
	on gl_voucher (ledger_id, accounting_period_id, voucher_word, voucher_number)
	where status = 'POSTED';
create unique index uk_gl_voucher_create_idempotency
	on gl_voucher (created_by, idempotency_key)
	where idempotency_key is not null and source_type = 'MANUAL';
create index idx_gl_voucher_status_date on gl_voucher (status, voucher_date desc, id desc);
create index idx_gl_voucher_source on gl_voucher (source_type, source_id);

create table gl_voucher_line (
	id bigserial primary key,
	voucher_id bigint not null,
	line_no integer not null,
	summary varchar(255) not null,
	account_id bigint not null,
	account_code varchar(64) not null,
	account_name varchar(120) not null,
	account_category varchar(32) not null,
	account_balance_direction varchar(16) not null,
	debit_amount numeric(18, 2) not null default 0,
	credit_amount numeric(18, 2) not null default 0,
	normalized_fact_code varchar(64),
	source_type varchar(64),
	source_id bigint,
	source_no varchar(120),
	source_route jsonb,
	created_at timestamptz not null,
	constraint fk_gl_voucher_line_voucher foreign key (voucher_id) references gl_voucher (id) on delete cascade,
	constraint fk_gl_voucher_line_account foreign key (account_id) references gl_account (id),
	constraint uk_gl_voucher_line_no unique (voucher_id, line_no),
	constraint ck_gl_voucher_line_amount check (
		(debit_amount > 0 and credit_amount = 0) or (credit_amount > 0 and debit_amount = 0)
	)
);

create index idx_gl_voucher_line_account on gl_voucher_line (account_id, voucher_id);

create table gl_voucher_line_auxiliary (
	id bigserial primary key,
	voucher_line_id bigint not null,
	dimension_id bigint not null,
	dimension_code varchar(32) not null,
	dimension_name varchar(120) not null,
	object_type varchar(32) not null,
	object_id bigint,
	object_code varchar(120),
	object_name varchar(255),
	aux_item_id bigint,
	created_at timestamptz not null,
	constraint fk_gl_voucher_aux_line foreign key (voucher_line_id) references gl_voucher_line (id) on delete cascade,
	constraint fk_gl_voucher_aux_dimension foreign key (dimension_id) references gl_aux_dimension (id),
	constraint fk_gl_voucher_aux_item foreign key (aux_item_id) references gl_aux_item (id),
	constraint uk_gl_voucher_aux_dimension unique (voucher_line_id, dimension_id)
);

create table gl_voucher_source_claim (
	id bigserial primary key,
	source_type varchar(64) not null,
	source_id bigint not null,
	source_no varchar(120),
	voucher_id bigint,
	status varchar(32) not null,
	source_version bigint not null,
	source_fingerprint varchar(64) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_by varchar(64) not null,
	updated_at timestamptz not null,
	constraint fk_gl_source_claim_voucher foreign key (voucher_id) references gl_voucher (id),
	constraint ck_gl_source_claim_status check (status in ('RESERVED', 'POSTED', 'RELEASED'))
);

create unique index uk_gl_source_claim_active
	on gl_voucher_source_claim (source_type, source_id)
	where status in ('RESERVED', 'POSTED');

create table gl_voucher_number_sequence (
	id bigserial primary key,
	ledger_id bigint not null,
	period_id bigint not null,
	voucher_word varchar(8) not null,
	last_number integer not null default 0,
	updated_at timestamptz not null,
	constraint fk_gl_number_sequence_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint fk_gl_number_sequence_period foreign key (period_id) references gl_accounting_period (id),
	constraint uk_gl_number_sequence unique (ledger_id, period_id, voucher_word)
);

create table gl_ledger_entry (
	id bigserial primary key,
	ledger_id bigint not null,
	period_id bigint not null,
	voucher_id bigint not null,
	voucher_line_id bigint not null,
	voucher_date date not null,
	voucher_no varchar(64) not null,
	voucher_word varchar(8) not null,
	voucher_number integer not null,
	line_no integer not null,
	summary varchar(255) not null,
	account_id bigint not null,
	account_code varchar(64) not null,
	account_name varchar(120) not null,
	balance_direction varchar(16) not null,
	voucher_type varchar(16) not null,
	debit_amount numeric(18, 2) not null default 0,
	credit_amount numeric(18, 2) not null default 0,
	auxiliary_snapshot jsonb not null default '[]'::jsonb,
	source_type varchar(64),
	source_id bigint,
	source_no varchar(120),
	source_route jsonb,
	posted_by varchar(64) not null,
	posted_at timestamptz not null,
	created_at timestamptz not null,
	constraint fk_gl_ledger_entry_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint fk_gl_ledger_entry_period foreign key (period_id) references gl_accounting_period (id),
	constraint fk_gl_ledger_entry_voucher foreign key (voucher_id) references gl_voucher (id),
	constraint fk_gl_ledger_entry_line foreign key (voucher_line_id) references gl_voucher_line (id),
	constraint fk_gl_ledger_entry_account foreign key (account_id) references gl_account (id),
	constraint uk_gl_ledger_entry_line unique (voucher_line_id)
);

create index idx_gl_ledger_entry_period_account on gl_ledger_entry (period_id, account_id, voucher_date, id);
create index idx_gl_ledger_entry_voucher on gl_ledger_entry (voucher_id, line_no);

create table gl_account_period_total (
	id bigserial primary key,
	ledger_id bigint not null,
	period_id bigint not null,
	account_id bigint not null,
	opening_debit numeric(18, 2) not null default 0,
	opening_credit numeric(18, 2) not null default 0,
	period_debit numeric(18, 2) not null default 0,
	period_credit numeric(18, 2) not null default 0,
	ending_debit numeric(18, 2) not null default 0,
	ending_credit numeric(18, 2) not null default 0,
	updated_at timestamptz not null,
	constraint fk_gl_account_total_ledger foreign key (ledger_id) references gl_ledger (id),
	constraint fk_gl_account_total_period foreign key (period_id) references gl_accounting_period (id),
	constraint fk_gl_account_total_account foreign key (account_id) references gl_account (id),
	constraint uk_gl_account_period_total unique (ledger_id, period_id, account_id)
);

create table gl_voucher_reversal_link (
	id bigserial primary key,
	original_voucher_id bigint not null,
	reversal_voucher_id bigint not null,
	status varchar(32) not null,
	reason varchar(500) not null,
	created_by varchar(64) not null,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint fk_gl_reversal_original foreign key (original_voucher_id) references gl_voucher (id),
	constraint fk_gl_reversal_voucher foreign key (reversal_voucher_id) references gl_voucher (id),
	constraint uk_gl_reversal_voucher unique (reversal_voucher_id),
	constraint ck_gl_reversal_status check (status in ('DRAFT', 'POSTED', 'CANCELLED'))
);

create unique index uk_gl_reversal_active
	on gl_voucher_reversal_link (original_voucher_id)
	where status in ('DRAFT', 'POSTED');

create table gl_action_idempotency (
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
	result_version bigint not null,
	result_snapshot jsonb,
	created_at timestamptz not null default now()
);

create unique index uk_gl_action_idempotency
	on gl_action_idempotency (operator_user_id, action, resource_type, coalesce(resource_id, 0), idempotency_key);

create table gl_audit_event (
	id bigserial primary key,
	operator_user_id bigint,
	operator_username varchar(64) not null,
	action varchar(64) not null,
	resource_type varchar(64) not null,
	resource_id varchar(120),
	resource_no varchar(120),
	result varchar(32) not null,
	error_code varchar(80),
	source_type varchar(64),
	source_id bigint,
	before_summary jsonb,
	after_summary jsonb,
	trace_id varchar(120),
	created_at timestamptz not null default now()
);

create index idx_gl_audit_resource on gl_audit_event (resource_type, resource_id, created_at desc);

create or replace function forbid_gl_posted_voucher_mutation()
returns trigger
language plpgsql
as $$
begin
	if tg_op in ('UPDATE', 'DELETE') and old.status = 'POSTED' then
		raise exception '已记账凭证不可更新或删除';
	end if;
	return new;
end;
$$;

create trigger tr_gl_voucher_posted_immutable
	before update or delete on gl_voucher
	for each row execute function forbid_gl_posted_voucher_mutation();

create or replace function forbid_gl_posted_voucher_line_mutation()
returns trigger
language plpgsql
as $$
declare
	parent_status varchar(32);
	parent_id bigint;
begin
	parent_id := coalesce(new.voucher_id, old.voucher_id);
	select status into parent_status from gl_voucher where id = parent_id;
	if parent_status = 'POSTED' then
		raise exception '已记账凭证分录不可插入、更新或删除';
	end if;
	return coalesce(new, old);
end;
$$;

create trigger tr_gl_voucher_line_posted_immutable
	before insert or update or delete on gl_voucher_line
	for each row execute function forbid_gl_posted_voucher_line_mutation();

create or replace function forbid_gl_posted_voucher_aux_mutation()
returns trigger
language plpgsql
as $$
declare
	parent_status varchar(32);
	line_id bigint;
begin
	line_id := coalesce(new.voucher_line_id, old.voucher_line_id);
	select v.status into parent_status
	from gl_voucher_line l
	join gl_voucher v on v.id = l.voucher_id
	where l.id = line_id;
	if parent_status = 'POSTED' then
		raise exception '已记账凭证辅助核算不可插入、更新或删除';
	end if;
	return coalesce(new, old);
end;
$$;

create trigger tr_gl_voucher_aux_posted_immutable
	before insert or update or delete on gl_voucher_line_auxiliary
	for each row execute function forbid_gl_posted_voucher_aux_mutation();

create or replace function forbid_gl_ledger_entry_mutation()
returns trigger
language plpgsql
as $$
begin
	raise exception '总账分录不可更新或删除';
end;
$$;

create trigger tr_gl_ledger_entry_immutable
	before update or delete on gl_ledger_entry
	for each row execute function forbid_gl_ledger_entry_mutation();

insert into gl_ledger (code, name, currency, initialized, created_by, created_at, updated_by, updated_at)
values ('MAIN', '总账', 'CNY', false, 'system', now(), 'system', now());

with ledger as (
	select id from gl_ledger where code = 'MAIN'
),
seed(code, name, category, direction, parent_code, postable, level_no, sort_no) as (
	values
		('1001', '库存现金', 'ASSET', 'DEBIT', null, true, 1, 1),
		('1002', '银行存款', 'ASSET', 'DEBIT', null, true, 1, 2),
		('1122', '应收账款', 'ASSET', 'DEBIT', null, true, 1, 3),
		('1123', '预付账款', 'ASSET', 'DEBIT', null, true, 1, 4),
		('1401', '材料采购', 'ASSET', 'DEBIT', null, true, 1, 5),
		('1403', '原材料', 'ASSET', 'DEBIT', null, true, 1, 6),
		('1405', '库存商品', 'ASSET', 'DEBIT', null, true, 1, 7),
		('1408', '委托加工物资', 'ASSET', 'DEBIT', null, true, 1, 8),
		('1601', '固定资产', 'ASSET', 'DEBIT', null, true, 1, 9),
		('2202', '应付账款', 'LIABILITY', 'CREDIT', null, true, 1, 10),
		('2203', '预收账款', 'LIABILITY', 'CREDIT', null, true, 1, 11),
		('2221', '应交税费', 'LIABILITY', 'CREDIT', null, false, 1, 12),
		('4001', '实收资本', 'EQUITY', 'CREDIT', null, true, 1, 13),
		('5001', '生产成本', 'COST', 'DEBIT', null, true, 1, 14),
		('5101', '制造费用', 'COST', 'DEBIT', null, true, 1, 15),
		('6001', '主营业务收入', 'PROFIT_LOSS', 'CREDIT', null, true, 1, 16),
		('6051', '其他业务收入', 'PROFIT_LOSS', 'CREDIT', null, true, 1, 17),
		('6401', '主营业务成本', 'PROFIT_LOSS', 'DEBIT', null, true, 1, 18),
		('6601', '销售费用', 'PROFIT_LOSS', 'DEBIT', null, true, 1, 19),
		('6602', '管理费用', 'PROFIT_LOSS', 'DEBIT', null, true, 1, 20),
		('6603', '财务费用', 'PROFIT_LOSS', 'DEBIT', null, true, 1, 21),
		('6301', '营业外收入', 'PROFIT_LOSS', 'CREDIT', null, true, 1, 22),
		('6711', '营业外支出', 'PROFIT_LOSS', 'DEBIT', null, true, 1, 23)
)
insert into gl_account (
	ledger_id, code, name, category, balance_direction, parent_id, level_no, is_leaf, postable, enabled,
	created_by, created_at, updated_by, updated_at
)
select l.id, s.code, s.name, s.category, s.direction, null, s.level_no, s.code <> '2221', s.postable, true,
       'system', now(), 'system', now()
from seed s
cross join ledger l
order by s.sort_no;

with parent as (
	select id, ledger_id from gl_account where code = '2221'
)
insert into gl_account (
	ledger_id, parent_id, code, name, category, balance_direction, level_no, is_leaf, postable, enabled,
	created_by, created_at, updated_by, updated_at
)
select ledger_id, id, '2221.01', '应交增值税-进项税额', 'LIABILITY', 'CREDIT', 2, true, true, true,
       'system', now(), 'system', now()
from parent
union all
select ledger_id, id, '2221.02', '应交增值税-销项税额', 'LIABILITY', 'CREDIT', 2, true, true, true,
       'system', now(), 'system', now()
from parent;

insert into gl_aux_dimension (
	code, name, dimension_type, object_source, system_defined, enabled, sort_order, created_by, created_at, updated_by,
	updated_at
)
values
	('CUSTOMER', '客户', 'SYSTEM', 'mst_customer', true, true, 10, 'system', now(), 'system', now()),
	('SUPPLIER', '供应商', 'SYSTEM', 'mst_supplier', true, true, 20, 'system', now(), 'system', now()),
	('PROJECT', '项目', 'SYSTEM', 'sal_project', true, true, 30, 'system', now(), 'system', now());

insert into gl_account_aux_requirement (account_id, dimension_id, requirement, created_by, created_at)
select a.id, d.id, 'REQUIRED', 'system', now()
from gl_account a
join gl_aux_dimension d on (
	(a.code in ('1122', '2203') and d.code = 'CUSTOMER')
	or (a.code in ('1123', '2202') and d.code = 'SUPPLIER')
);

insert into gl_account_aux_requirement (account_id, dimension_id, requirement, created_by, created_at)
select a.id, d.id, 'OPTIONAL', 'system', now()
from gl_account a
join gl_aux_dimension d on d.code = 'PROJECT'
where a.code in ('1122', '1123', '2202', '2203', '5001', '5101', '6001', '6401', '6601', '6602', '6603');

insert into gl_posting_rule (
	source_type, source_variant, rule_version, status, name, description, effective_from, effective_to, created_by,
	created_at, updated_by, updated_at, activated_by, activated_at
)
values
	('SALES_INVOICE', 'DEFAULT', 1, 'ACTIVE', '销售发票默认规则', '应收、收入和销项税', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('PURCHASE_INVOICE', 'DEFAULT', 1, 'ACTIVE', '采购发票默认规则', '采购/材料、进项税和应付', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('EXPENSE', 'DEFAULT', 1, 'ACTIVE', '费用默认规则', '费用、进项税和应付', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('RECEIPT', 'DEFAULT', 1, 'ACTIVE', '收款默认规则', '银行存款和预收', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('PAYMENT', 'DEFAULT', 1, 'ACTIVE', '付款默认规则', '预付和银行存款', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('SETTLEMENT_ALLOCATION', 'RECEIVABLE', 1, 'ACTIVE', '应收核销默认规则', '预收冲抵应收', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now()),
	('SETTLEMENT_ALLOCATION', 'PAYABLE', 1, 'ACTIVE', '应付核销默认规则', '应付冲抵预付', date '2026-01-01', null, 'system', now(), 'system', now(), 'system', now());

with rule_line(source_type, source_variant, line_no, normalized_fact_code, direction, account_code, summary_template) as (
	values
		('SALES_INVOICE', 'DEFAULT', 1, 'SALES_RECEIVABLE', 'DEBIT', '1122', '销售发票应收'),
		('SALES_INVOICE', 'DEFAULT', 2, 'SALES_REVENUE', 'CREDIT', '6001', '销售收入'),
		('SALES_INVOICE', 'DEFAULT', 3, 'OUTPUT_VAT', 'CREDIT', '2221.02', '销项税额'),
		('PURCHASE_INVOICE', 'DEFAULT', 1, 'PURCHASE_CLEARING', 'DEBIT', '1401', '采购未税金额'),
		('PURCHASE_INVOICE', 'DEFAULT', 2, 'INPUT_VAT', 'DEBIT', '2221.01', '进项税额'),
		('PURCHASE_INVOICE', 'DEFAULT', 3, 'PURCHASE_PAYABLE', 'CREDIT', '2202', '采购应付'),
		('EXPENSE', 'DEFAULT', 1, 'EXPENSE', 'DEBIT', '6602', '费用未税金额'),
		('EXPENSE', 'DEFAULT', 2, 'INPUT_VAT', 'DEBIT', '2221.01', '进项税额'),
		('EXPENSE', 'DEFAULT', 3, 'EXPENSE_PAYABLE', 'CREDIT', '2202', '费用应付'),
		('RECEIPT', 'DEFAULT', 1, 'BANK_RECEIPT', 'DEBIT', '1002', '收款入账'),
		('RECEIPT', 'DEFAULT', 2, 'ADVANCE_RECEIPT', 'CREDIT', '2203', '预收款'),
		('PAYMENT', 'DEFAULT', 1, 'PREPAYMENT', 'DEBIT', '1123', '预付款'),
		('PAYMENT', 'DEFAULT', 2, 'BANK_PAYMENT', 'CREDIT', '1002', '付款出账'),
		('SETTLEMENT_ALLOCATION', 'RECEIVABLE', 1, 'ADVANCE_RECEIPT_CLEAR', 'DEBIT', '2203', '预收冲抵'),
		('SETTLEMENT_ALLOCATION', 'RECEIVABLE', 2, 'RECEIVABLE_CLEAR', 'CREDIT', '1122', '应收结清'),
		('SETTLEMENT_ALLOCATION', 'PAYABLE', 1, 'PAYABLE_CLEAR', 'DEBIT', '2202', '应付结清'),
		('SETTLEMENT_ALLOCATION', 'PAYABLE', 2, 'PREPAYMENT_CLEAR', 'CREDIT', '1123', '预付冲抵')
)
insert into gl_posting_rule_line (rule_id, line_no, normalized_fact_code, direction, account_id, summary_template,
	created_at)
select r.id, rl.line_no, rl.normalized_fact_code, rl.direction, a.id, rl.summary_template, now()
from rule_line rl
join gl_posting_rule r on r.source_type = rl.source_type and r.source_variant = rl.source_variant
join gl_account a on a.code = rl.account_code;

with aux_map(source_type, source_variant, normalized_fact_code, dimension_code, mapping_type) as (
	values
		('SALES_INVOICE', 'DEFAULT', 'SALES_RECEIVABLE', 'CUSTOMER', 'SOURCE_CUSTOMER'),
		('PURCHASE_INVOICE', 'DEFAULT', 'PURCHASE_PAYABLE', 'SUPPLIER', 'SOURCE_SUPPLIER'),
		('EXPENSE', 'DEFAULT', 'EXPENSE_PAYABLE', 'SUPPLIER', 'SOURCE_SUPPLIER'),
		('RECEIPT', 'DEFAULT', 'ADVANCE_RECEIPT', 'CUSTOMER', 'SOURCE_CUSTOMER'),
		('PAYMENT', 'DEFAULT', 'PREPAYMENT', 'SUPPLIER', 'SOURCE_SUPPLIER'),
		('SETTLEMENT_ALLOCATION', 'RECEIVABLE', 'ADVANCE_RECEIPT_CLEAR', 'CUSTOMER', 'SOURCE_CUSTOMER'),
		('SETTLEMENT_ALLOCATION', 'RECEIVABLE', 'RECEIVABLE_CLEAR', 'CUSTOMER', 'SOURCE_CUSTOMER'),
		('SETTLEMENT_ALLOCATION', 'PAYABLE', 'PAYABLE_CLEAR', 'SUPPLIER', 'SOURCE_SUPPLIER'),
		('SETTLEMENT_ALLOCATION', 'PAYABLE', 'PREPAYMENT_CLEAR', 'SUPPLIER', 'SOURCE_SUPPLIER')
)
insert into gl_posting_rule_line_aux_map (rule_line_id, dimension_id, mapping_type, created_at)
select rl.id, d.id, am.mapping_type, now()
from aux_map am
join gl_posting_rule r on r.source_type = am.source_type and r.source_variant = am.source_variant
join gl_posting_rule_line rl on rl.rule_id = r.id and rl.normalized_fact_code = am.normalized_fact_code
join gl_aux_dimension d on d.code = am.dimension_code;

insert into platform_approval_definition (
	scene_code, name, business_object_type, action_code, definition_version, status
)
values ('GL_VOUCHER_POST', '总账凭证通过并记账', 'GL_VOUCHER', 'POST', 1, 'ENABLED');

insert into platform_approval_definition_step (
	definition_id, step_no, name, candidate_permission_code
)
select id, 1, '通过并记账', 'gl:voucher:approve-post'
from platform_approval_definition
where scene_code = 'GL_VOUCHER_POST';

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
values ('gl', '会计核算', 'MENU', null, '/gl', null, null, 850, 'system', now(), 'system', now())
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, seed.type, parent.id, seed.route_path, seed.http_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('gl:account', '会计科目', 'MENU', '/gl/accounts', null, null, 851),
		('gl:auxiliary', '辅助核算', 'MENU', '/gl/auxiliaries', null, null, 852),
		('gl:period', '会计期间', 'MENU', '/gl/accounting-periods', null, null, 853),
		('gl:rule', '制证规则', 'MENU', '/gl/posting-rules', null, null, 854),
		('gl:voucher', '正式凭证', 'MENU', '/gl/vouchers', null, null, 855),
		('gl:ledger', '总账明细账', 'MENU', '/gl/ledgers/general', null, null, 856),
		('gl:balance', '余额与试算', 'MENU', '/gl/trial-balance', null, null, 857)
) as seed(code, name, type, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = 'gl'
on conflict (code) do nothing;

insert into sys_permission (code, name, type, parent_id, route_path, api_method, api_path, sort_order,
	created_by, created_at, updated_by, updated_at)
select seed.code, seed.name, 'ACTION', parent.id, seed.route_path, seed.http_method, seed.api_path,
       seed.sort_order, 'system', now(), 'system', now()
from (
	values
		('gl:account:view', '查看会计科目', 'gl:account', '/gl/accounts', 'GET', '/api/admin/gl/accounts/**', 858),
		('gl:account:create', '创建会计科目', 'gl:account', '/gl/accounts', 'POST', '/api/admin/gl/accounts', 859),
		('gl:account:update', '维护会计科目', 'gl:account', '/gl/accounts', 'PUT', '/api/admin/gl/accounts/{id}', 860),
		('gl:account:disable', '停用会计科目', 'gl:account', '/gl/accounts', 'POST', '/api/admin/gl/accounts/{id}/disable', 861),
		('gl:auxiliary:view', '查看辅助核算', 'gl:auxiliary', '/gl/auxiliaries', 'GET', '/api/admin/gl/aux-dimensions/**', 862),
		('gl:auxiliary:manage', '维护辅助核算', 'gl:auxiliary', '/gl/auxiliaries', null, '/api/admin/gl/aux-dimensions/**', 863),
		('gl:period:view', '查看会计期间', 'gl:period', '/gl/accounting-periods', 'GET', '/api/admin/gl/accounting-periods/**', 864),
		('gl:period:initialize', '启用总账', 'gl:period', '/gl/accounting-periods', 'POST', '/api/admin/gl/ledger/initialize', 865),
		('gl:period:create', '新增会计期间', 'gl:period', '/gl/accounting-periods', 'POST', '/api/admin/gl/accounting-periods', 866),
		('gl:rule:view', '查看制证规则', 'gl:rule', '/gl/posting-rules', 'GET', '/api/admin/gl/posting-rules/**', 867),
		('gl:rule:manage', '维护制证规则', 'gl:rule', '/gl/posting-rules', null, '/api/admin/gl/posting-rules/**', 868),
		('gl:voucher:view', '查看正式凭证', 'gl:voucher', '/gl/vouchers', 'GET', '/api/admin/gl/vouchers/**', 869),
		('gl:voucher:create', '创建正式凭证', 'gl:voucher', '/gl/vouchers', 'POST', '/api/admin/gl/vouchers', 870),
		('gl:voucher:update', '维护正式凭证草稿', 'gl:voucher', '/gl/vouchers', 'PUT', '/api/admin/gl/vouchers/{id}', 871),
		('gl:voucher:convert', '从往来草稿生成正式凭证', 'gl:voucher', '/gl/vouchers', 'POST', '/api/admin/gl/vouchers/from-finance-draft/{id}', 872),
		('gl:voucher:submit', '提交正式凭证审批', 'gl:voucher', '/gl/vouchers', 'POST', '/api/admin/gl/vouchers/{id}/submit', 873),
		('gl:voucher:cancel', '取消正式凭证草稿', 'gl:voucher', '/gl/vouchers', 'POST', '/api/admin/gl/vouchers/{id}/cancel', 874),
		('gl:voucher:reverse', '发起正式凭证冲销', 'gl:voucher', '/gl/vouchers', 'POST', '/api/admin/gl/vouchers/{id}/reversals', 875),
		('gl:voucher:approve-post', '通过并记账正式凭证', 'gl:voucher', '/platform/tasks', 'POST', '/api/admin/approval-tasks/{id}/approve', 876),
		('gl:ledger:view', '查看总账和明细账', 'gl:ledger', '/gl/ledgers/general', 'GET', '/api/admin/gl/ledgers/**', 877),
		('gl:balance:view', '查看科目余额和试算平衡', 'gl:balance', '/gl/trial-balance', 'GET', '/api/admin/gl/trial-balance', 878),
		('gl:amount:view', '查看总账金额', 'gl:balance', '/gl/trial-balance', null, null, 879),
		('gl:source:view', '查看总账来源', 'gl:voucher', '/gl/vouchers', null, null, 880)
) as seed(code, name, parent_code, route_path, http_method, api_path, sort_order)
join sys_permission parent on parent.code = seed.parent_code
on conflict (code) do nothing;

insert into sys_role_permission (role_id, permission_id, created_by, created_at)
select r.id, p.id, 'system', now()
from sys_role r
join sys_permission p on p.code like 'gl:%' or p.code = 'gl'
where r.code = 'SYSTEM_ADMIN'
and not exists (
	select 1 from sys_role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
);
