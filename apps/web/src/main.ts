import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { installElementPlus } from './elementPlus'
import { router } from './router'
import './style.css'

const app = createApp(App)

app
  .use(createPinia())
  .use(router)

installElementPlus(app).mount('#app')
