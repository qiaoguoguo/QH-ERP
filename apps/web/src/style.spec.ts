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
const elementPlusSource = readFileSync(resolve(currentDir, 'elementPlus.ts'), 'utf8')
const componentListStart = elementPlusSource.indexOf('const elementPlusComponents = [')
const componentListEnd = elementPlusSource.indexOf('] as Plugin[]', componentListStart)
const elementPlusComponentList = elementPlusSource.slice(componentListStart, componentListEnd)

function expectElementPlusComponentRegistered(componentName: string) {
  expect(elementPlusSource).toContain(componentName)
  expect(elementPlusComponentList).toMatch(new RegExp(`\\b${componentName}\\b`))
}

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

  it('应用入口挂载共享 Element Plus 生产注册入口', () => {
    expect(mainSource).toContain("import { installElementPlus } from './elementPlus'")
    expect(mainSource).toContain('installElementPlus(app).mount')
  })

  it('日期组件同时加载输入框和日历面板基础样式', () => {
    expectElementPlusComponentRegistered('ElDatePicker')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-date-picker.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-date-picker-panel.css")
  })

  it('时间线组件同时注册组件和加载基础样式', () => {
    expectElementPlusComponentRegistered('ElTimeline')
    expectElementPlusComponentRegistered('ElTimelineItem')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-timeline.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-timeline-item.css")
  })

  it('页签组件同时注册组件和加载基础样式', () => {
    expectElementPlusComponentRegistered('ElTabs')
    expectElementPlusComponentRegistered('ElTabPane')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-tabs.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-tab-pane.css")
  })

  it('描述列表组件同时注册组件和加载基础样式', () => {
    expectElementPlusComponentRegistered('ElDescriptions')
    expectElementPlusComponentRegistered('ElDescriptionsItem')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-descriptions.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-descriptions-item.css")
  })

  it('022 文档平台组件同时注册组件和加载基础样式', () => {
    for (const componentName of ['ElBadge', 'ElProgress', 'ElUpload', 'ElDivider', 'ElSwitch']) {
      expectElementPlusComponentRegistered(componentName)
    }
    for (const stylePath of [
      'element-plus/theme-chalk/el-badge.css',
      'element-plus/theme-chalk/el-progress.css',
      'element-plus/theme-chalk/el-upload.css',
      'element-plus/theme-chalk/el-divider.css',
      'element-plus/theme-chalk/el-switch.css',
    ]) {
      expect(elementPlusSource).toContain(stylePath)
    }
  })

  it('追踪选择抽屉使用的 Loading 指令纳入共享 Element Plus 注册入口', () => {
    expectElementPlusComponentRegistered('ElLoading')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-loading.css")
  })

  it('库存追溯抽屉使用的骨架屏纳入共享 Element Plus 注册入口', () => {
    expectElementPlusComponentRegistered('ElSkeleton')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-skeleton.css")
  })

  it('表格更多菜单使用的下拉组件纳入共享 Element Plus 注册入口和样式入口', () => {
    expectElementPlusComponentRegistered('ElDropdown')
    expectElementPlusComponentRegistered('ElDropdownMenu')
    expectElementPlusComponentRegistered('ElDropdownItem')
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-dropdown.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-dropdown-menu.css")
    expect(elementPlusSource).toContain("element-plus/theme-chalk/el-dropdown-item.css")
  })

  it('027 外协页面规格不再通过旧挂载降级写法降低类型', () => {
    const outsourcingSpecSource = readFileSync(
      resolve(currentDir, 'modules/production/outsourcing/ProductionOutsourcingViews.spec.ts'),
      'utf8',
    )

    expect(outsourcingSpecSource).not.toContain(['component', 'as', 'never'].join(' '))
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

  it('共享状态标签支持中文主标签和可选诊断原码，不抹平语义色', () => {
    expect(styleSource).toContain('.qherp-status-tag')
    expect(styleSource).toContain('.qherp-status-tag__label')
    expect(styleSource).toContain('.qherp-status-tag__diagnostic')
    expect(styleSource).toContain('font-family: var(--qherp-font-mono);')
    expect(styleSource).toContain('.el-tag.el-tag--success')
    expect(styleSource).toContain('.el-tag.el-tag--warning')
    expect(styleSource).toContain('.el-tag.el-tag--danger')
    expect(styleSource).toContain('.el-tag.el-tag--info')
  })

  it('移动端主内容容器覆盖全局高度并允许主区域独立滚动', () => {
    const mobileMedia = styleSource.slice(styleSource.indexOf('@media (max-width: 760px)'))

    expect(mobileMedia).toMatch(/\.app-shell\s*>\s*\.el-container\s*{[^}]*height:\s*auto;[^}]*flex:\s*1 1 auto;[^}]*min-height:\s*0;/s)
    expect(mobileMedia).toMatch(/\.app-main\s*{[^}]*flex:\s*1 1 auto;[^}]*min-height:\s*0;[^}]*overflow:\s*auto;/s)
  })

  it('390px 窄屏侧栏使用临时覆盖和收起结构', () => {
    const narrowMedia = styleSource.slice(styleSource.indexOf('@media (max-width: 390px)'))

    expect(narrowMedia).toMatch(/\.app-sidebar\s*{[^}]*position:\s*fixed;[^}]*height:\s*100svh;[^}]*transform:\s*translateX\(0\);/s)
    expect(narrowMedia).toMatch(/\.app-sidebar\.is-collapsed\s*{[^}]*position:\s*sticky;[^}]*height:\s*56px;[^}]*max-height:\s*56px;/s)
    expect(narrowMedia).toContain('.app-sidebar.is-collapsed .side-menu-scroll')
  })
})
