DO $$
DECLARE
  suffix text := to_char(clock_timestamp(), 'HH24MISSMS');
  op text := 'wp6-test';
  unit_id bigint := 1;
  wh_rm bigint := 1;
  wh_fg bigint := 3;
  supplier_id bigint := 1;
  customer_id bigint := 1;
  cat_fg bigint := 1;
  cat_raw bigint := 2;
  mat_batch bigint;
  mat_serial bigint;
  mat_product bigint;
  mat_none bigint;
  bom_id bigint;
  bom_item_id bigint;
  po_id bigint;
  po_l1 bigint;
  po_l2 bigint;
  pr_id bigint;
  pr_l1 bigint;
  pr_l2 bigint;
  batch_a bigint;
  batch_b bigint;
  batch_fg bigint;
  serial_1 bigint;
  serial_2 bigint;
  serial_3 bigint;
  qa_batch bigint;
  qa_serial bigint;
  qa_fg bigint;
  so_id bigint;
  so_l1 bigint;
  sh_id bigint;
  sh_l1 bigint;
  ship_alloc_a bigint;
  ship_alloc_b bigint;
  sr_id bigint;
  sr_l1 bigint;
  wo_id bigint;
  wom_id bigint;
  mi_id bigint;
  mi_l1 bigint;
  issue_alloc_b bigint;
  pmr_id bigint;
  pmr_l1 bigint;
  pms_id bigint;
  pms_l1 bigint;
  cr_id bigint;
  mov_id bigint;
  readonly_role bigint;
  notrace_role bigint;
  readonly_user bigint;
  notrace_user bigint;
  base_id bigint;
