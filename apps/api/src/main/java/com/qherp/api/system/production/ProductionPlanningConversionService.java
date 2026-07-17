package com.qherp.api.system.production;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProductionPlanningConversionService {

	private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

	private static final int MAX_NO_ATTEMPTS = 3;

	private static final AtomicInteger WORK_ORDER_NO_SEQUENCE = new AtomicInteger();

	private static final AtomicInteger OUTSOURCING_ORDER_NO_SEQUENCE = new AtomicInteger();

	private final JdbcTemplate jdbcTemplate;

	public ProductionPlanningConversionService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public ConvertedProductionTarget createDraftWorkOrder(PlanningProductionSuggestion suggestion, String operatorName,
			OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String workOrderNo = nextNo("MFG-WO", WORK_ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_work_order (
							work_order_no, product_material_id, bom_id, planned_quantity, reported_quantity,
							qualified_quantity, defective_quantity, received_quantity, issue_warehouse_id,
							receipt_warehouse_id, planned_start_date, planned_finish_date, status, ownership_type,
							project_id, source_mrp_run_id, source_mrp_requirement_line_id, source_mrp_suggestion_id,
							remark, created_by, created_at, updated_by, updated_at
						)
						values (?, ?, null, ?, 0, 0, 0, 0, null, null, null, null, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, workOrderNo, suggestion.materialId(), suggestion.quantity(),
						suggestion.ownershipType(), suggestion.projectId(), suggestion.runId(),
						suggestion.requirementLineId(), suggestion.suggestionId(), suggestion.remark(), operatorName,
						now, operatorName, now);
				return new ConvertedProductionTarget("PRODUCTION_WORK_ORDER", id, workOrderNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	public ConvertedProductionTarget createDraftOutsourcingOrder(PlanningProductionSuggestion suggestion,
			String operatorName, OffsetDateTime now) {
		for (int attempt = 1; attempt <= MAX_NO_ATTEMPTS; attempt++) {
			String orderNo = nextNo("MFG-OS", OUTSOURCING_ORDER_NO_SEQUENCE);
			try {
				Long id = this.jdbcTemplate.queryForObject("""
						insert into mfg_outsourcing_order (
							outsourcing_order_no, supplier_id, product_material_id, bom_id, planned_quantity,
							issued_quantity, received_quantity, issue_warehouse_id, receipt_warehouse_id,
							planned_issue_date, planned_receipt_date, status, ownership_type, project_id,
							source_mrp_run_id, source_mrp_requirement_line_id, source_mrp_suggestion_id, remark,
							created_by, created_at, updated_by, updated_at
						)
						values (?, null, ?, null, ?, 0, 0, null, null, null, null, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						returning id
						""", Long.class, orderNo, suggestion.materialId(), suggestion.quantity(),
						suggestion.ownershipType(), suggestion.projectId(), suggestion.runId(),
						suggestion.requirementLineId(), suggestion.suggestionId(), suggestion.remark(), operatorName,
						now, operatorName, now);
				return new ConvertedProductionTarget("OUTSOURCING_ORDER", id, orderNo);
			}
			catch (DuplicateKeyException exception) {
				if (attempt < MAX_NO_ATTEMPTS) {
					continue;
				}
				throw exception;
			}
		}
		throw new BusinessException(ApiErrorCode.CONFLICT);
	}

	private String nextNo(String prefix, AtomicInteger sequence) {
		int value = Math.floorMod(sequence.getAndIncrement(), 1000);
		return prefix + "-" + LocalDateTime.now().format(NUMBER_FORMATTER) + "-" + String.format("%03d", value);
	}

	public record PlanningProductionSuggestion(Long suggestionId, Long runId, Long requirementLineId, Long materialId,
			Long unitId, BigDecimal quantity, String ownershipType, Long projectId, LocalDate requiredDate,
			String remark) {
	}

	public record ConvertedProductionTarget(String targetObjectType, Long targetObjectId, String targetObjectNo) {
	}

}
