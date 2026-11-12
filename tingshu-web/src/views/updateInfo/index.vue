<template>
  <main class="profile-page">
    <van-nav-bar title="编辑资料" left-text="返回" left-arrow @click-left="goBack" />
    <section class="profile-content" aria-label="个人资料">
      <div v-if="profileLoading" class="status-line" role="status"><van-loading size="20">正在读取资料…</van-loading></div>
      <div v-if="loadError" class="load-error" role="alert">
        <p>{{ loadError }}</p>
        <van-button size="small" plain type="primary" @click="loadProfile">重新加载</van-button>
      </div>

      <form v-if="profileReady" novalidate @submit.prevent="saveProfile">
        <div class="tingshu-card form-card">
          <div class="avatar-section">
            <div class="avatar-preview">
              <img v-if="form.avatarUrl && !avatarFailed" :src="form.avatarUrl" alt="当前头像" @error="avatarFailed = true" />
              <van-icon v-else name="manager" size="42" color="#9aa1ab" aria-label="暂无可显示的头像" />
            </div>
            <div class="avatar-actions">
              <span class="field-label">头像</span>
              <button type="button" class="upload-button" :disabled="busy" :aria-busy="uploading" @click="fileInput?.click()">
                {{ uploading ? '上传中…' : '更换头像' }}
              </button>
              <input ref="fileInput" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" tabindex="-1" aria-label="选择头像图片" :disabled="busy" @change="uploadAvatar" />
              <p class="field-hint">JPG、PNG 或 WebP，不超过 5 MB</p>
            </div>
          </div>
          <p v-if="uploadError" class="field-error" role="alert">{{ uploadError }}</p>
          <p v-if="avatarError" class="field-error" role="alert">{{ avatarError }}</p>

          <div class="nickname-field">
            <label for="profile-nickname">昵称</label>
            <input
              id="profile-nickname"
              ref="nicknameInput"
              v-model="form.nickname"
              name="nickname"
              type="text"
              autocomplete="nickname"
              maxlength="32"
              placeholder="请输入昵称"
              required
              :disabled="busy"
              :aria-invalid="!!nicknameError"
              :aria-describedby="nicknameError ? 'profile-nickname-error' : 'profile-nickname-hint'"
              @input="nicknameError = ''; saveError = ''"
            />
            <p v-if="nicknameError" id="profile-nickname-error" class="field-error">{{ nicknameError }}</p>
            <p v-else id="profile-nickname-hint" class="field-hint">1–32 个字符，不能只填写空格</p>
          </div>
          <p v-if="saveError" class="save-error" role="alert">{{ saveError }}</p>
          <van-button class="save-button" native-type="submit" type="primary" block round :loading="saving" loading-text="保存中…" :disabled="busy || !changed">
            保存修改
          </van-button>
          <p class="save-hint">{{ uploading ? '头像上传完成后，点击保存修改' : changed ? '保存后将更新你的昵称和头像' : '修改昵称或头像后保存' }}</p>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { userApi } from '../../api/user.js'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()
const form = reactive({ nickname: userStore.user.nickname || '', avatarUrl: userStore.user.avatarUrl || '' })
const original = reactive({ nickname: '', avatarUrl: '' })
const profileReady = ref(false)
const profileLoading = ref(false)
const uploading = ref(false)
const saving = ref(false)
const loadError = ref('')
const uploadError = ref('')
const nicknameError = ref('')
const avatarError = ref('')
const saveError = ref('')
const avatarFailed = ref(false)
const fileInput = ref(null)
const nicknameInput = ref(null)
const busy = computed(() => profileLoading.value || uploading.value || saving.value)
const changed = computed(() => form.nickname.trim() !== original.nickname || form.avatarUrl !== original.avatarUrl)

function errorMessage(error, fallback) {
  return error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请稍后重试' : error?.message) || fallback
}

async function loadProfile() {
  if (busy.value) return
  profileLoading.value = true
  loadError.value = ''
  try {
    await userStore.getUserInfo()
    form.nickname = userStore.user.nickname || ''
    form.avatarUrl = userStore.user.avatarUrl || ''
    original.nickname = form.nickname.trim()
    original.avatarUrl = form.avatarUrl
    avatarFailed.value = false
    profileReady.value = true
  } catch (error) {
    loadError.value = errorMessage(error, '资料加载失败，请重试')
  } finally {
    profileLoading.value = false
  }
}

