package com.qherp.api.system.sales;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.platform.PlatformApprovalService;
import com.qherp.api.system.salesproject.SalesOrderProjectLinkService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SalesQuoteService {

	private static final String QUOTE_TARGET = "SALES_QUOTE";

	private static final String ORDER_TARGET = "SALES_ORDER";

	private static final String CONTRACT_TARGET = "SALES_PROJECT_CONTRACT";

	private static final Set<String> SELLABLE_MATERIAL_TYPES = Set.of("FINISHED_GOOD", "SEMI_FINISHED");

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger QUOTE_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger CONTRACT_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final SalesOrderProjectLinkService salesOrderProjectLinkService;

	private final PlatformApprovalService approvalService;

	public SalesQuoteService(JdbcTemplate jdbcTemplate, AuditService auditService,
			SalesOrderProjectLinkService salesOrderProjectLinkService,
			@Lazy PlatformApprovalService approvalService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.salesOrderProjectLinkService = salesOrderProjectLinkService;
		this.approvalService = approvalService;
	}

	@Transactional
	public SalesQuoteDetailResponse create(QuoteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedQuote quote = validateQuoteRequest(request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument created = insertQuoteWithRetry(quote, operator.username(), now);
			insertQuoteLines(created.id(), quote.lines(), now);
			this.auditService.record(operator, "SALES_QUOTE_CREATE", QUOTE_TARGET, created.id(),
					created.documentNo(), servletRequest);
			return quote(created.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateQuoteException(exception);
		}
	}

	@Transactional(readOnly = true)
	public SalesQuoteDetailResponse quote(Long id) {
		QuoteHeader header = quoteHeader(id).orElseThrow(this::quoteNotFound);
		return toQuoteResponse(header, quoteLines(id));
	}

	@Transactional
	public SalesQuoteDetailResponse update(Long id, QuoteRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ValidatedQuote quote = validateQuoteRequest(request);
		QuoteHeader row = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status()) || row.approvalInstanceId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		try {
			this.jdbcTemplate.update("""
					update sal_sales_quote
					set customer_id = ?, project_id = ?, contract_id = ?, quote_date = ?, valid_until = ?,
					    tax_excluded_amount = ?, tax_amount = ?, tax_included_amount = ?, remark = ?,
					    updated_by = ?, updated_at = ?, version = version + 1
					where id = ?
					""", quote.customer().id(), quote.projectLink() == null ? null : quote.projectLink().projectId(),
					quote.projectLink() == null ? null : quote.projectLink().contractId(), quote.quoteDate(),
					quote.validUntil(), quote.taxExcludedAmount(), quote.taxAmount(), quote.taxIncludedAmount(),
					quote.remark(), operator.username(), now, id);
			this.jdbcTemplate.update("delete from sal_sales_quote_line where quote_id = ?", id);
			insertQuoteLines(id, quote.lines(), now);
			this.auditService.record(operator, "SALES_QUOTE_UPDATE", QUOTE_TARGET, id, row.quoteNo(), servletRequest);
			return quote(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateQuoteException(exception);
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<SalesQuoteSummaryResponse> quotes(String keyword, Long customerId, Long projectId,
			String status, int page, int pageSize) {
		QueryParts query = quoteQueryParts(keyword, customerId, projectId, status);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				left join sal_project_contract pc on pc.id = q.contract_id
				%s
				""".formatted(query.where()), Long.class, query.args().toArray());
		List<Object> args = new ArrayList<>(query.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<SalesQuoteSummaryResponse> items = this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.customer_id, c.code as customer_code, c.name as customer_name,
				       q.project_id, p.project_no, p.name as project_name, q.contract_id, pc.contract_no,
				       pc.external_contract_no, q.quote_date, q.valid_until, q.status, q.currency,
				       q.tax_excluded_amount, q.tax_amount, q.tax_included_amount, q.approval_instance_id,
				       q.converted_order_id, q.converted_contract_id, q.remark, q.created_by, q.created_at,
				       q.updated_at, q.approved_by, q.approved_at, q.cancelled_by, q.cancelled_at, q.version
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				left join sal_project_contract pc on pc.id = q.contract_id
				%s
				order by q.updated_at desc, q.id desc
				limit ? offset ?
				""".formatted(query.where()), this::mapQuoteSummary, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional
	public PlatformApprovalService.ApprovalInstanceRecord submitApproval(Long id,
			PlatformApprovalService.ApprovalSubmitRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		PlatformApprovalService.ApprovalInstanceRecord idempotent = this.approvalService
			.idempotentSubmitResult("SALES_QUOTE_APPROVAL", id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		QuoteHeader row = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
		PlatformApprovalService.ApprovalInstanceRecord approval = this.approvalService.submitSalesQuoteApproval(id,
				request, operator, servletRequest);
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_quote
				set approval_instance_id = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				returning version
				""", Long.class, approval.id(), operator.username(), OffsetDateTime.now(), id);
		this.approvalService.updateBusinessObjectVersion(approval.id(), newVersion);
		this.auditService.record(operator, "SALES_QUOTE_SUBMIT", QUOTE_TARGET, id, row.quoteNo(), servletRequest);
		return this.approvalService.get(approval.id(), operator);
	}

	@Transactional
	public SalesQuoteDetailResponse cancel(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, true);
		SalesQuoteDetailResponse idempotent = idempotentQuoteResult("CANCEL", id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		QuoteHeader row = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(row.version(), request.version());
		if (!"DRAFT".equals(row.status()) || row.approvalInstanceId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Long newVersion = this.jdbcTemplate.queryForObject("""
				update sal_sales_quote
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				returning version
				""", Long.class, operator.username(), now, operator.username(), now, id);
		recordActionIdempotency("CANCEL", "SALES_QUOTE", id, request, "SALES_QUOTE", id, newVersion, operator);
		this.auditService.record(operator, "SALES_QUOTE_CANCEL", QUOTE_TARGET, id, row.quoteNo(), servletRequest);
		return quote(id);
	}

	@Transactional
	public void approveFromApproval(Long id, Long submittedVersion, CurrentUser operator,
			HttpServletRequest servletRequest) {
		QuoteHeader row = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(row.version(), submittedVersion);
		if (!"DRAFT".equals(row.status())) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_sales_quote
				set status = 'APPROVED', approved_by = ?, approved_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_QUOTE_APPROVE", QUOTE_TARGET, id, row.quoteNo(), servletRequest);
	}

	@Transactional
	public void reopenAfterApprovalTerminal(Long id, CurrentUser operator) {
		QuoteHeader row = lockQuote(id).orElseThrow(this::quoteNotFound);
		if (!"DRAFT".equals(row.status())) {
			return;
		}
		this.jdbcTemplate.update("""
				update sal_sales_quote
				set approval_instance_id = null, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), OffsetDateTime.now(), id);
	}

	@Transactional
	public ConvertedOrderResponse convertToOrder(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateActionRequest(request, false);
		ConvertedOrderResponse idempotent = idempotentConvertResult(id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		QuoteHeader quote = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(quote.version(), request.version());
		requireQuoteConvertible(quote);
		List<QuoteLineRow> lines = quoteLines(id);
		if (lines.isEmpty()) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_EMPTY_LINES);
		}
		SalesOrderProjectLinkService.ProjectLink targetLink = this.salesOrderProjectLinkService
			.validateForDraftSave(quote.customerId(),
					request.projectId() == null ? quote.projectId() : request.projectId(),
					request.contractId() == null ? quote.contractId() : request.contractId());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument order = insertOrderFromQuote(quote, targetLink, operator.username(), now);
			insertOrderLinesFromQuote(order.id(), quote, lines, now);
			insertDefaultDeliveryPlans(order.id(), operator.username(), now);
			Long orderVersion = this.jdbcTemplate.queryForObject("""
					update sal_sales_quote
					set status = 'CONVERTED', converted_order_id = ?, converted_at = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					returning version
					""", Long.class, order.id(), now, operator.username(), now, id);
			recordActionIdempotency("CONVERT_ORDER", "SALES_QUOTE", id, request, "SALES_ORDER", order.id(),
					orderVersion, operator);
			this.auditService.record(operator, "SALES_QUOTE_CONVERT_ORDER", QUOTE_TARGET, id, quote.quoteNo(),
					servletRequest);
			this.auditService.record(operator, "SALES_ORDER_CREATE_FROM_QUOTE", ORDER_TARGET, order.id(),
					order.documentNo(), servletRequest);
			return convertedOrder(order.id());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateQuoteException(exception);
		}
	}

	@Transactional
	public ConvertedContractResponse convertToContract(Long id, ConvertContractRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		validateConvertContractRequest(request);
		ConvertedContractResponse idempotent = idempotentContractResult(id, request, operator);
		if (idempotent != null) {
			return idempotent;
		}
		QuoteHeader quote = lockQuote(id).orElseThrow(this::quoteNotFound);
		requireVersion(quote.version(), request.version());
		requireQuoteConvertible(quote);
		validateContractProject(quote.customerId(), request.projectId() == null ? quote.projectId() : request.projectId());
		validateContractType(request.contractType(), request.mainContractId());
		OffsetDateTime now = OffsetDateTime.now();
		try {
			CreatedDocument contract = insertContractFromQuote(quote, request, operator.username(), now);
			Long newVersion = this.jdbcTemplate.queryForObject("""
					update sal_sales_quote
					set status = 'CONVERTED', converted_contract_id = ?, converted_at = ?, updated_by = ?,
					    updated_at = ?, version = version + 1
					where id = ?
					returning version
					""", Long.class, contract.id(), now, operator.username(), now, id);
			recordRawActionIdempotency("CONVERT_CONTRACT", "SALES_QUOTE", id, request.version(),
					request.idempotencyKey(), convertContractFingerprint(id, request), CONTRACT_TARGET,
					contract.id(), newVersion, operator);
			this.auditService.record(operator, "SALES_QUOTE_CONVERT_CONTRACT", QUOTE_TARGET, id, quote.quoteNo(),
					servletRequest);
			this.auditService.record(operator, "SALES_PROJECT_CONTRACT_CREATE_FROM_QUOTE", CONTRACT_TARGET,
					contract.id(), contract.documentNo(), servletRequest);
			return new ConvertedContractResponse(contract.id(), contract.documentNo());
		}
		catch (DuplicateKeyException exception) {
			throw duplicateQuoteException(exception);
		}
	}

	@Transactional(readOnly = true)
	public ConvertedOrderResponse convertedOrder(Long orderId) {
		ConvertedOrderHeader header = this.jdbcTemplate.query("""
				select o.id, o.order_no, o.customer_id, c.code as customer_code, c.name as customer_name,
				       o.order_date, o.expected_ship_date, o.status, o.project_id, p.project_no, p.name as project_name,
				       o.contract_id, pc.contract_no, pc.external_contract_no, o.source_quote_id, o.source_quote_no,
				       o.currency, o.tax_excluded_amount, o.tax_amount, o.tax_included_amount, o.version,
				       case
				           when count(distinct l.price_source_type) = 1 then min(l.price_source_type)
				           when count(l.id) = 0 then null
				           else 'MIXED'
				       end as price_source_type
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project p on p.id = o.project_id
				left join sal_project_contract pc on pc.id = o.contract_id
				left join sal_sales_order_line l on l.order_id = o.id
				where o.id = ?
				group by o.id, c.id, p.id, pc.id
				""", this::mapConvertedOrderHeader, orderId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_ORDER_NOT_FOUND));
		List<ConvertedOrderLineResponse> lines = this.jdbcTemplate.query("""
				select l.id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, l.quantity, l.shipped_quantity,
				       (l.quantity - l.shipped_quantity) as remaining_quantity, l.unit_price,
				       l.source_quote_line_id, l.price_source_type, l.source_no, l.currency, l.tax_rate,
				       l.tax_excluded_unit_price, l.tax_included_unit_price, l.tax_excluded_amount,
				       l.tax_amount, l.tax_included_amount, l.expected_ship_date, l.version
				from sal_sales_order_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.order_id = ?
				order by l.line_no asc, l.id asc
				""", this::mapConvertedOrderLine, orderId);
		return new ConvertedOrderResponse(header.id(), header.orderNo(), header.customerId(), header.customerCode(),
				header.customerName(), header.orderDate(), header.expectedShipDate(), header.status(),
				header.projectId(), header.projectNo(), header.projectName(), header.contractId(),
				header.contractNo(), header.externalContractNo(), header.sourceQuoteId(), header.sourceQuoteNo(),
				header.currency(), moneyString(header.taxExcludedAmount()), moneyString(header.taxAmount()),
				moneyString(header.taxIncludedAmount()), header.priceSourceType(), header.version(), List.of(),
				lines);
	}

	private ValidatedQuote validateQuoteRequest(QuoteRequest request) {
		LocalDate validUntil = request == null ? null
				: (request.validUntil() == null ? request.validTo() : request.validUntil());
		if (request == null || request.customerId() == null || request.quoteDate() == null
				|| validUntil == null || request.lines() == null || request.lines().isEmpty()) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.quoteDate().isAfter(validUntil)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (request.currency() != null && !"CNY".equals(request.currency())) {
			throw new BusinessException(ApiErrorCode.SALES_CURRENCY_UNSUPPORTED);
		}
		Customer customer = customer(request.customerId());
		SalesOrderProjectLinkService.ProjectLink projectLink = validateQuoteProject(customer, request.projectId(),
				request.contractId());
		Set<Integer> lineNos = new HashSet<>();
		List<ValidatedQuoteLine> lines = new ArrayList<>();
		BigDecimal taxExcludedAmount = ZERO;
		BigDecimal taxAmount = ZERO;
		BigDecimal taxIncludedAmount = ZERO;
		for (QuoteLineRequest line : request.lines()) {
			ValidatedQuoteLine validatedLine = validateQuoteLine(line, lineNos);
			lines.add(validatedLine);
			taxExcludedAmount = taxExcludedAmount.add(validatedLine.taxExcludedAmount());
			taxAmount = taxAmount.add(validatedLine.taxAmount());
			taxIncludedAmount = taxIncludedAmount.add(validatedLine.taxIncludedAmount());
		}
		return new ValidatedQuote(customer, projectLink, request.quoteDate(), validUntil,
				blankToNull(request.remark()), money(taxExcludedAmount), money(taxAmount), money(taxIncludedAmount),
				lines);
	}

	private ValidatedQuoteLine validateQuoteLine(QuoteLineRequest line, Set<Integer> lineNos) {
		BigDecimal requestTaxExcludedUnitPrice = line == null ? null
				: (line.taxExcludedUnitPrice() == null ? line.untaxedUnitPrice() : line.taxExcludedUnitPrice());
		LocalDate requiredDate = line == null ? null
				: (line.requiredDate() == null ? line.promisedDate() : line.requiredDate());
		if (line == null || line.lineNo() == null || line.materialId() == null || line.unitId() == null
				|| line.quantity() == null || requestTaxExcludedUnitPrice == null || line.taxRate() == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (!lineNos.add(line.lineNo())) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_DUPLICATE_LINE);
		}
		if (line.quantity().compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.SALES_QUANTITY_INVALID);
		}
		if (requestTaxExcludedUnitPrice.compareTo(ZERO) < 0 || line.taxRate().compareTo(ZERO) < 0) {
			throw new BusinessException(ApiErrorCode.SALES_TAX_PRICE_INVALID);
		}
		Material material = material(line.materialId());
		if (!material.unitId().equals(line.unitId())) {
			throw new BusinessException(ApiErrorCode.SALES_UNIT_INVALID);
		}
		BigDecimal taxExcludedUnitPrice = scale6(requestTaxExcludedUnitPrice);
		BigDecimal taxRate = scale6(line.taxRate());
		BigDecimal taxIncludedUnitPrice = scale6(taxExcludedUnitPrice.multiply(BigDecimal.ONE.add(taxRate)));
		BigDecimal taxExcludedAmount = money(line.quantity().multiply(taxExcludedUnitPrice));
		BigDecimal taxIncludedAmount = money(line.quantity().multiply(taxIncludedUnitPrice));
		BigDecimal taxAmount = money(taxIncludedAmount.subtract(taxExcludedAmount));
		return new ValidatedQuoteLine(line.lineNo(), material.id(), material.unitId(), scale6(line.quantity()),
				requiredDate, taxRate, taxExcludedUnitPrice, taxIncludedUnitPrice, taxExcludedAmount,
				taxAmount, taxIncludedAmount, blankToNull(line.remark()));
	}

	private SalesOrderProjectLinkService.ProjectLink validateQuoteProject(Customer customer, Long projectId,
			Long contractId) {
		if (projectId == null && contractId == null) {
			return null;
		}
		if (contractId != null) {
			return this.salesOrderProjectLinkService.validateForDraftSave(customer.id(), projectId, contractId);
		}
		return this.jdbcTemplate.query("""
				select id as project_id, project_no, name as project_name, customer_id
				from sal_project
				where id = ?
				""", (rs, rowNum) -> {
			if (!customer.id().equals(rs.getLong("customer_id"))) {
				throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
			}
			String status = this.jdbcTemplate.queryForObject("select status from sal_project where id = ?",
					String.class, projectId);
			if (!"DRAFT".equals(status) && !"ACTIVE".equals(status)) {
				throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
			}
			return new SalesOrderProjectLinkService.ProjectLink(rs.getLong("project_id"),
					rs.getString("project_no"), rs.getString("project_name"), rs.getLong("customer_id"),
					null, null, null, null, null);
		}, projectId).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
	}

	private Customer customer(Long customerId) {
		return this.jdbcTemplate.query("""
				select id, code, name, status
				from mst_customer
				where id = ?
				""", (rs, rowNum) -> new Customer(rs.getLong("id"), rs.getString("code"),
				rs.getString("name")), customerId)
			.stream()
			.filter((customer) -> "ENABLED".equals(this.jdbcTemplate.queryForObject(
					"select status from mst_customer where id = ?", String.class, customer.id())))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_CUSTOMER_INVALID));
	}

	private Material material(Long materialId) {
		return this.jdbcTemplate.query("""
				select id, unit_id, material_type, status
				from mst_material
				where id = ?
				""", (rs, rowNum) -> new Material(rs.getLong("id"), rs.getLong("unit_id"),
				rs.getString("material_type")), materialId)
			.stream()
			.filter((material) -> SELLABLE_MATERIAL_TYPES.contains(material.materialType()))
			.filter((material) -> "ENABLED".equals(this.jdbcTemplate.queryForObject(
					"select status from mst_material where id = ?", String.class, material.id())))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.SALES_MATERIAL_INVALID));
	}

	private CreatedDocument insertQuoteWithRetry(ValidatedQuote quote, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			String quoteNo = nextNo("SQT", QUOTE_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_quote (
							quote_no, customer_id, project_id, contract_id, quote_date, valid_until, status, currency,
							tax_excluded_amount, tax_amount, tax_included_amount, remark, created_by, created_at,
							updated_by, updated_at
						)
						values (?, ?, ?, ?, ?, ?, 'DRAFT', 'CNY', ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, quoteNo, quote.customer().id(),
						quote.projectLink() == null ? null : quote.projectLink().projectId(),
						quote.projectLink() == null ? null : quote.projectLink().contractId(), quote.quoteDate(),
						quote.validUntil(), quote.taxExcludedAmount(), quote.taxAmount(), quote.taxIncludedAmount(),
						quote.remark(), operatorName, now, operatorName, now);
				return new CreatedDocument(id, quoteNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_quote_no") && attempt < 3) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertQuoteLines(Long quoteId, List<ValidatedQuoteLine> lines, OffsetDateTime now) {
		for (ValidatedQuoteLine line : lines) {
			this.jdbcTemplate.update("""
					insert into sal_sales_quote_line (
						quote_id, line_no, material_id, unit_id, quantity, required_date, tax_rate,
						tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
						tax_included_amount, remark, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", quoteId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(),
					line.requiredDate(), line.taxRate(), line.taxExcludedUnitPrice(), line.taxIncludedUnitPrice(),
					line.taxExcludedAmount(), line.taxAmount(), line.taxIncludedAmount(), line.remark(), now, now);
		}
	}

	private CreatedDocument insertOrderFromQuote(QuoteHeader quote, SalesOrderProjectLinkService.ProjectLink targetLink,
			String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			String orderNo = nextNo("SO", ORDER_NO_SEQUENCE);
			try {
				Long orderId = this.jdbcTemplate.queryForObject("""
						insert into sal_sales_order (
							order_no, customer_id, order_date, expected_ship_date, status, remark, project_id,
							contract_id, source_quote_id, source_quote_no, source_quote_version, currency,
							tax_excluded_amount, tax_amount, tax_included_amount, created_by, created_at, updated_by,
							updated_at
						)
						values (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, quote.customerId(), LocalDate.now(), latestRequiredDate(quote.id()),
						quote.remark(), targetLink == null ? null : targetLink.projectId(),
						targetLink == null ? null : targetLink.contractId(), quote.id(), quote.quoteNo(),
						quote.version(), quote.taxExcludedAmount(), quote.taxAmount(), quote.taxIncludedAmount(),
						operatorName, now, operatorName, now);
				return new CreatedDocument(orderId, orderNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_sales_order_no") && attempt < 3) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void insertOrderLinesFromQuote(Long orderId, QuoteHeader quote, List<QuoteLineRow> lines,
			OffsetDateTime now) {
		for (QuoteLineRow line : lines) {
			this.jdbcTemplate.update("""
					insert into sal_sales_order_line (
						order_id, line_no, material_id, unit_id, quantity, shipped_quantity, unit_price,
						expected_ship_date, source_quote_line_id, price_source_type, source_no, currency, tax_rate,
						tax_excluded_unit_price, tax_included_unit_price, tax_excluded_amount, tax_amount,
						tax_included_amount, created_at, updated_at
					)
					values (?, ?, ?, ?, ?, 0, ?, ?, ?, 'QUOTE', ?, 'CNY', ?, ?, ?, ?, ?, ?, ?, ?)
					""", orderId, line.lineNo(), line.materialId(), line.unitId(), line.quantity(),
					line.taxExcludedUnitPrice(), line.requiredDate(), line.id(), quote.quoteNo(), line.taxRate(),
					line.taxExcludedUnitPrice(), line.taxIncludedUnitPrice(), line.taxExcludedAmount(),
					line.taxAmount(), line.taxIncludedAmount(), now, now);
		}
	}

	private CreatedDocument insertContractFromQuote(QuoteHeader quote, ConvertContractRequest request,
			String operatorName, OffsetDateTime now) {
		Long projectId = request.projectId() == null ? quote.projectId() : request.projectId();
		String contractType = request.contractType().trim().toUpperCase();
		for (int attempt = 1; attempt <= 3; attempt++) {
			String contractNo = nextNo("SC", CONTRACT_NO_SEQUENCE);
			try {
				Long contractId = this.jdbcTemplate.queryForObject("""
						insert into sal_project_contract (
							contract_no, external_contract_no, project_id, contract_type, main_contract_id, name,
							signed_date, effective_start_date, effective_end_date, amount, status, remark,
							created_by, created_at, updated_by, updated_at, source_quote_id, source_quote_no,
							source_quote_version
						)
						values (?, null, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, contractNo, projectId, contractType, request.mainContractId(),
						"报价转合同 " + quote.quoteNo(), LocalDate.now(), quote.quoteDate(), quote.validUntil(),
						quote.taxIncludedAmount(), quote.remark(), operatorName, now, operatorName, now, quote.id(),
						quote.quoteNo(), quote.version());
				return new CreatedDocument(contractId, contractNo);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_sal_project_contract_no") && attempt < 3) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private void requireQuoteConvertible(QuoteHeader quote) {
		if (!"APPROVED".equals(quote.status()) || quote.convertedOrderId() != null
				|| quote.convertedContractId() != null) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
		if (quote.validUntil() != null && quote.validUntil().isBefore(LocalDate.now())) {
			throw new BusinessException(ApiErrorCode.SALES_QUOTE_STATUS_INVALID);
		}
	}

	private void validateContractProject(Long customerId, Long projectId) {
		if (projectId == null) {
			throw new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND);
		}
		this.jdbcTemplate.query("""
				select id, customer_id, status
				from sal_project
				where id = ?
				""", (rs, rowNum) -> {
			if (!customerId.equals(rs.getLong("customer_id"))) {
				throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
			}
			if (!"ACTIVE".equals(rs.getString("status"))) {
				throw new BusinessException(ApiErrorCode.CONTRACT_PROJECT_NOT_ACTIVE);
			}
			return rs.getLong("id");
		}, projectId).stream().findFirst().orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
	}

	private void validateContractType(String contractType, Long mainContractId) {
		if (!hasText(contractType)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		String normalized = contractType.trim().toUpperCase();
		if ("MAIN".equals(normalized)) {
			if (mainContractId != null) {
				throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_INVALID);
			}
			return;
		}
		if (!"SUPPLEMENT".equals(normalized) || mainContractId == null) {
			throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_REQUIRED);
		}
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project_contract
				where id = ?
				and contract_type = 'MAIN'
				and status = 'EFFECTIVE'
				""", Long.class, mainContractId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_NOT_EFFECTIVE);
		}
	}

	private void insertDefaultDeliveryPlans(Long orderId, String operatorName, OffsetDateTime now) {
		this.jdbcTemplate.update("""
				insert into sal_sales_delivery_plan (
					order_id, order_line_id, line_no, planned_date, planned_quantity, shipped_quantity, status,
					created_by, created_at, updated_by, updated_at
				)
				select order_id, id, 1, coalesce(expected_ship_date, current_date), quantity, 0, 'PLANNED',
				       ?, ?, ?, ?
				from sal_sales_order_line
				where order_id = ?
				""", operatorName, now, operatorName, now, orderId);
	}

	private ConvertedOrderResponse idempotentConvertResult(Long quoteId, VersionedActionRequest request,
			CurrentUser operator) {
		List<ExistingAction> existing = this.jdbcTemplate.query("""
				select result_resource_id, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = 'CONVERT_ORDER'
				and resource_type = 'SALES_QUOTE'
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getLong("result_resource_id"),
				rs.getString("request_fingerprint")), operator.id(), quoteId, request.idempotencyKey().trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingAction action = existing.getFirst();
		if (!action.requestFingerprint().equals(actionFingerprint("CONVERT_ORDER", "SALES_QUOTE", quoteId, request))) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
		return convertedOrder(action.resultResourceId());
	}

	private SalesQuoteDetailResponse idempotentQuoteResult(String action, Long quoteId, VersionedActionRequest request,
			CurrentUser operator) {
		List<ExistingAction> existing = this.jdbcTemplate.query("""
				select result_resource_id, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = ?
				and resource_type = 'SALES_QUOTE'
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getLong("result_resource_id"),
				rs.getString("request_fingerprint")), operator.id(), action, quoteId, request.idempotencyKey().trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingAction record = existing.getFirst();
		if (!record.requestFingerprint().equals(actionFingerprint(action, "SALES_QUOTE", quoteId, request))) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
		return quote(record.resultResourceId());
	}

	private ConvertedContractResponse idempotentContractResult(Long quoteId, ConvertContractRequest request,
			CurrentUser operator) {
		List<ExistingAction> existing = this.jdbcTemplate.query("""
				select result_resource_id, request_fingerprint
				from sal_action_idempotency
				where operator_user_id = ?
				and action = 'CONVERT_CONTRACT'
				and resource_type = 'SALES_QUOTE'
				and resource_id = ?
				and idempotency_key = ?
				""", (rs, rowNum) -> new ExistingAction(rs.getLong("result_resource_id"),
				rs.getString("request_fingerprint")), operator.id(), quoteId, request.idempotencyKey().trim());
		if (existing.isEmpty()) {
			return null;
		}
		ExistingAction record = existing.getFirst();
		if (!record.requestFingerprint().equals(convertContractFingerprint(quoteId, request))) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
		return convertedContract(record.resultResourceId());
	}

	private ConvertedContractResponse convertedContract(Long contractId) {
		return this.jdbcTemplate.query("""
				select id, contract_no
				from sal_project_contract
				where id = ?
				""", (rs, rowNum) -> new ConvertedContractResponse(rs.getLong("id"),
				rs.getString("contract_no")), contractId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
	}

	private void recordActionIdempotency(String action, String resourceType, Long resourceId,
			VersionedActionRequest request, String resultResourceType, Long resultResourceId, Long resultVersion,
			CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into sal_action_idempotency (
						operator_user_id, action, resource_type, resource_id, idempotency_key, request_fingerprint,
						result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, request.idempotencyKey().trim(),
					actionFingerprint(action, resourceType, resourceId, request), resultResourceType,
					resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	private void recordRawActionIdempotency(String action, String resourceType, Long resourceId, Long resourceVersion,
			String idempotencyKey, String requestFingerprint, String resultResourceType, Long resultResourceId,
			Long resultVersion, CurrentUser operator) {
		try {
			this.jdbcTemplate.update("""
					insert into sal_action_idempotency (
						operator_user_id, action, resource_type, resource_id, resource_version, idempotency_key,
						request_fingerprint, result_resource_type, result_resource_id, result_version
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", operator.id(), action, resourceType, resourceId, resourceVersion, idempotencyKey.trim(),
					requestFingerprint, resultResourceType, resultResourceId, resultVersion);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.SALES_ACTION_IDEMPOTENCY_CONFLICT);
		}
	}

	private OptionalQuote quoteHeader(Long id) {
		return OptionalQuote.of(this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.customer_id, c.code as customer_code, c.name as customer_name,
				       q.project_id, p.project_no, p.name as project_name, q.contract_id, pc.contract_no,
				       pc.external_contract_no, q.quote_date, q.valid_until, q.status, q.currency,
				       q.tax_excluded_amount, q.tax_amount, q.tax_included_amount, q.approval_instance_id,
				       q.converted_order_id, q.converted_contract_id, q.remark, q.created_by, q.created_at,
				       q.updated_at, q.approved_by, q.approved_at, q.cancelled_by, q.cancelled_at, q.version
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				left join sal_project_contract pc on pc.id = q.contract_id
				where q.id = ?
				""", this::mapQuoteHeader, id).stream().findFirst().orElse(null));
	}

	private OptionalQuote lockQuote(Long id) {
		return OptionalQuote.of(this.jdbcTemplate.query("""
				select q.id, q.quote_no, q.customer_id, c.code as customer_code, c.name as customer_name,
				       q.project_id, p.project_no, p.name as project_name, q.contract_id, pc.contract_no,
				       pc.external_contract_no, q.quote_date, q.valid_until, q.status, q.currency,
				       q.tax_excluded_amount, q.tax_amount, q.tax_included_amount, q.approval_instance_id,
				       q.converted_order_id, q.converted_contract_id, q.remark, q.created_by, q.created_at,
				       q.updated_at, q.approved_by, q.approved_at, q.cancelled_by, q.cancelled_at, q.version
				from sal_sales_quote q
				join mst_customer c on c.id = q.customer_id
				left join sal_project p on p.id = q.project_id
				left join sal_project_contract pc on pc.id = q.contract_id
				where q.id = ?
				for update of q
				""", this::mapQuoteHeader, id).stream().findFirst().orElse(null));
	}

	private List<QuoteLineRow> quoteLines(Long quoteId) {
		return this.jdbcTemplate.query("""
				select l.id, l.quote_id, l.line_no, l.material_id, m.code as material_code, m.name as material_name,
				       l.unit_id, u.name as unit_name, l.quantity, l.required_date, l.tax_rate,
				       l.tax_excluded_unit_price, l.tax_included_unit_price, l.tax_excluded_amount, l.tax_amount,
				       l.tax_included_amount, l.remark, l.version
				from sal_sales_quote_line l
				join mst_material m on m.id = l.material_id
				join mst_unit u on u.id = l.unit_id
				where l.quote_id = ?
				order by l.line_no asc, l.id asc
				""", this::mapQuoteLine, quoteId);
	}

	private QueryParts quoteQueryParts(String keyword, Long customerId, Long projectId, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(q.quote_no ilike ? or c.code ilike ? or c.name ilike ? or q.remark ilike ?)");
			String like = "%" + keyword.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (customerId != null) {
			conditions.add("q.customer_id = ?");
			args.add(customerId);
		}
		if (projectId != null) {
			conditions.add("q.project_id = ?");
			args.add(projectId);
		}
		if (hasText(status)) {
			conditions.add("q.status = ?");
			args.add(status.trim());
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private LocalDate latestRequiredDate(Long quoteId) {
		return this.jdbcTemplate.queryForObject("""
				select max(required_date)
				from sal_sales_quote_line
				where quote_id = ?
				""", LocalDate.class, quoteId);
	}

	private SalesQuoteDetailResponse toQuoteResponse(QuoteHeader header, List<QuoteLineRow> lines) {
		return new SalesQuoteDetailResponse(header.id(), header.quoteNo(), header.customerId(),
				header.customerCode(), header.customerName(), header.projectId(), header.projectNo(),
				header.projectName(), header.contractId(), header.contractNo(), header.externalContractNo(),
				header.quoteDate(), header.validUntil(), header.status(), quoteApprovalStatus(header), header.currency(),
				moneyString(header.taxExcludedAmount()), moneyString(header.taxAmount()),
				moneyString(header.taxIncludedAmount()), header.approvalInstanceId(), header.convertedOrderId(),
				header.convertedContractId(), header.remark(), header.createdBy(), header.createdAt(),
				header.updatedAt(), header.approvedBy(), header.approvedAt(), header.cancelledBy(),
				header.cancelledAt(), header.version(),
				quoteAllowedActions(header), lines.stream().map(this::toQuoteLineResponse).toList());
	}

	private SalesQuoteSummaryResponse mapQuoteSummary(ResultSet rs, int rowNum) throws SQLException {
		QuoteHeader header = mapQuoteHeader(rs, rowNum);
		return new SalesQuoteSummaryResponse(header.id(), header.quoteNo(), header.customerId(),
				header.customerCode(), header.customerName(), header.projectId(), header.projectNo(),
				header.projectName(), header.contractId(), header.contractNo(), header.externalContractNo(),
				header.quoteDate(), header.validUntil(), header.status(), quoteApprovalStatus(header), header.currency(),
				moneyString(header.taxExcludedAmount()), moneyString(header.taxAmount()),
				moneyString(header.taxIncludedAmount()), header.approvalInstanceId(), header.convertedOrderId(),
				header.convertedContractId(), header.remark(), header.createdBy(), header.createdAt(),
				header.updatedAt(), header.approvedBy(), header.approvedAt(), header.cancelledBy(),
				header.cancelledAt(), header.version(),
				quoteAllowedActions(header), quoteActionDisabledReason(header));
	}

	private SalesQuoteLineResponse toQuoteLineResponse(QuoteLineRow line) {
		return new SalesQuoteLineResponse(line.id(), line.lineNo(), line.materialId(), line.materialCode(),
				line.materialName(), line.unitId(), line.unitName(), quantityString(line.quantity()),
				line.requiredDate(), "QUOTE", line.id(), moneyString(line.taxRate(), 6),
				moneyString(line.taxExcludedUnitPrice(), 6), moneyString(line.taxIncludedUnitPrice(), 6),
				moneyString(line.taxExcludedAmount()), moneyString(line.taxAmount()),
				moneyString(line.taxIncludedAmount()), line.remark(), line.version());
	}

	private String quoteApprovalStatus(QuoteHeader quote) {
		if ("APPROVED".equals(quote.status()) || "CONVERTED".equals(quote.status())) {
			return "APPROVED";
		}
		if (quote.approvalInstanceId() != null) {
			return "SUBMITTED";
		}
		return null;
	}

	private List<String> quoteAllowedActions(QuoteHeader quote) {
		if ("DRAFT".equals(quote.status())) {
			if (quote.approvalInstanceId() != null) {
				return List.of();
			}
			return List.of("UPDATE", "SUBMIT_APPROVAL", "CANCEL");
		}
		if ("APPROVED".equals(quote.status())) {
			if (quote.convertedOrderId() != null || quote.convertedContractId() != null
					|| (quote.validUntil() != null && quote.validUntil().isBefore(LocalDate.now()))) {
				return List.of();
			}
			return List.of("CONVERT_ORDER", "CONVERT_CONTRACT", "PRINT", "EXPORT");
		}
		return List.of();
	}

	private String quoteActionDisabledReason(QuoteHeader quote) {
		return quoteAllowedActions(quote).isEmpty() ? ApiErrorCode.SALES_QUOTE_STATUS_INVALID.message() : null;
	}

	private QuoteHeader mapQuoteHeader(ResultSet rs, int rowNum) throws SQLException {
		return new QuoteHeader(rs.getLong("id"), rs.getString("quote_no"), rs.getLong("customer_id"),
				rs.getString("customer_code"), rs.getString("customer_name"), rs.getObject("project_id", Long.class),
				rs.getString("project_no"), rs.getString("project_name"), rs.getObject("contract_id", Long.class),
				rs.getString("contract_no"), rs.getString("external_contract_no"),
				rs.getObject("quote_date", LocalDate.class), rs.getObject("valid_until", LocalDate.class),
				rs.getString("status"), rs.getString("currency"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"),
				rs.getObject("approval_instance_id", Long.class), rs.getObject("converted_order_id", Long.class),
				rs.getObject("converted_contract_id", Long.class), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getString("approved_by"), rs.getObject("approved_at", OffsetDateTime.class),
				rs.getString("cancelled_by"), rs.getObject("cancelled_at", OffsetDateTime.class),
				rs.getLong("version"));
	}

	private QuoteLineRow mapQuoteLine(ResultSet rs, int rowNum) throws SQLException {
		return new QuoteLineRow(rs.getLong("id"), rs.getLong("quote_id"), rs.getInt("line_no"),
				rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_name"),
				rs.getLong("unit_id"), rs.getString("unit_name"), rs.getBigDecimal("quantity"),
				rs.getObject("required_date", LocalDate.class), rs.getBigDecimal("tax_rate"),
				rs.getBigDecimal("tax_excluded_unit_price"), rs.getBigDecimal("tax_included_unit_price"),
				rs.getBigDecimal("tax_excluded_amount"), rs.getBigDecimal("tax_amount"),
				rs.getBigDecimal("tax_included_amount"), rs.getString("remark"), rs.getLong("version"));
	}

	private ConvertedOrderHeader mapConvertedOrderHeader(ResultSet rs, int rowNum) throws SQLException {
		return new ConvertedOrderHeader(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_code"), rs.getString("customer_name"),
				rs.getObject("order_date", LocalDate.class), rs.getObject("expected_ship_date", LocalDate.class),
				rs.getString("status"), rs.getObject("project_id", Long.class), rs.getString("project_no"),
				rs.getString("project_name"), rs.getObject("contract_id", Long.class), rs.getString("contract_no"),
				rs.getString("external_contract_no"), rs.getObject("source_quote_id", Long.class),
				rs.getString("source_quote_no"), rs.getString("currency"), rs.getBigDecimal("tax_excluded_amount"),
				rs.getBigDecimal("tax_amount"), rs.getBigDecimal("tax_included_amount"),
				rs.getString("price_source_type"), rs.getLong("version"));
	}

	private ConvertedOrderLineResponse mapConvertedOrderLine(ResultSet rs, int rowNum) throws SQLException {
		return new ConvertedOrderLineResponse(rs.getLong("id"), rs.getInt("line_no"), rs.getLong("material_id"),
				rs.getString("material_code"), rs.getString("material_name"), rs.getLong("unit_id"),
				rs.getString("unit_name"), quantityString(rs.getBigDecimal("quantity")),
				quantityString(rs.getBigDecimal("shipped_quantity")),
				quantityString(rs.getBigDecimal("remaining_quantity")),
				rs.getObject("source_quote_line_id", Long.class),
				rs.getObject("source_quote_line_id", Long.class), rs.getString("price_source_type"),
				rs.getString("source_no"), rs.getString("currency"),
				moneyString(rs.getBigDecimal("tax_rate"), 6),
				moneyString(rs.getBigDecimal("tax_excluded_unit_price"), 6),
				moneyString(rs.getBigDecimal("tax_included_unit_price"), 6),
				moneyString(rs.getBigDecimal("tax_excluded_amount")), moneyString(rs.getBigDecimal("tax_amount")),
				moneyString(rs.getBigDecimal("tax_included_amount")),
				rs.getObject("expected_ship_date", LocalDate.class), rs.getLong("version"));
	}

	private void validateActionRequest(VersionedActionRequest request, boolean reasonRequired) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (reasonRequired && !hasText(request.reason())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateConvertContractRequest(ConvertContractRequest request) {
		if (request == null || request.version() == null || !hasText(request.idempotencyKey())
				|| request.idempotencyKey().length() > 120 || !hasText(request.contractType())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void requireVersion(Long currentVersion, Long requestVersion) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(ApiErrorCode.VERSION_CONFLICT);
		}
	}

	private BusinessException duplicateQuoteException(DuplicateKeyException exception) {
		if (containsConstraint(exception, "uk_sal_sales_quote_line_no")) {
			return new BusinessException(ApiErrorCode.SALES_QUOTE_DUPLICATE_LINE);
		}
		return new BusinessException(ApiErrorCode.CONFLICT);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private BusinessException quoteNotFound() {
		return new BusinessException(ApiErrorCode.SALES_QUOTE_NOT_FOUND);
	}

	private String actionFingerprint(String action, String resourceType, Long resourceId,
			VersionedActionRequest request) {
		return sha256(action + "|" + resourceType + "|" + resourceId + "|" + request.version() + "|"
				+ nullToBlank(blankToNull(request.reason())));
	}

	private String convertContractFingerprint(Long quoteId, ConvertContractRequest request) {
		return sha256("CONVERT_CONTRACT|SALES_QUOTE|" + quoteId + "|" + request.version() + "|"
				+ nullToBlank(request.projectId()) + "|" + nullToBlank(request.contractType()) + "|"
				+ nullToBlank(request.mainContractId()));
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private static BigDecimal scale6(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static String quantityString(BigDecimal value) {
		return value == null ? null : value.setScale(6, RoundingMode.HALF_UP).toPlainString();
	}

	private static String moneyString(BigDecimal value) {
		return moneyString(value, 2);
	}

	private static String moneyString(BigDecimal value, int scale) {
		return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value.trim() : null;
	}

	private static String nullToBlank(Object value) {
		return value == null ? "" : value.toString();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public record QuoteRequest(@NotNull Long customerId, Long projectId, Long contractId,
			@NotNull LocalDate quoteDate, LocalDate validUntil, LocalDate validTo, String currency, String remark,
			@Valid List<QuoteLineRequest> lines, Long version, String idempotencyKey) {
	}

	public record QuoteLineRequest(@NotNull Integer lineNo, @NotNull Long materialId, @NotNull Long unitId,
			@NotNull BigDecimal quantity, LocalDate requiredDate, BigDecimal taxExcludedUnitPrice,
			BigDecimal untaxedUnitPrice, BigDecimal taxIncludedUnitPrice, BigDecimal untaxedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount, LocalDate promisedDate,
			@NotNull BigDecimal taxRate, String remark) {
	}

	public record VersionedActionRequest(@NotNull Long version, String reason, @NotNull String idempotencyKey,
			Long projectId, Long contractId) {
	}

	public record ConvertContractRequest(@NotNull Long version, @NotNull String idempotencyKey, Long projectId,
			String contractType, Long mainContractId) {
	}

	public record SalesQuoteDetailResponse(Long id, String quoteNo, Long customerId, String customerCode,
			String customerName, Long projectId, String projectCode, String projectName, Long contractId,
			String contractNo, String externalContractNo, LocalDate quoteDate, LocalDate validUntil, String status,
			String approvalStatus, String currency, String taxExcludedAmount, String taxAmount, String taxIncludedAmount,
			Long approvalInstanceId, Long convertedOrderId, Long convertedContractId, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String approvedByName,
			OffsetDateTime approvedAt, String cancelledByName, OffsetDateTime cancelledAt, Long version,
			List<String> allowedActions, List<SalesQuoteLineResponse> lines) {
	}

	public record SalesQuoteSummaryResponse(Long id, String quoteNo, Long customerId, String customerCode,
			String customerName, Long projectId, String projectCode, String projectName, Long contractId,
			String contractNo, String externalContractNo, LocalDate quoteDate, LocalDate validUntil, String status,
			String approvalStatus, String currency, String taxExcludedAmount, String taxAmount, String taxIncludedAmount,
			Long approvalInstanceId, Long convertedOrderId, Long convertedContractId, String remark,
			String createdByName, OffsetDateTime createdAt, OffsetDateTime updatedAt, String approvedByName,
			OffsetDateTime approvedAt, String cancelledByName, OffsetDateTime cancelledAt, Long version,
			List<String> allowedActions, String actionDisabledReason) {
	}

	public record SalesQuoteLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, Long unitId, String unitName, String quantity, LocalDate requiredDate,
			String priceSourceType, Long quoteLineId, String taxRate, String taxExcludedUnitPrice,
			String taxIncludedUnitPrice, String taxExcludedAmount, String taxAmount, String taxIncludedAmount,
			String remark, Long version) {
	}

	public record ConvertedOrderResponse(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, Long projectId,
			String projectCode, String projectName, Long contractId, String contractNo, String externalContractNo,
			Long sourceQuoteId, String sourceQuoteNo, String currency, String taxExcludedAmount, String taxAmount,
			String taxIncludedAmount, String priceSourceType, Long version, List<String> allowedActions,
			List<ConvertedOrderLineResponse> lines) {
	}

	public record ConvertedContractResponse(Long id, String contractNo) {
	}

	public record ConvertedOrderLineResponse(Long id, Integer lineNo, Long materialId, String materialCode,
			String materialName, Long unitId, String unitName, String quantity, String shippedQuantity,
			String remainingQuantity, Long quoteLineId, Long sourceQuoteLineId, String priceSourceType,
			String sourceNo, String currency, String taxRate, String taxExcludedUnitPrice,
			String taxIncludedUnitPrice, String taxExcludedAmount, String taxAmount, String taxIncludedAmount,
			LocalDate expectedShipDate, Long version) {
	}

	private record ValidatedQuote(Customer customer, SalesOrderProjectLinkService.ProjectLink projectLink,
			LocalDate quoteDate, LocalDate validUntil, String remark, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount, List<ValidatedQuoteLine> lines) {
	}

	private record ValidatedQuoteLine(Integer lineNo, Long materialId, Long unitId, BigDecimal quantity,
			LocalDate requiredDate, BigDecimal taxRate, BigDecimal taxExcludedUnitPrice,
			BigDecimal taxIncludedUnitPrice, BigDecimal taxExcludedAmount, BigDecimal taxAmount,
			BigDecimal taxIncludedAmount, String remark) {
	}

	private record Customer(Long id, String code, String name) {
	}

	private record Material(Long id, Long unitId, String materialType) {
	}

	private record QuoteHeader(Long id, String quoteNo, Long customerId, String customerCode, String customerName,
			Long projectId, String projectNo, String projectName, Long contractId, String contractNo,
			String externalContractNo, LocalDate quoteDate, LocalDate validUntil, String status, String currency,
			BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount,
			Long approvalInstanceId, Long convertedOrderId, Long convertedContractId, String remark,
			String createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String approvedBy,
			OffsetDateTime approvedAt, String cancelledBy, OffsetDateTime cancelledAt, Long version) {
	}

	private record QuoteLineRow(Long id, Long quoteId, Integer lineNo, Long materialId, String materialCode,
			String materialName, Long unitId, String unitName, BigDecimal quantity, LocalDate requiredDate,
			BigDecimal taxRate, BigDecimal taxExcludedUnitPrice, BigDecimal taxIncludedUnitPrice,
			BigDecimal taxExcludedAmount, BigDecimal taxAmount, BigDecimal taxIncludedAmount, String remark,
			Long version) {
	}

	private record ConvertedOrderHeader(Long id, String orderNo, Long customerId, String customerCode,
			String customerName, LocalDate orderDate, LocalDate expectedShipDate, String status, Long projectId,
			String projectNo, String projectName, Long contractId, String contractNo, String externalContractNo,
			Long sourceQuoteId, String sourceQuoteNo, String currency, BigDecimal taxExcludedAmount,
			BigDecimal taxAmount, BigDecimal taxIncludedAmount, String priceSourceType, Long version) {
	}

	private record CreatedDocument(Long id, String documentNo) {
	}

	private record ExistingAction(Long resultResourceId, String requestFingerprint) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

	private record OptionalQuote(QuoteHeader value) {

		private static OptionalQuote of(QuoteHeader value) {
			return new OptionalQuote(value);
		}

		private QuoteHeader orElseThrow(java.util.function.Supplier<BusinessException> exceptionSupplier) {
			if (this.value == null) {
				throw exceptionSupplier.get();
			}
			return this.value;
		}

	}

}
