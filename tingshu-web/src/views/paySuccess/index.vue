<template>
  <main class="result-page">
    <van-nav-bar title="支付结果" left-arrow @click-left="goBack" />
    <section class="pay-result" aria-live="polite">
      <div v-if="state === 'checking'" class="checking" role="status"><van-loading vertical>正在核验支付结果</van-loading><p>以订单或充值记录的后端状态为准</p></div>
      <template v-else>
        <div class="pay-status">
          <van-icon :name="statusIcon" :color="statusColor" size="30" />
          <h1>{{ statusTitle }}</h1>
        </div>
        <p class="status-description" :class="{ failure: state === 'error' }" :role="state === 'error' ? 'alert' : undefined">{{ message }}</p>
        <p v-if="record" class="pay-money">{{ isRecharge ? '充值金额' : '订单金额' }} <strong>￥{{ formatAmount(isRecharge ? record.rechargeAmount : record.orderAmount) }}</strong></p>
        <p v-if="orderNo" class="order-number">{{ isRecharge ? '充值单号' : '订单号' }}：{{ orderNo }}</p>
        <p v-if="record?.payWayName" class="pay-way">付款方式：{{ record.payWayName }}</p>
        <van-button v-if="state !== 'success' && validRequest" plain round color="#fa4126" :loading="verifying" :disabled="verifying" @click="verify">重新核验</van-button>
        <div class="btn-wrapper">
          <van-button round plain color="#fa4126" @click="viewRecord">{{ isRecharge ? '查看钱包' : '查看订单' }}</van-button>
          <van-button round plain color="#fa4126" @click="router.replace('/index')">返回首页</van-button>
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
const statusColor = computed(() => state.value === 'success' ? '#47D368' : state.value === 'error' ? '#EE0A25' : '#ED6A0C')
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
.result-page { min-height: 100vh; background: #fff; color: #333; }
.pay-result { min-height: calc(100vh - 46px); display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 30px 16px; box-sizing: border-box; text-align: center; }
.checking { color: #969696; font-size: 13px; line-height: 24px; }
.pay-status { display: flex; align-items: center; justify-content: center; margin-top: 50px; gap: 6px; }
.pay-status h1 { font-size: 24px; line-height: 36px; font-weight: 600; margin: 0; }
.status-description { font-size: 14px; line-height: 24px; max-width: 360px; color: #666; margin: 14px 0; overflow-wrap: anywhere; }
.failure { color: #EE0A25; }
.pay-money { color: #666; font-size: 14px; line-height: 24px; margin: 0 0 14px; }
.pay-money strong { font-size: 18px; color: #fa4126; padding-left: 6px; }
.order-number { font-size: 12px; line-height: 20px; color: #969696; margin: 0 0 10px; overflow-wrap: anywhere; max-width: 100%; }
.pay-way { font-size: 12px; color: #969696; margin: 0 0 15px; }
.btn-wrapper { display: flex; width: 100%; box-sizing: border-box; gap: 12px; justify-content: space-between; margin-top: 70px; padding: 6px 0; }
.btn-wrapper :deep(.van-button) { flex: 1; min-width: 0; height: 44px; font-size: 14px; font-weight: 600; }
@media (max-width: 350px) { .pay-status h1 { font-size: 21px; } }
</style>
