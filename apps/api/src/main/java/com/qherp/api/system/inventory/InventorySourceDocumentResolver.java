package com.qherp.api.system.inventory;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventorySourceDocumentResolver {

	private final JdbcTemplate jdbcTemplate;

	public InventorySourceDocumentResolver(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public String documentNo(String sourceType, Long sourceId) {
		if (sourceType == null || sourceId == null) {
			return null;
		}
		try {
			return switch (sourceType) {
				case "INVENTORY_DOCUMENT" -> this.jdbcTemplate.queryForObject(
						"select document_no from inv_inventory_document where id = ?", String.class, sourceId);
				case "PURCHASE_RECEIPT" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from proc_purchase_receipt where id = ?", String.class, sourceId);
				case "PURCHASE_RETURN" -> this.jdbcTemplate.queryForObject(
						"select return_no from proc_purchase_return where id = ?", String.class, sourceId);
				case "SALES_SHIPMENT" -> this.jdbcTemplate.queryForObject(
						"select shipment_no from sal_sales_shipment where id = ?", String.class, sourceId);
				case "SALES_RETURN" -> this.jdbcTemplate.queryForObject(
						"select return_no from sal_sales_return where id = ?", String.class, sourceId);
				case "PRODUCTION_MATERIAL_ISSUE" -> this.jdbcTemplate.queryForObject(
						"select issue_no from mfg_material_issue where id = ?", String.class, sourceId);
				case "PRODUCTION_MATERIAL_RETURN" -> this.jdbcTemplate.queryForObject(
						"select return_no from mfg_material_return where id = ?", String.class, sourceId);
				case "PRODUCTION_MATERIAL_SUPPLEMENT" -> this.jdbcTemplate.queryForObject(
						"select supplement_no from mfg_material_supplement where id = ?", String.class, sourceId);
				case "PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION" -> this.jdbcTemplate.queryForObject(
						"select receipt_no from mfg_completion_receipt where id = ?", String.class, sourceId);
				case "QUALITY_INSPECTION" -> this.jdbcTemplate.queryForObject(
						"select inspection_no from qua_quality_inspection where id = ?", String.class, sourceId);
				case "QUALITY_STATUS_TRANSFER" -> "QUALITY_STATUS_TRANSFER-" + sourceId;
				default -> null;
			};
		}
		catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

	public Integer lineNo(String sourceType, Long sourceLineId) {
		if (sourceType == null || sourceLineId == null) {
			return null;
		}
		try {
			return switch (sourceType) {
				case "INVENTORY_DOCUMENT" -> this.jdbcTemplate.queryForObject(
						"select line_no from inv_inventory_document_line where id = ?", Integer.class, sourceLineId);
				case "PURCHASE_RECEIPT" -> this.jdbcTemplate.queryForObject(
						"select line_no from proc_purchase_receipt_line where id = ?", Integer.class, sourceLineId);
				case "PURCHASE_RETURN" -> this.jdbcTemplate.queryForObject(
						"select line_no from proc_purchase_return_line where id = ?", Integer.class, sourceLineId);
				case "SALES_SHIPMENT" -> this.jdbcTemplate.queryForObject(
						"select line_no from sal_sales_shipment_line where id = ?", Integer.class, sourceLineId);
				case "SALES_RETURN" -> this.jdbcTemplate.queryForObject(
						"select line_no from sal_sales_return_line where id = ?", Integer.class, sourceLineId);
				case "PRODUCTION_MATERIAL_ISSUE" -> this.jdbcTemplate.queryForObject(
						"select line_no from mfg_material_issue_line where id = ?", Integer.class, sourceLineId);
				case "PRODUCTION_MATERIAL_RETURN" -> this.jdbcTemplate.queryForObject(
						"select line_no from mfg_material_return_line where id = ?", Integer.class, sourceLineId);
				case "PRODUCTION_MATERIAL_SUPPLEMENT" -> this.jdbcTemplate.queryForObject(
						"select line_no from mfg_material_supplement_line where id = ?", Integer.class,
						sourceLineId);
				case "PRODUCTION_COMPLETION_RECEIPT", "PRODUCTION_COMPLETION" -> 1;
				default -> null;
			};
		}
		catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

}
