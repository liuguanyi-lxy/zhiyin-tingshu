<template>
  <main class="vip-page">
    <van-nav-bar title="VIP 频道" left-text="返回" left-arrow @click-left="goBack" />
    <section class="member-card" aria-label="会员信息">
      <div class="member-avatar"><img v-if="userStore.user.avatarUrl && !avatarFailed" :src="userStore.user.avatarUrl" alt="当前头像" @error="avatarFailed = true" /><van-icon v-else name="user-o" size="28" /></div>
      <div class="member-info"><h1>{{ userStore.isLogin ? userStore.user.nickname || '尚未读取昵称' : '登录后开通会员' }}</h1><p>{{ membershipText }}</p></div>
      <span class="member-tag">VIP</span>
    </section>

    <section class="plans-section" aria-labelledby="plans-heading" :aria-busy="loading">
      <h2 id="plans-heading">会员套餐</h2>
      <p class="section-note">套餐价格和时长以平台配置为准</p>
      <div v-if="loading" class="status-line" role="status"><van-loading size="22">读取套餐中…</van-loading></div>
      <div v-else-if="error" class="error-state" role="alert"><p>{{ error }}</p><van-button plain size="small" color="#b38356" @click="loadPlans">重新加载</van-button></div>
      <van-empty v-else-if="!plans.length" description="暂无可购买的会员套餐" />
      <div v-else class="plan-list" aria-label="选择会员套餐">
        <button v-for="plan in plans" :key="plan.id" class="plan-card" :class="{ selected: selectedId === String(plan.id) }" type="button" :disabled="!isPurchasable(plan)" :aria-pressed="selectedId === String(plan.id)" @click="selectedId = String(plan.id)">
          <div class="plan-title"><strong>{{ plan.name || '未命名套餐' }}</strong><span>{{ validMonths(plan.serviceMonth) ? `${plan.serviceMonth} 个月` : '期限未提供' }}</span></div>
          <div class="plan-price"><span class="price"><small>¥</small>{{ money(plan.discountPrice) }}</span><del v-if="validPrice(plan.price) && validPrice(plan.discountPrice) && Number(plan.price) > Number(plan.discountPrice)">¥{{ money(plan.price) }}</del></div>
          <p v-if="plan.intro" class="plan-intro">{{ plan.intro }}</p>
          <span v-if="!isPurchasable(plan)" class="invalid-plan">套餐信息不完整，暂不可购买</span>
          <van-icon v-if="selectedId === String(plan.id)" class="selected-check" name="checked" color="#b38356" size="20" />
        </button>
      </div>
      <p v-if="selectedPlan?.richIntro" class="plan-description">{{ plainText(selectedPlan.richIntro) }}</p>
    </section>
    <footer class="purchase-footer">
      <div><span>已选套餐</span><strong>{{ selectedPlan?.name || '请选择套餐' }}</strong></div>
      <van-button round color="#dcac80" text-color="#744c28" :disabled="loading || !selectedPlan" @click="purchase">{{ userStore.isLogin ? '确认套餐' : '登录并继续' }}</van-button>
    </footer>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../../api/user.js'
import { ITEM_TYPE } from '../../api/order.js'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const plans = ref([])
const selectedId = ref('')
const avatarFailed = ref(false)
const selectedPlan = computed(() => plans.value.find(plan => String(plan.id) === selectedId.value && isPurchasable(plan)))
const membershipText = computed(() => {
  if (!userStore.isLogin) return '先选择套餐，再登录确认订单'
  if (userStore.user.isVip == null) return '会员状态尚未读取'
  if (Number(userStore.user.isVip) !== 1) return '尚未开通 VIP'
  return userStore.user.vipExpireTime ? `VIP 到期：${String(userStore.user.vipExpireTime).slice(0, 10)}` : 'VIP 会员'
})

function validPrice(value) { return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0 }
function validMonths(value) { return value != null && Number.isInteger(Number(value)) && Number(value) > 0 }
function money(value) { return validPrice(value) ? Number(value).toFixed(2) : '—' }
function isPurchasable(plan) { return /^[1-9]\d*$/.test(String(plan.id)) && validPrice(plan.discountPrice) && validMonths(plan.serviceMonth) }
function plainText(value) { return String(value).replace(/<[^>]*>/g, '').trim() }

