<template>
  <main class="fund-page">
    <van-nav-bar title="资金明细" left-text="返回" left-arrow @click-left="goBack" />
    <nav class="fund-tabs" aria-label="交易类型">
      <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" :aria-pressed="activeTab === tab.id" @click="switchTab(tab.id)">{{ tab.name }}</button>
    </nav>
    <section class="fund-content" :aria-label="activeTab === 'invest' ? '充值记录' : '消费记录'" :aria-busy="current.loading">
      <div class="list-tools"><span>{{ activeTab === 'invest' ? '已支付充值' : '账户消费' }}</span><button type="button" :disabled="current.loading" @click="loadPage(activeTab, true)">刷新</button></div>
      <div v-if="current.loading && !current.list.length" class="loading-line" role="status"><van-loading size="22">正在读取明细…</van-loading></div>
      <div v-if="current.error" class="error-state" role="alert"><p>{{ current.error }}</p><van-button size="small" plain color="#07c160" :disabled="current.loading" @click="loadPage(activeTab, current.retryReset)">重新加载</van-button></div>
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
.fund-page { min-height: 100vh; background: #fff; color: #323233; padding-bottom: calc(24px + env(safe-area-inset-bottom)); box-sizing: border-box; --van-nav-bar-icon-color: #646566; --van-nav-bar-text-color: #646566; }
.fund-tabs { display: flex; padding: 0 15px; border-bottom: 1px solid #ebedf0; }
.fund-tabs button { position: relative; flex: 1; min-height: 52px; border: 0; background: none; color: #969799; font: inherit; font-size: 15px; cursor: pointer; }
.fund-tabs button.active { color: #323233; font-weight: 600; }
.fund-tabs button.active::after { content: ''; position: absolute; width: 20px; height: 2px; left: calc(50% - 10px); bottom: 0; background: #07c160; border-radius: 1px; }
.fund-content { max-width: 600px; margin: 0 auto; padding: 0 15px; }
.list-tools { display: flex; justify-content: space-between; align-items: center; margin: 12px 0; font-size: 12px; color: #969799; }
.list-tools button, .list-footer button { min-height: 36px; padding: 6px 10px; border: 0; background: none; color: #079b4e; font: inherit; cursor: pointer; }
.record-list { list-style: none; padding: 0; margin: 0; }
.record-item { display: flex; justify-content: space-between; gap: 14px; padding: 14px 0 18px; margin-bottom: 10px; border-bottom: 1px solid #ebedf0; }
.record-info { min-width: 0; flex: 1; }
.record-info h2 { font-size: 14px; font-weight: 400; margin: 0 0 9px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.record-info time { font-size: 12px; color: #969799; }
.record-amount { color: #ef8a26; font-size: 15px; font-weight: 600; text-align: right; overflow-wrap: anywhere; max-width: 45%; }
.record-amount.expense { color: #646566; }
.record-amount small { font-size: 12px; font-weight: 400; margin-left: 2px; }
.loading-line, .list-footer { text-align: center; padding: 24px 0; color: #969799; font-size: 12px; }
.error-state { text-align: center; padding: 20px 10px; color: #646566; font-size: 13px; line-height: 1.7; }
button:focus-visible { outline: 2px solid #079b4e; outline-offset: 2px; border-radius: 4px; }
button:disabled { cursor: not-allowed; opacity: 0.5; }
</style>
