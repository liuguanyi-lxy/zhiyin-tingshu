<template>
  <main class="login-page">
    <nav class="login-nav" aria-label="页面导航">
      <router-link to="/index"><van-icon name="arrow-left" /> 返回首页</router-link>
    </nav>

    <header class="brand">
      <span class="brand-mark" aria-hidden="true">听</span>
      <h1>知音听书</h1>
      <p>登录后同步你的收听记录</p>
    </header>

    <section class="login-panel" aria-labelledby="login-heading">
      <h2 id="login-heading">{{ registering ? '注册账号' : (smsMode ? '验证码登录' : '手机号登录') }}</h2>

      <div v-if="smsEnabled && !registering" class="login-tabs" role="tablist" aria-label="登录方式">
        <button type="button" role="tab" :aria-selected="!smsMode" :class="{ active: !smsMode }" @click="switchLoginMode('password')">密码登录</button>
        <button type="button" role="tab" :aria-selected="smsMode" :class="{ active: smsMode }" @click="switchLoginMode('sms')">验证码登录</button>
      </div>

      <form novalidate @submit.prevent="submit">
        <fieldset :disabled="loading">
          <legend class="sr-only">{{ registering ? '注册信息' : '登录信息' }}</legend>
          <div class="form-field">
            <label for="login-phone">手机号</label>
            <input
              id="login-phone"
              ref="phoneInput"
              v-model="form.phone"
              name="phone"
              type="tel"
              inputmode="numeric"
              autocomplete="username"
              maxlength="11"
              placeholder="请输入 11 位手机号"
              required
              :aria-invalid="!!errors.phone"
              :aria-describedby="errors.phone ? 'phone-error' : undefined"
              @input="errors.phone = ''; serverError = ''"
            />
            <p v-if="errors.phone" id="phone-error" class="field-error">{{ errors.phone }}</p>
          </div>

          <div v-if="!smsMode" class="form-field">
            <label for="login-password">密码</label>
            <div class="password-input">
              <input
                id="login-password"
                ref="passwordInput"
                v-model="form.password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                :autocomplete="registering ? 'new-password' : 'current-password'"
                :placeholder="registering ? '请设置至少 6 位密码' : '请输入密码'"
                required
                :aria-invalid="!!errors.password"
                :aria-describedby="errors.password ? 'password-error' : undefined"
                @input="errors.password = ''; serverError = ''"
              />
              <button type="button" class="password-toggle" :aria-pressed="showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </div>
            <p v-if="errors.password" id="password-error" class="field-error">{{ errors.password }}</p>
          </div>

          <div v-if="needsSmsCode" class="form-field">
            <label for="login-sms-code">短信验证码</label>
            <div class="sms-code-row">
              <input
                id="login-sms-code"
                ref="smsCodeInput"
                v-model="form.smsCode"
                name="smsCode"
                type="text"
                inputmode="numeric"
                autocomplete="one-time-code"
                maxlength="6"
                placeholder="请输入 6 位验证码"
                :aria-invalid="!!errors.smsCode"
                :aria-describedby="errors.smsCode ? 'sms-code-error' : undefined"
                @input="errors.smsCode = ''; serverError = ''"
              />
              <button type="button" class="sms-send" :disabled="sendingSms || countdown > 0 || loading" @click="sendSmsCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : sendingSms ? '发送中…' : '获取验证码' }}
              </button>
            </div>
            <p v-if="errors.smsCode" id="sms-code-error" class="field-error">{{ errors.smsCode }}</p>
          </div>

          <template v-if="registering">
            <div class="form-field">
              <label for="login-confirm">确认密码</label>
              <input
                id="login-confirm"
                ref="confirmInput"
                v-model="form.confirmPassword"
                name="confirmPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="请再次输入密码"
                required
                :aria-invalid="!!errors.confirmPassword"
                :aria-describedby="errors.confirmPassword ? 'confirm-error' : undefined"
                @input="errors.confirmPassword = ''; serverError = ''"
              />
              <p v-if="errors.confirmPassword" id="confirm-error" class="field-error">{{ errors.confirmPassword }}</p>
            </div>
            <div class="form-field">
              <label for="login-nickname">昵称</label>
              <input
                id="login-nickname"
                ref="nicknameInput"
                v-model="form.nickname"
                name="nickname"
                type="text"
                autocomplete="nickname"
                maxlength="32"
                placeholder="给自己起个昵称"
                required
                :aria-invalid="!!errors.nickname"
                :aria-describedby="errors.nickname ? 'nickname-error' : undefined"
                @input="errors.nickname = ''; serverError = ''"
              />
              <p v-if="errors.nickname" id="nickname-error" class="field-error">{{ errors.nickname }}</p>
            </div>
          </template>

          <p v-if="serverError" class="submit-error" role="alert">{{ serverError }}</p>
          <van-button class="submit-button" native-type="submit" color="#07c160" block round :loading="loading" :disabled="loading" :loading-text="registering ? '注册中…' : '登录中…'">
            {{ registering ? '注册并登录' : '登录' }}
          </van-button>
          <p class="switch-mode">
            {{ registering ? '已有账号？' : '还没有账号？' }}
            <button type="button" @click="switchMode">{{ registering ? '返回登录' : '注册账号' }}</button>
          </p>
        </fieldset>
      </form>

      <div class="wechat-info">
        <span class="other-label">其他登录方式</span>
        <button type="button" :disabled="loading" @click="explainWechat">
          <van-icon name="wechat" size="30" color="#07c160" />
          <span>微信登录说明</span>
        </button>
        <p>H5 微信授权暂未配置，请使用手机号登录</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { useUserStore } from '../../stores/user.js'
