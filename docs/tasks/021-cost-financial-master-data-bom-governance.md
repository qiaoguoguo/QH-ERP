# 任务记录：成本财务主数据与 BOM 治理

## 实现计划要求

- 021 阶段采用固定五角色执行：产品经理、UI 设计师、前端开发、后端开发、测试。主代理只负责统筹、派发、范围控制、审查汇总和交付判断，不承担产品设计、UI 设计、前端实现、后端实现或测试执行。
- 固定角色会话必须复用本阶段已登记的后台线程；不得因为任务完成、审查、返工、并发槽位或上下文切换新增第六类角色，不得创建临时审查角色。
- 如确需替代失效角色，必须按仓库规则记录原角色 ID、故障现象、恢复尝试和替代原因，并只允许使用 `gpt-5.5` 与 `xhigh`。
- 本阶段只维护本文件作为 021 唯一权威阶段说明；不另建设计规格、接口契约、测试计划或实施计划文档。
- 各工作包采用测试先行的执行习惯，测试与实现属于同一完整业务工作包，不把红测、编码、转绿、审查或返工拆成独立流程任务。
- 开发期间只运行受影响后端测试、前端定向测试、必要类型检查和必要迁移验证；全量后端测试、全量前端测试、类型检查、生产构建、数据库迁移验证、浏览器验收和桌面端视觉检验统一进入唯一交付前全量验证窗口。
- 所有沟通、文档、界面文案、提交信息、审查意见和验收记录使用中文；技术标识、路径、接口、命令和枚举可保留原文。
- 设计和开发仍需考虑响应式、窄屏和触屏结构，避免后续适配受阻；本阶段测试、集中审查和用户验收只覆盖桌面端。真实页面视觉检验不保存截图，不建立视觉截图目录。

## 阶段状态

- 阶段编号：`021`
- 阶段名称：`成本财务主数据与 BOM 治理`
- 当前状态：021 原阶段已完成并进入 `main`；2026-07-13 发现 BOM 时效状态表达及生产工单按计划开工日期消费 BOM 的闭环缺陷，现已按阻断缺陷重新进入修复、审查和验证流程。
- 当前分支：`codex/021-bom-effective-date-fix`
- 权威输入：根目录 `AGENTS.md`、当前交接 `docs/handoffs/2026-07-12-project-handoff-current.md`、020 任务记录、020 实施计划、2026-07-13 固定五角色 021 讨论结论和用户最新指令。
- 权威产物：本文件。后续固定角色实现、集中审查、定向复审和交付前全量验证均以本文件为唯一阶段输入。
- 原阶段最终验证：后端 315/315、前端 82 个文件 648/648、类型检查、生产构建、空库 V1-V18、V17 存量升级、桌面浏览器业务验收和相关页面视觉检验全部通过；没有保存截图，没有执行移动端验收。
- 本次修复基线：受影响前端测试 27/27、受影响后端测试 22/22 通过；后端命令固定使用 JDK 21。本次修复尚未进入最终验证。

## 变更控制记录

### 2026-07-13：集中整改差异复审后的接口口径收口

- 变更原因：集中整改要求工程变更创建表单提供可见的 `BOM_ECO` 编码生成交互，但原接口章节又规定创建接口在保存时二次生成 `ecoNo`，造成用户确认编号与最终落库编号可能不一致；差异复审同时确认 BOM 详情尚缺少可消费的真实新旧版本关系字段。
- 最终决策：工程变更编号仍只能由后端编码规则占号。前端“生成编码”按钮调用后端编码生成接口取得 `ecoNo`，创建请求必须提交该编号；创建服务只做必填、对象权限和唯一性校验并写入，不得再次生成另一个编号。编辑态编号只读。
- 版本关系契约：`BomDetailRecord` 增加 `historyRelations`，返回当前 BOM 作为来源或目标关联到的工程变更关系，页面据此展示真实来源版本、目标版本、工程变更编号、状态和生效信息，不得以 BOM 自身发布状态替代历史关系。
- 影响范围：`BomEngineeringChangePayload`、工程变更创建服务及测试、`BomDetailRecord`、BOM 详情页面及测试；不扩展审批、自动替代、库存计价或 022 以后范围。
- 验收影响：创建前展示的工程变更编号必须与保存后记录一致且一次创建不重复占号；BOM 详情必须能追溯关联工程变更的新旧版本关系；相关权限、唯一冲突、版本并发和稳定错误语义继续由后端保证。
- 决策依据：021 已确认的后端唯一编码原则、集中整改 `F-02`/`F-06`、产品与跨端差异复审证据，以及长期数据可追溯性与用户可预期性。

### 2026-07-13：BOM 时效状态与生产工单消费闭环缺陷修复

- 变更原因：验收库同一父项存在有效期不重叠的 V1 当前版本和 V2 未来版本，数据模型符合“同一业务日期只命中一个已发布 BOM”的规则；但 BOM 页面只显示“已发布”，生产工单又加载全部 `ENABLED` BOM，前后端均未按计划开工日期校验有效期，可能把未来或历史版本写入工单用料快照。
- 缺陷等级：`BLOCKER`。错误 BOM 一旦随工单发布生成 `mfg_work_order_material` 快照，将影响核心生产数量与后续成本数据可信度；修复、复审和验证通过前不得将本分支合入 `main`。
- 方案比较：仅改页面状态会留下后端绕过风险；仅加后端校验会继续给用户错误候选并在保存时才失败；最终采用“页面清晰表达 + 工单按业务日期查询 + 后端三处强校验”的双重防线。
- 业务日期：生产工单选用 BOM 的业务日期固定为 `plannedStartDate`，不得使用当前日期、创建日期、发布日期或计划完工日期。
- 日期规则：BOM 有效期继续使用闭区间；`effectiveFrom = null` 表示无限早，`effectiveTo = null` 表示无限未来。计划开工日期等于开始日或结束日均有效。
- 状态口径：`DRAFT`、`ENABLED`、`DISABLED` 继续只表达发布生命周期；`ENABLED` 统一解释为“已发布”，不等同于“当前有效”。页面另行派生“当前有效、未来生效、历史失效、草稿未发布、已停用”，派生状态不落库。
- 查询契约：生产工单沿用 `GET /api/admin/boms`，固定提交 `status=ENABLED`、`parentMaterialId`、`effectiveDate=plannedStartDate`、`page=1` 和受控 `pageSize`。服务层已有同父项同日期唯一命中约束，因此该查询最多返回一个有效版本，不新增候选接口或权限码。
- 工单契约：新建和编辑时，未同时选择产品物料与计划开工日期不得选择 BOM；任一字段变化后重新查询并清理不再有效的 BOM。后端创建、更新、发布必须分别校验 BOM 已发布、父项匹配且计划开工日期落在有效期内；发布校验必须在锁定工单后、删除或生成用料快照前完成。
- 错误语义：新增 `PRODUCTION_BOM_EFFECTIVE_DATE_INVALID`，HTTP `409`，文案为“所选 BOM 在计划开工日期不生效，请选择有效 BOM 或调整计划开工日期”。BOM 不存在、未发布或父项不匹配继续使用 `PRODUCTION_BOM_INVALID`。
- 历史保护：既有草稿不批量改写，下次保存或发布时执行新规则；已发布、进行中、已完成、已取消工单及其用料快照不重算、不回写。无数据库结构变更，不新增 Flyway 迁移。
- 页面范围：仅修改 `/materials/boms` 的发布状态/时效状态表达和已有有效日期筛选入口，以及生产工单创建/编辑表单的 BOM 查询、空态和错误提示；不扩展生产执行、自动排产、缺料净算、库存计价或后续阶段能力。
- 验收影响：必须覆盖 V1 当前/V2 未来、历史失效、空起止日期、有效期首尾日、错误父项、草稿日期变化、发布前二次校验、无有效 BOM 空态、页面双状态表达及已发布工单快照保护。

## 本次缺陷修复实施计划

本计划是 021 唯一权威说明的一部分，不另建规格或实施计划文件。两个实现工作包可在接口和错误语义冻结后并行推进；每个工作包必须先写能稳定复现缺陷的失败测试，确认失败原因正确后再写实现。

### 工作包一：后端工单 BOM 有效期完整性

**所有权：** 后端开发固定角色；测试角色负责独立核对测试覆盖，前端开发固定角色负责交叉审查接口可消费性。

**文件：**

