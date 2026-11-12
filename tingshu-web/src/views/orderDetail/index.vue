<template>
  <main class="detail-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="goBack" />
    <div v-if="loading" class="loading-state" role="status"><van-loading vertical>正在加载订单</van-loading></div>
    <section v-else-if="error" class="state-panel" role="alert">
      <van-empty image="error" :description="error" />
      <van-button type="primary" plain @click="loadOrder">重新加载</van-button>
      <van-button plain @click="router.replace('/order')">我的订单</van-button>
    </section>
    <template v-else-if="order">
      <header class="status-banner">
        <span
          class="tag status-tag"
          :class="{
            'status-paid': order.orderStatus === ORDER_STATUS.Paid,
            'status-unpaid': order.orderStatus === ORDER_STATUS.Unpaid,
            'status-cancelled': order.orderStatus === ORDER_STATUS.Cancelled
          }"
        >{{ orderStatusName(order) }}</span>
        <h1>{{ statusDescription }}</h1>
      </header>
      <div class="detail-body">
        <section class="order-card">
          <h2 class="section-title">商品明细</h2>
          <div class="goods-list">
            <article v-for="(item, index) in order.orderDetailList || []" :key="item.id || `${item.itemId}-${index}`" class="goods">
              <van-image width="75" height="75" fit="cover" radius="6" :src="item.itemUrl" alt="商品封面" />
              <div class="goods-info">
                <h3>{{ item.itemName }}</h3>
                <p class="goods-price"><strong class="num">￥{{ formatAmount(item.itemPrice) }}</strong><span class="quantity">× 1</span></p>
              </div>
            </article>
            <van-empty v-if="!order.orderDetailList?.length" description="暂无商品明细，请刷新确认" image-size="70" />
          </div>
        </section>
        <section class="order-card">
          <h2 class="section-title">订单信息</h2>
          <dl class="order-meta">
            <div><dt>商品总价</dt><dd class="num">￥{{ formatAmount(order.originalAmount) }}</dd></div>
            <div v-if="Number(order.derateAmount) > 0"><dt>优惠减免</dt><dd class="num">-￥{{ formatAmount(order.derateAmount) }}</dd></div>
            <div><dt>{{ order.orderStatus === ORDER_STATUS.Paid ? '实付款' : '应付款' }}</dt><dd class="amount num">￥{{ formatAmount(order.orderAmount) }}</dd></div>
            <div><dt>订单号</dt><dd>{{ order.orderNo }}</dd></div>
            <div><dt>付款方式</dt><dd>{{ order.payWayName || payWayName }}</dd></div>
            <div><dt>下单时间</dt><dd>{{ order.createTime || '—' }}</dd></div>
          </dl>
          <div class="detail-actions">
            <template v-if="order.orderStatus === ORDER_STATUS.Unpaid">
              <p class="channel-warning">{{ NATIVE_PAYMENT_NOTICE }}</p>
              <van-button type="primary" block :disabled="order.payWay !== PAY_WAY.WeChat" @click="openPayment">选择支付方式</van-button>
              <p v-if="order.payWay !== PAY_WAY.WeChat" class="channel-warning">{{ BALANCE_PAYMENT_BLOCKER }}</p>
              <van-button plain block :loading="checking" :disabled="checking" @click="verifyPayment">已付款，核验状态</van-button>
            </template>
            <van-button v-else-if="order.orderStatus === ORDER_STATUS.Paid" type="primary" plain block @click="showResult">查看支付结果</van-button>
            <p v-else class="muted">{{ order.orderStatus === ORDER_STATUS.Cancelled ? '订单已取消，不能继续支付。' : '订单状态尚未确认，请刷新后再操作。' }}</p>
            <van-button plain block @click="loadOrder">刷新订单</van-button>
            <p v-if="checkError" role="alert" class="inline-error">{{ checkError }}</p>
          </div>
        </section>
      </div>
    </template>

    <van-popup v-model:show="showPayment" position="bottom" round closeable :close-on-click-overlay="false" class="payment-popup">
      <section class="payment-content" aria-labelledby="payment-title">
        <h2 id="payment-title">选择支付方式</h2>
        <p class="payment-total">应付 <strong>￥{{ formatAmount(order?.orderAmount) }}</strong></p>
        <van-radio-group v-model="payChoice" :disabled="phase === 'creating' || phase === 'polling'">
          <van-cell title="微信扫码支付" label="需另一台设备使用微信扫描付款码" clickable @click="selectNative">
            <template #right-icon><van-radio :name="PAY_WAY.WeChat" checked-color="#007aff" /></template>
          </van-cell>
          <van-cell title="账户余额" :label="balanceLoading ? '余额读取中…' : `可用余额 ￥${formatAmount(balance)} · 暂不可用`">
            <template #right-icon><van-radio :name="PAY_WAY.Balance" disabled /></template>
          </van-cell>
        </van-radio-group>
        <p class="channel-warning">{{ BALANCE_PAYMENT_BLOCKER }}</p>
        <p v-if="balanceError" class="inline-error" role="alert">{{ balanceError }} <van-button size="mini" plain @click="loadBalance">重试</van-button></p>
        <p class="channel-warning">{{ NATIVE_PAYMENT_NOTICE }}</p>
        <div v-if="phase === 'creating'" class="qr-state" role="status"><van-loading vertical>正在请求微信付款码</van-loading></div>
        <div v-if="phase === 'polling' && qrImage" class="qr-state" aria-live="polite">
          <img :src="qrImage" class="qr-image" width="220" height="220" alt="微信支付二维码，仅由后端返回的真实 codeUrl 生成" />
          <p>请使用另一台设备的微信扫一扫</p>
          <p class="muted">本次付款码最多展示 3 分钟；关闭或离开页面将停止查询。</p>
          <van-loading size="16">等待微信确认支付</van-loading>
        </div>
        <div v-if="payError" class="inline-error" role="alert">{{ payError }}</div>
        <van-button v-if="phase !== 'polling'" type="primary" block :loading="phase === 'creating'" :disabled="!NATIVE_ENDPOINT_AVAILABLE || phase === 'creating' || nativeRequestInFlight || checking" @click="createNativePayment">
          {{ !NATIVE_ENDPOINT_AVAILABLE ? '扫码支付暂不可用' : phase === 'expired' ? '重新获取付款码' : '获取微信付款码' }}
        </van-button>
        <van-button plain block @click="closePayment">关闭</van-button>
      </section>
    </van-popup>
  </main>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount, onDeactivated } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import QRCode from 'qrcode'
