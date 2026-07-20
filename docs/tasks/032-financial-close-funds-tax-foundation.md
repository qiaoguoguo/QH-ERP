# 032 财务结账与资金税务基础

> **固定角色执行要求：** 本文件是 032 唯一权威阶段说明，同时承载目标、设计、业务规则、状态机、权限、接口、数据迁移、工作包、验收矩阵、审查记录、变更记录和交付证据。执行角色必须使用 `superpowers:test-driven-development`；按工作包执行时使用 `superpowers:subagent-driven-development` 的测试先行、实现、定向验证和复核方法，但只复用当前交接登记的五个固定长期角色，不创建新角色或一次性审查代理。开发期只运行受影响的定向验证，唯一集中审查、一次集中整改、差异复审和唯一交付前全量验证窗口按本文件与仓库规则执行。

**目标：** 在 031 单公司、单账簿、人民币正式凭证和总账基础之上，交付财务期间关闭与受控反结账、期末损益结转、银行账户及银行对账、可追溯税务基础汇总和税款台账，使制造业财务人员能够完成月末财务结账闭环，同时不把 030 业务月结误作财务结账、不把税务基础扩大成申报或税控平台。

**架构：** 032 前向新增独立 `financialclose` 后端领域和 V34 数据模型，代码与表边界不并入 030 `periodclose` 或 028 `finance`；它只读消费 028/030/031 已有事实，通过受控服务创建新的 031 正式凭证草稿，并受控切换 031 会计期间当前状态。前端代码使用独立 `financialClose` 模块，产品导航仍归现有“会计核算”和 `/gl` 路由族，避免混入 028“财务往来”。

**技术栈：** Java 21、Spring Boot 4.0.7、JdbcTemplate、PostgreSQL、Flyway、Vue 3、TypeScript、Element Plus、Vite、Vitest。

## 全局约束

- 权威开发基线：`origin/main` 提交 `30ad38c0948cae2f1d7a64c1f41e4b7c625a750e`。
- 权威开发工作区：`C:\Users\14567\.codex\worktrees\7b6d\qherp`。
- 权威开发分支：`codex/032-financial-close-funds-tax-foundation`。
- 正式主工作区：`F:\zhangqiao\AI-study\qherp`；只有交付窗口全部通过后才允许纯快进合入 `main`、推送并从该工作区启动验收服务。
- V34 只能前向追加；不得修改 V1—V33。必须保持 V29 checksum `774334682`、V30 checksum `2130342893`、V31 checksum `-2074547591`、V32 checksum `249406902`、V33 checksum `612501943`。
- 单公司、单账簿 `MAIN`、本位币 `CNY`；不做多公司、多组织、多账套、多账簿、多币种、汇率、外币折算或银行直连。
- 所有金额在数据库使用 `numeric(18,2)`，API 使用十进制字符串；前端不得以 JavaScript 浮点数重算余额、差额、税额或结账结论。
- 032 不回写 028 发票、费用、收付款、核销或凭证草稿，不回写 029 项目成本，不回写 030 业务期间、月结运行或经营快照，不修改 031 已 `POSTED` 凭证和不可变账簿。
- 030 同自然月业务月结 `CLOSED` 是财务关闭的强制前置，但 030 仍是独立业务事实；银行对账、税务汇总草稿和结账检查预览可以在 030 未关闭时进行。
- 财务关闭本身使用强权限、二次确认、期间级互斥、事务内复检和完整审计，不再叠加审批；反结账必须填写原因并走 022 固定双人审批，申请人不得审批自己的申请。
- 新增和受影响页面必须完整遵守 `docs/ui/page-standards.md`；只验收真实桌面页面，不截图、不测试移动端，但实现结构不得阻碍后续响应式适配。
- 开发期只运行受影响的定向测试；全部工作包功能完整并通过集中审查和差异复审后，才进入唯一交付前全量验证窗口。
- 主代理只负责范围冻结、统筹、审查汇总、文档、Git、交付判断和交接；产品、UI、前端、后端和测试工作必须由对应固定角色完成。

---

## 阶段状态

- 状态：隔离分支整包开发与开发期定向验证已完成，准备唯一一轮五角色集中审查。
- 冻结日期：2026-07-20。
- 用户授权：用户明确要求主代理作为项目负责人自主决策、自主推进 032 开发落地；本文件内未越出单公司制造业 ERP 路线的裁决无需重复等待确认。
- 启动基线：当前工作树为 linked worktree、detached HEAD；`HEAD`、本地 `main`、`origin/main`、实际远端 `main` 均为 `30ad38c0948cae2f1d7a64c1f41e4b7c625a750e`。正式 Web `5173`、API `18080` 为 HTTP 200，PostgreSQL 和 MinIO 健康；Flyway 最新成功 V33、失败迁移 0；数据库 `AVAILABLE` 文件与 MinIO 对象为 18/18。
- 正式财务事实：V33 已存在单一 `MAIN/CNY` 账簿和开放会计期间，但正式库尚无正式凭证、已记账分录或来源占用；032 不为正式库补造财务关闭、银行对账或税务汇总事实。
- 正式经营事实：030 的业务月结状态与阻断保持既有事实。032 的交付不以修改正式 030 事实为前提；关闭正例在隔离验收数据中验证，正式页面应如实显示当前不可关闭原因。

## 权威输入与合规依据

- `AGENTS.md`
- `docs/handoffs/2026-07-12-project-handoff-current.md`
- `docs/ops/stage-collaboration-delivery-process.md`
- `docs/ui/page-standards.md`
- `docs/testing/acceptance-criteria.md`
- `docs/tasks/022-fixed-approval-document-platform.md` 及现有 022 审批实现
- `docs/tasks/028-invoice-expense-settlement-deepening.md`
- `docs/tasks/030-business-period-snapshot.md`
- `docs/tasks/031-accounting-voucher-general-ledger-foundation.md`
- 财政部《企业会计准则——基本准则》：`https://tfs.mof.gov.cn/caizhengbuling/201407/t20140729_1119494.htm`。
- 《中华人民共和国增值税法》，自 2026-01-01 施行：`https://fgk.chinatax.gov.cn/zcfgk/c100009/c5237365/content.html`。
- 《中华人民共和国发票管理办法》：`https://fgk.chinatax.gov.cn/zcfgk/c100010/c5195084/content.html`。
- 国家税务总局公告 2024 年第 11 号，数电发票全国推广：`https://fgk.chinatax.gov.cn/zcfgk/c100012/c5236067/content.html`。
- 《中华人民共和国企业所得税法》：`https://fgk.chinatax.gov.cn/zcfgk/c100009/c5193018/content.html`。
- 《中华人民共和国城市维护建设税法》：`https://fgk.chinatax.gov.cn/zcfgk/c100009/c5193055/content.html`。
- 中国人民银行关于基本存款账户和一般存款账户口径的公开答复：`https://wzdt.pbc.gov.cn/eportal/ui?msgDataId=8848e90bbff048a690719444b6394787&pageId=77c3557bd521439ea5cd869f5393ba98`。

本阶段只将上述依据转化为会计期间、可配置税率/票种、银行账户类型、来源追溯和汇总口径，不宣称产品能够代替专业税务判断、电子税务局申报、税控、发票平台或正式财务报告。

## 五角色唯一目标讨论结论

| 角色 | 当前有效会话 | 冻结结论 |
|---|---|---|
| 产品经理 | `019f7f42-c523-7a30-87f6-1a27e3101810` | 采用独立 032 财务结账域；纳入关闭/反结账、损益结转、银行对账、税务主数据与汇总、税款台账；关闭依赖同月 030 `CLOSED`，反结账走固定审批。 |
| UI 设计师 | `019f7f42-e2ae-76c1-a478-445aad81a394` | 032 归现有“会计核算”和 `/gl`；结账工作台显示摘要与阻断，详细事实进入分组宽表和追溯抽屉；银行对账候选池不得受主表十条分页限制。 |
| 前端开发 | `019f7f43-0212-7700-bcc0-1fcd305f29e1` | 代码采用独立 `financialClose` 模块、API、路由、菜单和测试，只对注册入口、031 页面和审批中心做最小接线；金额字符串化，动作以后端 `allowedActions/actionDisabledReasons` 为准。 |
| 后端开发 | `019f7f43-11be-73f1-8e28-7f1df97521bc` | V34 新增独立 `financialclose` 包和 `fin_close_/fin_bank_/fin_tax_` 表；关闭原子复检，反结账留存旧版本，损益结转只创建 031 凭证，028/030 只读。 |
| 测试 | `019f7f43-24a7-7c51-8f68-bd68bdbe7515` | 独立正式 V33 副本与专用跨期数据验收；覆盖状态、并发、双人审批、金额、来源、迁移、权限、桌面页面和对象一致性；开发期定向、交付前唯一全量窗口。 |