- 修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`。
- 修改 `apps/api/src/main/java/com/qherp/api/system/production/ProductionAdminService.java`。
- 修改 `apps/api/src/test/java/com/qherp/api/system/production/ProductionAdminControllerTests.java`。

**接口与实现约束：**

- `BomRef` 增加 `LocalDate effectiveFrom`、`LocalDate effectiveTo`，`bomRef` 查询同步读取 `effective_from`、`effective_to`。
- `validateBom` 调整为接收业务日期，或新增职责清晰的等价方法；闭区间判断固定为 `(effectiveFrom == null || !businessDate.isBefore(effectiveFrom)) && (effectiveTo == null || !businessDate.isAfter(effectiveTo))`。
- `validateWorkOrderRequest` 必须先完成计划日期必填及先后关系校验，再按 `request.plannedStartDate()` 校验 BOM；创建和草稿更新共同走该逻辑。
- `releaseWorkOrder` 必须按锁定工单的 `plannedStartDate` 再次校验 BOM，且校验位于 `delete from mfg_work_order_material` 之前。
- 只有“BOM 存在、已发布、父项匹配，但业务日期不命中”返回 `PRODUCTION_BOM_EFFECTIVE_DATE_INVALID`；其他既有错误语义不得改变。

**测试先行步骤：**

1. 在 `ProductionAdminControllerTests` 先增加创建工单选择未来 BOM、历史 BOM 均返回新错误码的测试，并运行该测试确认旧实现错误地成功或返回错误语义。
2. 增加 `plannedStartDate == effectiveFrom`、`plannedStartDate == effectiveTo`、空开始、空结束均允许的边界测试，确认旧实现不能证明日期规则。
3. 增加草稿更新改变计划日期后拒绝原 BOM 的测试，以及草稿保存后有效期被截断、发布前拒绝且不生成用料快照的测试。
4. 实现最小后端改动使上述红测转绿，同时保持错误父项、停用 BOM、空明细和既有发布快照测试通过。
5. 定向运行 `BomAdminControllerTests,ProductionAdminControllerTests`；使用 `C:\Users\14567\.codex\jdks\jdk-21.0.11+10`，预期退出码 `0`。

### 工作包二：BOM 时效表达与工单候选联动

**所有权：** 前端开发固定角色；UI 设计师固定角色提供状态和空态约束，后端开发固定角色交叉审查请求参数与错误语义。

**文件：**

- 修改 `apps/web/src/modules/materials/boms/bomPageHelpers.ts`。
- 新增 `apps/web/src/modules/materials/boms/bomPageHelpers.spec.ts`。
- 修改 `apps/web/src/modules/materials/boms/BomListView.vue`。
- 修改 `apps/web/src/modules/materials/boms/BomListView.spec.ts`。
- 修改 `apps/web/src/modules/production/ProductionWorkOrderFormView.vue`。
- 修改 `apps/web/src/modules/production/ProductionWorkOrderFormView.spec.ts`。
- 如稳定错误文案映射需要，最小修改 `apps/web/src/modules/production/productionPageHelpers.ts` 及其对应测试。

**接口与实现约束：**

- 在 `bomPageHelpers.ts` 提供可测试的时效状态纯函数，输入发布状态、有效期和参考日期，输出 `CURRENT | FUTURE | EXPIRED | UNPUBLISHED | DISABLED`；页面文案分别为“当前有效、未来生效、历史失效、草稿未发布、已停用”。
- BOM 列表保留现有“状态”列，并新增“时效状态”列；详情同时显示发布状态、时效状态和有效期。有效日期和包含历史使用已有查询字段，不改变路由。
- `loadReferences` 只加载物料和仓库；BOM 由独立方法在产品物料和计划开工日期均存在时调用 `bomApi.list`，查询参数固定为 `status: 'ENABLED'`、`parentMaterialId`、`effectiveDate`、`page: 1`、`pageSize: 20`。
- 新建工单中产品物料或计划开工日期变化时清空原 BOM 与明细预览并重新查询。编辑草稿加载详情后按保存的产品和日期查询；原 BOM 不再有效时清空选择、保留明确错误提示并阻止保存。
- 未选择产品物料或计划开工日期时 BOM 控件禁用并提示“请先选择产品物料和计划开工日期”；查询无结果时提示该产品在所选计划开工日期无有效 BOM。
- 候选项至少展示 BOM 编码、版本和有效期；错误必须显示在表单可见错误区域，不能只依赖瞬时消息。

**测试先行步骤：**

1. 新增 `bomPageHelpers.spec.ts`，固定参考日期验证当前、未来、历史、空起止、草稿和停用派生状态；先运行并确认函数缺失导致失败。
2. 在 `BomListView.spec.ts` 增加发布状态与时效状态并列展示、有效日期筛选参数的测试；确认旧页面缺少时效状态而失败。
3. 在 `ProductionWorkOrderFormView.spec.ts` 增加未选产品/日期时禁用、按父项和计划开工日期查询、字段变化清空旧 BOM、无候选空态、编辑态无效 BOM 阻止保存及后端 409 文案测试；确认旧实现继续加载全部 `ENABLED` BOM 而失败。
4. 实现最小前端改动使红测转绿，不改变已发布工单只读快照展示。
5. 定向运行 `BomListView.spec.ts`、`bomPageHelpers.spec.ts`、`ProductionWorkOrderFormView.spec.ts` 及受影响 helper 测试；随后运行 `npm run typecheck`，预期退出码均为 `0`。

### 集中审查与验证

- 产品经理核对业务日期、状态语义、既有草稿和历史工单处理；UI 设计师检查本阶段两个相关页面的桌面端状态、空态、错误和信息密度；前端与后端交叉审查非本人实现；测试角色核对边界、异常和回归覆盖。
- 阻断和严重意见一次性合并整改；整改后只复审修复差异和受影响路径。
- 交付前验证运行受影响后端测试、受影响前端测试、前端全量测试、类型检查和生产构建；本次不涉及迁移，若实现未新增迁移则不重复空库和存量库升级验证。
- 真实桌面浏览器只验收 `/materials/boms` 的双状态表达与筛选，以及生产工单创建/编辑/发布的日期联动、无候选和错误路径；不保存截图，不执行移动端或窄屏验收。

## 阶段目标

在 020 销售项目维度之上，补齐后续库存计价、项目专采、发票往来、项目成本、月结和总账共同依赖的主数据契约，并把现有 BOM 从“可维护版本字段和启停用”深化为“版本、有效期、工程变更和替代料可治理、可追溯、可历史保护”的基础能力。

021 的完成结果必须满足：

- 物料基本单位继续作为库存与成本数量基准，业务单位只能通过后端受控换算落到基本单位。
- 物料成本属性、库存计价分类、客户和供应商结算税务资料形成稳定主数据，不在 023 至 029 重复补字段。
- 编码规则能支撑物料、客户、供应商、BOM 和后续关键单据的后端唯一生成，不改写历史编码。
- 已发布 BOM 不直接改明细，变更通过新版本和工程变更记录承接。
- 已发布生产工单的 BOM 用料快照不受后续 BOM 治理、替代料或工程变更污染。
- 替代料只维护受控关系和人工识别基础，不自动替代领料、采购或缺料建议。
- 000 至 020 历史主数据、业务单据、库存、生产、往来、报表和追溯路径继续可查询、可运行，不伪造未来业务字段。

## 背景与现状核对

### 已具备能力

- 系统已有登录、用户、角色、权限点、后端接口鉴权和系统审计。
- 当前已有基础资料：计量单位、仓库、供应商、客户、物料分类和物料档案。
- 当前计量单位具备编码、名称、精度、状态、排序和备注；物料通过 `unit_id` 引用一个基本单位。
- 当前客户和供应商具备编码、名称、联系人、联系电话、状态和备注，不具备开票、税务、结算、付款条件和银行资料。
- 当前物料具备编码、名称、规格、物料类型、来源类型、追踪方式、分类、基本单位、状态和备注，不具备成本属性和库存计价分类。
- 当前 BOM 具备 BOM 编码、父项物料、版本编码、名称、基准数量、基准单位、状态、有效日期、复制、启用、停用和用料明细。
- 当前 BOM 状态为 `DRAFT`、`ENABLED`、`DISABLED`；同一父项物料当前只允许一个启用 BOM。
- 当前 BOM 写入、更新、复制、启用和停用均写系统审计。
- 当前生产工单引用明确 `bom_id`；发布工单时把 BOM 明细写入 `mfg_work_order_material`，形成工单用料快照。
- 当前权限初始化已有主数据、物料、BOM、生产、销售项目、合同等权限种子；后端管理接口必须在权限映射中显式配置，否则管理接口会被拒绝。
- 当前审计基于 `sys_audit_log`，记录操作人、动作、目标类型、目标 ID、摘要、请求信息、结果和时间。

### 当前缺口

- 没有物料级业务单位换算关系，业务单据无法可靠表达采购单位、销售单位、库存成本基准单位之间的换算。
- 没有后端统一的数量换算服务和换算历史口径，已过账数量不能被安全重算。
- 没有物料成本属性、库存计价分类或成本归集分类，023 库存计价和 029 项目成本缺少稳定输入。
- 客户和供应商缺少税号、开票抬头、结算方式、付款条件、默认税率、银行账号等资料。
- 编码仍主要依赖人工填写和唯一约束，缺少受控的编码规则、后端生成和并发唯一占号。
- BOM 当前只能表达基础版本和一个启用版本，缺少工程变更原因、影响范围、新旧版本关系和版本历史治理。
- BOM 替代料关系不存在，后续缺料、采购、生产和成本只能看到单一物料关系。

## 用户已确认决策

- 021 正式阶段名称为 `成本财务主数据与 BOM 治理`。
- 021 覆盖物料单位换算、物料成本属性、客户和供应商结算税务资料、编码规则、BOM 版本治理、工程变更、替代料、权限审计与 000 至 020 历史兼容。
- 基本单位继续作为库存与成本数量基准；业务单位由后端受控换算；历史已过账数量不重算。
- 已发布 BOM 不直接改明细；通过新版本或工程变更承接；已发布工单 BOM 用料快照不得被后续治理污染。
- 替代料只维护受控关系，不自动替代领料、采购或缺料建议。
- 021 排除 022 至 036 的审批平台、库存计价、项目专采、销售深化、缺料净算、生产执行深化、发票凭证、总账月结和正式项目成本。
- 阶段采用整包模式：一轮集中审查、一轮集中整改、一次差异复审、唯一交付前全量验证窗口。

## 范围

### 本次包含

- 物料单位换算：按物料维护业务单位到基本单位的换算关系、精度、舍入、启停用、唯一性和历史口径。
- 物料基本单位保护：已有业务事实的物料基本单位不得通过普通编辑直接改变。
- 编码规则：维护物料、客户、供应商、BOM 和后续关键单据的编码规则、后端生成、并发唯一占号和历史兼容。
- 物料成本属性：维护物料成本分类、库存计价分类、是否参与库存价值和是否允许进入项目成本。
- 客户结算税务资料：维护客户开票抬头、税号、默认税率、结算方式、收款条件、银行信息和敏感字段权限。
- 供应商结算税务资料：维护供应商开票资料、税号、默认税率、付款条件、银行信息和敏感字段权限。
- BOM 版本治理：发布规则、有效期规则、版本唯一性、历史有效性、已发布版本保护和工单快照保护。
- 工程变更：记录变更原因、影响范围、来源 BOM、目标 BOM、新旧版本关系、变更摘要、状态和审计。
- 替代料：维护主物料、替代物料、适用范围、优先级、换算比例、有效期、状态和备注。
- 页面入口、权限、审计、异常语义、迁移规则、历史兼容和桌面端浏览器验收。

### 本次不包含

- 固定审批、待办、消息、附件、打印、通用单据平台、导入导出平台，属于 022。
- 项目专属库存、公共库存金额、实际批次成本、移动加权平均、成本层、项目库存归属、调拨、盘点和盘差，属于 023。
- 请购、询价比价、价格协议、项目专采、公共采购策略、到货计划和采购结案，属于 024。
- 销售报价、信用控制、合同变更深化、交付计划、项目履约和销售结案，属于 025。
- 缺料净算、供给建议、计划订单、完整 MRP、APS 和产能优化，属于 026 或更后阶段。
- 工单项目归集、外协、生产执行深化、工序、工作中心、派工、工序转移和精细在制数量，属于 027 或后置范围。
- 销售发票、采购发票、费用单、三单匹配、收入确认、预收预付、凭证草稿、总账和财务结账，属于 028 至 032。
- 项目实际成本、在制成本、完工成本、成本调整、成本差异和项目毛利，属于 029。
- 复杂多属性配置、产品配置器、多组织、多账套、多租户、多币种、可配置 BI、复杂 QMS 和 MES 设备采集。

## 工作包划分

021 只拆为 3 个完整业务能力工作包。每个工作包同时包含后端、前端和测试完成条件，不再拆分规格、红测、实现、转绿、审查等流程任务。

### 工作包一：单位换算与编码规则

目标：建立物料业务单位到基本单位的受控换算，并形成后端唯一编码规则基础。

后端完成条件：

- 新增物料单位换算持久化能力，至少覆盖物料、业务单位、基本单位、换算比例、数量精度、舍入方式、状态、有效日期、备注、创建更新人和版本。
- 换算关系必须以物料基本单位为目标单位；业务单位与基本单位相同时不得创建重复换算。
- 同一物料、同一业务单位、同一有效日期范围内不得存在多个启用换算关系。
- 换算比例必须大于 0；后端统一负责业务单位数量到基本单位数量的换算、精度处理和舍入。
- 已存在库存流水、库存余额、采购、销售、生产、成本或追踪事实的物料，基本单位不得通过普通物料更新直接改变。
- 新增编码规则持久化能力，覆盖对象类型、前缀、日期段、流水长度、重置周期、下一个流水号、状态、备注和版本。
- 编码生成必须由后端完成并保证并发唯一；前端不得自行拼接可信编码。
- 历史编码不改写；已有人工编码继续有效；自动编码不得与历史编码冲突。
- 新增或调整 `/api/admin` 后端资源时，同步权限种子和权限映射，管理接口不得处于未映射状态。
- 单位换算和编码规则所有写入、启用、停用、生成占号动作写系统审计。

前端完成条件：

- 在 `/master/unit-conversions` 提供物料单位换算唯一主维护界面，可按物料查看、创建、编辑、启用、停用业务单位换算。
- 在物料档案页面明确展示基本单位、可用业务单位和基本单位锁定原因。
- 在 `/master/coding-rules` 提供编码规则维护入口，可查看对象类型、规则摘要、下一个流水号、状态和最后更新时间。
- 物料、客户、供应商和 BOM 创建界面支持使用后端编码生成；生成失败时展示稳定错误，不自行补号。
- 桌面端表格、表单、抽屉或弹窗中的单位、精度、舍入、状态、有效期和错误信息清晰可见。

测试完成条件：

- 覆盖单位换算创建、重复范围、比例非法、停用、启用、换算精度、舍入和历史基本单位锁定。
- 覆盖编码规则创建或更新、并发生成、历史编码冲突、规则停用、规则缺失和手工编码唯一性。
- 覆盖权限不足时无法维护单位换算和编码规则，直接调用接口不能绕过前端限制。
- 覆盖 `AccountPermissionInitializerTests` 新增权限幂等和 `PermissionAuthorizationTests` 新增路径映射。
- 覆盖物料、单位候选接口分页搜索、禁用原因和选中项回显。
- 覆盖审计记录包含目标类型、动作、编码或规则摘要、操作人和时间。
- 覆盖桌面端主路径、异常提示、空态、无权限态和加载失败态。

### 工作包二：成本属性与结算税务主数据

目标：为库存计价、项目成本、发票往来和后续财务闭环补齐必要主数据，不计算正式成本、不生成发票或凭证。

后端完成条件：

- 物料档案新增成本属性和库存计价分类，至少能表达：成本分类、库存计价分类、是否参与库存价值、是否允许进入项目成本、默认成本备注和版本。
- 物料成本属性只作为主数据契约，不在 021 计算库存金额、成本层、项目成本、在制或完工成本。
- 既有物料迁移后保留可查询状态；历史物料默认值不得伪造已经确认的成本口径。对未完成成本分类的既有物料使用稳定的未分类枚举，并允许后续按权限维护。
- 新建或启用物料时，成本属性必须满足后续库存和成本引用的最小完整性要求。
- 客户和供应商新增结算税务资料，至少覆盖开票名称、纳税识别号、注册地址电话、开户行、银行账号、默认税率、结算方式、账期天数、付款或收款条件、发票类型和备注。
- 默认币种固定为人民币口径，不引入多币种。
- 银行账号、税号等敏感字段必须支持独立查看和维护权限；无敏感权限时不得在接口响应中泄露完整值。
- 客户和供应商结算税务资料所有写入必须写系统审计。
- 后端异常必须区分字段校验失败、引用不存在、权限不足、敏感字段无权访问、并发冲突和编码重复。

前端完成条件：

- 物料档案列表和详情展示成本属性、库存计价分类和未分类状态；维护入口受权限控制。
- 物料成本属性编辑界面不展示库存金额、成本层、项目成本或财务凭证概念。
- 客户和供应商页面增加结算税务资料区域，字段分组清晰；无敏感查看权限时只展示脱敏值和“受限”标签，完整值不得进入前端状态。
- 默认税率、账期天数、发票类型、结算方式等字段使用受控选项或数字输入，避免自由文本承载关键枚举。
- 桌面端页面保持基础资料工作台的信息密度，关键状态、未分类提示、权限受限态和错误提示可识别。

测试完成条件：

- 覆盖物料成本属性维护、未分类历史数据查询、新物料完整性、启停用和权限控制。
- 覆盖客户和供应商结算税务资料创建、编辑、敏感字段脱敏、无权限响应、字段校验和审计。
- 覆盖结算税务资料“不传不改”、无敏感查看权限不返回完整值、无敏感维护权限不能通过接口修改敏感字段。
- 覆盖历史客户、供应商、物料在迁移后无需补造未来字段即可继续查询。
- 覆盖前端列表、详情、编辑、权限受限、异常提示和空态。

### 工作包三：BOM 版本、工程变更与替代料

目标：在既有 BOM 基础上建立版本治理、工程变更和替代料关系，保护已发布工单快照。

后端完成条件：

- 保留既有 BOM 基础能力和历史数据，新增或强化版本治理规则，不破坏已有 `DRAFT`、`ENABLED`、`DISABLED` 历史状态。
- 已发布 BOM 不允许通过普通更新直接改父项、版本编码、基准数量、基准单位和明细；如需调整，必须复制或创建新版本，并通过工程变更记录承接。
- 同一父项物料下版本编码唯一；同一父项物料在同一业务日期只能命中一个已发布有效 BOM。
- BOM 有效期必须满足开始日期不晚于结束日期；已发布有效区间不得产生业务日期上的冲突。
- 工程变更记录必须包含变更编号、来源 BOM、目标 BOM、变更原因、影响范围、变更摘要、状态、申请或记录人、应用人、应用时间和版本。
- 工程变更不是审批流；状态只表达记录、应用和取消，不建设待办或工作流。
- 工程变更应用后，目标 BOM 才能成为受控发布版本；来源 BOM 按规则进入历史或失效口径，历史查询仍可追溯。
- 替代料关系必须包含主物料、替代物料、适用范围、优先级、换算比例、有效期、状态、备注和版本。
- 主物料不得替代自身；同一适用范围内替代优先级不得冲突；启用替代关系不得与既有启用有效期规则冲突。
- 替代料只供人工识别和后续阶段引用，不自动改 BOM 明细，不自动替代领料、采购或缺料建议。
- 生产工单已经发布后继续使用 `mfg_work_order_material` 快照；BOM 新版本、工程变更和替代料调整不得回写已发布工单用料。
- 所有 BOM 发布、停用、工程变更应用、工程变更取消、替代料写入、启用和停用写系统审计。

前端完成条件：

- BOM 列表和详情展示版本、状态、有效期、发布状态、历史版本关系和工程变更入口。
- 已发布 BOM 的明细在普通编辑入口中只读；页面明确引导通过复制新版本或工程变更承接。
- 工程变更界面能查看来源 BOM、目标 BOM、变更原因、影响范围、变更摘要、状态和审计摘要。
- 替代料界面能按主物料或 BOM 相关范围查看、维护、启用、停用替代关系。
- 工单详情或生产相关页面如展示 BOM 信息，只能展示当时工单快照和引用 BOM 摘要，不展示会误导用户认为工单已被新版本改写的内容。
- 桌面端 BOM 明细、工程变更、替代料、权限受限态、异常态和历史态清晰可扫描。

测试完成条件：

- 覆盖已发布 BOM 普通更新被拒绝、草稿可编辑、新版本复制、有效期冲突、版本编码重复和发布规则。
- 覆盖工程变更创建、应用、取消、来源目标关系、状态流转、审计和并发冲突。
- 覆盖替代料创建、重复优先级、自替代、有效期冲突、停用、权限和审计。
- 覆盖物料、BOM、来源 BOM、目标 BOM 候选接口分页搜索、禁用原因和选中项回显。
- 覆盖生产工单发布后，后续 BOM 新版本、工程变更和替代料调整不改变工单用料快照。
- 覆盖桌面端 BOM 主路径、工程变更路径、替代料路径、异常提示、权限受限和历史查询。

## 产品规则

### 单位换算规则

- 物料基本单位是库存数量、成本数量、BOM 基准数量、工单快照数量和后续计价数量的统一基准。
- 业务单位只能作为采购、销售、BOM 展示或后续业务录入单位；落库为业务事实时，必须由后端按受控换算关系换算出基本单位数量。
- 单位换算按物料维护；不同物料即使使用同一业务单位，也可以拥有不同换算比例。
- 换算关系必须从业务单位指向该物料基本单位，不能形成多级递归换算链。
- 换算比例必须大于 0；数量精度不得小于目标单位允许精度；舍入方式必须为受控枚举。
- 同一物料、同一业务单位在同一日期不得存在多个启用换算关系。
- 有业务事实的物料基本单位不得通过普通编辑变更；业务事实包括库存余额、库存流水、采购单据、销售单据、BOM、生产工单、成本记录、批次、序列号和追踪分配。
- 历史已过账数量不重算；历史单据继续显示当时保存的单位和数量。021 新增换算只影响后续录入和查询换算能力。

### BOM、工单和库存数量闭环规则

- 021 之后新建或更新的 BOM 行允许使用业务单位录入，但后端必须同时保存业务单位和基本单位口径。
- BOM 行请求使用 `businessUnitId` 和 `businessQuantity`；后端根据物料基本单位和有效单位换算关系生成 `baseUnitId`、`baseQuantity`、`conversionId`、`conversionRateSnapshot`、`quantityScaleSnapshot` 和 `roundingModeSnapshot`。
- 既有 `mfg_bom_item.unit_id` 和 `mfg_bom_item.quantity` 在 021 后作为历史兼容字段继续保留；新写入时其值等同 `businessUnitId` 和 `businessQuantity`。
- BOM 行返回必须同时包含 `businessUnitId`、`businessUnitName`、`businessQuantity`、`baseUnitId`、`baseUnitName`、`baseQuantity`、`conversionId`、`conversionRateSnapshot`、`quantityScaleSnapshot`、`roundingModeSnapshot` 和 `quantityBasis`。
- `quantityBasis` 枚举为 `BASE_UNIT`、`CONVERTED_BUSINESS_UNIT`、`LEGACY_BUSINESS_UNIT`。新写入只能产生前两者；`LEGACY_BUSINESS_UNIT` 只用于历史非基本单位且缺少换算快照的行。
- 工单发布时必须把 BOM 行当时的业务单位、业务数量、换算关系标识、换算比例快照、精度快照、舍入方式快照、基本单位和基本单位数量写入 `mfg_work_order_material`。
- 工单发布快照返回必须同时包含 `businessUnitId`、`businessQuantity`、`baseUnitId`、`baseRequiredQuantity` 和换算快照字段，不能只返回业务单位数量。
- 生产领料、退料、补料、库存预留、库存占用、追踪分配、库存流水、库存余额和成本数量一律使用物料基本单位数量；业务单位只用于展示和追溯。
- 021 迁移不重算已过账库存、预留、追踪分配、成本或生产单据数量；只校验其 `unit_id` 是否等于物料基本单位。
- 既有 BOM 行的 `unit_id` 等于子项物料基本单位时，V18 初始化 `businessUnitId=unit_id`、`businessQuantity=quantity`、`baseUnitId=unit_id`、`baseQuantity=quantity`、`quantityBasis=BASE_UNIT`。
- 既有 BOM 行的 `unit_id` 不等于子项物料基本单位且没有可证明的换算快照时，V18 保留原单位和数量，标记为 `LEGACY_BUSINESS_UNIT`，允许查询和复制，不允许直接发布；用户必须补齐换算关系后复制为新版本再发布。
- 既有工单快照的 `unit_id` 等于物料基本单位时，V18 初始化基本单位快照；不等于基本单位且缺少换算关系的历史工单快照保留查询，不允许继续过账新的生产领退补料或完工入库，必须取消并基于治理后的 BOM 新建工单。
- 库存余额、库存流水、库存预留和追踪分配在 V18 存量升级断言中必须满足 `unit_id = mst_material.unit_id`；否则迁移验证失败并按历史数量口径阻断处理。

### 编码规则

- 编码规则受控对象固定为 `MATERIAL`、`CUSTOMER`、`SUPPLIER`、`BOM`、`BOM_ECO`。021 只接入这些对象，不为采购、销售、生产、库存、发票、凭证或月结单据生成编码。
- 编码生成必须在后端事务内完成，占号后不得被其他并发请求复用；页面展示的自动编码只能来自后端编码生成接口，不得由前端自行拼接。
- 编码规则至少由对象类型、前缀、日期段、流水长度、重置周期、下一个流水号和状态组成。
- 同一对象类型只能有一个启用的默认编码规则；停用规则后，不允许继续自动生成该对象编码。
- 历史编码不改写；手工编码继续允许，但必须经过后端唯一性校验。
- 自动生成编码与手工编码共享唯一约束；冲突时返回稳定业务错误，不允许前端自行递增重试。
- 编码规则变更只影响后续生成，不影响已生成编码。

### 物料成本属性规则

- 物料成本属性是后续库存计价和项目成本的主数据输入，不代表当前库存金额或正式成本已经计算。
- 成本分类用于区分直接材料、辅助材料、半成品、成品、外协或服务类对象。
- 库存计价分类用于表达物料是否参与库存价值、是否需要后续库存计价和是否可能进入项目成本。
- 021 不在物料上固定项目专采或公共库存成本方法；项目专采实际批次成本和公共库存移动加权平均由 023 根据库存归属和计价规则处理。
- 既有物料可迁移为稳定的未分类状态，查询和历史业务不受影响；新建或重新启用物料必须补齐最小成本属性。
- 成本属性变更必须写审计，并在存在后续业务事实时保留历史兼容口径；021 不追溯重算已存在成本记录。

### 客户和供应商结算税务规则

- 客户和供应商结算税务资料是后续发票、往来、收付款和财务凭证的主数据输入，不代表 021 已支持开票或总账。
- 纳税识别号、开户行、银行账号、注册地址电话等字段按敏感资料处理；无敏感查看权限时必须脱敏或不返回完整值。
- 默认税率、发票类型、结算方式、账期天数和付款或收款条件必须使用后端受控校验。
- 默认币种为人民币；021 不支持多币种。
- 结算税务资料变更不重算既有应收、应付、收款、付款或调整记录。
- 历史客户和供应商无需补造结算税务资料即可继续参与 000 至 020 已有查询和业务链路；后续业务入口如需强制完整资料，必须在对应阶段单独定义校验规则。

### BOM 版本治理规则

- BOM 状态沿用现有 `DRAFT`、`ENABLED`、`DISABLED`，其中 `ENABLED` 表示已发布有效版本，`DISABLED` 表示停用或历史版本。
- 草稿 BOM 可编辑；已发布 BOM 不允许普通编辑父项、版本编码、基准数量、基准单位和明细。
- 已发布 BOM 需要变更时，必须复制或创建新版本，并通过工程变更记录说明原因、影响范围和新旧版本关系。
- 同一父项物料下版本编码唯一。
- 同一父项物料在同一业务日期只能存在一个已发布有效 BOM；有效期重叠必须被后端拒绝。
- 有效期开始日期不得晚于结束日期；`effectiveFrom = null` 表示从无限早开始有效，`effectiveTo = null` 表示持续有效到无限未来；区间两端均为闭区间。
- 两个已发布 BOM 的有效区间判断使用 `coalesce(effectiveFrom, 0001-01-01) <= coalesce(other.effectiveTo, 9999-12-31)` 且 `coalesce(effectiveTo, 9999-12-31) >= coalesce(other.effectiveFrom, 0001-01-01)`；满足该条件即为重叠。
- BOM 发布时必须校验父项物料、子项物料、单位、用量、损耗率和循环引用。
- 已发布工单使用发布时写入的工单用料快照；后续 BOM 发布、停用、工程变更或替代料维护不得回写已发布工单。
- 021 不新增 `/publish` 接口；后端继续使用 `PUT /api/admin/boms/{id}/enable`，前端按钮和页面文案统一显示为“发布”，权限仍为 `material:bom:enable`。
- V18 必须删除 `uk_mfg_bom_enabled_parent`，改由服务层发布事务保证“同父项同日期仅一个已发布有效版本”。
- BOM 发布事务必须先锁定父项物料行，再锁定该父项全部 BOM 行，随后校验目标 BOM 明细、基本单位数量、有效期区间和同父项已发布版本重叠；校验通过后才能把目标状态置为 `ENABLED`。
- V18 保留 `BOM_ENABLED_VERSION_EXISTS` 枚举只用于历史兼容；021 服务不再主动返回该错误码。有效期冲突统一返回 `BOM_EFFECTIVE_DATE_OVERLAP`，前端若遇到历史环境返回的 `BOM_ENABLED_VERSION_EXISTS`，按同一文案展示。

### 状态口径

| 对象 | 状态 | 规则 |
|---|---|---|
| 单位换算 | `ENABLED`、`DISABLED` | 启用关系可用于后端换算；停用关系只保留历史查询，不参与后续换算。 |
| 编码规则 | `ENABLED`、`DISABLED` | 同一对象类型只能存在一个启用默认规则；停用后不能继续自动生成编码。 |
| 物料成本属性 | 随物料状态 | 成本属性不是独立生命周期；物料启用时必须满足最小完整性，历史未分类值可查询。 |
| 客户和供应商结算税务资料 | 随客户或供应商状态 | 结算税务资料不是独立生命周期；客户或供应商停用不删除资料。 |
| BOM | `DRAFT`、`ENABLED`、`DISABLED` | 草稿可编辑；已发布不可普通编辑明细；停用保留历史追溯。 |
| 工程变更 | `DRAFT`、`APPLIED`、`CANCELLED` | 只表达版本治理记录，不表达审批流程。 |
| 替代料 | `ENABLED`、`DISABLED` | 启用关系可供人工识别和后续阶段引用；停用关系只保留历史查询。 |

### 工程变更状态机

工程变更只表达 BOM 版本治理记录，不表达审批流程。

| 状态 | 含义 | 允许操作 |
|---|---|---|
| `DRAFT` | 变更记录已创建，尚未应用到 BOM 发布关系 | 编辑变更摘要、取消、应用 |
| `APPLIED` | 变更已应用，新旧 BOM 版本关系已生效 | 查询、追溯 |
| `CANCELLED` | 变更记录作废，未改变 BOM 发布关系 | 查询、追溯 |

状态流转：

```text
DRAFT -> APPLIED
DRAFT -> CANCELLED
```

规则：

- 工程变更必须引用来源 BOM 和目标 BOM；来源 BOM 必须为同父项 `ENABLED` 版本，目标 BOM 必须为同父项 `DRAFT` 新版本。
- 工程变更必须有 `effectiveFrom`；`effectiveFrom` 是目标 BOM 发布生效日期，也是来源 BOM 历史区间截断日期的次日。
- 工程变更应用前必须重新校验目标 BOM 明细、基本单位数量、有效期、版本唯一性和有效期冲突。
- 工程变更应用必须在同一事务内锁定来源 BOM、目标 BOM 和父项物料，设置目标 BOM 为 `ENABLED`，设置目标 `effectiveFrom = 工程变更.effectiveFrom`，保留目标已有 `effectiveTo`，并把来源 BOM 的 `effectiveTo` 设置为 `工程变更.effectiveFrom - 1 天`。
- 来源 BOM 应继续保持 `ENABLED` 状态以表达历史已发布版本；如果来源 BOM 截断后有效期为空或倒置，应用必须失败。
- 工程变更应用响应必须返回 `sourceBomBefore`、`sourceBomAfter`、`targetBomBefore`、`targetBomAfter`、`appliedAt` 和 `appliedBy`。
- 应用后的工程变更不得编辑或取消。
- 取消工程变更必须保留取消原因和审计。

### 替代料规则

- 替代料关系不改变 BOM 原始明细，只建立“主物料可被哪些物料替代”的受控资料。
- 替代范围可表达全局物料、父项物料或 BOM 相关范围；021 不建立项目专属替代策略。
- 主物料和替代物料必须存在且启用，且不得相同。
- 替代关系必须有优先级；同一范围内启用关系的优先级不得冲突。
- 替代比例必须大于 0，用于后续人工判断和未来阶段换算参考。
- 替代料有效期不得与同一主物料、替代物料、范围和优先级的启用关系冲突。
- 021 不自动替代采购、领料、BOM 明细或缺料建议；任何自动使用替代料的能力必须由后续阶段另行定义。

## 页面入口与桌面端交互范围

- `/master/units`：继续只作为计量单位主数据维护入口，不承担物料级换算关系的主维护。
- `/master/unit-conversions`：新增“基础资料 > 物料单位换算”菜单，路由名 `master-unit-conversions`，组件职责为物料单位换算唯一主维护入口，权限为 `master:unit-conversion:view`，必须加入前端 `supportedMenuPaths` 和后端权限种子。
- `/master/coding-rules`：新增“基础资料 > 编码规则”菜单，路由名 `master-coding-rules`，组件职责为编码规则维护和生成记录查看，权限为 `master:coding-rule:view`，必须加入前端 `supportedMenuPaths` 和后端权限种子。
- `/materials/items`：扩展物料档案列表、详情和表单，展示基本单位锁定原因、业务单位换算摘要、成本属性和库存计价分类；物料详情只提供跳转到 `/master/unit-conversions?materialId={id}` 或打开同一换算抽屉的入口，不另建第二套维护面。
- `/master/customers`：扩展客户资料，增加结算税务资料区域和敏感字段权限态。
- `/master/suppliers`：扩展供应商资料，增加结算税务资料区域和敏感字段权限态。
- `/materials/boms`：扩展 BOM 管理，路由名继续为 `material-boms`，页面固定为“BOM 版本”“工程变更”“替代料”三个页签；BOM 版本页签承接现有列表、详情、复制、发布、停用；工程变更页签承接 ECO 列表和详情；替代料页签承接替代关系维护。
- BOM 版本详情采用右侧详情抽屉；已发布版本详情顶部显示只读提示，并把核心动作固定为“复制新版本”“创建工程变更”“停用”。普通编辑按钮只对草稿版本可见。
- 工程变更详情展示来源 BOM、目标 BOM、变更原因、影响范围、变更摘要、状态、审计摘要和应用结果；替代料详情展示主物料、替代物料、适用范围、优先级、比例、有效期和状态。
- 创建物料、客户、供应商、BOM 和工程变更时，编码字段旁固定显示“生成编码”按钮；按钮需要对象创建权限和 `master:coding-rule:generate` 权限。生成中按钮进入加载态并禁止重复点击；生成失败显示后端错误；用户手工填写编码时以后端唯一校验为准；编辑态历史编码只读，不允许重新生成。
- 客户和供应商列表只展示结算税务资料“已维护/未维护”和脱敏摘要；详情在有敏感查看权限时展示完整值，无敏感查看权限时只展示脱敏值和“受限”标签；编辑表单按“基础资料”和“结算税务资料”分组，无敏感维护权限时结算税务字段禁用且不得把完整值放入前端状态。
- 页面必须覆盖桌面端主路径、空态、加载失败、字段校验失败、权限受限、敏感字段脱敏和历史兼容状态。
- 本阶段浏览器验收只覆盖本阶段新增或修改页面的桌面端真实运行效果；不保存截图，不建立视觉截图目录。

## 后端接口、权限、审计与异常语义

### 接口约束

- 既有主数据接口路径继续可用：`/api/admin/master/units`、`/api/admin/master/customers`、`/api/admin/master/suppliers`、`/api/admin/master/materials`。
- 既有 BOM 接口路径继续可用：`/api/admin/boms`。
- 新增单位换算、编码规则、工程变更和替代料资源必须使用 `/api/admin` 管理接口前缀，并在权限映射中显式登记。
- 现有物料、客户、供应商、BOM 响应可新增字段，但不得删除或重命名 000 至 020 已有字段。
- 写接口必须使用后端校验后的枚举、引用和版本；涉及并发更新的资源必须通过版本或行锁保证不会静默覆盖。
- 金额、税率、换算比例和数量使用字符串或高精度数字传输，不允许前端浮点数作为可信计算结果。
- 任何业务单位数量转换必须由后端完成；前端只展示转换结果和错误信息。
- 所有新增管理接口必须返回统一 `ApiResponse` 结构和稳定业务错误码，不能用裸字符串错误替代业务异常。

资源级操作约束：

| 资源 | 必须支持的后端操作 | 关键约束 |
|---|---|---|
| 物料单位换算 | 列表、详情、创建、更新、启用、停用、按物料查询可用换算、执行换算预览 | 按物料和业务单位受控；换算预览不写业务事实。 |
| 编码规则 | 列表、详情、创建或更新、启用、停用、生成编码 | 生成编码必须在后端事务中占号并校验唯一。 |
| 物料成本属性 | 随物料详情返回、随物料创建或更新维护、单独权限校验 | 不计算库存金额和项目成本。 |
| 客户结算税务资料 | 随客户详情返回、创建或更新、敏感字段受限返回 | 无敏感权限不得返回完整税号和银行账号。 |
| 供应商结算税务资料 | 随供应商详情返回、创建或更新、敏感字段受限返回 | 无敏感权限不得返回完整税号和银行账号。 |
| BOM 版本治理 | BOM 列表、详情、创建、更新草稿、复制版本、发布、停用、查询历史版本 | 已发布 BOM 普通更新必须拒绝。 |
| 工程变更 | 列表、详情、创建、更新草稿、应用、取消 | 应用时重新校验目标 BOM 和有效期。 |
| 替代料 | 列表、详情、创建、更新、启用、停用、按主物料或范围查询 | 不自动改写 BOM、采购、领料或缺料建议。 |

### 固定接口契约

通用字段类型：

- `id` 使用后端资源 ID，前端类型为 `string | number`。
- `version` 固定为 `number`。所有本阶段新增资源响应必须返回 `version`，更新和状态动作必须提交 `version`。
- `decimal` 字段在请求和响应中统一使用字符串，格式为不带千分位的十进制文本。021 必须把既有 BOM 的 `baseQuantity`、`quantity`、`lossRate` 扩展为字符串口径；前端表单只做格式校验，可信精度、换算和舍入由后端完成。
- 日期字段使用 `YYYY-MM-DD` 字符串或 `null`。
- 分页响应使用 `{ items, page, pageSize, total, totalPages }`。
- 候选响应使用 `{ items, selectedItems, page, pageSize, total, totalPages }`，其中 `selectedItems` 用于回显不在当前页的已选项。
- 候选接口必须支持 `keyword`、`page`、`pageSize`、`selectedIds`，`selectedIds` 为英文逗号分隔 ID；不得用固定前 100 条作为完整候选集。
- 候选项必须包含 `id`、`code`、`name`、`status`、`disabled`、`disabledReason` 和业务摘要字段；禁用项可展示但不可选择。

#### 物料单位换算接口

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/master/unit-conversions` | `keyword`、`materialId`、`businessUnitId`、`status`、`effectiveDate`、`page`、`pageSize` | 分页 `UnitConversionRecord` |
| `GET` | `/api/admin/master/unit-conversions/{id}` | 路径 ID | `UnitConversionRecord` |
| `POST` | `/api/admin/master/unit-conversions` | `UnitConversionPayload` | `UnitConversionRecord` |
| `PUT` | `/api/admin/master/unit-conversions/{id}` | `UnitConversionPayload` 加 `version` | `UnitConversionRecord` |
| `PUT` | `/api/admin/master/unit-conversions/{id}/enable` | `{ version: number }` | `UnitConversionRecord` |
| `PUT` | `/api/admin/master/unit-conversions/{id}/disable` | `{ version: number }` | `UnitConversionRecord` |
| `POST` | `/api/admin/master/unit-conversions/convert` | `{ materialId, businessUnitId, businessQuantity: decimal, businessDate?: date }` | `{ conversionId, materialId, businessUnitId, businessQuantity, baseUnitId, baseQuantity, conversionRateSnapshot, quantityScaleSnapshot, roundingModeSnapshot }` |
| `GET` | `/api/admin/master/unit-conversions/material-candidates` | `keyword`、`status`、`selectedIds`、`page`、`pageSize` | 候选响应 |
| `GET` | `/api/admin/master/unit-conversions/unit-candidates` | `keyword`、`status`、`selectedIds`、`page`、`pageSize` | 候选响应 |

