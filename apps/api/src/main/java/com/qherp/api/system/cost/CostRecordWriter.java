package com.qherp.api.system.cost;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CostRecordWriter {

	private static final String TARGET_TYPE = "MFG_COST_RECORD";

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger COST_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public CostRecordWriter(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional
	public void writeMaterialIssue(Long issueId, CurrentUser operator, HttpServletRequest servletRequest) {
		List<MaterialIssueCostSource> sources = this.jdbcTemplate.query("""
				select i.id as issue_id, i.issue_no, i.work_order_id, wo.product_material_id, i.business_date,
				       l.id as line_id, l.work_order_material_id, l.material_id, l.unit_id, l.quantity
				from mfg_material_issue i
				join mfg_work_order wo on wo.id = i.work_order_id
				join mfg_material_issue_line l on l.issue_id = i.id
				where i.id = ?
				and i.status = 'POSTED'
				order by l.line_no asc, l.id asc
				""", this::mapMaterialIssueCostSource, issueId);
		if (sources.isEmpty()) {
			throw new BusinessException(ApiErrorCode.COST_SOURCE_DOCUMENT_STATUS_INVALID);
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (MaterialIssueCostSource source : sources) {
			Long recordId = insertAutoRecordWithRetry(new AutoCostRecord(source.workOrderId(),
					source.productMaterialId(), CostType.MATERIAL, CostSourceDocumentType.PRODUCTION_MATERIAL_ISSUE,
					source.issueNo(), source.issueId(), source.lineId(), source.workOrderMaterialId(),
					source.materialId(), source.unitId(), source.quantity(), source.businessDate(), "生产领料自动归集"),
					operator.username(), now);
			this.auditService.record(operator, "MFG_COST_RECORD_AUTO_CREATE", TARGET_TYPE, recordId,
					source.issueNo(), servletRequest);
		}
	}

	@Transactional
	public void writeWorkReport(Long reportId, CurrentUser operator, HttpServletRequest servletRequest) {
		WorkReportCostSource source = this.jdbcTemplate.query("""
				select r.id as report_id, r.report_no, r.work_order_id, wo.product_material_id, r.business_date,
				       (r.qualified_quantity + r.defective_quantity) as total_quantity
				from mfg_work_report r
				join mfg_work_order wo on wo.id = r.work_order_id
				where r.id = ?
				and r.status = 'POSTED'
				""", this::mapWorkReportCostSource, reportId).stream().findFirst()
			.orElseThrow(() -> new BusinessException(ApiErrorCode.COST_SOURCE_DOCUMENT_STATUS_INVALID));
		OffsetDateTime now = OffsetDateTime.now();
		Long recordId = insertAutoRecordWithRetry(new AutoCostRecord(source.workOrderId(), source.productMaterialId(),
				CostType.LABOR, CostSourceDocumentType.PRODUCTION_WORK_REPORT, source.reportNo(), source.reportId(),
				null, null, null, null, source.totalQuantity(), source.businessDate(), "生产报工自动归集"),
				operator.username(), now);
		this.auditService.record(operator, "MFG_COST_RECORD_AUTO_CREATE", TARGET_TYPE, recordId, source.reportNo(),
				servletRequest);
	}

	private Long insertAutoRecordWithRetry(AutoCostRecord record, String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String recordNo = nextNo();
			try {
				return this.jdbcTemplate.queryForObject("""
						insert into mfg_cost_record (
							record_no, work_order_id, product_material_id, cost_type, source_type,
							source_document_type, source_document_no, source_document_id, source_line_id,
							work_order_material_id, material_id, unit_id, quantity, basis_type, business_date,
							status, remark, recorded_by, recorded_at, created_by, created_at, updated_by,
							updated_at
						)
						values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, recordNo, record.workOrderId(), record.productMaterialId(),
						record.costType().name(), CostSourceType.AUTO_PRODUCTION.name(),
						record.sourceDocumentType().name(), record.sourceDocumentNo(), record.sourceDocumentId(),
						record.sourceLineId(), record.workOrderMaterialId(), record.materialId(), record.unitId(),
						record.quantity(), CostBasisType.SOURCE_QUANTITY_ONLY.name(), record.businessDate(),
						CostRecordStatus.ACTIVE.name(), record.remark(), operatorName, now, operatorName, now,
						operatorName, now);
			}
			catch (DuplicateKeyException exception) {
				if (containsConstraint(exception, "uk_mfg_cost_record_no") && attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				if (containsConstraint(exception, "uk_mfg_cost_record_source_line")
						|| containsConstraint(exception, "uk_mfg_cost_record_source_document")
						|| containsConstraint(exception, "uk_mfg_cost_record_output_trace")) {
					throw new BusinessException(ApiErrorCode.COST_SOURCE_DUPLICATED);
				}
				throw new BusinessException(ApiErrorCode.CONFLICT);
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private MaterialIssueCostSource mapMaterialIssueCostSource(ResultSet rs, int rowNum) throws SQLException {
		return new MaterialIssueCostSource(rs.getLong("issue_id"), rs.getString("issue_no"),
				rs.getLong("work_order_id"), rs.getLong("product_material_id"),
				rs.getObject("business_date", LocalDate.class), rs.getLong("line_id"),
				rs.getLong("work_order_material_id"), rs.getLong("material_id"), rs.getLong("unit_id"),
				rs.getBigDecimal("quantity"));
	}

	private WorkReportCostSource mapWorkReportCostSource(ResultSet rs, int rowNum) throws SQLException {
		return new WorkReportCostSource(rs.getLong("report_id"), rs.getString("report_no"),
				rs.getLong("work_order_id"), rs.getLong("product_material_id"),
				rs.getObject("business_date", LocalDate.class), rs.getBigDecimal("total_quantity"));
	}

	private String nextNo() {
		int value = Math.floorMod(COST_NO_SEQUENCE.getAndIncrement(), 1000);
		return "COST-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	private boolean containsConstraint(DuplicateKeyException exception, String constraintName) {
		String message = exception.getMostSpecificCause() == null ? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(constraintName);
	}

	private record AutoCostRecord(Long workOrderId, Long productMaterialId, CostType costType,
			CostSourceDocumentType sourceDocumentType, String sourceDocumentNo, Long sourceDocumentId,
			Long sourceLineId, Long workOrderMaterialId, Long materialId, Long unitId, BigDecimal quantity,
			LocalDate businessDate, String remark) {
	}

	private record MaterialIssueCostSource(Long issueId, String issueNo, Long workOrderId, Long productMaterialId,
			LocalDate businessDate, Long lineId, Long workOrderMaterialId, Long materialId, Long unitId,
			BigDecimal quantity) {
	}

	private record WorkReportCostSource(Long reportId, String reportNo, Long workOrderId, Long productMaterialId,
			LocalDate businessDate, BigDecimal totalQuantity) {
	}

}
