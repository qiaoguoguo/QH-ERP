// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只检查本地共享显示层文件。
import { existsSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { dirname, resolve } from 'node:path'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const statusDisplayFile = resolve(currentDir, 'statusDisplay.ts')

describe('共享未知状态显示策略', () => {
  it('未知状态默认只展示中文主标签，并在需要诊断时分离原编码', async () => {
    expect(existsSync(statusDisplayFile)).toBe(true)
    const { createUnknownStatusDisplay } = await import('./statusDisplay')
    const source = Object.freeze({
      status: 'READY_TO_COMMIT',
      statusName: 'READY_TO_COMMIT',
      amount: '12.00',
    })

    const display = createUnknownStatusDisplay({
      domain: 'platform',
      field: 'documentTask.status',
      code: source.status,
      statusName: source.statusName,
      tone: 'warning',
      includeOriginalCode: true,
    })

    expect(display).toEqual({
      label: '未知状态',
      tone: 'warning',
      originalCode: 'READY_TO_COMMIT',
      diagnostic: '原编码：READY_TO_COMMIT',
      context: {
        domain: 'platform',
        field: 'documentTask.status',
      },
    })
    expect(display.label).not.toContain('READY_TO_COMMIT')
    expect(source).toEqual({
      status: 'READY_TO_COMMIT',
      statusName: 'READY_TO_COMMIT',
      amount: '12.00',
    })
  })

  it('只接受中文服务端名称作为主标签，英文名称仍使用未知状态兜底', async () => {
    expect(existsSync(statusDisplayFile)).toBe(true)
    const { createUnknownStatusDisplay } = await import('./statusDisplay')

    expect(createUnknownStatusDisplay({
      domain: 'production',
      field: 'workOrder.status',
      code: 'RELEASED',
      statusName: '已下达',
      tone: 'success',
    })).toMatchObject({
      label: '已下达',
      tone: 'success',
    })

    expect(createUnknownStatusDisplay({
      domain: 'production',
      field: 'workOrder.status',
      code: 'IN_PROGRESS',
      statusName: 'In Progress',
      tone: 'danger',
    })).toMatchObject({
      label: '未知状态',
      tone: 'danger',
    })
  })
})
