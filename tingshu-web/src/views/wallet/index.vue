<template>
  <main class="wallet-page">
    <van-nav-bar title="钱包" left-text="返回" left-arrow @click-left="goBack" />
    <div class="wallet-body">
      <section class="balance-card" aria-labelledby="balance-title">
        <div class="balance-main">
          <p id="balance-title">账户余额</p>
          <div class="balance-amount"><strong class="num">{{ balanceText }}</strong><span>元</span></div>
        </div>
        <van-button round type="primary" :disabled="!nativePaymentReady" @click="openRecharge">{{ orderNo && !credited ? '继续充值' : '充值' }}</van-button>
      </section>
      <p v-if="balanceError" class="balance-error" role="alert">{{ balanceError }} <button type="button" :disabled="balanceLoading" @click="loadBalance">重试</button></p>
      <p v-if="!nativePaymentReady" class="service-note">充值支付服务暂未接通，余额只展示后端真实数据。</p>
      <nav class="wallet-links" aria-label="钱包明细">
        <router-link to="/fund?id=consume"><span>消费记录</span><van-icon name="arrow" color="#9aa1ab" /></router-link>
        <router-link to="/fund?id=invest"><span>充值记录</span><van-icon name="arrow" color="#9aa1ab" /></router-link>
      </nav>
    </div>

    <van-popup v-model:show="showRecharge" position="bottom" round :closeable="!creating" :close-on-click-overlay="!creating" @closed="stopPolling">
      <section class="recharge-sheet" aria-labelledby="recharge-heading">
        <h2 id="recharge-heading">{{ orderNo ? '微信充值' : '选择充值金额' }}</h2>
        <template v-if="!orderNo">
          <div class="amount-grid">
            <button v-for="amount in presets" :key="amount" type="button" :disabled="creating" :class="{ selected: amount === selectedAmount }" :aria-pressed="amount === selectedAmount" @click="selectedAmount = amount">{{ amount }} 元</button>
            <button type="button" :disabled="creating" :class="{ selected: selectedAmount === null }" :aria-pressed="selectedAmount === null" @click="selectedAmount = null">自定义</button>
          </div>
          <label v-if="selectedAmount === null" class="custom-label">自定义金额（元）<input v-model="customAmount" type="text" inputmode="numeric" autocomplete="off" placeholder="请输入正整数金额" maxlength="9" :disabled="creating" /></label>
          <p class="dialog-note">充值金额须为正整数。提交后使用微信扫描真实付款码完成支付。</p>
          <p v-if="paymentErrorText" class="payment-error" role="alert">{{ paymentErrorText }}</p>
          <van-button block round color="#07c160" :loading="creating" :disabled="creating || !nativePaymentReady" loading-text="正在创建充值单…" @click="submitRecharge">微信充值</van-button>
        </template>
        <template v-else>
          <p class="recharge-value">充值 <strong>¥{{ submittedAmount }}</strong></p>
          <p class="order-no">充值单号：{{ orderNo }}</p>
          <div v-if="creating" class="payment-status" role="status"><van-loading size="22">正在获取付款码…</van-loading></div>
          <img v-else-if="qrImage && !credited" class="payment-qr" :src="qrImage" alt="微信充值付款二维码，请用微信扫一扫" />
          <div v-if="credited" class="credited-state" role="status"><van-icon name="checked" size="40" color="#07c160" /><h3>充值支付已确认</h3></div>
          <p v-else-if="statusText" class="payment-status" role="status">{{ statusText }}</p>
          <p v-if="paymentErrorText" class="payment-error" role="alert">{{ paymentErrorText }}</p>
          <van-button v-if="!qrImage && !creating && !credited" block plain round color="#07c160" @click="createPaymentCode">重试获取付款码</van-button>
          <van-button v-else-if="qrImage && !creating && !credited && !polling" block plain round color="#07c160" @click="startPolling">继续查询支付状态</van-button>
          <van-button v-if="credited" block round color="#07c160" @click="finishRecharge">完成</van-button>
          <p v-if="!credited" class="dialog-note">请使用另一台设备上的微信扫码。关闭窗口只停止查询，不会取消已创建的充值单。</p>
        </template>
      </section>
    </van-popup>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { accountApi } from '../../api/account.js'
