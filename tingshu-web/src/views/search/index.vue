<template>
  <div class="search-page">
    <van-nav-bar :title="categoryTitle || '搜索'" left-arrow fixed placeholder @click-left="router.back()" />
    <van-search v-model="keyword" placeholder="搜索专辑 / 声音" show-action @update:model-value="onInput" @search="onSearch">
      <template #action><button class="search-action" type="button" @click="onSearch">搜索</button></template>
    </van-search>
    <div v-if="suggests.length" class="suggest-panel">
      <button v-for="(suggestion, index) in suggests" :key="index" type="button" class="suggest-item" @click="selectKeyword(suggestion)"><van-icon name="search" />{{ suggestion }}</button>
    </div>
    <section v-if="!searched" class="history-panel">
      <div class="panel-head"><h2>搜索历史</h2><button v-if="history.length" type="button" aria-label="清空搜索历史" @click="clearHistory"><van-icon name="delete-o" /></button></div>
      <div v-if="history.length" class="history-tags"><button v-for="item in history" :key="item" type="button" @click="selectKeyword(item)">{{ item }}</button></div>
      <p v-else class="hint">输入书名、主播，找到你想听的内容</p>
    </section>
    <template v-else>
      <div class="result-toolbar">
        <span>{{ total }} 个专辑</span>
        <select v-model="sort" aria-label="专辑排序" @change="onSearch"><option value="">综合排序</option><option value="2:desc">播放最多</option><option value="3:desc">最近更新</option></select>
      </div>
      <van-loading v-if="loading" class="loading-wrap">搜索中...</van-loading>
      <van-empty v-else-if="failed" image="network" description="搜索失败，请稍后重试"><van-button size="small" round @click="fetchPage">重试</van-button></van-empty>
      <div v-else class="result-area">
        <router-link v-for="album in albums" :key="album.id" class="album-item" :to="`/detail/${album.id}`">
          <img class="cover" :src="album.coverUrl" :alt="plainTitle(album.albumTitle)" loading="lazy" />
          <div class="album-info"><h2 class="album-title"><template v-for="(part, index) in titleParts(album.albumTitle)" :key="index"><mark v-if="part.highlight">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></h2><p>{{ album.includeTrackCount ?? 0 }} 集 · {{ album.announcerName || '未知主播' }}</p><span class="play-count"><van-icon name="play-circle-o" /> {{ Number(album.playStatNum || 0).toLocaleString() }} 次播放</span></div>
        </router-link>
        <van-empty v-if="!albums.length" description="没有找到相关专辑，换个关键词试试" />
        <van-pagination v-if="total > pageSize" v-model="page" :total-items="total" :items-per-page="pageSize" :show-page-size="3" force-ellipses @change="fetchPage" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '../../api/search.js'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const suggests = ref([])
const albums = ref([])
const loading = ref(false)
const failed = ref(false)
const searched = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 10
const sort = ref('')
const categoryTitle = computed(() => typeof route.query.pageTitle === 'string' ? route.query.pageTitle : '')
const history = ref(readHistory())
let debounceTimer
let suggestVersion = 0
let searchVersion = 0

function readHistory() {
  try {
    const items = JSON.parse(localStorage.getItem('ts_search_history') || '[]')
    return Array.isArray(items) ? items.filter(item => typeof item === 'string').slice(0, 8) : []
  } catch { return [] }
}

function saveHistory() {
  try { localStorage.setItem('ts_search_history', JSON.stringify(history.value)) } catch {}
}
function clearHistory() { history.value = []; saveHistory() }
function selectKeyword(value) { keyword.value = value; onSearch() }

function onInput() {
  clearTimeout(debounceTimer)
  const version = ++suggestVersion
  suggests.value = []
  const value = keyword.value.trim()
  if (!value) return
  debounceTimer = setTimeout(async () => {
    try {
      const res = await searchApi.getSearchSuggestions(value)
      if (version === suggestVersion) suggests.value = Array.isArray(res.data) ? res.data.filter(item => typeof item === 'string') : []
    } catch { if (version === suggestVersion) suggests.value = [] }
  }, 300)
}

