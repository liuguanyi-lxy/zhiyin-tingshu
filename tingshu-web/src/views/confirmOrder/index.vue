<template>
  <main class="confirm-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="goBack" />
    <div v-if="loading" class="loading-state" role="status"><van-loading vertical>正在获取订单价格</van-loading></div>
    <section v-else-if="error && !quote" class="state-panel" role="alert">
      <van-empty image="error" :description="error" />
      <van-button v-if="validSelection" plain type="primary" @click="loadQuote">重新获取</van-button>
      <van-button v-else plain type="primary" @click="router.push('/index')">返回选择商品</van-button>
    </section>
    <template v-else-if="quote">
      <div class="confirm-body">
        <div class="confirm-main">
          <section class="goods-card">
            <div class="section-title">{{ typeName }}订单</div>
            <article v-for="(item, index) in quote.orderDetailVoList" :key="`${item.itemId}-${index}`" class="goods">
              <van-image width="75" height="75" radius="6" fit="cover" :src="item.itemUrl" alt="商品封面" />
              <div class="goods-info">
                <h2>{{ item.itemName }}</h2>
                <p class="goods-tip">以订单明细及平台服务规则为准</p>
                <div class="goods-price"><strong class="num">￥{{ formatAmount(item.itemPrice) }}</strong><span class="muted">× 1</span></div>
              </div>
            </article>
            <dl class="totals">
              <div><dt>商品总价</dt><dd class="num">￥{{ formatAmount(quote.originalAmount) }}</dd></div>
              <div v-if="Number(quote.derateAmount) > 0"><dt>优惠减免</dt><dd class="num accent">-￥{{ formatAmount(quote.derateAmount) }}</dd></div>
              <div class="subtotal"><dt>小计</dt><dd class="num accent">￥{{ formatAmount(quote.orderAmount) }}</dd></div>
            </dl>
          </section>
          <p class="quote-note">金额、优惠和签名均由后端生成。创建订单不代表已经支付。</p>
        </div>

        <aside class="confirm-aside">
          <section class="account-card">
            <div><span class="muted">购买账号</span><strong>{{ userStore.user.nickname || '当前登录账号' }}</strong></div>
            <div class="balance"><span class="muted">账号余额</span><strong class="num">{{ balanceLoading ? '读取中…' : `￥${formatAmount(balance)}` }}</strong></div>
            <div v-if="balanceError" class="balance-error" role="alert">
              {{ balanceError }} <van-button size="mini" plain @click="loadBalance">重试</van-button>
            </div>
          </section>

          <section v-if="selection.itemType === ITEM_TYPE.Track" class="selection-card">
            <label for="track-plan">购买范围</label>
            <select v-if="trackPlans.length" id="track-plan" :value="selection.trackCount" :disabled="submitting" @change="changePlan">
              <option v-for="plan in trackPlans" :key="plan.trackCount" :value="plan.trackCount">{{ plan.name }}</option>
            </select>
            <span v-else>{{ selection.trackCount === 0 ? '本集' : `后 ${selection.trackCount} 集` }}</span>
            <p v-if="plansError" class="muted">{{ plansError }}；当前范围以本次后端结算明细为准。</p>
          </section>

          <section class="payment-card">
            <h2 class="section-title">支付方式</h2>
            <van-radio-group v-model="selectedPayWay">
              <van-cell title="微信扫码支付" label="创建订单后，在订单详情中获取付款码" clickable @click="selectedPayWay = PAY_WAY.WeChat">
                <template #right-icon><van-radio :name="PAY_WAY.WeChat" checked-color="#007aff" /></template>
              </van-cell>
              <van-cell title="账户余额" :label="BALANCE_PAYMENT_BLOCKER">
                <template #right-icon><van-radio :name="PAY_WAY.Balance" disabled /></template>
              </van-cell>
            </van-radio-group>
            <p class="payment-notice" role="note">{{ NATIVE_PAYMENT_NOTICE }}</p>
          </section>

          <section v-if="submitError" class="submit-error" role="alert">
            <p>{{ submitError }}</p>
            <p v-if="attempted">提交结果可能尚未同步。请先检查订单，勿反复创建或付款。</p>
            <van-button size="small" plain type="primary" @click="router.push('/order')">查看我的订单</van-button>
          </section>
        </aside>
      </div>
      <footer class="checkout-bar">
        <div class="checkout-bar-inner">
          <div class="checkout-total"><span>合计：<strong class="num">￥{{ formatAmount(quote.orderAmount) }}</strong></span><small v-if="Number(quote.derateAmount) > 0">已优惠￥{{ formatAmount(quote.derateAmount) }}</small></div>
          <van-button type="primary" :loading="submitting" :disabled="submitting || attempted" @click="submitOrder">
            {{ attempted && !submitting ? '请先检查订单' : '确认并创建订单' }}
          </van-button>
        </div>
      </footer>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '../../stores/user.js'
