<template>
  <main class="rank-page">
    <van-nav-bar title="排行榜" />
    <div v-if="categoryLoading" class="page-status" role="status"><van-loading size="22">读取分类中…</van-loading></div>
    <div v-else-if="categoryError" class="page-status error-state" role="alert"><p>{{ categoryError }}</p><van-button plain size="small" color="#007aff" @click="loadCategories">重新加载</van-button></div>
    <van-empty v-else-if="!categories.length" description="暂无可用分类" />
    <template v-else>
      <nav class="category-tabs" aria-label="一级分类">
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          :class="{ active: String(category.id) === categoryId }"
          :aria-pressed="String(category.id) === categoryId"
          @click="changeCategory(category.id)"
        >{{ category.name }}</button>
      </nav>

      <div class="page rank-body">
        <div class="ranking-layout">
          <nav class="dimension-menu" aria-label="榜单类型">
            <button
              v-for="item in dimensions"
              :key="item.key"
              type="button"
              :class="{ active: dimension === item.key }"
              :aria-pressed="dimension === item.key"
              @click="changeDimension(item.key)"
            >{{ item.name }}榜</button>
          </nav>

          <section class="ranking-content" :aria-label="`${currentDimension.name}排行榜`" :aria-busy="rankingLoading">
            <div class="section-head">
              <h1 class="section-title">{{ currentDimension.name }}榜</h1>
              <span class="rank-summary">{{ currentCategoryName }} · <span class="num">{{ rankings.length }}</span> 个专辑</span>
            </div>

            <div v-if="rankingLoading" class="page-status" role="status"><van-loading size="20">读取榜单中…</van-loading></div>
            <div v-else-if="rankingError" class="page-status error-state" role="alert"><p>{{ rankingError }}</p><van-button plain size="small" color="#007aff" @click="loadRanking">重新加载</van-button></div>
            <van-empty v-else-if="!rankings.length" image-size="70" description="该分类暂无榜单数据" />
            <ol v-else class="ranking-list">
              <li v-for="(album, index) in rankings" :key="album.id" class="ranking-item">
                <router-link
                  class="ranking-link"
                  :to="`/detail/${encodeURIComponent(album.id)}`"
                  :aria-label="`第 ${index + 1} 名：${plainTitle(album.albumTitle)}`"
                >
                  <span class="rank-number" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
                  <span class="cover-wrap">
                    <img
                      v-if="!coverBroken(album.coverUrl)"
                      :src="album.coverUrl"
                      :alt="plainTitle(album.albumTitle)"
                      loading="lazy"
                      @error="markBroken(album.coverUrl)"
                    />
                    <span v-else class="cover-fallback">{{ firstChar(album.albumTitle) }}</span>
                  </span>
                  <div class="album-info">
                    <h2 class="album-title">
                      <template v-for="(part, partIndex) in titleParts(album.albumTitle || '未提供专辑标题')" :key="partIndex"><em v-if="part.highlight" class="hit">{{ part.text }}</em><span v-else>{{ part.text }}</span></template>
                    </h2>
                    <span class="announcer"><van-icon name="user-o" /> {{ album.announcerName || '主播未提供' }}</span>
                    <span class="album-stats">
                      <span class="stat"><van-icon name="play-circle-o" /><span class="num">{{ formatCount(album.playStatNum) }}</span> 播放</span>
                      <span class="stat"><van-icon name="like-o" /><span class="num">{{ formatCount(album.subscribeStatNum) }}</span> 订阅</span>
                      <span class="stat" v-if="album.includeTrackCount != null">{{ album.includeTrackCount }} 集</span>
                    </span>
                    <span class="album-tags">
                      <span v-if="currentCategoryName" class="tag tag-free">{{ currentCategoryName }}</span>
                      <span v-if="album.payType === '0102'" class="tag tag-vip">VIP 免费</span>
                      <span v-else-if="album.payType === '0103'" class="tag tag-paid">付费专辑</span>
                      <span v-else class="tag tag-free">免费</span>
                    </span>
                  </div>
                </router-link>
              </li>
            </ol>
          </section>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { categoryApi } from '../../api/category.js'
import { searchApi } from '../../api/search.js'
import { plainTitle, titleParts } from '../../utils/format.js'

const categories = ref([])
const categoryId = ref('')
const categoryLoading = ref(false)
const categoryError = ref('')
const rankings = ref([])
const rankingLoading = ref(false)
const rankingError = ref('')
const dimension = ref('hotScore')
const dimensions = [
  { key: 'hotScore', name: '热度' }, { key: 'playStatNum', name: '播放量' },
  { key: 'subscribeStatNum', name: '订阅量' }, { key: 'buyStatNum', name: '购买量' },
  { key: 'commentStatNum', name: '评论数' }
]
const currentDimension = computed(() => dimensions.find(item => item.key === dimension.value))
const currentCategoryName = computed(() => categories.value.find(item => String(item.id) === categoryId.value)?.name || '')
const brokenCovers = reactive(new Set())
let requestVersion = 0
let disposed = false

