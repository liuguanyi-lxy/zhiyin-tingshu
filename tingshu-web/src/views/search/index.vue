<template>
  <div class="search-page">
    <van-nav-bar :title="categoryTitle || '搜索'" left-arrow fixed placeholder @click-left="router.back()" />

    <div class="search-head">
      <div class="search-head-inner">
        <van-search v-model="keyword" placeholder="搜索专辑 / 主播" show-action @update:model-value="onInput" @search="onSearch">
          <template #action><button class="search-action" type="button" @click="onSearch">搜索</button></template>
        </van-search>
        <div v-if="suggests.length" class="suggest-panel">
          <button v-for="(suggestion, index) in suggests" :key="index" type="button" class="suggest-item" @click="selectKeyword(suggestion)">
            <van-icon name="search" /><span>{{ suggestion }}</span>
          </button>
        </div>
      </div>
    </div>

    <section v-if="!searched" class="page history-panel">
      <div class="panel-head">
        <h2 class="section-title">搜索历史</h2>
        <button v-if="history.length" type="button" class="section-action" aria-label="清空搜索历史" @click="clearHistory">
          <van-icon name="delete-o" />
        </button>
      </div>
      <div v-if="history.length" class="history-tags">
        <button v-for="item in history" :key="item" type="button" @click="selectKeyword(item)">{{ item }}</button>
      </div>
      <p v-else class="hint">输入书名、主播，找到你想听的内容</p>
    </section>

    <template v-else>
      <div class="page result-page">
        <div class="result-toolbar">
          <span class="result-count"><span class="num">{{ total }}</span> 个专辑</span>
          <div class="filter-tags" role="group" aria-label="排序筛选">
            <button
              v-for="option in sortOptions"
              :key="option.value"
              type="button"
              class="filter-tag"
              :class="{ active: sort === option.value }"
              :aria-pressed="sort === option.value"
              @click="setSort(option.value)"
            >{{ option.name }}</button>
          </div>
        </div>

        <van-loading v-if="loading" class="loading-wrap">搜索中...</van-loading>
        <van-empty v-else-if="failed" image="network" description="搜索失败，请稍后重试">
          <van-button size="small" plain @click="fetchPage">重试</van-button>
        </van-empty>
        <template v-else>
          <div v-if="albums.length" class="result-grid">
            <router-link v-for="album in albums" :key="album.id" class="album-card" :to="`/detail/${album.id}`">
              <div class="cover-wrap">
                <img
                  v-if="!coverBroken(album.coverUrl)"
                  :src="album.coverUrl"
                  :alt="plainTitle(album.albumTitle)"
                  loading="lazy"
                  @error="markBroken(album.coverUrl)"
                />
                <span v-else class="cover-fallback">{{ firstChar(album.albumTitle) }}</span>
                <span v-if="payTags[album.payType]" class="pay-tag" :class="payClasses[album.payType]">{{ payTags[album.payType] }}</span>
              </div>
              <h2 class="album-title">
                <template v-for="(part, index) in titleParts(album.albumTitle)" :key="index"><em v-if="part.highlight" class="hit">{{ part.text }}</em><span v-else>{{ part.text }}</span></template>
              </h2>
              <p class="album-meta">{{ album.includeTrackCount ?? 0 }} 集 · {{ album.announcerName || '未知主播' }}</p>
              <p class="album-stat"><van-icon name="play-circle-o" /><span class="num">{{ formatCount(album.playStatNum) }}</span> 次播放</p>
            </router-link>
          </div>
          <van-empty v-else description="没有找到相关专辑，换个关键词试试" />
          <van-pagination
            v-if="total > pageSize"
            v-model="page"
            :total-items="total"
            :items-per-page="pageSize"
            :show-page-size="3"
            force-ellipses
            @change="fetchPage"
          />
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '../../api/search.js'
import { plainTitle, titleParts } from '../../utils/format.js'

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
const sortOptions = [
  { value: '', name: '综合排序' },
  { value: '2:desc', name: '播放最多' },
  { value: '3:desc', name: '最近更新' }
]
const payTags = { '0101': '免费', '0102': 'VIP 免费', '0103': '付费' }
const payClasses = { '0101': 'tag-free', '0102': 'tag-vip', '0103': 'tag-paid' }
const categoryTitle = computed(() => typeof route.query.pageTitle === 'string' ? route.query.pageTitle : '')
const history = ref(readHistory())
const brokenCovers = reactive(new Set())
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
function setSort(value) {
  if (sort.value === value) return
  sort.value = value
  onSearch()
}
function coverBroken(url) { return !url || brokenCovers.has(url) }
function markBroken(url) { if (url) brokenCovers.add(url) }
function firstChar(value) {
  const text = plainTitle(value).trim()
  return text ? text.slice(0, 1) : '知'
}
function formatCount(value) {
  const count = Number(value) || 0
  return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : count.toLocaleString()
}

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