import { orderApi, ORDER_STATUS, PAY_WAY, formatAmount, orderStatusName, requestError } from '../../api/order.js'
import { paymentApi, PAYMENT_TYPE, NATIVE_ENDPOINT_AVAILABLE, BALANCE_PAYMENT_BLOCKER, NATIVE_PAYMENT_NOTICE, paymentError } from '../../api/payment.js'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(false)
const error = ref('')
const checking = ref(false)
const checkError = ref('')
const showPayment = ref(false)
const payChoice = ref(PAY_WAY.WeChat)
const balance = ref(null)
const balanceLoading = ref(false)
const balanceError = ref('')
const phase = ref('idle')
const qrImage = ref('')
const payError = ref('')
const nativeRequestInFlight = ref(false)
let disposed = false
let orderVersion = 0
let balanceVersion = 0
let sessionVersion = 0
let pollTimer = null
let expiryTimer = null
let pollInFlight = false
let deadline = 0
const POLL_DELAY = 2500
const MAX_PAYMENT_TIME = 3 * 60 * 1000
const orderNo = computed(() => typeof route.params.orderNo === 'string' ? route.params.orderNo.trim() : '')
const payWayName = computed(() => ({ '1101': '微信支付', '1103': '余额支付' }[order.value?.payWay] || '待确认'))
const statusDescription = computed(() => ({
  '0901': '订单尚未支付，请核对金额后选择付款方式。',
  '0902': '后端已确认订单支付完成。',
  '0903': '订单已关闭，请勿继续付款。'
}[order.value?.orderStatus] || '请刷新确认订单状态。'))

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/order')
}
function stopNative() {
  sessionVersion += 1
  clearTimeout(pollTimer)
  clearTimeout(expiryTimer)
  pollTimer = null
  expiryTimer = null
  deadline = 0
  qrImage.value = ''
}
function closePayment() {
  stopNative()
  showPayment.value = false
  phase.value = 'idle'
}
function endSession(message, state = 'failed') {
  stopNative()
  phase.value = state
  payError.value = message
}
function isLive(version, currentOrderNo) {
  return !disposed && version === sessionVersion && showPayment.value && !document.hidden
    && currentOrderNo === orderNo.value && deadline > Date.now()
}
async function loadOrder() {
  closePayment()
  const current = ++orderVersion
  const number = orderNo.value
  loading.value = true
  error.value = ''
  checkError.value = ''
  order.value = null
  if (!number) {
    error.value = '缺少订单号，请从我的订单重新进入'
    loading.value = false
    return
  }
  try {
    const res = await orderApi.getOrderInfo(number)
    if (disposed || current !== orderVersion || number !== orderNo.value) return
    if (!res.data || res.data.orderNo !== number || !res.data.orderStatus) throw new Error('订单不存在或返回信息不完整')
    order.value = res.data
  } catch (failure) {
    if (!disposed && current === orderVersion) error.value = requestError(failure, '订单加载失败，请重试')
  } finally {
    if (!disposed && current === orderVersion) loading.value = false
  }
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
function selectNative() {
  if (!['creating', 'polling'].includes(phase.value)) payChoice.value = PAY_WAY.WeChat
}
function openPayment() {
  if (document.hidden || order.value?.orderStatus !== ORDER_STATUS.Unpaid || order.value.payWay !== PAY_WAY.WeChat) return
  stopNative()
  phase.value = 'idle'
  payError.value = ''
  showPayment.value = true
  loadBalance()
}
function showResult() {
  return router.replace({ path: '/paySuccess', query: { orderNo: orderNo.value, paymentType: PAYMENT_TYPE.Order } })
}
async function verifyPayment() {
  if (checking.value || pollInFlight || phase.value === 'creating' || phase.value === 'polling' || document.hidden) return
  const current = orderVersion
  checking.value = true
  checkError.value = ''
  try {
    // 结果页会重新查询订单；此处不凭“已付款”点击直接显示成功。
    await showResult()
  } catch (failure) {
    if (!disposed && current === orderVersion) checkError.value = requestError(failure, '无法打开支付核验页')
  } finally {
    if (!disposed) checking.value = false
  }
}
async function createNativePayment() {
  if (!NATIVE_ENDPOINT_AVAILABLE) {
    payError.value = NATIVE_PAYMENT_NOTICE
    return
  }
  if (nativeRequestInFlight.value || phase.value === 'polling' || checking.value || document.hidden || !showPayment.value) return
  const number = orderNo.value
  if (order.value?.orderStatus !== ORDER_STATUS.Unpaid || order.value.payWay !== PAY_WAY.WeChat) return
  stopNative()
  const current = sessionVersion
  deadline = Date.now() + MAX_PAYMENT_TIME
  expiryTimer = setTimeout(() => {
    if (!disposed && current === sessionVersion) endSession('本次付款码已过期，已停止查询。请先确认订单状态，再重新获取付款码。', 'expired')
  }, MAX_PAYMENT_TIME)
  phase.value = 'creating'
  payError.value = ''
  nativeRequestInFlight.value = true
  try {
    // 先读取最新订单，避免对已经取消或已支付的旧详情发起支付。
    const latest = await orderApi.getOrderInfo(number)
    if (!isLive(current, number)) return
    if (!latest.data || latest.data.orderNo !== number) throw new Error('订单信息异常，请刷新后重试')
    order.value = latest.data
    if (latest.data.orderStatus === ORDER_STATUS.Paid) {
      closePayment()
      await showResult()
      return
    }
    if (latest.data.orderStatus !== ORDER_STATUS.Unpaid || latest.data.payWay !== PAY_WAY.WeChat) {
      endSession('订单已取消、状态已变化或不支持微信支付，不能继续付款。')
      return
    }
    const res = await paymentApi.createNative(PAYMENT_TYPE.Order, number)
    if (!isLive(current, number)) return
    const codeUrl = res.data?.codeUrl
    if (typeof codeUrl !== 'string' || !/^weixin:\/\/wxpay\/bizpayurl\?/i.test(codeUrl.trim())) {
      throw new Error('支付服务未返回有效 codeUrl，不能展示付款码')
    }
    const image = await QRCode.toDataURL(codeUrl.trim(), { width: 220, margin: 2, errorCorrectionLevel: 'M' })
    if (!isLive(current, number)) return
    qrImage.value = image
    phase.value = 'polling'
    // 只有真实 codeUrl 已显示且弹层可见，才启动串行轮询。
    pollTimer = setTimeout(() => pollPayment(current, number), POLL_DELAY)
  } catch (failure) {
    if (isLive(current, number)) endSession(paymentError(failure))
  } finally {
    nativeRequestInFlight.value = false
  }
}
async function pollPayment(current, number) {
  if (!isLive(current, number)) return
  if (pollInFlight) {
    pollTimer = setTimeout(() => pollPayment(current, number), POLL_DELAY)
    return
  }
  pollInFlight = true
  try {
    const res = await paymentApi.queryPayStatus(number)
    if (!isLive(current, number)) return
    if (res.data === true) {
      closePayment()
      await showResult()
      return
    }
    if (res.data !== false) throw new Error('支付状态返回异常，不能确认支付结果')
    const latest = await orderApi.getOrderInfo(number)
    if (!isLive(current, number)) return
    if (!latest.data || latest.data.orderNo !== number) throw new Error('订单状态读取异常')
    order.value = latest.data
    if (latest.data.orderStatus === ORDER_STATUS.Paid) {
      closePayment()
      await showResult()
      return
    }
    if (latest.data.orderStatus !== ORDER_STATUS.Unpaid) {
      endSession('订单已关闭或状态已变化，付款码已停用，请勿继续扫码。')
      return
    }
  } catch (failure) {
    if (isLive(current, number)) endSession(paymentError(failure))
    return
  } finally {
    pollInFlight = false
  }
  if (isLive(current, number)) pollTimer = setTimeout(() => pollPayment(current, number), POLL_DELAY)
}
function onVisibilityChange() {
  if (document.hidden) closePayment()
}
watch(orderNo, loadOrder, { immediate: true })
watch(showPayment, (visible) => { if (!visible) stopNative() })
onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('pagehide', closePayment)
})
onBeforeRouteLeave(closePayment)
onDeactivated(closePayment)
onBeforeUnmount(() => {
  disposed = true
  orderVersion += 1
  balanceVersion += 1
  closePayment()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('pagehide', closePayment)
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-6);
  font-size: var(--fs-body);
}