import { orderApi, ITEM_TYPE, PAY_WAY, formatAmount, requestError } from '../../api/order.js'
import { BALANCE_PAYMENT_BLOCKER, NATIVE_PAYMENT_NOTICE } from '../../api/payment.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const quote = ref(null)
const loading = ref(false)
const error = ref('')
const balance = ref(null)
const balanceLoading = ref(false)
const balanceError = ref('')
const trackPlans = ref([])
const plansError = ref('')
const submitting = ref(false)
const attempted = ref(false)
const submitError = ref('')
const selectedPayWay = ref(PAY_WAY.WeChat)
let version = 0
let balanceVersion = 0
let disposed = false
// 与响应式展示分开保存原始 JSON 快照；禁止重算价格、重签名或替换 payWay。
let signedSnapshot = null
const scalar = (value) => typeof value === 'string' ? value : ''
const selection = computed(() => ({
  itemType: scalar(route.query.itemType),
  itemId: scalar(route.query.itemId),
  trackCount: route.query.trackCount == null ? 0 : Number(scalar(route.query.trackCount))
}))
const validSelection = computed(() => {
  const { itemType, itemId, trackCount } = selection.value
  return Object.values(ITEM_TYPE).includes(itemType) && /^[1-9]\d{0,18}$/.test(itemId)
    && BigInt(itemId) <= 9223372036854775807n
    && (itemType !== ITEM_TYPE.Track || (Number.isSafeInteger(trackCount) && trackCount >= 0 && trackCount <= 2147483647))
})
const typeName = computed(() => ({ '1001': '专辑', '1002': '声音', '1003': 'VIP 会员' }[selection.value.itemType] || '商品'))

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/order')
}
async function loadBalance() {
  const current = ++balanceVersion
  balanceLoading.value = true
  balanceError.value = ''
  balance.value = null
  try {
    const res = await orderApi.getAccountBalance()
    if (disposed || current !== balanceVersion) return
    if (res.data == null || res.data === '' || !Number.isFinite(Number(res.data))) throw new Error('余额返回异常')
    balance.value = res.data
  } catch (failure) {
    if (!disposed && current === balanceVersion) balanceError.value = requestError(failure, '余额读取失败')
  } finally {
    if (!disposed && current === balanceVersion) balanceLoading.value = false
  }
}
async function loadQuote() {
  const current = ++version
  quote.value = null
  signedSnapshot = null
  error.value = ''
  submitError.value = ''
  attempted.value = false
  trackPlans.value = []
  plansError.value = ''
  if (!validSelection.value) {
    loading.value = false
    error.value = '购买信息无效。请选择专辑、声音或 VIP 套餐后重新进入。'
    return
  }
  loading.value = true
  const params = { itemType: selection.value.itemType, itemId: selection.value.itemId }
  if (params.itemType === ITEM_TYPE.Track) params.trackCount = selection.value.trackCount
  try {
    const res = await orderApi.getSubmitOrderInfo(params)
    if (disposed || current !== version) return
    const data = res.data
    if (!data?.tradeNo || !data.sign || data.timestamp == null || data.itemType !== params.itemType
      || !Array.isArray(data.orderDetailVoList) || !data.orderDetailVoList.length
      || [data.originalAmount, data.orderAmount, data.derateAmount].some((value) => value == null || value === '' || !Number.isFinite(Number(value)))
      || Number(data.orderAmount) <= 0 || data.payWay !== PAY_WAY.WeChat) {
      throw new Error('后端结算信息不完整或支付方式不受支持，暂不能创建订单')
    }
    signedSnapshot = JSON.stringify(data)
    quote.value = data
  } catch (failure) {
    if (!disposed && current === version) error.value = requestError(failure, '获取结算信息失败，请重试')
  } finally {
    if (!disposed && current === version) loading.value = false
  }
  if (params.itemType === ITEM_TYPE.Track && current === version && !disposed) {
    try {
      const res = await orderApi.getTrackSettingList(params.itemId)
      if (disposed || current !== version) return
      if (!Array.isArray(res.data)) throw new Error('购买范围读取失败')
      trackPlans.value = res.data.filter((plan) => Number.isSafeInteger(plan.trackCount) && plan.trackCount >= 0)
      if (!trackPlans.value.some((plan) => plan.trackCount === params.trackCount)) trackPlans.value = []
    } catch (failure) {
      if (!disposed && current === version) plansError.value = requestError(failure, '其他购买范围暂不可用')
    }
  }
}
function changePlan(event) {
  const count = Number(event.target.value)
  if (trackPlans.value.some((plan) => plan.trackCount === count)) {
    router.replace({ path: '/confirmOrder', query: { ...route.query, trackCount: String(count) } })
  }
}
async function submitOrder() {
  if (submitting.value || attempted.value || !signedSnapshot || loading.value) return
  const current = version
  const snapshot = signedSnapshot
  submitting.value = true
  submitError.value = ''
  try {
    try {
      await showConfirmDialog({
        title: '创建待支付订单',
        message: '当前扫码支付通道尚未接通，余额支付也暂不可用。继续只会创建待支付订单，不会扣款，是否继续？',
        confirmButtonText: '确认创建', confirmButtonColor: '#007aff'
      })
    } catch { return }
    if (disposed || current !== version) return
    attempted.value = true
    const res = await orderApi.submitOrder(JSON.parse(snapshot))
    if (disposed || current !== version) return
    if (typeof res.data?.orderNo !== 'string' || !res.data.orderNo.trim()) throw new Error('提交未返回订单号，请先在我的订单中确认结果')
    await router.replace({ name: 'orderDetail', params: { orderNo: res.data.orderNo } })
  } catch (failure) {
    if (!disposed && current === version) submitError.value = requestError(failure, '订单提交失败，请先检查我的订单')
  } finally {
    if (!disposed) submitting.value = false
  }
}
watch(() => [route.query.itemType, route.query.itemId, route.query.trackCount], loadQuote, { immediate: true })
onMounted(loadBalance)
onBeforeUnmount(() => { disposed = true; version += 1; balanceVersion += 1 })
</script>

