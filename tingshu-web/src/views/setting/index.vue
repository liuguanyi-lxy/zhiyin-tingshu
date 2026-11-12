<template>
  <main class="setting-page">
    <van-nav-bar title="设置" left-text="返回" left-arrow @click-left="goBack" />
    <div class="setting-content">
      <section class="settings-group" aria-labelledby="account-heading">
        <h2 id="account-heading" class="group-title">账号信息</h2>
        <div class="tingshu-card group-card">
          <router-link to="/updateInfo" class="profile-link" aria-label="编辑昵称和头像">
            <div class="avatar">
              <img v-if="userStore.user.avatarUrl && !avatarFailed" :src="userStore.user.avatarUrl" alt="当前头像" @error="avatarFailed = true" />
              <van-icon v-else name="manager" size="28" color="#9aa1ab" />
            </div>
            <div class="profile-text">
              <span class="nickname">{{ userStore.user.nickname || '尚未读取昵称' }}</span>
              <span class="edit-label">编辑昵称和头像</span>
            </div>
            <van-icon name="arrow" color="#9aa1ab" />
          </router-link>
          <div v-if="loading" class="status-line" role="status"><van-loading size="18">正在读取账号信息…</van-loading></div>
          <div v-if="loadError" class="load-error" role="alert">
            <p>{{ loadError }}</p>
            <button type="button" @click="loadAccount">重新加载</button>
          </div>
          <dl class="account-details">
            <div><dt>用户 ID</dt><dd class="num">{{ userStore.user.id ?? '尚未读取' }}</dd></div>
            <div><dt>会员状态</dt><dd><span class="tag" :class="Number(userStore.user.isVip) === 1 ? 'tag-vip' : 'tag-free'">{{ vipLabel }}</span></dd></div>
            <div v-if="Number(userStore.user.isVip) === 1 && userStore.user.vipExpireTime"><dt>会员到期</dt><dd class="num">{{ expireTime }}</dd></div>
          </dl>
        </div>
      </section>

      <section class="settings-group" aria-labelledby="session-heading">
        <h2 id="session-heading" class="group-title">登录管理</h2>
        <div class="tingshu-card group-card">
          <p class="session-note">退出后将清除本应用在当前浏览器的登录状态，不影响其他网站的数据。</p>
          <van-button block plain round type="danger" :loading="loggingOut" :disabled="loggingOut" loading-text="正在退出…" @click="logout">
            退出登录
          </van-button>
        </div>
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
      confirmButtonColor: '#007aff'
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
.setting-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: calc(var(--sp-6) + env(safe-area-inset-bottom));
  box-sizing: border-box;
  --van-nav-bar-icon-color: var(--ink-3);
  --van-nav-bar-text-color: var(--ink-3);
}

.setting-content {
  max-width: 520px;
  padding: var(--sp-4) var(--sp-3) var(--sp-5);
  margin: 0 auto;
}

.settings-group + .settings-group {
  margin-top: var(--sp-5);
}

.group-title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-meta);
  font-weight: 600;
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

.group-card {
  padding: var(--sp-4);
}

.profile-link {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-1) 0 var(--sp-4);
  text-decoration: none;
  color: inherit;
  border-radius: var(--r-sm);
}

.profile-link:hover .nickname {
  color: var(--brand);
}

.avatar {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--line-soft);
  overflow: hidden;
  display: grid;
  place-items: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.profile-text {
  min-width: 0;
  flex: 1;
}

.nickname {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-section);
  font-weight: 600;
  color: var(--ink);
  transition: color 0.15s ease;
}

.edit-label {
  display: block;
  color: var(--ink-3);
  font-size: var(--fs-meta);
  margin-top: 6px;
}

.status-line {
  padding: var(--sp-3) 0;
}

.load-error {
  color: var(--paid);
  font-size: var(--fs-body);
  padding: var(--sp-1) 0 var(--sp-3);
  line-height: 1.6;
}

.load-error p {
  margin: 0;
}

.load-error button {
  padding: 6px 0;
  min-height: 36px;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}

.load-error button:hover {
  color: var(--brand-strong);
}

.account-details {
  margin: 0;
}

.account-details div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  border-top: 1px solid var(--line-soft);
  font-size: var(--fs-body);
}

.account-details dt {
  flex-shrink: 0;
  color: var(--ink-3);
}

.account-details dd {
  text-align: right;
  margin: 0;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.session-note {
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.8;
  margin: 0 0 var(--sp-4);
}

.session-note + :deep(.van-button) {
  min-height: 46px;
}

.brand-footer {
  margin: var(--sp-6) 0 0;
  text-align: center;
  color: var(--ink-4);
  font-size: var(--fs-meta);
  letter-spacing: 2px;
}

a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
  border-radius: var(--r-sm);
}

@media (max-width: 360px) {
  .setting-content {
    padding-left: var(--sp-3);
    padding-right: var(--sp-3);
  }
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .setting-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .setting-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .setting-content {
    max-width: 560px;
    padding: var(--sp-5) var(--sp-4) var(--sp-6);
  }

  .group-title {
    font-size: var(--fs-body);
  }
}
</style>
