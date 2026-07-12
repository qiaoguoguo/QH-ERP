package com.qherp.api.system.salesproject;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.common.PageResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.system.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderProjectLinkService {

	private static final String PROJECT_TARGET = "SALES_PROJECT";

	private final JdbcTemplate jdbcTemplate;

	private final AuditService auditService;

	public SalesOrderProjectLinkService(JdbcTemplate jdbcTemplate, AuditService auditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public ProjectLink validateForDraftSave(Long customerId, Long projectId, Long contractId) {
		if ((projectId == null) != (contractId == null)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_PROJECT_PAIR_REQUIRED);
		}
		if (projectId == null) {
			return null;
		}
		return validateActiveEffective(customerId, projectId, contractId);
	}

	@Transactional(readOnly = true)
	public ProjectLink validateForConfirm(Long customerId, Long projectId, Long contractId) {
		if ((projectId == null) != (contractId == null)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_PROJECT_PAIR_REQUIRED);
		}
		if (projectId == null) {
			return null;
		}
		return validateActiveEffective(customerId, projectId, contractId);
	}

	@Transactional(readOnly = true)
	public ProjectLink findLink(Long projectId, Long contractId) {
		if (projectId == null || contractId == null) {
			return null;
		}
		return this.jdbcTemplate.query("""
				select p.id as project_id, p.project_no, p.name as project_name, p.customer_id,
				       c.id as contract_id, c.contract_no, c.external_contract_no
				from sal_project p
				join sal_project_contract c on c.project_id = p.id
				where p.id = ?
				and c.id = ?
				""", this::mapProjectLink, projectId, contractId).stream().findFirst().orElse(null);
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderLinkCandidateResponse> listOrderLinkCandidates(Long customerId, String keyword, int page,
			int pageSize) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("p.status = 'ACTIVE'");
		conditions.add("c.status = 'EFFECTIVE'");
		if (customerId != null) {
			conditions.add("p.customer_id = ?");
			args.add(customerId);
		}
		if (hasText(keyword)) {
			conditions.add("(p.project_no ilike ? or p.name ilike ? or c.contract_no ilike ? or c.name ilike ? "
					+ "or c.external_contract_no ilike ?)");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		QueryParts queryParts = where(conditions, args);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_project p
				join sal_project_contract c on c.project_id = p.id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> pageArgs = new ArrayList<>(queryParts.args());
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<OrderLinkCandidateResponse> items = this.jdbcTemplate.query("""
				select p.id as project_id, p.project_no, p.name as project_name, p.customer_id,
				       c.id as contract_id, c.contract_no, c.external_contract_no
				from sal_project p
				join sal_project_contract c on c.project_id = p.id
				%s
				order by p.updated_at desc, p.id desc, c.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), (rs, rowNum) -> new OrderLinkCandidateResponse(
				rs.getLong("project_id"), rs.getString("project_no"), rs.getString("project_name"),
				rs.getLong("customer_id"), rs.getLong("contract_id"), rs.getString("contract_no"),
				rs.getString("external_contract_no")), pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProjectSalesOrderResponse> listProjectSalesOrders(Long projectId, String keyword,
			String status, int page, int pageSize, CurrentUser currentUser) {
		if (!currentUser.permissions().contains("sales:order:view")) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
		ensureProjectExists(projectId);
		QueryParts queryParts = salesOrderQueryParts(projectId, keyword, status);
		long total = this.jdbcTemplate.queryForObject("""
				select count(*)
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project_contract pc on pc.id = o.contract_id
				%s
				""".formatted(queryParts.where()), Long.class, queryParts.args().toArray());
		List<Object> pageArgs = new ArrayList<>(queryParts.args());
		pageArgs.add(limit(pageSize));
		pageArgs.add(offset(page, pageSize));
		List<ProjectSalesOrderResponse> items = this.jdbcTemplate.query("""
				select o.id, o.order_no, o.customer_id, c.name as customer_name, o.order_date,
				       o.expected_ship_date, o.status, o.project_id, o.contract_id, pc.contract_no,
				       pc.external_contract_no,
				       coalesce((select count(*) from sal_sales_order_line l where l.order_id = o.id), 0) as line_count,
				       coalesce((select sum(l.quantity * l.unit_price) from sal_sales_order_line l where l.order_id = o.id), 0) as business_amount
				from sal_sales_order o
				join mst_customer c on c.id = o.customer_id
				left join sal_project_contract pc on pc.id = o.contract_id
				%s
				order by o.updated_at desc, o.id desc
				limit ? offset ?
				""".formatted(queryParts.where()), this::mapProjectSalesOrder, pageArgs.toArray());
		return PageResponse.of(items, page, limit(pageSize), total);
	}

	@Transactional(readOnly = true)
	public SalesOrderSummary salesOrderSummary(Long projectId) {
		SalesOrderSummary summary = this.jdbcTemplate.queryForObject("""
				select count(*) as order_count,
				       coalesce(sum(line_amount.business_amount), 0) as business_amount
				from sal_sales_order o
				left join (
					select order_id, sum(quantity * unit_price) as business_amount
					from sal_sales_order_line
					group by order_id
				) line_amount on line_amount.order_id = o.id
				where o.project_id = ?
				""", (rs, rowNum) -> new SalesOrderSummary(rs.getLong("order_count"),
				rs.getBigDecimal("business_amount")), projectId);
		return summary == null ? new SalesOrderSummary(0L, BigDecimal.ZERO) : summary;
	}

	public void recordProjectLinkAudit(CurrentUser operator, String orderNo, ProjectLink oldLink, ProjectLink newLink,
			HttpServletRequest request) {
		if (sameLink(oldLink, newLink)) {
			return;
		}
		String summary = "订单 " + orderNo + " 项目合同关联 " + describe(oldLink) + " -> " + describe(newLink);
		if (oldLink != null) {
			this.auditService.record(operator, "SALES_ORDER_PROJECT_UNLINK", PROJECT_TARGET, oldLink.projectId(),
					summary, request);
		}
		if (newLink != null) {
			this.auditService.record(operator, "SALES_ORDER_PROJECT_LINK", PROJECT_TARGET, newLink.projectId(),
					summary, request);
		}
	}

	private ProjectLink validateActiveEffective(Long customerId, Long projectId, Long contractId) {
		ProjectContractState state = this.jdbcTemplate.query("""
				select p.id as project_id, p.project_no, p.name as project_name, p.customer_id, p.status as project_status,
				       c.id as contract_id, c.contract_no, c.external_contract_no, c.status as contract_status,
				       c.contract_type, c.project_id as contract_project_id
				from sal_project p
				left join sal_project_contract c on c.id = ?
				where p.id = ?
				""", this::mapProjectContractState, contractId, projectId).stream().findFirst().orElse(null);
		if (state == null || state.projectStatus() != SalesProjectStatus.ACTIVE) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_PROJECT_INVALID);
		}
		if (state.contractId() == null || !state.contractProjectId().equals(projectId)
				|| state.contractStatus() != SalesProjectContractStatus.EFFECTIVE) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_CONTRACT_INVALID);
		}
		if (!state.customerId().equals(customerId)) {
			throw new BusinessException(ApiErrorCode.SALES_ORDER_PROJECT_CUSTOMER_MISMATCH);
		}
		return new ProjectLink(state.projectId(), state.projectNo(), state.projectName(), state.customerId(),
				state.contractId(), state.contractNo(), state.externalContractNo());
	}

	private QueryParts salesOrderQueryParts(Long projectId, String keyword, String status) {
		List<String> conditions = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		conditions.add("o.project_id = ?");
		args.add(projectId);
		if (hasText(keyword)) {
			conditions.add("(o.order_no ilike ? or c.name ilike ? or exists ("
					+ "select 1 from sal_sales_order_line l join mst_material m on m.id = l.material_id "
					+ "where l.order_id = o.id and (m.code ilike ? or m.name ilike ?)))");
			String like = "%" + keyword + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (hasText(status)) {
			conditions.add("o.status = ?");
			args.add(status);
		}
		return where(conditions, args);
	}

	private void ensureProjectExists(Long projectId) {
		Long count = this.jdbcTemplate.queryForObject("select count(*) from sal_project where id = ?", Long.class,
				projectId);
		if (count == null || count == 0) {
			throw new BusinessException(ApiErrorCode.PROJECT_NOT_FOUND);
		}
	}

	private ProjectContractState mapProjectContractState(ResultSet rs, int rowNum) throws SQLException {
		Long contractId = nullableLong(rs, "contract_id");
		return new ProjectContractState(rs.getLong("project_id"), rs.getString("project_no"),
				rs.getString("project_name"), rs.getLong("customer_id"),
				SalesProjectStatus.valueOf(rs.getString("project_status")), contractId,
				contractId == null ? null : rs.getString("contract_no"),
				contractId == null ? null : rs.getString("external_contract_no"),
				contractId == null ? null : SalesProjectContractStatus.valueOf(rs.getString("contract_status")),
				contractId == null ? null : SalesProjectContractType.valueOf(rs.getString("contract_type")),
				nullableLong(rs, "contract_project_id"));
	}

	private ProjectLink mapProjectLink(ResultSet rs, int rowNum) throws SQLException {
		return new ProjectLink(rs.getLong("project_id"), rs.getString("project_no"), rs.getString("project_name"),
				rs.getLong("customer_id"), rs.getLong("contract_id"), rs.getString("contract_no"),
				rs.getString("external_contract_no"));
	}

	private ProjectSalesOrderResponse mapProjectSalesOrder(ResultSet rs, int rowNum) throws SQLException {
		return new ProjectSalesOrderResponse(rs.getLong("id"), rs.getString("order_no"), rs.getLong("customer_id"),
				rs.getString("customer_name"), rs.getObject("order_date", LocalDate.class),
				rs.getObject("expected_ship_date", LocalDate.class), rs.getString("status"),
				nullableLong(rs, "project_id"), nullableLong(rs, "contract_id"), rs.getString("contract_no"),
				rs.getString("external_contract_no"), rs.getInt("line_count"), rs.getBigDecimal("business_amount"));
	}

	private static boolean sameLink(ProjectLink oldLink, ProjectLink newLink) {
		return Objects.equals(oldLink == null ? null : oldLink.projectId(), newLink == null ? null : newLink.projectId())
				&& Objects.equals(oldLink == null ? null : oldLink.contractId(),
						newLink == null ? null : newLink.contractId());
	}

	private static String describe(ProjectLink link) {
		if (link == null) {
			return "未关联";
		}
		return link.projectNo() + "/" + link.contractNo();
	}

	private static QueryParts where(List<String> conditions, List<Object> args) {
		return new QueryParts(conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions), args);
	}

	private static int limit(int pageSize) {
		return Math.max(1, Math.min(pageSize, 100));
	}

	private static int offset(int page, int pageSize) {
		return (Math.max(page, 1) - 1) * limit(pageSize);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	public record ProjectLink(Long projectId, String projectNo, String projectName, Long customerId, Long contractId,
			String contractNo, String externalContractNo) {
	}

	public record OrderLinkCandidateResponse(Long projectId, String projectNo, String projectName, Long customerId,
			Long contractId, String contractNo, String externalContractNo) {
	}

	public record ProjectSalesOrderResponse(Long id, String orderNo, Long customerId, String customerName,
			LocalDate orderDate, LocalDate expectedShipDate, String status, Long projectId, Long contractId,
			String contractNo, String externalContractNo, int lineCount, BigDecimal businessAmount) {
	}

	public record SalesOrderSummary(Long salesOrderCount, BigDecimal businessAmount) {
	}

	private record ProjectContractState(Long projectId, String projectNo, String projectName, Long customerId,
			SalesProjectStatus projectStatus, Long contractId, String contractNo, String externalContractNo,
			SalesProjectContractStatus contractStatus, SalesProjectContractType contractType, Long contractProjectId) {
	}

	private record QueryParts(String where, List<Object> args) {
	}

}