## 方案比较与负责人裁决

### 方案一：独立 032 领域、会计核算导航、正式凭证承接（采用）

- 后端代码和数据独立，前端代码独立，但导航归 `/gl`。
- 关闭运行、银行对账和税务汇总保存来源指纹、版本、幂等和审计；正式会计影响只通过 031 凭证产生。
- 优点：不污染 028/030/031 已交付边界，状态、权限和追溯清晰，能为后续正式财务报表提供可信期间事实。
- 代价：V34 数据模型和跨域只读适配较多，但复杂度与阶段目标匹配。

### 方案二：扩展 030 业务月结（不采用）

- 不采用原因：030 锁定业务期间并形成经营快照，032 关闭会计期间并依赖已记账账簿、银行和税务事实；复用会混淆状态、权限、数据责任和验收结论。

### 方案三：只做关闭状态和只读汇总（不采用）

- 不采用原因：缺少正式损益结转、反结账审批、可确认银行对账和税务汇总版本，无法形成可验证财务结账闭环。

### 方案四：扩展成完整税务申报、税控和财务报表（不采用）

- 不采用原因：会提前引入电子税务局报送、红字发票、税控、出口退税、完整三大报表和 033 经营财务分析，超过一期边界。

## 负责人冻结裁决

1. 后端包名固定为 `com.qherp.api.system.financialclose`，表前缀按能力使用 `fin_close_`、`fin_bank_`、`fin_tax_`；前端模块名固定为 `financialClose`。
2. 页面入口固定归“会计核算”和 `/gl`，不新增一级菜单、不并入 028“财务往来”。API 统一使用 `/api/admin/financial-closes`、`/api/admin/bank-*`、`/api/admin/tax-*`，不得复用 `/api/admin/period-closes`。
3. `gl_accounting_period.status` 当前态只使用 `OPEN/CLOSED`。反结账成功后恢复 `OPEN`；`REOPENED` 只存在于关闭运行历史，不成为会计期间长期当前态。
4. `fin_close_check_run` 状态固定为 `CHECKING/BLOCKED/READY/STALE/CONSUMED/FAILED`，`fin_close_run` 状态固定为 `CLOSED/REOPENED`。任何来源指纹变化使未执行的 `READY` 检查失效为 `STALE`；关闭成功后该检查进入 `CONSUMED` 并新建 `CLOSED` 关闭运行。关闭必须在同一事务内重算全部检查，不能信任旧页面结果。
5. 财务关闭不走审批；用户必须具备 `financial-close:period:close`，填写至少 2 个非空中文字符的原因，使用统一高风险确认，并由后端期间行锁/数据库事务保证单成功者。反结账必须具备独立权限、原因和 022 `FINANCIAL_PERIOD_REOPEN` 双人审批。
6. 同自然月 030 业务月结未 `CLOSED` 时，财务关闭必为 `BLOCKED`；但页面必须显示这是“业务月结前置”，不得把两者状态合并。
7. 期末损益结转只处理当前月 `PROFIT_LOSS` 末级科目的已记账净发生额，生成 031 正式凭证草稿并复用 `GL_VOUCHER_POST` 审批记账。本阶段新增 `4103 本年利润`；不做年末“本年利润转未分配利润”、利润分配、股利和盈余公积自动处理。
8. 银行账户只能绑定 `1002 银行存款` 或其启用、可记账末级下级科目。系统不保存可恢复的完整账号，只保存规范化账号 SHA-256 指纹、末四位和脱敏显示；不接银行直连。
9. 银行流水支持 CSV 导入和手工录入，原文件不写入正式对象存储。匹配支持多对多、零金额容差；确认对账允许已解释未达项，不允许未解释差额。
10. 税务基础包括单公司税务档案、有效期税率/征收率、票种、增值税汇总、城建税及可配置附加税费计提建议、企业所得税估算与调整、税款缴纳台账。所有结果明确标注“基础汇总/估算，非正式申报”。
11. 税务汇总从 028 当前有效、已确认事实和 031 已记账账簿读取，保存来源指纹；来源变化使未关闭版本 `STALE`。税务凭证只生成 031 草稿，不能直接写账簿。
12. 税款缴纳台账只登记并追溯已有已记账凭证或合法 028 付款，不另造资金支付动作，不作为本期关闭前置。
13. 关闭后禁止本期间新增、编辑、转换、刷新、提交、撤回、取消、冲销创建或审批记账任何凭证；唯一恢复写入路径是已批准的反结账。
14. 关闭版本、已确认银行对账、已确认税务汇总和已记账损益结转不得删除或修改。反结账保留全部旧版本；再次关闭创建递增版本。
15. 正式数据库不为演示补造 032 事实。正向关闭、反结账、银行匹配、税额正例和跨期间状态在隔离正式副本中通过真实 API 建立和验证。

## 业务目标与范围

### 完成后用户必须能够

1. 在财务结账工作台查看每个会计期间当前状态、前置摘要、最新检查、关闭版本和允许动作。
2. 发起结账检查，逐项追溯 030、031、银行和税务来源，明确区分可结账、阻断、失败和已失效运行。
3. 对当期损益科目生成幂等正式凭证草稿，通过既有双人审批记账，并在来源变化或反结账后安全生成增量结转版本。
4. 维护银行账户，CSV 导入或手工录入流水，把银行流水与已记账银行科目分录进行多对多匹配，分类未达项并确认不可变对账版本。
5. 维护税务档案、有效期税率/征收率、票种和基础映射，计算并确认增值税、附加税费建议和企业所得税估算，追溯来源并按需生成正式凭证草稿。
6. 登记税款缴纳台账并追溯已记账凭证或合法付款，不重复执行资金支付。
7. 在全部门禁满足后原子关闭会计期间；关闭后所有该期间制证和记账路径失败关闭。
8. 提交反结账申请，通过 022 双人审批后原子恢复期间开放，且旧关闭、对账、税务和结转历史保持可读。
9. 在无金额、无来源、无银行敏感或无动作权限时看到明确受限状态，不能通过列表、详情、候选、审批、审计或错误文案反推受限信息。

### 纳入范围

- V34 财务关闭检查/运行/快照、反结账申请、损益结转、银行账户/流水/对账/未达项、税务档案/规则/汇总/调整/缴纳、幂等和审计模型。
- 031 会计期间受控关闭/开放、关闭期全路径写入保护、损益和税费正式凭证草稿来源类型。
- 022 `FINANCIAL_PERIOD_REOPEN` 固定审批场景及最终审批事务回调。
- 独立前端 `financialClose` 模块，产品路由位于 `/gl`；新增页面和 031/028/022 受影响入口。
- 权限、金额/来源/银行账号脱敏、审计、版本、来源指纹、幂等、并发和迁移。
- V1→V34、V33→V34、正式 V33 副本迁移，隔离真实 API 和真实桌面浏览器验收。

### 排除范围

- 电子税务局申报文件、在线申报、税控设备、发票开具/查验/用途确认同步和完整红字发票流程。
- 出口退税、消费税深度计算、个人所得税、工资社保、固定资产所得税差异全自动调整。
- 银企直连、自动下载银行流水、支付指令、网银复核、现金池和多币种银行账户。
- 资产负债表、利润表、现金流量表、合并报表、XBRL、电子会计档案和 033 经营财务分析。
- 年末本年利润转未分配利润、利润分配、股利、盈余公积和结转后自动生成正式财务报表。
- 修改或删除已记账凭证、账簿分录、已确认对账/税务版本或旧关闭快照。
- 自动关闭、定时关闭、批量关闭多个期间、正式库补造演示关闭事实和移动端验收。

## 财务期间、检查、关闭与反结账

### 当前状态与运行历史

