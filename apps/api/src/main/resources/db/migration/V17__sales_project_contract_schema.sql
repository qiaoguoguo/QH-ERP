create table sal_project (
	id bigserial primary key,
	project_no varchar(64) not null,
	name varchar(120) not null,
	customer_id bigint not null,
	owner_user_id bigint not null,
	planned_start_date date,
	planned_finish_date date,
	status varchar(32) not null,
	target_revenue numeric(18, 2) not null default 0,
	target_cost numeric(18, 2) not null default 0,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	activated_by varchar(64),
	activated_at timestamp with time zone,
	closed_by varchar(64),
	closed_at timestamp with time zone,
	close_reason varchar(200),
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	cancel_reason varchar(200),
	version bigint not null default 0,
	constraint uk_sal_project_no unique (project_no),
	constraint fk_sal_project_customer foreign key (customer_id) references mst_customer (id),
	constraint fk_sal_project_owner foreign key (owner_user_id) references sys_user (id),
	constraint ck_sal_project_status check (status in ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED')),
	constraint ck_sal_project_target_revenue_non_negative check (target_revenue >= 0),
	constraint ck_sal_project_target_cost_non_negative check (target_cost >= 0),
	constraint ck_sal_project_plan_range check (
		planned_start_date is null or planned_finish_date is null or planned_start_date <= planned_finish_date
	),
	constraint ck_sal_project_close_reason_length check (close_reason is null or length(close_reason) between 1 and 200),
	constraint ck_sal_project_cancel_reason_length check (cancel_reason is null or length(cancel_reason) between 1 and 200)
);

create table sal_project_contract (
	id bigserial primary key,
	contract_no varchar(64) not null,
	external_contract_no varchar(100),
	project_id bigint not null,
	contract_type varchar(32) not null,
	main_contract_id bigint,
	name varchar(120) not null,
	signed_date date not null,
	effective_start_date date,
	effective_end_date date,
	amount numeric(18, 2) not null,
	status varchar(32) not null,
	remark varchar(500),
	created_by varchar(64) not null,
	created_at timestamp with time zone not null,
	updated_by varchar(64) not null,
	updated_at timestamp with time zone not null,
	activated_by varchar(64),
	activated_at timestamp with time zone,
	closed_by varchar(64),
	closed_at timestamp with time zone,
	close_reason varchar(200),
	terminated_by varchar(64),
	terminated_at timestamp with time zone,
	terminate_reason varchar(200),
	cancelled_by varchar(64),
	cancelled_at timestamp with time zone,
	cancel_reason varchar(200),
	version bigint not null default 0,
	constraint uk_sal_project_contract_no unique (contract_no),
	constraint fk_sal_project_contract_project foreign key (project_id) references sal_project (id),
	constraint fk_sal_project_contract_main foreign key (main_contract_id) references sal_project_contract (id),
	constraint ck_sal_project_contract_type check (contract_type in ('MAIN', 'SUPPLEMENT')),
	constraint ck_sal_project_contract_status check (status in (
		'DRAFT',
		'EFFECTIVE',
		'CLOSED',
		'TERMINATED',
		'CANCELLED'
	)),
	constraint ck_sal_project_contract_main_ref check (
		(contract_type = 'MAIN' and main_contract_id is null)
		or (contract_type = 'SUPPLEMENT' and main_contract_id is not null)
	),
	constraint ck_sal_project_contract_amount check (
		(contract_type = 'MAIN' and amount > 0)
		or (contract_type = 'SUPPLEMENT' and amount <> 0)
	),
	constraint ck_sal_project_contract_effective_range check (
		effective_start_date is null or effective_end_date is null or effective_start_date <= effective_end_date
	),
	constraint ck_sal_project_contract_close_reason_length check (
		close_reason is null or length(close_reason) between 1 and 200
	),
	constraint ck_sal_project_contract_terminate_reason_length check (
		terminate_reason is null or length(terminate_reason) between 1 and 200
	),
	constraint ck_sal_project_contract_cancel_reason_length check (
		cancel_reason is null or length(cancel_reason) between 1 and 200
	)
);

alter table sal_sales_order add column project_id bigint;
alter table sal_sales_order add column contract_id bigint;

alter table sal_sales_order add constraint fk_sal_sales_order_project
	foreign key (project_id) references sal_project (id);

alter table sal_sales_order add constraint fk_sal_sales_order_contract
	foreign key (contract_id) references sal_project_contract (id);

alter table sal_sales_order add constraint ck_sal_sales_order_project_pair
	check ((project_id is null and contract_id is null) or (project_id is not null and contract_id is not null));

create index idx_sal_project_customer on sal_project (customer_id);
create index idx_sal_project_owner on sal_project (owner_user_id);
create index idx_sal_project_status_updated on sal_project (status, updated_at desc, id desc);
create index idx_sal_project_plan_dates on sal_project (planned_start_date, planned_finish_date);

create unique index uk_sal_project_contract_main_active on sal_project_contract (project_id)
	where contract_type = 'MAIN' and status <> 'CANCELLED';
create index idx_sal_project_contract_project on sal_project_contract (project_id);
create index idx_sal_project_contract_main on sal_project_contract (main_contract_id);
create index idx_sal_project_contract_signed_date on sal_project_contract (signed_date desc, id desc);
create index idx_sal_project_contract_external_no on sal_project_contract (external_contract_no);

create index idx_sal_sales_order_project_status_date
	on sal_sales_order (project_id, status, order_date desc, id desc);
create index idx_sal_sales_order_contract on sal_sales_order (contract_id);
