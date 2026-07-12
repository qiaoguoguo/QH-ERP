# QH ERP

QH ERP 是一套面向制造业单公司使用的 Web ERP 系统。系统不面向零售、门店收银或通用进销存，长期目标是围绕生产管理形成可追溯、可验收、可持续扩展的企业内部管理系统。

## 当前阶段

当前稳定交付已经完成从 `000` 到 `020` 的基础阶段，并完成页面基础体验治理。`020 销售项目与合同管理基础` 在既有客户、销售订单和权限基础上补齐了销售项目、主合同、补充合同、销售订单可选关联、状态动作、权限脱敏和审计追溯。

后续 `021` 至 `036` 继续按“项目型制造的业财成本闭环优先”推进。下一阶段为 `021 成本财务主数据与 BOM 治理`，当前尚未启动；路线规划不代表功能已经实现。

后续阶段推进必须基于最新交接文档、当前仓库状态和用户最新指令，复用产品经理、UI 设计师、前端开发、后端开发、测试五个固定角色子代理。主代理只负责总体规划、范围控制、并行协调、审查汇总和交付判断，不因执行耗时、故障或返工压力直接接手业务实现。

## 项目重点

- 长期覆盖基础资料、采购、销售、库存、生产、成本、财务和报表等常规制造业 ERP 能力。
- 第一版后续建设以销售项目为一级成本和利润对象，优先补齐项目合同、库存计价、项目专采、发票、项目成本、月结和正式财务。
- 生产保持工单、BOM 快照、领退补料、简单报工、完工入库、外协和序列追溯；精细工序、产能排程和复杂质量后置。
- 阶段性成果必须可本地部署，并能通过浏览器实际验收。
- 页面、表格、表单、弹窗、抽屉、追溯和报表必须遵守页面治理规范，并保留可信浏览器视觉验收记录。
- 主代理负责总体规划、协调、审查汇总和交付判断；业务实现、设计、测试和审核必须由固定角色子代理分工完成。

## 文档入口

- [仓库规范](AGENTS.md)
- [文档索引](docs/README.md)
- [当前项目交接与后续规划](docs/handoffs/2026-07-12-project-handoff-current.md)
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
- [本地开发与验证说明](docs/ops/local-development.md)
- [任务文档模板](docs/tasks/task-template.md)

## 分支与验收

日常开发在独立任务分支或功能分支完成。主分支用于阶段性成果验收，必须保持相对稳定、可部署、可浏览器访问。模块完成并通过验证后，才能合入或推送到主分支并通知用户验收。
