import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
// 按需引入模式下模板 van-* 标签由 unplugin-vue-components 自动注入；
// 函数式 API（Toast/Dialog）不走模板，样式需手动引入；
// base.css 提供 :root 级 --van-* 主题变量（global.css 的覆盖依赖它）。
import 'vant/es/style/base.css'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import './styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
