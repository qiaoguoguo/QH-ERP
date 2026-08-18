create table sys_logic_snapshot (
    id bigserial primary key,
    source_digest char(64) not null,
    schema_version integer not null,
    generator_version varchar(32) not null,
    generated_at timestamptz not null,
    source_file_count integer not null,
    evidence_count integer not null,
    status varchar(16) not null default 'INACTIVE',
    imported_at timestamptz not null default now(),
    constraint ck_sys_logic_snapshot_schema check (schema_version > 0),
    constraint ck_sys_logic_snapshot_counts check (source_file_count >= 0 and evidence_count >= 0),
    constraint ck_sys_logic_snapshot_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint uq_sys_logic_snapshot_source_generator unique (source_digest, generator_version)
);

create unique index uq_sys_logic_snapshot_active
    on sys_logic_snapshot (status)
    where status = 'ACTIVE';

create table sys_logic_evidence (
    id bigserial primary key,
    snapshot_id bigint not null references sys_logic_snapshot(id) on delete cascade,
    evidence_key varchar(32) not null,
    evidence_type varchar(32) not null,
    domain varchar(80) not null,
    title varchar(300) not null,
    summary text not null,
    keywords text not null,
    route_path varchar(500),
    http_method varchar(16),
    permission_code varchar(200),
    symbol varchar(300),
    source_path varchar(1000) not null,
    source_line integer not null,
    confidence numeric(4, 3) not null,
    evidence_digest char(64) not null,
    created_at timestamptz not null default now(),
    constraint uq_sys_logic_evidence_key unique (snapshot_id, evidence_key),
    constraint ck_sys_logic_evidence_type check (evidence_type in (
        'ROUTE', 'MENU', 'PAGE_ELEMENT', 'API', 'VALIDATION', 'PERMISSION', 'ENUM',
        'STATE_TRANSITION', 'ERROR', 'DATABASE_CONSTRAINT', 'TEST_BEHAVIOR'
    )),
    constraint ck_sys_logic_evidence_line check (source_line > 0),
    constraint ck_sys_logic_evidence_confidence check (confidence >= 0 and confidence <= 1)
);

create index idx_sys_logic_evidence_snapshot_type
    on sys_logic_evidence (snapshot_id, evidence_type);

create index idx_sys_logic_evidence_snapshot_domain
    on sys_logic_evidence (snapshot_id, domain);

create index idx_sys_logic_evidence_snapshot_route
    on sys_logic_evidence (snapshot_id, route_path)
    where route_path is not null;

create index idx_sys_logic_evidence_source
    on sys_logic_evidence (snapshot_id, source_path, source_line);
