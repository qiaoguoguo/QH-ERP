package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SalesProjectContractService {

	private static final String CONTRACT_TARGET = "SALES_PROJECT_CONTRACT";

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final AtomicInteger CONTRACT_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public SalesProjectContractService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ContractResponse> listByProject(Long projectId) {
		ensureProjectExists(projectId);
		return this.jdbcTemplate.query("""
				select c.id, c.contract_no, c.external_contract_no, c.project_id, c.contract_type, c.main_contract_id,
				       c.name, c.signed_date, c.effective_start_date, c.effective_end_date, c.amount, c.status,
				       c.remark, c.created_by, c.created_at, c.updated_by, c.updated_at, c.activated_by,
				       c.activated_at, c.closed_by, c.closed_at, c.close_reason, c.terminated_by, c.terminated_at,
				       c.terminate_reason, c.cancelled_by, c.cancelled_at, c.cancel_reason, c.version
				from sal_project_contract c
				where c.project_id = ?
				order by case when c.contract_type = 'MAIN' then 0 else 1 end, c.id asc
				""", this::mapContract, projectId);
	}

	@Transactional(readOnly = true)
	public ContractResponse get(Long id) {
		return contract(id).orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
	}

	@Transactional
	public ContractResponse create(Long projectId, ContractCreateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ProjectState project = lockProject(projectId).orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		ValidatedContract validated = validateCreate(project, request);
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into sal_project_contract (
						contract_no, external_contract_no, project_id, contract_type, main_contract_id, name,
						signed_date, effective_start_date, effective_end_date, amount, status, remark, created_by,
						created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
					returning id
					""", Long.class, nextNo("SC", CONTRACT_SEQUENCE), blankToNull(validated.externalContractNo()),
					project.id(), validated.contractType().name(), validated.mainContractId(), validated.name(),
					validated.signedDate(), validated.effectiveStartDate(), validated.effectiveEndDate(),
					validated.amount(), blankToNull(validated.remark()), operator.username(), now,
					operator.username(), now);
			this.auditService.record(operator, "SALES_PROJECT_CONTRACT_CREATE", CONTRACT_TARGET, id,
					"创建合同 " + validated.name(), servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException(ApiErrorCode.CONFLICT);
		}
	}

	@Transactional
	public ContractResponse update(Long id, ContractUpdateRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ContractRow current = lockContract(id).orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		if (current.status() != SalesProjectContractStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.CONTRACT_STATUS_INVALID);
		}
		ValidatedContract validated = validateUpdate(current, request);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project_contract
				set external_contract_no = ?, name = ?, signed_date = ?, effective_start_date = ?,
				    effective_end_date = ?, amount = ?, remark = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", blankToNull(validated.externalContractNo()), validated.name(), validated.signedDate(),
				validated.effectiveStartDate(), validated.effectiveEndDate(), validated.amount(),
				blankToNull(validated.remark()), operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_CONTRACT_UPDATE", CONTRACT_TARGET, id,
				"更新字段 name,signedDate,effectiveDate,amount,remark", servletRequest);
		return get(id);
	}

	@Transactional
	public ContractResponse activate(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ContractRow current = lockContract(id).orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		if (current.status() != SalesProjectContractStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.CONTRACT_STATUS_INVALID);
		}
		ProjectState project = lockProject(current.projectId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND));
		if (current.contractType() == SalesProjectContractType.MAIN) {
			if (project.status() == SalesProjectStatus.CLOSED || project.status() == SalesProjectStatus.CANCELLED) {
				throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
			}
		}
		else {
			if (project.status() != SalesProjectStatus.ACTIVE) {
				throw new BusinessException(ApiErrorCode.CONTRACT_PROJECT_NOT_ACTIVE);
			}
			ensureEffectiveMainContract(current.projectId(), current.mainContractId());
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project_contract
				set status = 'EFFECTIVE', activated_by = ?, activated_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ?
				""", operator.username(), now, operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_CONTRACT_ACTIVATE", CONTRACT_TARGET, id,
				"合同生效", servletRequest);
		return get(id);
	}

	@Transactional
	public ContractResponse close(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return finishEffectiveContract(id, request, operator, servletRequest, SalesProjectContractStatus.CLOSED,
				"SALES_PROJECT_CONTRACT_CLOSE", "close_reason", "closed_by", "closed_at");
	}

	@Transactional
	public ContractResponse terminate(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return finishEffectiveContract(id, request, operator, servletRequest, SalesProjectContractStatus.TERMINATED,
				"SALES_PROJECT_CONTRACT_TERMINATE", "terminate_reason", "terminated_by", "terminated_at");
	}

	@Transactional
	public ContractResponse cancel(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		ContractRow current = lockContract(id).orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		String reason = validateReason(request == null ? null : request.reason());
		if (current.status() != SalesProjectContractStatus.DRAFT) {
			throw new BusinessException(ApiErrorCode.CONTRACT_STATUS_INVALID);
		}
		Long referenced = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order
				where contract_id = ?
				""", Long.class, id);
		if (referenced != null && referenced > 0) {
			throw new BusinessException(ApiErrorCode.CONTRACT_REFERENCED_BY_ORDER);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project_contract
				set status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, cancel_reason = ?, updated_by = ?,
				    updated_at = ?, version = version + 1
				where id = ?
				""", operator.username(), now, reason, operator.username(), now, id);
		this.auditService.record(operator, "SALES_PROJECT_CONTRACT_CANCEL", CONTRACT_TARGET, id,
				"取消原因：" + reason, servletRequest);
		return get(id);
	}

	private ContractResponse finishEffectiveContract(Long id, VersionedActionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest, SalesProjectContractStatus targetStatus, String action,
			String reasonColumn, String operatorColumn, String timeColumn) {
		ContractRow current = lockContract(id).orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_NOT_FOUND));
		requireVersion(current.version(), request == null ? null : request.version());
		String reason = validateReason(request == null ? null : request.reason());
		if (current.status() != SalesProjectContractStatus.EFFECTIVE) {
			throw new BusinessException(ApiErrorCode.CONTRACT_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update sal_project_contract
				set status = ?, %s = ?, %s = ?, %s = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""".formatted(operatorColumn, timeColumn, reasonColumn), targetStatus.name(), operator.username(),
				now, reason, operator.username(), now, id);
		this.auditService.record(operator, action, CONTRACT_TARGET, id, "原因：" + reason, servletRequest);
		return get(id);
	}

	private ValidatedContract validateCreate(ProjectState project, ContractCreateRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		SalesProjectContractType type = parseType(request.contractType());
		if (project.status() == SalesProjectStatus.CLOSED || project.status() == SalesProjectStatus.CANCELLED) {
			throw new BusinessException(ApiErrorCode.PROJECT_STATUS_INVALID);
		}
		if (type == SalesProjectContractType.SUPPLEMENT) {
			if (project.status() != SalesProjectStatus.ACTIVE) {
				throw new BusinessException(ApiErrorCode.CONTRACT_PROJECT_NOT_ACTIVE);
			}
			if (request.mainContractId() == null) {
				throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_REQUIRED);
			}
			ensureEffectiveMainContract(project.id(), request.mainContractId());
		}
		else if (request.mainContractId() != null) {
			throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_INVALID);
		}
		return validatedContract(type, request.mainContractId(), request.name(), request.signedDate(),
				request.effectiveStartDate(), request.effectiveEndDate(), request.amount(),
				request.externalContractNo(), request.remark());
	}

	private ValidatedContract validateUpdate(ContractRow current, ContractUpdateRequest request) {
		if (request == null) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		return validatedContract(current.contractType(), current.mainContractId(), request.name(),
				request.signedDate(), request.effectiveStartDate(), request.effectiveEndDate(), request.amount(),
				request.externalContractNo(), request.remark());
	}

	private ValidatedContract validatedContract(SalesProjectContractType type, Long mainContractId, String name,
			LocalDate signedDate, LocalDate effectiveStartDate, LocalDate effectiveEndDate, BigDecimal amount,
			String externalContractNo, String remark) {
		if (!hasText(name) || name.length() > 120) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (effectiveStartDate != null && effectiveEndDate != null && effectiveStartDate.isAfter(effectiveEndDate)) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		validateAmount(type, amount);
		validateOptionalText(externalContractNo, 100);
		validateOptionalText(remark, 500);
		return new ValidatedContract(type, mainContractId, name, signedDate, effectiveStartDate, effectiveEndDate,
				amount, externalContractNo, remark);
	}

	private void validateAmount(SalesProjectContractType type, BigDecimal amount) {
		if (amount == null || amount.scale() > 2 || amount.precision() - amount.scale() > 16) {
			throw new BusinessException(ApiErrorCode.CONTRACT_AMOUNT_INVALID);
		}
		if (type == SalesProjectContractType.MAIN && amount.compareTo(ZERO) <= 0) {
			throw new BusinessException(ApiErrorCode.CONTRACT_AMOUNT_INVALID);
		}
		if (type == SalesProjectContractType.SUPPLEMENT && amount.compareTo(ZERO) == 0) {
			throw new BusinessException(ApiErrorCode.CONTRACT_AMOUNT_INVALID);
		}
	}

	private void ensureEffectiveMainContract(Long projectId, Long mainContractId) {
		ContractRow main = lockContract(mainContractId)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.CONTRACT_MAIN_INVALID));
		if (!main.projectId().equals(projectId) || main.contractType() != SalesProjectContractType.MAIN) {
			throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_INVALID);
		}
		if (main.status() != SalesProjectContractStatus.EFFECTIVE) {
			throw new BusinessException(ApiErrorCode.CONTRACT_MAIN_NOT_EFFECTIVE);
		}
	}

	private Optional<ProjectState> lockProject(Long projectId) {
		return this.jdbcTemplate.query("""
				select id, customer_id, status
				from sal_project
				where id = ?
				for update
				""", (rs, rowNum) -> new ProjectState(rs.getLong("id"), rs.getLong("customer_id"),
				SalesProjectStatus.valueOf(rs.getString("status"))), projectId).stream().findFirst();
	}

	private void ensureProjectExists(Long projectId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sal_project where id = ?", Long.class,
				projectId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND);
		}
	}

	private Optional<ContractResponse> contract(Long id) {
		return this.jdbcTemplate.query("""
				select c.id, c.contract_no, c.external_contract_no, c.project_id, c.contract_type, c.main_contract_id,
				       c.name, c.signed_date, c.effective_start_date, c.effective_end_date, c.amount, c.status,
				       c.remark, c.created_by, c.created_at, c.updated_by, c.updated_at, c.activated_by,
				       c.activated_at, c.closed_by, c.closed_at, c.close_reason, c.terminated_by, c.terminated_at,
				       c.terminate_reason, c.cancelled_by, c.cancelled_at, c.cancel_reason, c.version
				from sal_project_contract c
				where c.id = ?
				""", this::mapContract, id).stream().findFirst();
	}

	private Optional<ContractRow> lockContract(Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return this.jdbcTemplate.query("""
				select id, project_id, contract_type, main_contract_id, status, version
				from sal_project_contract
				where id = ?
				for update
				""", (rs, rowNum) -> new ContractRow(rs.getLong("id"), rs.getLong("project_id"),
				SalesProjectContractType.valueOf(rs.getString("contract_type")), nullableLong(rs, "main_contract_id"),
				SalesProjectContractStatus.valueOf(rs.getString("status")), rs.getLong("version")), id)
			.stream()
			.findFirst();
	}

	private ContractResponse mapContract(ResultSet rs, int rowNum) throws SQLException {
		return new ContractResponse(rs.getLong("id"), rs.getString("contract_no"),
				rs.getString("external_contract_no"), rs.getLong("project_id"), rs.getString("contract_type"),
				nullableLong(rs, "main_contract_id"), rs.getString("name"), rs.getObject("signed_date", LocalDate.class),
				rs.getObject("effective_start_date", LocalDate.class),
				rs.getObject("effective_end_date", LocalDate.class), rs.getBigDecimal("amount"),
				rs.getString("status"), rs.getString("remark"), rs.getString("created_by"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getString("updated_by"),
				rs.getObject("updated_at", OffsetDateTime.class), rs.getString("activated_by"),
				rs.getObject("activated_at", OffsetDateTime.class), rs.getString("closed_by"),
				rs.getObject("closed_at", OffsetDateTime.class), rs.getString("close_reason"),
				rs.getString("terminated_by"), rs.getObject("terminated_at", OffsetDateTime.class),
				rs.getString("terminate_reason"), rs.getString("cancelled_by"),
				rs.getObject("cancelled_at", OffsetDateTime.class), rs.getString("cancel_reason"),
				rs.getLong("version"));
	}

	private SalesProjectContractType parseType(String value) {
		try {
			return SalesProjectContractType.valueOf(value);
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private void requireVersion(long currentVersion, Long requestedVersion) {
		if (requestedVersion == null || requestedVersion != currentVersion) {
			throw new BusinessException(ApiErrorCode.CONTRACT_CONCURRENT_MODIFICATION);
		}
	}

	private String validateReason(String value) {
		if (!hasText(value) || value.length() > 200) {
			throw new BusinessException(ApiErrorCode.CONTRACT_REASON_REQUIRED);
		}
		return value;
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		return prefix + OffsetDateTime.now().format(NUMBER_FORMATTER) + String.format("%03d",
				Math.floorMod(sequence.incrementAndGet(), 1000));
	}

	private void validateOptionalText(String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ContractCreateRequest(@NotNull String contractType, Long mainContractId, @NotNull String name,
			LocalDate signedDate, LocalDate effectiveStartDate, LocalDate effectiveEndDate, @NotNull BigDecimal amount,
			String externalContractNo, String remark) {
	}

	public record ContractUpdateRequest(@NotNull String name, LocalDate signedDate, LocalDate effectiveStartDate,
			LocalDate effectiveEndDate, @NotNull BigDecimal amount, String externalContractNo, String remark,
			@NotNull Long version) {
	}

	public record VersionedActionRequest(@NotNull Long version, String reason) {
	}

	public record ContractResponse(Long id, String contractNo, String externalContractNo, Long projectId,
			String contractType, Long mainContractId, String name, LocalDate signedDate, LocalDate effectiveStartDate,
			LocalDate effectiveEndDate, BigDecimal amount, String status, String remark, String createdByName,
			OffsetDateTime createdAt, String updatedByName, OffsetDateTime updatedAt, String activatedByName,
			OffsetDateTime activatedAt, String closedByName, OffsetDateTime closedAt, String closeReason,
			String terminatedByName, OffsetDateTime terminatedAt, String terminateReason, String cancelledByName,
			OffsetDateTime cancelledAt, String cancelReason, Long version) {
	}

	private record ValidatedContract(SalesProjectContractType contractType, Long mainContractId, String name,
			LocalDate signedDate, LocalDate effectiveStartDate, LocalDate effectiveEndDate, BigDecimal amount,
			String externalContractNo, String remark) {
	}

	private record ContractRow(Long id, Long projectId, SalesProjectContractType contractType, Long mainContractId,
			SalesProjectContractStatus status, long version) {
	}

	private record ProjectState(Long id, Long customerId, SalesProjectStatus status) {
	}

}
