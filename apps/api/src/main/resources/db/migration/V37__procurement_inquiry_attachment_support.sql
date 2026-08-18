alter table platform_business_attachment drop constraint if exists ck_platform_business_attachment_object;
alter table platform_business_attachment add constraint ck_platform_business_attachment_object check (
	object_type in (
		'SALES_PROJECT_CONTRACT',
		'BOM_ENGINEERING_CHANGE',
		'INVENTORY_OWNERSHIP_CONVERSION',
		'INVENTORY_STOCKTAKE',
		'INVENTORY_VALUATION_ADJUSTMENT',
		'PROCUREMENT_REQUISITION',
		'PROCUREMENT_PRICE_AGREEMENT',
		'PROCUREMENT_ORDER',
		'PROCUREMENT_INQUIRY',
		'SALES_QUOTE',
		'SALES_ORDER_CHANGE',
		'SALES_PROJECT',
		'DATA_REPAIR_REQUEST'
	)
);