import { userApi } from '../../api/user.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const registering = ref(false)
const loading = ref(false)
const showPassword = ref(false)
const serverError = ref('')
const smsEnabled = ref(false)
const loginMode = ref('password')
const sendingSms = ref(false)
const countdown = ref(0)
const form = reactive({ phone: '', password: '', confirmPassword: '', nickname: '', smsCode: '' })
const errors = reactive({ phone: '', password: '', confirmPassword: '', nickname: '', smsCode: '' })
const phoneInput = ref(null)
const passwordInput = ref(null)
const confirmInput = ref(null)
const nicknameInput = ref(null)
const smsCodeInput = ref(null)
let countdownTimer = null
const smsMode = computed(() => !registering.value && loginMode.value === 'sms')
const needsSmsCode = computed(() => smsEnabled.value && (registering.value || smsMode.value))

onMounted(async () => {
  try {
    const res = await userApi.getSmsEnabled()
    smsEnabled.value = res?.data?.enabled === true
  } catch {
    smsEnabled.value = false
  }
})

function safeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return '/index'
  try {
    const decoded = decodeURIComponent(value)
    if (decoded.startsWith('//') || /[\\\u0000-\u0020]/.test(decoded)) return '/index'
    const target = router.resolve(value)
    return target.matched.length && target.path !== '/login' ? target.fullPath : '/index'
  } catch {
    return '/index'
  }
}

function switchMode() {
  if (loading.value) return
  registering.value = !registering.value
  form.password = ''
  form.confirmPassword = ''
  form.smsCode = ''
  showPassword.value = false
  serverError.value = ''
  Object.keys(errors).forEach((key) => { errors[key] = '' })
  nextTick(() => phoneInput.value?.focus())
}

function switchLoginMode(mode) {
  if (loading.value || mode === loginMode.value) return
  loginMode.value = mode
  form.smsCode = ''
  form.password = ''
  serverError.value = ''
  Object.keys(errors).forEach((key) => { errors[key] = '' })
  nextTick(() => (mode === 'sms' ? smsCodeInput.value : passwordInput.value)?.focus())
}

