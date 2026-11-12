<template>
  <main class="fund-page">
    <van-nav-bar title="资金明细" left-text="返回" left-arrow @click-left="goBack" />
    <nav class="fund-tabs" aria-label="交易类型">
      <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" :aria-pressed="activeTab === tab.id" @click="switchTab(tab.id)">{{ tab.name }}</button>
    </nav>
    <section class="fund-content" :aria-label="activeTab === 'invest' ? '充值记录' : '消费记录'" :aria-busy="current.loading">
      <div class="list-tools"><span>{{ activeTab === 'invest' ? '已支付充值' : '账户消费' }}</span><button type="button" :disabled="current.loading" @click="loadPage(activeTab, true)">刷新</button></div>
      <div v-if="current.loading && !current.list.length" class="loading-line" role="status"><van-loading size="22">正在读取明细…</van-loading></div>
      <div v-if="current.error" class="error-state" role="alert"><p>{{ current.error }}</p><van-button size="small" plain type="primary" :disabled="current.loading" @click="loadPage(activeTab, current.retryReset)">重新加载</van-button></div>
      <van-empty v-if="current.loaded && !current.loading && !current.error && !current.list.length" :description="activeTab === 'invest' ? '暂无已支付充值记录' : '暂无消费记录'" />
      <ul v-if="current.list.length" class="record-list">
        <li v-for="(item, index) in current.list" :key="item.id ?? `${item.createTime}-${index}`" class="record-item">
          <div class="record-info"><h2>{{ item.title || '未提供交易说明' }}</h2><time>{{ item.createTime || '未提供交易时间' }}</time></div>
          <span class="record-amount" :class="{ expense: activeTab === 'consume' }">{{ signedAmount(item.amount) }}<small v-if="validAmount(item.amount)">元</small></span>
        </li>
      </ul>
      <div v-if="current.list.length" class="list-footer">
        <van-loading v-if="current.loading" size="20">读取中…</van-loading>
        <button v-else-if="current.hasMore && !current.error" type="button" @click="loadPage(activeTab)">加载更多</button>
        <span v-else-if="!current.error">没有更多记录了</span>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { accountApi } from '../../api/account.js'

const route = useRoute()
const router = useRouter()
const tabs = [{ id: 'invest', name: '充值记录' }, { id: 'consume', name: '消费记录' }]
const activeTab = ref(route.query.id === 'consume' ? 'consume' : 'invest')
const newState = () => ({ list: [], page: 0, loaded: false, loading: false, error: '', hasMore: true, retryReset: false })
const records = reactive({ invest: newState(), consume: newState() })
const current = computed(() => records[activeTab.value])
const limit = 10

function validAmount(value) {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}
function signedAmount(value) {
  if (!validAmount(value)) return '金额未提供'
  return `${activeTab.value === 'invest' ? '+' : '−'}${Math.abs(Number(value)).toFixed(2)}`
}

async function loadPage(key, reset = false) {
  const state = records[key]
  if (!state || state.loading || (!reset && !state.hasMore)) return
  state.loading = true
  state.error = ''
  const page = reset ? 1 : state.page + 1
  try {
    const response = await (key === 'invest' ? accountApi.getRechargePage : accountApi.getConsumePage).call(accountApi, { page, limit })
    if (response?.code !== 200 || !Array.isArray(response.data?.records)) throw new Error(response?.message || '服务未返回有效资金明细')
    const data = response.data
    state.list = page === 1 ? data.records : state.list.concat(data.records)
    state.page = page
    state.loaded = true
    state.hasMore = data.total != null && Number.isFinite(Number(data.total)) ? page * limit < Number(data.total) : data.records.length === limit
  } catch (error) {
    state.retryReset = reset
    const status = error?.response?.status
    state.error = status === 404 || status === 405 ? '当前服务尚未提供资金明细接口，请稍后重试' : error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请稍后重试' : error?.message) || '明细读取失败，请重试'
  } finally {
    state.loading = false
  }
}

function switchTab(key) {
  if (!records[key] || key === activeTab.value) return
  activeTab.value = key
  router.replace({ query: { ...route.query, id: key } })
  if (!records[key].loaded) loadPage(key)
}

watch(() => route.query.id, (value) => {
  activeTab.value = value === 'consume' ? 'consume' : 'invest'
  if (!current.value.loaded) loadPage(activeTab.value)
})

function goBack() {
  if (router.options.history.state.back) router.back()
  else router.replace('/wallet')
}
onMounted(() => loadPage(activeTab.value))
</script>

<style scoped>
.fund-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: calc(var(--sp-6) + env(safe-area-inset-bottom));
  box-sizing: border-box;
  --van-nav-bar-icon-color: var(--ink-3);
  --van-nav-bar-text-color: var(--ink-3);
}

.fund-tabs {
  display: flex;
  gap: var(--sp-2);
  padding: 0 var(--sp-3);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.fund-tabs button {
  position: relative;
  flex: 1;
  min-height: 48px;
  border: 0;
  background: none;
  color: var(--ink-3);
  font: inherit;
  font-size: var(--fs-body);
  cursor: pointer;
  transition: color 0.15s ease;
}

.fund-tabs button:hover {
  color: var(--ink-2);
}

.fund-tabs button.active {
  color: var(--ink);
  font-weight: 600;
}

.fund-tabs button.active::after {
  content: '';
  position: absolute;
  width: 24px;
  height: 2px;
  left: calc(50% - 12px);
  bottom: 0;
  background: var(--brand);
  border-radius: 1px;
}

.fund-content {
  max-width: 860px;
  margin: 0 auto;
  padding: var(--sp-3);
}

.list-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: var(--sp-3) 0;
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.list-tools button,
.list-footer button {
  min-height: 36px;
  padding: 6px 10px;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}

.list-tools button:hover,
.list-footer button:hover {
  color: var(--brand-strong);
}

.record-list {
  list-style: none;
  padding: 0;
  margin: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.record-item:last-child {
  border-bottom: 0;
}

.record-info {
  min-width: 0;
  flex: 1;
}

.record-info h2 {
  font-size: var(--fs-body);
  font-weight: 500;
  margin: 0 0 6px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-info time {
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

.record-amount {
  color: var(--ink-2);
  font-size: var(--fs-body);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
  overflow-wrap: anywhere;
  max-width: 45%;
}

.record-amount.expense {
  color: var(--paid);
}

.record-amount small {
  font-size: var(--fs-meta);
  font-weight: 400;
  margin-left: 2px;
}

.loading-line,
.list-footer {
  text-align: center;
  padding: var(--sp-5) 0;
  color: var(--ink-3);
  font-size: var(--fs-meta);
}

.error-state {
  text-align: center;
  padding: var(--sp-5) var(--sp-3);
  color: var(--ink-2);
  font-size: var(--fs-body);
  line-height: 1.7;
}

button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
  border-radius: var(--r-sm);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .fund-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .fund-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .fund-tabs {
    gap: var(--sp-4);
    padding: 0 var(--sp-5);
  }

  .fund-tabs button {
    flex: 0 0 auto;
    min-height: 52px;
    font-size: var(--fs-section);
  }

  .fund-content {
    max-width: var(--shell-max);
    padding: var(--sp-5);
  }

  .record-item {
    padding: var(--sp-4) var(--sp-5);
  }

  .record-info h2 {
    font-size: var(--fs-section);
  }

  .record-amount {
    font-size: var(--fs-section);
  }
}
</style>