async function uploadAvatar(event) {
  const file = event.target.files?.[0]
  if (!file || busy.value || !profileReady.value) return
  uploadError.value = ''
  avatarError.value = ''
  saveError.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    uploadError.value = '请选择 JPG、PNG 或 WebP 图片'
    event.target.value = ''
    return
  }
  if (file.size === 0 || file.size > 5 * 1024 * 1024) {
    uploadError.value = '请选择不超过 5 MB 的非空图片'
    event.target.value = ''
    return
  }
  const requestToken = userStore.token
  if (!requestToken) {
    uploadError.value = '登录已失效，请重新登录'
    event.target.value = ''
    return
  }
  uploading.value = true
  try {
    // Service.upload 统一注入 header.token；fileUpload 的 data 不是对象。
    const res = await userApi.uploadAvatar(file)
    if (userStore.token !== requestToken) throw new Error('登录状态已变化，请重新登录')
    if (res?.code !== 200 || typeof res.data !== 'string' || !/^https?:\/\//i.test(res.data)) {
      throw new Error(res?.code === 200 ? '上传未返回有效图片地址，请重试' : res?.message || '头像上传失败')
    }
    form.avatarUrl = res.data
    avatarFailed.value = false
  } catch (error) {
    uploadError.value = errorMessage(error, '头像上传失败，请重试')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function saveProfile() {
  if (busy.value || !profileReady.value || !changed.value) return
  nicknameError.value = !form.nickname.trim() ? '请输入昵称' : form.nickname.trim().length > 32 ? '昵称不能超过 32 个字符' : ''
  avatarError.value = !form.avatarUrl ? '请先上传头像' : ''
  saveError.value = ''
  if (nicknameError.value || avatarError.value) {
    if (nicknameError.value) nicknameInput.value?.focus()
    return
  }
  const requestToken = userStore.token
  if (!requestToken) {
    saveError.value = '登录已失效，请重新登录'
    return
  }
  const payload = { nickname: form.nickname.trim(), avatarUrl: form.avatarUrl }
  saving.value = true
  try {
    const res = await userApi.updateUserInfo(payload)
    if (userStore.token !== requestToken) throw new Error('登录状态已变化，请重新登录')
    if (res?.code !== 200) throw new Error(res?.message || '保存失败，请重试')
    // 后端确认写入后同步 store；避免立即重读尚未由 CDC 失效的旧资料缓存。
    userStore.setUserInfo({ ...userStore.user, ...payload })
    Object.assign(original, payload)
    showSuccessToast('资料已保存')
    returnToMy()
  } catch (error) {
    saveError.value = errorMessage(error, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function returnToMy() {
  const back = router.options.history.state.back
  if (typeof back === 'string' && back.startsWith('/') && !back.startsWith('/login')) router.back()
  else router.replace('/my')
}

function goBack() {
  if (!busy.value) returnToMy()
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: calc(var(--sp-6) + env(safe-area-inset-bottom));
  box-sizing: border-box;
  --van-nav-bar-icon-color: var(--ink-3);
  --van-nav-bar-text-color: var(--ink-3);
}

.profile-content {
  max-width: 520px;
  margin: 0 auto;
  padding: var(--sp-4) var(--sp-3) var(--sp-5);
}

.status-line {
  padding: var(--sp-5) 0;
  text-align: center;
}

.load-error {
  padding: var(--sp-4) 0;
  color: var(--ink-2);
  font-size: var(--fs-body);
  line-height: 1.6;
}

.load-error p {
  margin: 0 0 var(--sp-2);
}

.form-card {
  padding: var(--sp-4);
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-1) 0 var(--sp-4);
}

.avatar-preview {
  width: 84px;
  height: 84px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--line-soft);
  overflow: hidden;
}

.avatar-preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  min-width: 0;
}

.field-label {
  display: block;
  font-size: var(--fs-body);
  color: var(--ink);
}

.upload-button {
  display: block;
  min-height: 40px;
  padding: 4px 0;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  font-size: var(--fs-body);
  cursor: pointer;
}

.upload-button:hover {
  color: var(--brand-strong);
}

.field-hint {
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.6;
  margin: 6px 0 0;
}

.nickname-field {
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.nickname-field label {
  display: block;
  color: var(--ink-2);
  font-size: var(--fs-body);
  margin-bottom: var(--sp-2);
}

.nickname-field input {
  width: 100%;
  min-height: 46px;
  padding: 10px 2px;
  box-sizing: border-box;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 16px;
}

.nickname-field input::placeholder {
  color: var(--ink-4);
}

.nickname-field input:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 1px 0 var(--brand);
}

.nickname-field input[aria-invalid='true'] {
  border-color: var(--paid);
}

.field-error {
  color: var(--paid);
  font-size: var(--fs-meta);
  margin: var(--sp-2) 0 0;
  line-height: 1.5;
}

.save-error {
  margin-top: var(--sp-4);
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--paid-soft);
  color: var(--paid);
  font-size: var(--fs-meta);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.save-button {
  margin-top: var(--sp-5);
  min-height: 46px;
  font-size: var(--fs-body);
}

.save-hint {
  margin: var(--sp-3) 0 0;
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.6;
  text-align: center;
}

button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
  border-radius: var(--r-sm);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 360px) {
  .profile-content {
    padding: var(--sp-3);
  }

  .avatar-section {
    gap: var(--sp-3);
  }
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .profile-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .profile-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .profile-content {
    max-width: 520px;
    padding: var(--sp-5) var(--sp-4) var(--sp-6);
  }

  .form-card {
    padding: var(--sp-5);
  }
}
</style>