import { paymentApi, PAYMENT_TYPE, paymentError, NATIVE_ENDPOINT_AVAILABLE } from '../../api/payment.js'
import { PAY_WAY, ORDER_STATUS } from '../../api/order.js'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()
const balance = ref(null)
const balanceLoading = ref(false)
const balanceError = ref('')
// 审计时后端尚无 Native 映射；收到服务已补齐的确认后才开启，不能让用户点击必然404的支付按钮。
const nativePaymentReady = NATIVE_ENDPOINT_AVAILABLE
const showRecharge = ref(false)
const presets = [10, 20, 30, 50, 100]
const selectedAmount = ref(10)
const customAmount = ref('')
const creating = ref(false)
const orderNo = ref('')
const submittedAmount = ref('')
const qrImage = ref('')
const paymentErrorText = ref('')
const statusText = ref('')
const polling = ref(false)
const credited = ref(false)
let paymentToken = ''
let wechatPaid = false
let timer = null
let version = 0
let attempts = 0
let disposed = false
const balanceText = computed(() => balance.value == null ? '—' : Number(balance.value).toFixed(2))

async function loadBalance() {
  if (balanceLoading.value) return
  balanceLoading.value = true
  balanceError.value = ''
  const token = userStore.token
  try {
    const response = await accountApi.getAvailableAmount()
    if (disposed || token !== userStore.token) return
    if (response?.code !== 200 || response.data == null || response.data === '' || !Number.isFinite(Number(response.data))) throw new Error(response?.message || '服务未返回有效余额')
    balance.value = Number(response.data)
    userStore.amount = balance.value
  } catch (error) {
    if (!disposed) balanceError.value = error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请稍后重试' : error?.message) || '余额读取失败，请重试'
  } finally {
    if (!disposed) balanceLoading.value = false
  }
}
function validOperation(current) { return !disposed && showRecharge.value && current === version && paymentToken && paymentToken === userStore.token }
function stopPolling() {
  version += 1
  if (timer !== null) clearTimeout(timer)
  timer = null
  polling.value = false
}
function openRecharge() {
  if (!nativePaymentReady) return
  showRecharge.value = true
  if (orderNo.value && qrImage.value && !credited.value) startPolling()
}

async function submitRecharge() {
  if (!nativePaymentReady || creating.value || orderNo.value) return
  const value = selectedAmount.value === null ? customAmount.value.trim() : String(selectedAmount.value)
  if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value)) || value.length > 9) {
    paymentErrorText.value = '请输入不超过 9 位的正整数充值金额'
    return
  }
  paymentErrorText.value = ''
  creating.value = true
  paymentToken = userStore.token
  const current = version
  try {
    if (!paymentToken) throw new Error('请先登录')
    if (userStore.user.id == null) await userStore.getUserInfo()
    if (!validOperation(current)) return
    // RechargeInfoVo.amount / payWay；rechargeAmount 仅是返回记录字段。
    const response = await accountApi.submitRecharge({ amount: Number(value), payWay: PAY_WAY.WeChat })
    if (!validOperation(current)) return
    if (response?.code !== 200 || typeof response.data?.orderNo !== 'string' || !response.data.orderNo) throw new Error(response?.message || '充值服务未返回有效单号')
    orderNo.value = response.data.orderNo
    submittedAmount.value = value
    creating.value = false
    await createPaymentCode()
  } catch (error) {
    if (validOperation(current)) paymentErrorText.value = error?.response?.data?.message || error?.message || '创建充值单失败，请稍后重试'
  } finally {
    creating.value = false
  }
}

