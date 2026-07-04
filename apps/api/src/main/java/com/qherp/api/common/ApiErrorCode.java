package com.qherp.api.common;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

	OK("OK", "成功", HttpStatus.OK),

	VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", HttpStatus.BAD_REQUEST),

	AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未认证或登录已过期", HttpStatus.UNAUTHORIZED),

	AUTH_FORBIDDEN("AUTH_FORBIDDEN", "无访问权限", HttpStatus.FORBIDDEN),

	AUTH_ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN),

	AUTH_USERNAME_EXISTS("AUTH_USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT),

	AUTH_ROLE_CODE_EXISTS("AUTH_ROLE_CODE_EXISTS", "角色编码已存在", HttpStatus.CONFLICT),

	AUTH_INVALID_PASSWORD_RULE("AUTH_INVALID_PASSWORD_RULE", "密码不符合规则", HttpStatus.BAD_REQUEST),

	AUTH_ROLE_DISABLED("AUTH_ROLE_DISABLED", "角色已停用", HttpStatus.FORBIDDEN),

	AUTH_PERMISSION_NOT_FOUND("AUTH_PERMISSION_NOT_FOUND", "权限点不存在", HttpStatus.NOT_FOUND),

	AUTH_INIT_ADMIN_FAILED("AUTH_INIT_ADMIN_FAILED", "初始管理员初始化失败", HttpStatus.INTERNAL_SERVER_ERROR),

	MASTER_DATA_CODE_EXISTS(HttpStatus.CONFLICT, "主数据编码已存在"),

	MASTER_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "主数据不存在"),

	MASTER_DATA_INVALID_STATUS(HttpStatus.BAD_REQUEST, "主数据状态不正确"),

	MASTER_DATA_REFERENCE_INVALID(HttpStatus.BAD_REQUEST, "主数据引用不正确"),

	MASTER_DATA_CATEGORY_IN_USE(HttpStatus.CONFLICT, "物料分类已被启用数据引用"),

	MASTER_DATA_CATEGORY_PARENT_INVALID(HttpStatus.BAD_REQUEST, "物料分类父级不正确"),

	MASTER_DATA_UNIT_IN_USE(HttpStatus.CONFLICT, "计量单位已被启用物料引用"),

	BOM_NOT_FOUND(HttpStatus.NOT_FOUND, "BOM 不存在"),

	BOM_CODE_EXISTS(HttpStatus.CONFLICT, "BOM 编码已存在"),

	BOM_VERSION_EXISTS(HttpStatus.CONFLICT, "同一父项物料的 BOM 版本已存在"),

	BOM_ENABLED_VERSION_EXISTS(HttpStatus.CONFLICT, "同一父项物料已存在启用 BOM"),

	BOM_STATUS_NOT_EDITABLE(HttpStatus.CONFLICT, "当前 BOM 状态不可编辑"),

	BOM_PARENT_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "BOM 父项物料不正确"),

	BOM_CHILD_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "BOM 子项物料不正确"),

	BOM_UNIT_INVALID(HttpStatus.BAD_REQUEST, "BOM 单位不正确"),

	BOM_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "BOM 明细不能为空"),

	BOM_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "BOM 数量或损耗率不正确"),

	BOM_DUPLICATE_ITEM(HttpStatus.CONFLICT, "BOM 明细子项重复"),

	BOM_SELF_REFERENCE(HttpStatus.BAD_REQUEST, "BOM 父项物料不能作为子项"),

	BOM_CYCLE_DETECTED(HttpStatus.CONFLICT, "BOM 存在循环引用"),

	INVENTORY_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "库存单据不存在"),

	INVENTORY_DOCUMENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "库存单据类型不正确"),

	INVENTORY_DOCUMENT_STATUS_INVALID(HttpStatus.CONFLICT, "库存单据状态不允许当前操作"),

	INVENTORY_DOCUMENT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账单据不可编辑"),

	INVENTORY_DOCUMENT_EMPTY_LINES(HttpStatus.BAD_REQUEST, "库存单据明细不能为空"),

	INVENTORY_DOCUMENT_DUPLICATE_LINE(HttpStatus.CONFLICT, "库存单据明细重复"),

	INVENTORY_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "库存数量不正确"),

	INVENTORY_STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "库存不足，调减后库存不能小于 0"),

	INVENTORY_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "仓库不存在或已停用"),

	INVENTORY_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "物料不存在或已停用"),

	INVENTORY_UNIT_INVALID(HttpStatus.BAD_REQUEST, "单位不存在、已停用或不是物料基本单位"),

	INVENTORY_OPENING_EXISTS(HttpStatus.CONFLICT, "同一仓库物料已存在已过账期初"),

	INVENTORY_DUPLICATE_POST(HttpStatus.CONFLICT, "库存单据已过账或重复过账"),

	INVENTORY_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "来源明细已生成库存变动"),

	PRODUCTION_WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "生产工单不存在"),

	PRODUCTION_WORK_ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "生产工单状态不允许当前操作"),

	PRODUCTION_WORK_ORDER_HAS_POSTED_BUSINESS(HttpStatus.CONFLICT, "生产工单已有过账业务，不能取消"),

	PRODUCTION_PRODUCT_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "产品物料不存在、停用或类型不允许"),

	PRODUCTION_BOM_INVALID(HttpStatus.BAD_REQUEST, "生产 BOM 不存在、未启用或父项物料不匹配"),

	PRODUCTION_BOM_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "生产 BOM 明细不能为空"),

	PRODUCTION_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "生产仓库不存在或已停用"),

	PRODUCTION_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "生产用料物料不存在或已停用"),

	PRODUCTION_UNIT_INVALID(HttpStatus.BAD_REQUEST, "生产单位不存在或已停用"),

	PRODUCTION_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "生产数量不正确"),

	PRODUCTION_ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "生产领料单不存在"),

	PRODUCTION_ISSUE_EMPTY_LINES(HttpStatus.BAD_REQUEST, "生产领料明细不能为空"),

	PRODUCTION_ISSUE_EXCEEDS_REQUIRED(HttpStatus.CONFLICT, "累计领料超过应领数量"),

	PRODUCTION_STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "生产领料库存不足"),

	PRODUCTION_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "生产报工单不存在"),

	PRODUCTION_REPORT_EXCEEDS_PLAN(HttpStatus.CONFLICT, "累计报工超过计划数量"),

	PRODUCTION_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "完工入库单不存在"),

	PRODUCTION_RECEIPT_EXCEEDS_REPORTED(HttpStatus.CONFLICT, "累计入库超过累计合格报工数量"),

	PRODUCTION_DOCUMENT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账生产单据不可编辑"),

	PRODUCTION_DUPLICATE_POST(HttpStatus.CONFLICT, "生产单据已过账或重复过账"),

	PRODUCTION_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "生产来源明细已生成库存变动"),

	PROCUREMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "采购订单不存在"),

	PROCUREMENT_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "采购入库单不存在"),

	PROCUREMENT_ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "采购订单状态不允许当前操作"),

	PROCUREMENT_RECEIPT_STATUS_INVALID(HttpStatus.CONFLICT, "采购入库单状态不允许当前操作"),

	PROCUREMENT_ORDER_EMPTY_LINES(HttpStatus.BAD_REQUEST, "采购订单明细不能为空"),

	PROCUREMENT_RECEIPT_EMPTY_LINES(HttpStatus.BAD_REQUEST, "采购入库明细不能为空"),

	PROCUREMENT_SUPPLIER_INVALID(HttpStatus.BAD_REQUEST, "供应商不存在或已停用"),

	PROCUREMENT_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "采购入库仓库不存在或已停用"),

	PROCUREMENT_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "采购物料不存在、已停用或不可采购"),

	PROCUREMENT_UNIT_INVALID(HttpStatus.BAD_REQUEST, "采购单位不存在、已停用或不是物料基本单位"),

	PROCUREMENT_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "采购数量不正确"),

	PROCUREMENT_UNIT_PRICE_INVALID(HttpStatus.BAD_REQUEST, "采购单价不正确"),

	PROCUREMENT_ORDER_DUPLICATE_LINE(HttpStatus.CONFLICT, "采购订单明细物料重复"),

	PROCUREMENT_RECEIPT_DUPLICATE_LINE(HttpStatus.CONFLICT, "采购入库明细来源订单行重复"),

	PROCUREMENT_RECEIPT_EXCEEDS_ORDER(HttpStatus.CONFLICT, "采购入库数量超过订单未入库数量"),

	PROCUREMENT_RECEIPT_LINE_SOURCE_INVALID(HttpStatus.CONFLICT, "采购入库明细来源不正确"),

	PROCUREMENT_RECEIPT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账采购入库单不可编辑"),

	PROCUREMENT_DUPLICATE_POST(HttpStatus.CONFLICT, "采购入库单已过账或重复过账"),

	PROCUREMENT_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "采购来源明细已生成库存变动"),

	SALES_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "销售订单不存在"),

	SALES_SHIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "销售出库单不存在"),

	SALES_ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "销售订单状态不允许当前操作"),

	SALES_SHIPMENT_STATUS_INVALID(HttpStatus.CONFLICT, "销售出库状态不允许当前操作"),

	SALES_ORDER_EMPTY_LINES(HttpStatus.BAD_REQUEST, "销售订单明细不能为空"),

	SALES_SHIPMENT_EMPTY_LINES(HttpStatus.BAD_REQUEST, "销售出库明细不能为空"),

	SALES_CUSTOMER_INVALID(HttpStatus.BAD_REQUEST, "客户不存在或已停用"),

	SALES_WAREHOUSE_INVALID(HttpStatus.BAD_REQUEST, "销售出库仓库不存在或已停用"),

	SALES_MATERIAL_INVALID(HttpStatus.BAD_REQUEST, "销售物料不存在或已停用"),

	SALES_MATERIAL_NOT_SELLABLE(HttpStatus.BAD_REQUEST, "物料类型当前不可销售"),

	SALES_UNIT_INVALID(HttpStatus.BAD_REQUEST, "销售单位不存在、已停用或不是物料基本单位"),

	SALES_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "销售或出库数量不正确"),

	SALES_UNIT_PRICE_INVALID(HttpStatus.BAD_REQUEST, "销售单价不正确"),

	SALES_ORDER_DUPLICATE_LINE(HttpStatus.CONFLICT, "销售订单明细物料重复"),

	SALES_SHIPMENT_DUPLICATE_LINE(HttpStatus.CONFLICT, "销售出库明细来源订单行重复"),

	SALES_SHIPMENT_EXCEEDS_ORDER(HttpStatus.CONFLICT, "销售出库数量超过订单未出库数量"),

	SALES_SHIPMENT_LINE_SOURCE_INVALID(HttpStatus.CONFLICT, "销售出库明细来源不正确"),

	SALES_STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "销售出库库存不足"),

	SALES_SHIPMENT_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账销售出库单不可编辑"),

	SALES_DUPLICATE_POST(HttpStatus.CONFLICT, "销售出库单已过账或重复过账"),

	SALES_MOVEMENT_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "销售来源明细已生成库存变动"),

	COST_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "成本记录不存在"),

	COST_WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "成本关联工单不存在"),

	COST_WORK_ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "工单状态不允许成本记录"),

	COST_SOURCE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "成本来源单据不存在"),

	COST_SOURCE_DOCUMENT_STATUS_INVALID(HttpStatus.CONFLICT, "成本来源单据状态无效"),

	COST_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "成本来源已归集"),

	COST_TYPE_INVALID(HttpStatus.BAD_REQUEST, "成本类型不合法"),

	COST_BASIS_INVALID(HttpStatus.BAD_REQUEST, "成本口径不合法"),

	COST_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "成本数量不合法"),

	COST_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "成本金额不合法"),

	COST_GENERATED_RECORD_IMMUTABLE(HttpStatus.CONFLICT, "自动生成记录来源字段不可修改"),

	FINANCE_RECEIVABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "应收不存在"),

	FINANCE_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "收款不存在"),

	FINANCE_PAYABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "应付不存在"),

	FINANCE_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "付款不存在"),

	FINANCE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "来源单据不存在"),

	FINANCE_SOURCE_STATUS_INVALID(HttpStatus.CONFLICT, "来源单据状态不允许生成往来"),

	FINANCE_SOURCE_DUPLICATED(HttpStatus.CONFLICT, "来源明细已生成往来"),

	FINANCE_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "金额不合法"),

	FINANCE_ALLOCATION_EXCEEDS_BALANCE(HttpStatus.CONFLICT, "收付款金额超过未结余额"),

	FINANCE_STATUS_NOT_ALLOWED(HttpStatus.CONFLICT, "当前状态不允许操作"),

	FINANCE_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账收付款不可编辑或取消"),

	FINANCE_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "并发更新冲突"),

	FINANCE_DUE_DATE_INVALID(HttpStatus.BAD_REQUEST, "到期日期不合法"),

	FINANCE_METHOD_INVALID(HttpStatus.BAD_REQUEST, "收付款方式不合法"),

	REPORT_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "报表日期范围非法"),

	REPORT_PARAMETER_INVALID(HttpStatus.BAD_REQUEST, "报表查询参数非法"),

	REPORT_TRACE_KEY_INVALID(HttpStatus.BAD_REQUEST, "报表追溯键非法"),

	REVERSAL_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "反向业务来源不存在"),

	REVERSAL_SOURCE_STATUS_INVALID(HttpStatus.CONFLICT, "来源状态不允许反向业务"),

	REVERSAL_STATUS_NOT_ALLOWED(HttpStatus.CONFLICT, "当前反向单据状态不允许操作"),

	REVERSAL_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "反向业务数量不合法"),

	REVERSAL_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "反向业务金额不合法"),

	REVERSAL_QUANTITY_EXCEEDS_AVAILABLE(HttpStatus.CONFLICT, "反向业务数量超过可用数量"),

	REVERSAL_AMOUNT_EXCEEDS_AVAILABLE(HttpStatus.CONFLICT, "反向业务金额超过可冲金额"),

	REVERSAL_STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "反向出库库存不足"),

	REVERSAL_DUPLICATED(HttpStatus.CONFLICT, "反向业务重复"),

	REVERSAL_POSTED_IMMUTABLE(HttpStatus.CONFLICT, "已过账反向单不可编辑"),

	REVERSAL_TRACE_RESTRICTED(HttpStatus.FORBIDDEN, "反向业务来源追溯受限"),

	REVERSAL_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "反向业务并发更新冲突"),

	CONFLICT("CONFLICT", "数据冲突", HttpStatus.CONFLICT),

	SYSTEM_ERROR("SYSTEM_ERROR", "系统异常", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String code;

	private final String message;

	private final HttpStatus httpStatus;

	ApiErrorCode(String code, String message, HttpStatus httpStatus) {
		this.code = code;
		this.message = message;
		this.httpStatus = httpStatus;
	}

	ApiErrorCode(HttpStatus httpStatus, String message) {
		this.code = name();
		this.message = message;
		this.httpStatus = httpStatus;
	}

	public String code() {
		return this.code;
	}

	public String message() {
		return this.message;
	}

	public HttpStatus httpStatus() {
		return this.httpStatus;
	}

}