watch(() => route.query, () => {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  searched.value = false
  albums.value = []
  if (keyword.value || route.query.category1Id || route.query.category3Id) onSearch()
}, { immediate: true })
onBeforeUnmount(() => { clearTimeout(debounceTimer); suggestVersion++; searchVersion++ })
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-5);
}

/* 搜索框 */
.search-head {
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}
.search-head-inner { max-width: var(--shell-max); margin: 0 auto; }
.search-head :deep(.van-search) {
  background: transparent;
  padding: var(--sp-3);
}
.search-action {
  border: 0;
  background: none;
  padding: 0 var(--sp-2);
  color: var(--brand);
  font-size: var(--fs-body);
  font-weight: 500;
}
.suggest-panel { border-top: 1px solid var(--line-soft); }
.suggest-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: var(--sp-3);
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--ink-2);
  font-size: var(--fs-body);
}
.suggest-item + .suggest-item { border-top: 1px solid var(--line-soft); }
.suggest-item .van-icon { color: var(--ink-4); }

/* 搜索历史 */
.history-panel { padding-top: var(--sp-4); }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}
.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}
.history-tags button {
  height: 28px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-meta);
}
.history-tags button:hover {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand);
}
.hint {
  margin: 0;
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

/* 结果工具条 */
.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-3);
  padding: var(--sp-4) 0 var(--sp-3);
}
.result-count {
  font-size: var(--fs-meta);
  color: var(--ink-3);
}
.result-count .num { color: var(--ink); }
.filter-tags {
  display: flex;
  gap: var(--sp-2);
}
.filter-tag {
  height: 26px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-micro);
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}
.filter-tag.active {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand);
  font-weight: 500;
}

.loading-wrap {
  padding: var(--sp-6) 0;
  text-align: center;
}

/* 结果网格：手机两列，桌面四列 */
.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-4) var(--sp-3);
}
.album-card {
  display: block;
  min-width: 0;
}
.cover-wrap {
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  aspect-ratio: 1;
  border-radius: var(--r-md);
  background: var(--line-soft);
}
.cover-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-fallback {
  font-size: var(--fs-display);
  font-weight: 600;
  color: var(--ink-4);
}
.pay-tag {
  position: absolute;
  top: 6px;
  left: 6px;
}
.album-title {
  margin: var(--sp-2) 0 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  font-size: var(--fs-body);
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink);
}
.hit {
  font-style: normal;
  color: var(--brand);
}
.album-meta {
  margin: 0;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 4px 0 0;
  font-size: var(--fs-micro);
  color: var(--ink-4);
}
.album-stat .num { color: var(--ink-3); }
.result-page :deep(.van-pagination) {
  margin: var(--sp-5) 0 0;
}
.result-page :deep(.van-pagination__item) {
  color: var(--ink-2);
  border-radius: var(--r-sm);
}
.result-page :deep(.van-pagination__item--active) {
  color: var(--brand);
  font-weight: 600;
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .search-page :deep(.van-nav-bar) {
    background: var(--surface);
    border-bottom: 1px solid var(--line);
  }
  .search-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }
  .search-head-inner {
    padding: 0 var(--sp-5);
  }
  .result-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--sp-5) var(--sp-4);
  }
  .history-panel { padding-top: var(--sp-5); }
}

@media (min-width: 1280px) {
  .result-grid { grid-template-columns: repeat(5, minmax(0, 1fr)); }
}
</style>
