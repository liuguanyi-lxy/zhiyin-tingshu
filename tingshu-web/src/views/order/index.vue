<template>
  <main class="orders-page">
    <van-nav-bar title="我的订单" left-arrow @click-left="goBack" />
    <van-pull-refresh v-model="refreshing" @refresh="refreshOrders">
      <div v-if="loading && !orders.length" class="loading-state" role="status">
        <van-loading vertical>正在加载订单</van-loading>
      </div>
      <section v-else-if="error && !orders.length" class="state-panel" role="alert">
        <van-empty image="error" :description="error" />
        <van-button type="primary" plain @click="refreshOrders">重新加载</van-button>
      </section>
      <van-empty v-else-if="!orders.length" description="暂无订单，购买的专辑、声音和会员会显示在这里">
        <van-button type="primary" round @click="router.push('/index')">去发现好书</van-button>
      </van-empty>
      <div v-else class="order-list">
        <article v-for="order in orders" :key="order.orderNo" class="order-card">
          <button class="order-link" type="button" @click="openOrder(order.orderNo)">
            <div class="order-heading">
              <span class="order-number">订单号：{{ order.orderNo }}</span>
              <span
                class="tag order-status"
                :class="{
                  'status-paid': order.orderStatus === ORDER_STATUS.Paid,
                  'status-unpaid': order.orderStatus === ORDER_STATUS.Unpaid,
                  'status-cancelled': order.orderStatus === ORDER_STATUS.Cancelled
                }"
              >
                {{ orderStatusName(order) }}
              </span>
            </div>
            <div v-for="(item, index) in order.orderDetailList || []" :key="item.id || `${item.itemId}-${index}`" class="goods">
              <van-image class="cover" width="50" height="50" fit="cover" radius="6" :src="item.itemUrl" alt="商品封面" />
              <span class="goods-name">{{ item.itemName }}</span>
              <span class="goods-price num">￥{{ formatAmount(item.itemPrice) }}</span>
            </div>
            <p v-if="!order.orderDetailList?.length" class="fallback-title">{{ order.orderTitle || '查看商品明细' }}</p>
            <div class="order-footer">
              <time>{{ order.createTime || '下单时间待确认' }}</time>
              <span>共 {{ order.orderDetailList?.length || '—' }} 件，合计 <strong class="num">￥{{ formatAmount(order.orderAmount) }}</strong></span>
            </div>
          </button>
          <div class="card-actions">
            <van-button size="small" round :plain="order.orderStatus !== ORDER_STATUS.Unpaid" type="primary" @click="openOrder(order.orderNo)">
              {{ order.orderStatus === ORDER_STATUS.Unpaid ? '查看并支付' : '查看详情' }}
            </van-button>
          </div>
        </article>
        <div v-if="error" class="page-error" role="alert">{{ error }}</div>
        <div class="pagination">
          <van-button v-if="!finished || error" block plain :loading="loading" :disabled="loading" type="primary" @click="loadMore">
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
.orders-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-6);
}

.orders-page :deep(.van-pull-refresh) {
  min-height: calc(100vh - 66px);
}

.loading-state {
  padding: 80px 0;
}

.state-panel {
  text-align: center;
  padding-bottom: var(--sp-5);
}

.order-list {
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-3);
}

.order-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
}

.order-link {
  display: block;
  border: 0;
  background: none;
  padding: 0;
  width: 100%;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  border-radius: var(--r-sm);
}

.order-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
  font-size: var(--fs-meta);
  line-height: 1.6;
}

.order-number {
  min-width: 0;
  flex: 1;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

.order-status {
  flex-shrink: 0;
  color: var(--ink-3);
  background: var(--line-soft);
}

.order-status.status-paid {
  color: var(--brand);
  background: var(--brand-soft);
}

.order-status.status-unpaid {
  color: var(--paid);
  background: var(--paid-soft);
}

.order-status.status-cancelled {
  color: var(--ink-3);
  background: var(--line-soft);
}

.goods {
  display: flex;
  align-items: flex-start;
  padding-bottom: var(--sp-3);
}

.cover {
  flex-shrink: 0;
  border-radius: var(--r-md);
}

.goods-name {
  flex: 1;
  min-width: 0;
  margin: 0 var(--sp-3);
  line-height: 1.6;
  font-size: var(--fs-body);
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.goods-price {
  font-size: var(--fs-meta);
  line-height: 25px;
  text-align: right;
  flex-shrink: 0;
  color: var(--ink-2);
}

.fallback-title {
  font-size: var(--fs-body);
  color: var(--ink-2);
  margin: 0 0 var(--sp-3);
}

.order-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--sp-1) var(--sp-3);
  margin-top: var(--sp-1);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.7;
}

.order-footer strong {
  color: var(--ink);
  font-weight: 600;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.pagination {
  margin: var(--sp-4) 0 0;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.page-error {
  padding: var(--sp-4);
  color: var(--paid);
  text-align: center;
  font-size: var(--fs-body);
}

.order-link:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 4px;
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .orders-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .orders-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .order-list {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-5);
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
    align-items: start;
  }

  .pagination {
    grid-column: 1 / -1;
    max-width: 420px;
    margin: var(--sp-4) auto 0;
  }

  .goods-name {
    line-height: 1.7;
  }
}

@media (min-width: 1280px) {
  .order-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
