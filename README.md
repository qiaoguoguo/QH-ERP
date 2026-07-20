# QH ERP

QH ERP 是一套面向制造业单公司使用的 Web ERP 系统。系统不面向零售、门店收银或通用进销存，长期目标是围绕生产管理形成可追溯、可验收、可持续扩展的企业内部管理系统。

## 当前阶段

当前正式主线已经交付从 `000` 到 `032` 的业务阶段，并完成 025A、027A 两轮页面与现存问题治理。`024` 至 `027` 已形成项目采购、销售履约、订单驱动供给和项目生产/外协可信来源；`028 发票、费用与往来深化` 已交付业务结算来源；`029 项目成本核算` 已交付项目成本子账；`030 业务月结与期间快照` 已交付月末检查、原子关闭/重开、不可变多版本快照和八类固定经营报表基线；`031 会计科目、凭证与总账基础` 已正式交付单公司、单账簿、人民币的会计期间、科目、辅助核算、凭证、审批记账、总账、明细账、科目余额和试算平衡基础；`032 财务结账与资金税务基础` 已正式交付财务期间关闭与反结账、期末损益结转、银行对账和税务基础台账。

`032 财务结账与资金税务基础` 已纯快进合入并推送 `main`，本地 `main`、`origin/main` 和实际远端均为 `c823861fd467b78119fb964a2f1d41314129186f`；正式 API `18080`、Web `5173` 均已核验可访问。后续 `033` 至 `036` 继续按“项目型制造的业财成本闭环优先”推进；下一业务阶段为 `033 正式经营财务分析`，尚未启动，不得提前表达为已实现能力。

正式验收环境沿用受控电气制造演示数据，不因 024 至 032 前向迁移重建既有事实。正式库已由 Flyway V33 自然前迁至 V34；V29/V30/V31/V32/V33/V34 checksum 分别为 `774334682`、`2130342893`、`-2074547591`、`249406902`、`612501943`、`-629066235`，失败迁移 0，完整验证器 170/170。数据库 `AVAILABLE` 文件与 `qherp-private` 对象本次正式核验为 18/18；对象数量不是长期固定契约，后续合法业务文件可增长，但必须保持两侧动态一致且不少于 8。030 原 `OPEN/BLOCKED`、3 阻断/2 警告且无关闭/重开/快照事实保持不变；031 与 032 正式业务事实均为 0，未为验收补造结账、凭证、银行或税务事实。032 部署前备份目录为 `C:\Users\14567\.codex\backups\qherp\032-main-before-v34-20260721-043029`。运行入口、测试隔离和验收细节以当前交接为准。

后续阶段推进必须基于最新交接文档、当前仓库状态和用户最新指令，复用产品经理、UI 设计师、前端开发、后端开发、测试五个 Codex 后台固定角色会话。主代理只负责总体规划、范围控制、并行协调、审查汇总和交付判断，不因执行耗时、故障或返工压力直接接手业务实现。后台会话、计划—开发—审核—验收和接管门禁见 `docs/ops/stage-collaboration-delivery-process.md`。

## 项目重点

- 长期覆盖基础资料、采购、销售、库存、生产、成本、财务和报表等常规制造业 ERP 能力。
- 第一版以销售项目为一级成本和利润对象；项目合同、库存计价、项目专采、销售履约、订单驱动供给、项目生产/外协执行、业务发票费用往来及项目成本子账已在 020、023 至 029 完成，030 已补齐业务月结和期间快照，031 已正式补齐会计科目、凭证与总账基础，032 已正式补齐财务结账与资金税务基础。
- 生产保持工单、BOM 快照、领退补料、简单报工、完工入库、外协和序列追溯；精细工序、产能排程和复杂质量后置。
- 阶段性成果必须可本地部署，并能通过浏览器实际验收。
- 页面、表格、表单、弹窗、抽屉、追溯和报表必须遵守 `docs/ui/page-standards.md`；该文件是设计、实现、审查和验收的强制基线，不得以页面可访问、功能冒烟通过或没有明显溢出替代逐项合规核对。设计和开发仍需考虑响应式适配，但后续测试与验收只对本阶段相关页面改动进行桌面端浏览器视觉检验，不要求保存截图，也不测试或验收移动端、窄屏及响应式兼容性。
- 027 的业务功能、接口、迁移和数据交付已经完成；用户于 2026-07-18 指出的页面规范问题已由独立 027A 完成逐页清点、整改、集中审查、真实桌面复验和全量验证，页面规范治理现已闭合。此前桌面冒烟仍只作为历史业务路径证据，不倒推为当时已完成规范验收。
- 028 的业务实现、V30、集中审查整改、唯一全量窗口和窗口后差异复验已经完成，阻断/严重为 0；业务发票、费用和凭证草稿不得表达为正式收入、正式项目成本、正式会计凭证或总账。
- 029 的业务实现、V31、集中审查整改、唯一全量窗口、差异复验、正式迁移和真实桌面验收已经完成；项目成本子账及经营口径毛利不得表达为正式收入确认、会计凭证、总账或月结结果。
- 030 的业务实现、V32、集中审查整改、唯一全量窗口、正式备份/迁移、127/127 独立验证和真实桌面验收已经完成；业务月结与期间快照不等同正式会计期间、会计凭证、总账或财务关账。
- 031 的 V33、集中审查整改、差异复审、唯一全量窗口、整改复验、正式完整验证 142/142、数据库/MinIO 对象 18/18 和 14 个桌面入口正式核验已经完成；031 提供单公司单账簿人民币的会计科目、凭证、审批记账、总账/明细账/余额/试算基础，不等同 032 财务结账与资金税务基础。
- 032 的 V34、集中审查、一次集中整改、差异复审、最终差异复核、唯一交付前全量验证、纯快进合入、正式 V34 自然前迁、完整验证器 170/170 和 12 个桌面入口核验已经完成；032 提供财务期间检查/关闭/反结账、期末损益结转、银行账户/流水/对账和税务基础汇总/缴纳台账，不提供完整财务报表、033 经营财务分析、电子税务局申报、税控或红字发票平台。
- 主代理负责总体规划、协调、审查汇总和交付判断；业务实现、设计、测试和审核必须由固定角色子代理分工完成。

