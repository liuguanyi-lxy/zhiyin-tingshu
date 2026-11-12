import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '../api/user.js'

const TOKEN_KEY = 'tingshu_token'
const USER_KEY = 'tingshu_user'
const PROFILE_FIELDS = ['id', 'nickname', 'avatarUrl', 'isVip', 'vipExpireTime']

function readStorage(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key, value) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // 禁用存储时仍保留内存会话，不记录凭证或密码。
  }
}

function publicProfile(info) {
  if (!info || typeof info !== 'object' || Array.isArray(info)) return {}
  return Object.fromEntries(PROFILE_FIELDS.filter((key) => info[key] !== undefined).map((key) => [key, info[key]]))
}

function readProfile() {
  try {
    return publicProfile(JSON.parse(readStorage(USER_KEY) || '{}'))
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(readStorage(TOKEN_KEY) || '')
  const user = ref(token.value ? readProfile() : {})
  const amount = ref(0)
  const isLogin = computed(() => !!token.value)
  let sessionVersion = 0

  function setToken(value) {
    sessionVersion += 1
    token.value = typeof value === 'string' ? value.trim() : ''
    writeStorage(TOKEN_KEY, token.value || null)
  }

  function setUserInfo(info) {
    // 仅持久化公开资料；密码和确认密码只存在于登录组件内存中。
    user.value = publicProfile(info)
    writeStorage(USER_KEY, JSON.stringify(user.value))
  }

  function clear() {
    sessionVersion += 1
    token.value = ''
    user.value = {}
    amount.value = 0
    writeStorage(TOKEN_KEY, null)
    writeStorage(USER_KEY, null)
  }

  async function getUserInfo() {
    const requestToken = token.value
    if (!requestToken) throw new Error('请先登录')
    const res = await userApi.getUserInfo()
    if (token.value !== requestToken) throw new Error('登录状态已变化，请重试')
    if (res?.code !== 200 || !res.data || res.data.id == null) {
      throw new Error(res?.message || '未能获取用户资料，请重试')
    }
    setUserInfo(res.data)
    return res
  }

  async function authenticate(request) {
    const version = sessionVersion
    const res = await request()
    if (version !== sessionVersion) throw new Error('登录状态已变化，请重试')
    if (res?.code !== 200 || typeof res.data?.token !== 'string' || !res.data.token.trim()) {
      throw new Error(res?.message || '登录未返回有效凭证，请重试')
    }
    setUserInfo({})
    amount.value = 0
    setToken(res.data.token)
    const issuedToken = token.value
    try {
      await getUserInfo()
      return res
    } catch (error) {
      // 资料拉取失败不能显示登录成功，也不能留下半登录状态。
      if (token.value === issuedToken) clear()
      throw error
    }
  }

  function phoneLogin(credentials) {
    return authenticate(() => userApi.phoneLogin(credentials))
  }

  function smsLogin(credentials) {
    return authenticate(() => userApi.smsLogin(credentials))
  }

  async function register(credentials) {
    let accountCreated = false
    try {
      return await authenticate(async () => {
        const res = await userApi.register(credentials)
        accountCreated = res?.code === 200
        return res
      })
    } catch (error) {
      if (accountCreated) {
        const failure = new Error('账号已创建，但未能完成登录。请切换“返回登录”后重试。')
        failure.cause = error
        throw failure
      }
      throw error
    }
  }

  /** 保留开发接口供旧调用兼容，普通登录页不提供该入口。 */
  function devLogin(userId) {
    return authenticate(() => userApi.devLogin(userId))
  }

  return { token, user, amount, isLogin, setToken, setUserInfo, clear, devLogin, phoneLogin, smsLogin, register, getUserInfo }
})
