import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '../stores/user.js'

/**
 * 路由表与小程序 app.json 页面清单对齐（28 页）：
 * - live 系列与 demo/test 页面为已放弃/调试页，未纳入
 * - 布隆守卫与网关 AuthGlobalFilter 行为对齐：无 token 跳登录
 */
const routes = [
  { path: '/', redirect: '/index' },
  { path: '/login', name: 'login', component: () => import('../views/login/index.vue'), meta: { title: '登录' } },
  { path: '/index', name: 'index', component: () => import('../views/index/index.vue'), meta: { title: '首页', tab: true } },
  { path: '/categories', name: 'categories', component: () => import('../views/categories/index.vue'), meta: { title: '分类', tab: true } },
  { path: '/rank', name: 'rank', component: () => import('../views/rank/index.vue'), meta: { title: '排行榜', tab: true } },
  { path: '/my', name: 'my', component: () => import('../views/my/index.vue'), meta: { title: '我的', tab: true } },
  { path: '/search', name: 'search', component: () => import('../views/search/index.vue'), meta: { title: '搜索' } },
  { path: '/detail/:albumId', name: 'detail', component: () => import('../views/detail/index.vue'), meta: { title: '专辑详情' } },
  { path: '/player/:trackId', name: 'player', component: () => import('../views/player/index.vue'), meta: { title: '播放器' } },
  { path: '/album/:albumId', name: 'album', component: () => import('../views/album/index.vue'), meta: { title: '专辑管理' } },
  { path: '/works', name: 'works', component: () => import('../views/works/index.vue'), meta: { title: '作品' } },
  { path: '/myWork', name: 'myWork', component: () => import('../views/myWork/index.vue'), meta: { title: '我的作品' } },
  { path: '/createAlbum', name: 'createAlbum', component: () => import('../views/createAlbum/index.vue'), meta: { title: '创建专辑' } },
  { path: '/createAlbum/:id', name: 'editAlbum', component: () => import('../views/createAlbum/index.vue'), meta: { title: '修改专辑' } },
  { path: '/createTrack', name: 'createTrack', component: () => import('../views/createTrack/index.vue'), meta: { title: '创建声音' } },
  { path: '/createTrack/:id', name: 'editTrack', component: () => import('../views/createTrack/index.vue'), meta: { title: '修改声音' } },
  { path: '/updateInfo', name: 'updateInfo', component: () => import('../views/updateInfo/index.vue'), meta: { title: '编辑资料' } },
  { path: '/order', name: 'order', component: () => import('../views/order/index.vue'), meta: { title: '我的订单' } },
  { path: '/orderDetail/:orderNo', name: 'orderDetail', component: () => import('../views/orderDetail/index.vue'), meta: { title: '订单详情' } },
  { path: '/confirmOrder', name: 'confirmOrder', component: () => import('../views/confirmOrder/index.vue'), meta: { title: '确认订单' } },
  { path: '/wallet', name: 'wallet', component: () => import('../views/wallet/index.vue'), meta: { title: '钱包' } },
  { path: '/fund', name: 'fund', component: () => import('../views/fund/index.vue'), meta: { title: '资金明细' } },
  { path: '/paySuccess', name: 'paySuccess', component: () => import('../views/paySuccess/index.vue'), meta: { title: '支付成功' } },
  { path: '/vipChannel', name: 'vipChannel', component: () => import('../views/vipChannel/index.vue'), meta: { title: 'VIP 频道' } },
  { path: '/setting', name: 'setting', component: () => import('../views/setting/index.vue'), meta: { title: '设置' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 知音听书` : '知音听书'
  // 公开浏览与网关白名单一致；账户、订单、创作和播放器仍需登录。
  const publicPages = new Set(['login', 'index', 'categories', 'rank', 'search', 'detail', 'vipChannel'])
  if (!publicPages.has(to.name)) {
    const userStore = useUserStore()
    if (!userStore.token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