async function createPaymentCode() {
  if (!nativePaymentReady || creating.value || !orderNo.value || credited.value || !showRecharge.value) return
  stopPolling()
  const current = version
  creating.value = true
  paymentErrorText.value = ''
  qrImage.value = ''
  try {
    const response = await paymentApi.createNative(PAYMENT_TYPE.Recharge, orderNo.value)
    if (!validOperation(current)) return
    const codeUrl = response?.data?.codeUrl
    if (response?.code !== 200 || typeof codeUrl !== 'string' || !/^weixin:\/\//i.test(codeUrl)) throw new Error(response?.message || '支付服务未返回有效微信付款码')
    const image = await QRCode.toDataURL(codeUrl, { width: 232, margin: 2, errorCorrectionLevel: 'M' })
    if (!validOperation(current)) return
    qrImage.value = image
    startPolling()
  } catch (error) {
    if (validOperation(current)) paymentErrorText.value = paymentError(error)
  } finally {
    creating.value = false
  }
}

function startPolling() {
  if (!orderNo.value || !qrImage.value || credited.value || !showRecharge.value || paymentToken !== userStore.token) return
  stopPolling()
  attempts = 0
  polling.value = true
  paymentErrorText.value = ''
  pollPayment(version)
}
async function pollPayment(current) {
  if (!validOperation(current)) return
  attempts += 1
  try {
    if (!wechatPaid) {
      const result = await paymentApi.queryPayStatus(orderNo.value)
      if (!validOperation(current)) return
      if (result?.code !== 200 || typeof result.data !== 'boolean') throw new Error(result?.message || '支付状态返回异常')
      wechatPaid = result.data === true
    }
    if (wechatPaid) {
      const result = await paymentApi.getRechargeInfo(orderNo.value)
      if (!validOperation(current)) return
      const record = result?.data
      if (result?.code !== 200 || !record || String(record.orderNo) !== orderNo.value || String(record.userId) !== String(userStore.user.id) || Number(record.rechargeAmount) !== Number(submittedAmount.value)) throw new Error('充值记录核对失败，请稍后查询')
      if (record.rechargeStatus === ORDER_STATUS.Paid) {
        credited.value = true
        stopPolling()
        await loadBalance()
        return
      }
      statusText.value = '微信已确认支付，正在等待充值记录更新…'
    } else {
      statusText.value = '等待微信支付，付款后会自动查询结果'
    }
    if (attempts >= 100) {
      stopPolling()
      statusText.value = '自动查询已暂停，可点击继续查询。请勿重复付款。'
      return
    }
    timer = setTimeout(() => pollPayment(current), 3000)
  } catch (error) {
    if (validOperation(current)) {
      stopPolling()
      paymentErrorText.value = paymentError(error)
      statusText.value = '查询已暂停，尚未确认充值完成'
    }
  }
}
function finishRecharge() {
  if (!credited.value) return
  showRecharge.value = false
  stopPolling()
  orderNo.value = ''
  qrImage.value = ''
  paymentErrorText.value = ''
  statusText.value = ''
  wechatPaid = false
  credited.value = false
}
function goBack() {
  stopPolling()
  if (router.options.history.state.back) router.back()
  else router.replace('/my')
}
watch(showRecharge, value => { if (!value) stopPolling() })
onBeforeRouteLeave(() => { stopPolling() })
onBeforeUnmount(() => { disposed = true; stopPolling() })
onMounted(loadBalance)
</script>

<style scoped>
.wallet-page {
  min-height: 100vh;
  background: var(--ground);
  padding-bottom: calc(var(--sp-6) + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.wallet-body {
  padding: var(--sp-3);
}

.balance-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-5) var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--vip-soft);
  border: 1px solid var(--line);
  color: var(--vip);
}

.balance-main {
  min-width: 0;
}

.balance-main p {
  margin: 0 0 6px;
  font-size: var(--fs-meta);
  color: var(--vip);
}

.balance-amount {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.balance-amount strong {
  font-size: var(--fs-display);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
  color: var(--ink);
}

.balance-amount span {
  font-size: var(--fs-meta);
  color: var(--vip);
}

.balance-card :deep(.van-button) {
  flex-shrink: 0;
  min-width: 76px;
}

.balance-error,
.service-note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-meta);
  line-height: 1.6;
}