function coverBroken(url) { return !url || brokenCovers.has(url) }
function markBroken(url) { if (url) brokenCovers.add(url) }
function firstChar(value) {
  const text = plainTitle(value).trim()
  return text ? text.slice(0, 1) : '知'
}
function formatCount(value) {
  const count = Number(value)
  if (!Number.isFinite(count)) return '—'
  return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : String(count)
}
function errorText(error) { return error?.response?.data?.message || (error?.isAxiosError ? '网络异常，请稍后重试' : error?.message) || '榜单读取失败，请重试' }

async function loadCategories() {
  if (categoryLoading.value) return
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const response = await categoryApi.findAllCategory1()
    if (disposed) return
    if (response?.code !== 200 || !Array.isArray(response.data)) throw new Error(response?.message || '分类数据格式异常')
    categories.value = response.data.filter(item => item?.id != null)
    if (!categories.value.some(item => String(item.id) === categoryId.value)) categoryId.value = String(categories.value[0]?.id || '')
    if (categoryId.value) await loadRanking()
  } catch (error) {
    if (!disposed) categoryError.value = errorText(error)
  } finally {
    if (!disposed) categoryLoading.value = false
  }
}

async function loadRanking() {
  if (!categoryId.value || disposed) return
  const version = ++requestVersion
  rankingLoading.value = true
  rankingError.value = ''
  rankings.value = []
  try {
    const response = await searchApi.findRankingList(categoryId.value, dimension.value)
    if (disposed || version !== requestVersion) return
    if (response?.code !== 200 || !Array.isArray(response.data)) throw new Error(response?.message || '榜单数据格式异常')
    rankings.value = response.data.filter(item => item?.id != null)
  } catch (error) {
    if (!disposed && version === requestVersion) rankingError.value = errorText(error)
  } finally {
    if (!disposed && version === requestVersion) rankingLoading.value = false
  }
}
function changeCategory(id) {
  if (String(id) === categoryId.value) return
  categoryId.value = String(id)
  loadRanking()
}
function changeDimension(key) {
  if (key === dimension.value || !dimensions.some(item => item.key === key)) return
  dimension.value = key
  loadRanking()
}
onMounted(loadCategories)
onBeforeUnmount(() => { disposed = true; requestVersion += 1 })
</script>

<style scoped>
.rank-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-5);
}

/* 一级分类：手机横向滚动 */
.category-tabs {
  display: flex;
  gap: var(--sp-2);
  overflow-x: auto;
  padding: var(--sp-3);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  scrollbar-width: none;
}
.category-tabs::-webkit-scrollbar { display: none; }
.category-tabs button {
  flex-shrink: 0;
  height: 28px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-meta);
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}
.category-tabs button.active {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand);
  font-weight: 500;
}

.rank-body { padding-top: var(--sp-4); }
.ranking-layout { display: block; }

/* 榜单维度：手机为横向筛选条 */
.dimension-menu {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
  overflow-x: auto;
  scrollbar-width: none;
}
.dimension-menu::-webkit-scrollbar { display: none; }
.dimension-menu button {
  flex-shrink: 0;
  height: 30px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-meta);
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;
}
.dimension-menu button.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-weight: 500;
}

.ranking-content { min-width: 0; }
.rank-summary {
  font-size: var(--fs-meta);
  color: var(--ink-3);
}
.rank-summary .num { color: var(--ink); }

/* 榜单列表：手机单列，桌面两列 */
.ranking-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-3);
}
.ranking-link {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  color: inherit;
}
.ranking-link:hover { border-color: var(--brand-line); }

/* 名次块：前 3 名使用三档等级色 */
.rank-number {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  background: var(--ink-4);
  color: #fff;
  font-size: var(--fs-micro);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.rank-number.rank-1 { background: var(--brand); }
.rank-number.rank-2 { background: var(--vip); }
.rank-number.rank-3 { background: var(--ink-3); }

.cover-wrap {
  width: 58px;
  height: 72px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: var(--r-md);
  background: var(--line-soft);
}
.cover-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-fallback {
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink-4);
}

.album-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.album-title {
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
.announcer {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--fs-meta);
  color: var(--ink-3);
}
.album-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1) var(--sp-3);
  font-size: var(--fs-meta);
  color: var(--ink-3);
}
.album-stats .stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.album-stats .num { color: var(--ink-2); }
.album-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
}

.page-status {
  padding: var(--sp-6) var(--sp-3);
  text-align: center;
  font-size: var(--fs-meta);
  line-height: 1.7;
  color: var(--ink-3);
}
.error-state p { margin: 0 0 var(--sp-3); }

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .rank-page :deep(.van-nav-bar) {
    background: var(--surface);
    border-bottom: 1px solid var(--line);
  }
  .rank-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }
  .category-tabs {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-4) var(--sp-5);
  }
  .ranking-layout {
    display: grid;
    grid-template-columns: 176px minmax(0, 1fr);
    gap: var(--sp-5);
    align-items: start;
  }
  .dimension-menu {
    flex-direction: column;
    overflow: visible;
    margin-bottom: 0;
    padding: var(--sp-2);
    background: var(--surface);
    border: 1px solid var(--line);
    border-radius: var(--r-lg);
    position: sticky;
    top: 80px;
  }
  .dimension-menu button {
    width: 100%;
    height: 34px;
    text-align: left;
    border: 0;
    background: transparent;
  }
  .dimension-menu button.active {
    background: var(--brand-soft);
    color: var(--brand);
  }
  .ranking-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
  }
}
</style>