`UnitConversionPayload` 字段：`materialId`、`businessUnitId`、`conversionRate: decimal`、`quantityScale: number`、`roundingMode`、`effectiveFrom?: date | null`、`effectiveTo?: date | null`、`remark?: string | null`。

`UnitConversionRecord` 字段：`id`、`materialId`、`materialCode`、`materialName`、`baseUnitId`、`baseUnitName`、`businessUnitId`、`businessUnitName`、`conversionRate`、`quantityScale`、`roundingMode`、`effectiveFrom`、`effectiveTo`、`status`、`lockedReason`、`createdAt`、`updatedAt`、`version`。

枚举：`roundingMode = HALF_UP | UP | DOWN`，`status = ENABLED | DISABLED`。

#### 编码规则接口

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/coding-rules` | `keyword`、`objectType`、`status`、`page`、`pageSize` | 分页 `CodingRuleRecord` |
| `GET` | `/api/admin/coding-rules/{id}` | 路径 ID | `CodingRuleRecord` |
| `POST` | `/api/admin/coding-rules` | `CodingRulePayload` | `CodingRuleRecord` |
| `PUT` | `/api/admin/coding-rules/{id}` | `CodingRulePayload` 加 `version` | `CodingRuleRecord` |
| `PUT` | `/api/admin/coding-rules/{id}/enable` | `{ version: number }` | `CodingRuleRecord` |
| `PUT` | `/api/admin/coding-rules/{id}/disable` | `{ version: number }` | `CodingRuleRecord` |
| `POST` | `/api/admin/coding-rules/generate` | `{ objectType, contextDate?: date }` | `{ objectType, ruleId, generatedCode, generatedAt }` |

`CodingRulePayload` 字段：`ruleCode`、`name`、`objectType`、`prefix`、`datePattern`、`serialLength: number`、`resetCycle`、`nextSerialNo: number`、`status`、`remark?: string | null`。

`CodingRuleRecord` 字段：`id`、`ruleCode`、`name`、`objectType`、`prefix`、`datePattern`、`serialLength`、`resetCycle`、`nextSerialNo`、`status`、`lastGeneratedCode`、`lastGeneratedAt`、`remark`、`createdAt`、`updatedAt`、`version`。

枚举：`objectType = MATERIAL | CUSTOMER | SUPPLIER | BOM | BOM_ECO`，`datePattern = NONE | YYYY | YYYYMM | YYYYMMDD`，`resetCycle = NEVER | YEAR | MONTH | DAY`，`status = ENABLED | DISABLED`。

#### 物料成本属性接口

物料成本属性不建立独立页面资源，随物料接口返回和维护：

- `GET /api/admin/master/materials` 和 `GET /api/admin/master/materials/{id}` 响应必须新增 `version`、`baseUnitImmutableReason`、`costCategory`、`inventoryValuationCategory`、`inventoryValueEnabled`、`projectCostEnabled`、`costAttributeCompleted`、`costRemark`。
- `POST /api/admin/master/materials` 请求必须包含成本属性字段。
- `PUT /api/admin/master/materials/{id}` 请求必须包含 `version`，并可维护成本属性字段。
- `PUT /api/admin/master/materials/{id}/enable` 与 `/disable` 请求体必须为 `{ version: number }`。

成本属性枚举：

- `costCategory = DIRECT_MATERIAL | AUXILIARY_MATERIAL | SEMI_FINISHED | FINISHED_GOOD | OUTSOURCING | SERVICE | UNCLASSIFIED`
- `inventoryValuationCategory = VALUATED_MATERIAL | NON_VALUATED_CONSUMABLE | SERVICE_NON_STOCK | UNCLASSIFIED`

021 不定义移动加权、批次实际成本、项目专采成本或正式成本计算算法。

#### 客户和供应商结算税务接口

结算税务资料使用独立接口，不放入普通客户或供应商更新接口，避免普通维护权限绕过敏感字段控制。

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/master/customers/{id}/settlement-tax` | 路径 ID | `SettlementTaxRecord` |
| `PUT` | `/api/admin/master/customers/{id}/settlement-tax` | `SettlementTaxPayload` 加 `version` | `SettlementTaxRecord` |
| `GET` | `/api/admin/master/suppliers/{id}/settlement-tax` | 路径 ID | `SettlementTaxRecord` |
| `PUT` | `/api/admin/master/suppliers/{id}/settlement-tax` | `SettlementTaxPayload` 加 `version` | `SettlementTaxRecord` |

