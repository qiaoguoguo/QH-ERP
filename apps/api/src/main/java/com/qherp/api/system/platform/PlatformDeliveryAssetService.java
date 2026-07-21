package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PlatformDeliveryAssetService {

	private final JdbcTemplate jdbcTemplate;

	private final String environmentCode;

	private final String manualVersion;

	private final String manualUpdatedAt;

	private final String demoDataVersion;

	private final String demoDataStatus;

	private final String demoDataVerifiedAt;

	public PlatformDeliveryAssetService(JdbcTemplate jdbcTemplate,
			@Value("${qherp.delivery.environment-code:}") String environmentCode,
			@Value("${qherp.delivery.manual-version:}") String manualVersion,
			@Value("${qherp.delivery.manual-updated-at:}") String manualUpdatedAt,
			@Value("${qherp.delivery.demo-data-version:}") String demoDataVersion,
			@Value("${qherp.delivery.demo-data-status:}") String demoDataStatus,
			@Value("${qherp.delivery.demo-data-verified-at:}") String demoDataVerifiedAt) {
		this.jdbcTemplate = jdbcTemplate;
		this.environmentCode = environmentCode;
		this.manualVersion = manualVersion;
		this.manualUpdatedAt = manualUpdatedAt;
		this.demoDataVersion = demoDataVersion;
		this.demoDataStatus = demoDataStatus;
		this.demoDataVerifiedAt = demoDataVerifiedAt;
	}

	@Transactional(readOnly = true)
	public DeliveryAssetCatalog catalog(CurrentUser currentUser) {
		requirePermission(currentUser, "platform:delivery-asset:view");
		DeliveryReleaseMetadata metadata = releaseMetadata();
		return new DeliveryAssetCatalog("034", OffsetDateTime.now(), metadata.environmentCode(), metadata.manual(),
				metadata.demoData(),
				historyImportAdapters(), dataRepairAdapters(),
				batchTools(), printTemplates(), List.of(new StaticAsset("OPERATION_MANUAL",
						"docs/manual/system-operation-manual.md", "操作手册由文档角色维护，后端提供只读目录引用"),
						new StaticAsset("DEMO_DATA_TOOLS", "tools/demo-data", "演示数据工具由测试角色维护，后端不执行")));
	}

	DeliveryReleaseMetadata releaseMetadata() {
		return new DeliveryReleaseMetadata(configured(this.environmentCode, "UNCONFIGURED"),
				new ManualAsset(configured(this.manualVersion, "UNCONFIGURED"),
						parseConfiguredTime(this.manualUpdatedAt)),
				new DemoDataAsset(configured(this.demoDataVersion, "UNCONFIGURED"),
						configured(this.demoDataStatus, "NOT_VERIFIED"),
						parseConfiguredTime(this.demoDataVerifiedAt)));
	}

	private List<AdapterAsset> historyImportAdapters() {
		return this.jdbcTemplate.query("""
				select adapter_code, name, target_object_type, status, version
				from platform_import_adapter_definition
				order by id
				""", (rs, rowNum) -> new AdapterAsset(rs.getString("adapter_code"), rs.getString("name"),
				rs.getString("target_object_type"), rs.getString("status"), rs.getLong("version")));
	}

	private List<AdapterAsset> dataRepairAdapters() {
		return this.jdbcTemplate.query("""
				select adapter_code, name, target_object_type, status, version
				from platform_data_repair_adapter_definition
				order by id
				""", (rs, rowNum) -> new AdapterAsset(rs.getString("adapter_code"), rs.getString("name"),
				rs.getString("target_object_type"), rs.getString("status"), rs.getLong("version")));
	}

	private List<BatchToolAsset> batchTools() {
		return this.jdbcTemplate.query("""
				select tool_code, name, target_object_type, action_code, status, version
				from platform_batch_tool_definition
				order by id
				""", (rs, rowNum) -> new BatchToolAsset(rs.getString("tool_code"), rs.getString("name"),
				rs.getString("target_object_type"), rs.getString("action_code"), rs.getString("status"),
				rs.getLong("version")));
	}

	private List<PrintTemplateAsset> printTemplates() {
		return this.jdbcTemplate.query("""
				select template_code, scene_code, name, object_type, template_version, status
				from platform_print_template
				order by id
				""", (rs, rowNum) -> new PrintTemplateAsset(rs.getString("template_code"),
				rs.getString("scene_code"), rs.getString("name"), rs.getString("object_type"),
				rs.getInt("template_version"), rs.getString("status")));
	}

	private void requirePermission(CurrentUser operator, String permissionCode) {
		if (!operator.permissions().contains(permissionCode)) {
			throw new BusinessException(ApiErrorCode.AUTH_FORBIDDEN);
		}
	}

	private static String configured(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static OffsetDateTime parseConfiguredTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value.trim());
		}
		catch (RuntimeException exception) {
			throw new BusinessException(ApiErrorCode.VALIDATION_ERROR);
		}
	}

	public record DeliveryAssetCatalog(String stageCode, OffsetDateTime generatedAt,
			String environmentCode, ManualAsset manual, DemoDataAsset demoData,
			List<AdapterAsset> historyImportAdapters, List<AdapterAsset> dataRepairAdapters,
			List<BatchToolAsset> batchTools, List<PrintTemplateAsset> printTemplates,
			List<StaticAsset> staticAssets) {
	}

	public record ManualAsset(String version, OffsetDateTime updatedAt) {
	}

	public record DemoDataAsset(String version, String status, OffsetDateTime verifiedAt) {
	}

	public record DeliveryReleaseMetadata(String environmentCode, ManualAsset manual, DemoDataAsset demoData) {
	}

	public record AdapterAsset(String code, String name, String targetObjectType, String status, Long version) {
	}

	public record BatchToolAsset(String code, String name, String targetObjectType, String actionCode, String status,
			Long version) {
	}

	public record PrintTemplateAsset(String templateCode, String sceneCode, String name, String objectType,
			int templateVersion, String status) {
	}

	public record StaticAsset(String code, String path, String note) {
	}

}
