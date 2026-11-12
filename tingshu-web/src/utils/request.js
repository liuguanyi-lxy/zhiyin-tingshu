import axios from 'axios'
import { BASE_URL } from '../config/confjg.js'
import { useUserStore } from '../stores/user.js'
import router from '../router/index.js'
import { showLoadingToast, closeToast, showFailToast } from 'vant'

/**
 * 与小程序 utils/request.js 对齐的请求封装（axios 版）：
 * - 自动携带 token 请求头
 * - 页面自行维护加载态，默认不弹全局 toast；仅调用方显式 loading:true 时提示
 * - code==200 放行；code==208 登录过期 → 跳登录页；其余失败提示（silent:true 可静默，用于有兜底的可选增强接口）
 */
const instance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-type': 'application/json; charset=UTF-8' }
})

let pendingLoading = 0
function finishLoading(config) {
  if (!config?.__ownsLoadingToast) return
  config.__ownsLoadingToast = false
  pendingLoading = Math.max(0, pendingLoading - 1)
  if (!pendingLoading) {
    // 清掉全部残留 toast，避免并发 show/close 留下无法退场的半透明遮罩。
    closeToast(true)
  }
}

instance.interceptors.request.use((config) => {
  config.headers.token = useUserStore().token
  // 显式 loading:true 才显示全局提示；普通请求由页面 skeleton/loading 承担反馈。
  if (config.showLoading === true && !config.silent) {
    config.__ownsLoadingToast = true
    pendingLoading++
    if (pendingLoading === 1) {
      showLoadingToast({ message: '加载中...', forbidClick: false, duration: 0 })
    }
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const res = response.data
    finishLoading(response.config)
    if (res.code === 200) {
      return res
    }
    if (res.code === 208) {
      // 会话失效必须拒绝当前请求，避免调用方误当成业务成功。
      const userStore = useUserStore()
      // 旧请求迟到的208不能清除用户刚刚重新登录的新会话。
      if (String(response.config.headers?.token || '') !== String(userStore.token || '')) {
        return Promise.reject(res)
      }
      userStore.clear()
      const current = router.currentRoute.value
      if (current.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: current.fullPath } })
      }
      return Promise.reject(res)
    }
    if (!response.config.silent) {
      showFailToast(res.message || '请求失败')
    }
    return Promise.reject(res)
  },
  (error) => {
    finishLoading(error.config)
    if (!error.config?.silent) {
      showFailToast('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export class Service {
  get(opts) {
    return instance({ url: opts.url, method: 'GET', params: opts.data, showLoading: opts.loading, silent: opts.silent })
  }
  post(opts) {
    return instance({ url: opts.url, method: 'POST', data: opts.data, showLoading: opts.loading, silent: opts.silent })
  }
  put(opts) {
    return instance({ url: opts.url, method: 'PUT', data: opts.data, showLoading: opts.loading, silent: opts.silent })
  }
  delete(opts) {
    return instance({ url: opts.url, method: 'DELETE', data: opts.data, showLoading: opts.loading, silent: opts.silent })
  }
  /**
   * 文件上传（与小程序 upload 对齐）
   */
  upload(opts) {
    const formData = new FormData()
    formData.append('file', opts.file)
    return instance({
      url: opts.url,
      method: 'POST',
      data: formData,
      headers: { 'Content-type': 'multipart/form-data' }
    })
  }
}