- 会计期间当前状态：`OPEN -> CLOSED -> OPEN`。第二个 `OPEN` 只能由反结账审批最终通过产生。
- 检查运行：创建时 `CHECKING`；计算完成进入 `BLOCKED` 或 `READY`；技术失败进入 `FAILED`；来源变化使 `READY` 进入 `STALE`；成功关闭后进入 `CONSUMED`。关闭运行只在关闭事务成功时创建为 `CLOSED`，该关闭版本被反结账后进入 `REOPENED`。
- 同一账簿和期间只能有一个当前 `READY` 检查运行、一个当前 `CLOSED` 关闭运行；历史 `BLOCKED/FAILED/STALE/CONSUMED/REOPENED` 全部保留。
- 每次检查保存 `source_fingerprint`、检查项代码、严重度、实际值、期望值、中文结论、可追溯来源类型/标识和运行版本。无来源权限时 DTO 移除来源标识而不是仅前端隐藏。

### 强制检查项

| 代码 | 通过条件 | 级别 |
|---|---|---|
| `PREVIOUS_PERIOD_CLOSED` | 非启用首月时，紧邻上月会计期间为 `CLOSED` | 阻断 |
| `BUSINESS_PERIOD_CLOSED` | 同自然月 030 业务月结为 `CLOSED` | 阻断 |
| `NO_INCOMPLETE_VOUCHERS` | 当期不存在 `DRAFT/SUBMITTED` 凭证，也不存在有效未记账冲销/来源转换草稿 | 阻断 |
| `TRIAL_BALANCE_BALANCED` | 期初、发生额、期末三组借贷均严格相等 | 阻断 |
| `BANK_RECONCILIATIONS_CONFIRMED` | 每个当期启用银行账户均有当前已确认对账版本，且无未解释差额 | 阻断 |
| `TAX_SUMMARIES_CONFIRMED` | 按税务档案申报周期，当期适用税务汇总均已确认且未失效 | 阻断 |
| `TAX_VOUCHERS_POSTED` | 已选择生成的税费计提凭证全部 `POSTED` | 阻断 |
| `PROFIT_LOSS_TRANSFER_POSTED` | 所有损益余额为零，且最新结转运行对应凭证已 `POSTED`；原本无余额时按零额通过 | 阻断 |
| `NO_SOURCE_CHANGES` | 当前来源指纹与检查、对账、税务和结转版本一致 | 阻断 |
| `ATTACHMENT_OBJECTS_CONSISTENT` | 只读验证数据库 `AVAILABLE` 数与 MinIO 对象数一致且不少于 8 | 交付环境阻断，不作为业务页面实时检查 |

### 关闭动作

- 请求字段固定为 `checkRunId`、`reason`、`version`、`idempotencyKey`。
- 事务内锁定 `gl_accounting_period` 和当前检查运行，拒绝非 `OPEN`、旧版本、旧指纹、重复关闭或跨期间请求；重算全部业务检查后才更新期间为 `CLOSED`、检查运行为 `CONSUMED`、新建 `CLOSED` 关闭运行并保存关闭快照和审计。
- 关闭快照至少保存：试算三组金额、凭证数量、最后记账时间、损益结转凭证标识、银行对账版本清单、税务汇总版本清单、030 月结运行标识和整体来源指纹。
- 相同幂等键、相同请求指纹返回原结果；相同键不同请求返回 409。并发不同键只有一方成功，另一方返回稳定 409，不得产生两个当前关闭版本。

### 反结账审批

- 请求字段固定为 `closeRunId`、`reason`、`version`、`idempotencyKey`，提交后创建 `fin_close_reopen_request` 并进入 022 `FINANCIAL_PERIOD_REOPEN`。
- 请求状态固定为 `SUBMITTED/REJECTED/APPLIED/CANCELLED`；不持久化可停留的 `APPROVED`。最终审批在一个事务内锁定期间和关闭运行、复核其仍为当前 `CLOSED`，把期间恢复 `OPEN`、关闭运行置为 `REOPENED`、申请置为 `APPLIED` 并写审计。
- 申请人不得自审；同一关闭运行最多一个有效 `SUBMITTED` 申请；拒绝/撤回保留历史，允许使用新幂等键重新提交。
- 反结账不删除、不冲销或重写旧凭证、关闭快照、银行对账和税务汇总。后续新增调整事实会使旧对账/税务/检查版本只读保留，用户必须创建新版本后再次关闭。

## 期末损益结转

- V34 为 `MAIN` 账簿补充 `4103 本年利润`，类别 `EQUITY`、贷方余额、启用、可记账；补充税务会计所需 `2221.03 未交增值税`、`2221.04 应交城市维护建设税`、`2221.05 应交教育费附加`、`2221.06 应交企业所得税`、`6403 税金及附加`、`6801 所得税费用`。
- 结转读取当期已 `POSTED` 账簿中全部启用/历史有效 `PROFIT_LOSS` 末级科目净余额。每个非零损益科目生成一条方向相反、金额绝对值相同的分录；差额由一条 `4103 本年利润` 分录平衡。净损益为零但分项非零时仍结清各损益科目，不强制生成零额本年利润行。
- 结转凭证日期固定为期间末日、类型沿用 `GENERAL`、来源类型新增 `PROFIT_LOSS_CARRYFORWARD`，摘要固定包含 `YYYY-MM 期末损益结转`。前端不能编辑系统生成的科目、方向和金额，只能取消草稿后重新生成。
- 同一期间和来源指纹最多一个有效 `DRAFT/SUBMITTED/POSTED` 结转。重复相同请求返回原结果；来源变化时旧 `DRAFT` 标为不可提交并允许取消后新建；旧 `SUBMITTED` 必须先按 031 撤回；旧 `POSTED` 不改写，反结账后只对新增净余额生成新版本。
- 结转草稿提交和记账完全复用 `GL_VOUCHER_POST`，不得绕过双人审批或直接写 `gl_ledger_entry`。
- 税费计提凭证必须先于最终损益结转记账；企业所得税估算使用“所得税费用前”的会计利润，排除 `6801` 自身，避免循环计算。

## 银行账户、流水与对账

### 银行账户

- 类型固定为 `BASIC/GENERAL/SPECIAL/TEMPORARY/OTHER`，页面中文为基本户、一般户、专用户、临时户、其他。
- 必填：账户名称、账户类型、开户行、币种 `CNY`、绑定 GL 科目、启用日期、账号原始输入。后端规范化账号后只保存 SHA-256 指纹、末四位和脱敏显示，不保存可恢复完整账号；同一指纹不能重复。
- 绑定科目必须为 `1002` 或其后代、启用、末级、可记账且资产类借方余额。已产生流水或对账后不得换绑科目；只能停用并保留历史。

### 银行流水

- 流水方向固定为 `CREDIT`（银行入账）或 `DEBIT`（银行出账）；金额必须大于零。字段至少包括银行账户、交易日期、入账日期、方向、金额、对方名称、摘要、银行交易标识、参考号、来源方式和版本。
- CSV 模板固定列：`交易日期,入账日期,方向,金额,对方名称,摘要,银行交易标识,参考号`；导入先预览、逐行返回中文错误，确认后原子写入合法行。文件只在请求内解析，不保存到 MinIO。
- 去重指纹至少包含银行账户、银行交易标识；银行交易标识为空时使用账户、交易日期、方向、金额、对方名称和参考号。重复导入返回已有行，不创建重复事实。
- 流水状态固定为 `UNMATCHED/PARTIALLY_MATCHED/MATCHED/IGNORED`。`IGNORED` 只用于银行重复/无效技术行，必须填写原因并审计，不能用于掩盖真实未达项。

### 对账运行与匹配

