# QH ERP

QH ERP 是一套面向制造业单公司使用的 Web ERP 系统。系统不面向零售、门店收银或通用进销存，长期目标是围绕生产管理形成可追溯、可验收、可持续扩展的企业内部管理系统。

## 当前阶段

当前稳定交付已经完成从 `000` 到 `026` 的阶段，并完成页面基础体验治理。`024 采购深化与项目专采` 已交付项目/公共采购来源，`025 销售深化与项目履约` 已交付可信有效销售需求，`026 订单驱动缺料与供给建议` 已交付混合快照、确定性供给分配、四类建议、来源追溯、权限脱敏和采购建议幂等转请购草稿。

后续 `027` 至 `036` 继续按“项目型制造的业财成本闭环优先”推进。下一阶段为 `027 项目生产执行与外协基础`，尚未形成权威阶段说明；路线规划不代表功能已经实现。

正式验收环境沿用受控电气制造演示数据，不因 024 至 026 前向迁移重建既有事实；当前正式库为 Flyway V28，独立验证 114/114，MinIO 与数据库可用文件对象为 17/17。运行入口、测试隔离和验收细节以当前交接为准。

后续阶段推进必须基于最新交接文档、当前仓库状态和用户最新指令，复用产品经理、UI 设计师、前端开发、后端开发、测试五个 Codex 后台固定角色会话。主代理只负责总体规划、范围控制、并行协调、审查汇总和交付判断，不因执行耗时、故障或返工压力直接接手业务实现。后台会话、计划—开发—审核—验收和接管门禁见 `docs/ops/stage-collaboration-delivery-process.md`。

## 项目重点

- 长期覆盖基础资料、采购、销售、库存、生产、成本、财务和报表等常规制造业 ERP 能力。
- 第一版以销售项目为一级成本和利润对象；项目合同、库存计价、项目专采、销售履约和订单驱动供给已在 020、023、024、025、026 完成，后续优先补齐项目生产、发票、正式项目成本、月结和正式财务。
- 生产保持工单、BOM 快照、领退补料、简单报工、完工入库、外协和序列追溯；精细工序、产能排程和复杂质量后置。
- 阶段性成果必须可本地部署，并能通过浏览器实际验收。
- 页面、表格、表单、弹窗、抽屉、追溯和报表必须遵守页面治理规范；设计和开发仍需考虑响应式适配，但后续测试与验收只对本阶段相关页面改动进行桌面端浏览器视觉检验，不要求保存截图，也不测试或验收移动端、窄屏及响应式兼容性。
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
- [本地开发与验证说明](docs/ops/local-development.md)
- [任务文档模板](docs/tasks/task-template.md)

## 分支与验收

日常开发在独立任务分支或功能分支完成。主分支用于阶段性成果验收，必须保持相对稳定、可部署、可浏览器访问。模块完成并通过验证后，才能合入或推送到主分支并通知用户验收。
