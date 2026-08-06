# 物料与 BOM 草稿导入模板兼容修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让物料和 BOM 草稿导入模板直接展示中文表头及可见中文下拉，同时保持旧英文模板和英文值继续可导入。

**Architecture:** 在现有 `PlatformDocumentTaskController` 的 Apache POI 生成链路内做模板专属增量配置，不抽取全系统模板框架；在 `PlatformDocumentTaskService` 的现有解析标准化层增加中文别名和中文值归一化。前端下载 API、权限和页面按钮保持不变，模板结构与解析兼容由后端测试锁定。

**Tech Stack:** Java 21、Spring Boot、Apache POI、MockMvc、JUnit 5、AssertJ、Vue 3/Vitest（仅回归既有下载入口）。

## Global Constraints

- 只修改物料导入模板、BOM 草稿导入模板及对应解析兼容和受影响测试。
- 保持 `/api/admin/import-templates/materials`、`/api/admin/import-templates/bom-drafts`、文件名、HTTP 方法、权限和二进制附件响应不变。
- 物料工作表名保持 `template`；BOM 工作表名保持 `bom`、`items`。
- 保持字段顺序、字段含义、行数限制、文件限制、引用校验、错误码、预检/确认流程和数据库不变。
- 历史导入适配器、导出报表、打印模板、附件和网页生产代码不在范围内。
- 保留工作树中用户已有的三个 `Platform*` 后端文件、`apps/web/vite.config.ts` 和本地日志差异，不得回退或覆盖。
- 所有生产代码必须在对应失败测试确认后实施。

---

### Task 1: 物料模板中文下拉与布尔兼容

**Files:**
- Modify: `apps/api/src/test/java/com/qherp/api/system/stage022/Stage022BackendControllerTests.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskController.java:56-114`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskController.java:320-380`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java:62-130`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java:2705-2815`

**Interfaces:**
- Consumes: `GET /api/admin/import-templates/materials` 和现有 `POST /api/admin/imports/materials` 上传链路。
- Produces: 中文 15 列物料模板、8 个可见中文下拉列，以及中英文表头/枚举/布尔兼容解析。

- [ ] **Step 1: 写入物料模板失败测试**

在 `Stage022BackendControllerTests` 增加下载模板测试，通过 MockMvc 获取 XLSX 并使用 `XSSFWorkbook` 断言：

```java
assertThat(workbook.getSheetName(0)).isEqualTo("template");
assertThat(headerValues(sheet)).containsExactly(
        "物料编码", "物料名称", "规格型号", "物料类型", "来源类型", "跟踪方式",
        "物料分类编码", "计量单位编码", "状态", "成本分类", "库存计价类别",
        "是否启用库存计价", "是否启用项目成本", "成本备注", "备注");
assertListValidation(sheet, "D2:D10001", "原材料", "半成品", "成品", "辅料");
assertListValidation(sheet, "L2:L10001", "是", "否");
assertListValidation(sheet, "M2:M10001", "是", "否");
assertDropdownArrowVisible(sheet, "D2:D10001");
assertThat(sheet.getPaneInformation()).isNotNull();
assertThat(sheet.getAutoFilter()).isNotNull();
```

同时断言六类既有枚举列与两类布尔列共 8 个验证区域，且底层 OOXML 未设置隐藏箭头语义。

- [ ] **Step 2: 写入物料解析兼容失败测试**

增加两条上传预检用例：

```java
// 新模板：中文表头、中文枚举、“是/否”
List<String> chineseRow = List.of("", "测试物料", "T-01", "原材料", "外购", "批次",
        categoryCode, unitCode, "启用", "直接材料", "计价物料", "是", "否", "", "");
assertThat(uploadMaterialWorkbook(chineseHeaders(), chineseRow).status()).isEqualTo("READY_TO_COMMIT");

// 旧模板：英文表头、英文枚举、true/false
List<String> legacyRow = List.of("", "Legacy Material", "L-01", "RAW_MATERIAL", "PURCHASED", "BATCH",
        categoryCode, unitCode, "ENABLED", "DIRECT_MATERIAL", "VALUED_MATERIAL", "true", "false", "", "");
