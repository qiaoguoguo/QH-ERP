// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地 CSS 源码。
import { readFileSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { fileURLToPath } from 'node:url'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { dirname, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const styleSource = readFileSync(resolve(currentDir, 'style.css'), 'utf8')
const mainSource = readFileSync(resolve(currentDir, 'main.ts'), 'utf8')

describe('全局样式契约', () => {
  it('左侧菜单栏使用深色文档侧栏配色', () => {
    expect(styleSource).toContain('--qherp-sidebar: #0a0a0a;')
    expect(styleSource).toContain('--qherp-sidebar-surface: #1c1c1e;')
    expect(styleSource).toContain('--qherp-sidebar-heading: #ffffff;')
    expect(styleSource).toContain('--qherp-sidebar-text: #b3b3b3;')
  })

  it('可点击超链接和表格内操作按钮具备明确视觉提示', () => {
    expect(styleSource).toContain('.app-main a')
    expect(styleSource).toContain('.app-main .el-button.is-link')
    expect(styleSource).toContain('--qherp-action-bg')
    expect(styleSource).toContain('text-decoration: underline')
  })

  it('普通按钮覆写不覆盖语义按钮状态色', () => {
    expect(styleSource).not.toContain('.el-button:not(.el-button--primary):not(.el-button--danger):not(.is-link)')
    expect(styleSource).toContain('.el-button:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):not(.el-button--info):not(.is-link)')
  })

  it('表格文本按钮增强不覆盖语义按钮状态色', () => {
    const plainTableTextButton = '.app-main .el-table .el-button.is-text:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):not(.el-button--info)'

    expect(styleSource).toContain(plainTableTextButton)
    expect(styleSource).not.toContain('.app-main .el-table .el-button.is-text {')
    expect(styleSource).not.toContain('.app-main .el-table .el-button.is-text:hover,')
    expect(styleSource).not.toContain('.app-main .el-table .el-button.is-text:focus-visible')
    expect(styleSource).toContain('.app-main .el-table .el-button.is-text.el-button--danger')
  })

  it('日期选择器和确认弹窗使用统一系统样式', () => {
    expect(styleSource).toContain('.el-picker__popper')
    expect(styleSource).toContain('.qherp-confirm-message-box')
    expect(styleSource).toContain('--qherp-date-hover-bg')
  })

  it('日期组件同时加载输入框和日历面板基础样式', () => {
    expect(mainSource).toContain("element-plus/theme-chalk/el-date-picker.css")
    expect(mainSource).toContain("element-plus/theme-chalk/el-date-picker-panel.css")
  })

  it('搜索栏使用统一的标签置顶布局并对齐日期控件', () => {
    expect(styleSource).toContain('.el-form--inline.query-form')
    expect(styleSource).toContain('.query-form > .el-form-item .el-form-item__label')
    expect(styleSource).toContain('.query-form > .el-form-item .el-form-item__content')
    expect(styleSource).toContain('grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));')
    expect(styleSource).toContain('.report-filter-bar .el-form-item__content')
    expect(styleSource).toContain('.el-date-editor.el-input')
    expect(styleSource).toContain('height: 40px;')
  })

  it('移动端主内容容器覆盖全局高度并允许主区域独立滚动', () => {
    const mobileMedia = styleSource.slice(styleSource.indexOf('@media (max-width: 760px)'))

    expect(mobileMedia).toMatch(/\.app-shell\s*>\s*\.el-container\s*{[^}]*height:\s*auto;[^}]*flex:\s*1 1 auto;[^}]*min-height:\s*0;/s)
    expect(mobileMedia).toMatch(/\.app-main\s*{[^}]*flex:\s*1 1 auto;[^}]*min-height:\s*0;[^}]*overflow:\s*auto;/s)
  })
})
