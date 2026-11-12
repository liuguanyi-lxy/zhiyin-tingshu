<template>
  <main class="result-page">
    <van-nav-bar title="支付结果" left-arrow @click-left="goBack" />
    <section class="pay-result" aria-live="polite">
      <div v-if="state === 'checking'" class="checking" role="status"><van-loading vertical>正在核验支付结果</van-loading><p>以订单或充值记录的后端状态为准</p></div>
      <template v-else>
        <div class="pay-status" :class="`state-${state}`">
          <van-icon :name="statusIcon" :color="statusColor" size="30" />
          <h1>{{ statusTitle }}</h1>
        </div>
        <p class="status-description" :class="{ failure: state === 'error' }" :role="state === 'error' ? 'alert' : undefined">{{ message }}</p>
        <dl v-if="record" class="pay-meta">
          <div><dt>{{ isRecharge ? '充值金额' : '订单金额' }}</dt><dd class="amount num">￥{{ formatAmount(isRecharge ? record.rechargeAmount : record.orderAmount) }}</dd></div>
          <div v-if="orderNo"><dt>{{ isRecharge ? '充值单号' : '订单号' }}</dt><dd>{{ orderNo }}</dd></div>
          <div v-if="record?.payWayName"><dt>付款方式</dt><dd>{{ record.payWayName }}</dd></div>
        </dl>
        <van-button v-if="state !== 'success' && validRequest" plain round type="primary" :loading="verifying" :disabled="verifying" @click="verify">重新核验</van-button>
        <div class="btn-wrapper">
          <van-button round plain type="primary" @click="viewRecord">{{ isRecharge ? '查看钱包' : '查看订单' }}</van-button>
          <van-button round plain @click="router.replace('/index')">返回首页</van-button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user.js'
import { orderApi, ORDER_STATUS, PAY_WAY, formatAmount, requestError } from '../../api/order.js'
import { paymentApi, PAYMENT_TYPE, paymentError } from '../../api/payment.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const record = ref(null)
const state = ref('checking')
const verifying = ref(false)
const message = ref('')
let version = 0
let disposed = false
const orderNo = computed(() => typeof route.query.orderNo === 'string' ? route.query.orderNo.trim() : '')
const paymentType = computed(() => route.query.paymentType == null ? PAYMENT_TYPE.Order : route.query.paymentType)
const isRecharge = computed(() => paymentType.value === PAYMENT_TYPE.Recharge)
const validRequest = computed(() => !!orderNo.value && Object.values(PAYMENT_TYPE).includes(paymentType.value))
const statusIcon = computed(() => ({ success: 'checked', pending: 'clock-o', syncing: 'clock-o', cancelled: 'close', error: 'warning-o', invalid: 'warning-o' }[state.value] || 'warning-o'))
const statusColor = computed(() => state.value === 'success' ? '#007aff' : state.value === 'error' ? '#e0453a' : '#9aa1ab')
const statusTitle = computed(() => ({
  success: isRecharge.value ? '充值成功' : '支付成功',
  pending: '尚未确认支付',
  syncing: '付款已确认，状态同步中',
  cancelled: isRecharge.value ? '充值已取消' : '订单已取消',
  error: '暂时无法确认',
  invalid: '缺少有效支付信息'
}[state.value] || '支付状态待确认'))

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace(isRecharge.value ? '/wallet' : '/order')
}
function viewRecord() {
  if (isRecharge.value) router.replace('/wallet')
  else if (orderNo.value && validRequest.value) router.replace({ name: 'orderDetail', params: { orderNo: orderNo.value } })
  else router.replace('/order')
}
async function readRecord(number, type, expectedUserId) {
  const res = type === PAYMENT_TYPE.Recharge
    ? await paymentApi.getRechargeInfo(number)
    : await orderApi.getOrderInfo(number)
  const data = res.data
  const status = type === PAYMENT_TYPE.Recharge ? data?.rechargeStatus : data?.orderStatus
  if (!data || data.orderNo !== number || !Object.values(ORDER_STATUS).includes(status)) {
    throw new Error('订单或充值记录不存在，或后端状态不完整')
  }
  // 当前后端详情接口未在服务层校验所有权；前端不展示不属于当前账号的结果。
  // 这不能替代后端鉴权，服务端仍需补齐访问控制。
  if (data.userId == null || String(data.userId) !== String(expectedUserId)) {
    throw new Error('无法核验当前账号的支付记录，请返回自己的订单或钱包查看')
  }
  return { data, status }
}
async function verify() {
  const current = ++version
  const number = orderNo.value
  const type = paymentType.value
  record.value = null
  message.value = ''
  if (!validRequest.value) {
    state.value = 'invalid'
    message.value = '请从订单详情或充值记录进入。网址中的“成功”或金额参数不能证明已经支付。'
    verifying.value = false
    return
  }
  state.value = 'checking'
  verifying.value = true
  const live = () => !disposed && current === version && number === orderNo.value && type === paymentType.value
  try {
    if (userStore.user.id == null) await userStore.getUserInfo()
    if (!live()) return
    const userId = userStore.user.id
    if (userId == null) throw new Error('当前账号信息不完整，请重新登录')
    let result = await readRecord(number, type, userId)
    if (!live()) return
    record.value = result.data
    if (result.status === ORDER_STATUS.Paid) {
      state.value = 'success'
      message.value = type === PAYMENT_TYPE.Recharge ? '后端充值记录已确认入账。' : '后端订单已确认支付完成。'
      return
    }
    if (result.status === ORDER_STATUS.Cancelled) {
      state.value = 'cancelled'
      message.value = '该记录已关闭，请勿继续付款。如已被扣款，请联系平台核对。'
      return
    }
    state.value = 'pending'
    message.value = '后端记录仍为待支付，请勿重复付款。可以返回详情或稍后重新核验。'
    if (result.data.payWay !== PAY_WAY.WeChat || document.hidden) return
    // 一次性主动核验，不进行后台或定时轮询；只接受后端严格布尔成功。
    let payment
    try {
      payment = await paymentApi.queryPayStatus(number)
    } catch (failure) {
      if (live()) {
        state.value = 'error'
        message.value = `后端记录尚未支付，且${paymentError(failure)} 如已扣款，请勿重复付款。`
      }
      return
    }
    if (!live()) return
    if (payment.data === false) return
    if (payment.data !== true) throw new Error('支付服务返回异常，无法确认是否支付成功')
    result = await readRecord(number, type, userId)
    if (!live()) return
    record.value = result.data
    if (result.status === ORDER_STATUS.Paid) {
      state.value = 'success'
      message.value = type === PAYMENT_TYPE.Recharge ? '后端充值记录已确认入账。' : '后端订单已确认支付完成。'
    } else if (result.status === ORDER_STATUS.Cancelled) {
      state.value = 'error'
      message.value = '微信已确认付款，但订单或充值记录已关闭。请联系平台核对，请勿重复付款。'
    } else {
      state.value = 'syncing'
      message.value = '微信已确认付款，订单或到账状态仍在同步。请勿重复付款，稍后重新核验。'
    }
  } catch (failure) {
    if (live()) {
      state.value = 'error'
      message.value = requestError(failure, '支付结果核验失败，请稍后重试')
    }
  } finally {
    if (live()) verifying.value = false
  }
}
watch(() => [route.query.orderNo, route.query.paymentType], verify, { immediate: true })
onBeforeUnmount(() => { disposed = true; version += 1 })
</script>

