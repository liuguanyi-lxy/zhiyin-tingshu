<template>
  <main class="creator-page">
    <van-nav-bar title="我的作品" left-arrow @click-left="back">
      <template #right><router-link to="/index" aria-label="回到首页"><van-icon name="wap-home-o" size="20" /></router-link></template>
    </van-nav-bar>
    <van-tabs v-model:active="tab" color="#008aff" title-active-color="#008aff" @change="resetList">
      <van-tab title="专辑" name="album" />
      <van-tab title="声音" name="track" />
      <template v-if="!simple">
        <van-tab title="视频" name="video" />
        <van-tab title="有声PPT" name="ppt" />
        <van-tab title="咔嚓笔记" name="note" />
      </template>
    </van-tabs>
    <template v-if="supported">
      <div class="filter-bar">
        <label class="status-filter">审核状态
          <select v-model="statuses[tab]" @change="resetList">
            <option value="">全部</option>
            <option :value="tab === 'album' ? '0301' : '0501'">已通过</option>
            <option :value="tab === 'album' ? '0302' : '0502'">未通过</option>
          </select>
        </label>
        <van-button size="small" type="primary" color="#008aff" icon="plus" @click="createItem">新建{{ tab === 'album' ? '专辑' : '声音' }}</van-button>
      </div>
      <form class="search-bar" @submit.prevent="resetList">
        <input v-model="keyword" type="search" :aria-label="`搜索${tab === 'album' ? '专辑' : '声音'}标题`" placeholder="搜索作品标题" />
        <button type="submit">搜索</button>
      </form>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <van-pull-refresh v-model="refreshing" @refresh="refresh">
        <van-list v-model:loading="loading" v-model:error="listError" :finished="finished" :immediate-check="false" error-text="加载失败，点击重试" :finished-text="items.length ? '没有更多作品了' : ''" @load="loadMore">
          <article v-for="item in items" :key="itemId(item)" class="work-card" :aria-busy="busyId === itemId(item)">
            <button class="card-main" type="button" @click="openItem(item)">
              <van-image class="cover" :src="item.coverUrl || ''" fit="cover" width="64" height="64" radius="8"><template #error><van-icon name="music-o" size="26" /></template></van-image>
              <div class="card-copy">
                <h2>{{ tab === 'album' ? item.albumTitle : item.trackTitle }}</h2>
                <p>{{ tab === 'album' ? `${item.includeTrackCount ?? '—'} 个声音` : item.albumTitle || '所属专辑未返回' }}</p>
                <span class="status" :class="{ rejected: ['0302', '0502'].includes(String(item.status)) }">{{ statusText(item.status) }}</span>
              </div>
              <van-icon name="arrow" color="#999" />
            </button>
            <div class="metrics" aria-label="作品数据">
              <span v-if="tab === 'track'"><van-icon name="clock-o" /> {{ duration(item.mediaDuration) }}</span>
              <span><van-icon name="service-o" /> {{ item.playStatNum ?? '—' }} 播放</span>
              <span><van-icon :name="tab === 'album' ? 'cart-o' : 'star-o'" /> {{ (tab === 'album' ? item.buyStatNum : item.collectStatNum) ?? '—' }} {{ tab === 'album' ? '购买' : '收藏' }}</span>
              <span><van-icon name="comment-o" /> {{ item.commentStatNum ?? '—' }}</span>
            </div>
            <footer class="card-actions">
              <button type="button" @click="share(item)"><van-icon name="share-o" /> 分享</button>
              <button v-if="tab === 'album'" type="button" @click="openItem(item)"><van-icon name="bar-chart-o" /> 管理</button>
              <button type="button" @click="editItem(item)"><van-icon name="edit" /> 编辑</button>
              <button type="button" class="danger" :disabled="!!busyId" @click="removeItem(item)"><van-icon name="delete-o" /> 删除</button>
            </footer>
          </article>
          <van-empty v-if="!loading && !items.length && !listError" description="还没有符合条件的作品">
            <van-button type="primary" color="#008aff" size="small" @click="createItem">创建{{ tab === 'album' ? '专辑' : '声音' }}</van-button>
          </van-empty>
        </van-list>
      </van-pull-refresh>
      <div v-if="listError && !items.length" class="retry"><van-button size="small" @click="retry">重新加载</van-button></div>
    </template>
    <van-empty v-else :description="`${unsupportedName}创作尚未就绪`">
      <p class="unavailable">当前服务仅提供专辑、声音接口，暂不能创建或管理{{ unsupportedName }}。</p>
      <van-button size="small" color="#008aff" @click="tab = 'album'; resetList()">管理专辑</van-button>
    </van-empty>
    <van-popup v-model:show="shareVisible" round position="bottom" closeable class="share-sheet">
      <h2>分享作品</h2><p>可复制以下链接分享给好友</p>
      <input :value="shareUrl" readonly aria-label="作品分享链接" @focus="$event.target.select()" />
      <van-button block color="#008aff" @click="copyShareLink">复制链接</van-button>
      <p v-if="shareError" role="alert">{{ shareError }}</p>
    </van-popup>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { albumsApi } from '../../api/albums.js'