function startCountdown() {
  countdown.value = 60
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
      countdown.value = 0
    }
  }, 1000)
}

async function sendSmsCode() {
  if (sendingSms.value || countdown.value > 0 || loading.value) return
  serverError.value = ''
  const phone = form.phone.trim()
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    errors.phone = '请输入正确的 11 位手机号'
    await nextTick()
    phoneInput.value?.focus()
    return
  }
  sendingSms.value = true
  try {
    const scene = registering.value ? 'register' : 'login'
    const res = await userApi.sendSmsCode(phone, scene)
    if (res?.code !== 200) throw new Error(res?.message || '验证码发送失败')
    startCountdown()
    showToast('验证码已发送')
    await nextTick()
    smsCodeInput.value?.focus()
  } catch (error) {
    serverError.value = error?.response?.data?.message || error?.message || '验证码发送失败，请稍后重试'
  } finally {
    sendingSms.value = false
  }
}

async function submit() {
  if (loading.value) return
  serverError.value = ''
  errors.phone = /^1[3-9]\d{9}$/.test(form.phone.trim()) ? '' : '请输入正确的 11 位手机号'
  errors.password = smsMode.value
    ? ''
    : !form.password.trim() ? '请输入密码' : registering.value && form.password.length < 6 ? '密码至少 6 位' : ''
  errors.confirmPassword = registering.value && form.password !== form.confirmPassword ? '两次输入的密码不一致' : ''
  errors.nickname = !registering.value ? '' : !form.nickname.trim() ? '请输入昵称' : form.nickname.trim().length > 32 ? '昵称不能超过 32 个字符' : ''
  errors.smsCode = needsSmsCode.value && !/^\d{6}$/.test(form.smsCode.trim()) ? '请输入 6 位短信验证码' : ''
  const invalid = [
    [errors.phone, phoneInput], [errors.password, passwordInput],
    [errors.confirmPassword, confirmInput], [errors.nickname, nicknameInput],
    [errors.smsCode, smsCodeInput]
  ].find(([message]) => message)
  if (invalid) {
    await nextTick()
    invalid[1].value?.focus()
    return
  }

  loading.value = true
  try {
    const phone = form.phone.trim()
    if (smsMode.value) {
      await userStore.smsLogin({ phone, smsCode: form.smsCode.trim() })
    } else {
      const credentials = { phone, password: form.password }
      if (registering.value) {
        await userStore.register({
          ...credentials,
          nickname: form.nickname.trim(),
          ...(smsEnabled.value ? { smsCode: form.smsCode.trim() } : {})
        })
      } else {
        await userStore.phoneLogin(credentials)
      }
    }
    form.password = ''
    form.confirmPassword = ''
    form.smsCode = ''
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    serverError.value = error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请检查连接后重试' : error?.message) || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function explainWechat() {
  showDialog({ title: '微信登录暂不可用', message: 'H5 微信开放平台授权尚未配置，网页端请使用手机号和密码登录。微信小程序仍使用原有的微信授权流程。', confirmButtonText: '知道了', confirmButtonColor: '#07c160' })
}

onBeforeUnmount(() => {
  clearInterval(countdownTimer)
  countdownTimer = null
  form.password = ''
  form.confirmPassword = ''
  form.smsCode = ''
})
</script>

<style scoped>
.login-page {
  --account-green: #07c160;
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
  background: #fff;
  color: #323233;
  padding: 0 24px calc(28px + env(safe-area-inset-bottom));
}
.login-nav { max-width: 360px; margin: 0 auto; padding: 20px 0 8px; }
.login-nav a { display: inline-flex; align-items: center; gap: 4px; color: #646566; font-size: 14px; text-decoration: none; min-height: 32px; }
.brand { padding: 26px 0 30px; text-align: center; }
.brand-mark { display: inline-grid; place-items: center; width: 64px; height: 64px; border-radius: 17px; background: #ff2300; color: #fff; font-size: 42px; line-height: 1; font-weight: 500; }
.brand h1 { margin: 14px 0 8px; font-size: 25px; font-weight: 600; letter-spacing: 3px; }
.brand p { margin: 0; color: #969799; font-size: 13px; }
.login-panel { width: 100%; max-width: 360px; margin: 0 auto; }
.login-panel h2 { margin: 0 0 24px; font-size: 20px; font-weight: 600; }
fieldset { min-width: 0; border: 0; padding: 0; margin: 0; }
.form-field { margin-bottom: 20px; }
.form-field label { display: block; margin-bottom: 6px; color: #646566; font-size: 13px; }
.form-field input { display: block; width: 100%; min-height: 46px; box-sizing: border-box; padding: 9px 2px; border: 0; border-bottom: 1px solid #ebedf0; border-radius: 0; color: #323233; background: #fff; font: inherit; font-size: 16px; }
.form-field input::placeholder { color: #b1b3b8; }
.form-field input:focus { border-bottom-color: var(--account-green); outline: none; box-shadow: 0 1px 0 var(--account-green); }
.form-field input[aria-invalid='true'] { border-bottom-color: #ee0a24; }
.password-input { display: flex; align-items: center; position: relative; }
.password-input input { padding-right: 52px; }
.password-toggle { position: absolute; right: 0; top: 0; height: 46px; padding: 0 6px; color: #646566; border: 0; background: none; font: inherit; font-size: 13px; cursor: pointer; }
.field-error { margin: 6px 0 0; color: #ee0a24; font-size: 12px; line-height: 1.5; }
.submit-error { padding: 10px 12px; background: #fff2f3; color: #c3293e; border-radius: 6px; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.submit-button { margin-top: 28px; min-height: 46px; font-size: 16px; }
.switch-mode { display: flex; justify-content: center; align-items: center; margin: 12px 0 0; color: #969799; font-size: 13px; }
.switch-mode button { min-height: 44px; padding: 0 5px; border: 0; background: none; color: #079b4e; font: inherit; cursor: pointer; }
.login-tabs { display: flex; gap: 28px; margin: -6px 0 24px; border-bottom: 1px solid #f2f3f5; }
.login-tabs button { position: relative; min-height: 42px; padding: 0 2px 12px; border: 0; background: none; color: #969799; font: inherit; font-size: 15px; cursor: pointer; }
.login-tabs button.active { color: #323233; font-weight: 600; }
.login-tabs button.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 3px; border-radius: 2px; background: var(--account-green); }
.sms-code-row { display: flex; align-items: center; gap: 10px; }
.sms-code-row input { flex: 1; min-width: 0; }
.sms-send { flex-shrink: 0; min-height: 34px; padding: 0 12px; border: 1px solid #07c160; border-radius: 17px; background: #fff; color: #079b4e; font: inherit; font-size: 12px; cursor: pointer; }
.sms-send:disabled { border-color: #d8d8d8; color: #b1b3b8; cursor: not-allowed; }
.wechat-info { text-align: center; margin-top: 30px; }
.other-label { display: flex; gap: 16px; align-items: center; color: #969799; font-size: 12px; }
.other-label::before, .other-label::after { content: ''; flex: 1; height: 1px; background: #ebedf0; }
.wechat-info button { display: inline-flex; flex-direction: column; align-items: center; gap: 7px; margin-top: 18px; padding: 4px 16px; border: 0; background: none; color: #646566; font: inherit; font-size: 12px; cursor: pointer; }
.wechat-info p { color: #969799; font-size: 12px; line-height: 1.6; margin: 12px 0 0; }
button:focus-visible, a:focus-visible { outline: 2px solid #079b4e; outline-offset: 4px; border-radius: 4px; }
button:disabled { cursor: not-allowed; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 360px) { .login-page { padding-left: 20px; padding-right: 20px; } .brand { padding-top: 14px; padding-bottom: 24px; } }
</style>
