alter table proc_price_agreement
    add column if not exists source_quote_id bigint;

alter table proc_price_agreement
    drop constraint if exists fk_proc_price_agreement_source_quote;

alter table proc_price_agreement
    add constraint fk_proc_price_agreement_source_quote
        foreign key (source_quote_id) references proc_supplier_quote(id);
