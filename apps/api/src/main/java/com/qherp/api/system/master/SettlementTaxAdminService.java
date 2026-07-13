package com.qherp.api.system.master;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class SettlementTaxAdminService {

	private static final Set<String> SENSITIVE_FIELDS = Set.of("taxNo", "registeredAddress", "registeredPhone",
			"bankName", "bankAccount");

	private static final Set<String> INVOICE_TYPES = Set.of("GENERAL_VAT", "SPECIAL_VAT", "NONE");

	private static final Set<String> SETTLEMENT_METHODS = Set.of("MONTHLY", "CASH_ON_DELIVERY", "ADVANCE",
			"CUSTOM");

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public SettlementTaxAdminService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public SettlementTaxRecord get(MasterDataAdminService.Resource resource, Long ownerId, CurrentUser operator) {
		ensureOwnerExists(resource, ownerId);
		return row(resource, ownerId, operator).toRecord(ownerType(resource), operatorCanViewSensitive(resource,
				operator));
	}

	@Transactional
	public SettlementTaxRecord update(MasterDataAdminService.Resource resource, Long ownerId, Map<String, Object> request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		ensureOwnerExists(resource, ownerId);
		if (touchesSensitive(request) && !operatorCanUpdateSensitive(resource, operator)) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_SENSITIVE_FORBIDDEN);
		}
		SettlementTaxRow current = row(resource, ownerId, operator);
		Long requestVersion = longValue(request.get("version"));
		if (!current.version().equals(requestVersion)) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_CONCURRENT_MODIFICATION);
		}
		ValidatedSettlementTax validated = validateRequest(request, current);
		OffsetDateTime now = OffsetDateTime.now();
		String table = settlementTable(resource);
		String idColumn = settlementIdColumn(resource);
		if (!current.hasData()) {
			this.jdbcTemplate.update("""
					insert into %s (
						%s, invoice_title, tax_no, registered_address, registered_phone, bank_name, bank_account,
						default_tax_rate, invoice_type, settlement_method, payment_term_days, payment_terms, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""".formatted(table, idColumn), ownerId, validated.invoiceTitle(), validated.taxNo(),
					validated.registeredAddress(), validated.registeredPhone(), validated.bankName(),
					validated.bankAccount(), validated.defaultTaxRate(), validated.invoiceType(),
					validated.settlementMethod(), validated.paymentTermDays(), validated.paymentTerms(),
					validated.remark(), operator.username(), now, operator.username(), now);
		}
		else {
			int updated = this.jdbcTemplate.update("""
					update %s
					set invoice_title = ?, tax_no = ?, registered_address = ?, registered_phone = ?, bank_name = ?,
					    bank_account = ?, default_tax_rate = ?, invoice_type = ?, settlement_method = ?,
					    payment_term_days = ?, payment_terms = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where %s = ? and version = ?
					""".formatted(table, idColumn), validated.invoiceTitle(), validated.taxNo(),
					validated.registeredAddress(), validated.registeredPhone(), validated.bankName(),
					validated.bankAccount(), validated.defaultTaxRate(), validated.invoiceType(),
					validated.settlementMethod(), validated.paymentTermDays(), validated.paymentTerms(),
					validated.remark(), operator.username(), now, ownerId, requestVersion);
			if (updated == 0) {
				throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_CONCURRENT_MODIFICATION);
			}
		}
		this.auditService.record(operator, auditAction(resource), auditTargetType(resource), ownerId,
				code(resource, ownerId), servletRequest);
		return get(resource, ownerId, operator);
	}

	private SettlementTaxRow row(MasterDataAdminService.Resource resource, Long ownerId, CurrentUser operator) {
		String table = settlementTable(resource);
		String idColumn = settlementIdColumn(resource);
		return this.jdbcTemplate.query("""
				select %s as owner_id, invoice_title, tax_no, registered_address, registered_phone, bank_name,
				       bank_account, default_tax_rate, invoice_type, settlement_method, payment_term_days,
				       payment_terms, remark, created_at, updated_at, version
				from %s
				where %s = ?
				""".formatted(idColumn, table, idColumn), this::mapRow, ownerId).stream().findFirst()
			.orElse(SettlementTaxRow.empty(ownerId));
	}

	private SettlementTaxRow mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new SettlementTaxRow(true, rs.getLong("owner_id"), rs.getString("invoice_title"),
				rs.getString("tax_no"), rs.getString("registered_address"), rs.getString("registered_phone"),
				rs.getString("bank_name"), rs.getString("bank_account"), rs.getBigDecimal("default_tax_rate"),
				rs.getString("invoice_type"), rs.getString("settlement_method"),
				rs.getObject("payment_term_days", Integer.class), rs.getString("payment_terms"),
				rs.getString("remark"), rs.getObject("created_at", OffsetDateTime.class),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getLong("version"));
	}

	private boolean touchesSensitive(Map<String, Object> request) {
		return SENSITIVE_FIELDS.stream().anyMatch(request::containsKey);
	}

	private boolean operatorCanViewSensitive(MasterDataAdminService.Resource resource, CurrentUser operator) {
		return operator != null && operator.permissions().contains(sensitiveViewPermission(resource));
	}

	private boolean operatorCanUpdateSensitive(MasterDataAdminService.Resource resource, CurrentUser operator) {
		return operator != null && operator.permissions().contains(sensitiveUpdatePermission(resource));
	}

	private String sensitiveViewPermission(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "master:customer-settlement:sensitive-view";
			case SUPPLIER -> "master:supplier-settlement:sensitive-view";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private String sensitiveUpdatePermission(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "master:customer-settlement:sensitive-update";
			case SUPPLIER -> "master:supplier-settlement:sensitive-update";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private String settlementTable(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "mst_customer_settlement_tax";
			case SUPPLIER -> "mst_supplier_settlement_tax";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private String settlementIdColumn(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "customer_id";
			case SUPPLIER -> "supplier_id";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private String auditAction(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "CUSTOMER_SETTLEMENT_TAX_UPDATE";
			case SUPPLIER -> "SUPPLIER_SETTLEMENT_TAX_UPDATE";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private String auditTargetType(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "CUSTOMER_SETTLEMENT_TAX";
			case SUPPLIER -> "SUPPLIER_SETTLEMENT_TAX";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	private void ensureOwnerExists(MasterDataAdminService.Resource resource, Long ownerId) {
		String table = switch (resource) {
			case CUSTOMER -> "mst_customer";
			case SUPPLIER -> "mst_supplier";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
		Long count = this.jdbcTemplate.queryForObject("select count(*) from " + table + " where id = ?",
				Long.class, ownerId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.MASTER_DATA_NOT_FOUND);
		}
	}

	private String code(MasterDataAdminService.Resource resource, Long ownerId) {
		String table = resource == MasterDataAdminService.Resource.CUSTOMER ? "mst_customer" : "mst_supplier";
		return this.jdbcTemplate.queryForObject("select code from " + table + " where id = ?", String.class, ownerId);
	}

	private String valueOrCurrent(Map<String, Object> request, String field, String current) {
		return request.containsKey(field) ? stringValue(request.get(field)) : current;
	}

	private BigDecimal decimalOrCurrent(Map<String, Object> request, String field, BigDecimal current) {
		return request.containsKey(field) ? decimalValue(request.get(field)) : current;
	}

	private Integer integerOrCurrent(Map<String, Object> request, String field, Integer current) {
		return request.containsKey(field) ? integerValue(request.get(field)) : current;
	}

	private ValidatedSettlementTax validateRequest(Map<String, Object> request, SettlementTaxRow current) {
		BigDecimal defaultTaxRate = decimalOrCurrent(request, "defaultTaxRate", current.defaultTaxRate());
		if (defaultTaxRate != null
				&& (defaultTaxRate.compareTo(BigDecimal.ZERO) < 0 || defaultTaxRate.compareTo(BigDecimal.ONE) > 0)) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		}
		Integer paymentTermDays = integerOrCurrent(request, "paymentTermDays", current.paymentTermDays());
		if (paymentTermDays != null && paymentTermDays < 0) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		}
		String invoiceType = valueOrCurrent(request, "invoiceType", current.invoiceType());
		validateEnum(invoiceType, INVOICE_TYPES);
		String settlementMethod = valueOrCurrent(request, "settlementMethod", current.settlementMethod());
		validateEnum(settlementMethod, SETTLEMENT_METHODS);
		return new ValidatedSettlementTax(valueOrCurrent(request, "invoiceTitle", current.invoiceTitle()),
				valueOrCurrent(request, "taxNo", current.taxNo()),
				valueOrCurrent(request, "registeredAddress", current.registeredAddress()),
				valueOrCurrent(request, "registeredPhone", current.registeredPhone()),
				valueOrCurrent(request, "bankName", current.bankName()),
				valueOrCurrent(request, "bankAccount", current.bankAccount()), defaultTaxRate, invoiceType,
				settlementMethod, paymentTermDays, valueOrCurrent(request, "paymentTerms", current.paymentTerms()),
				valueOrCurrent(request, "remark", current.remark()));
	}

	private void validateEnum(String value, Set<String> allowedValues) {
		if (value != null && !allowedValues.contains(value)) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		}
	}

	private static String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private static BigDecimal decimalValue(Object value) {
		try {
			return value == null ? null : new BigDecimal(value.toString());
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		}
	}

	private static Integer integerValue(Object value) {
		try {
			return value == null ? null : Integer.valueOf(value.toString());
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		}
	}

	private static Long longValue(Object value) {
		return value == null ? null : Long.valueOf(value.toString());
	}

	private static String maskTail(String value, int keep) {
		if (value == null || value.isBlank()) {
			return null;
		}
		int visible = Math.min(keep, value.length());
		return "*".repeat(Math.max(0, value.length() - visible)) + value.substring(value.length() - visible);
	}

	private String ownerType(MasterDataAdminService.Resource resource) {
		return switch (resource) {
			case CUSTOMER -> "CUSTOMER";
			case SUPPLIER -> "SUPPLIER";
			default -> throw new BusinessException(ApiErrorCode.SETTLEMENT_TAX_FIELD_INVALID);
		};
	}

	public record SettlementTaxRecord(String ownerType, Long ownerId, boolean hasData, boolean sensitiveRestricted,
			String restrictedMessage, String invoiceTitle, String taxNo, String taxNoMasked,
			String registeredAddress, String registeredPhone, String bankName, String bankAccount,
			String bankAccountMasked, String defaultTaxRate, String invoiceType, String settlementMethod,
			Integer paymentTermDays, String paymentTerms, String remark, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, Long version) {
	}

	private record SettlementTaxRow(boolean hasData, Long ownerId, String invoiceTitle, String taxNo,
			String registeredAddress, String registeredPhone, String bankName, String bankAccount,
			BigDecimal defaultTaxRate, String invoiceType, String settlementMethod, Integer paymentTermDays,
			String paymentTerms, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt, Long version) {

		static SettlementTaxRow empty(Long ownerId) {
			return new SettlementTaxRow(false, ownerId, null, null, null, null, null, null, null, null, null, null,
					null, null, null, null, 0L);
		}

		SettlementTaxRecord toRecord(String ownerType, boolean canViewSensitive) {
			boolean restricted = this.hasData && !canViewSensitive;
			return new SettlementTaxRecord(ownerType, this.ownerId, this.hasData, restricted,
					restricted ? "敏感资料受限" : null, this.invoiceTitle, canViewSensitive ? this.taxNo : null,
					maskTail(this.taxNo, 4), canViewSensitive ? this.registeredAddress : null,
					canViewSensitive ? this.registeredPhone : null, canViewSensitive ? this.bankName : null,
					canViewSensitive ? this.bankAccount : null, maskTail(this.bankAccount, 4),
					this.defaultTaxRate == null ? null : this.defaultTaxRate.setScale(6).toPlainString(),
					this.invoiceType, this.settlementMethod, this.paymentTermDays, this.paymentTerms, this.remark,
					this.createdAt, this.updatedAt, this.version);
		}

	}

	private record ValidatedSettlementTax(String invoiceTitle, String taxNo, String registeredAddress,
			String registeredPhone, String bankName, String bankAccount, BigDecimal defaultTaxRate, String invoiceType,
			String settlementMethod, Integer paymentTermDays, String paymentTerms, String remark) {
	}

}
