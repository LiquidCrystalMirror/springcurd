import { createApp } from 'vue'
import App from './App.vue'
import ElementPlus from 'element-plus'
import Router from '@/router/index'
import 'element-plus/dist/index.css'
let app = createApp(App)
app.use(ElementPlus)
app.use(Router)
app.mount('#app')