.balance-error {
  color: var(--paid);
}

.balance-error button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}

.balance-error button:hover {
  color: var(--brand-strong);
}

.service-note {
  color: var(--ink-3);
}

.wallet-links {
  margin-top: var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.wallet-links a {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 52px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
  color: var(--ink);
  font-size: var(--fs-body);
  text-decoration: none;
  transition: background 0.15s ease;
}

.wallet-links a:last-child {
  border-bottom: 0;
}

.wallet-links a:hover {
  background: var(--line-soft);
}

/* 充值弹层 */
.recharge-sheet {
  box-sizing: border-box;
  max-height: 88dvh;
  overflow-y: auto;
  padding: var(--sp-5) var(--sp-4) calc(var(--sp-5) + env(safe-area-inset-bottom));
  color: var(--ink);
}

.recharge-sheet h2 {
  margin: 0 0 var(--sp-5);
  text-align: center;
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink);
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-2);
}

.amount-grid button {
  min-height: 48px;
  padding: 0 var(--sp-2);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-body);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.amount-grid button:hover {
  border-color: var(--brand-line);
}

.amount-grid button.selected {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

.custom-label {
  display: block;
  margin-top: var(--sp-4);
  color: var(--ink-2);
  font-size: var(--fs-meta);
}

.custom-label input {
  width: 100%;
  box-sizing: border-box;
  margin-top: var(--sp-2);
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 16px;
}

.custom-label input::placeholder {
  color: var(--ink-4);
}

.dialog-note {
  margin: var(--sp-4) 0;
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.7;
}

.recharge-value {
  text-align: center;
  font-size: var(--fs-body);
  margin: 0 0 var(--sp-2);
  color: var(--ink-2);
}

.recharge-value strong {
  margin-left: 4px;
  font-size: var(--fs-display);
  color: var(--paid);
  font-variant-numeric: tabular-nums;
}

.order-no {
  font-size: var(--fs-micro);
  color: var(--ink-3);
  text-align: center;
  overflow-wrap: anywhere;
}

.payment-qr {
  display: block;
  width: min(232px, 100%);
  margin: var(--sp-4) auto;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.payment-status,
.payment-error {
  text-align: center;
  font-size: var(--fs-meta);
  line-height: 1.7;
  margin: var(--sp-4) 0;
}

.payment-status {
  color: var(--ink-3);
}

.payment-error {
  color: var(--paid);
  overflow-wrap: anywhere;
}

.credited-state {
  text-align: center;
  padding: var(--sp-5) 0;
}

.credited-state h3 {
  font-size: var(--fs-section);
  margin: var(--sp-3) 0 0;
  color: var(--ink);
}

a:focus-visible,
button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
  border-radius: var(--r-sm);
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .wallet-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .wallet-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .wallet-body {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-5) var(--sp-5) var(--sp-6);
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    grid-template-areas:
      'balance balance'
      'error error'
      'note note'
      'links links';
    gap: var(--sp-4);
    align-items: start;
  }

  .balance-card {
    grid-area: balance;
    padding: var(--sp-5) var(--sp-5);
  }

  .balance-error {
    grid-area: error;
    margin: 0;
  }

  .service-note {
    grid-area: note;
    margin: 0;
  }

  .wallet-links {
    grid-area: links;
    margin-top: 0;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
    background: none;
    border: 0;
    border-radius: 0;
  }

  .wallet-links a {
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: var(--r-lg);
    min-height: 56px;
  }

  .wallet-links a:last-child {
    border-bottom: 1px solid var(--line);
  }

  .recharge-sheet {
    width: 420px;
    margin: 0 auto;
    padding: var(--sp-5) var(--sp-5) var(--sp-5);
  }

  .wallet-page :deep(.van-popup--bottom) {
    width: 480px;
    max-width: 100%;
    left: 50%;
    right: auto;
    margin-left: -240px;
    border-radius: var(--r-lg);
    overflow: hidden;
  }
}
</style>