- 对账运行状态固定为 `DRAFT/RECONCILING/BALANCED/CONFIRMED/REOPENED`。同一银行账户、期间只能有一个当前 `DRAFT/RECONCILING/BALANCED/CONFIRMED` 版本。
- 候选 GL 分录只来自绑定银行科目、当期 `POSTED` 账簿；银行 `CREDIT` 对应 GL 借方、银行 `DEBIT` 对应 GL 贷方。日期、摘要和对方仅参与排序建议，不替代金额与方向校验。
- 匹配支持多对多；`fin_bank_reconciliation_match` 每行保存匹配组、银行流水行、GL 分录、匹配金额。单边累计不得超过原金额，组内银行金额与 GL 金额必须精确相等，CNY 容差固定 `0.00`。
- 未匹配事实必须分类为：`BANK_ONLY_CREDIT`、`BANK_ONLY_DEBIT`、`BOOK_ONLY_DEBIT`、`BOOK_ONLY_CREDIT`，并填写原因；前两类调整账面，后两类调整银行余额。
- 调整后银行余额 = 银行期末余额 + `BOOK_ONLY_DEBIT` - `BOOK_ONLY_CREDIT`；调整后账面余额 = GL 期末余额 + `BANK_ONLY_CREDIT` - `BANK_ONLY_DEBIT`。两者必须精确相等且所有未匹配事实均已分类，运行才进入 `BALANCED` 并允许确认。
- 确认请求带原因、版本和幂等键；确认后匹配、未达项、期初/期末余额、来源指纹不可修改。反结账后若新增银行分录或流水，旧版本保留并新建对账版本。

## 税务基础、汇总与缴纳台账

### 税务档案与有效期配置

- 单公司仅一个当前有效税务档案；字段至少包括纳税人类型 `GENERAL/SMALL_SCALE`、统一社会信用代码、主管税务机关、增值税申报周期 `MONTHLY/QUARTERLY`、企业所得税基本税率、城建税税率和生效日期。
- 税率/征收率采用有效期版本，不硬编码到计算服务。V34 按 2026-01-01 现行增值税法预置 `13%/9%/6%/0%` 和简易征收率 `3%`；企业所得税默认 `25%`、城建税选项 `7%/5%/1%`，用户可按权限新建未来版本，已确认汇总引用的旧版本不可修改。
- 票种至少预置数电专票、数电普票、纸质专票、纸质普票；票种只用于来源分类和展示，不实现开票、查验、红字确认或用途确认。
- 统一社会信用代码、税额、来源单号分别受敏感、金额和来源权限控制；DTO 失败关闭，不得只依赖前端遮罩。

### 增值税汇总

- 来源只读取 028 当前有效、已确认销售发票的销项税额，以及已确认采购发票和费用的可追溯进项税额；不按含税金额在前端或汇总服务重新反算税额。
- 一般纳税人净额：`销项税额 + 进项转出 + 手工调整 - 可抵扣进项税额 - 期初留抵`；应纳税额为净额与零的较大者，期末留抵为净额绝对值与零的较大者。
- 小规模纳税人以合法来源税额和有原因调整汇总，不自行推断优惠政策；页面显示适用征收率和来源，不宣称正式应申报金额。
- 手工调整必须选择类型、填写至少 2 个非空中文字符原因、金额和有效期，完整审计；不能修改 028 来源事实。
- 城建税和附加税费只生成计提建议：以本汇总增值税应纳税额为基础，按有效期配置计算。消费税未纳入时必须在页面说明未包含消费税基础。

### 企业所得税估算

- 按自然季度或年度生成估算版本，数据源为 031 年初至期间末已记账损益事实；会计利润使用所得税费用前口径，排除 `6801 所得税费用`。
- 估算公式：`应纳税所得额 = 会计利润 + 纳税调增 - 纳税调减 - 可弥补亏损`；估算应纳税额为正应纳税所得额乘有效税率，再减已登记预缴税额，最低为零。
- 调增、调减、可弥补亏损和预缴均为有原因、可审计人工基础项；本阶段不自动判断固定资产税会差异、研发加计扣除、优惠资格或亏损可用性。

### 汇总状态、确认和凭证建议

- 税务汇总状态固定为 `DRAFT/CALCULATED/CONFIRMED`。计算保存来源指纹和各来源行；确认前必须重新计算一致，确认后不可修改。来源变化时，旧 `CONFIRMED` 版本仍保持原状态和内容，但通过实时指纹派生 `stale=true/current=false`，并为未关闭期间创建新版本；未确认 `CALCULATED` 版本可直接标记为失效历史。关闭检查只接受 `CONFIRMED && current && !stale`。
- 汇总可生成税费正式凭证草稿，来源类型新增 `TAX_SUMMARY`。凭证建议使用 V34 科目映射，仍需 031 `GL_VOUCHER_POST` 审批记账；用户可明确选择暂不生成，不得由计算动作暗中写账。
- 页面和 API 均必须返回固定免责声明：“本结果为 ERP 基础汇总或估算，不是正式纳税申报结果，不代替税务专业判断。”

### 税款缴纳台账

- 台账记录税种、所属期间、应缴金额、已缴金额、缴纳日期、关联 `POSTED` 凭证、可选合法 028 付款、银行账户脱敏标识、原因和版本。
- 台账只建立追溯关系，不创建付款、不修改 028/031 来源、不自动改变税务汇总。已引用记录不可删除，只能追加更正记录。

## V34 数据迁移设计

迁移文件固定为 `apps/api/src/main/resources/db/migration/V34__financial_close_funds_tax_foundation.sql`，前向追加且至少创建以下对象：

### 财务关闭

- `fin_close_run`
- `fin_close_check_run`
- `fin_close_check_item`
- `fin_close_snapshot`
- `fin_close_reopen_request`
- `fin_close_profit_loss_transfer`
- `fin_close_action_idempotency`
- `fin_close_audit_event`

### 银行对账

- `fin_bank_account`
- `fin_bank_statement`
- `fin_bank_statement_line`
- `fin_bank_reconciliation_run`
- `fin_bank_reconciliation_match`
- `fin_bank_reconciliation_exception`

### 税务基础

- `fin_tax_profile`
- `fin_tax_rate_rule`
- `fin_tax_invoice_type`
- `fin_tax_period_summary`
- `fin_tax_summary_line`
- `fin_tax_adjustment`
- `fin_tax_payment_record`

### 迁移约束

- 给 `gl_voucher.source_type` 前向增加 `PROFIT_LOSS_CARRYFORWARD`、`TAX_SUMMARY`，不放宽其他非法值；给 031 会计期间保留现有 `OPEN/CLOSED` 约束。
- 增加 `FINANCIAL_PERIOD_REOPEN` 审批定义和固定单步审批配置，增加 032 菜单/动作权限及 `SYSTEM_ADMIN` 权限种子。
- 补充 `4103`、`2221.03`—`2221.06`、`6403`、`6801` 科目；如果同编码已存在则迁移必须复核类别、方向、父级和可记账属性，一致时幂等接受，不一致时失败关闭，不能覆盖用户数据。
- 对当前记录使用局部唯一索引，对金额、状态、方向、版本、幂等指纹和多对多匹配累计使用数据库约束或事务锁保护。
- 已确认对账/税务、关闭快照和已应用反结账历史必须由数据库触发器或等价不可变约束阻止更新/删除。
- V1→V34、V33→V34 和正式 V33 副本前迁必须成功；实现冻结后把 V34 精确 checksum 固化进 `tools/demo-data/sql/validate-demo-data.sql` 和 `tools/demo-data/lib/demo-data-self-test.ps1`，保留 V29—V33 精确 checksum。

## 权限与审计

### 权限点

- `financial-close:period:view`
- `financial-close:period:check`
- `financial-close:period:close`
- `financial-close:period:reopen`
- `financial-close:profit-loss:view`
- `financial-close:profit-loss:generate`
- `financial-close:bank-account:view`
- `financial-close:bank-account:manage`
- `financial-close:bank-reconciliation:view`
- `financial-close:bank-reconciliation:import`
- `financial-close:bank-reconciliation:match`
- `financial-close:bank-reconciliation:confirm`
- `financial-close:bank-reconciliation:reopen`
- `financial-close:tax-profile:view`
- `financial-close:tax-profile:manage`
- `financial-close:tax-summary:view`
- `financial-close:tax-summary:calculate`
- `financial-close:tax-summary:confirm`
- `financial-close:tax-summary:generate-voucher`
- `financial-close:tax-payment:view`
- `financial-close:tax-payment:manage`
- `financial-close:amount:view`
- `financial-close:source:view`
- `financial-close:bank-sensitive:view`

列表、详情、候选、追溯、审批摘要、审计和错误信息都执行权限过滤。跨到 031 凭证/账簿或 028 来源时，还必须满足目标资源原有查看权限；缺任一权限时不返回受限标识、金额或来源单号。

### 审计动作

