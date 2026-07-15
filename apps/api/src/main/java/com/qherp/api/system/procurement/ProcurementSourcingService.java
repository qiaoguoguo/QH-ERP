package com.qherp.api.system.procurement;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.platform.PlatformApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class ProcurementSourcingService {

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger INQUIRY_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger QUOTE_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger AGREEMENT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final PlatformApprovalService approvalService;

	private final ProcurementActionIdempotencyService actionIdempotencyService;

	public ProcurementSourcingService(JdbcTemplate jdbcTemplate, AuditService auditService,
			@Lazy PlatformApprovalService approvalService,
			ProcurementActionIdempotencyService actionIdempotencyService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.approvalService = approvalService;
		this.actionIdempotencyService = actionIdempotencyService;
	}

	@Transactional
	public Map<String, Object> createInquiry(PurchaseInquiryRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (request == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseMode mode = parseMode(request.purchaseMode());
		Long projectId = mode == PurchaseMode.PROJECT ? request.projectId() : null;
		if (mode == PurchaseMode.PROJECT) {
			requireActiveProject(projectId);
		}
		String title = requiredText(request.title(), 120);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into proc_purchase_inquiry (
						inquiry_no, purchase_mode, project_id, status, title, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextNo("PINQ", INQUIRY_NO_SEQUENCE), mode.name(), projectId, title,
					blankToNull(request.remark()), operator.username(), now, operator.username(), now);
			for (PurchaseInquiryLineRequest line : request.lines()) {
				insertInquiryLine(id, line, now);
			}
			this.auditService.record(operator, "PROCUREMENT_INQUIRY_CREATE", "PROCUREMENT_INQUIRY", id, title,
					servletRequest);
			return inquiry(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> inquiries(String keyword, String procurementMode, Long projectId,
			String status, int page, int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(lower(i.inquiry_no) like ? or lower(i.title) like ?)");
			String like = "%" + keyword.trim().toLowerCase() + "%";
			args.add(like);
			args.add(like);
		}
		if (hasText(procurementMode)) {
			conditions.add("i.purchase_mode = ?");
			args.add(parseMode(procurementMode.trim()).name());
		}
		if (projectId != null) {
			conditions.add("i.project_id = ?");
			args.add(projectId);
		}
		if (hasText(status)) {
			conditions.add("i.status = ?");
			args.add(status.trim());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_inquiry i
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select i.id, i.inquiry_no, i.purchase_mode, i.project_id, i.status, i.title, i.version,
				       i.created_by, i.created_at, i.updated_at,
				       count(distinct q.supplier_id) as supplier_count,
				       count(distinct q.id) as quote_count,
				       count(distinct l.id) as line_count,
				       coalesce(sum(l.quantity), 0) as total_quantity
				from proc_purchase_inquiry i
				left join proc_purchase_inquiry_line l on l.inquiry_id = i.id
				left join proc_supplier_quote q on q.inquiry_id = i.id
				%s
				group by i.id
				order by i.updated_at desc, i.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			String rowStatus = rs.getString("status");
			String mode = rs.getString("purchase_mode");
			row.put("id", rs.getLong("id"));
			row.put("inquiryNo", rs.getString("inquiry_no"));
			row.put("purchaseMode", mode);
			row.put("procurementMode", mode);
			row.put("ownershipType", mode);
			row.put("projectId", nullableLong(rs, "project_id"));
			row.put("title", rs.getString("title"));
			row.put("status", rowStatus);
			row.put("supplierCount", rs.getInt("supplier_count"));
			row.put("quoteCount", rs.getInt("quote_count"));
			row.put("lineCount", rs.getInt("line_count"));
			row.put("totalQuantity", decimalString(rs.getBigDecimal("total_quantity")));
			row.put("allowedActions", inquiryAllowedActions(rowStatus));
			row.put("createdByName", rs.getString("created_by"));
			row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
			row.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
			row.put("version", rs.getLong("version"));
			return row;
		}, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> inquiry(Long id) {
		InquiryRow row = inquiryRow(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("inquiryNo", row.inquiryNo());
		response.put("purchaseMode", row.purchaseMode());
		response.put("procurementMode", row.purchaseMode());
		response.put("ownershipType", row.purchaseMode());
		response.put("projectId", row.projectId());
		response.put("title", row.title());
		response.put("status", row.status());
		response.put("version", row.version());
		response.put("lines", inquiryLines(id));
		response.put("quotes", quotes(id, null, null, 1, 100).items());
		response.put("allowedActions", inquiryAllowedActions(row.status()));
		return response;
	}

	@Transactional
	public Map<String, Object> updateInquiry(Long id, PurchaseInquiryRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		InquiryRow row = lockInquiry(id);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
		}
		if (request == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		PurchaseMode mode = parseMode(request.purchaseMode());
		Long projectId = mode == PurchaseMode.PROJECT ? request.projectId() : null;
		if (mode == PurchaseMode.PROJECT) {
			requireActiveProject(projectId);
		}
		String title = requiredText(request.title(), 120);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_purchase_inquiry
				set purchase_mode = ?, project_id = ?, title = ?, remark = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", mode.name(), projectId, title, blankToNull(request.remark()), operator.username(), now, id);
		this.jdbcTemplate.update("delete from proc_purchase_inquiry_line where inquiry_id = ?", id);
		for (PurchaseInquiryLineRequest line : request.lines()) {
			insertInquiryLine(id, line, now);
		}
		this.auditService.record(operator, "PROCUREMENT_INQUIRY_UPDATE", "PROCUREMENT_INQUIRY", id,
				row.inquiryNo(), servletRequest);
		return inquiry(id);
	}

	@Transactional
	public Map<String, Object> releaseInquiry(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentAction("RELEASE", "PROCUREMENT_INQUIRY", id, request, operator, () -> inquiry(id), () -> {
			InquiryRow row = lockInquiry(id);
			requireVersion(row.version(), request.version());
			if (!"DRAFT".equals(row.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
			}
			this.jdbcTemplate.update("""
					update proc_purchase_inquiry
					set status = 'RELEASED', updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", operator.username(), OffsetDateTime.now(), id);
			this.auditService.record(operator, "PROCUREMENT_INQUIRY_RELEASE", "PROCUREMENT_INQUIRY", id,
					row.inquiryNo(), servletRequest);
			return inquiry(id);
		});
	}

	@Transactional
	public Map<String, Object> completeInquiry(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentAction("COMPLETE", "PROCUREMENT_INQUIRY", id, request, operator, () -> inquiry(id), () -> {
			InquiryRow row = lockInquiry(id);
			requireVersion(row.version(), request.version());
			if (!"RELEASED".equals(row.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
			}
			this.jdbcTemplate.update("""
					update proc_purchase_inquiry
					set status = 'COMPLETED', updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", operator.username(), OffsetDateTime.now(), id);
			this.auditService.record(operator, "PROCUREMENT_INQUIRY_COMPLETE", "PROCUREMENT_INQUIRY", id,
					row.inquiryNo(), servletRequest);
			return inquiry(id);
		});
	}

	@Transactional
	public Map<String, Object> cancelInquiry(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentAction("CANCEL", "PROCUREMENT_INQUIRY", id, request, operator, () -> inquiry(id), () -> {
			InquiryRow row = lockInquiry(id);
			requireVersion(row.version(), request.version());
			if (!"DRAFT".equals(row.status()) && !"RELEASED".equals(row.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
			}
			this.jdbcTemplate.update("""
					update proc_purchase_inquiry
					set status = 'CANCELLED', updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", operator.username(), OffsetDateTime.now(), id);
			this.auditService.record(operator, "PROCUREMENT_INQUIRY_CANCEL", "PROCUREMENT_INQUIRY", id,
					row.inquiryNo(), servletRequest);
			return inquiry(id);
		});
	}

	@Transactional
	public Map<String, Object> createQuote(Long inquiryId, SupplierQuoteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		InquiryRow inquiry = lockInquiry(inquiryId);
		if (!"RELEASED".equals(inquiry.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
		}
		validateCurrency(request.currency());
		requireEnabledSupplier(request.supplierId());
		List<SupplierQuoteLineRequest> lines = quoteLineRequests(inquiryId, request);
		OffsetDateTime now = OffsetDateTime.now();
		Long id = this.jdbcTemplate.queryForObject("""
				insert into proc_supplier_quote (
					quote_no, inquiry_id, supplier_id, status, valid_from, valid_to, currency, remark,
					created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, 'VALID', ?, ?, 'CNY', ?, ?, ?, ?, ?)
				returning id
				""", Long.class, nextNo("PQT", QUOTE_NO_SEQUENCE), inquiryId, request.supplierId(),
				request.validFrom(), request.validTo(), blankToNull(request.remark()), operator.username(), now,
				operator.username(), now);
		for (SupplierQuoteLineRequest line : lines) {
			insertQuoteLine(id, line, now);
		}
		this.auditService.record(operator, "PROCUREMENT_QUOTE_CREATE", "PROCUREMENT_QUOTE", id,
				String.valueOf(id), servletRequest);
		return quote(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> quotes(Long inquiryId, Long supplierId, String status, int page,
			int pageSize) {
		inquiryRow(inquiryId).orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("q.inquiry_id = ?");
		args.add(inquiryId);
		if (supplierId != null) {
			conditions.add("q.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(status)) {
			conditions.add("q.status = ?");
			args.add(status.trim());
		}
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_supplier_quote q
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select q.id
				from proc_supplier_quote q
				%s
				order by q.updated_at desc, q.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> quote(rs.getLong("id")), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> quote(Long id) {
		QuoteRow row = quoteRow(id).orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_NOT_FOUND));
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("quoteNo", row.quoteNo());
		response.put("inquiryId", row.inquiryId());
		response.put("inquiryNo", row.inquiryNo());
		response.put("purchaseMode", row.purchaseMode());
		response.put("procurementMode", row.purchaseMode());
		response.put("ownershipType", row.purchaseMode());
		response.put("projectId", row.projectId());
		response.put("projectCode", row.projectCode());
		response.put("projectName", row.projectName());
		response.put("supplierId", row.supplierId());
		response.put("supplierCode", row.supplierCode());
		response.put("supplierName", row.supplierName());
		response.put("validFrom", row.validFrom());
		response.put("validTo", row.validTo());
		response.put("status", row.status());
		response.put("version", row.version());
		List<Map<String, Object>> lines = quoteLines(id);
		response.put("lines", lines);
		if (!lines.isEmpty()) {
			Map<String, Object> first = lines.getFirst();
			response.put("materialId", first.get("materialId"));
			response.put("materialCode", first.get("materialCode"));
			response.put("materialName", first.get("materialName"));
			response.put("quantity", first.get("quantity"));
			response.put("minPurchaseQuantity", first.get("minPurchaseQuantity"));
			response.put("taxRate", first.get("taxRate"));
			response.put("taxExcludedUnitPrice", first.get("taxExcludedUnitPrice"));
			response.put("taxIncludedUnitPrice", first.get("taxIncludedUnitPrice"));
			response.put("taxExcludedAmount", first.get("taxExcludedAmount"));
			response.put("taxIncludedAmount", first.get("taxIncludedAmount"));
			response.put("deliveryDate", first.get("deliveryDate"));
		}
		response.put("currency", row.currency());
		response.put("allowedActions", quoteAllowedActions(row.status()));
		return response;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> quote(Long inquiryId, Long quoteId) {
		Map<String, Object> response = quote(quoteId);
		if (!response.get("inquiryId").equals(inquiryId)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
		}
		return response;
	}

	@Transactional
	public Map<String, Object> updateQuote(Long inquiryId, Long quoteId, SupplierQuoteRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		QuoteRow row = lockQuote(quoteId);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!row.inquiryId().equals(inquiryId)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
		}
		if (!"VALID".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
		}
		validateCurrency(request.currency());
		requireEnabledSupplier(request.supplierId());
		List<SupplierQuoteLineRequest> lines = quoteLineRequests(inquiryId, request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_supplier_quote
				set supplier_id = ?, valid_from = ?, valid_to = ?, currency = 'CNY', remark = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", request.supplierId(), request.validFrom(), request.validTo(), blankToNull(request.remark()),
				operator.username(), now, quoteId);
		this.jdbcTemplate.update("delete from proc_supplier_quote_line where quote_id = ?", quoteId);
		for (SupplierQuoteLineRequest line : lines) {
			insertQuoteLine(quoteId, line, now);
		}
		this.auditService.record(operator, "PROCUREMENT_QUOTE_UPDATE", "PROCUREMENT_QUOTE", quoteId,
				row.quoteNo(), servletRequest);
		return quote(quoteId);
	}

	@Transactional
	public Map<String, Object> selectQuote(Long inquiryId, Long quoteId, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return idempotentAction("SELECT", "PROCUREMENT_QUOTE", quoteId, request, operator, () -> quote(quoteId), () -> {
			QuoteRow row = lockQuote(quoteId);
			requireVersion(row.version(), request.version());
			if (!row.inquiryId().equals(inquiryId) || !"VALID".equals(row.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
			}
			Long selectedQuoteLineId = firstQuoteLineId(quoteId);
			awardInquiry(inquiryId, new PriceAwardRequest(selectedQuoteLineId, request.reason(),
					request.idempotencyKey()), operator, servletRequest);
			return quote(quoteId);
		});
	}

	@Transactional
	public Map<String, Object> cancelQuote(Long inquiryId, Long quoteId, VersionedActionRequest request,
			CurrentUser operator, HttpServletRequest servletRequest) {
		return idempotentAction("CANCEL", "PROCUREMENT_QUOTE", quoteId, request, operator, () -> quote(quoteId), () -> {
			QuoteRow row = lockQuote(quoteId);
			requireVersion(row.version(), request.version());
			if (!row.inquiryId().equals(inquiryId) || !"VALID".equals(row.status())) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
			}
			this.jdbcTemplate.update("""
					update proc_supplier_quote
					set status = 'CANCELLED', updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", operator.username(), OffsetDateTime.now(), quoteId);
			this.auditService.record(operator, "PROCUREMENT_QUOTE_CANCEL", "PROCUREMENT_QUOTE", quoteId,
					row.quoteNo(), servletRequest);
			return quote(quoteId);
		});
	}

	@Transactional
	public Map<String, Object> awardInquiry(Long inquiryId, PriceAwardRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		InquiryRow inquiry = lockInquiry(inquiryId);
		if (!"COMPLETED".equals(inquiry.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_STATUS_INVALID);
		}
		QuoteSelection selected = quoteSelection(request.selectedQuoteLineId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_NOT_FOUND));
		if (!selected.inquiryId().equals(inquiryId)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_INVALID);
		}
		QuoteSelection lowest = lowestQuoteSelection(selected.inquiryLineId());
		boolean requiresException = lowest != null && !lowest.quoteLineId().equals(selected.quoteLineId());
		if (requiresException && !hasText(request.nonLowestReason())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_SOURCE_INVALID);
		}
		this.jdbcTemplate.update("""
				update proc_supplier_quote
				set status = 'REJECTED', updated_at = now(), version = version + 1
				where inquiry_id = ?
				and id <> ?
				and status = 'VALID'
				""", inquiryId, selected.quoteId());
		this.jdbcTemplate.update("""
				update proc_supplier_quote
				set status = 'SELECTED', updated_at = now(), version = version + 1
				where id = ?
				""", selected.quoteId());
		this.jdbcTemplate.update("""
				update proc_purchase_inquiry
				set status = 'AWARDED', updated_by = ?, updated_at = now(), version = version + 1
				where id = ?
				""", operator.username(), inquiryId);
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", selected.quoteLineId());
		response.put("selectedQuoteLineId", selected.quoteLineId());
		response.put("selectedQuoteId", selected.quoteId());
		response.put("lowestQuoteId", lowest == null ? selected.quoteId() : lowest.quoteId());
		response.put("lowestQuoteLineId", lowest == null ? selected.quoteLineId() : lowest.quoteLineId());
		response.put("requiresExceptionApproval", requiresException);
		response.put("nonLowestReason", request.nonLowestReason());
		this.auditService.record(operator, "PROCUREMENT_INQUIRY_AWARD", "PROCUREMENT_INQUIRY", inquiryId,
				inquiry.inquiryNo(), servletRequest);
		return response;
	}

	@Transactional
	public Map<String, Object> createPriceAgreement(PriceAgreementRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PurchaseMode mode = parseMode(request.purchaseMode());
		Long projectId = mode == PurchaseMode.PROJECT ? request.projectId() : null;
		if (mode == PurchaseMode.PROJECT) {
			requireActiveProject(projectId);
		}
		validateCurrency(request.currency());
		requireEnabledSupplier(request.supplierId());
		List<PriceAgreementLineRequest> lines = agreementLineRequests(request);
		OffsetDateTime now = OffsetDateTime.now();
		Long id = this.jdbcTemplate.queryForObject("""
				insert into proc_price_agreement (
					agreement_no, supplier_id, purchase_mode, project_id, status, currency,
					valid_from, valid_to, priority, remark, created_by, created_at, updated_by, updated_at
				)
				values (?, ?, ?, ?, 'DRAFT', 'CNY', ?, ?, 0, ?, ?, ?, ?, ?)
				returning id
				""", Long.class, nextNo("PAG", AGREEMENT_NO_SEQUENCE), request.supplierId(), mode.name(),
				projectId, request.validFrom(), request.validTo(), blankToNull(request.remark()),
				operator.username(), now, operator.username(), now);
		for (PriceAgreementLineRequest line : lines) {
			insertAgreementLine(id, line, now);
		}
		this.auditService.record(operator, "PROCUREMENT_PRICE_AGREEMENT_CREATE", "PROCUREMENT_PRICE_AGREEMENT",
				id, String.valueOf(id), servletRequest);
		return priceAgreement(id);
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> priceAgreements(String keyword, Long supplierId, Long materialId,
			String procurementMode, Long projectId, String status, int page, int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("lower(a.agreement_no) like ?");
			args.add("%" + keyword.trim().toLowerCase() + "%");
		}
		if (supplierId != null) {
			conditions.add("a.supplier_id = ?");
			args.add(supplierId);
		}
		if (materialId != null) {
			conditions.add("exists (select 1 from proc_price_agreement_line l where l.agreement_id = a.id and l.material_id = ?)");
			args.add(materialId);
		}
		if (hasText(procurementMode)) {
			conditions.add("a.purchase_mode = ?");
			args.add(parseMode(procurementMode.trim()).name());
		}
		if (projectId != null) {
			conditions.add("a.project_id = ?");
			args.add(projectId);
		}
		if (hasText(status)) {
			conditions.add("a.status = ?");
			args.add(status.trim());
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_price_agreement a
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select a.id
				from proc_price_agreement a
				%s
				order by a.updated_at desc, a.id desc
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> priceAgreement(rs.getLong("id")), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> priceAgreement(Long id) {
		AgreementRow row = agreementRow(id)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_NOT_FOUND));
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", row.id());
		response.put("agreementNo", row.agreementNo());
		response.put("supplierId", row.supplierId());
		response.put("supplierCode", row.supplierCode());
		response.put("supplierName", row.supplierName());
		response.put("purchaseMode", row.purchaseMode());
		response.put("procurementMode", row.purchaseMode());
		response.put("ownershipType", row.purchaseMode());
		response.put("projectId", row.projectId());
		response.put("projectCode", row.projectCode());
		response.put("projectName", row.projectName());
		response.put("validFrom", row.validFrom());
		response.put("validTo", row.validTo());
		response.put("status", row.status());
		response.put("approvalInstanceId", row.approvalInstanceId());
		response.put("approvalStatus", approvalStatus(row.status(), row.approvalInstanceId()));
		response.put("version", row.version());
		List<Map<String, Object>> lines = agreementLines(row);
		response.put("lines", lines);
		if (!lines.isEmpty()) {
			Map<String, Object> first = lines.getFirst();
			response.put("materialId", first.get("materialId"));
			response.put("materialCode", first.get("materialCode"));
			response.put("materialName", first.get("materialName"));
			response.put("minPurchaseQuantity", first.get("minimumQuantity"));
			response.put("minimumQuantity", first.get("minimumQuantity"));
			response.put("taxRate", first.get("taxRate"));
			response.put("taxExcludedUnitPrice", first.get("taxExcludedUnitPrice"));
			response.put("taxIncludedUnitPrice", first.get("taxIncludedUnitPrice"));
		}
		response.put("currency", row.currency());
		response.put("allowedActions", agreementAllowedActions(row.status()));
		return response;
	}

	@Transactional
	public Map<String, Object> updatePriceAgreement(Long id, PriceAgreementRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		AgreementRow row = lockAgreement(id);
		requireVersion(row.version(), request == null ? null : request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID);
		}
		PurchaseMode mode = parseMode(request.purchaseMode());
		Long projectId = mode == PurchaseMode.PROJECT ? request.projectId() : null;
		if (mode == PurchaseMode.PROJECT) {
			requireActiveProject(projectId);
		}
		validateCurrency(request.currency());
		requireEnabledSupplier(request.supplierId());
		List<PriceAgreementLineRequest> lines = agreementLineRequests(request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update proc_price_agreement
				set supplier_id = ?, purchase_mode = ?, project_id = ?, currency = 'CNY', valid_from = ?,
				    valid_to = ?, remark = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", request.supplierId(), mode.name(), projectId, request.validFrom(), request.validTo(),
				blankToNull(request.remark()), operator.username(), now, id);
		this.jdbcTemplate.update("delete from proc_price_agreement_line where agreement_id = ?", id);
		for (PriceAgreementLineRequest line : lines) {
			insertAgreementLine(id, line, now);
		}
		this.auditService.record(operator, "PROCUREMENT_PRICE_AGREEMENT_UPDATE",
				"PROCUREMENT_PRICE_AGREEMENT", id, row.agreementNo(), servletRequest);
		return priceAgreement(id);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitPriceAgreementActivation(Long id,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("PROCUREMENT_PRICE_AGREEMENT_ACTIVATION", id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		AgreementRow row = lockAgreement(id);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService
			.submitProcurementPriceAgreementActivation(id, request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update proc_price_agreement
				set status = 'SUBMITTED', approval_instance_id = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		this.auditService.record(operator, "PROCUREMENT_PRICE_AGREEMENT_SUBMIT",
				"PROCUREMENT_PRICE_AGREEMENT", id, row.agreementNo(), servletRequest);
		return this.approvalService.get(approval.id(), operator);
	}

	@Transactional
	public Map<String, Object> disablePriceAgreement(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentAction("DISABLE", "PROCUREMENT_PRICE_AGREEMENT", id, request, operator,
				() -> priceAgreement(id), () -> {
					AgreementRow row = lockAgreement(id);
					requireVersion(row.version(), request.version());
					if (!"ACTIVE".equals(row.status())) {
						throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID);
					}
					if (!hasText(request.reason())) {
						throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
					}
					this.jdbcTemplate.update("""
							update proc_price_agreement
							set status = 'DISABLED', updated_by = ?, updated_at = ?, version = version + 1
							where id = ?
							""", operator.username(), OffsetDateTime.now(), id);
					this.auditService.record(operator, "PROCUREMENT_PRICE_AGREEMENT_DISABLE",
							"PROCUREMENT_PRICE_AGREEMENT", id, row.agreementNo(), servletRequest);
					return priceAgreement(id);
				});
	}

	@Transactional
	public Map<String, Object> cancelPriceAgreement(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return idempotentAction("CANCEL", "PROCUREMENT_PRICE_AGREEMENT", id, request, operator,
				() -> priceAgreement(id), () -> {
					AgreementRow row = lockAgreement(id);
					requireVersion(row.version(), request.version());
					if (!"DRAFT".equals(row.status())) {
						throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID);
					}
					this.jdbcTemplate.update("""
							update proc_price_agreement
							set status = 'CANCELLED', updated_by = ?, updated_at = ?, version = version + 1
							where id = ?
							""", operator.username(), OffsetDateTime.now(), id);
					this.auditService.record(operator, "PROCUREMENT_PRICE_AGREEMENT_CANCEL",
							"PROCUREMENT_PRICE_AGREEMENT", id, row.agreementNo(), servletRequest);
					return priceAgreement(id);
				});
	}

	@Transactional(readOnly = true)
	public PageResponse<Map<String, Object>> effectiveSupplies(Long projectId, Long materialId, Long supplierId,
			String procurementMode, Boolean countedOnly, String status, LocalDate expectedDateFrom,
			LocalDate expectedDateTo, int page, int pageSize, CurrentUser currentUser) {
		if (Boolean.FALSE.equals(countedOnly)) {
			return ineffectiveSupplies(projectId, materialId, supplierId, procurementMode, status, expectedDateFrom,
					expectedDateTo, page, pageSize, currentUser);
		}
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		if (projectId != null) {
			conditions.add("eps.project_id = ?");
			args.add(projectId);
		}
		if (materialId != null) {
			conditions.add("eps.material_id = ?");
			args.add(materialId);
		}
		if (supplierId != null) {
			conditions.add("o.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(procurementMode)) {
			conditions.add("eps.purchase_mode = ?");
			args.add(parseMode(procurementMode.trim()).name());
		}
		if (hasText(status)) {
			conditions.add("(eps.order_status = ? or eps.schedule_status = ?)");
			args.add(status.trim());
			args.add(status.trim());
		}
		if (expectedDateFrom != null) {
			conditions.add("eps.expected_arrival_date >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add("eps.expected_arrival_date <= ?");
			args.add(expectedDateTo);
		}
		String where = conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_effective_purchase_supply eps
				join proc_purchase_order o on o.id = eps.order_id
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select eps.order_id, eps.order_no, eps.order_line_id, eps.schedule_id, s.line_no as schedule_seq,
				       eps.purchase_mode, eps.project_id, p.project_no as project_code, p.name as project_name,
				       eps.material_id, m.code as material_code,
				       m.name as material_name, eps.unit_id, eps.expected_arrival_date, eps.remaining_quantity,
				       eps.order_status, eps.schedule_status, eps.source_type, o.supplier_id, sup.name as supplier_name,
				       ol.price_source_type,
				       case
				           when ol.price_source_type in ('QUOTE_SELECTION', 'SUPPLIER_QUOTE', 'QUOTE') then sq.quote_no
				           when ol.price_source_type in ('AGREEMENT', 'PRICE_AGREEMENT') then pa.agreement_no
				           when ol.price_source_type in ('REQUISITION_APPROVED', 'REQUISITION') then rq.requisition_no
				           else null
				       end as price_source_no,
				       round(eps.remaining_quantity * ol.tax_excluded_unit_price, 2) as tax_excluded_amount
				from proc_effective_purchase_supply eps
				join proc_purchase_order o on o.id = eps.order_id
				join proc_purchase_order_line ol on ol.id = eps.order_line_id
				join mst_supplier sup on sup.id = o.supplier_id
				join mst_material m on m.id = eps.material_id
				left join proc_purchase_order_schedule s on s.id = eps.schedule_id
				left join sal_project p on p.id = eps.project_id
				left join proc_purchase_requisition_line rl on rl.id = ol.source_requisition_line_id
				left join proc_purchase_requisition rq on rq.id = rl.requisition_id
				left join proc_supplier_quote_line ql on ql.id = ol.source_quote_line_id
				left join proc_supplier_quote sq on sq.id = ql.quote_id
				left join proc_price_agreement_line pal on pal.id = ol.price_agreement_line_id
				left join proc_price_agreement pa on pa.id = pal.agreement_id
				%s
				order by eps.expected_arrival_date nulls last, eps.order_id, eps.order_line_id, eps.schedule_id
				limit ? offset ?
				""".formatted(where), (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			Long scheduleId = nullableLong(rs, "schedule_id");
			row.put("id", scheduleId == null ? rs.getLong("order_line_id") : scheduleId);
			row.put("sourceId", scheduleId == null ? rs.getLong("order_line_id") : scheduleId);
			row.put("orderId", rs.getLong("order_id"));
			row.put("orderNo", rs.getString("order_no"));
			row.put("orderLineId", rs.getLong("order_line_id"));
			row.put("scheduleId", scheduleId);
			row.put("scheduleSeq", nullableLong(rs, "schedule_seq"));
			row.put("sourceType", scheduleId == null ? "ORDER_LINE" : "SCHEDULE");
			row.put("supplyType", rs.getString("source_type"));
			row.put("ownershipType", rs.getString("purchase_mode"));
			row.put("procurementMode", rs.getString("purchase_mode"));
			row.put("supplierId", rs.getLong("supplier_id"));
			row.put("supplierName", rs.getString("supplier_name"));
			row.put("projectId", nullableLong(rs, "project_id"));
			row.put("projectCode", rs.getString("project_code"));
			row.put("projectName", rs.getString("project_name"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("unitId", rs.getLong("unit_id"));
			row.put("expectedArrivalDate", rs.getObject("expected_arrival_date", LocalDate.class));
			row.put("remainingQuantity", decimalString(rs.getBigDecimal("remaining_quantity")));
			row.put("countedAsEffectiveSupply", true);
			row.put("notCountedReason", null);
			row.put("status", rs.getString("order_status"));
			row.put("scheduleStatus", rs.getString("schedule_status"));
			row.put("priceSourceType", rs.getString("price_source_type"));
			row.put("priceSourceNo", rs.getString("price_source_no"));
			row.put("sourceNo", rs.getString("price_source_no"));
			row.put("allowedActions", List.of());
			if (currentUser.permissions().contains("inventory:valuation:view")) {
				row.put("currency", "CNY");
				row.put("taxExcludedAmount", decimalString(rs.getBigDecimal("tax_excluded_amount")));
			}
			return row;
		}, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	private PageResponse<Map<String, Object>> ineffectiveSupplies(Long projectId, Long materialId, Long supplierId,
			String procurementMode, String status, LocalDate expectedDateFrom, LocalDate expectedDateTo, int page,
			int pageSize, CurrentUser currentUser) {
		List<Object> args = new ArrayList<>();
		List<String> conditions = new ArrayList<>();
		String expectedDateExpression = "coalesce(s.planned_date, ol.expected_arrival_date, o.expected_arrival_date)";
		String remainingExpression = "coalesce(s.planned_quantity - s.received_quantity, ol.quantity - ol.received_quantity)";
		if (projectId != null) {
			conditions.add("o.project_id = ?");
			args.add(projectId);
		}
		if (materialId != null) {
			conditions.add("ol.material_id = ?");
			args.add(materialId);
		}
		if (supplierId != null) {
			conditions.add("o.supplier_id = ?");
			args.add(supplierId);
		}
		if (hasText(procurementMode)) {
			conditions.add("o.purchase_mode = ?");
			args.add(parseMode(procurementMode.trim()).name());
		}
		if (hasText(status)) {
			conditions.add("(o.status = ? or coalesce(s.status, 'PLANNED') = ?)");
			args.add(status.trim());
			args.add(status.trim());
		}
		if (expectedDateFrom != null) {
			conditions.add(expectedDateExpression + " >= ?");
			args.add(expectedDateFrom);
		}
		if (expectedDateTo != null) {
			conditions.add(expectedDateExpression + " <= ?");
			args.add(expectedDateTo);
		}
		conditions.add(remainingExpression + " > 0");
		conditions.add("""
				not (
					o.status in ('CONFIRMED', 'PARTIALLY_RECEIVED')
					and coalesce(s.status, 'PLANNED') in ('PLANNED', 'PARTIALLY_RECEIVED')
				)
				""");
		String where = "where " + String.join(" and ", conditions);
		Long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from proc_purchase_order o
				join proc_purchase_order_line ol on ol.order_id = o.id
				left join proc_purchase_order_schedule s on s.order_line_id = ol.id
				%s
				""".formatted(where), Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<Map<String, Object>> items = this.jdbcTemplate.query("""
				select o.id as order_id, o.order_no, ol.id as order_line_id, s.id as schedule_id,
				       s.line_no as schedule_seq, o.purchase_mode, o.project_id,
				       p.project_no as project_code, p.name as project_name, ol.material_id,
				       m.code as material_code, m.name as material_name, ol.unit_id,
				       %s as expected_arrival_date, %s as remaining_quantity, o.status as order_status,
				       coalesce(s.status, 'PLANNED') as schedule_status, 'PURCHASE_ORDER' as source_type,
				       o.supplier_id, sup.name as supplier_name, ol.price_source_type,
				       case
				           when ol.price_source_type in ('QUOTE_SELECTION', 'SUPPLIER_QUOTE', 'QUOTE') then sq.quote_no
				           when ol.price_source_type in ('AGREEMENT', 'PRICE_AGREEMENT') then pa.agreement_no
				           when ol.price_source_type in ('REQUISITION_APPROVED', 'REQUISITION') then rq.requisition_no
				           else null
				       end as price_source_no,
				       round(%s * ol.tax_excluded_unit_price, 2) as tax_excluded_amount
				from proc_purchase_order o
				join proc_purchase_order_line ol on ol.order_id = o.id
				left join proc_purchase_order_schedule s on s.order_line_id = ol.id
				join mst_supplier sup on sup.id = o.supplier_id
				join mst_material m on m.id = ol.material_id
				left join sal_project p on p.id = o.project_id
				left join proc_purchase_requisition_line rl on rl.id = ol.source_requisition_line_id
				left join proc_purchase_requisition rq on rq.id = rl.requisition_id
				left join proc_supplier_quote_line ql on ql.id = ol.source_quote_line_id
				left join proc_supplier_quote sq on sq.id = ql.quote_id
				left join proc_price_agreement_line pal on pal.id = ol.price_agreement_line_id
				left join proc_price_agreement pa on pa.id = pal.agreement_id
				%s
				order by %s nulls last, o.id, ol.id, s.id
				limit ? offset ?
				""".formatted(expectedDateExpression, remainingExpression, remainingExpression, where,
				expectedDateExpression), (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			Long scheduleId = nullableLong(rs, "schedule_id");
			String orderStatus = rs.getString("order_status");
			String scheduleStatus = rs.getString("schedule_status");
			String effectiveStatus = scheduleId == null ? orderStatus : scheduleStatus;
			row.put("id", scheduleId == null ? rs.getLong("order_line_id") : scheduleId);
			row.put("sourceId", scheduleId == null ? rs.getLong("order_line_id") : scheduleId);
			row.put("orderId", rs.getLong("order_id"));
			row.put("orderNo", rs.getString("order_no"));
			row.put("orderLineId", rs.getLong("order_line_id"));
			row.put("scheduleId", scheduleId);
			row.put("scheduleSeq", nullableLong(rs, "schedule_seq"));
			row.put("sourceType", scheduleId == null ? "ORDER_LINE" : "SCHEDULE");
			row.put("supplyType", rs.getString("source_type"));
			row.put("ownershipType", rs.getString("purchase_mode"));
			row.put("procurementMode", rs.getString("purchase_mode"));
			row.put("supplierId", rs.getLong("supplier_id"));
			row.put("supplierName", rs.getString("supplier_name"));
			row.put("projectId", nullableLong(rs, "project_id"));
			row.put("projectCode", rs.getString("project_code"));
			row.put("projectName", rs.getString("project_name"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("unitId", rs.getLong("unit_id"));
			row.put("expectedArrivalDate", rs.getObject("expected_arrival_date", LocalDate.class));
			row.put("remainingQuantity", decimalString(rs.getBigDecimal("remaining_quantity")));
			row.put("countedAsEffectiveSupply", false);
			row.put("notCountedReason", "STATUS_" + effectiveStatus);
			row.put("status", effectiveStatus);
			row.put("scheduleStatus", scheduleStatus);
			row.put("priceSourceType", rs.getString("price_source_type"));
			row.put("priceSourceNo", rs.getString("price_source_no"));
			row.put("sourceNo", rs.getString("price_source_no"));
			row.put("allowedActions", List.of());
			if (currentUser.permissions().contains("inventory:valuation:view")) {
				row.put("currency", "CNY");
				row.put("taxExcludedAmount", decimalString(rs.getBigDecimal("tax_excluded_amount")));
			}
			return row;
		}, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total == null ? 0 : total);
	}

	@Transactional
	public void activatePriceAgreementFromApproval(Long id, Long expectedVersion, CurrentUser operator) {
		AgreementRow row = lockAgreement(id);
		requireVersion(row.version(), expectedVersion);
		if (!"SUBMITTED".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_STATUS_INVALID);
		}
		assertNoActiveAgreementOverlap(id);
		this.jdbcTemplate.update("""
				update proc_price_agreement
				set status = 'ACTIVE', activated_by = ?, activated_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional
	public void reopenPriceAgreementAfterApprovalTerminal(Long id, CurrentUser operator) {
		this.jdbcTemplate.update("""
				update proc_price_agreement
				set status = 'DRAFT', approval_instance_id = null, updated_by = ?, updated_at = ?
				where id = ?
				and status = 'SUBMITTED'
				""", operator.username(), OffsetDateTime.now(), id);
	}

	private void assertNoActiveAgreementOverlap(Long agreementId) {
		Boolean overlap = this.jdbcTemplate.queryForObject("""
				select exists (
					select 1
					from proc_price_agreement current_agreement
					join proc_price_agreement_line current_line on current_line.agreement_id = current_agreement.id
					join proc_price_agreement active_agreement
					  on active_agreement.id <> current_agreement.id
					 and active_agreement.status = 'ACTIVE'
					 and active_agreement.supplier_id = current_agreement.supplier_id
					 and active_agreement.purchase_mode = current_agreement.purchase_mode
					 and coalesce(active_agreement.project_id, 0) = coalesce(current_agreement.project_id, 0)
					 and active_agreement.priority = current_agreement.priority
					 and active_agreement.valid_from <= current_agreement.valid_to
					 and active_agreement.valid_to >= current_agreement.valid_from
					join proc_price_agreement_line active_line
					  on active_line.agreement_id = active_agreement.id
					 and active_line.material_id = current_line.material_id
					where current_agreement.id = ?
				)
				""", Boolean.class, agreementId);
		if (Boolean.TRUE.equals(overlap)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_OVERLAP);
		}
	}

	private void insertInquiryLine(Long inquiryId, PurchaseInquiryLineRequest request, OffsetDateTime now) {
		ProcurementRequisitionService.RequisitionLineSource source = null;
		if (request.requisitionLineId() != null) {
			source = sourceLine(request.requisitionLineId());
			if (source.status() != PurchaseRequisitionStatus.APPROVED
					&& source.status() != PurchaseRequisitionStatus.PARTIALLY_ORDERED) {
				throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_STATUS_INVALID);
			}
		}
		Long materialId = source == null ? request.materialId() : source.materialId();
		Long unitId = source == null ? request.unitId() : source.unitId();
		this.jdbcTemplate.update("""
				insert into proc_purchase_inquiry_line (
					inquiry_id, line_no, requisition_line_id, material_id, unit_id, quantity, required_date,
					created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", inquiryId, request.lineNo(), request.requisitionLineId(), materialId, unitId,
				positive(request.quantity()), request.requiredDate(), now, now);
	}

	private void insertQuoteLine(Long quoteId, SupplierQuoteLineRequest request, OffsetDateTime now) {
		InquiryLineRow inquiryLine = inquiryLine(request.inquiryLineId());
		BigDecimal taxExcluded = unitPrice(request.taxExcludedUnitPrice());
		BigDecimal taxIncluded = unitPrice(request.taxIncludedUnitPrice());
		BigDecimal quantity = positive(request.quantity());
		this.jdbcTemplate.update("""
				insert into proc_supplier_quote_line (
					quote_id, inquiry_line_id, line_no, material_id, unit_id, min_purchase_quantity, quantity,
					tax_rate, tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount,
					tax_included_amount, delivery_date, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", quoteId, request.inquiryLineId(), request.lineNo(), inquiryLine.materialId(),
				inquiryLine.unitId(), quantity, nonNegative(request.taxRate()), taxExcluded, taxIncluded,
				amount(quantity, taxExcluded), amount(quantity, taxIncluded), request.deliveryDate(), now, now);
	}

	private void insertAgreementLine(Long agreementId, PriceAgreementLineRequest request, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into proc_price_agreement_line (
					agreement_id, line_no, material_id, unit_id, min_purchase_quantity, tax_rate,
					tax_excluded_unit_price, tax_included_unit_price, created_at, updated_at
				)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", agreementId, request.lineNo(), request.materialId(), request.unitId(),
				nonNegative(request.minimumQuantity()), nonNegative(request.taxRate()),
				unitPrice(request.taxExcludedUnitPrice()), unitPrice(request.taxIncludedUnitPrice()), now, now);
	}

	private List<Map<String, Object>> inquiryLines(Long inquiryId) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.requisition_line_id, l.material_id, m.code as material_code,
				       m.name as material_name, l.unit_id, u.name as unit_name, l.quantity, l.required_date,
				       r.requisition_no,
				       coalesce(r.purchase_mode, i.purchase_mode) as source_purchase_mode,
				       coalesce(r.project_id, i.project_id) as source_project_id,
				       p.project_no as project_code, p.name as project_name
				from proc_purchase_inquiry_line l
				join proc_purchase_inquiry i on i.id = l.inquiry_id
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				left join proc_purchase_requisition_line rl on rl.id = l.requisition_line_id
				left join proc_purchase_requisition r on r.id = rl.requisition_id
				left join sal_project p on p.id = coalesce(r.project_id, i.project_id)
				where l.inquiry_id = ?
				order by l.line_no, l.id
				""", (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("lineNo", rs.getInt("line_no"));
			row.put("requisitionLineId", nullableLong(rs, "requisition_line_id"));
			row.put("requisitionNo", rs.getString("requisition_no"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("unitId", rs.getLong("unit_id"));
			row.put("unitName", rs.getString("unit_name"));
			row.put("quantity", decimalString(rs.getBigDecimal("quantity")));
			row.put("requiredDate", rs.getObject("required_date", LocalDate.class));
			row.put("purchaseMode", rs.getString("source_purchase_mode"));
			row.put("procurementMode", rs.getString("source_purchase_mode"));
			row.put("ownershipType", rs.getString("source_purchase_mode"));
			row.put("projectId", nullableLong(rs, "source_project_id"));
			row.put("projectCode", rs.getString("project_code"));
			row.put("projectName", rs.getString("project_name"));
			return row;
		}, inquiryId);
	}

	private List<Map<String, Object>> quoteLines(Long quoteId) {
		return this.jdbcTemplate.query("""
				select ql.id, ql.line_no, ql.inquiry_line_id, ql.material_id, m.code as material_code,
				       m.name as material_name, ql.unit_id, ql.min_purchase_quantity, ql.quantity, ql.tax_rate,
				       tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_included_amount,
				       ql.delivery_date,
				       coalesce(r.purchase_mode, i.purchase_mode) as source_purchase_mode,
				       coalesce(r.project_id, i.project_id) as source_project_id,
				       p.project_no as project_code,
				       p.name as project_name
				from proc_supplier_quote_line ql
				join proc_supplier_quote q on q.id = ql.quote_id
				join proc_purchase_inquiry i on i.id = q.inquiry_id
				join proc_purchase_inquiry_line il on il.id = ql.inquiry_line_id
				left join proc_purchase_requisition_line rl on rl.id = il.requisition_line_id
				left join proc_purchase_requisition r on r.id = rl.requisition_id
				join mst_material m on m.id = ql.material_id
				left join sal_project p on p.id = coalesce(r.project_id, i.project_id)
				where ql.quote_id = ?
				order by ql.line_no, ql.id
				""", (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("lineNo", rs.getInt("line_no"));
			row.put("inquiryLineId", rs.getLong("inquiry_line_id"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("unitId", rs.getLong("unit_id"));
			row.put("minPurchaseQuantity", decimalString(rs.getBigDecimal("min_purchase_quantity")));
			row.put("quantity", decimalString(rs.getBigDecimal("quantity")));
			row.put("taxRate", decimalString(rs.getBigDecimal("tax_rate")));
			row.put("taxExcludedUnitPrice", decimalString(rs.getBigDecimal("tax_excluded_unit_price")));
			row.put("taxIncludedUnitPrice", decimalString(rs.getBigDecimal("tax_included_unit_price")));
			row.put("taxExcludedAmount", decimalString(rs.getBigDecimal("tax_excluded_amount")));
			row.put("taxIncludedAmount", decimalString(rs.getBigDecimal("tax_included_amount")));
			row.put("deliveryDate", rs.getObject("delivery_date", LocalDate.class));
			row.put("purchaseMode", rs.getString("source_purchase_mode"));
			row.put("procurementMode", rs.getString("source_purchase_mode"));
			row.put("ownershipType", rs.getString("source_purchase_mode"));
			row.put("projectId", nullableLong(rs, "source_project_id"));
			row.put("projectCode", rs.getString("project_code"));
			row.put("projectName", rs.getString("project_name"));
			return row;
		}, quoteId);
	}

	private List<Map<String, Object>> agreementLines(AgreementRow agreement) {
		return this.jdbcTemplate.query("""
				select l.id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, l.min_purchase_quantity, l.tax_rate,
				       l.tax_excluded_unit_price, l.tax_included_unit_price
				from proc_price_agreement_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.agreement_id = ?
				order by l.line_no, l.id
				""", (rs, rowNum) -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", rs.getLong("id"));
			row.put("lineNo", rs.getInt("line_no"));
			row.put("materialId", rs.getLong("material_id"));
			row.put("materialCode", rs.getString("material_code"));
			row.put("materialName", rs.getString("material_name"));
			row.put("unitId", rs.getLong("unit_id"));
			row.put("unitName", rs.getString("unit_name"));
			row.put("minimumQuantity", decimalString(rs.getBigDecimal("min_purchase_quantity")));
			row.put("taxRate", decimalString(rs.getBigDecimal("tax_rate")));
			row.put("taxExcludedUnitPrice", decimalString(rs.getBigDecimal("tax_excluded_unit_price")));
			row.put("taxIncludedUnitPrice", decimalString(rs.getBigDecimal("tax_included_unit_price")));
			row.put("purchaseMode", agreement.purchaseMode());
			row.put("procurementMode", agreement.purchaseMode());
			row.put("ownershipType", agreement.purchaseMode());
			row.put("projectId", agreement.projectId());
			row.put("projectCode", agreement.projectCode());
			row.put("projectName", agreement.projectName());
			return row;
		}, agreement.id());
	}

	private Optional<InquiryRow> inquiryRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, inquiry_no, purchase_mode, project_id, status, title, version
				from proc_purchase_inquiry
				where id = ?
				""", this::mapInquiry, id).stream().findFirst();
	}

	private InquiryRow lockInquiry(Long id) {
		return this.jdbcTemplate.query("""
				select id, inquiry_no, purchase_mode, project_id, status, title, version
				from proc_purchase_inquiry
				where id = ?
				for update
				""", this::mapInquiry, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
	}

	private Optional<QuoteRow> quoteRow(Long id) {
		return this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.inquiry_id, i.inquiry_no, q.supplier_id, s.code as supplier_code,
				       s.name as supplier_name, q.status, q.valid_from, q.valid_to, q.currency, q.remark,
				       q.version, i.purchase_mode, i.project_id, p.project_no as project_code, p.name as project_name
				from proc_supplier_quote q
				join proc_purchase_inquiry i on i.id = q.inquiry_id
				join mst_supplier s on s.id = q.supplier_id
				left join sal_project p on p.id = i.project_id
				where q.id = ?
				""", this::mapQuoteRow, id)
			.stream()
			.findFirst();
	}

	private QuoteRow lockQuote(Long id) {
		return this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.inquiry_id, i.inquiry_no, q.supplier_id, s.code as supplier_code,
				       s.name as supplier_name, q.status, q.valid_from, q.valid_to, q.currency, q.remark,
				       q.version, i.purchase_mode, i.project_id, p.project_no as project_code, p.name as project_name
				from proc_supplier_quote q
				join proc_purchase_inquiry i on i.id = q.inquiry_id
				join mst_supplier s on s.id = q.supplier_id
				left join sal_project p on p.id = i.project_id
				where q.id = ?
				for update of q
				""", this::mapQuoteRow, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_NOT_FOUND));
	}

	private Optional<AgreementRow> agreementRow(Long id) {
		return this.jdbcTemplate.query("""
				select a.id, a.agreement_no, a.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       a.purchase_mode, a.project_id, p.project_no as project_code, p.name as project_name,
				       a.status, a.approval_instance_id, a.currency, a.valid_from, a.valid_to, a.version
				from proc_price_agreement a
				join mst_supplier s on s.id = a.supplier_id
				left join sal_project p on p.id = a.project_id
				where a.id = ?
				""", this::mapAgreement, id).stream().findFirst();
	}

	private AgreementRow lockAgreement(Long id) {
		return this.jdbcTemplate.query("""
				select a.id, a.agreement_no, a.supplier_id, s.code as supplier_code, s.name as supplier_name,
				       a.purchase_mode, a.project_id, p.project_no as project_code, p.name as project_name,
				       a.status, a.approval_instance_id, a.currency, a.valid_from, a.valid_to, a.version
				from proc_price_agreement a
				join mst_supplier s on s.id = a.supplier_id
				left join sal_project p on p.id = a.project_id
				where a.id = ?
				for update of a
				""", this::mapAgreement, id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_PRICE_AGREEMENT_NOT_FOUND));
	}

	private InquiryLineRow inquiryLine(Long id) {
		return this.jdbcTemplate.query("""
				select id, inquiry_id, material_id, unit_id, quantity
				from proc_purchase_inquiry_line
				where id = ?
				""", (rs, rowNum) -> new InquiryLineRow(rs.getLong("id"), rs.getLong("inquiry_id"),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity")), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
	}

	private ProcurementRequisitionService.RequisitionLineSource sourceLine(Long id) {
		return this.jdbcTemplate.query("""
				select rl.id, rl.requisition_id, r.purchase_mode, r.project_id, r.status, rl.material_id, rl.unit_id,
				       rl.quantity, rl.ordered_quantity
				from proc_purchase_requisition_line rl
				join proc_purchase_requisition r on r.id = rl.requisition_id
				where rl.id = ?
				""", (rs, rowNum) -> new ProcurementRequisitionService.RequisitionLineSource(rs.getLong("id"),
				rs.getLong("requisition_id"), PurchaseMode.valueOf(rs.getString("purchase_mode")),
				nullableLong(rs, "project_id"), PurchaseRequisitionStatus.valueOf(rs.getString("status")),
				rs.getLong("material_id"), rs.getLong("unit_id"), rs.getBigDecimal("quantity"),
				rs.getBigDecimal("ordered_quantity")), id).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_NOT_FOUND));
	}

	private Optional<QuoteSelection> quoteSelection(Long quoteLineId) {
		return this.jdbcTemplate.query("""
				select ql.id as quote_line_id, q.id as quote_id, q.inquiry_id, ql.inquiry_line_id,
				       q.supplier_id, ql.tax_excluded_unit_price
				from proc_supplier_quote_line ql
				join proc_supplier_quote q on q.id = ql.quote_id
				where ql.id = ?
				""", this::mapQuoteSelection, quoteLineId).stream().findFirst();
	}

	private QuoteSelection lowestQuoteSelection(Long inquiryLineId) {
		return this.jdbcTemplate.query("""
				select ql.id as quote_line_id, q.id as quote_id, q.inquiry_id, ql.inquiry_line_id,
				       q.supplier_id, ql.tax_excluded_unit_price
				from proc_supplier_quote_line ql
				join proc_supplier_quote q on q.id = ql.quote_id
				where ql.inquiry_line_id = ?
				and q.status in ('VALID', 'SELECTED')
				order by ql.tax_excluded_unit_price asc, ql.id asc
				limit 1
				""", this::mapQuoteSelection, inquiryLineId).stream().findFirst().orElse(null);
	}

	private InquiryRow mapInquiry(ResultSet rs, int rowNum) throws SQLException {
		return new InquiryRow(rs.getLong("id"), rs.getString("inquiry_no"), rs.getString("purchase_mode"),
				nullableLong(rs, "project_id"), rs.getString("status"), rs.getString("title"),
				rs.getLong("version"));
	}

	private AgreementRow mapAgreement(ResultSet rs, int rowNum) throws SQLException {
		return new AgreementRow(rs.getLong("id"), rs.getString("agreement_no"), rs.getLong("supplier_id"),
				rs.getString("supplier_code"), rs.getString("supplier_name"), rs.getString("purchase_mode"),
				nullableLong(rs, "project_id"), rs.getString("project_code"), rs.getString("project_name"),
				rs.getString("status"), nullableLong(rs, "approval_instance_id"), rs.getString("currency"),
				rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class),
				rs.getLong("version"));
	}

	private QuoteRow mapQuoteRow(ResultSet rs, int rowNum) throws SQLException {
		return new QuoteRow(rs.getLong("id"), rs.getString("quote_no"), rs.getLong("inquiry_id"),
				rs.getString("inquiry_no"), rs.getLong("supplier_id"), rs.getString("supplier_code"),
				rs.getString("supplier_name"), rs.getString("status"), rs.getObject("valid_from", LocalDate.class),
				rs.getObject("valid_to", LocalDate.class), rs.getString("currency"), rs.getString("remark"),
				rs.getLong("version"), rs.getString("purchase_mode"), nullableLong(rs, "project_id"),
				rs.getString("project_code"), rs.getString("project_name"));
	}

	private QuoteSelection mapQuoteSelection(ResultSet rs, int rowNum) throws SQLException {
		return new QuoteSelection(rs.getLong("quote_line_id"), rs.getLong("quote_id"), rs.getLong("inquiry_id"),
				rs.getLong("inquiry_line_id"), rs.getLong("supplier_id"),
				rs.getBigDecimal("tax_excluded_unit_price"));
	}

	private List<SupplierQuoteLineRequest> quoteLineRequests(Long inquiryId, SupplierQuoteRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.lines() != null && !request.lines().isEmpty()) {
			return request.lines();
		}
		if (request.materialId() == null || request.quantity() == null || request.taxRate() == null
				|| request.taxExcludedUnitPrice() == null || request.taxIncludedUnitPrice() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Long inquiryLineId = inquiryLineIdByMaterial(inquiryId, request.materialId());
		return List.of(new SupplierQuoteLineRequest(1, inquiryLineId, request.quantity(), request.taxRate(),
				request.taxExcludedUnitPrice(), request.taxIncludedUnitPrice(), request.deliveryDate(),
				request.currency()));
	}

	private List<PriceAgreementLineRequest> agreementLineRequests(PriceAgreementRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.lines() != null && !request.lines().isEmpty()) {
			return request.lines();
		}
		if (request.materialId() == null || request.taxRate() == null || request.taxExcludedUnitPrice() == null
				|| request.taxIncludedUnitPrice() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		Long unitId = request.unitId() == null ? materialUnitId(request.materialId()) : request.unitId();
		return List.of(new PriceAgreementLineRequest(1, request.materialId(), unitId, request.minimumQuantity(),
				request.taxRate(), request.taxExcludedUnitPrice(), request.taxIncludedUnitPrice(), request.currency()));
	}

	private Long inquiryLineIdByMaterial(Long inquiryId, Long materialId) {
		return this.jdbcTemplate.query("""
				select id
				from proc_purchase_inquiry_line
				where inquiry_id = ?
				and material_id = ?
				order by line_no asc, id asc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id"), inquiryId, materialId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND));
	}

	private Long materialUnitId(Long materialId) {
		return this.jdbcTemplate.query("""
				select unit_id
				from mst_material
				where id = ?
				and status = 'ENABLED'
				""", (rs, rowNum) -> rs.getLong("unit_id"), materialId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_MATERIAL_INVALID));
	}

	private Long firstQuoteLineId(Long quoteId) {
		return this.jdbcTemplate.query("""
				select id
				from proc_supplier_quote_line
				where quote_id = ?
				order by line_no asc, id asc
				limit 1
				""", (rs, rowNum) -> rs.getLong("id"), quoteId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROCUREMENT_QUOTE_NOT_FOUND));
	}

	private List<String> inquiryAllowedActions(String status) {
		return switch (status) {
			case "DRAFT" -> List.of("UPDATE", "RELEASE", "CANCEL");
			case "RELEASED" -> List.of("COMPLETE", "CANCEL");
			case "COMPLETED" -> List.of("SELECT_QUOTE");
			case "AWARDED", "CANCELLED" -> List.of();
			default -> List.of();
		};
	}

	private List<String> quoteAllowedActions(String status) {
		return switch (status) {
			case "VALID" -> List.of("UPDATE", "SELECT", "CANCEL");
			case "SELECTED", "REJECTED", "EXPIRED", "CANCELLED", "DRAFT" -> List.of();
			default -> List.of();
		};
	}

	private List<String> agreementAllowedActions(String status) {
		return switch (status) {
			case "DRAFT" -> List.of("UPDATE", "SUBMIT", "CANCEL");
			case "ACTIVE" -> List.of("DISABLE");
			case "SUBMITTED", "DISABLED", "EXPIRED", "CANCELLED" -> List.of();
			default -> List.of();
		};
	}

	private String approvalStatus(String status, Long approvalInstanceId) {
		return approvalInstanceId == null ? null : status;
	}

	private Map<String, Object> idempotentAction(String action, String resourceType, Long resourceId,
			VersionedActionRequest request, CurrentUser operator, Supplier<Map<String, Object>> existingResult,
			Supplier<Map<String, Object>> callback) {
		VersionedActionRequest actionRequest = requireActionRequest(request);
		String fingerprint = this.actionIdempotencyService.fingerprint(action, resourceType, resourceId,
				actionRequest.version(), actionRequest.reason());
		Optional<ProcurementActionIdempotencyService.ResultRecord> existing = this.actionIdempotencyService
			.existing(action, resourceType, resourceId, actionRequest.idempotencyKey(), fingerprint, operator);
		if (existing.isPresent()) {
			return existingResult.get();
		}
		Map<String, Object> result = callback.get();
		Long resultId = result.get("id") instanceof Number number ? number.longValue() : resourceId;
		Long resultVersion = result.get("version") instanceof Number number ? number.longValue() : null;
		this.actionIdempotencyService.record(action, resourceType, resourceId, actionRequest.version(),
				actionRequest.idempotencyKey(), fingerprint, resourceType, resultId, resultVersion, operator);
		return result;
	}

	private VersionedActionRequest requireActionRequest(VersionedActionRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return request;
	}

	private void requireEnabledSupplier(Long supplierId) {
		Integer count = this.jdbcTemplate.queryForObject(
				"select count(*) from mst_supplier where id = ? and status = 'ENABLED'", Integer.class, supplierId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_SUPPLIER_INVALID);
		}
	}

	private void requireActiveProject(Long projectId) {
		if (projectId == null) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_PROJECT_REQUIRED);
		}
		List<String> statuses = this.jdbcTemplate.query("select status from sal_project where id = ?",
				(rs, rowNum) -> rs.getString("status"), projectId);
		if (statuses.isEmpty() || !"ACTIVE".equals(statuses.getFirst())) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_REQUISITION_PROJECT_INVALID);
		}
	}

	private PurchaseMode parseMode(String value) {
		try {
			return value == null ? PurchaseMode.PUBLIC : PurchaseMode.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateCurrency(String currency) {
		if (currency != null && !"CNY".equals(currency)) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_CURRENCY_UNSUPPORTED);
		}
	}

	private void requireVersion(Long actual, Long expected) {
		if (expected == null || !actual.equals(expected)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private BigDecimal positive(BigDecimal value) {
		if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.scale() > 6) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_QUANTITY_INVALID);
		}
		return value;
	}

	private BigDecimal nonNegative(BigDecimal value) {
		if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.scale() > 6) {
			throw new BusinessException(ApiErrorCode.PROCUREMENT_UNIT_PRICE_INVALID);
		}
		return value;
	}

	private BigDecimal unitPrice(BigDecimal value) {
		return nonNegative(value);
	}

	private BigDecimal amount(BigDecimal quantity, BigDecimal unitPrice) {
		return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
	}

	private String requiredText(String value, int maxLength) {
		String text = blankToNull(value);
		if (text == null || text.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return text;
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String decimalString(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record PurchaseInquiryRequest(String title, String purchaseMode, String procurementMode,
			String ownershipType, Long projectId, LocalDate quotationDeadline, String compareBasis, String currency,
			String remark, Long version, String idempotencyKey, List<Long> supplierIds,
			@Valid List<PurchaseInquiryLineRequest> lines) {

		@Override
		public String purchaseMode() {
			if (purchaseMode != null && !purchaseMode.isBlank()) {
				return purchaseMode;
			}
			if (procurementMode != null && !procurementMode.isBlank()) {
				return procurementMode;
			}
			return ownershipType;
		}
	}

	public record PurchaseInquiryLineRequest(@NotNull Integer lineNo, Long requisitionId, Long requisitionLineId,
			Long materialId, Long unitId, @NotNull BigDecimal quantity, LocalDate requiredDate, String remark) {
	}

	public record SupplierQuoteRequest(@NotNull Long supplierId, Long materialId, BigDecimal quantity,
			BigDecimal minPurchaseQuantity, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount, BigDecimal taxIncludedAmount,
			String currency, LocalDate validFrom, LocalDate validTo, LocalDate deliveryDate, String remark,
			Long version, String idempotencyKey,
			@Valid List<SupplierQuoteLineRequest> lines) {

		@Override
		public LocalDate validFrom() {
			return validFrom == null ? LocalDate.now() : validFrom;
		}

		@Override
		public LocalDate validTo() {
			return validTo == null ? LocalDate.now().plusDays(30) : validTo;
		}
	}

	public record SupplierQuoteLineRequest(@NotNull Integer lineNo, @NotNull Long inquiryLineId,
			@NotNull BigDecimal quantity, @NotNull BigDecimal taxRate, @NotNull BigDecimal taxExcludedUnitPrice,
			@NotNull BigDecimal taxIncludedUnitPrice, LocalDate deliveryDate, String currency) {
	}

	public record PriceAwardRequest(@NotNull Long selectedQuoteLineId, String nonLowestReason, String idempotencyKey) {
	}

	public record PriceAgreementRequest(@NotNull Long supplierId, Long materialId, Long unitId,
			BigDecimal minimumQuantity, BigDecimal minPurchaseQuantity, BigDecimal taxRate,
			BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice, String purchaseMode,
			String procurementMode, String ownershipType, Long projectId, String currency, @NotNull LocalDate validFrom,
			@NotNull LocalDate validTo, String remark, Long version, String idempotencyKey,
			@Valid List<PriceAgreementLineRequest> lines) {

		@Override
		public String purchaseMode() {
			if (purchaseMode != null && !purchaseMode.isBlank()) {
				return purchaseMode;
			}
			if (procurementMode != null && !procurementMode.isBlank()) {
				return procurementMode;
			}
			return ownershipType;
		}

		@Override
		public BigDecimal minimumQuantity() {
			return minimumQuantity == null ? minPurchaseQuantity : minimumQuantity;
		}
	}

	public record PriceAgreementLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, @NotNull Long unitId,
			@NotNull BigDecimal minimumQuantity, @NotNull BigDecimal taxRate,
			@NotNull BigDecimal taxExcludedUnitPrice, @NotNull BigDecimal taxIncludedUnitPrice, String currency) {
	}

	public record VersionedActionRequest(Long version, String reason, String idempotencyKey) {
	}

	private record InquiryRow(Long id, String inquiryNo, String purchaseMode, Long projectId, String status,
			String title, Long version) {
	}

	private record InquiryLineRow(Long id, Long inquiryId, Long materialId, Long unitId, BigDecimal quantity) {
	}

	private record QuoteRow(Long id, String quoteNo, Long inquiryId, String inquiryNo, Long supplierId,
			String supplierCode, String supplierName, String status, LocalDate validFrom, LocalDate validTo,
			String currency, String remark, Long version, String purchaseMode, Long projectId, String projectCode,
			String projectName) {
	}

	private record AgreementRow(Long id, String agreementNo, Long supplierId, String supplierCode,
			String supplierName, String purchaseMode, Long projectId, String projectCode, String projectName,
			String status, Long approvalInstanceId, String currency, LocalDate validFrom, LocalDate validTo,
			Long version) {
	}

	private record QuoteSelection(Long quoteLineId, Long quoteId, Long inquiryId, Long inquiryLineId,
			Long supplierId, BigDecimal taxExcludedUnitPrice) {
	}

}