`SettlementTaxRecord` 字段：`ownerType`、`ownerId`、`hasData`、`sensitiveRestricted`、`restrictedMessage`、`invoiceTitle`、`taxNo`、`taxNoMasked`、`registeredAddress`、`registeredPhone`、`bankName`、`bankAccount`、`bankAccountMasked`、`defaultTaxRate`、`invoiceType`、`settlementMethod`、`paymentTermDays`、`paymentTerms`、`remark`、`createdAt`、`updatedAt`、`version`。

敏感查看规则：无敏感查看权限时，`taxNo`、`registeredAddress`、`registeredPhone`、`bankName`、`bankAccount` 必须为 `null`，`taxNoMasked` 和 `bankAccountMasked` 可返回脱敏值，`sensitiveRestricted=true`。未填写时 `hasData=false` 且脱敏字段为 `null`，不得用无权限态冒充未填写。

`SettlementTaxPayload` 字段均可省略，采用“不传不改”语义；传 `null` 表示清空对应字段。`taxNo`、`registeredAddress`、`registeredPhone`、`bankName`、`bankAccount` 的新增、修改或清空必须具备敏感维护权限；无敏感维护权限时即使拥有普通客户或供应商更新权限也必须拒绝。

枚举：`invoiceType = GENERAL_VAT | SPECIAL_VAT | NONE`，`settlementMethod = MONTHLY | CASH_ON_DELIVERY | ADVANCE | CUSTOM`。