<style scoped>
.result-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
}

.pay-result {
  min-height: calc(100vh - 46px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--sp-6) var(--sp-4);
  box-sizing: border-box;
  text-align: center;
}

.checking {
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.8;
}

.pay-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.pay-status h1 {
  font-size: var(--fs-display);
  line-height: 1.35;
  font-weight: 600;
  margin: 0;
  color: var(--ink);
}

.status-description {
  font-size: var(--fs-body);
  line-height: 1.8;
  max-width: 460px;
  color: var(--ink-2);
  margin: 0 0 var(--sp-5);
  overflow-wrap: anywhere;
}

.status-description.failure {
  color: var(--paid);
}

.pay-meta {
  width: 100%;
  max-width: 420px;
  margin: 0 0 var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-2) var(--sp-4);
}

.pay-meta > div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--line-soft);
}

.pay-meta > div:last-child {
  border-bottom: 0;
}

.pay-meta dt {
  flex-shrink: 0;
  color: var(--ink-3);
  font-size: var(--fs-meta);
}

.pay-meta dd {
  margin: 0;
  text-align: right;
  color: var(--ink-2);
  font-size: var(--fs-body);
  overflow-wrap: anywhere;
  min-width: 0;
}

.pay-meta .amount {
  color: var(--paid);
  font-size: var(--fs-title);
  font-weight: 600;
}

.btn-wrapper {
  display: flex;
  width: 100%;
  max-width: 420px;
  box-sizing: border-box;
  gap: var(--sp-3);
  margin-top: var(--sp-6);
}

.btn-wrapper :deep(.van-button) {
  flex: 1;
  min-width: 0;
  height: 44px;
  font-size: var(--fs-body);
  font-weight: 600;
}

@media (max-width: 350px) {
  .pay-status h1 {
    font-size: var(--fs-title);
  }
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .result-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .result-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .pay-result {
    padding: var(--sp-6) var(--sp-5);
  }

  .status-description {
    margin-bottom: var(--sp-5);
  }

  .pay-meta {
    max-width: 460px;
  }

  .btn-wrapper {
    max-width: 460px;
    margin-top: var(--sp-5);
  }
}
</style>