## 文档入口

- [仓库规范](AGENTS.md)
- [文档索引](docs/README.md)
- [当前项目交接与后续规划](docs/handoffs/2026-07-12-project-handoff-current.md)
- [固定五角色阶段协作与交付流程](docs/ops/stage-collaboration-delivery-process.md)
- [电气制造业全量演示数据基线与重建验收单](docs/data/electrical-manufacturing-demo-data.md)
- [产品决策记录](docs/product/product-decisions.md)
- [核心业务流程](docs/product/business-flow.md)
- [页面治理规范](docs/ui/page-standards.md)
- [系统操作手册](docs/manual/system-operation-manual.md)
- [库存可用量、占用、冻结与预留任务记录](docs/tasks/018-inventory-availability-reservation-foundation.md)
- [库存可用量与预留接口契约](docs/api/inventory-availability-reservation-api.md)
- [库存可用量与预留测试计划](docs/testing/inventory-availability-reservation-test-plan.md)
- [库存可用量与预留实施计划](docs/superpowers/plans/2026-07-11-inventory-availability-reservation-implementation-plan.md)
- [批次、序列号与来源去向追溯任务记录](docs/tasks/019-batch-serial-trace-foundation.md)
- [批次、序列号与来源去向追溯设计规格](docs/superpowers/specs/2026-07-11-batch-serial-trace-design.md)
- [批次、序列号与来源去向追溯接口契约](docs/api/batch-serial-trace-api.md)
- [批次、序列号与来源去向追溯测试计划](docs/testing/batch-serial-trace-test-plan.md)
- [批次、序列号与来源去向追溯实施计划](docs/superpowers/plans/2026-07-11-batch-serial-trace-implementation-plan.md)
- [销售项目与合同管理基础任务记录](docs/tasks/020-sales-project-contract-foundation.md)
- [销售项目与合同管理基础设计规格](docs/superpowers/specs/2026-07-12-sales-project-contract-design.md)
- [销售项目与合同管理基础接口契约](docs/api/sales-project-contract-api.md)
- [销售项目与合同管理基础测试计划](docs/testing/sales-project-contract-test-plan.md)
- [销售项目与合同管理基础实施计划](docs/superpowers/plans/2026-07-12-sales-project-contract-implementation-plan.md)
- [销售项目与合同管理基础视觉验收记录](docs/testing/020-sales-project-contract-visual-audit/notes.md)
- [成本财务主数据与 BOM 治理任务记录](docs/tasks/021-cost-financial-master-data-bom-governance.md)
- [固定审批与业务单据平台基础任务记录](docs/tasks/022-fixed-approval-document-platform.md)
- [库存计价与仓储账实一致任务记录](docs/tasks/023-inventory-valuation-stock-integrity.md)
- [采购深化与项目专采任务记录](docs/tasks/024-procurement-project-sourcing.md)
- [销售深化与项目履约任务记录](docs/tasks/025-sales-project-fulfillment.md)
- [订单驱动缺料与供给建议任务记录](docs/tasks/026-order-driven-shortage-supply-advice.md)
- [项目生产执行与外协基础任务记录](docs/tasks/027-project-production-outsourcing-foundation.md)
- [027A 现存问题闭环与页面规范治理任务记录](docs/tasks/027a-existing-issues-page-governance-remediation.md)
- [028 发票、费用与往来深化任务记录](docs/tasks/028-invoice-expense-settlement-deepening.md)
- [029 项目成本核算任务记录](docs/tasks/029-project-cost-accounting.md)
- [030 业务月结与期间快照任务记录](docs/tasks/030-business-period-snapshot.md)
- [031 会计科目、凭证与总账基础任务记录](docs/tasks/031-accounting-voucher-general-ledger-foundation.md)
- [032 财务结账与资金税务基础任务记录](docs/tasks/032-financial-close-funds-tax-foundation.md)
- [本地开发与验证说明](docs/ops/local-development.md)
- [任务文档模板](docs/tasks/task-template.md)

## 分支与验收

日常开发在独立任务分支或功能分支完成。主分支用于阶段性成果验收，必须保持相对稳定、可部署、可浏览器访问。模块完成并通过验证后，才能合入或推送到主分支并通知用户验收。
