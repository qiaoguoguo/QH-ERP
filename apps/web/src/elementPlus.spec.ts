import { createApp } from 'vue'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地源码。
import { readFileSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { dirname, join } from 'node:path'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { installElementPlus } from './elementPlus'

const sourceRoot = dirname(fileURLToPath(import.meta.url))
const elementPlusSource = readFileSync(join(sourceRoot, 'elementPlus.ts'), 'utf8')
const appSource = readFileSync(join(sourceRoot, 'App.vue'), 'utf8')

describe('Element Plus 共享注册', () => {
  it('注册核销工作台和加载态使用的组件，避免未解析告警', () => {
    const app = createApp({ render: () => null })

    installElementPlus(app)

    expect(app.component('ElSkeleton')).toBeTruthy()
    expect(app.component('ElSegmented')).toBeTruthy()
  })

  it('通过官方中文区域设置统一 Element Plus 内置用户文案', async () => {
    const app = createApp({ render: () => null })
    const { elementPlusLocale } = await import('./elementPlus')

    installElementPlus(app)

    expect(app.component('ElConfigProvider')).toBeTruthy()
    expect(elementPlusLocale).toMatchObject({
      name: 'zh-cn',
      el: {
        pagination: {
          total: '共 {total} 条',
          goto: '前往',
          pagesize: '条/页',
        },
        datepicker: {
          today: '今天',
          clear: '清空',
          confirm: '确定',
        },
      },
    })
    expect(elementPlusSource).toContain("element-plus/es/locale/lang/zh-cn.mjs")
    expect(elementPlusSource).not.toContain('Total')
    expect(elementPlusSource).not.toContain('/page')
    expect(appSource).toContain('<el-config-provider :locale="elementPlusLocale">')
  })

  it('032 受影响分页使用 v-model 写法，避免 Element Plus 当前废弃提示', () => {
    const files = [
      'modules/financialClose/FinancialCloseWorkbenchView.vue',
      'modules/financialClose/ProfitLossCarryforwardView.vue',
      'modules/financialClose/BankAccountsView.vue',
      'modules/financialClose/BankStatementsView.vue',
      'modules/financialClose/BankReconciliationView.vue',
      'modules/financialClose/TaxSummaryView.vue',
      'modules/financialClose/TaxPaymentsView.vue',
      'modules/gl/GlAccountingPeriodsView.vue',
    ]

    const violations = files.flatMap((file) => {
      const source = readFileSync(join(sourceRoot, file), 'utf8')
      if (!source.includes('<el-pagination')) {
        return []
      }
      const paginationBlocks = [...source.matchAll(/<el-pagination\b[\s\S]*?\/>/g)].map((match) => match[0])
      const messages: string[] = []
      if (!source.includes('v-model:current-page="pagination.page"')) {
        messages.push(`${file}:缺少 current-page v-model`)
      }
      if (!source.includes('v-model:page-size="pagination.pageSize"')) {
        messages.push(`${file}:缺少 page-size v-model`)
      }
      if (paginationBlocks.some((block) => /\s:current-page=/.test(block) || /\s:page-size=/.test(block))) {
        messages.push(`${file}:仍使用分页废弃绑定`)
      }
      return messages
    })

    expect(violations).toEqual([])
  })
})