#### BOM 版本接口

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/boms` | `keyword`、`status`、`parentMaterialId`、`effectiveDate`、`includeHistory`、`page`、`pageSize` | 分页 `BomSummaryRecord` |
| `GET` | `/api/admin/boms/{id}` | 路径 ID | `BomDetailRecord` |
| `POST` | `/api/admin/boms` | `BomPayload` | `BomDetailRecord` |
| `PUT` | `/api/admin/boms/{id}` | `BomPayload` 加 `version` | `BomDetailRecord` |
| `POST` | `/api/admin/boms/{id}/copy` | `BomCopyPayload` | `BomDetailRecord` |
| `PUT` | `/api/admin/boms/{id}/enable` | `{ version: number }`，页面文案为“发布” | `BomDetailRecord` |
| `PUT` | `/api/admin/boms/{id}/disable` | `{ version: number }` | `BomDetailRecord` |
| `GET` | `/api/admin/boms/material-candidates` | `keyword`、`materialType`、`status`、`selectedIds`、`page`、`pageSize` | 候选响应 |
| `GET` | `/api/admin/boms/unit-candidates` | `keyword`、`status`、`selectedIds`、`page`、`pageSize` | 候选响应 |

`BomPayload` 字段：`bomCode`、`parentMaterialId`、`versionCode`、`name`、`baseQuantity: decimal`、`baseUnitId`、`effectiveFrom?: date | null`、`effectiveTo?: date | null`、`remark?: string | null`、`items`。

`BomItemPayload` 字段：`lineNo: number`、`childMaterialId`、`businessUnitId`、`businessQuantity: decimal`、`lossRate?: decimal`、`remark?: string | null`。`baseUnitId` 和 `baseQuantity` 不由前端提交，由后端换算生成。

`BomSummaryRecord` 必须包含 `version: number`，`baseQuantity` 为字符串。`BomItemRecord` 必须包含业务单位字段、基本单位字段、换算快照字段和 `quantityBasis`。`BomDetailRecord` 还必须包含 `historyRelations: BomHistoryRelationRecord[]`；每条关系至少包含 `ecoId`、`ecoNo`、`relationType: SOURCE | TARGET`、`sourceBomId`、`sourceBomCode`、`sourceVersionCode`、`targetBomId`、`targetBomCode`、`targetVersionCode`、`status`、`effectiveFrom`、`effectiveTo`、`appliedBy`、`appliedAt`。

#### 工程变更接口

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/bom-engineering-changes` | `keyword`、`status`、`sourceBomId`、`targetBomId`、`parentMaterialId`、`page`、`pageSize` | 分页 `BomEngineeringChangeRecord` |
| `GET` | `/api/admin/bom-engineering-changes/{id}` | 路径 ID | `BomEngineeringChangeRecord` |
| `POST` | `/api/admin/bom-engineering-changes` | `BomEngineeringChangePayload` | `BomEngineeringChangeRecord` |
| `PUT` | `/api/admin/bom-engineering-changes/{id}` | `BomEngineeringChangePayload` 加 `version` | `BomEngineeringChangeRecord` |
| `PUT` | `/api/admin/bom-engineering-changes/{id}/apply` | `{ version: number }` | `BomEngineeringChangeApplyResult` |
| `PUT` | `/api/admin/bom-engineering-changes/{id}/cancel` | `{ version: number, reason: string }` | `BomEngineeringChangeRecord` |
| `GET` | `/api/admin/bom-engineering-changes/source-bom-candidates` | `keyword`、`parentMaterialId`、`selectedIds`、`page`、`pageSize` | 候选响应 |
| `GET` | `/api/admin/bom-engineering-changes/target-bom-candidates` | `keyword`、`sourceBomId`、`selectedIds`、`page`、`pageSize` | 候选响应 |

