import './style.css'

import {
  ElAside,
  ElContainer,
  ElHeader,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElTag,
} from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-aside.css'
import 'element-plus/theme-chalk/el-container.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-main.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import 'element-plus/theme-chalk/el-tag.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'

const app = createApp(App)

app
  .use(createPinia())
  .use(router)
  .use(ElAside)
  .use(ElContainer)
  .use(ElHeader)
  .use(ElMain)
  .use(ElMenu)
  .use(ElMenuItem)
  .use(ElTag)
  .mount('#app')