- 检查创建/完成/失败/失效，财务关闭成功/失败，反结账提交/拒绝/撤回/应用。
- 损益结转预览/生成/重复命中/来源变化。
- 银行账户创建/变更/停用，流水预览/导入/手工录入/忽略，匹配/取消匹配，对账确认/重开。
- 税务档案/税率/票种变更，汇总计算/确认/失效/生成凭证，调整项和缴纳台账创建/更正。
- 所有写动作记录操作人、目标、前后状态、原因、版本、幂等键、请求指纹、来源指纹、结果和稳定错误码；审计内容同样执行敏感字段脱敏。

## API 契约

所有列表默认 `page=1&pageSize=10`，只允许 `10/20/50/100`；日期使用 ISO `YYYY-MM-DD`，年月使用 `YYYY-MM`；金额为字符串。写请求统一携带 `version`、`idempotencyKey` 和规定动作的 `reason`。

### 财务关闭与损益结转

- `GET /api/admin/financial-closes/periods`
- `GET /api/admin/financial-closes/periods/{periodId}`
- `POST /api/admin/financial-closes/periods/{periodId}/checks`
- `GET /api/admin/financial-closes/check-runs/{runId}`
- `POST /api/admin/financial-closes/check-runs/{runId}/close`
- `POST /api/admin/financial-closes/close-runs/{runId}/reopen-requests`
- `GET /api/admin/financial-closes/reopen-requests/{requestId}`
- `POST /api/admin/financial-closes/periods/{periodId}/profit-loss-transfers/preview`
- `POST /api/admin/financial-closes/periods/{periodId}/profit-loss-transfers`
- `GET /api/admin/financial-closes/periods/{periodId}/profit-loss-transfers`

### 银行账户、流水与对账

- `GET/POST /api/admin/bank-accounts`
- `GET/PUT /api/admin/bank-accounts/{id}`
- `POST /api/admin/bank-accounts/{id}/disable`
- `GET/POST /api/admin/bank-statements`
- `POST /api/admin/bank-statements/import-preview`
- `POST /api/admin/bank-statements/import-confirm`
- `GET /api/admin/bank-statements/{id}`
- `POST /api/admin/bank-statement-lines/{id}/ignore`
- `GET/POST /api/admin/bank-reconciliations`
- `GET /api/admin/bank-reconciliations/{id}`
- `GET /api/admin/bank-reconciliations/{id}/candidates`
- `POST/DELETE /api/admin/bank-reconciliations/{id}/matches`
- `POST /api/admin/bank-reconciliations/{id}/exceptions`
- `POST /api/admin/bank-reconciliations/{id}/calculate`
- `POST /api/admin/bank-reconciliations/{id}/confirm`
- `POST /api/admin/bank-reconciliations/{id}/reopen`

### 税务基础

- `GET/PUT /api/admin/tax-profiles/current`
- `GET/POST /api/admin/tax-rate-rules`
- `GET/POST /api/admin/tax-invoice-types`
- `GET/POST /api/admin/tax-summaries`
- `GET /api/admin/tax-summaries/{id}`
- `POST /api/admin/tax-summaries/{id}/calculate`
- `POST /api/admin/tax-summaries/{id}/adjustments`
- `POST /api/admin/tax-summaries/{id}/confirm`
- `POST /api/admin/tax-summaries/{id}/voucher-drafts`
- `GET/POST /api/admin/tax-payments`
- `POST /api/admin/tax-payments/{id}/corrections`

### DTO 约束

所有操作型详情 DTO 至少返回：

```text
id, status, version, allowedActions[], actionDisabledReasons{},
amountVisible, sourceVisible, bankSensitiveVisible,
createdAt, updatedAt
```

关闭检查 DTO 额外返回 `sourceFingerprint/checkItems/closeVersion`；银行对账 DTO 返回字符串 `bankEndingBalance/glEndingBalance/adjustedBankBalance/adjustedBookBalance/difference`；税务汇总 DTO 返回字符串来源合计、调整、应纳、留抵、估算税额和固定免责声明。

稳定错误码至少包括：`FIN_CLOSE_NOT_READY`、`FIN_CLOSE_STALE`、`FIN_CLOSE_PERIOD_CLOSED`、`FIN_CLOSE_CONFLICT`、`FIN_REOPEN_APPROVAL_REQUIRED`、`FIN_PROFIT_LOSS_STALE`、`FIN_BANK_MATCH_AMOUNT_INVALID`、`FIN_BANK_RECON_NOT_BALANCED`、`FIN_TAX_SUMMARY_STALE`、`FIN_TAX_SOURCE_CHANGED`、`FIN_PERMISSION_DENIED`。

## 页面与 `page-standards` 映射

### 新增页面

| 路由 | 页面 | 强制验收状态 |
|---|---|---|
| `/gl/financial-close` | 财务结账工作台 | 期间摘要、未检查、检查中、阻断、可结账、已关闭、无权限、空/错/加载、关闭/反结账禁用原因 |
| `/gl/financial-close/:runId` | 结账检查/运行详情 | 分组检查宽表、来源抽屉、失效运行、关闭快照、反结账历史、`returnTo` |
| `/gl/profit-loss-carryforward` | 期末损益结转 | 无余额、预览、生成草稿、审批中、已记账、来源变化、重复幂等、查看凭证 |
| `/gl/bank-accounts` | 银行账户 | 新增、编辑、停用、账号脱敏、绑定科目、空/错/403/冲突 |
| `/gl/bank-statements` | 银行流水 | CSV 预览与逐行错误、手工录入、重复命中、未匹配/部分/已匹配/忽略 |
| `/gl/bank-reconciliation` | 银行对账工作台 | 左右或上下候选池、搜索、选中同步、多对多、未达分类、差额摘要、确认后只读 |
| `/gl/tax-settings` | 税务基础设置 | 档案、有效期税率/票种、权限态、历史版本、非申报边界文案 |
| `/gl/tax-summary` | 税额汇总 | 增值税、附加建议、所得税估算、来源追溯、调整、确认、失效、凭证草稿、零数据 |
| `/gl/tax-payments` | 税款缴纳台账 | 已记账凭证/付款追溯、账号脱敏、更正、金额权限、空/错/403 |

### 修改和受影响页面

- `/gl/accounting-periods`：增加财务当前状态、最新检查、关闭入口和禁用原因；不显示 `REOPENED` 为当前状态。
- `/gl/vouchers`、凭证创建/编辑/详情：关闭期间所有写动作禁用并展示后端原因；损益/税务来源明确可追溯。
- `/gl/ledgers/general`、`/gl/ledgers/detail`、`/gl/account-balances`、`/gl/trial-balance`：结转记账后金额和来源可查；关闭不改变账簿事实。
- 028 收款、付款、预收预付、发票、费用和凭证草稿入口：关闭期间来源转换失败关闭；只增加受影响提示，不改变 028 状态机。
- 022 审批中心：支持 `FINANCIAL_PERIOD_REOPEN` 摘要、金额/来源/敏感脱敏、安全 `returnTo` 和“通过并反结账”文案。

### 页面规范门禁

- 每页必须有标题、一句业务说明、右侧主操作；查询条件采用标签置顶网格，操作条不得滥用表单项布局。
- 宽表只能在表格容器内横向滚动，页面本身不得出现水平滚动；分页固定 10/20/50/100。
- 弹窗/抽屉内容可滚动，底部动作始终可见；所有高风险动作统一使用 `confirmAction`，跨模块回跳统一使用 `navigationReturn`。
- 候选池必须独立搜索、分页或完整加载，不得被主列表默认十条截断；选择态、差额和两侧同步必须在 1280×720 可识别。
- 加载、空、错误、403、只读、禁用、409 冲突、来源失效和权限脱敏均为显式状态，不得用空白页或仅控制台错误表达。
- 税务页面固定显示“基础汇总/估算，非正式申报”说明，不出现“报送成功、已申报、税控同步”等超范围文案。
- 真实桌面检验范围仅为本阶段新增、修改和受影响页面；不截图、不保存视觉目录、不测试移动端。

## 工作包、文件所有权与测试先行步骤

### 工作包 A：财务关闭、银行和税务后端闭环（后端开发）

**所有权：**

