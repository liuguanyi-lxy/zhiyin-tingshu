<template>
  <main class="setting-page">
    <van-nav-bar title="设置" left-text="返回" left-arrow @click-left="goBack" />
    <div class="setting-content">
      <section class="account-section" aria-labelledby="account-heading">
        <h1 id="account-heading">账号信息</h1>
        <router-link to="/updateInfo" class="profile-link" aria-label="编辑昵称和头像">
          <div class="avatar">
            <img v-if="userStore.user.avatarUrl && !avatarFailed" :src="userStore.user.avatarUrl" alt="当前头像" @error="avatarFailed = true" />
            <van-icon v-else name="manager" size="28" color="#c8c9cc" />
          </div>
          <div class="profile-text">
            <span class="nickname">{{ userStore.user.nickname || '尚未读取昵称' }}</span>
            <span class="edit-label">编辑昵称和头像</span>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </router-link>
        <div v-if="loading" class="status-line" role="status"><van-loading size="18">正在读取账号信息…</van-loading></div>
        <div v-if="loadError" class="load-error" role="alert">
          <p>{{ loadError }}</p>
          <button type="button" @click="loadAccount">重新加载</button>
        </div>
        <dl class="account-details">
          <div><dt>用户 ID</dt><dd>{{ userStore.user.id ?? '尚未读取' }}</dd></div>
          <div><dt>会员状态</dt><dd>{{ vipLabel }}</dd></div>
          <div v-if="Number(userStore.user.isVip) === 1 && userStore.user.vipExpireTime"><dt>会员到期</dt><dd>{{ expireTime }}</dd></div>
        </dl>
      </section>

      <section class="session-section" aria-labelledby="session-heading">
        <h2 id="session-heading">登录管理</h2>
        <p>退出后将清除本应用在当前浏览器的登录状态，不影响其他网站的数据。</p>
        <van-button block plain round type="danger" :loading="loggingOut" :disabled="loggingOut" loading-text="正在退出…" @click="logout">
          退出登录
        </van-button>
      </section>
      <p class="brand-footer">知音听书</p>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loadError = ref('')
const loggingOut = ref(false)
const avatarFailed = ref(false)
const vipLabel = computed(() => userStore.user.isVip == null ? '尚未读取' : Number(userStore.user.isVip) === 1 ? 'VIP 会员' : '普通用户')
const expireTime = computed(() => {
  const value = userStore.user.vipExpireTime
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString('zh-CN')
})
watch(() => userStore.user.avatarUrl, () => { avatarFailed.value = false })

async function loadAccount() {
  if (loading.value || loggingOut.value) return
  loading.value = true
  loadError.value = ''
  try {
    await userStore.getUserInfo()
  } catch (error) {
    loadError.value = error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请稍后重试' : error?.message) || '账号信息读取失败'
  } finally {
    loading.value = false
  }
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定退出知音听书？退出后，查看收听记录、订单等账号内容需要重新登录。',
      confirmButtonText: '退出登录',
      cancelButtonText: '继续收听',
      confirmButtonColor: '#ee0a24'
    })
  } catch {
    loggingOut.value = false
    return
  }
  try {
    // 后端暂无注销接口，只清理本应用会话键，不使用 localStorage.clear()。
    userStore.clear()
    await router.replace('/login')
    showToast('已退出登录')
  } finally {
    loggingOut.value = false
  }
}

function goBack() {
  if (loggingOut.value) return
  const back = router.options.history.state.back
  if (typeof back === 'string' && back.startsWith('/') && !back.startsWith('/login')) router.back()
  else router.replace('/my')
}

onMounted(loadAccount)
</script>

<style scoped>
.setting-page { min-height: 100vh; min-height: 100dvh; background: #fff; color: #323233; padding-bottom: calc(24px + env(safe-area-inset-bottom)); box-sizing: border-box; --van-nav-bar-icon-color: #646566; --van-nav-bar-text-color: #646566; }
.setting-content { max-width: 400px; padding: 28px 24px; margin: 0 auto; }
.account-section h1, .session-section h2 { font-size: 15px; font-weight: 500; margin: 0 0 16px; color: #969799; }
.profile-link { display: flex; align-items: center; gap: 14px; padding: 4px 0 24px; text-decoration: none; color: inherit; }
.avatar { width: 58px; height: 58px; flex-shrink: 0; border-radius: 50%; background: #f7f8fa; overflow: hidden; display: grid; place-items: center; }
.avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
.profile-text { min-width: 0; flex: 1; }
.nickname { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 17px; font-weight: 600; }
.edit-label { display: block; color: #969799; font-size: 12px; margin-top: 7px; }
.status-line { padding: 12px 0; }
.load-error { color: #c3293e; font-size: 13px; padding: 4px 0 12px; line-height: 1.6; }
.load-error p { margin: 0; }
.load-error button { padding: 6px 0; min-height: 36px; border: 0; background: none; color: #079b4e; font: inherit; cursor: pointer; }
.account-details { margin: 0; }
.account-details div { display: flex; justify-content: space-between; align-items: baseline; gap: 24px; padding: 18px 0; border-top: 1px solid #ebedf0; font-size: 14px; }
.account-details dt { flex-shrink: 0; color: #646566; }
.account-details dd { text-align: right; margin: 0; overflow-wrap: anywhere; }
.session-section { border-top: 1px solid #ebedf0; margin-top: 24px; padding-top: 26px; }
.session-section p { color: #969799; font-size: 13px; line-height: 1.8; margin: 0 0 24px; }
.session-section .van-button { min-height: 46px; }
.brand-footer { margin: 40px 0 0; text-align: center; color: #c8c9cc; font-size: 12px; letter-spacing: 2px; }
a:focus-visible, button:focus-visible { outline: 2px solid #079b4e; outline-offset: 4px; border-radius: 4px; }
@media (max-width: 360px) { .setting-content { padding-left: 20px; padding-right: 20px; } }
</style>
