package com.qherp.api.system.bom;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import com.qherp.api.system.master.UnitConversionAdminService;
import com.qherp.api.system.platform.ApprovalExecutionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class BomEngineeringChangeAdminService {

	private static final String TARGET_TYPE = "BOM_ENGINEERING_CHANGE";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	private final ApprovalExecutionContext approvalExecutionContext;

	private final boolean enforceApprovalDirectActions;

	public BomEngineeringChangeAdminService(JdbcTemplate jdbcTemplate, AuditService auditService,
			ApprovalExecutionContext approvalExecutionContext,
			@Value("${qherp.platform.approval.enforce-direct-actions:true}") boolean enforceApprovalDirectActions) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
		this.approvalExecutionContext = approvalExecutionContext;
		this.enforceApprovalDirectActions = enforceApprovalDirectActions;
	}

	@Transactional(readOnly = true)
	public PageResponse<EngineeringChangeRecord> list(String keyword, String status, Long parentMaterialId,
			Long sourceBomId, Long targetBomId, int page, int pageSize) {
		QueryParts queryParts = queryParts(keyword, status, parentMaterialId, sourceBomId, targetBomId);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom_engineering_change e
				join mst_material p on p.id = e.parent_material_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<EngineeringChangeRecord> items = this.jdbcTemplate.query("""
				select e.id, e.eco_no, e.source_bom_id, sb.bom_code as source_bom_code,
				       sb.version_code as source_version_code, e.target_bom_id,
				       tb.bom_code as target_bom_code, tb.version_code as target_version_code, e.parent_material_id,
				       p.code as parent_material_code, p.name as parent_material_name, e.effective_from,
				       e.effective_to, e.change_reason, e.impact_scope, e.change_summary, e.status,
				       e.applied_by, e.applied_at, e.cancel_reason, e.remark, e.created_at, e.updated_at,
				       e.version
				from mfg_bom_engineering_change e
				join mfg_bom sb on sb.id = e.source_bom_id
				join mfg_bom tb on tb.id = e.target_bom_id
				join mst_material p on p.id = e.parent_material_id
				%s
				order by e.updated_at desc, e.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapRecord, args.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public EngineeringChangeRecord get(Long id) {
		return record(id).orElseThrow(this::notFound);
	}

	@Transactional
	public EngineeringChangeRecord create(EngineeringChangeRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		BomState source = bomState(request.sourceBomId()).orElseThrow(this::targetInvalid);
		BomState target = bomState(request.targetBomId()).orElseThrow(this::targetInvalid);
		validateCreateRequest(request, source, target);
		String ecoNo = request.ecoNo().trim();
		OffsetDateTime now = OffsetDateTime.now();
		try {
			Long id = this.jdbcTemplate.queryForObject("""
					insert into mfg_bom_engineering_change (
						eco_no, source_bom_id, target_bom_id, parent_material_id, effective_from, effective_to,
						change_reason, impact_scope, change_summary, status, remark,
						created_by, created_at, updated_by, updated_at
					)
					values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)
					returning id
					""", Long.class, ecoNo, source.id(), target.id(), source.parentMaterialId(),
					request.effectiveFrom(), request.effectiveTo(), request.changeReason(), request.impactScope(),
					request.changeSummary(), blankToNull(request.remark()), operator.username(), now,
					operator.username(), now);
			this.auditService.record(operator, "BOM_ECO_CREATE", TARGET_TYPE, id, ecoNo, servletRequest);
			return get(id);
		}
		catch (DuplicateKeyException exception) {
			throw duplicateEngineeringChangeException(exception);
		}
	}

	@Transactional
	public EngineeringChangeRecord update(Long id, EngineeringChangeRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		EngineeringChangeRow current = lockRow(id).orElseThrow(this::notFound);
		if (!"DRAFT".equals(current.status())) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_STATUS_INVALID);
		}
		requireVersion(current.version(), request.version());
		BomState source = bomState(request.sourceBomId()).orElseThrow(this::targetInvalid);
		BomState target = bomState(request.targetBomId()).orElseThrow(this::targetInvalid);
		validateCreateRequest(request, source, target);
		this.jdbcTemplate.update("""
				update mfg_bom_engineering_change
				set source_bom_id = ?, target_bom_id = ?, parent_material_id = ?, effective_from = ?,
				    effective_to = ?, change_reason = ?, impact_scope = ?, change_summary = ?, remark = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", source.id(), target.id(), source.parentMaterialId(), request.effectiveFrom(),
				request.effectiveTo(), request.changeReason(), request.impactScope(), request.changeSummary(),
				blankToNull(request.remark()), operator.username(), OffsetDateTime.now(), id, current.version());
		this.auditService.record(operator, "BOM_ECO_UPDATE", TARGET_TYPE, id, current.ecoNo(), servletRequest);
		return get(id);
	}

	@Transactional
	public EngineeringChangeRecord apply(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		if (this.enforceApprovalDirectActions
				&& !this.approvalExecutionContext.allows("BOM_ECO_APPLICATION", id)) {
			throw new BusinessException(ApiErrorCode.APPROVAL_REQUIRED);
		}
		EngineeringChangeRow eco = lockRow(id).orElseThrow(this::notFound);
		requireVersion(eco.version(), request == null ? null : request.version());
		if (!"DRAFT".equals(eco.status())) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_STATUS_INVALID);
		}
		BomState sourceBefore = lockBom(eco.sourceBomId()).orElseThrow(this::targetInvalid);
		BomState targetBefore = lockBom(eco.targetBomId()).orElseThrow(this::targetInvalid);
		validateApply(eco, sourceBefore, targetBefore);
		lockParentBomSet(eco.parentMaterialId());
		LocalDate sourceEffectiveTo = eco.effectiveFrom().minusDays(1);
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbcTemplate.update("""
				update mfg_bom
				set effective_to = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", sourceEffectiveTo, operator.username(), now, eco.sourceBomId());
		this.jdbcTemplate.update("""
				update mfg_bom
				set status = 'ENABLED', effective_from = ?, effective_to = ?, enabled_by = ?, enabled_at = ?,
				    updated_by = ?, updated_at = ?, version = version + 1
				where id = ?
				""", eco.effectiveFrom(), eco.effectiveTo(), operator.username(), now, operator.username(), now,
				eco.targetBomId());
		validateNoEnabledOverlap(eco.targetBomId(), eco.parentMaterialId(), eco.effectiveFrom(), eco.effectiveTo());
		this.jdbcTemplate.update("""
				update mfg_bom_engineering_change
				set status = 'APPLIED', applied_by = ?, applied_at = ?, updated_by = ?, updated_at = ?,
				    version = version + 1
				where id = ? and version = ?
				""", operator.username(), now, operator.username(), now, id, eco.version());
		this.auditService.record(operator, "BOM_ECO_APPLY", TARGET_TYPE, id, eco.ecoNo(), servletRequest);
		EngineeringChangeRecord applied = get(id);
		return applied.withStates(sourceBefore, bomState(eco.sourceBomId()).orElseThrow(), targetBefore,
				bomState(eco.targetBomId()).orElseThrow());
	}

	@Transactional
	public EngineeringChangeRecord applyFromApproval(Long id, VersionRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		return this.approvalExecutionContext.run("BOM_ECO_APPLICATION", id,
				() -> apply(id, request, operator, servletRequest));
	}

	@Transactional
	public EngineeringChangeRecord cancel(Long id, CancelRequest request, CurrentUser operator,
			HttpServletRequest servletRequest) {
		EngineeringChangeRow eco = lockRow(id).orElseThrow(this::notFound);
		requireVersion(eco.version(), request == null ? null : request.version());
		if (!"DRAFT".equals(eco.status())) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_STATUS_INVALID);
		}
		this.jdbcTemplate.update("""
				update mfg_bom_engineering_change
				set status = 'CANCELLED', cancel_reason = ?, updated_by = ?, updated_at = ?, version = version + 1
				where id = ? and version = ?
				""", blankToNull(request.reason()), operator.username(), OffsetDateTime.now(), id, eco.version());
		this.auditService.record(operator, "BOM_ECO_CANCEL", TARGET_TYPE, id, eco.ecoNo(), servletRequest);
		return get(id);
	}

	@Transactional(readOnly = true)
	public UnitConversionAdminService.CandidatePage sourceBomCandidates(String keyword, Long parentMaterialId,
			String selectedIds, int page, int pageSize) {
		return bomCandidates(keyword, parentMaterialId, null, selectedIds, page, pageSize, BomCandidateRole.SOURCE);
	}

	@Transactional(readOnly = true)
	public UnitConversionAdminService.CandidatePage targetBomCandidates(String keyword, Long sourceBomId,
			String selectedIds, int page, int pageSize) {
		BomState source = sourceBomId == null ? null : bomState(sourceBomId).orElse(null);
		return bomCandidates(keyword, source == null ? null : source.parentMaterialId(), source, selectedIds, page,
				pageSize, BomCandidateRole.TARGET);
	}

	private void validateCreateRequest(EngineeringChangeRequest request, BomState source, BomState target) {
		if (!hasText(request.ecoNo())) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
		if (source.id().equals(target.id()) || !source.parentMaterialId().equals(target.parentMaterialId())
				|| !"ENABLED".equals(source.status()) || !"DRAFT".equals(target.status())) {
			throw targetInvalid();
		}
		if (request.effectiveFrom() == null || (request.effectiveTo() != null
				&& request.effectiveFrom().isAfter(request.effectiveTo()))) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_EFFECTIVE_INVALID);
		}
		if (source.effectiveFrom() != null && !request.effectiveFrom().isAfter(source.effectiveFrom())) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_EFFECTIVE_INVALID);
		}
	}

	private void validateApply(EngineeringChangeRow eco, BomState source, BomState target) {
		if (!"ENABLED".equals(source.status()) || !"DRAFT".equals(target.status())
				|| !source.parentMaterialId().equals(target.parentMaterialId())
				|| !source.parentMaterialId().equals(eco.parentMaterialId())) {
			throw targetInvalid();
		}
		validateBomItemsConvertible(target.id());
	}

	private void validateBomItemsConvertible(Long bomId) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom_item
				where bom_id = ?
				and (quantity_basis = 'LEGACY_BUSINESS_UNIT' or base_unit_id is null or base_quantity is null)
				""", Long.class, bomId);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.BOM_LEGACY_UNIT_CONVERSION_REQUIRED);
		}
	}

	private void validateNoEnabledOverlap(Long bomId, Long parentMaterialId, LocalDate effectiveFrom,
			LocalDate effectiveTo) {
		Long count = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom
				where parent_material_id = ?
				and status = 'ENABLED'
				and id <> ?
				and coalesce(effective_from, date '0001-01-01') <= coalesce(cast(? as date), date '9999-12-31')
				and coalesce(cast(? as date), date '0001-01-01') <= coalesce(effective_to, date '9999-12-31')
				""", Long.class, parentMaterialId, bomId, effectiveTo, effectiveFrom);
		if (count != null && count > 0) {
			throw new BusinessException(ApiErrorCode.BOM_EFFECTIVE_DATE_OVERLAP);
		}
	}

	private void lockParentBomSet(Long parentMaterialId) {
		this.jdbcTemplate.query("select id from mst_material where id = ? for update", (rs, rowNum) -> rs.getLong("id"),
				parentMaterialId);
		this.jdbcTemplate.query("select id from mfg_bom where parent_material_id = ? for update",
				(rs, rowNum) -> rs.getLong("id"), parentMaterialId);
	}

	private Optional<BomState> bomState(Long bomId) {
		return this.jdbcTemplate.query("""
				select id, bom_code, parent_material_id, version_code, status, effective_from, effective_to, version
				from mfg_bom
				where id = ?
				""", this::mapBomState, bomId).stream().findFirst();
	}

	private Optional<BomState> lockBom(Long bomId) {
		return this.jdbcTemplate.query("""
				select id, bom_code, parent_material_id, version_code, status, effective_from, effective_to, version
				from mfg_bom
				where id = ?
				for update
				""", this::mapBomState, bomId).stream().findFirst();
	}

	private BomState mapBomState(ResultSet rs, int rowNum) throws SQLException {
		return new BomState(rs.getLong("id"), rs.getString("bom_code"), rs.getLong("parent_material_id"),
				rs.getString("version_code"), rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
				rs.getObject("effective_to", LocalDate.class), rs.getLong("version"));
	}

	private Optional<EngineeringChangeRecord> record(Long id) {
		return this.jdbcTemplate.query("""
				select e.id, e.eco_no, e.source_bom_id, sb.bom_code as source_bom_code,
				       sb.version_code as source_version_code, e.target_bom_id,
				       tb.bom_code as target_bom_code, tb.version_code as target_version_code, e.parent_material_id,
				       p.code as parent_material_code, p.name as parent_material_name, e.effective_from,
				       e.effective_to, e.change_reason, e.impact_scope, e.change_summary, e.status,
				       e.applied_by, e.applied_at, e.cancel_reason, e.remark, e.created_at, e.updated_at,
				       e.version
				from mfg_bom_engineering_change e
				join mfg_bom sb on sb.id = e.source_bom_id
				join mfg_bom tb on tb.id = e.target_bom_id
				join mst_material p on p.id = e.parent_material_id
				where e.id = ?
				""", this::mapRecord, id).stream().findFirst();
	}

	private Optional<EngineeringChangeRow> lockRow(Long id) {
		return this.jdbcTemplate.query("""
				select id, eco_no, source_bom_id, target_bom_id, parent_material_id, effective_from, effective_to,
				       status, version
				from mfg_bom_engineering_change
				where id = ?
				for update
				""", (rs, rowNum) -> new EngineeringChangeRow(rs.getLong("id"), rs.getString("eco_no"),
				rs.getLong("source_bom_id"), rs.getLong("target_bom_id"), rs.getLong("parent_material_id"),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
				rs.getString("status"), rs.getLong("version")), id).stream().findFirst();
	}

	private UnitConversionAdminService.CandidatePage bomCandidates(String keyword, Long parentMaterialId,
			BomState source, String selectedIds, int page, int pageSize, BomCandidateRole role) {
		QueryParts queryParts = bomCandidateQueryParts(keyword, parentMaterialId, source);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from mfg_bom b
				join mst_material p on p.id = b.parent_material_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> args = new ArrayList<>(queryParts.args());
		args.add(limit(pageSize));
		args.add(offset(page, pageSize));
		List<UnitConversionAdminService.CandidateItem> items = this.jdbcTemplate.query("""
				select b.id, b.bom_code, b.name, b.status, b.parent_material_id, b.version_code,
				       p.code as parent_material_code
				from mfg_bom b
				join mst_material p on p.id = b.parent_material_id
				%s
				order by b.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> mapBomCandidate(rs, role, source),
				args.toArray());
		List<Long> selected = parseIds(selectedIds);
		List<UnitConversionAdminService.CandidateItem> selectedItems = selected.isEmpty() ? List.of()
				: this.jdbcTemplate.query("""
						select b.id, b.bom_code, b.name, b.status, b.parent_material_id, b.version_code,
						       p.code as parent_material_code
						from mfg_bom b
						join mst_material p on p.id = b.parent_material_id
						where b.id in (%s)
						order by b.id desc
						""".formatted(placeholders(selected.size())), (rs, rowNum) -> mapBomCandidate(rs, role,
								source), selected.toArray());
		return new UnitConversionAdminService.CandidatePage(items, selectedItems, Math.max(page, 1), limit(pageSize),
				total, totalPages(total, limit(pageSize)));
	}

	private QueryParts bomCandidateQueryParts(String keyword, Long parentMaterialId, BomState source) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(b.bom_code ilike ? or b.name ilike ? or b.version_code ilike ? or p.code ilike ? or p.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (parentMaterialId != null) {
			conditions.add("b.parent_material_id = ?");
			args.add(parentMaterialId);
		}
		if (source != null) {
			conditions.add("b.id <> ?");
			args.add(source.id());
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private UnitConversionAdminService.CandidateItem mapBomCandidate(ResultSet rs, BomCandidateRole role,
			BomState source) throws SQLException {
		Long id = rs.getLong("id");
		Long parentMaterialId = rs.getLong("parent_material_id");
		String status = rs.getString("status");
		String disabledReason = switch (role) {
			case SOURCE -> !"ENABLED".equals(status) ? "来源BOM必须已发布" : null;
			case TARGET -> targetDisabledReason(id, parentMaterialId, status, source);
		};
		String summary = rs.getString("parent_material_code") + "/" + rs.getString("version_code");
		return new UnitConversionAdminService.CandidateItem(id, rs.getString("bom_code"), rs.getString("name"),
				status, disabledReason != null, disabledReason, summary);
	}

	private String targetDisabledReason(Long id, Long parentMaterialId, String status, BomState source) {
		if (!"DRAFT".equals(status)) {
			return "目标BOM必须为草稿";
		}
		if (source != null && id.equals(source.id())) {
			return "目标BOM不能等于来源BOM";
		}
		if (source != null && !parentMaterialId.equals(source.parentMaterialId())) {
			return "目标BOM必须与来源BOM属于同一父项";
		}
		return null;
	}

	private BusinessException duplicateEngineeringChangeException(DuplicateKeyException exception) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		if (message != null && message.contains("uk_mfg_bom_engineering_change_no")) {
			return new BusinessException(ApiErrorCode.CODING_RULE_GENERATE_CONFLICT);
		}
		return new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_TARGET_INVALID);
	}

	private EngineeringChangeRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
		return new EngineeringChangeRecord(rs.getLong("id"), rs.getString("eco_no"), rs.getLong("source_bom_id"),
				rs.getString("source_bom_code"), rs.getString("source_version_code"), rs.getLong("target_bom_id"),
				rs.getString("target_bom_code"), rs.getString("target_version_code"), rs.getLong("parent_material_id"),
				rs.getString("parent_material_code"), rs.getString("parent_material_name"),
				rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
				rs.getString("change_reason"), rs.getString("impact_scope"), rs.getString("change_summary"),
				rs.getString("status"), rs.getString("applied_by"), rs.getObject("applied_at", OffsetDateTime.class),
				rs.getString("cancel_reason"), rs.getString("remark"),
				rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
				rs.getLong("version"), null, null, null, null);
	}

	private QueryParts queryParts(String keyword, String status, Long parentMaterialId, Long sourceBomId,
			Long targetBomId) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		if (hasText(keyword)) {
			conditions.add("(e.eco_no ilike ? or e.change_summary ilike ? or p.code ilike ? or p.name ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("e.status = ?");
			args.add(status);
		}
		if (parentMaterialId != null) {
			conditions.add("e.parent_material_id = ?");
			args.add(parentMaterialId);
		}
		if (sourceBomId != null) {
			conditions.add("e.source_bom_id = ?");
			args.add(sourceBomId);
		}
		if (targetBomId != null) {
			conditions.add("e.target_bom_id = ?");
			args.add(targetBomId);
		}
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private void requireVersion(Long currentVersion, Long requestVersion) {
		if (requestVersion == null || !currentVersion.equals(requestVersion)) {
			throw new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_CONCURRENT_MODIFICATION);
		}
	}

	private BusinessException notFound() {
		return new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_NOT_FOUND);
	}

	private BusinessException targetInvalid() {
		return new BusinessException(ApiErrorCode.BOM_ENGINEERING_CHANGE_TARGET_INVALID);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static int totalPages(long total, int pageSize) {
		return (int) Math.ceil(total / (double) pageSize);
	}

	private static List<Long> parseIds(String selectedIds) {
		if (!hasText(selectedIds)) {
			return List.of();
		}
		return Arrays.stream(selectedIds.split(","))
			.map(String::trim)
			.filter(BomEngineeringChangeAdminService::hasText)
			.map(Long::parseLong)
			.toList();
	}

	private static String placeholders(int size) {
		return String.join(",", java.util.Collections.nCopies(size, "?"));
	}

	private static String blankToNull(String value) {
		return hasText(value) ? value : null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record EngineeringChangeRequest(String ecoNo, @NotNull Long sourceBomId, @NotNull Long targetBomId,
			@NotNull LocalDate effectiveFrom, LocalDate effectiveTo, @NotNull String changeReason,
			@NotNull String impactScope, @NotNull String changeSummary, String remark, Long version) {
	}

	public record VersionRequest(@NotNull Long version) {
	}

	public record CancelRequest(@NotNull Long version, String reason) {
	}

	public record EngineeringChangeRecord(Long id, String ecoNo, Long sourceBomId, String sourceBomCode,
			String sourceVersionCode, Long targetBomId, String targetBomCode, String targetVersionCode,
			Long parentMaterialId, String parentMaterialCode, String parentMaterialName, LocalDate effectiveFrom,
			LocalDate effectiveTo, String changeReason, String impactScope, String changeSummary, String status,
			String appliedBy, OffsetDateTime appliedAt, String cancelReason, String remark, OffsetDateTime createdAt,
			OffsetDateTime updatedAt, Long version,
			BomState sourceBomBefore, BomState sourceBomAfter, BomState targetBomBefore, BomState targetBomAfter) {

		EngineeringChangeRecord withStates(BomState sourceBefore, BomState sourceAfter, BomState targetBefore,
				BomState targetAfter) {
			return new EngineeringChangeRecord(this.id, this.ecoNo, this.sourceBomId, this.sourceBomCode,
					this.sourceVersionCode, this.targetBomId, this.targetBomCode, this.targetVersionCode,
					this.parentMaterialId, this.parentMaterialCode, this.parentMaterialName, this.effectiveFrom,
					this.effectiveTo, this.changeReason, this.impactScope, this.changeSummary, this.status,
					this.appliedBy, this.appliedAt, this.cancelReason, this.remark, this.createdAt, this.updatedAt,
					this.version, sourceBefore, sourceAfter, targetBefore, targetAfter);
		}

	}

	public record BomState(Long id, String bomCode, Long parentMaterialId, String versionCode, String status,
			LocalDate effectiveFrom, LocalDate effectiveTo, Long version) {
	}

	private enum BomCandidateRole {

		SOURCE,

		TARGET

	}

	private record EngineeringChangeRow(Long id, String ecoNo, Long sourceBomId, Long targetBomId,
			Long parentMaterialId, LocalDate effectiveFrom, LocalDate effectiveTo, String status, Long version) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
