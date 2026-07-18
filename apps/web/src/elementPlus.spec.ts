import { createApp } from 'vue'
import { describe, expect, it } from 'vitest'
import { installElementPlus } from './elementPlus'

describe('Element Plus 共享注册', () => {
  it('注册核销工作台和加载态使用的组件，避免未解析告警', () => {
    const app = createApp({ render: () => null })

    installElementPlus(app)

    expect(app.component('ElSkeleton')).toBeTruthy()
    expect(app.component('ElSegmented')).toBeTruthy()
  })
})