.loading-state {
  padding: 80px 0;
}

.state-panel {
  text-align: center;
}

.state-panel :deep(.van-button) {
  margin: 0 5px;
}

.status-banner {
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  padding: var(--sp-5) var(--sp-4);
}

.status-tag {
  margin-bottom: var(--sp-2);
}

.status-tag.status-paid {
  color: var(--brand);
  background: var(--brand-soft);
}

.status-tag.status-unpaid {
  color: var(--paid);
  background: var(--paid-soft);
}

.status-tag.status-cancelled {
  color: var(--ink-3);
  background: var(--line-soft);
}

.status-banner h1 {
  margin: 0;
  font-size: var(--fs-title);
  line-height: 1.5;
  font-weight: 600;
  color: var(--ink);
}

.detail-body {
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-3);
}

.order-card {
  position: relative;
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--surface);
  border: 1px solid var(--line);
}

.order-card > .section-title {
  margin-bottom: var(--sp-3);
}

.goods-list {
  max-height: 340px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.goods {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.goods:last-child {
  margin-bottom: 0;
}

.goods :deep(.van-image) {
  flex-shrink: 0;
  border-radius: var(--r-md);
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-info h3 {
  font-size: var(--fs-body);
  line-height: 1.5;
  margin: 0 0 var(--sp-2);
  font-weight: 500;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.goods-price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
  margin: 0;
}

.goods-price strong {
  font-size: var(--fs-body);
  color: var(--ink);
}

.quantity {
  color: var(--ink-3);
  font-size: var(--fs-meta);
}

.order-meta {
  margin: 0;
}

.order-meta > div {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--line-soft);
  align-items: baseline;
}

.order-meta > div:last-child {
  border-bottom: 0;
}

.order-meta dt {
  flex-shrink: 0;
  color: var(--ink-3);
  font-size: var(--fs-body);
}

.order-meta dd {
  margin: 0;
  text-align: right;
  color: var(--ink-2);
  font-size: var(--fs-body);
  overflow-wrap: anywhere;
  min-width: 0;
}

.order-meta .amount {
  color: var(--paid);
  font-weight: 600;
  font-size: var(--fs-title);
}

.detail-actions {
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.detail-actions :deep(.van-button) + :deep(.van-button) {
  margin-top: var(--sp-2);
}

.muted {
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.8;
}

.channel-warning {
  color: var(--vip);
  font-size: var(--fs-meta);
  line-height: 1.8;
  margin: 0 0 var(--sp-3);
}

.inline-error {
  font-size: var(--fs-body);
  line-height: 1.8;
  color: var(--paid);
  margin: var(--sp-3) 0;
  overflow-wrap: anywhere;
}

.payment-popup {
  left: 50%;
  transform: translateX(-50%);
  max-width: 480px;
  width: 100%;
}

.payment-content {
  padding: var(--sp-5) var(--sp-4) calc(var(--sp-4) + env(safe-area-inset-bottom));
  max-height: 85vh;
  overflow-y: auto;
}

.payment-content > h2 {
  text-align: center;
  margin: 0 var(--sp-5) var(--sp-3);
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink);
}

.payment-total {
  text-align: center;
  margin: 0 0 var(--sp-4);
  color: var(--ink-2);
}

.payment-total strong {
  color: var(--paid);
  font-size: var(--fs-display);
  font-variant-numeric: tabular-nums;
}

.payment-content :deep(.van-cell) {
  padding-left: 0;
  padding-right: 0;
}

.payment-content > :deep(.van-button) {
  margin-top: var(--sp-2);
}

.qr-state {
  text-align: center;
  padding: var(--sp-3) 0;
  font-size: var(--fs-body);
}

.qr-image {
  display: block;
  margin: 0 auto;
  max-width: 100%;
  height: auto;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.qr-state :deep(.van-loading) {
  margin: var(--sp-3) 0;
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .detail-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .detail-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .status-banner {
    padding: var(--sp-5);
  }

  .status-banner h1 {
    font-size: var(--fs-display);
  }

  .detail-body {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-5);
    grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
    gap: var(--sp-4);
    align-items: start;
  }

  .goods-list {
    max-height: none;
  }

  .order-card {
    padding: var(--sp-5);
  }
}
</style>
