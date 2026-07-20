// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地源码。
import { readdirSync, readFileSync, statSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { dirname, join, relative } from 'node:path'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const sourceRoot = dirname(fileURLToPath(import.meta.url))

function vueFilesUnder(path: string): string[] {
  return (readdirSync(path) as string[]).flatMap((entry) => {
    const fullPath = join(path, entry)
    const status = statSync(fullPath)
    if (status.isDirectory()) {
      return vueFilesUnder(fullPath)
    }
    return entry.endsWith('.vue') ? [fullPath] : []
  })
}

describe('日期选择器契约', () => {
  it('所有日期选择器清空时都回写空字符串', () => {
    const missingValueOnClear = vueFilesUnder(sourceRoot)
      .flatMap((file) => {
        const source = readFileSync(file, 'utf8')
        return [...source.matchAll(/<el-date-picker\b[\s\S]*?\/>/g)]
          .filter((match) => !match[0].includes('value-on-clear'))
          .map((match) => `${relative(sourceRoot, file)}:${source.slice(0, match.index).split('\n').length}`)
      })

    expect(missingValueOnClear).toEqual([])
  })

  it('032 财务结账日期录入使用统一日期选择器和 YYYY-MM-DD 值格式', () => {
    const requiredDateFields = [
      ['modules/financialClose/BankAccountsView.vue', 'bank-account-opened-on'],
      ['modules/financialClose/BankStatementsView.vue', 'bank-statement-transaction-date'],
      ['modules/financialClose/BankStatementsView.vue', 'bank-statement-posting-date'],
      ['modules/financialClose/TaxSettingsView.vue', 'tax-profile-effective-from'],
      ['modules/financialClose/TaxSettingsView.vue', 'tax-rate-effective-from'],
      ['modules/financialClose/TaxSettingsView.vue', 'tax-rate-effective-to'],
      ['modules/financialClose/TaxPaymentsView.vue', 'tax-payment-date'],
    ] as const

    const violations = requiredDateFields.flatMap(([file, fieldName]) => {
      const source = readFileSync(join(sourceRoot, file), 'utf8')
      const datePickerPattern = new RegExp(`<el-date-picker\\b[\\s\\S]*?name="${fieldName}"[\\s\\S]*?value-format="YYYY-MM-DD"[\\s\\S]*?value-on-clear[\\s\\S]*?/?>`)
      const inputPattern = new RegExp(`<el-input\\b[^>]*name="${fieldName}"`)
      const messages: string[] = []
      if (!datePickerPattern.test(source)) {
        messages.push(`${file}:${fieldName}:缺少统一日期选择器`)
      }
      if (inputPattern.test(source)) {
        messages.push(`${file}:${fieldName}:仍使用普通输入框`)
      }
      return messages
    })

    expect(violations).toEqual([])
  })
})