function onSearch() {
  clearTimeout(debounceTimer)
  suggestVersion++
  suggests.value = []
  const value = keyword.value.trim()
  if (!value && !route.query.category1Id && !route.query.category3Id) return
  if (value) {
    history.value = [value, ...history.value.filter(item => item !== value)].slice(0, 8)
    saveHistory()
  }
  page.value = 1
  fetchPage()
}

async function fetchPage() {
  const version = ++searchVersion
  searched.value = true
  loading.value = true
  failed.value = false
  const params = { keyword: keyword.value.trim(), pageNo: page.value, pageSize, order: sort.value }
  for (const key of ['category1Id', 'category2Id', 'category3Id']) {
    const value = Number(route.query[key])
    if (Number.isSafeInteger(value) && value > 0) params[key] = value
  }
  try {
    const res = await searchApi.getSearchAlbumInfo(params)
    if (version !== searchVersion) return
    albums.value = (res.data?.list || []).filter(item => item?.id)
    total.value = Number(res.data?.total) || 0
  } catch {
    if (version === searchVersion) failed.value = true
  } finally {
    if (version === searchVersion) loading.value = false
  }
}

// 只保留 ES 高亮标记的文本，不将服务端标题作为可执行 HTML 插入页面。
function titleParts(value) {
  const parsed = new DOMParser().parseFromString(String(value || ''), 'text/html')
  const result = []
  function visit(node, highlight = false) {
    if (node.nodeType === 3) { result.push({ text: node.textContent || '', highlight }); return }
    if (['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT'].includes(node.nodeName)) return
    for (const child of node.childNodes) visit(child, highlight || ['EM', 'MARK', 'SPAN'].includes(node.nodeName))
  }
  visit(parsed.body)
  return result
}
function plainTitle(value) { return titleParts(value).map(part => part.text).join('') }

watch(() => route.query, () => {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  searched.value = false
  albums.value = []
  if (keyword.value || route.query.category1Id || route.query.category3Id) onSearch()
}, { immediate: true })
onBeforeUnmount(() => { clearTimeout(debounceTimer); suggestVersion++; searchVersion++ })
</script>

<style scoped>
.search-page { min-height: 100vh; background: #fff; padding-bottom: 24px; }
button { cursor: pointer; font: inherit; }
.search-action { border: 0; background: none; padding: 0 4px; color: #333; }
.suggest-panel { padding: 0 16px; box-shadow: 0 8px 15px #00000008; }
.suggest-item { display: flex; align-items: center; gap: 10px; width: 100%; border: 0; border-bottom: 1px solid #f5f5f5; text-align: left; background: #fff; padding: 14px 0; color: #555; }
.history-panel { margin: 16px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; }
.panel-head h2 { font-size: 16px; margin: 0 0 16px; }
.panel-head button { background: none; border: 0; padding: 6px; color: #999; }
.history-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.history-tags button { border: 0; padding: 7px 14px; background: #f5f5f5; border-radius: 16px; font-size: 13px; color: #666; }
.hint { font-size: 13px; color: #999; }
.result-toolbar { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-top: 1px solid #f5f5f5; font-size: 12px; color: #999; }
.result-toolbar select { max-width: 140px; padding: 5px; border: 0; color: #555; background: #fff; }
.loading-wrap { padding: 48px 0; text-align: center; }
.album-item { display: flex; gap: 12px; margin: 0 16px; padding: 14px 0; border-bottom: 1px solid #f4f4f4; color: inherit; text-decoration: none; }
.cover { width: 88px; height: 88px; border-radius: 6px; object-fit: cover; flex-shrink: 0; background: #f5f5f5; }
.album-info { flex: 1; min-width: 0; }
.album-title { margin: 0; font-size: 15px; font-weight: 500; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.album-title mark { background: none; color: #ff3547; }
.album-info p, .play-count { font-size: 12px; color: #999; line-height: 1.6; }
.album-info p { margin: 5px 0; }
.result-area :deep(.van-pagination) { margin: 24px 16px; }
button:focus-visible, a:focus-visible, select:focus-visible { outline: 2px solid #ff3547; outline-offset: 3px; }
</style>
