<template>
  <main class="rank-page">
    <van-nav-bar title="排行榜" />
    <div v-if="categoryLoading" class="page-status" role="status"><van-loading size="22">读取分类中…</van-loading></div>
    <div v-else-if="categoryError" class="page-status error-state" role="alert"><p>{{ categoryError }}</p><van-button plain size="small" color="#07c160" @click="loadCategories">重新加载</van-button></div>
    <van-empty v-else-if="!categories.length" description="暂无可用分类" />
    <template v-else>
      <nav class="category-tabs" aria-label="一级分类">
        <button v-for="category in categories" :key="category.id" type="button" :class="{ active: String(category.id) === categoryId }" :aria-pressed="String(category.id) === categoryId" @click="changeCategory(category.id)">{{ category.name }}</button>
      </nav>
      <div class="ranking-layout">
        <nav class="dimension-menu" aria-label="榜单类型">
          <button v-for="item in dimensions" :key="item.key" type="button" :class="{ active: dimension === item.key }" :aria-pressed="dimension === item.key" @click="changeDimension(item.key)">{{ item.name }}</button>
        </nav>
        <section class="ranking-content" :aria-label="`${currentDimension.name}排行榜`" :aria-busy="rankingLoading">
          <h1>{{ currentDimension.name }}榜</h1>
          <div v-if="rankingLoading" class="page-status" role="status"><van-loading size="20">读取榜单中…</van-loading></div>
          <div v-else-if="rankingError" class="page-status error-state" role="alert"><p>{{ rankingError }}</p><van-button plain size="small" color="#07c160" @click="loadRanking">重新加载</van-button></div>
          <van-empty v-else-if="!rankings.length" image-size="70" description="该分类暂无榜单数据" />
          <ol v-else class="ranking-list">
            <li v-for="(album, index) in rankings" :key="album.id" class="ranking-item">
              <router-link :to="`/detail/${encodeURIComponent(album.id)}`" :aria-label="`第 ${index + 1} 名：${plainText(album.albumTitle)}`">
                <span class="rank-number" :class="{ podium: index < 3, first: index === 0 }">{{ index + 1 }}</span>
                <van-image class="album-cover" :src="album.coverUrl" fit="cover" radius="4"><template #error><van-icon name="music-o" size="24" /></template></van-image>
                <div class="album-info">
                  <h2><template v-for="(part, partIndex) in highlightParts(album.albumTitle || '未提供专辑标题')" :key="partIndex"><mark v-if="part.highlight">{{ part.text }}</mark><span v-else>{{ part.text }}</span></template></h2>
                  <p class="announcer">{{ plainText(album.announcerName || '主播名称未提供') }}</p>
                  <p class="album-meta"><span v-if="album.includeTrackCount != null">{{ album.includeTrackCount }} 集</span><span v-if="album[dimension] != null">{{ currentDimension.name }} {{ formatCount(album[dimension]) }}</span><span v-else-if="dimension === 'hotScore'">热度排行</span></p>
                  <span v-if="album.payType === '0102'" class="vip-label">VIP 免费</span><span v-else-if="album.payType === '0103'" class="paid-label">付费专辑</span>
                </div>
              </router-link>
            </li>
          </ol>
        </section>
      </div>
    </template>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { categoryApi } from '../../api/category.js'
import { searchApi } from '../../api/search.js'

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
let requestVersion = 0
let disposed = false

function plainText(value) { return String(value || '').replace(/<[^>]*>/g, '') }
function highlightParts(value) {
  let highlight = false
  return String(value).split(/(<\/?(?:em|font|span)\b[^>]*>)/gi).flatMap(part => {
    if (/^<\/?(?:em|font|span)\b[^>]*>$/i.test(part)) {
      highlight = !/^<\//.test(part)
      return []
    }
    return part ? [{ text: plainText(part), highlight }] : []
  })
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
.rank-page { min-height: 100vh; background: #fff; padding-bottom: 12px; box-sizing: border-box; color: #323233; }
.category-tabs { display: flex; overflow-x: auto; border-bottom: 1px solid #ebedf0; padding: 0 10px; scrollbar-width: none; }
.category-tabs::-webkit-scrollbar { display: none; }
.category-tabs button { position: relative; flex-shrink: 0; min-height: 48px; padding: 0 15px; border: 0; background: #fff; color: #969799; font: inherit; font-size: 14px; cursor: pointer; }
.category-tabs button.active { color: #323233; font-weight: 600; }
.category-tabs button.active::after { content: ''; position: absolute; left: calc(50% - 10px); bottom: 0; width: 20px; height: 2px; background: #ee0a24; }
.ranking-layout { display: flex; align-items: stretch; min-height: calc(100vh - 160px); }
.dimension-menu { width: 100px; flex-shrink: 0; background: #f6f6f6; }
.dimension-menu button { position: relative; width: 100%; display: block; height: 50px; padding: 0 8px; border: 0; background: #f6f6f6; font: inherit; font-size: 13px; color: #444; cursor: pointer; }
.dimension-menu button.active { color: #000; font-weight: 600; background: #fff; }
.dimension-menu button.active::before { content: ''; position: absolute; left: 0; top: 19px; height: 16px; width: 3px; background: #ee0a24; }
.ranking-content { min-width: 0; flex: 1; padding: 16px 12px; }
.ranking-content h1 { font-size: 16px; margin: 0 0 14px; }
.ranking-list { list-style: none; padding: 0; margin: 0; }
.ranking-item { margin-bottom: 16px; }
.ranking-item a { position: relative; display: flex; align-items: flex-start; gap: 9px; color: inherit; text-decoration: none; }
.rank-number { position: absolute; top: 0; left: 0; z-index: 1; min-width: 15px; padding: 2px 3px; text-align: center; font-size: 11px; color: #fff; background: #969799; border-radius: 4px 0 4px 0; }
.rank-number.podium { background: #ef9b43; font-weight: 600; }
.rank-number.first { background: #ee0a24; }
.album-cover { width: 62px; height: 76px; flex-shrink: 0; background: #f7f8fa; }
.album-info { flex: 1; min-width: 0; }
.album-info h2 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin: 0 0 6px; font-size: 13px; line-height: 1.5; font-weight: 500; overflow-wrap: anywhere; }
.album-info mark { color: #ee0a24; background: none; }
.announcer { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; margin: 0 0 5px; color: #969799; font-size: 11px; }
.album-meta { display: flex; gap: 6px; flex-wrap: wrap; margin: 0; font-size: 10px; color: #969799; line-height: 1.7; }
.vip-label, .paid-label { display: inline-block; font-size: 10px; margin-top: 5px; padding: 1px 4px; border-radius: 2px; color: #a76732; background: #fff6ed; }
.paid-label { color: #ee0a24; background: #fff2f3; }
.page-status { text-align: center; padding: 28px 6px; color: #969799; font-size: 12px; line-height: 1.7; }
button:focus-visible, a:focus-visible { outline: 2px solid #07c160; outline-offset: 2px; }
@media (max-width: 360px) { .dimension-menu { width: 76px; } .ranking-content { padding: 14px 8px; } .album-cover { width: 54px; height: 68px; } .ranking-item a { gap: 7px; } }
</style>