`BomEngineeringChangePayload` 字段：创建时包含 `ecoNo`、`sourceBomId`、`targetBomId`、`effectiveFrom: date`、`effectiveTo?: date | null`、`changeReason`、`impactScope`、`changeSummary`、`remark?: string | null`；更新时编号只读，不允许改写。`ecoNo` 必须先由后端编码生成接口按 `BOM_ECO` 规则占号，前端不得自行拼接；创建服务校验必填、调用权限和唯一性后原值写入，不得在保存阶段二次生成。缺失编号返回 `VALIDATION_ERROR`，编号已占用或发生唯一冲突返回 `CODING_RULE_GENERATE_CONFLICT`。

`BomEngineeringChangeRecord` 字段：`id`、`ecoNo`、`sourceBomId`、`sourceBomCode`、`sourceVersionCode`、`targetBomId`、`targetBomCode`、`targetVersionCode`、`parentMaterialId`、`parentMaterialCode`、`parentMaterialName`、`effectiveFrom`、`effectiveTo`、`changeReason`、`impactScope`、`changeSummary`、`status`、`appliedBy`、`appliedAt`、`cancelReason`、`createdAt`、`updatedAt`、`version`。

#### 替代料接口

| 方法 | 路径 | 查询或请求 | 响应 |
|---|---|---|---|
| `GET` | `/api/admin/material-substitutes` | `keyword`、`mainMaterialId`、`substituteMaterialId`、`scopeType`、`scopeId`、`status`、`effectiveDate`、`page`、`pageSize` | 分页 `MaterialSubstituteRecord` |
| `GET` | `/api/admin/material-substitutes/{id}` | 路径 ID | `MaterialSubstituteRecord` |
| `POST` | `/api/admin/material-substitutes` | `MaterialSubstitutePayload` | `MaterialSubstituteRecord` |
| `PUT` | `/api/admin/material-substitutes/{id}` | `MaterialSubstitutePayload` 加 `version` | `MaterialSubstituteRecord` |
| `PUT` | `/api/admin/material-substitutes/{id}/enable` | `{ version: number }` | `MaterialSubstituteRecord` |
| `PUT` | `/api/admin/material-substitutes/{id}/disable` | `{ version: number }` | `MaterialSubstituteRecord` |
| `GET` | `/api/admin/material-substitutes/material-candidates` | `keyword`、`status`、`selectedIds`、`page`、`pageSize` | 候选响应 |
| `GET` | `/api/admin/material-substitutes/bom-candidates` | `keyword`、`parentMaterialId`、`selectedIds`、`page`、`pageSize` | 候选响应 |

`MaterialSubstitutePayload` 字段：`mainMaterialId`、`substituteMaterialId`、`scopeType`、`scopeId?: id | null`、`priority: number`、`substituteRate: decimal`、`effectiveFrom?: date | null`、`effectiveTo?: date | null`、`status?: ENABLED | DISABLED`、`remark?: string | null`。

枚举：`scopeType = GLOBAL | PARENT_MATERIAL | BOM`，`status = ENABLED | DISABLED`。

### 权限约束

021 权限编码、菜单、路由和 API 映射固定如下，权限种子必须幂等初始化并默认赋予系统管理员。

