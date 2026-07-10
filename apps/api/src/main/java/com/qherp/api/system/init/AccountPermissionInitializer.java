package com.qherp.api.system.init;

import com.qherp.api.system.permission.SystemPermission;
import com.qherp.api.system.permission.SystemPermissionRepository;
import com.qherp.api.system.permission.SystemPermissionType;
import com.qherp.api.system.permission.SystemRolePermission;
import com.qherp.api.system.permission.SystemRolePermissionRepository;
import com.qherp.api.system.role.SystemRole;
import com.qherp.api.system.role.SystemRoleRepository;
import com.qherp.api.system.role.SystemRoleStatus;
import com.qherp.api.system.user.SystemUser;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserRole;
import com.qherp.api.system.user.SystemUserRoleRepository;
import com.qherp.api.system.user.SystemUserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class AccountPermissionInitializer implements ApplicationRunner {

	private static final String SYSTEM_OPERATOR = "system";

	private static final String ADMIN_USERNAME = "admin";

	private static final String SYSTEM_ADMIN_ROLE_CODE = "SYSTEM_ADMIN";

	private static final List<PermissionSeed> PERMISSION_SEEDS = List.of(
			new PermissionSeed("system", "系统管理", SystemPermissionType.MENU, null, "/system", null, null, 0),
			new PermissionSeed("system:user", "用户管理", SystemPermissionType.MENU, "system", "/system/users", null,
					null, 10),
			new PermissionSeed("system:user:view", "查看用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "GET", "/api/admin/users/**", 11),
			new PermissionSeed("system:user:create", "创建用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "POST", "/api/admin/users", 12),
			new PermissionSeed("system:user:update", "更新、启用、停用用户", SystemPermissionType.ACTION, "system:user",
					"/system/users", "PUT", "/api/admin/users/**", 13),
			new PermissionSeed("system:user:reset-password", "重置用户密码", SystemPermissionType.ACTION, "system:user",
					"/system/users", "PUT", "/api/admin/users/{id}/password", 14),
			new PermissionSeed("system:role", "角色管理", SystemPermissionType.MENU, "system", "/system/roles", null,
					null, 20),
			new PermissionSeed("system:role:view", "查看角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "GET", "/api/admin/roles/**", 21),
			new PermissionSeed("system:role:create", "创建角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "POST", "/api/admin/roles", 22),
			new PermissionSeed("system:role:update", "更新、启用、停用角色", SystemPermissionType.ACTION, "system:role",
					"/system/roles", "PUT", "/api/admin/roles/**", 23),
			new PermissionSeed("system:role:assign-permission", "分配角色权限", SystemPermissionType.ACTION,
					"system:role", "/system/roles", "PUT", "/api/admin/roles/{id}/permissions", 24),
			new PermissionSeed("system:permission", "权限管理", SystemPermissionType.MENU, "system",
					"/system/permissions", null, null, 30),
			new PermissionSeed("system:permission:view", "查看权限树", SystemPermissionType.ACTION, "system:permission",
					"/system/permissions", "GET", "/api/admin/permissions/tree", 31),
			new PermissionSeed("system:audit", "审计日志", SystemPermissionType.MENU, "system", "/system/audit-logs",
					null, null, 40),
			new PermissionSeed("system:audit:view", "查看账号权限审计", SystemPermissionType.ACTION, "system:audit",
					"/system/audit-logs", "GET", "/api/admin/audit-logs", 41),
			new PermissionSeed("system:business-period", "业务期间", SystemPermissionType.MENU, "system",
					"/system/business-periods", null, null, 50),
			new PermissionSeed("system:business-period:view", "查看业务期间", SystemPermissionType.ACTION,
					"system:business-period", "/system/business-periods", "GET",
					"/api/admin/system/business-periods/**", 51),
			new PermissionSeed("system:business-period:create", "创建业务期间", SystemPermissionType.ACTION,
					"system:business-period", "/system/business-periods", "POST",
					"/api/admin/system/business-periods", 52),
			new PermissionSeed("system:business-period:update", "更新业务期间", SystemPermissionType.ACTION,
					"system:business-period", "/system/business-periods", "PUT",
					"/api/admin/system/business-periods/{id}", 53),
			new PermissionSeed("system:business-period:lock", "锁定业务期间", SystemPermissionType.ACTION,
					"system:business-period", "/system/business-periods", "POST",
					"/api/admin/system/business-periods/{id}/lock", 54),
			new PermissionSeed("system:business-period:unlock", "解锁业务期间", SystemPermissionType.ACTION,
					"system:business-period", "/system/business-periods", "POST",
					"/api/admin/system/business-periods/{id}/unlock", 55),
			new PermissionSeed("master", "基础资料", SystemPermissionType.MENU, null, "/master", null, null, 100),
			new PermissionSeed("master:unit", "计量单位", SystemPermissionType.MENU, "master", "/master/units",
					null, null, 110),
			new PermissionSeed("master:unit:view", "查看计量单位", SystemPermissionType.ACTION, "master:unit",
					"/master/units", "GET", "/api/admin/master/units/**", 111),
			new PermissionSeed("master:unit:create", "创建计量单位", SystemPermissionType.ACTION, "master:unit",
					"/master/units", "POST", "/api/admin/master/units", 112),
			new PermissionSeed("master:unit:update", "更新、启用、停用计量单位", SystemPermissionType.ACTION,
					"master:unit", "/master/units", "PUT", "/api/admin/master/units/**", 113),
			new PermissionSeed("master:warehouse", "仓库", SystemPermissionType.MENU, "master",
					"/master/warehouses", null, null, 120),
			new PermissionSeed("master:warehouse:view", "查看仓库", SystemPermissionType.ACTION, "master:warehouse",
					"/master/warehouses", "GET", "/api/admin/master/warehouses/**", 121),
			new PermissionSeed("master:warehouse:create", "创建仓库", SystemPermissionType.ACTION,
					"master:warehouse", "/master/warehouses", "POST", "/api/admin/master/warehouses", 122),
			new PermissionSeed("master:warehouse:update", "更新、启用、停用仓库", SystemPermissionType.ACTION,
					"master:warehouse", "/master/warehouses", "PUT", "/api/admin/master/warehouses/**", 123),
			new PermissionSeed("master:supplier", "供应商", SystemPermissionType.MENU, "master",
					"/master/suppliers", null, null, 130),
			new PermissionSeed("master:supplier:view", "查看供应商", SystemPermissionType.ACTION, "master:supplier",
					"/master/suppliers", "GET", "/api/admin/master/suppliers/**", 131),
			new PermissionSeed("master:supplier:create", "创建供应商", SystemPermissionType.ACTION,
					"master:supplier", "/master/suppliers", "POST", "/api/admin/master/suppliers", 132),
			new PermissionSeed("master:supplier:update", "更新、启用、停用供应商", SystemPermissionType.ACTION,
					"master:supplier", "/master/suppliers", "PUT", "/api/admin/master/suppliers/**", 133),
			new PermissionSeed("master:customer", "客户", SystemPermissionType.MENU, "master", "/master/customers",
					null, null, 140),
			new PermissionSeed("master:customer:view", "查看客户", SystemPermissionType.ACTION, "master:customer",
					"/master/customers", "GET", "/api/admin/master/customers/**", 141),
			new PermissionSeed("master:customer:create", "创建客户", SystemPermissionType.ACTION,
					"master:customer", "/master/customers", "POST", "/api/admin/master/customers", 142),
			new PermissionSeed("master:customer:update", "更新、启用、停用客户", SystemPermissionType.ACTION,
					"master:customer", "/master/customers", "PUT", "/api/admin/master/customers/**", 143),
			new PermissionSeed("material", "物料管理", SystemPermissionType.MENU, null, "/materials", null, null,
					200),
			new PermissionSeed("master:material-category", "物料分类", SystemPermissionType.MENU, "material",
					"/materials/categories", null, null, 210),
			new PermissionSeed("master:material-category:view", "查看物料分类", SystemPermissionType.ACTION,
					"master:material-category", "/materials/categories", "GET",
					"/api/admin/master/material-categories/**", 211),
			new PermissionSeed("master:material-category:create", "创建物料分类", SystemPermissionType.ACTION,
					"master:material-category", "/materials/categories", "POST",
					"/api/admin/master/material-categories", 212),
			new PermissionSeed("master:material-category:update", "更新、启用、停用物料分类", SystemPermissionType.ACTION,
					"master:material-category", "/materials/categories", "PUT",
					"/api/admin/master/material-categories/**", 213),
			new PermissionSeed("master:material", "物料档案", SystemPermissionType.MENU, "material",
					"/materials/items", null, null, 220),
			new PermissionSeed("master:material:view", "查看物料", SystemPermissionType.ACTION, "master:material",
					"/materials/items", "GET", "/api/admin/master/materials/**", 221),
			new PermissionSeed("master:material:create", "创建物料", SystemPermissionType.ACTION, "master:material",
					"/materials/items", "POST", "/api/admin/master/materials", 222),
			new PermissionSeed("master:material:update", "更新、启用、停用物料", SystemPermissionType.ACTION,
					"master:material", "/materials/items", "PUT", "/api/admin/master/materials/**", 223),
			new PermissionSeed("material:bom", "BOM 管理", SystemPermissionType.MENU, "material",
					"/materials/boms", null, null, 230),
			new PermissionSeed("material:bom:view", "查看 BOM", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "GET", "/api/admin/boms/**", 231),
			new PermissionSeed("material:bom:create", "创建 BOM", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "POST", "/api/admin/boms", 232),
			new PermissionSeed("material:bom:update", "更新 BOM", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "PUT", "/api/admin/boms/{id}", 233),
			new PermissionSeed("material:bom:copy", "复制 BOM 版本", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "POST", "/api/admin/boms/{id}/copy", 234),
			new PermissionSeed("material:bom:enable", "启用 BOM", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "PUT", "/api/admin/boms/{id}/enable", 235),
			new PermissionSeed("material:bom:disable", "停用 BOM", SystemPermissionType.ACTION, "material:bom",
					"/materials/boms", "PUT", "/api/admin/boms/{id}/disable", 236),
			new PermissionSeed("inventory", "库存管理", SystemPermissionType.MENU, null, "/inventory/balances",
					null, null, 300),
			new PermissionSeed("inventory:balance:view", "查看库存余额", SystemPermissionType.ACTION, "inventory",
					"/inventory/balances", "GET", "/api/admin/inventory/balances", 301),
			new PermissionSeed("inventory:movement:view", "查看库存变动", SystemPermissionType.ACTION, "inventory",
					"/inventory/movements", "GET", "/api/admin/inventory/movements", 302),
			new PermissionSeed("inventory:document:view", "查看库存单据", SystemPermissionType.ACTION, "inventory",
					"/inventory/documents", "GET", "/api/admin/inventory/documents/**", 303),
			new PermissionSeed("inventory:document:create", "创建库存单据", SystemPermissionType.ACTION, "inventory",
					"/inventory/documents", "POST", "/api/admin/inventory/documents", 304),
			new PermissionSeed("inventory:document:update", "更新库存单据", SystemPermissionType.ACTION, "inventory",
					"/inventory/documents", "PUT", "/api/admin/inventory/documents/{id}", 305),
			new PermissionSeed("inventory:document:post", "过账库存单据", SystemPermissionType.ACTION, "inventory",
					"/inventory/documents", "PUT", "/api/admin/inventory/documents/{id}/post", 306),
			new PermissionSeed("procurement", "采购管理", SystemPermissionType.MENU, null, "/procurement/orders",
					null, null, 350),
			new PermissionSeed("procurement:order:view", "查看采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "GET", "/api/admin/procurement/orders/**", 351),
			new PermissionSeed("procurement:order:create", "创建采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "POST", "/api/admin/procurement/orders", 352),
			new PermissionSeed("procurement:order:update", "更新采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "PUT", "/api/admin/procurement/orders/{id}", 353),
			new PermissionSeed("procurement:order:confirm", "确认采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "PUT", "/api/admin/procurement/orders/{id}/confirm", 354),
			new PermissionSeed("procurement:order:cancel", "取消采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "PUT", "/api/admin/procurement/orders/{id}/cancel", 355),
			new PermissionSeed("procurement:order:close", "关闭采购订单", SystemPermissionType.ACTION,
					"procurement", "/procurement/orders", "PUT", "/api/admin/procurement/orders/{id}/close", 356),
			new PermissionSeed("procurement:receipt:view", "查看采购入库", SystemPermissionType.ACTION,
					"procurement", "/procurement/receipts", "GET", "/api/admin/procurement/receipts/**", 357),
			new PermissionSeed("procurement:receipt:create", "创建采购入库", SystemPermissionType.ACTION,
					"procurement", "/procurement/receipts", "POST",
					"/api/admin/procurement/orders/{id}/receipts", 358),
			new PermissionSeed("procurement:receipt:update", "更新采购入库", SystemPermissionType.ACTION,
					"procurement", "/procurement/receipts", "PUT", "/api/admin/procurement/receipts/{id}", 359),
			new PermissionSeed("procurement:receipt:post", "过账采购入库", SystemPermissionType.ACTION,
					"procurement", "/procurement/receipts", "PUT", "/api/admin/procurement/receipts/{id}/post",
					360),
			new PermissionSeed("procurement:return:view", "查看采购退货", SystemPermissionType.ACTION,
					"procurement", "/procurement/returns", "GET", "/api/admin/procurement/returns/**", 361),
			new PermissionSeed("procurement:return:create", "创建采购退货", SystemPermissionType.ACTION,
					"procurement", "/procurement/returns", "POST", "/api/admin/procurement/returns", 362),
			new PermissionSeed("procurement:return:update", "更新采购退货草稿", SystemPermissionType.ACTION,
					"procurement", "/procurement/returns", "PUT", "/api/admin/procurement/returns/{id}", 363),
			new PermissionSeed("procurement:return:post", "过账采购退货", SystemPermissionType.ACTION,
					"procurement", "/procurement/returns", "PUT", "/api/admin/procurement/returns/{id}/post", 364),
			new PermissionSeed("procurement:return:cancel", "取消采购退货草稿", SystemPermissionType.ACTION,
					"procurement", "/procurement/returns", "PUT", "/api/admin/procurement/returns/{id}/cancel",
					365),
			new PermissionSeed("sales", "销售管理", SystemPermissionType.MENU, null, "/sales/orders", null, null,
					370),
			new PermissionSeed("sales:order:view", "查看销售订单", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "GET", "/api/admin/sales/orders/**", 371),
			new PermissionSeed("sales:order:create", "创建销售订单", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "POST", "/api/admin/sales/orders", 372),
			new PermissionSeed("sales:order:update", "更新销售订单草稿", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "PUT", "/api/admin/sales/orders/{id}", 373),
			new PermissionSeed("sales:order:confirm", "确认销售订单", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "PUT", "/api/admin/sales/orders/{id}/confirm", 374),
			new PermissionSeed("sales:order:cancel", "取消销售订单", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "PUT", "/api/admin/sales/orders/{id}/cancel", 375),
			new PermissionSeed("sales:order:close", "关闭销售订单", SystemPermissionType.ACTION, "sales",
					"/sales/orders", "PUT", "/api/admin/sales/orders/{id}/close", 376),
			new PermissionSeed("sales:shipment:view", "查看销售出库", SystemPermissionType.ACTION, "sales",
					"/sales/shipments", "GET", "/api/admin/sales/shipments/**", 377),
			new PermissionSeed("sales:shipment:create", "创建销售出库", SystemPermissionType.ACTION, "sales",
					"/sales/shipments", "POST", "/api/admin/sales/orders/{id}/shipments", 378),
			new PermissionSeed("sales:shipment:update", "更新销售出库草稿", SystemPermissionType.ACTION, "sales",
					"/sales/shipments", "PUT", "/api/admin/sales/shipments/{id}", 379),
			new PermissionSeed("sales:shipment:post", "过账销售出库", SystemPermissionType.ACTION, "sales",
					"/sales/shipments", "PUT", "/api/admin/sales/shipments/{id}/post", 380),
			new PermissionSeed("sales:return:view", "查看销售退货", SystemPermissionType.ACTION, "sales",
					"/sales/returns", "GET", "/api/admin/sales/returns/**", 381),
			new PermissionSeed("sales:return:create", "创建销售退货", SystemPermissionType.ACTION, "sales",
					"/sales/returns", "POST", "/api/admin/sales/returns", 382),
			new PermissionSeed("sales:return:update", "更新销售退货草稿", SystemPermissionType.ACTION, "sales",
					"/sales/returns", "PUT", "/api/admin/sales/returns/{id}", 383),
			new PermissionSeed("sales:return:post", "过账销售退货", SystemPermissionType.ACTION, "sales",
					"/sales/returns", "PUT", "/api/admin/sales/returns/{id}/post", 384),
			new PermissionSeed("sales:return:cancel", "取消销售退货草稿", SystemPermissionType.ACTION, "sales",
					"/sales/returns", "PUT", "/api/admin/sales/returns/{id}/cancel", 385),
			new PermissionSeed("production", "生产管理", SystemPermissionType.MENU, null, "/production/work-orders",
					null, null, 400),
			new PermissionSeed("production:work-order:view", "查看生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "GET", "/api/admin/production/work-orders/**", 401),
			new PermissionSeed("production:work-order:create", "创建生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "POST", "/api/admin/production/work-orders", 402),
			new PermissionSeed("production:work-order:update", "更新生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}", 403),
			new PermissionSeed("production:work-order:release", "发布生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/release", 404),
			new PermissionSeed("production:work-order:complete", "完成生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/complete", 405),
			new PermissionSeed("production:work-order:cancel", "取消生产工单", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/cancel",
					406),
			new PermissionSeed("production:issue:view", "查看生产领料", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "GET",
					"/api/admin/production/work-orders/{id}/material-issues/**", 407),
			new PermissionSeed("production:issue:create", "创建生产领料", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "POST",
					"/api/admin/production/work-orders/{id}/material-issues", 408),
			new PermissionSeed("production:issue:update", "更新生产领料", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/material-issues/{issueId}", 409),
			new PermissionSeed("production:issue:post", "过账生产领料", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/material-issues/{issueId}/post", 410),
			new PermissionSeed("production:report:view", "查看生产报工", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "GET", "/api/admin/production/work-orders/{id}/reports/**", 411),
			new PermissionSeed("production:report:create", "创建生产报工", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "POST", "/api/admin/production/work-orders/{id}/reports", 412),
			new PermissionSeed("production:report:update", "更新生产报工", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "PUT", "/api/admin/production/work-orders/{id}/reports/{reportId}",
					413),
			new PermissionSeed("production:report:post", "过账生产报工", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/reports/{reportId}/post", 414),
			new PermissionSeed("production:receipt:view", "查看完工入库", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "GET",
					"/api/admin/production/work-orders/{id}/completion-receipts/**", 415),
			new PermissionSeed("production:receipt:create", "创建完工入库", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "POST",
					"/api/admin/production/work-orders/{id}/completion-receipts", 416),
			new PermissionSeed("production:receipt:update", "更新完工入库", SystemPermissionType.ACTION,
					"production", "/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}", 417),
			new PermissionSeed("production:receipt:post", "过账完工入库", SystemPermissionType.ACTION, "production",
					"/production/work-orders", "PUT",
					"/api/admin/production/work-orders/{id}/completion-receipts/{receiptId}/post", 418),
			new PermissionSeed("production:material-return:view", "查看生产退料", SystemPermissionType.ACTION,
					"production", "/production/material-returns", "GET",
					"/api/admin/production/material-returns/**", 419),
			new PermissionSeed("production:material-return:create", "创建生产退料", SystemPermissionType.ACTION,
					"production", "/production/material-returns", "POST",
					"/api/admin/production/material-returns", 420),
			new PermissionSeed("production:material-return:update", "更新生产退料草稿", SystemPermissionType.ACTION,
					"production", "/production/material-returns", "PUT",
					"/api/admin/production/material-returns/{id}", 421),
			new PermissionSeed("production:material-return:post", "过账生产退料", SystemPermissionType.ACTION,
					"production", "/production/material-returns", "PUT",
					"/api/admin/production/material-returns/{id}/post", 422),
			new PermissionSeed("production:material-return:cancel", "取消生产退料草稿", SystemPermissionType.ACTION,
					"production", "/production/material-returns", "PUT",
					"/api/admin/production/material-returns/{id}/cancel", 423),
			new PermissionSeed("production:material-supplement:view", "查看生产补料", SystemPermissionType.ACTION,
					"production", "/production/material-supplements", "GET",
					"/api/admin/production/material-supplements/**", 424),
			new PermissionSeed("production:material-supplement:create", "创建生产补料", SystemPermissionType.ACTION,
					"production", "/production/material-supplements", "POST",
					"/api/admin/production/material-supplements", 425),
			new PermissionSeed("production:material-supplement:update", "更新生产补料草稿",
					SystemPermissionType.ACTION, "production", "/production/material-supplements", "PUT",
					"/api/admin/production/material-supplements/{id}", 426),
			new PermissionSeed("production:material-supplement:post", "过账生产补料", SystemPermissionType.ACTION,
					"production", "/production/material-supplements", "PUT",
					"/api/admin/production/material-supplements/{id}/post", 427),
			new PermissionSeed("production:material-supplement:cancel", "取消生产补料草稿",
					SystemPermissionType.ACTION, "production", "/production/material-supplements", "PUT",
					"/api/admin/production/material-supplements/{id}/cancel", 428),
			new PermissionSeed("cost", "成本管理", SystemPermissionType.MENU, null, "/cost/records", null, null,
					500),
			new PermissionSeed("cost:record:view", "查看成本记录", SystemPermissionType.ACTION, "cost",
					"/cost/records", "GET", "/api/admin/cost/**", 501),
			new PermissionSeed("cost:record:create", "创建成本记录", SystemPermissionType.ACTION, "cost",
					"/cost/records", "POST", "/api/admin/cost/records", 502),
			new PermissionSeed("cost:record:update", "更新手工成本记录", SystemPermissionType.ACTION, "cost",
					"/cost/records", "PUT", "/api/admin/cost/records/{id}", 503),
			new PermissionSeed("finance", "财务往来", SystemPermissionType.MENU, null, "/finance", null, null,
					600),
			new PermissionSeed("finance:receivable:view", "查看应收", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "GET", "/api/admin/finance/receivables/**", 601),
			new PermissionSeed("finance:receivable:create", "生成应收", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "POST", "/api/admin/finance/receivables", 602),
			new PermissionSeed("finance:receivable:update", "更新应收草稿", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "PUT", "/api/admin/finance/receivables/{id}", 603),
			new PermissionSeed("finance:receivable:confirm", "确认应收", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "PUT", "/api/admin/finance/receivables/{id}/confirm", 604),
			new PermissionSeed("finance:receivable:cancel", "取消应收", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "PUT", "/api/admin/finance/receivables/{id}/cancel", 605),
			new PermissionSeed("finance:receivable:close", "关闭应收", SystemPermissionType.ACTION, "finance",
					"/finance/receivables", "PUT", "/api/admin/finance/receivables/{id}/close", 606),
			new PermissionSeed("finance:receipt:view", "查看收款", SystemPermissionType.ACTION, "finance",
					"/finance/receipts", "GET", "/api/admin/finance/receipts/**", 607),
			new PermissionSeed("finance:receipt:create", "创建收款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/receipts", "POST", "/api/admin/finance/receivables/{id}/receipts", 608),
			new PermissionSeed("finance:receipt:update", "更新收款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/receipts", "PUT", "/api/admin/finance/receipts/{id}", 609),
			new PermissionSeed("finance:receipt:post", "过账收款", SystemPermissionType.ACTION, "finance",
					"/finance/receipts", "PUT", "/api/admin/finance/receipts/{id}/post", 610),
			new PermissionSeed("finance:receipt:cancel", "取消收款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/receipts", "PUT", "/api/admin/finance/receipts/{id}/cancel", 611),
			new PermissionSeed("finance:payable:view", "查看应付", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "GET", "/api/admin/finance/payables/**", 612),
			new PermissionSeed("finance:payable:create", "生成应付", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "POST", "/api/admin/finance/payables", 613),
			new PermissionSeed("finance:payable:update", "更新应付草稿", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "PUT", "/api/admin/finance/payables/{id}", 614),
			new PermissionSeed("finance:payable:confirm", "确认应付", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "PUT", "/api/admin/finance/payables/{id}/confirm", 615),
			new PermissionSeed("finance:payable:cancel", "取消应付", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "PUT", "/api/admin/finance/payables/{id}/cancel", 616),
			new PermissionSeed("finance:payable:close", "关闭应付", SystemPermissionType.ACTION, "finance",
					"/finance/payables", "PUT", "/api/admin/finance/payables/{id}/close", 617),
			new PermissionSeed("finance:payment:view", "查看付款", SystemPermissionType.ACTION, "finance",
					"/finance/payments", "GET", "/api/admin/finance/payments/**", 618),
			new PermissionSeed("finance:payment:create", "创建付款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/payments", "POST", "/api/admin/finance/payables/{id}/payments", 619),
			new PermissionSeed("finance:payment:update", "更新付款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/payments", "PUT", "/api/admin/finance/payments/{id}", 620),
			new PermissionSeed("finance:payment:post", "过账付款", SystemPermissionType.ACTION, "finance",
					"/finance/payments", "PUT", "/api/admin/finance/payments/{id}/post", 621),
			new PermissionSeed("finance:payment:cancel", "取消付款草稿", SystemPermissionType.ACTION, "finance",
					"/finance/payments", "PUT", "/api/admin/finance/payments/{id}/cancel", 622),
			new PermissionSeed("finance:settlement-adjustment:view", "查看往来冲减", SystemPermissionType.ACTION,
					"finance", "/finance/settlement-adjustments", "GET",
					"/api/admin/finance/settlement-adjustments/**", 623),
			new PermissionSeed("finance:settlement-adjustment:create", "创建往来冲减", SystemPermissionType.ACTION,
					"finance", "/finance/settlement-adjustments", "POST",
					"/api/admin/finance/settlement-adjustments", 624),
			new PermissionSeed("finance:settlement-adjustment:update", "更新往来冲减草稿",
					SystemPermissionType.ACTION, "finance", "/finance/settlement-adjustments", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}", 625),
			new PermissionSeed("finance:settlement-adjustment:post", "过账往来冲减", SystemPermissionType.ACTION,
					"finance", "/finance/settlement-adjustments", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}/post", 626),
			new PermissionSeed("finance:settlement-adjustment:cancel", "取消往来冲减草稿",
					SystemPermissionType.ACTION, "finance", "/finance/settlement-adjustments", "PUT",
					"/api/admin/finance/settlement-adjustments/{id}/cancel", 627),
			new PermissionSeed("quality", "质量管理", SystemPermissionType.MENU, null, "/quality/inspections", null,
					null, 700),
			new PermissionSeed("quality:inspection:view", "查看质量确认", SystemPermissionType.ACTION, "quality",
					"/quality/inspections", "GET", "/api/admin/quality/inspections/**", 701),
			new PermissionSeed("quality:inspection:process", "处理质量确认", SystemPermissionType.ACTION, "quality",
					"/quality/inspections", "POST", "/api/admin/quality/inspections/{id}/process", 702),
			new PermissionSeed("quality:status:freeze", "冻结库存质量状态", SystemPermissionType.ACTION, "quality",
					"/quality/inspections", "POST", "/api/admin/inventory/quality-transfers/freeze", 703),
			new PermissionSeed("quality:status:unfreeze", "解冻库存质量状态", SystemPermissionType.ACTION, "quality",
					"/quality/inspections", "POST", "/api/admin/inventory/quality-transfers/unfreeze", 704),
			new PermissionSeed("report", "经营报表", SystemPermissionType.MENU, null, "/reports", null, null,
					700),
			new PermissionSeed("report:overview:view", "查看经营概览", SystemPermissionType.ACTION, "report",
					"/reports/overview", "GET", "/api/admin/reports/overview", 701),
			new PermissionSeed("report:sales:view", "查看销售经营报表", SystemPermissionType.ACTION, "report",
					"/reports/sales", "GET", "/api/admin/reports/sales-summary/**", 702),
			new PermissionSeed("report:procurement:view", "查看采购经营报表", SystemPermissionType.ACTION,
					"report", "/reports/procurement", "GET",
					"/api/admin/reports/procurement-summary/**", 703),
			new PermissionSeed("report:inventory:view", "查看库存收发存报表", SystemPermissionType.ACTION,
					"report", "/reports/inventory", "GET",
					"/api/admin/reports/inventory-stock-flow/**", 704),
			new PermissionSeed("report:production:view", "查看生产执行报表", SystemPermissionType.ACTION,
					"report", "/reports/production", "GET",
					"/api/admin/reports/production-execution/**", 705),
			new PermissionSeed("report:cost:view", "查看成本归集报表", SystemPermissionType.ACTION, "report",
					"/reports/cost", "GET", "/api/admin/reports/cost-collection/**", 706),
			new PermissionSeed("report:settlement:view", "查看往来收付报表", SystemPermissionType.ACTION,
					"report", "/reports/settlement", "GET",
					"/api/admin/reports/settlement-summary/**", 707),
			new PermissionSeed("report:exception:view", "查看经营异常清单", SystemPermissionType.ACTION,
					"report", "/reports/exceptions", "GET", "/api/admin/reports/exceptions/**", 708),
			new PermissionSeed("reversal", "退货退款与反冲", SystemPermissionType.MENU, null, "/reversal", null, null,
					800),
			new PermissionSeed("business:reversal:view", "查看业务反冲追溯", SystemPermissionType.ACTION,
					"reversal", "/reversal/traces", "GET", "/api/admin/reversal-traces", 801));

	private final SystemUserRepository userRepository;

	private final SystemRoleRepository roleRepository;

	private final SystemPermissionRepository permissionRepository;

	private final SystemUserRoleRepository userRoleRepository;

	private final SystemRolePermissionRepository rolePermissionRepository;

	private final PasswordEncoder passwordEncoder;

	private final String initialAdminPassword;

	public AccountPermissionInitializer(SystemUserRepository userRepository, SystemRoleRepository roleRepository,
			SystemPermissionRepository permissionRepository, SystemUserRoleRepository userRoleRepository,
			SystemRolePermissionRepository rolePermissionRepository, PasswordEncoder passwordEncoder,
			@Value("${qherp.account-permission.initial-admin-password:Qherp@2026!}") String initialAdminPassword) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.userRoleRepository = userRoleRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.passwordEncoder = passwordEncoder;
		this.initialAdminPassword = initialAdminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		initialize();
	}

	@Transactional
	public void initialize() {
		SystemUser admin = this.userRepository.findByUsername(ADMIN_USERNAME)
			.orElseGet(() -> this.userRepository.save(new SystemUser(ADMIN_USERNAME,
					this.passwordEncoder.encode(this.initialAdminPassword), "超级管理员", SystemUserStatus.ENABLED,
					SYSTEM_OPERATOR)));

		SystemRole systemAdmin = this.roleRepository.findByCode(SYSTEM_ADMIN_ROLE_CODE)
			.orElseGet(() -> this.roleRepository
				.save(new SystemRole(SYSTEM_ADMIN_ROLE_CODE, "系统管理员", SystemRoleStatus.ENABLED, 0, SYSTEM_OPERATOR)));

		Map<String, SystemPermission> permissionsByCode = new LinkedHashMap<>();
		for (PermissionSeed permission : PERMISSION_SEEDS) {
			Long parentId = permission.parentCode() == null ? null : permissionsByCode.get(permission.parentCode()).getId();
			SystemPermission savedPermission = this.permissionRepository.findByCode(permission.code())
				.orElseGet(() -> this.permissionRepository.save(new SystemPermission(permission.code(), permission.name(),
						permission.type(), parentId, permission.routePath(), permission.apiMethod(), permission.apiPath(),
						permission.sortOrder(), SYSTEM_OPERATOR)));
			permissionsByCode.put(permission.code(), savedPermission);
		}

		if (!this.userRoleRepository.existsByUserIdAndRoleId(admin.getId(), systemAdmin.getId())) {
			this.userRoleRepository.save(new SystemUserRole(admin.getId(), systemAdmin.getId(), SYSTEM_OPERATOR));
		}

		for (SystemPermission permission : permissionsByCode.values()) {
			if (!this.rolePermissionRepository.existsByRoleIdAndPermissionId(systemAdmin.getId(), permission.getId())) {
				this.rolePermissionRepository
					.save(new SystemRolePermission(systemAdmin.getId(), permission.getId(), SYSTEM_OPERATOR));
			}
		}
	}

	private record PermissionSeed(String code, String name, SystemPermissionType type, String parentCode,
			String routePath, String apiMethod, String apiPath, int sortOrder) {
	}

}
