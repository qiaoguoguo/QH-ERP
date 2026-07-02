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