const props = defineProps({ simple: { type: Boolean, default: false } })
const route = useRoute()
const router = useRouter()
const tab = ref(route.query.tab === 'track' ? 'track' : 'album')
const statuses = ref({ album: '', track: '' })
const keyword = ref('')
const items = ref([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const listError = ref(false)
const error = ref('')
const busyId = ref('')
const shareVisible = ref(false)
const shareUrl = ref('')
const shareError = ref('')
const supported = computed(() => ['album', 'track'].includes(tab.value))
const unsupportedName = computed(() => ({ video: '视频', ppt: '有声PPT', note: '咔嚓笔记' }[tab.value]))
let page = 1
let generation = 0
let alive = true
const limit = 10
const message = e => e?.response?.data?.message || e?.message || '加载失败，请检查网络后重试'
const itemId = item => String(tab.value === 'album' ? item.albumId : item.trackId)
const statusText = value => ({ '0301': '已通过', '0302': '未通过', '0501': '已通过', '0502': '未通过' }[value] || (value ? `未知状态（${value}）` : '状态未返回'))
function duration(value) {
  if (value == null || !Number.isFinite(Number(value))) return '时长未返回'
  const seconds = Math.floor(Number(value))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
function back() { window.history.state?.back ? router.back() : router.replace('/my') }
function createItem() { router.push(tab.value === 'album' ? '/createAlbum' : '/createTrack') }
function editItem(item) { router.push(`/${tab.value === 'album' ? 'createAlbum' : 'createTrack'}/${itemId(item)}`) }
function openItem(item) { router.push(tab.value === 'album' ? `/album/${item.albumId}` : `/player/${item.trackId}`) }
function resetList() {
  generation++
  page = 1
  items.value = []
  error.value = ''
  loading.value = false
  listError.value = false
  finished.value = !supported.value
  if (supported.value) loadMore()
}
function refresh() { resetList() }
function retry() { listError.value = false; loadMore() }
async function loadMore() {
  if (!supported.value || finished.value) { loading.value = false; refreshing.value = false; return }
  const version = generation
  const activeTab = tab.value
  loading.value = true
  listError.value = false
  error.value = ''
  try {
    const params = { page, limit, status: statuses.value[activeTab], [activeTab === 'album' ? 'albumTitle' : 'trackTitle']: keyword.value.trim() }
    const { data } = await (activeTab === 'album' ? albumsApi.getAlbumList(params) : albumsApi.getTrackList(params))
    if (!alive || version !== generation) return
    if (!Array.isArray(data?.records)) throw new Error('作品列表响应不完整，请重试')
    items.value.push(...data.records)
    finished.value = data.records.length < limit || (data.total != null && items.value.length >= Number(data.total))
    page++
  } catch (e) {
    if (alive && version === generation) { error.value = message(e); listError.value = true }
  } finally {
    if (alive && version === generation) { loading.value = false; refreshing.value = false }
  }
}
async function removeItem(item) {
  const isAlbum = tab.value === 'album'
  const id = itemId(item)
  try {
    await showConfirmDialog({ title: `删除${isAlbum ? '专辑' : '声音'}`, message: isAlbum ? '删除后不可恢复。包含声音的专辑不能删除，是否继续？' : '将同时删除声音及其 VOD 媒体文件，无法恢复，是否继续？', confirmButtonColor: '#ee0a24' })
  } catch { return }
  busyId.value = id
  try {
    await (isAlbum ? albumsApi.deleteAlbumInfo(id) : albumsApi.deleteTrackInfo(id))
    showSuccessToast('删除成功')
    resetList()
  } catch (e) { error.value = message(e) }
  finally { busyId.value = '' }
}
function share(item) {
  const target = router.resolve(tab.value === 'album' ? `/detail/${item.albumId}` : `/player/${item.trackId}`)
  shareUrl.value = new URL(target.href, window.location.href).href
  shareError.value = ''
  shareVisible.value = true
}
async function copyShareLink() {
  try { await navigator.clipboard.writeText(shareUrl.value); showSuccessToast('链接已复制') }
  catch { shareError.value = '当前浏览器不支持自动复制，请长按上方链接手动复制。' }
}
onBeforeUnmount(() => { alive = false; generation++ })
resetList()
</script>

<style scoped>
.creator-page { --van-primary-color: #008aff; min-height: 100vh; color: #333; background: #f8f8f8; padding-bottom: max(20px, env(safe-area-inset-bottom)); }
.creator-page :deep(.van-nav-bar__text), .creator-page :deep(.van-nav-bar .van-icon), .creator-page a { color: #008aff; }
.filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 15px 6px; background: white; }
.status-filter { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #999; }
.status-filter select { border: 0; background: white; padding: 8px 4px; color: #333; font-size: 14px; max-width: 130px; }
.search-bar { display: flex; gap: 10px; padding: 6px 15px 12px; background: white; }
.search-bar input { min-width: 0; flex: 1; border: 0; border-radius: 5px; background: #f8f8f8; padding: 10px; font: inherit; font-size: 13px; }
.search-bar button { border: 0; background: white; color: #008aff; padding: 0 8px; }
.work-card { margin: 15px; padding: 12.5px; background: #fff; border-radius: 6px; box-shadow: 0 0 1.5px #999; }
.card-main { width: 100%; display: flex; gap: 12.5px; text-align: left; align-items: center; border: 0; padding: 0; background: none; color: inherit; }
.cover { flex-shrink: 0; }
.card-copy { min-width: 0; flex: 1; }
h2 { font-size: 14px; line-height: 20px; margin: 0 0 5px; overflow-wrap: anywhere; }
.card-copy p { font-size: 12px; color: #999; margin: 4px 0; overflow-wrap: anywhere; }
.status { color: #008aff; background: #edf7ff; border-radius: 3px; padding: 2px 5px; font-size: 10px; }
.status.rejected { color: #ee0a24; background: #fff0f0; }
.metrics { display: flex; flex-wrap: wrap; gap: 8px 12px; font-size: 11px; color: #999; padding: 12px 0; border-bottom: .5px solid #e5e5e5; }
.card-actions { display: flex; margin-top: 12.5px; }
.card-actions button { flex: 1; min-height: 32px; font-size: 13px; color: #666; border: 0; border-right: .5px solid #eee; background: none; }
.card-actions button:last-child { border-right: 0; }
.card-actions .danger { color: #bd3b46; }
button:disabled { opacity: .5; }
button, select { cursor: pointer; }
button:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid #008aff; outline-offset: 2px; }
.error-message { padding: 12px 15px; color: #b42318; font-size: 13px; background: #fff2f0; margin: 10px 15px; border-radius: 5px; }
.retry { text-align: center; padding: 15px; }
.unavailable { max-width: 270px; text-align: center; color: #999; font-size: 13px; line-height: 1.7; }
.share-sheet { box-sizing: border-box; padding: 24px 20px calc(20px + env(safe-area-inset-bottom)); max-width: 480px; left: 50%; transform: translateX(-50%); }
.share-sheet h2 { font-size: 17px; }
.share-sheet p { font-size: 13px; color: #666; }
.share-sheet input { box-sizing: border-box; width: 100%; padding: 12px; margin: 10px 0 15px; border: 1px solid #e5e5e5; border-radius: 5px; }
@media (max-width: 350px) { .work-card { margin: 10px; } .metrics { gap: 8px; } }
</style>
