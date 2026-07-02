import {
  ElAlert,
  ElAside,
  ElButton,
  ElCard,
  ElContainer,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElInput,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElSelect,
  ElSubMenu,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
} from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-aside.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-card.css'
import 'element-plus/theme-chalk/el-container.css'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-drawer.css'
import 'element-plus/theme-chalk/el-empty.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-main.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import 'element-plus/theme-chalk/el-option.css'
import 'element-plus/theme-chalk/el-pagination.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-sub-menu.css'
import 'element-plus/theme-chalk/el-table.css'
import 'element-plus/theme-chalk/el-table-column.css'
import 'element-plus/theme-chalk/el-tag.css'
import 'element-plus/theme-chalk/el-tree.css'
import './style.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'

const app = createApp(App)

app
  .use(createPinia())
  .use(router)
  .use(ElAlert)
  .use(ElAside)
  .use(ElButton)
  .use(ElCard)
  .use(ElContainer)
  .use(ElDialog)
  .use(ElDrawer)
  .use(ElEmpty)
  .use(ElForm)
  .use(ElFormItem)
  .use(ElHeader)
  .use(ElInput)
  .use(ElMain)
  .use(ElMenu)
  .use(ElMenuItem)
  .use(ElOption)
  .use(ElPagination)
  .use(ElSelect)
  .use(ElSubMenu)
  .use(ElTable)
  .use(ElTableColumn)
  .use(ElTag)
  .use(ElTree)
  .mount('#app')