- 新建 `apps/api/src/main/resources/db/migration/V34__financial_close_funds_tax_foundation.sql`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseAdminController.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseCheckService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/ProfitLossTransferService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/BankReconciliationService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/TaxFoundationService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseQueryService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseAuditService.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseModels.java`
- 新建 `apps/api/src/main/java/com/qherp/api/system/financialclose/FinancialCloseSupport.java`
- 修改 `apps/api/src/main/java/com/qherp/api/system/gl/GeneralLedgerSetupService.java`
- 修改 `apps/api/src/main/java/com/qherp/api/system/gl/GeneralLedgerVoucherService.java`
- 修改 `apps/api/src/main/java/com/qherp/api/system/platform/PlatformApprovalService.java`
- 最小修改 `apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java`
- 最小修改 `apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java`
- 最小修改 `apps/api/src/main/java/com/qherp/api/system/init/AccountPermissionInitializer.java`
- 新建后端定向测试 `apps/api/src/test/java/com/qherp/api/system/financialclose/FinancialCloseControllerTests.java`
- 新建后端定向测试 `apps/api/src/test/java/com/qherp/api/system/financialclose/FinancialCloseServiceTests.java`
- 新建迁移测试 `apps/api/src/test/java/com/qherp/api/system/financialclose/FinancialCloseV34MigrationRegressionTests.java`

**产出接口：** 上述固定 API、DTO、状态和稳定错误码；供前端和独立验收使用。不得编辑前端文件、独立验收文件或交接文档。

- [ ] 先为 V34 空库/存量迁移、科目和权限种子、历史 checksum、不可变约束写失败测试并确认失败原因来自缺少 V34。
- [ ] 实现 V34 最小迁移，使迁移定向测试转绿；不得修改 V1—V33。
- [ ] 先写关闭检查、关闭原子性、并发、幂等、来源失效、关闭后 GL 全路径阻断和反结账审批失败测试，再逐项实现到转绿。
- [ ] 先写损益结转借贷、零额、重复、来源变化、反结账增量和 031 审批接线失败测试，再实现到转绿。
- [ ] 先写银行账户约束、流水去重、CSV 预览、多对多匹配、未达公式、零容差、确认不可变和重开失败测试，再实现到转绿。
- [ ] 先写税务档案有效期、来源汇总、增值税公式、所得税估算、调整、来源失效、凭证草稿和缴纳追溯失败测试，再实现到转绿。
- [ ] 先写权限失败关闭、DTO 脱敏、审计、跨域不回写和稳定错误码测试，再实现到转绿。
- [ ] 只运行本工作包定向后端测试和必要编译；记录命令、通过数和未执行全量说明，不进入正式数据库或 bucket。

### 工作包 B：会计核算下 032 页面族（前端开发）

**所有权：**

- 新建 `apps/web/src/shared/api/financialCloseApi.ts` 及 `financialCloseApi.spec.ts`
- 新建 `apps/web/src/router/modules/financialCloseRoutes.ts` 及 `financialCloseRoutes.spec.ts`
- 新建 `apps/web/src/navigation/financialCloseMenu.ts` 及 `financialCloseMenu.spec.ts`
- 新建 `apps/web/src/modules/financialClose/**`
- 最小修改 `apps/web/src/router/index.ts`、`apps/web/src/navigation/glMenu.ts`、`apps/web/src/navigation/appMenuRegistry.ts`
- 受影响修改 `apps/web/src/modules/gl/GlAccountingPeriodsView.vue`、`GlVoucherWorkbenchView.vue`、`GlVoucherFormView.vue`、`GlVoucherDetailView.vue` 及对应测试
- 必要时最小修改 028 来源页面和 `apps/web/src/modules/platform/approvals/ApprovalCenterView.vue`，并补受影响测试

**产出接口：** 只消费工作包 A 冻结的 API/DTO；金额不重算，动作只依据 `allowedActions/actionDisabledReasons`。不得编辑后端、迁移、独立验收或验证器文件。

- [ ] 先为 API 金额字符串、权限脱敏、409、路由、菜单和安全 `returnTo` 写失败测试，再实现独立模块接线。
- [ ] 先为财务结账工作台和检查详情的加载、空、阻断、可结账、关闭、反结账、失效、403/409 状态写失败组件测试，再实现页面。
- [ ] 先为损益结转预览、幂等生成、审批状态、来源变化和查看凭证写失败测试，再实现页面。
- [ ] 先为银行账户、流水导入预览、候选搜索、多对多匹配、未达分类、差额和确认只读写失败测试，再实现页面。
- [ ] 先为税务设置、汇总、估算、调整、确认、凭证和缴纳台账的免责声明、金额/来源权限、零数据状态写失败测试，再实现页面。
- [ ] 补齐 031 会计期间、凭证和 022 审批中心受影响状态，保证 030/032 中文命名和权限不混用。
- [ ] 只运行 `financialClose`、受影响 `gl/finance/approvals`、路由/菜单/API 定向测试及一次必要类型检查；不运行全量构建或浏览器验收。

### 工作包 C：独立阶段验收与验证器升级（测试）

**所有权：**

- 新建 `apps/api/src/test/java/com/qherp/api/system/financialclose/FinancialCloseStage032AcceptanceTests.java`
- 新建不与后端重叠的隔离数据准备/真实 API 验收脚本或夹具文件
- 修改 `tools/demo-data/sql/validate-demo-data.sql`
- 修改 `tools/demo-data/lib/demo-data-self-test.ps1`
- 必要时修改 `tools/demo-data/validate-demo-data.ps1` 和 `tools/demo-data/README.md`

**职责边界：** 独立测试不替代开发角色 TDD；不得修改业务实现、前端实现、V34 迁移或后端开发测试文件。V34 checksum 在迁移内容冻结后固化。

- [ ] 先按验收矩阵编写独立阶段测试，确保未实现时失败原因对应缺失 032 能力，而不是测试环境错误。
- [ ] 覆盖状态、并发、双人审批、关闭后写入阻断、损益借贷、银行多对多/未达/差额、税务公式/来源、权限脱敏、审计和跨域不回写。
- [ ] 覆盖 V1→V34、V33→V34 和正式 V33 副本前迁；固定 V29—V34 checksum、失败迁移 0 和历史事实保持。
- [ ] 在验证器与自测中新增 032 表、权限、审批定义、科目、约束、不可变、动态业务事实和对象一致性门禁；不得把正式当前 18/18 写成固定业务数量。
- [ ] 准备 `qherp_032_review`、`qherp-032-review` 隔离策略与跨期间真实 API 数据方案，不接触正式 `qherp/qherp-private`。
- [ ] 开发期只运行独立 032 定向测试和验证器自测；真实桌面操作留到唯一交付前全量窗口。

## 开发期定向验证

- 后端：`FinancialCloseControllerTests`、`FinancialCloseServiceTests`、`FinancialCloseV34MigrationRegressionTests` 和受影响的 `GeneralLedgerControllerTests`、`PlatformApprovalService` 相关定向测试。
- 前端：`financialCloseApi`、`financialCloseRoutes`、`financialCloseMenu`、032 页面、受影响 `gl`/028/审批测试和必要 `vue-tsc`。
- 测试：`FinancialCloseStage032AcceptanceTests`、V34 双路径迁移、验证器自测和隔离数据准备脚本自检。
- 开发期不得反复运行全量后端、全量前端、生产构建、正式数据库迁移或真实桌面验收；工作包结果必须说明未执行项将在唯一交付前全量窗口完成。

## 阶段验收矩阵

| 编号 | 验收项 | 完成条件 |
|---|---|---|
| A01 | V34 迁移 | V1→V34、V33→V34、正式 V33 副本前迁成功；V29—V33 checksum 不变；V34 精确 checksum 固化；失败迁移 0。 |
| A02 | 领域边界 | 032 独立于 030/028；只读消费 028/030/031，除受控会计期间状态与新 031 草稿外不回写上游。 |
| A03 | 检查状态 | 检查可稳定进入 `BLOCKED/READY/FAILED/STALE/CONSUMED`，关闭运行进入 `CLOSED/REOPENED`，检查项、来源指纹和中文原因完整。 |
| A04 | 关闭前置 | 上期、030、未完成凭证、三组试算、银行、税务、税费凭证、损益和来源任一不满足均阻断关闭。 |
| A05 | 原子关闭 | 关闭事务重算门禁、保存快照并把期间置 `CLOSED`；失败无半关闭，并发只有一方成功。 |
| A06 | 关闭期保护 | 关闭后所有创建、编辑、转换、提交、撤回、取消、冲销和审批记账路径均失败关闭。 |
| A07 | 反结账 | 独立权限、原因、双人审批、申请人不得自审；最终审批原子恢复 `OPEN`，旧版本和审计完整。 |
| A08 | 损益结转 | 全部非零损益科目方向和金额正确，`4103` 平衡，零额、净零、重复、来源变化和反结账增量正确。 |
| A09 | 正式凭证 | 损益和税务只创建 031 草稿，继续走 `GL_VOUCHER_POST`，不直接写账簿。 |
| A10 | 银行账户 | 类型、账号不可恢复保存、唯一指纹、1002 科目绑定、停用和历史不可变正确。 |
| A11 | 银行流水 | CSV 预览/确认、手工录入、逐行错误、重复幂等、方向/金额和忽略原因正确，文件不进入 MinIO。 |
| A12 | 银行匹配 | 多对多、方向、累计上限、零容差、取消匹配和权限正确，候选不受十条主分页限制。 |
| A13 | 银行未达 | 四类未达公式正确；未解释事实或非零差额不能确认，确认版本不可修改。 |
| A14 | 税务档案 | 单公司档案、有效期税率/征收率、票种、权限和历史版本正确。 |
| A15 | 增值税 | 028 来源税额、进项转出、留抵、调整、一般/小规模边界和来源追溯正确，不重算来源税额。 |
| A16 | 附加与所得税 | 附加税费明确为建议；所得税估算排除 6801 自循环，调增/调减/亏损/预缴和税率正确。 |
| A17 | 税务确认 | 来源指纹、确认不可变、来源变化失效、凭证草稿和固定非申报免责声明正确。 |
| A18 | 税款台账 | 只追溯已记账凭证/合法付款，可更正、不可删除，不重复创建资金动作且不阻断关闭。 |
| A19 | 权限脱敏 | 金额、来源、银行账号、税务敏感和动作权限失败关闭；列表、详情、候选、审批、审计、错误均无侧漏。 |
| A20 | 审计幂等 | 所有高风险写动作前后状态、原因、版本、幂等和来源指纹完整；相同键同请求命中、异请求冲突。 |
| A21 | 页面规范 | 九个新增页面及受影响页面覆盖真实桌面规定状态，无页面级横向溢出、重叠或关键动作不可见。 |
| A22 | 历史兼容 | 028/029/030/031 已交付状态、数据、审批、账簿、权限和历史迁移测试保持；正式数据不被补造。 |
| A23 | 对象一致 | 数据库 `AVAILABLE` 文件数等于 MinIO 对象数且不少于 8；当前 18/18 只作基线，不写死。 |
| A24 | 环境交付 | 全量后端/前端、类型、构建、迁移、真实 API、桌面、环境健康和空白检查通过；main/远端哈希一致、正式 V34 服务可访问。 |

## 阻断、严重和一般问题

### 阻断

- V34 迁移失败、修改历史迁移/checksum、污染正式数据库或对象存储、对象不一致。
- 任何强制检查未满足仍可关闭、关闭产生半状态、并发产生两个当前关闭版本。
- 关闭期间仍可通过任一入口制证、改变凭证或记账，或反结账绕过权限、原因、双人审批。
- 损益结转金额/方向/期间/科目错误，重复有效结转，或绕过 031 审批直接写账。
- 银行匹配超额/错向/重复仍能确认，未解释差额被当作平衡，已确认版本可修改。
- 税额来源、留抵、应纳、附加建议或所得税估算计算错误，或页面/API 宣称正式申报、税控结果。
- 032 回写 028/029/030、修改已 `POSTED` 凭证/账簿、泄露金额/来源/账号/税务敏感信息。
- 核心 API、路由或页面不可访问；真实桌面关键操作不可见、宽表撑破页面、控件重叠或页面空白。

### 严重

- 审计缺少原因、前后状态、版本、幂等或来源指纹，虽不直接造成错误状态但降低追责能力。
- 状态或中文文案混淆 030 业务月结与 032 财务结账，或税务估算与正式申报。
- 跨期间、来源变化、反结账后新版本、CSV 部分错误或候选同步存在不稳定行为。
- `page-standards` 强制项影响核心扫描效率或权限/业务状态识别，但仍有可用替代路径。

### 一般

- 不影响数据、权限、状态和关键操作的文案、提示、轻微布局或非关键易用性问题。一般问题默认登记后续清单，不重复阻断阶段交付。

## 唯一集中审查矩阵

| 角色 | 审查范围 | 不得自审 |
|---|---|---|
| 产品经理 | A02—A09、A14—A18，范围、状态、顺序、非申报边界和验收矩阵 | 不审实现代码质量 |
| UI 设计师 | 九个新增页面和受影响真实桌面页面，逐项核对 `page-standards` | 不以静态说明替代真实页面 |
| 前端开发 | 后端 API/DTO、权限、错误、跨域只读和不可变实现质量 | 不审本人前端实现 |
| 后端开发 | 前端 API 消费、金额处理、动作、路由、权限、页面状态和测试质量 | 不审本人后端实现 |
| 测试 | 自动化覆盖、异常/并发/迁移/权限/回归风险和唯一全量窗口可执行性 | 不修改业务实现 |

主代理汇总去重后只派发一轮集中整改；阻断和严重必须修复，一般默认登记。差异复审只覆盖整改差异和受影响路径。

## 唯一交付前全量验证窗口

全部工作包完成、集中审查和差异复审通过后，按顺序一次性执行并记录全部可执行项目；发现缺陷时先继续执行剩余项目，最后统一整改：

1. 全量后端测试及通过/失败/错误/跳过统计。
2. 全量前端测试及通过/失败统计。
3. 前端类型检查和生产构建。
4. V1→V34、V33→V34、正式 V33 副本前迁，V29—V34 checksum 和失败迁移检查。
5. 验证器自测、隔离完整验证器、032 新门禁和数据库/MinIO 动态对象一致性。
6. 隔离真实 API：跨两个月、双人审批、关闭/反结账、损益、银行多对多/未达、税务正负/零额、权限和并发。
7. 真实桌面浏览器：九个新增页面、031 会计期间/凭证/账簿、028 来源入口、022 审批中心；1280×720，不截图、不测移动端。
8. Web/API/PostgreSQL/MinIO 健康、端口来源、控制台/资源错误、页面空白、残留进程和正式环境只读边界。
9. `git diff --check`、工作树范围、分支/提交/远端哈希、无临时文件和无正式事实污染。

## 数据准备与资源隔离

- 从正式 V33 只读备份恢复隔离数据库 `qherp_032_review`，对象存储使用独立 bucket `qherp-032-review`；正式 `qherp/qherp-private` 不得被测试或数据准备写入。
- 先验证副本自然前迁 V34，再通过真实 API 建立两个月会计期间、030 关闭前置、收入/成本/费用/税费凭证、两个审批用户、无金额/无来源/无敏感权限用户、两类银行账户、银行流水、多对多与四类未达、增值税/所得税/零额税务场景。
- 测试夹具必须标识为隔离验收数据，不把高 ID SQL 直插替代真实 API 状态机；只有迁移、约束或无法经公共 API 构造的技术前置可由测试代码受控插入并说明。
- 真实桌面浏览器只访问隔离 Web/API；正式服务保持可访问但不用于造数。所有临时数据库、bucket 和受管进程在交付后按规则清理或明确保留用途。

## 变更记录

| 日期 | 变更 | 原因与影响 | 决策 |
|---|---|---|---|
| 2026-07-20 | 创建并冻结唯一 032 阶段说明 | 五角色唯一目标讨论完成；需要在实现前统一范围、状态、接口、迁移、页面和验收口径。 | 采用独立 `financialclose` + V34、产品导航归 `/gl`，进入隔离分支整包开发。 |
| 2026-07-20 | 期间当前态只保留 `OPEN/CLOSED` | 产品/测试曾建议把 `REOPENED` 作为状态，UI 建议当前态保持两态；长期 `REOPENED` 会混淆是否可写。 | `REOPENED` 仅作关闭运行历史，反结账后期间恢复 `OPEN`。 |
| 2026-07-20 | 关闭不审批、反结账固定审批 | 关闭已有完整前置和事务复检；重复审批增加流程而不增加事实正确性。反结账会重新开放历史期间，风险更高。 | 关闭强权限+原因+确认+审计；反结账走 022 双人审批。 |
| 2026-07-20 | 不纳入年末本年利润转未分配利润 | 产品讨论提出可选能力，但会引入利润分配和年度政策边界，超出“期末损益结转”最小闭环。 | 032 只做月末损益转 `4103`，年末利润分配留后续。 |
| 2026-07-20 | 补齐后端公共接线文件所有权 | 后端 TDD 前核验发现固定管理 API、稳定错误码和长期权限初始化无法只在 `financialclose` 包内完成；原工作包清单与冻结接口存在实施矛盾。 | 工作包 A 获得对 `PermissionAuthorizationManager`、`ApiErrorCode`、`AccountPermissionInitializer` 的最小修改权限；不改变 API、权限或业务范围。 |
| 2026-07-20 | 补齐冻结银行账户与流水 API | 集中审查环境准备发现账户更新/停用、CSV 预览/确认、流水详情/忽略 6 个冻结端点尚未公开，且无账户筛选的流水列表因 PostgreSQL 空参数类型推断返回 500。 | 审查开始前按原 API 契约补齐端点、权限、审计、幂等和状态保护并修复列表查询；V34 权限路径随之更新，最终 checksum 以集中整改冻结值为准。 |
| 2026-07-20 | 修正完整验证器 V34 审计列契约并重建隔离代表状态 | 完整验证器仍引用已废弃的 `fin_close_audit_event.target_type`；修正后又识别出仓库外环境准备脚本直接 SQL 构造的 030 `CLOSED/LOCKED` 状态缺少主快照与 `CLOSE/SUCCESS` 审计。 | 测试角色以红测锁定 `resource_type` 契约并最小修复验证器；只在隔离资源中撤销错误代表状态，改由库存、030、032 与审批公共 API 重建真实链路。完整验证器 `157/157` 通过，正式 V33 资源未写入或停止。 |
| 2026-07-21 | 按集中审查结论完成一次集中整改并重新冻结 V34 | 五角色集中审查识别出结账九项门禁、系统来源凭证唯一与新鲜度、敏感字段脱敏、银行方向化未达、页面动作闭环及对应契约和测试缺口；整改同时调整了 V34 权限、科目、约束和测试数据门禁。 | 后端、前端、测试原固定角色按一次性去重清单完成整改；V34 checksum 重新冻结为 `1689626005`。税务汇总失效沿用来源指纹派生 `stale/current` 与新版本机制，不新增独立失效端点。差异复审只覆盖本轮整改差异和受影响路径。 |

## 审查、整改、验证与交付记录

### 整包开发与定向验证

- 开发分支：`codex/032-financial-close-funds-tax-foundation`；阶段说明冻结提交：`5fc6cb04ce3f508a21cb1a182c63ce42ce3d682f`。
- 前端工作包：9 个 032 页面及路由、菜单、API 和受影响 031/028/022 页面接线已完成；032 定向组件测试 66 项、路由守卫测试 76 项和前端类型检查通过。
- 后端工作包：V34、独立 `financialclose` 领域、关闭/反结账、损益结转、银行对账、税务基础、权限脱敏、审计和跨域受控接线已完成；初始受影响后端定向套件 10 项、银行冻结 API 与对账组合回归 8 项及 V34 双路径迁移回归 2 项通过。
- 测试工作包：集中整改前 `FinancialCloseStage032AcceptanceTests` 5 项和完整验证器 `157/157` 通过；集中整改后独立验收扩展为 10 项并全部通过，完整验证器扩展为 `168/168` 通过。演示数据验证器自测和 032 隔离策略默认路径通过，正式 `qherp/qherp-private` 资源拒绝路径按预期失败关闭；V34 checksum 最终精确冻结为 `1689626005`。隔离 API `18083`、Web `5175`、数据库 `qherp_032_review` 与对象桶 `qherp-032-review` 保持可用；正式 API `18080`、Web `5173`、V33 数据库和对象桶状态未变化。
- 本节只证明开发期工作包和定向验证完成；尚未替代集中审查、差异复审或唯一交付前全量验证。

### 唯一一轮五角色集中审查

- 审查基线：`22b5825426656f42c2719971a45a9b7e418ba487`；产品、UI、前端、后端、测试五个固定角色于 2026-07-21 完成唯一一轮集中审查，原始报告位于 `.superpowers/sdd/032-central-review-*.md`。
- 审查时运行证据：隔离 API `18083`、Web `5175`、当时 V34 checksum 和完整验证器 `157/157` 均已核验；9 个新增页面和主要受影响桌面页面可登录渲染，正式 `18080/5173` 与 V33 资源未变化。这些证据只证明环境可审查，不抵消下列业务、权限、契约和页面缺陷；整改后的最终 checksum 为 `1689626005`。
- 主代理合并去重后的阻断簇：
  1. 财务关闭仅实现 4 项检查，缺少上期关闭、未完成凭证、试算平衡、税费凭证记账和来源变化等冻结门禁；关闭事务与验证器未覆盖 9 项完整集合。
  2. 系统来源凭证缺少同源有效版本唯一与提交/记账前来源复检；不同幂等键可能重复生成损益或税务草稿。
  3. 无银行敏感权限仍返回账号末四位，税务档案统一社会信用代码未按敏感权限脱敏。
  4. 银行未达项不是冻结四类方向化枚举；前端多个冻结写动作未接线，银行对账候选又会自动取第一条，阶段桌面闭环不可执行。
- 必须在一次集中整改中闭合的严重问题：银行账户 `1002` 子树/末级/方向校验与历史换绑保护；关闭、反结账和银行动作幂等及原因校验；税率、票种、税务科目、附加税费和所得税估算契约；后端 `allowedActions/actionDisabledReasons`、稳定错误码、菜单路由与审批对象追溯；税务和会计期间 DTO 映射；前端权限/状态失败关闭、检查详情入口、中文枚举、金额字段、分页和异常态；对应后端、前端、验收测试与验证器覆盖。
- 产品报告中的税款缴纳银行脱敏标识、UI 报告中的分页废弃用法和原始枚举会直接影响 A18/A19 或页面规范，合并进当前整改；不另开一般问题审核循环。
- 审查结论：当前不得进入全量验证或交付；按下述集中整改范围由原后端、前端、测试角色并行处理，随后只做修复差异和受影响路径复审。

### 集中整改与差异复审

- 一次集中整改已由原固定角色完成：后端闭合九项结账检查与事务复检、系统来源凭证唯一和来源新鲜度、银行账户和四类方向化未达、税务公式/种子/脱敏、幂等/原因/稳定错误码/动作能力及跨域追溯；前端补齐银行账户、流水、对账、税务设置、汇总、缴纳等冻结写动作、权限与状态失败关闭、路由菜单、审批追溯、DTO 映射和受影响页面规范；测试补齐独立验收、V34 回归和完整验证器门禁。
- 后端整改定向证据：`FinancialCloseBankTaxCompletionTests` 4/4，通过控制器、服务、银行税务、V34、总账、权限和初始化组合回归 73/73，生产代码编译通过。
- 前端整改定向证据：7 个受影响测试文件共 49 项通过，`vue-tsc` 类型检查通过。
- 测试整改独立证据：`FinancialCloseStage032AcceptanceTests` 10/10、`demo-data-self-test.ps1` 和完整验证器 `168/168` 通过；V34 checksum `1689626005`，失败迁移 0；隔离数据库与对象桶对象一致性 18/18。
- 复审环境保持运行：隔离 API `http://127.0.0.1:18083` 健康、Web `http://127.0.0.1:5175` 返回 200；正式 API `18080`、Web `5173`、V33 checksum `612501943` 和正式对象 18/18 未变化。
- 当前门禁：集中整改完成并已具备差异复审条件；五角色差异复审只检查整改差异和受影响路径，尚未替代唯一交付前全量验证。

### 唯一交付前全量验证窗口

- 待集中整改和差异复审通过后填写。窗口内先完成全部可执行项目，再统一处理缺陷。

### 正式交付

- 待全量验证最终通过后填写分支提交、main 合入、推送、正式迁移、服务 PID/端口、远端哈希、验收地址和交接更新。