async function loadPlans() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await userApi.getVipServiceConfig()
    if (response?.code !== 200 || !Array.isArray(response.data)) throw new Error(response?.message || '服务未返回有效套餐列表')
    plans.value = response.data
    if (!selectedPlan.value) selectedId.value = String(response.data.find(isPurchasable)?.id || '')
  } catch (cause) {
    error.value = cause?.response?.data?.message || (cause?.isAxiosError ? '网络异常，请稍后重试' : cause?.message) || '套餐读取失败，请重试'
  } finally {
    loading.value = false
  }
}

function purchase() {
  if (loading.value || !selectedPlan.value) return
  // 确认页向后端 trade 获取真实结算快照，不从本页传递或修改价格。
  const target = { path: '/confirmOrder', query: { itemType: ITEM_TYPE.Vip, itemId: String(selectedPlan.value.id) } }
  if (userStore.isLogin) router.push(target)
  else router.push({ path: '/login', query: { redirect: router.resolve(target).fullPath } })
}
function goBack() {
  if (router.options.history.state.back) router.back()
  else router.replace('/my')
}
onMounted(loadPlans)
</script>

<style scoped>
.vip-page { min-height: 100vh; color: var(--ink); background: var(--ground); box-sizing: border-box; padding-bottom: calc(108px + env(safe-area-inset-bottom)); }
.member-card { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-5) var(--sp-4); background: var(--surface); border-bottom: 1px solid var(--line); }
.member-avatar { width: 54px; height: 54px; border-radius: var(--r-pill); overflow: hidden; background: var(--vip-soft); color: var(--vip); display: grid; place-items: center; flex-shrink: 0; }
.member-avatar img { width: 100%; height: 100%; object-fit: cover; }
.member-info { flex: 1; min-width: 0; }
.member-info h1 { margin: 0 0 7px; font-size: var(--fs-section); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member-info p { margin: 0; color: var(--ink-3); font-size: var(--fs-meta); line-height: 1.6; }
.member-tag { background: var(--vip-soft); color: var(--vip); font-size: var(--fs-meta); border-radius: var(--r-pill); padding: 3px 9px; }
.plans-section { max-width: 600px; margin: 0 auto; padding: var(--sp-5) var(--sp-4); }
.plans-section h2 { font-size: var(--fs-title); margin: 0 0 var(--sp-2); }
.section-note { margin: 0 0 var(--sp-4); color: var(--ink-3); font-size: var(--fs-meta); }
.plan-list { display: grid; gap: var(--sp-3); }
.plan-card { display: block; position: relative; width: 100%; padding: var(--sp-4); border: 1px solid var(--line); border-radius: var(--r-lg); background: var(--surface); text-align: left; font: inherit; cursor: pointer; }
.plan-card.selected { border-color: var(--vip); background: var(--vip-soft); }
.plan-title { display: flex; justify-content: space-between; gap: var(--sp-3); align-items: baseline; }
.plan-title strong { font-size: var(--fs-body); color: var(--vip); overflow-wrap: anywhere; }
.plan-title > span { flex-shrink: 0; color: var(--ink-3); font-size: var(--fs-meta); }
.plan-price { display: flex; gap: var(--sp-3); align-items: baseline; margin-top: var(--sp-3); padding-right: var(--sp-5); }
.price { color: var(--vip); font-size: var(--fs-display); font-weight: 600; }
.price small { margin-right: 4px; font-size: var(--fs-body); }
.plan-price del { color: var(--ink-4); font-size: var(--fs-meta); }
.plan-intro, .plan-description { margin: var(--sp-3) 0 0; color: var(--ink-3); font-size: var(--fs-meta); line-height: 1.8; overflow-wrap: anywhere; }
.plan-description { margin-top: var(--sp-4); white-space: pre-wrap; }
.invalid-plan { display: block; color: var(--ink-3); font-size: var(--fs-meta); margin-top: var(--sp-2); }
.selected-check { position: absolute; right: var(--sp-4); bottom: var(--sp-4); }
.plan-card:disabled { opacity: 0.6; cursor: not-allowed; }
.purchase-footer { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: min(100%, 480px); box-sizing: border-box; padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom)); display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); background: var(--surface); border-top: 1px solid var(--line); z-index: 10; }
.purchase-footer > div { min-width: 0; flex: 1; }
.purchase-footer span { display: block; color: var(--ink-3); font-size: var(--fs-micro); }
.purchase-footer strong { display: block; margin-top: 5px; color: var(--vip); font-size: var(--fs-meta); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.purchase-footer .van-button { min-width: 120px; flex-shrink: 0; }
.status-line, .error-state { text-align: center; padding: var(--sp-5) var(--sp-2); color: var(--ink-3); font-size: var(--fs-body); line-height: 1.6; }
button:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
</style>