| 权限码 | 名称 | 类型 | 父级 | 前端路由 | API 方法与路径 |
|---|---|---|---|---|---|
| `master:unit-conversion` | 物料单位换算 | 菜单 | `master` | `/master/unit-conversions` | 无 |
| `master:unit-conversion:view` | 查看物料单位换算 | 动作 | `master:unit-conversion` | `/master/unit-conversions` | `GET /api/admin/master/unit-conversions/**`，含候选和换算预览 |
| `master:unit-conversion:create` | 创建物料单位换算 | 动作 | `master:unit-conversion` | `/master/unit-conversions` | `POST /api/admin/master/unit-conversions` |
| `master:unit-conversion:update` | 更新物料单位换算 | 动作 | `master:unit-conversion` | `/master/unit-conversions` | `PUT /api/admin/master/unit-conversions/{id}` |
| `master:unit-conversion:enable` | 启用物料单位换算 | 动作 | `master:unit-conversion` | `/master/unit-conversions` | `PUT /api/admin/master/unit-conversions/{id}/enable` |
| `master:unit-conversion:disable` | 停用物料单位换算 | 动作 | `master:unit-conversion` | `/master/unit-conversions` | `PUT /api/admin/master/unit-conversions/{id}/disable` |
| `master:coding-rule` | 编码规则 | 菜单 | `master` | `/master/coding-rules` | 无 |
| `master:coding-rule:view` | 查看编码规则 | 动作 | `master:coding-rule` | `/master/coding-rules` | `GET /api/admin/coding-rules/**`，不含生成 |
| `master:coding-rule:create` | 创建编码规则 | 动作 | `master:coding-rule` | `/master/coding-rules` | `POST /api/admin/coding-rules` |
| `master:coding-rule:update` | 更新编码规则 | 动作 | `master:coding-rule` | `/master/coding-rules` | `PUT /api/admin/coding-rules/{id}` |
| `master:coding-rule:enable` | 启用编码规则 | 动作 | `master:coding-rule` | `/master/coding-rules` | `PUT /api/admin/coding-rules/{id}/enable` |
| `master:coding-rule:disable` | 停用编码规则 | 动作 | `master:coding-rule` | `/master/coding-rules` | `PUT /api/admin/coding-rules/{id}/disable` |
| `master:coding-rule:generate` | 生成编码 | 动作 | `master:coding-rule` | 创建表单按钮 | `POST /api/admin/coding-rules/generate`，服务层同时校验对象创建权限 |
| `master:material-cost:view` | 查看物料成本属性 | 动作 | `master:material` | `/materials/items` | `GET /api/admin/master/materials/**` 响应成本属性 |
| `master:material-cost:update` | 维护物料成本属性 | 动作 | `master:material` | `/materials/items` | `POST /api/admin/master/materials`、`PUT /api/admin/master/materials/{id}` 中的成本属性字段服务层二次校验 |
| `master:customer-settlement:view` | 查看客户结算税务资料 | 动作 | `master:customer` | `/master/customers` | `GET /api/admin/master/customers/{id}/settlement-tax` |
| `master:customer-settlement:update` | 维护客户结算税务资料 | 动作 | `master:customer` | `/master/customers` | `PUT /api/admin/master/customers/{id}/settlement-tax` 非敏感字段 |
| `master:customer-settlement:sensitive-view` | 查看客户敏感税务资料 | 动作 | `master:customer` | `/master/customers` | 服务层控制完整敏感字段返回 |
| `master:customer-settlement:sensitive-update` | 维护客户敏感税务资料 | 动作 | `master:customer` | `/master/customers` | 服务层控制敏感字段新增、修改和清空 |
| `master:supplier-settlement:view` | 查看供应商结算税务资料 | 动作 | `master:supplier` | `/master/suppliers` | `GET /api/admin/master/suppliers/{id}/settlement-tax` |
| `master:supplier-settlement:update` | 维护供应商结算税务资料 | 动作 | `master:supplier` | `/master/suppliers` | `PUT /api/admin/master/suppliers/{id}/settlement-tax` 非敏感字段 |
| `master:supplier-settlement:sensitive-view` | 查看供应商敏感税务资料 | 动作 | `master:supplier` | `/master/suppliers` | 服务层控制完整敏感字段返回 |
| `master:supplier-settlement:sensitive-update` | 维护供应商敏感税务资料 | 动作 | `master:supplier` | `/master/suppliers` | 服务层控制敏感字段新增、修改和清空 |
| `material:bom:view` | 查看 BOM | 动作 | `material:bom` | `/materials/boms` | `GET /api/admin/boms/**` |
| `material:bom:create` | 创建 BOM | 动作 | `material:bom` | `/materials/boms` | `POST /api/admin/boms` |
| `material:bom:update` | 更新 BOM 草稿 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/boms/{id}` |
| `material:bom:copy` | 复制 BOM 版本 | 动作 | `material:bom` | `/materials/boms` | `POST /api/admin/boms/{id}/copy` |
| `material:bom:enable` | 发布 BOM | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/boms/{id}/enable` |
| `material:bom:disable` | 停用 BOM | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/boms/{id}/disable` |
| `material:bom-eco:view` | 查看工程变更 | 动作 | `material:bom` | `/materials/boms` | `GET /api/admin/bom-engineering-changes/**`，含候选 |
| `material:bom-eco:create` | 创建工程变更 | 动作 | `material:bom` | `/materials/boms` | `POST /api/admin/bom-engineering-changes` |
| `material:bom-eco:update` | 更新工程变更草稿 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/bom-engineering-changes/{id}` |
| `material:bom-eco:apply` | 应用工程变更 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/bom-engineering-changes/{id}/apply` |
| `material:bom-eco:cancel` | 取消工程变更 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/bom-engineering-changes/{id}/cancel` |
| `material:substitute:view` | 查看替代料 | 动作 | `material:bom` | `/materials/boms` | `GET /api/admin/material-substitutes/**`，含候选 |
| `material:substitute:create` | 创建替代料 | 动作 | `material:bom` | `/materials/boms` | `POST /api/admin/material-substitutes` |
| `material:substitute:update` | 更新替代料 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/material-substitutes/{id}` |
| `material:substitute:enable` | 启用替代料 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/material-substitutes/{id}/enable` |
| `material:substitute:disable` | 停用替代料 | 动作 | `material:bom` | `/materials/boms` | `PUT /api/admin/material-substitutes/{id}/disable` |

权限分工：

- `PermissionAuthorizationManager` 负责路径级权限判断；所有新增 `/api/admin` 路径必须有确定映射。
- 敏感结算税务字段由服务层基于 `CurrentUser.permissions()` 二次校验；路径级权限只能证明可访问结算税务资源，不能替代字段级敏感控制。
- 编码生成接口路径级权限为 `master:coding-rule:generate`，服务层还必须校验调用对象对应创建权限：`MATERIAL` 对应 `master:material:create`，`CUSTOMER` 对应 `master:customer:create`，`SUPPLIER` 对应 `master:supplier:create`，`BOM` 对应 `material:bom:create`，`BOM_ECO` 对应 `material:bom-eco:create`。
- 前端按钮显隐必须使用同一权限码；后端接口拒绝始终以权限为准。

### 审计约束

- 单位换算创建、更新、启用、停用写审计，目标类型使用稳定业务目标，不混用普通单位目标。
- 编码规则创建、更新、启用、停用、生成占号写审计，摘要包含对象类型和生成编码。
- 物料成本属性变更写审计，摘要包含物料编码和变更字段类型。
- 客户和供应商结算税务资料变更写审计，摘要不得明文暴露完整敏感值。
- BOM 发布、停用、复制新版本、工程变更应用、工程变更取消、替代料创建、替代料更新、替代料启用和停用写审计。
- 审计至少包含操作人、动作、目标类型、目标 ID、摘要、请求路径、结果和时间。

### 异常语义

- 字段缺失、枚举非法、日期区间非法、比例非法和税率非法返回校验类业务错误。
- 编码重复、单位换算有效期冲突、BOM 版本重复、BOM 有效期冲突、替代料优先级冲突返回冲突类业务错误。
- 引用不存在、引用已停用或跨物料、跨 BOM、跨客户供应商引用不合法返回引用类业务错误。
- 已发布 BOM 普通编辑、已有业务事实物料基本单位变更、已应用工程变更编辑、已发布工单快照回写尝试返回状态不允许类业务错误。
- 权限不足返回统一无权错误，不得以空数据掩盖写权限不足。
- 敏感字段无查看权限时，读接口返回脱敏值、`sensitiveRestricted=true`，完整字段为 `null`；写接口无维护权限时必须拒绝。
- 并发版本冲突返回稳定冲突错误，不能静默覆盖。

### 业务错误码矩阵

| 错误码 | HTTP 状态 | 场景 |
|---|---|---|
| `VALIDATION_ERROR` | `400` | 通用字段格式错误、分页参数非法、必填字段缺失。 |
| `AUTH_FORBIDDEN` | `403` | 路径权限不足、按钮越权调用、服务层权限不足。 |
| `VERSION_CONFLICT` | `409` | 本阶段新增资源或扩展资源的 `version` 并发冲突。 |
| `UNIT_CONVERSION_NOT_FOUND` | `404` | 单位换算关系不存在。 |
| `UNIT_CONVERSION_RATE_INVALID` | `400` | 换算比例小于等于 0、精度或舍入方式非法。 |
| `UNIT_CONVERSION_DATE_RANGE_INVALID` | `400` | 单位换算有效期开始晚于结束。 |
| `UNIT_CONVERSION_EFFECTIVE_OVERLAP` | `409` | 同一物料同一业务单位有效期重叠。 |
| `UNIT_CONVERSION_REQUIRED` | `409` | BOM 行或换算预览需要换算关系但当前不存在启用有效关系。 |
| `MATERIAL_BASE_UNIT_IMMUTABLE` | `409` | 已有业务事实的物料基本单位被普通编辑改变。 |
| `MATERIAL_COST_ATTRIBUTE_INCOMPLETE` | `400` | 新建或启用物料缺少最小成本属性。 |
| `CODING_RULE_NOT_FOUND` | `404` | 编码规则不存在。 |
| `CODING_RULE_OBJECT_TYPE_INVALID` | `400` | 编码对象类型不是 021 固定枚举。 |
| `CODING_RULE_DISABLED` | `409` | 对象类型无启用编码规则或规则已停用。 |
| `CODING_RULE_DUPLICATE_ENABLED` | `409` | 同一对象类型出现多个启用默认规则。 |
| `CODING_RULE_GENERATE_CONFLICT` | `409` | 并发生成或历史编码导致自动编码冲突。 |
| `SETTLEMENT_TAX_FIELD_INVALID` | `400` | 税率、发票类型、结算方式、账期或银行资料字段非法。 |
| `SETTLEMENT_TAX_SENSITIVE_FORBIDDEN` | `403` | 无敏感查看或维护权限访问完整敏感字段。 |
| `SETTLEMENT_TAX_CONCURRENT_MODIFICATION` | `409` | 客户或供应商结算税务资料并发更新冲突。 |
| `BOM_NOT_FOUND` | `404` | BOM 不存在，沿用现有码。 |
| `BOM_CODE_EXISTS` | `409` | BOM 编码已存在，沿用现有码。 |
| `BOM_VERSION_EXISTS` | `409` | 同一父项物料版本编码重复，沿用现有码。 |
| `BOM_STATUS_NOT_EDITABLE` | `409` | 已发布 BOM 普通编辑或状态不允许，沿用现有码。 |
| `BOM_EFFECTIVE_DATE_RANGE_INVALID` | `400` | BOM 有效期开始晚于结束。 |
| `BOM_EFFECTIVE_DATE_OVERLAP` | `409` | 同一父项物料已发布 BOM 有效期重叠。 |
| `BOM_LEGACY_UNIT_CONVERSION_REQUIRED` | `409` | 历史非基本单位 BOM 行缺少换算快照，不能发布。 |
| `BOM_ENGINEERING_CHANGE_NOT_FOUND` | `404` | 工程变更不存在。 |
| `BOM_ENGINEERING_CHANGE_STATUS_INVALID` | `409` | 工程变更状态不允许编辑、应用或取消。 |
| `BOM_ENGINEERING_CHANGE_TARGET_INVALID` | `400` | 目标 BOM 不是同父项草稿新版本或来源目标关系非法。 |
| `BOM_ENGINEERING_CHANGE_EFFECTIVE_INVALID` | `400` | 工程变更生效日期导致来源版本有效期倒置或目标区间非法。 |
| `BOM_ENGINEERING_CHANGE_CONCURRENT_MODIFICATION` | `409` | 工程变更应用或取消并发冲突。 |
| `MATERIAL_SUBSTITUTE_NOT_FOUND` | `404` | 替代料关系不存在。 |
| `MATERIAL_SUBSTITUTE_SELF_REFERENCE` | `400` | 主物料和替代物料相同。 |
| `MATERIAL_SUBSTITUTE_PRIORITY_CONFLICT` | `409` | 同一范围启用替代关系优先级冲突。 |
| `MATERIAL_SUBSTITUTE_EFFECTIVE_OVERLAP` | `409` | 同一替代关系有效期重叠。 |
| `MATERIAL_SUBSTITUTE_CONCURRENT_MODIFICATION` | `409` | 替代料并发更新冲突。 |
| `PRODUCTION_UNIT_CONVERSION_REQUIRED` | `409` | 历史非基本单位工单快照缺少换算快照，不能继续过账。 |

## 字段与数据迁移原则

- 数据库迁移必须在当前 `V17` 之后追加；当前仓库最新迁移为 `V17__sales_project_contract_schema.sql`，021 首个迁移使用 `V18__` 前缀。
- 迁移只能追加 021 所需表、字段、索引、约束和权限数据；不得删除 000 至 020 已有表、字段或历史记录。
- V18 必须删除 `uk_mfg_bom_enabled_parent`，新增或调整 BOM 有效期索引为 `parent_material_id, status, effective_from, effective_to`，并保留 `uk_mfg_bom_parent_version`。
- V18 必须新增 BOM、BOM 行、工单用料快照、单位换算、编码规则、工程变更、替代料、物料成本属性、客户供应商结算税务资料所需字段或表。
- V18 必须为 BOM 有效期增加开始不晚于结束的检查约束；有效期重叠通过发布服务事务锁和查询校验保证，不依赖前端校验。
- V18 必须迁移已有 BOM 行数量快照：基本单位行初始化为 `BASE_UNIT`，非基本单位行标记为 `LEGACY_BUSINESS_UNIT` 并允许基本数量为空。
- V18 必须迁移已有工单用料快照：基本单位行初始化基本数量，非基本单位且无换算快照行标记为历史不可继续过账。
- V18 必须断言库存余额、库存流水、库存预留和追踪分配中的 `unit_id` 与物料基本单位一致；断言失败视为历史数量口径阻断，不能继续交付。
- 单位换算迁移不为历史物料伪造业务单位；所有物料默认以基本单位可用。
- 物料成本属性迁移不得伪造已确认成本口径；既有物料使用稳定未分类值或空值兼容策略，并保证查询不失败。
- 客户和供应商结算税务资料迁移允许历史记录为空；历史客户和供应商继续可查询、可被既有业务引用。
- 编码规则迁移不得改写既有物料、客户、供应商、BOM 或单据编码；新规则只影响后续生成。
- BOM 迁移必须保留现有 BOM 编码、版本编码、有效日期、状态、明细和审计关联；不得批量改写历史版本含义。
- 生产工单用料快照表和已发布工单记录不得被迁移改写。
- 新增非空字段如作用于历史表，必须提供业务上保守且不会伪造事实的默认值，或采用允许为空并由后续维护补齐的兼容策略。
- 空库迁移和存量库升级都必须通过。存量库升级后，000 至 020 关键查询仍可执行。

### 迁移最小可执行断言清单

空库迁移必须执行并记录以下断言：

- 从 V1 顺序应用到 021 最新迁移，全部成功退出。
- 021 新增表、字段、索引和检查约束存在：单位换算、编码规则、物料成本属性、客户结算税务、供应商结算税务、BOM 工程变更、替代料、BOM 行基本数量字段、工单用料基本数量字段。
- `uk_mfg_bom_enabled_parent` 不存在，`uk_mfg_bom_parent_version` 存在。
- 权限种子包含 021 全部新增菜单和动作权限，系统管理员拥有全部新增权限。
- `PermissionAuthorizationManager` 覆盖 021 全部新增 `/api/admin` 路径，未映射路径测试必须失败。

V17 存量库升级必须先构造或保留至少一组历史数据：单位、物料、客户、供应商、BOM、已发布生产工单及 `mfg_work_order_material` 快照、库存余额或库存流水、往来记录、报表可查数据和追溯可查数据。升级到 021 最新迁移后必须执行并记录以下断言：

- 历史单位、物料、客户、供应商、BOM、生产工单、库存、往来、报表和追溯关键查询均可执行。
- 历史物料成本属性返回 `UNCLASSIFIED` 或兼容空值，不报错，不伪造成本口径。
- 历史客户和供应商结算税务资料返回 `hasData=false` 或脱敏兼容结构，不报错。
- 历史 BOM 的 `bom_code`、`version_code`、`status`、`effective_from`、`effective_to`、父项物料和明细行数不被改写。
- 历史 BOM 行如单位等于物料基本单位，基本数量等于历史数量；如非基本单位，标记为 `LEGACY_BUSINESS_UNIT`，查询可见且发布受阻。
- 已发布工单快照行数、物料、历史业务单位、历史业务数量、原 BOM 明细引用不变；基本单位行的基本数量等于历史数量。
- 库存余额、库存流水、库存预留和追踪分配至少各执行一条查询并断言单位为物料基本单位。
- 往来、报表和追溯至少各执行一条既有关键查询并返回受控结果。
- 权限初始化重复执行两次后，权限、菜单、管理员授权和路径映射数量保持唯一且完整。
- 交付验证记录必须保存命令、退出码和结论；视觉检验仍只记录范围、问题、处理结果和结论，不保存截图。

## 验收矩阵

| 类别 | 验收标准 |
|---|---|
| 正常路径 | 管理员能维护物料业务单位换算，并由后端返回基本单位换算结果。 |
| 正常路径 | 管理员能维护编码规则，并在物料、客户、供应商和 BOM 创建时使用后端生成编码。 |
| 正常路径 | 管理员能维护物料成本属性和库存计价分类，列表与详情展示一致。 |
| 正常路径 | 管理员能维护客户和供应商结算税务资料，敏感字段在有权限时完整可见。 |
| 正常路径 | 管理员能复制或创建 BOM 新版本，通过工程变更应用为已发布有效版本。 |
| 正常路径 | 管理员能维护替代料关系，按主物料或适用范围查看启用状态和优先级。 |
| 正常路径 | 单位、物料、BOM、工程变更和替代料候选接口支持分页搜索、禁用原因和选中项回显。 |
| 正常路径 | 物料、客户、供应商、BOM 和工程变更创建表单能按固定交互生成编码，手工编码仍经后端唯一性校验。 |
| 异常路径 | 单位换算比例小于等于 0、日期范围非法、同物料同业务单位有效期冲突时被拒绝。 |
| 异常路径 | 有业务事实的物料基本单位通过普通编辑变更时被拒绝。 |
| 异常路径 | 编码规则停用、规则不存在、自动生成编码与历史编码冲突时返回稳定错误。 |
| 异常路径 | 新建或启用物料缺少必要成本属性时被拒绝。 |
| 异常路径 | 税率、账期、发票类型、结算方式等字段非法时被拒绝。 |
| 异常路径 | 已发布 BOM 普通编辑关键字段或明细时被拒绝。 |
| 异常路径 | BOM 版本编码重复、有效期重叠、工程变更来源目标不匹配时被拒绝。 |
| 异常路径 | 替代料自替代、优先级冲突、比例非法或有效期冲突时被拒绝。 |
| 权限路径 | 无单位换算维护权限不能新增、编辑、启用或停用换算关系。 |
| 权限路径 | 无编码规则维护权限不能修改规则；仅有对象创建权限的用户只能调用对应对象生成编码能力。 |
| 权限路径 | 无物料成本属性维护权限不能修改成本属性。 |
| 权限路径 | 无客户或供应商敏感资料查看权限时，税号、银行账号等字段不得完整泄露。 |
| 权限路径 | 无 BOM 工程变更或替代料权限时不能执行对应写入或状态动作。 |
| 权限路径 | 权限种子重复初始化后权限码、菜单、管理员授权保持唯一且完整。 |
| 权限路径 | 021 新增 `/api/admin` 路径全部被 `PermissionAuthorizationManager` 映射测试覆盖。 |
| 接口路径 | 021 新增和扩展接口返回固定字段、`version`、decimal 字符串、分页结构和稳定错误码。 |
| 审计路径 | 单位换算、编码规则、成本属性、结算税务资料、BOM 发布、工程变更和替代料写操作均产生审计。 |
| 审计路径 | 审计摘要能识别对象、动作和关键变更类型，且不明文暴露完整敏感字段。 |
| 迁移路径 | 空库从 V1 到 021 最新迁移顺序应用通过。 |
| 迁移路径 | 空库断言 021 新表、字段、索引、约束、权限种子和管理员授权均存在。 |
| 迁移路径 | V1 至 V17 存量库升级到 021 后，历史单位、物料、客户、供应商、BOM、工单、库存、往来、报表和追溯关键查询可执行。 |
| 迁移路径 | 存量升级后 BOM 编码、版本、状态、有效期、明细行数和已发布工单快照行数、物料、数量、单位、BOM 明细引用不被改写。 |
| 历史兼容 | 历史已过账数量不因单位换算新增而重算。 |
| 历史兼容 | 历史客户、供应商、物料资料缺少 021 新字段时仍可查询，不阻断既有业务链路。 |
| 历史兼容 | 已发布工单 BOM 用料快照不因 BOM 新版本、工程变更或替代料维护而改变。 |
| 浏览器验收 | 桌面端可访问并操作单位换算、编码规则、物料成本属性、客户结算税务、供应商结算税务、BOM 工程变更和替代料路径。 |
| 浏览器验收 | 桌面端新增或修改页面的主操作、状态、错误信息、权限受限态、空态和加载失败态清晰可见，无关键文案溢出或控件重叠。 |
| 浏览器验收 | 真实页面视觉检验只记录范围、问题、处理结果和结论，不保存截图。 |

## 阶段集中审查

全部工作包达到功能完整后，只组织一轮阶段集中审查：

- 产品经理：核对本文件的业务目标、范围边界、产品规则、排除项和验收矩阵，确认没有缩水、越界或把未来能力写成当前能力。
- UI 设计师：审查单位换算、编码规则、物料成本属性、客户供应商结算税务、BOM 版本、工程变更、替代料页面的桌面端布局、信息密度、状态识别、权限态、异常态和视觉结论。
- 前端开发：审查非本人实现的后端接口行为、错误语义、权限受限响应、字段稳定性和前端可消费性；不得自审自己的前端实现质量。
- 后端开发：审查非本人实现的前端请求载荷、接口消费、权限态处理、类型一致性和异常呈现；不得自审自己的后端实现质量。
- 测试：审查自动化覆盖、接口覆盖、异常路径、权限路径、审计路径、迁移路径、历史兼容和回归风险。

主代理合并并去重审查意见，一次性交给对应角色整改。阻断和严重问题必须修复；一般问题默认进入后续清单，除非影响当前验收或用户明确要求，否则不得反复阻断阶段交付。

集中整改后只复审修复差异和受影响路径，不重新审查未变化的整个阶段。只有差异复审仍存在阻断或严重问题时，才允许继续返工。

## 唯一交付前全量验证窗口

进入交付前窗口的前提：

- 三个工作包功能完整。
- 一轮集中审查已完成。
- 阻断和严重问题已完成集中整改。
- 差异复审未发现新的阻断或严重问题。

交付前窗口必须完成：

- 后端全量测试通过。
- 前端全量测试通过。
- 前端类型检查通过。
- 前端生产构建通过。
- `git diff --check` 通过。
- 空库迁移从 V1 至 021 最新迁移顺序应用通过。
- V1 至 V17 存量库升级到 021 最新迁移通过，历史业务关键查询可执行。
- `AccountPermissionInitializerTests` 覆盖 021 新增权限种子幂等、管理员授权完整性和重复初始化唯一性。
- `PermissionAuthorizationTests` 覆盖 021 新增全部 `/api/admin` 路径映射和未授权拒绝。
- 本阶段新增或修改页面的桌面端浏览器验收通过。
- 本阶段新增或修改页面的桌面端视觉检验通过，并在交接或执行记录中写明检查范围、发现问题、处理结果和最终结论。
- 交付验证记录必须保存关键命令、退出码和结论；浏览器视觉检验不保存截图。

全量验证窗口发现问题时，先用定向测试定位和修复，再验证受影响范围；交付前必须在同一窗口完成最终全量确认。

## 阻断缺陷

以下问题属于 021 阶段阻断缺陷，存在时不得合入主分支，不得通知用户验收：

- 数据库迁移失败，或存量库升级导致 000 至 020 历史关键查询不可执行。
- 新增 `/api/admin` 管理接口未配置权限映射，或直接调用接口可绕过权限。
- 单位换算错误导致基本单位数量不可信，或历史已过账数量被重算。
- 有业务事实的物料基本单位可被普通编辑改变。
- 编码规则并发生成重复编码，或自动生成覆盖历史编码。
- 物料成本属性、库存计价分类、客户供应商结算税务资料无法保存或查询。
- 敏感税务、银行资料在无权限时完整泄露。
- 已发布 BOM 可被普通编辑改明细或关键字段。
- BOM 有效期冲突未被拦截，导致同一父项物料同一业务日期命中多个已发布版本。
- 工程变更应用后新旧版本关系不可追溯。
- 替代料自动改写采购、领料、BOM 明细或缺料建议。
- 已发布生产工单用料快照被后续 BOM 治理、工程变更或替代料维护改写。
- 本阶段核心页面无法通过浏览器访问或核心主路径不可操作。
- 关键写入动作缺失审计。
- 桌面端关键操作、权限状态、错误信息或业务状态不可识别，影响阶段验收路径执行。

## 完成定义

021 只有同时满足以下条件，才可视为阶段完成：

- 本文件定义的 3 个工作包全部实现完成。
- 单位换算、编码规则、成本属性、结算税务资料、BOM 版本治理、工程变更和替代料均可按权限维护并完整审计。
- 000 至 020 历史主数据、业务单据、库存、生产、往来、报表和追溯路径继续可查询、可运行。
- 已发布生产工单 BOM 用料快照不受后续 BOM 版本、工程变更和替代料影响。
- 阶段集中审查、集中整改和差异复审通过。
- 唯一交付前全量验证窗口全部通过。
- 阶段执行记录和必要交接更新完成，并明确 021 是已交付能力，022 至 036 仍是后续阶段能力。

## 范围控制与风险

- 单位换算影响采购、销售、生产、库存和成本数量口径。021 只建立后端受控换算和主数据契约，不批量重算历史单据。
- 物料成本属性容易被误解为正式成本核算。021 只维护主数据，不计算库存金额、项目成本、在制、完工或毛利。
- 客户和供应商结算税务资料容易滑向发票和总账。021 只维护资料，不生成发票、凭证、应收应付重算或财务结账。
- 编码规则容易扩展成通用低代码规则引擎。021 只支持固定对象的编码规则和后端生成，不建设可编程规则平台。
- BOM 工程变更容易扩展成审批平台。021 工程变更只表达版本治理记录，不建设审批、待办、消息或附件。
- 替代料容易被误用于自动缺料和自动领料。021 只维护受控关系，不自动替代任何业务单据。
- 有效期治理可能影响现有 BOM 唯一启用规则。实现必须在迁移和服务层同时保护历史数据、当前启用版本和后续有效期规则，不能让旧 BOM 查询或工单发布路径失效。