<style scoped>
.confirm-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: calc(90px + env(safe-area-inset-bottom));
  font-size: var(--fs-body);
}

.loading-state {
  padding: 80px 0;
}

.state-panel {
  text-align: center;
  padding-bottom: var(--sp-4);
}

.confirm-body {
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-3);
}

.account-card,
.selection-card,
.goods-card,
.payment-card,
.submit-error {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.account-card {
  padding: var(--sp-4);
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--sp-3);
}

.account-card strong {
  display: block;
  margin-top: var(--sp-2);
  font-size: var(--fs-section);
  color: var(--ink);
  overflow-wrap: anywhere;
}

.balance {
  text-align: right;
}

.muted {
  color: var(--ink-3);
  font-size: var(--fs-meta);
}

.balance-error {
  flex-basis: 100%;
  color: var(--paid);
  font-size: var(--fs-meta);
}

.selection-card {
  padding: var(--sp-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

.selection-card label {
  font-size: var(--fs-body);
  color: var(--ink-2);
}

.selection-card select {
  min-height: 38px;
  max-width: 65%;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  color: var(--ink);
  background: var(--surface);
  padding: 0 var(--sp-3);
  font: inherit;
}

.selection-card p {
  flex-basis: 100%;
  margin: 0;
  line-height: 1.6;
}

.goods-card {
  padding: var(--sp-4);
}

.goods-card > .section-title {
  margin-bottom: var(--sp-3);
}

.goods {
  display: flex;
  align-items: center;
  padding: var(--sp-2) 0;
  gap: var(--sp-3);
}

.goods :deep(.van-image) {
  flex-shrink: 0;
  border-radius: var(--r-md);
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-info h2 {
  font-size: var(--fs-body);
  font-weight: 500;
  line-height: 1.5;
  margin: 0;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.goods-tip {
  color: var(--ink-4);
  font-size: var(--fs-micro);
  margin: 6px 0;
}

.goods-price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
}

.goods-price strong {
  font-size: var(--fs-section);
  color: var(--ink);
}

.totals {
  border-top: 1px solid var(--line-soft);
  margin: var(--sp-3) 0 0;
  padding-top: var(--sp-2);
}

.totals div {
  display: flex;
  justify-content: space-between;
  padding: var(--sp-2) 0;
  gap: var(--sp-3);
  color: var(--ink-2);
}

.totals dd {
  margin: 0;
}

.totals .subtotal {
  justify-content: flex-end;
  font-weight: 600;
  padding: var(--sp-3) 0 0;
  color: var(--ink);
}

.accent {
  color: var(--brand);
}

.payment-card {
  overflow: hidden;
}

.payment-card > .section-title {
  padding: var(--sp-4) var(--sp-4) var(--sp-2);
  margin: 0;
}

.payment-card :deep(.van-cell) {
  padding-left: var(--sp-4);
  padding-right: var(--sp-4);
}

.payment-notice {
  font-size: var(--fs-meta);
  line-height: 1.8;
  color: var(--vip);
  margin: 0;
  padding: var(--sp-3) var(--sp-4) var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.submit-error {
  color: var(--paid);
  margin: 0;
  padding: var(--sp-4);
  line-height: 1.7;
  font-size: var(--fs-body);
}

.submit-error p {
  margin: 0 0 var(--sp-2);
}

.quote-note {
  margin: 0;
  color: var(--ink-3);
  line-height: 1.7;
  font-size: var(--fs-meta);
  padding: 0 var(--sp-1);
}

.checkout-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  box-sizing: border-box;
  background: var(--surface);
  border-top: 1px solid var(--line);
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  z-index: 10;
}

.checkout-bar-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-3);
}

.checkout-bar strong {
  color: var(--paid);
  font-size: var(--fs-title);
}

.checkout-bar small {
  display: block;
  color: var(--ink-3);
  font-size: var(--fs-micro);
  margin-top: 4px;
}

.checkout-bar :deep(.van-button) {
  min-height: 44px;
  flex-shrink: 0;
}

select:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

@media (max-width: 350px) {
  .checkout-bar {
    padding-left: 10px;
    padding-right: 10px;
  }

  .checkout-bar strong {
    font-size: var(--fs-section);
  }

  .checkout-bar :deep(.van-button) {
    padding: 0 10px;
  }
}

/* ============================================================
   桌面端：左商品、右订单信息
   ============================================================ */
@media (min-width: 960px) {
  .confirm-page {
    padding-bottom: 96px;
  }

  .confirm-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .confirm-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .confirm-body {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-5);
    grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
    gap: var(--sp-4);
    align-items: start;
  }

  .confirm-aside {
    display: grid;
    gap: var(--sp-4);
    position: sticky;
    top: 80px;
  }

  .checkout-bar {
    left: var(--sidebar-w);
    right: 0;
    transform: none;
    max-width: none;
    padding: var(--sp-3) var(--sp-5);
  }

  .checkout-bar-inner {
    max-width: var(--shell-max);
    margin: 0 auto;
  }

  .checkout-bar :deep(.van-button) {
    min-width: 180px;
  }
}
</style>
