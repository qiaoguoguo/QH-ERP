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
})