BEGIN
  mat_batch := (select coalesce(max(id), 0) + 1 from mst_material);
  mat_serial := mat_batch + 1;
  mat_product := mat_batch + 2;
  mat_none := mat_batch + 3;

  insert into mst_material(id, code, name, specification, material_type, source_type, category_id, unit_id, status, remark, created_by, created_at, updated_by, updated_at, version, tracking_method)
  values
    (mat_batch, '019VA-BATCH-' || suffix, '019验收批次原料' || suffix, 'WP6批次管理', 'RAW_MATERIAL', 'PURCHASED', cat_raw, unit_id, 'ENABLED', '019 WP6 visual audit local data', op, now(), op, now(), 0, 'BATCH'),
    (mat_serial, '019VA-SERIAL-' || suffix, '019验收序列原料' || suffix, 'WP6序列管理', 'RAW_MATERIAL', 'PURCHASED', cat_raw, unit_id, 'ENABLED', '019 WP6 visual audit local data', op, now(), op, now(), 0, 'SERIAL'),
    (mat_product, '019VA-FG-' || suffix, '019验收完工成品' || suffix, 'WP6完工批次', 'FINISHED_GOOD', 'SELF_MADE', cat_fg, unit_id, 'ENABLED', '019 WP6 visual audit local data', op, now(), op, now(), 0, 'BATCH'),
    (mat_none, '019VA-NONE-' || suffix, '019验收不追踪物料' || suffix, 'WP6不追踪对照', 'RAW_MATERIAL', 'PURCHASED', cat_raw, unit_id, 'ENABLED', '019 WP6 visual audit local data', op, now(), op, now(), 0, 'NONE');

  bom_id := (select coalesce(max(id), 0) + 1 from mfg_bom);
  bom_item_id := (select coalesce(max(id), 0) + 1 from mfg_bom_item);
  insert into mfg_bom(id, bom_code, parent_material_id, version_code, name, base_quantity, base_unit_id, status, effective_from, remark, enabled_by, enabled_at, created_by, created_at, updated_by, updated_at, version)
  values (bom_id, '019VA-BOM-' || suffix, mat_product, 'V1', '019验收BOM' || suffix, 1, unit_id, 'ENABLED', date '2026-07-01', '019 WP6 visual audit local data', op, now(), op, now(), op, now(), 0);
  insert into mfg_bom_item(id, bom_id, line_no, child_material_id, unit_id, quantity, loss_rate, remark, created_at, updated_at)
  values (bom_item_id, bom_id, 1, mat_batch, unit_id, 2, 0, '019 WP6 visual audit local data', now(), now());

  insert into proc_purchase_order(order_no, supplier_id, order_date, expected_arrival_date, status, remark, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at)
  values ('019VA-PO-' || suffix, supplier_id, date '2026-07-10', date '2026-07-12', 'CONFIRMED', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into po_id;
  insert into proc_purchase_order_line(order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price, expected_arrival_date, remark, created_at, updated_at)
  values (po_id, 1, mat_batch, unit_id, 20, 20, 10, date '2026-07-12', '019 batch line', now(), now()) returning id into po_l1;
  insert into proc_purchase_order_line(order_id, line_no, material_id, unit_id, quantity, received_quantity, unit_price, expected_arrival_date, remark, created_at, updated_at)
  values (po_id, 2, mat_serial, unit_id, 3, 3, 12, date '2026-07-12', '019 serial line', now(), now()) returning id into po_l2;
  insert into proc_purchase_receipt(receipt_no, order_id, supplier_id, warehouse_id, business_date, status, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-PR-' || suffix, po_id, supplier_id, wh_rm, date '2026-07-12', 'POSTED', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into pr_id;
  insert into proc_purchase_receipt_line(receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity, received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity, remark, created_at, updated_at)
  values (pr_id, 1, po_l1, mat_batch, unit_id, 20, 0, 20, 20, 0, 20, '019 batch receipt split two batches', now(), now()) returning id into pr_l1;
  insert into proc_purchase_receipt_line(receipt_id, line_no, order_line_id, material_id, unit_id, ordered_quantity, received_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity, remark, created_at, updated_at)
  values (pr_id, 2, po_l2, mat_serial, unit_id, 3, 0, 3, 3, 0, 3, '019 serial receipt three serials', now(), now()) returning id into pr_l2;

  insert into inv_batch(material_id, batch_no, source_type, source_id, source_line_id, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_batch, '019VA-BATCH-A-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l1, date '2026-07-12', '019 batch A', op, now(), op, now()) returning id into batch_a;
  insert into inv_batch(material_id, batch_no, source_type, source_id, source_line_id, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_batch, '019VA-BATCH-B-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l1, date '2026-07-12', '019 batch B', op, now(), op, now()) returning id into batch_b;
  insert into inv_serial(material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status, stock_status, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_serial, '019VA-SN-001-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, 'QUALIFIED', 'IN_STOCK', date '2026-07-12', '019 serial qualified', op, now(), op, now()) returning id into serial_1;
  insert into inv_serial(material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status, stock_status, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_serial, '019VA-SN-002-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, 'FROZEN', 'IN_STOCK', date '2026-07-12', '019 serial frozen', op, now(), op, now()) returning id into serial_2;
  insert into inv_serial(material_id, serial_no, source_type, source_id, source_line_id, warehouse_id, quality_status, stock_status, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_serial, '019VA-SN-003-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, 'REJECTED', 'IN_STOCK', date '2026-07-12', '019 serial rejected', op, now(), op, now()) returning id into serial_3;

  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-PR-A-' || suffix, 'PURCHASE_RECEIPT', 'IN', wh_rm, mat_batch, unit_id, 12, 0, 12, 'PURCHASE_RECEIPT', pr_id, pr_l1, date '2026-07-12', '采购入库', 'batch A inbound', op, now(), 'PENDING_INSPECTION', batch_a) returning id into mov_id;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, movement_id, remark, created_by, created_at, updated_by, updated_at)
  values ('INBOUND', 'PURCHASE_RECEIPT', pr_id, pr_l1, 'PURCHASE_ORDER', po_id, po_l1, wh_rm, mat_batch, unit_id, 'PENDING_INSPECTION', batch_a, 12, mov_id, 'purchase batch A inbound', op, now(), op, now());
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-PR-B-' || suffix, 'PURCHASE_RECEIPT', 'IN', wh_rm, mat_batch, unit_id, 8, 0, 8, 'PURCHASE_RECEIPT', pr_id, pr_l1, date '2026-07-12', '采购入库', 'batch B inbound', op, now(), 'PENDING_INSPECTION', batch_b) returning id into mov_id;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, movement_id, remark, created_by, created_at, updated_by, updated_at)
  values ('INBOUND', 'PURCHASE_RECEIPT', pr_id, pr_l1, 'PURCHASE_ORDER', po_id, po_l1, wh_rm, mat_batch, unit_id, 'PENDING_INSPECTION', batch_b, 8, mov_id, 'purchase batch B inbound', op, now(), op, now());

  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, serial_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values
    ('INBOUND', 'PURCHASE_RECEIPT', pr_id, pr_l2, 'PURCHASE_ORDER', po_id, po_l2, wh_rm, mat_serial, unit_id, 'PENDING_INSPECTION', serial_1, 1, 'purchase serial 1 inbound', op, now(), op, now()),
    ('INBOUND', 'PURCHASE_RECEIPT', pr_id, pr_l2, 'PURCHASE_ORDER', po_id, po_l2, wh_rm, mat_serial, unit_id, 'PENDING_INSPECTION', serial_2, 1, 'purchase serial 2 inbound', op, now(), op, now()),
    ('INBOUND', 'PURCHASE_RECEIPT', pr_id, pr_l2, 'PURCHASE_ORDER', po_id, po_l2, wh_rm, mat_serial, unit_id, 'PENDING_INSPECTION', serial_3, 1, 'purchase serial 3 inbound', op, now(), op, now());

  insert into qua_quality_inspection(inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, business_date, inspection_quantity, qualified_quantity, rejected_quantity, frozen_quantity, status, reason, remark, created_by, created_at, updated_by, updated_at, completed_by, completed_at)
  values ('019VA-QA-BATCH-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l1, wh_rm, mat_batch, unit_id, date '2026-07-13', 20, 16, 2, 2, 'COMPLETED', '019批次质量拆分', '批次 A 拆分合格/不合格/冻结，批次 B 合格', op, now(), op, now(), op, now()) returning id into qa_batch;
  insert into qua_quality_inspection(inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, business_date, inspection_quantity, qualified_quantity, rejected_quantity, frozen_quantity, status, reason, remark, created_by, created_at, updated_by, updated_at, completed_by, completed_at)
  values ('019VA-QA-SERIAL-' || suffix, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, mat_serial, unit_id, date '2026-07-13', 3, 1, 1, 1, 'COMPLETED', '019序列质量拆分', '三个序列分别确认合格/冻结/不合格', op, now(), op, now(), op, now()) returning id into qa_serial;

  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_batch, qa_batch, 'PURCHASE_RECEIPT', pr_id, pr_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_a, 8, 'batch A qualified', op, now(), op, now()),
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_batch, qa_batch, 'PURCHASE_RECEIPT', pr_id, pr_l1, wh_rm, mat_batch, unit_id, 'REJECTED', batch_a, 2, 'batch A rejected', op, now(), op, now()),
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_batch, qa_batch, 'PURCHASE_RECEIPT', pr_id, pr_l1, wh_rm, mat_batch, unit_id, 'FROZEN', batch_a, 2, 'batch A frozen', op, now(), op, now()),
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_batch, qa_batch, 'PURCHASE_RECEIPT', pr_id, pr_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_b, 8, 'batch B qualified', op, now(), op, now());
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, serial_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_serial, qa_serial, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, mat_serial, unit_id, 'QUALIFIED', serial_1, 1, 'serial qualified', op, now(), op, now()),
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_serial, qa_serial, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, mat_serial, unit_id, 'FROZEN', serial_2, 1, 'serial frozen', op, now(), op, now()),
    ('QUALITY_TRANSFER', 'QUALITY_INSPECTION', qa_serial, qa_serial, 'PURCHASE_RECEIPT', pr_id, pr_l2, wh_rm, mat_serial, unit_id, 'REJECTED', serial_3, 1, 'serial rejected', op, now(), op, now());

  insert into inv_stock_balance(warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at, quality_status, batch_id, serial_id)
  values
    (wh_rm, mat_batch, unit_id, 8, 0, now(), now(), 'QUALIFIED', batch_a, null),
    (wh_rm, mat_batch, unit_id, 2, 0, now(), now(), 'REJECTED', batch_a, null),
    (wh_rm, mat_batch, unit_id, 2, 0, now(), now(), 'FROZEN', batch_a, null),
    (wh_rm, mat_batch, unit_id, 8, 0, now(), now(), 'QUALIFIED', batch_b, null),
    (wh_rm, mat_serial, unit_id, 1, 0, now(), now(), 'QUALIFIED', null, serial_1),
    (wh_rm, mat_serial, unit_id, 1, 0, now(), now(), 'FROZEN', null, serial_2),
    (wh_rm, mat_serial, unit_id, 1, 0, now(), now(), 'REJECTED', null, serial_3);

  insert into sal_sales_order(order_no, customer_id, order_date, expected_ship_date, status, remark, created_by, created_at, updated_by, updated_at, confirmed_by, confirmed_at)
  values ('019VA-SO-' || suffix, customer_id, date '2026-07-14', date '2026-07-15', 'CONFIRMED', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into so_id;
  insert into sal_sales_order_line(order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price, expected_ship_date, remark, created_at, updated_at, reservation_warehouse_id)
  values (so_id, 1, mat_batch, unit_id, 4, 4, 30, date '2026-07-15', '019 sales multi batch', now(), now(), wh_rm) returning id into so_l1;
  insert into sal_sales_shipment(shipment_no, order_id, customer_id, warehouse_id, business_date, status, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-SH-' || suffix, so_id, customer_id, wh_rm, date '2026-07-15', 'POSTED', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into sh_id;
  insert into sal_sales_shipment_line(shipment_id, line_no, order_line_id, material_id, unit_id, ordered_quantity, shipped_quantity_before, remaining_quantity_before, quantity, before_quantity, after_quantity, remark, created_at, updated_at)
  values (sh_id, 1, so_l1, mat_batch, unit_id, 4, 0, 4, 4, 16, 12, '019 sales shipment split two batches', now(), now()) returning id into sh_l1;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('OUTBOUND', 'SALES_SHIPMENT', sh_id, sh_l1, 'SALES_ORDER', so_id, so_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_a, 2, 'sales outbound batch A', op, now(), op, now()) returning id into ship_alloc_a;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('OUTBOUND', 'SALES_SHIPMENT', sh_id, sh_l1, 'SALES_ORDER', so_id, so_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_b, 2, 'sales outbound batch B', op, now(), op, now()) returning id into ship_alloc_b;
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-SH-A-' || suffix, 'SALES_SHIPMENT', 'OUT', wh_rm, mat_batch, unit_id, 2, 8, 6, 'SALES_SHIPMENT', sh_id, sh_l1, date '2026-07-15', '销售出库', 'batch A outbound', op, now(), 'QUALIFIED', batch_a);
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-SH-B-' || suffix, 'SALES_SHIPMENT', 'OUT', wh_rm, mat_batch, unit_id, 2, 8, 6, 'SALES_SHIPMENT', sh_id, sh_l1, date '2026-07-15', '销售出库', 'batch B outbound', op, now(), 'QUALIFIED', batch_b);
  update inv_stock_balance set quantity_on_hand = quantity_on_hand - 2 where warehouse_id = wh_rm and material_id = mat_batch and quality_status = 'QUALIFIED' and batch_id in (batch_a, batch_b);

  insert into sal_sales_return(return_no, customer_id, source_shipment_id, source_shipment_no, warehouse_id, business_date, status, total_amount, client_request_id, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-SR-' || suffix, customer_id, sh_id, '019VA-SH-' || suffix, wh_rm, date '2026-07-16', 'POSTED', 30, '019VA-SR-' || suffix, '019 sales return source inherit', op, now(), op, now(), op, now()) returning id into sr_id;
  insert into sal_sales_return_line(return_id, source_shipment_line_id, sales_order_line_id, material_id, unit_id, line_no, returned_quantity_before, returnable_quantity_before, quantity, unit_price, amount, reason, created_at, updated_at)
  values (sr_id, sh_l1, so_l1, mat_batch, unit_id, 1, 0, 4, 1, 30, 30, '客户退回', now(), now()) returning id into sr_l1;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('SOURCE_INHERIT', 'SALES_RETURN', sr_id, sr_l1, 'SALES_SHIPMENT', sh_id, sh_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_a, 1, 'sales return inherits source allocation', op, now(), op, now());
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-SR-A-' || suffix, 'SALES_RETURN_IN', 'IN', wh_rm, mat_batch, unit_id, 1, 6, 7, 'SALES_RETURN', sr_id, sr_l1, date '2026-07-16', '销售退货入库', 'source inherit batch A', op, now(), 'QUALIFIED', batch_a);
  update inv_stock_balance set quantity_on_hand = quantity_on_hand + 1 where warehouse_id = wh_rm and material_id = mat_batch and quality_status = 'QUALIFIED' and batch_id = batch_a;

  insert into mfg_work_order(work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity, qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id, receipt_warehouse_id, planned_start_date, planned_finish_date, status, remark, created_by, created_at, updated_by, updated_at, released_by, released_at)
  values ('019VA-WO-' || suffix, mat_product, bom_id, 3, 0, 0, 0, 2, wh_rm, wh_fg, date '2026-07-14', date '2026-07-20', 'IN_PROGRESS', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into wo_id;
  insert into mfg_work_order_material(work_order_id, line_no, bom_item_id, material_id, unit_id, required_quantity, issued_quantity, loss_rate, remark, created_at, updated_at)
  values (wo_id, 1, bom_item_id, mat_batch, unit_id, 6, 2, 0, '019 work order material', now(), now()) returning id into wom_id;
  insert into mfg_material_issue(issue_no, work_order_id, status, business_date, reason, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-MI-' || suffix, wo_id, 'POSTED', date '2026-07-17', '生产领料', '019 WP6 visual audit local data', op, now(), op, now(), op, now()) returning id into mi_id;
  insert into mfg_material_issue_line(issue_id, work_order_material_id, line_no, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, remark, created_at, updated_at)
  values (mi_id, wom_id, 1, wh_rm, mat_batch, unit_id, 2, 6, 4, '019 production issue batch B', now(), now()) returning id into mi_l1;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('OUTBOUND', 'PRODUCTION_ISSUE', mi_id, mi_l1, 'WORK_ORDER', wo_id, wom_id, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_b, 2, 'production issue batch B', op, now(), op, now()) returning id into issue_alloc_b;
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-MI-B-' || suffix, 'PRODUCTION_ISSUE', 'OUT', wh_rm, mat_batch, unit_id, 2, 6, 4, 'PRODUCTION_ISSUE', mi_id, mi_l1, date '2026-07-17', '生产领料', 'batch B issue', op, now(), 'QUALIFIED', batch_b);
  update inv_stock_balance set quantity_on_hand = quantity_on_hand - 2 where warehouse_id = wh_rm and material_id = mat_batch and quality_status = 'QUALIFIED' and batch_id = batch_b;

  insert into mfg_material_return(return_no, work_order_id, source_issue_id, warehouse_id, business_date, status, client_request_id, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-PMR-' || suffix, wo_id, mi_id, wh_rm, date '2026-07-18', 'POSTED', '019VA-PMR-' || suffix, '019 production material return source inherit', op, now(), op, now(), op, now()) returning id into pmr_id;
  insert into mfg_material_return_line(return_id, source_issue_line_id, work_order_material_id, material_id, unit_id, line_no, returned_quantity_before, returnable_quantity_before, quantity, reason, created_at, updated_at)
  values (pmr_id, mi_l1, wom_id, mat_batch, unit_id, 1, 0, 2, 1, '生产退料', now(), now()) returning id into pmr_l1;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('SOURCE_INHERIT', 'PRODUCTION_MATERIAL_RETURN', pmr_id, pmr_l1, 'PRODUCTION_ISSUE', mi_id, mi_l1, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_b, 1, 'production return inherits source allocation', op, now(), op, now());
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-PMR-B-' || suffix, 'PRODUCTION_MATERIAL_RETURN_IN', 'IN', wh_rm, mat_batch, unit_id, 1, 4, 5, 'PRODUCTION_MATERIAL_RETURN', pmr_id, pmr_l1, date '2026-07-18', '生产退料入库', 'source inherit batch B', op, now(), 'QUALIFIED', batch_b) returning id into mov_id;
  update mfg_material_return_line set stock_movement_id = mov_id where id = pmr_l1;
  update inv_stock_balance set quantity_on_hand = quantity_on_hand + 1 where warehouse_id = wh_rm and material_id = mat_batch and quality_status = 'QUALIFIED' and batch_id = batch_b;

  insert into mfg_material_supplement(supplement_no, work_order_id, warehouse_id, business_date, status, client_request_id, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-PMS-' || suffix, wo_id, wh_rm, date '2026-07-19', 'POSTED', '019VA-PMS-' || suffix, '019 production material supplement', op, now(), op, now(), op, now()) returning id into pms_id;
  insert into mfg_material_supplement_line(supplement_id, work_order_material_id, material_id, unit_id, line_no, issued_quantity_before, supplemented_quantity_before, available_stock_quantity_before, quantity, reason, created_at, updated_at)
  values (pms_id, wom_id, mat_batch, unit_id, 1, 2, 0, 7, 1, '生产补料', now(), now()) returning id into pms_l1;
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('OUTBOUND', 'PRODUCTION_MATERIAL_SUPPLEMENT', pms_id, pms_l1, 'WORK_ORDER', wo_id, wom_id, wh_rm, mat_batch, unit_id, 'QUALIFIED', batch_a, 1, 'production supplement batch A', op, now(), op, now());
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-PMS-A-' || suffix, 'PRODUCTION_MATERIAL_SUPPLEMENT_OUT', 'OUT', wh_rm, mat_batch, unit_id, 1, 7, 6, 'PRODUCTION_MATERIAL_SUPPLEMENT', pms_id, pms_l1, date '2026-07-19', '生产补料出库', 'batch A supplement', op, now(), 'QUALIFIED', batch_a) returning id into mov_id;
  update mfg_material_supplement_line set stock_movement_id = mov_id where id = pms_l1;
  update inv_stock_balance set quantity_on_hand = quantity_on_hand - 1 where warehouse_id = wh_rm and material_id = mat_batch and quality_status = 'QUALIFIED' and batch_id = batch_a;

  insert into mfg_completion_receipt(receipt_no, work_order_id, status, business_date, receipt_warehouse_id, quantity, before_quantity, after_quantity, remark, created_by, created_at, updated_by, updated_at, posted_by, posted_at)
  values ('019VA-CR-' || suffix, wo_id, 'POSTED', date '2026-07-19', wh_fg, 2, 0, 2, '019 completion receipt batch FG', op, now(), op, now(), op, now()) returning id into cr_id;
  insert into inv_batch(material_id, batch_no, source_type, source_id, source_line_id, business_date, remark, created_by, created_at, updated_by, updated_at)
  values (mat_product, '019VA-FG-BATCH-' || suffix, 'PRODUCTION_COMPLETION', cr_id, cr_id, date '2026-07-19', '019 finished batch', op, now(), op, now()) returning id into batch_fg;
  insert into inv_stock_balance(warehouse_id, material_id, unit_id, quantity_on_hand, locked_quantity, created_at, updated_at, quality_status, batch_id)
  values (wh_fg, mat_product, unit_id, 2, 0, now(), now(), 'PENDING_INSPECTION', batch_fg);
  insert into inv_stock_tracking_allocation(allocation_type, document_type, document_id, document_line_id, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, quality_status, batch_id, quantity, remark, created_by, created_at, updated_by, updated_at)
  values ('INBOUND', 'PRODUCTION_COMPLETION', cr_id, cr_id, 'WORK_ORDER', wo_id, wo_id, wh_fg, mat_product, unit_id, 'PENDING_INSPECTION', batch_fg, 2, 'completion receipt batch', op, now(), op, now());
  insert into inv_stock_movement(movement_no, movement_type, direction, warehouse_id, material_id, unit_id, quantity, before_quantity, after_quantity, source_type, source_id, source_line_id, business_date, reason, remark, operator_name, occurred_at, quality_status, batch_id)
  values ('019VA-MOV-CR-FG-' || suffix, 'PRODUCTION_RECEIPT', 'IN', wh_fg, mat_product, unit_id, 2, 0, 2, 'PRODUCTION_COMPLETION', cr_id, cr_id, date '2026-07-19', '完工入库', 'finished batch inbound', op, now(), 'PENDING_INSPECTION', batch_fg);
  insert into qua_quality_inspection(inspection_no, source_type, source_id, source_line_id, warehouse_id, material_id, unit_id, business_date, inspection_quantity, qualified_quantity, rejected_quantity, frozen_quantity, status, reason, remark, created_by, created_at, updated_by, updated_at)
  values ('019VA-QA-FG-PENDING-' || suffix, 'PRODUCTION_COMPLETION', cr_id, cr_id, wh_fg, mat_product, unit_id, date '2026-07-19', 2, 0, 0, 0, 'PENDING', null, '待处理完工入库质量确认', op, now(), op, now()) returning id into qa_fg;

  readonly_role := (select coalesce(max(id), 0) + 1 from sys_role);
  notrace_role := readonly_role + 1;
  readonly_user := (select coalesce(max(id), 0) + 1 from sys_user);
  notrace_user := readonly_user + 1;
  insert into sys_role(id, code, name, description, status, sort_order, created_by, created_at, updated_by, updated_at, version)
  values
    (readonly_role, '019VA_READONLY_' || suffix, '019只读验收角色' || suffix, 'WP6只读验收', 'ENABLED', 9019, op, now(), op, now(), 0),
    (notrace_role, '019VA_NOTRACE_' || suffix, '019无追溯验收角色' || suffix, 'WP6无追溯权限验收', 'ENABLED', 9020, op, now(), op, now(), 0);
  base_id := (select coalesce(max(id), 0) from sys_role_permission);
  insert into sys_role_permission(id, role_id, permission_id, created_by, created_at)
  select base_id + row_number() over (), readonly_role, id, op, now()
  from sys_permission
  where code in ('master','master:material:view','inventory','inventory:balance:view','inventory:movement:view','inventory:trace:view','procurement','procurement:receipt:view','sales','sales:shipment:view','sales:return:view','production','production:work-order:view','production:material-return:view','production:material-supplement:view','quality','quality:inspection:view','system','system:business-period:view');
  base_id := (select coalesce(max(id), 0) from sys_role_permission);
  insert into sys_role_permission(id, role_id, permission_id, created_by, created_at)
  select base_id + row_number() over (), notrace_role, id, op, now()
  from sys_permission
  where code in ('master','master:material:view','inventory','inventory:balance:view','inventory:movement:view');
  insert into sys_user(id, username, password_hash, display_name, status, password_changed_at, created_by, created_at, updated_by, updated_at, version)
  select readonly_user, '019va_readonly_' || lower(suffix), password_hash, '019只读用户' || suffix, 'ENABLED', now(), op, now(), op, now(), 0 from sys_user where username = 'admin';
  insert into sys_user(id, username, password_hash, display_name, status, password_changed_at, created_by, created_at, updated_by, updated_at, version)
  select notrace_user, '019va_notrace_' || lower(suffix), password_hash, '019无追溯用户' || suffix, 'ENABLED', now(), op, now(), op, now(), 0 from sys_user where username = 'admin';
  base_id := (select coalesce(max(id), 0) from sys_user_role);
  insert into sys_user_role(id, user_id, role_id, created_by, created_at)
  values (base_id + 1, readonly_user, readonly_role, op, now()), (base_id + 2, notrace_user, notrace_role, op, now());

  raise notice 'WP6_019_DATA suffix=% mat_batch=% mat_serial=% mat_product=% mat_none=% receipt=% batch_a=% batch_b=% batch_fg=% serial_1=% serial_2=% serial_3=% qa_batch=% qa_serial=% qa_fg=% shipment=% sales_return=% work_order=% issue=% production_return=% supplement=% completion=% ship_alloc_a=% ship_alloc_b=% issue_alloc_b=% readonly_user=% notrace_user=%',
    suffix, mat_batch, mat_serial, mat_product, mat_none, pr_id, batch_a, batch_b, batch_fg, serial_1, serial_2, serial_3, qa_batch, qa_serial, qa_fg, sh_id, sr_id, wo_id, mi_id, pmr_id, pms_id, cr_id, ship_alloc_a, ship_alloc_b, issue_alloc_b, '019va_readonly_' || lower(suffix), '019va_notrace_' || lower(suffix);
END $$;