assertThat(uploadMaterialWorkbook(legacyHeaders(), legacyRow).status()).isEqualTo("READY_TO_COMMIT");
```

测试必须证明新增中文值被归一化，且旧英文输入没有回归；不得放宽空值、必填和引用校验。

- [ ] **Step 3: 运行定向测试确认 RED**

Run:

```powershell
.\mvnw.cmd -f apps/api/pom.xml -Dtest=Stage022BackendControllerTests test
```

Expected: 新增断言因下拉箭头隐藏、布尔列无“是/否”验证或中文布尔解析错误而失败；既有测试保持原结果。

- [ ] **Step 4: 最小实现物料模板生成修复**

在 `MATERIAL_IMPORT_TEMPLATE_ENUM_OPTIONS` 增加两个布尔列，并保持现有六类中文枚举不变：

```java
Map.entry("是否启用库存计价", new String[] { "是", "否" }),
Map.entry("是否启用项目成本", new String[] { "是", "否" })
```

将布尔批注改为“中文可填 是/否；兼容英文 true/false”。模板验证继续使用错误阻止策略，但生成后的 DataValidation 必须显示下拉箭头；不要改接口、文件名、工作表名或列顺序。

- [ ] **Step 5: 最小实现物料布尔归一化**

在现有布尔读取入口加入明确映射，保持旧值兼容：

```java
return switch (normalizedValue) {
    case "是", "TRUE" -> true;
    case "否", "FALSE" -> false;
    default -> existingBooleanFallback(value);
};
```

沿用现有空白处理和错误行为；不得把未知中文同义词静默转成布尔值。

- [ ] **Step 6: 运行定向测试确认 GREEN**

Run:

```powershell
.\mvnw.cmd -f apps/api/pom.xml -Dtest=Stage022BackendControllerTests test
```

Expected: 新增物料模板与解析兼容用例通过，既有 Stage022 用例全部通过。

---

### Task 2: BOM 双工作表中文模板与模式兼容

**Files:**
- Modify: `apps/api/src/test/java/com/qherp/api/system/stage022/Stage022BackendControllerTests.java`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskController.java:117-123`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskController.java:320-420`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java:2685-2703`
- Modify: `apps/api/src/main/java/com/qherp/api/system/platform/PlatformDocumentTaskService.java:2810-2880`

**Interfaces:**
- Consumes: `GET /api/admin/import-templates/bom-drafts` 和现有 `POST /api/admin/imports/bom-drafts` 上传链路。
- Produces: `bom`、`items` 两张中文表头工作表，`bom` 操作模式中文下拉，以及新旧 BOM 模板兼容解析。

- [ ] **Step 1: 写入 BOM 模板失败测试**

增加下载测试，断言：

```java
assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
assertThat(workbook.getSheetName(0)).isEqualTo("bom");
assertThat(workbook.getSheetName(1)).isEqualTo("items");
assertThat(headerValues(workbook.getSheet("bom"))).containsExactly(
        "操作模式", "BOM ID", "版本", "BOM 编码", "父项物料编码", "版本编码",
        "名称", "基准数量", "基准单位编码", "生效日期", "失效日期", "备注");
assertThat(headerValues(workbook.getSheet("items"))).containsExactly(
        "行号", "子项物料编码", "业务单位编码", "业务用量", "损耗率", "仓库编码", "备注");
assertListValidation(workbook.getSheet("bom"), "A2:A10001", "创建草稿", "更新草稿");
assertDropdownArrowVisible(workbook.getSheet("bom"), "A2:A10001");
assertThat(workbook.getSheet("bom").getRow(0).getCell(0).getCellComment().getString().getString())
        .contains("mode", "CREATE", "UPDATE_DRAFT");
```

同时断言两个工作表均冻结首行、启用首行筛选、列宽不会截断中文表头。

- [ ] **Step 2: 写入 BOM 解析兼容失败测试**

增加中文和旧英文两条预检用例：

