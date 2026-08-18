alter table proc_supplier_quote
	add column if not exists entry_source_type varchar(16) not null default 'MANUAL',
	add column if not exists selected_reason varchar(200);

alter table proc_supplier_quote
	drop constraint if exists ck_proc_supplier_quote_entry_source;
alter table proc_supplier_quote
	add constraint ck_proc_supplier_quote_entry_source check (
		entry_source_type in ('MANUAL', 'IMPORT')
	);
