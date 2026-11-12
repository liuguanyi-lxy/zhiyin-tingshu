<template>
  <main class="orders-page">
    <van-nav-bar title="我的订单" left-arrow @click-left="goBack" />
    <van-pull-refresh v-model="refreshing" @refresh="refreshOrders">
      <div v-if="loading && !orders.length" class="loading-state" role="status">
        <van-loading vertical>正在加载订单</van-loading>
      </div>
      <section v-else-if="error && !orders.length" class="state-panel" role="alert">
        <van-empty image="error" :description="error" />
        <van-button color="#EE0A25" plain @click="refreshOrders">重新加载</van-button>
      </section>
      <van-empty v-else-if="!orders.length" description="暂无订单，购买的专辑、声音和会员会显示在这里">
        <van-button color="#EE0A25" round @click="router.push('/index')">去发现好书</van-button>
      </van-empty>
      <div v-else class="order-list">
        <article v-for="order in orders" :key="order.orderNo" class="order-card">
          <button class="order-link" type="button" @click="openOrder(order.orderNo)">
            <div class="order-heading">
              <span class="order-number">订单号：{{ order.orderNo }}</span>
              <span class="order-status" :class="{ unpaid: order.orderStatus === ORDER_STATUS.Unpaid }">
                {{ orderStatusName(order) }}
              </span>
            </div>
            <div v-for="(item, index) in order.orderDetailList || []" :key="item.id || `${item.itemId}-${index}`" class="goods">
              <van-image class="cover" width="50" height="50" fit="cover" radius="5" :src="item.itemUrl" alt="商品封面" />
              <span class="goods-name">{{ item.itemName }}</span>
              <span class="goods-price">￥{{ formatAmount(item.itemPrice) }}</span>
            </div>
            <p v-if="!order.orderDetailList?.length" class="fallback-title">{{ order.orderTitle || '查看商品明细' }}</p>
            <div class="order-footer">
              <time>{{ order.createTime || '下单时间待确认' }}</time>
              <span>共 {{ order.orderDetailList?.length || '—' }} 件，合计 <strong>￥{{ formatAmount(order.orderAmount) }}</strong></span>
            </div>
          </button>
          <div class="card-actions">
            <van-button size="small" round :plain="order.orderStatus !== ORDER_STATUS.Unpaid" color="#EE0A25" @click="openOrder(order.orderNo)">
              {{ order.orderStatus === ORDER_STATUS.Unpaid ? '查看并支付' : '查看详情' }}
            </van-button>
          </div>
        </article>
        <div v-if="error" class="page-error" role="alert">{{ error }}</div>
        <div class="pagination">
          <van-button v-if="!finished || error" block plain :loading="loading" :disabled="loading" color="#EE0A25" @click="loadMore">
            {{ error ? '重试加载' : '加载更多' }}
          </van-button>
          <span v-else>已显示全部订单</span>
        </div>
      </div>
    </van-pull-refresh>
  </main>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi, ORDER_STATUS, orderStatusName, formatAmount, requestError } from '../../api/order.js'

const router = useRouter()
const orders = ref([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const error = ref('')
const page = ref(0)
const limit = 10
let version = 0
let disposed = false

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/my')
}
function openOrder(orderNo) {
  if (orderNo) router.push({ name: 'orderDetail', params: { orderNo } })
}
async function fetchPage(reset) {
  if (loading.value && !reset) return
  const current = ++version
  loading.value = true
  error.value = ''
  const nextPage = reset ? 1 : page.value + 1
  try {
    const res = await orderApi.getOrders({ page: nextPage, limit })
    if (disposed || current !== version) return
    const data = res.data
    if (!data || !Array.isArray(data.records)) throw new Error('订单列表返回异常，请重试')
    const records = data.records
    if (reset) orders.value = records
    else {
      const known = new Set(orders.value.map((order) => order.orderNo))
      orders.value.push(...records.filter((order) => !known.has(order.orderNo)))
    }
    page.value = nextPage
    const pages = Number(data.pages)
    const total = Number(data.total)
    finished.value = (data.pages != null && Number.isFinite(pages))
      ? nextPage >= pages
      : (data.total != null && Number.isFinite(total)) ? orders.value.length >= total : records.length < limit
  } catch (failure) {
    if (!disposed && current === version) error.value = requestError(failure, '订单加载失败，请重试')
  } finally {
    if (!disposed && current === version) {
      loading.value = false
      refreshing.value = false
    }
  }
}
function refreshOrders() { return fetchPage(true) }
function loadMore() { return fetchPage(false) }
onMounted(refreshOrders)
onBeforeUnmount(() => { disposed = true; version += 1 })
</script>

<style scoped>
.orders-page { min-height: 100vh; background: #F8F8F8; color: #373737; padding-bottom: 20px; }
.orders-page :deep(.van-pull-refresh) { min-height: calc(100vh - 66px); }
.loading-state { padding: 80px 0; }
.state-panel { text-align: center; padding-bottom: 25px; }
.order-card { background: #fff; padding: 12.5px; margin: 12.5px 12.5px 0; border-radius: 5px; }
.order-link { display: block; border: 0; background: none; padding: 0; width: 100%; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.order-heading { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; font-size: 13px; line-height: 25px; }
.order-number { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.order-status { color: #969696; flex-shrink: 0; }
.unpaid { color: #EE0A25; }
.goods { display: flex; align-items: flex-start; padding-bottom: 10px; }
.cover { flex-shrink: 0; }
.goods-name { flex: 1; min-width: 0; margin: 0 10px; line-height: 20px; font-size: 13px; color: #444; overflow-wrap: anywhere; }
.goods-price { font-size: 12px; line-height: 25px; text-align: right; flex-shrink: 0; }
.order-footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px; margin-top: 2px; color: #969696; font-size: 12px; line-height: 22px; }
.order-footer strong { color: #373737; font-weight: 500; }
.card-actions { display: flex; justify-content: flex-end; margin-top: 10px; }
.fallback-title { font-size: 14px; margin: 0 0 10px; }
.pagination { margin: 20px 12.5px; text-align: center; font-size: 12px; color: #969696; }
.page-error { padding: 15px; color: #EE0A25; text-align: center; font-size: 13px; }
.order-link:focus-visible { outline: 2px solid #ED6A0C; outline-offset: 4px; }
</style>
