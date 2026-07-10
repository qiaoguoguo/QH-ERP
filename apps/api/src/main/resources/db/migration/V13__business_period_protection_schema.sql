create table biz_business_period (
    id bigserial primary key,
    period_code varchar(20) not null,
    period_name varchar(80) not null,
    start_date date not null,
    end_date date not null,
    status varchar(20) not null,
    locked_by varchar(80),
    locked_at timestamptz,
    lock_reason varchar(300),
    unlocked_by varchar(80),
    unlocked_at timestamptz,
    unlock_reason varchar(300),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_biz_business_period_code unique (period_code),
    constraint ck_biz_business_period_status check (status in ('OPEN', 'LOCKED')),
    constraint ck_biz_business_period_date_range check (start_date <= end_date),
    constraint ex_biz_business_period_no_overlap exclude using gist (daterange(start_date, end_date, '[]') with &&)
);

create index idx_biz_business_period_date_range on biz_business_period (start_date, end_date);

create table biz_business_period_audit (
    id bigserial primary key,
    period_id bigint references biz_business_period(id),
    period_code varchar(20),
    action varchar(40) not null,
    business_date date,
    source_type varchar(80),
    source_id bigint,
    reason varchar(300),
    operator_username varchar(80) not null,
    created_at timestamptz not null
);

create index idx_biz_business_period_audit_period on biz_business_period_audit (period_id, created_at desc);
