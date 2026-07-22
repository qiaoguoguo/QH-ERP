// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地源码。
import { readFileSync, readdirSync, statSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地源码。
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const moduleRoots = [
  'src/modules/master',
  'src/modules/materials',
  'src/modules/system',
  'src/modules/platform',
]

function collectSourceFiles(dir: string): string[] {
  return (readdirSync(dir) as string[])
    .flatMap((entry: string) => {
      const path = join(dir, entry)
      if (statSync(path).isDirectory()) {
        return collectSourceFiles(path)
      }
      return /\.(vue|ts)$/.test(entry) && !entry.endsWith('.spec.ts') ? [path] : []
    })
}

function sourceMatches(pattern: RegExp) {
  return moduleRoots
    .flatMap(collectSourceFiles)
    .flatMap((path) => {
      const source = String(readFileSync(path, 'utf8'))
      return Array.from(source.matchAll(pattern)).map((match: RegExpMatchArray) => ({
        file: path.replaceAll('\\', '/'),
        text: match[0],
      }))
    })
}

describe('核心模块页面治理静态守卫', () => {
  it('查询区不再使用旧式 inline 布局', () => {
    const inlineQueryForms = sourceMatches(/<el-form\b[^>\n]*class="query-form"[^>\n]*\binline\b|<el-form\b[^>\n]*\binline\b[^>\n]*class="query-form"/g)

    expect(inlineQueryForms).toEqual([])
  })

  it('可见状态文案不得把未知英文编码作为回退文案', () => {
    const rawFallbacks = sourceMatches(/labels\[[^\]]+\]\s*\?\?\s*(?:status|type|stage|code|risk)\b|statusName\s*\|\|\s*status|disabledReason\s*\|\|[^}\n]*summary\s*\|\|[^}\n]*status/g)

    expect(rawFallbacks).toEqual([])
  })

  it('停用和取消动作不使用危险红色状态语义', () => {
    const dangerStatusActions = sourceMatches(/status === 'DISABLED' \? 'success' : 'danger'|data-test="(?:disable|cancel)[^"]*"[^>\n]*type="danger"|type="danger"[^>\n]*data-test="(?:disable|cancel)[^"]*"/g)

    expect(dangerStatusActions).toEqual([])
  })
})