```java
assertThat(uploadBomWorkbook(chineseBomHeaders(), "创建草稿", chineseItemHeaders()).status())
        .isEqualTo("READY_TO_COMMIT");
assertThat(uploadBomWorkbook(legacyBomHeaders(), "CREATE", legacyItemHeaders()).status())
        .isEqualTo("READY_TO_COMMIT");
```

另覆盖“更新草稿”映射到 `UPDATE_DRAFT`，并确认提交阶段二次读取源文件时使用同一套表头别名和模式归一化。

- [ ] **Step 3: 运行定向测试确认 RED**

Run:

```powershell
.\mvnw.cmd -f apps/api/pom.xml -Dtest=Stage022BackendControllerTests test
```

Expected: 新增断言因当前 BOM 模板结构/英文表头/缺少中文模式下拉或解析别名而失败。

- [ ] **Step 4: 最小实现 BOM 专属生成方法**

在控制器内增加仅服务该端点的 `bomDraftXlsx(...)`，生成 `bom`、`items` 两张工作表；工作表名和字段顺序固定，不复用会生成单一 `template` 工作表的普通辅助方法。为表头添加英文字段批注，并在 `bom!A2:A10001` 添加：

```java
new String[] { "创建草稿", "更新草稿" }
```

两个工作表应用与物料模板一致的表头样式、冻结、筛选、合理列宽和可见下拉规则；不增加动态物料、单位或仓库列表。

- [ ] **Step 5: 最小实现 BOM 表头别名和模式归一化**

为两个工作表分别定义一一对应的中英文表头别名，读取时先归一化为既有内部字段名。模式映射严格限定为：

```java
return switch (value.trim().toUpperCase(Locale.ROOT)) {
    case "CREATE", "创建草稿" -> "CREATE";
    case "UPDATE_DRAFT", "更新草稿" -> "UPDATE_DRAFT";
    default -> value.trim();
};
```

预检与确认提交阶段必须调用同一归一化逻辑；不改变支持的模式集合和既有错误码。

- [ ] **Step 6: 运行定向测试确认 GREEN**

Run:

```powershell
.\mvnw.cmd -f apps/api/pom.xml -Dtest=Stage022BackendControllerTests test
```

Expected: 物料与 BOM 新增用例、旧英文兼容用例和既有 Stage022 用例全部通过。

---

### Task 3: 边界回归与本地页面验收

**Files:**
- Verify only: `apps/web/src/shared/api/documentPlatformApi.ts`
- Verify only: `apps/web/src/modules/materials/items/MaterialItemListView.vue`
- Verify only: `apps/web/src/modules/materials/boms/BomListView.vue`
- Verify only: generated `materials-import-template.xlsx`
- Verify only: generated `bom-draft-import-template.xlsx`

**Interfaces:**
- Consumes: 两个既有下载按钮与后端附件响应。
- Produces: 下载入口、权限和二进制契约未变化的验证证据。

- [ ] **Step 1: 运行最小前端回归**

仅当现有入口测试覆盖两个按钮时运行对应测试；若无生产前端差异，不新增读取 XLSX 内容的前端测试。

```powershell
npm test -- src/modules/materials/items/MaterialItemListView.spec.ts src/modules/materials/boms/BomListView.spec.ts
```

Expected: 下载按钮仍调用固定模板 API 和 `downloadFile`，权限条件不变。

- [ ] **Step 2: 重启本地 `5174` 环境**

沿用本任务启动环境的既有方式重启 API 与 Web，不更改端口、Vite 配置或数据库。

- [ ] **Step 3: 从页面重新下载并检查物料模板**

在“物料档案”点击“下载模板”，确认：中文 15 列表头、6 类枚举中文下拉、2 类“是/否”下拉、下拉箭头可见、冻结和筛选存在。

- [ ] **Step 4: 从页面重新下载并检查 BOM 模板**

在 BOM 页面点击草稿模板下载，确认：`bom`、`items` 工作表名不变、中文表头直显、`bom` 操作模式显示“创建草稿/更新草稿”下拉、箭头可见。

- [ ] **Step 5: 核对修改边界**

确认未修改历史导入适配器、权限、数据库迁移、页面生产代码、打印/导出/附件功能；现有用户差异保持完整。
