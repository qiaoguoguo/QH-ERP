import type { App, Plugin } from 'vue'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElBadge } from 'element-plus/es/components/badge/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElCard } from 'element-plus/es/components/card/index.mjs'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index.mjs'
import { ElAside, ElContainer, ElHeader, ElMain } from 'element-plus/es/components/container/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDescriptions, ElDescriptionsItem } from 'element-plus/es/components/descriptions/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDivider } from 'element-plus/es/components/divider/index.mjs'
import { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElLoading } from 'element-plus/es/components/loading/index.mjs'
import { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus/es/components/menu/index.mjs'
import { ElOption } from 'element-plus/es/components/select/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElProgress } from 'element-plus/es/components/progress/index.mjs'
import { ElRadio, ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index.mjs'
import { ElResult } from 'element-plus/es/components/result/index.mjs'
import { ElScrollbar } from 'element-plus/es/components/scrollbar/index.mjs'
import { ElSegmented } from 'element-plus/es/components/segmented/index.mjs'
import { ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElSkeleton } from 'element-plus/es/components/skeleton/index.mjs'
import { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
import { ElTooltip } from 'element-plus/es/components/tooltip/index.mjs'
import { ElTree } from 'element-plus/es/components/tree/index.mjs'
import { ElUpload } from 'element-plus/es/components/upload/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-aside.css'
import 'element-plus/theme-chalk/el-badge.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-card.css'
import 'element-plus/theme-chalk/el-config-provider.css'
import 'element-plus/theme-chalk/el-container.css'
import 'element-plus/theme-chalk/el-date-picker.css'
import 'element-plus/theme-chalk/el-date-picker-panel.css'
import 'element-plus/theme-chalk/el-descriptions.css'
import 'element-plus/theme-chalk/el-descriptions-item.css'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-divider.css'
import 'element-plus/theme-chalk/el-drawer.css'
import 'element-plus/theme-chalk/el-empty.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-main.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-option.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-pagination.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-progress.css'
import 'element-plus/theme-chalk/el-radio.css'
import 'element-plus/theme-chalk/el-radio-button.css'
import 'element-plus/theme-chalk/el-radio-group.css'
import 'element-plus/theme-chalk/el-result.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/el-segmented.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-skeleton.css'
import 'element-plus/theme-chalk/el-sub-menu.css'
import 'element-plus/theme-chalk/el-switch.css'
import 'element-plus/theme-chalk/el-tab-pane.css'
import 'element-plus/theme-chalk/el-tabs.css'
import 'element-plus/theme-chalk/el-table.css'
import 'element-plus/theme-chalk/el-table-column.css'
import 'element-plus/theme-chalk/el-tag.css'
import 'element-plus/theme-chalk/el-timeline.css'
import 'element-plus/theme-chalk/el-timeline-item.css'
import 'element-plus/theme-chalk/el-tooltip.css'
import 'element-plus/theme-chalk/el-tree.css'
import 'element-plus/theme-chalk/el-upload.css'

const elementPlusComponents = [
  ElAlert,
  ElAside,
  ElBadge,
  ElButton,
  ElCard,
  ElConfigProvider,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDivider,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElInput,
  ElLoading,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElResult,
  ElScrollbar,
  ElSegmented,
  ElSelect,
  ElSkeleton,
  ElSubMenu,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElTooltip,
  ElTree,
  ElUpload,
] as Plugin[]

export const elementPlusLocale = zhCn

export function installElementPlus(app: App) {
  for (const component of elementPlusComponents) {
    app.use(component)
  }
  return app
}
