<template>
  <main class="album-page">
    <van-nav-bar title="专辑管理" left-arrow @click-left="back" />
    <van-skeleton v-if="initialLoading" title avatar :row="5" class="initial-loading" />
    <van-empty v-else-if="loadError" image="error" :description="loadError"><van-button size="small" color="#008aff" @click="loadAlbum">重新加载</van-button></van-empty>
    <template v-else-if="album">
      <section class="album-hero">
        <van-image width="100" height="100" radius="10" fit="cover" :src="album.coverUrl || ''"><template #error><van-icon name="music-o" size="32" /></template></van-image>
        <div class="album-heading">
          <span class="pay-tag">{{ payLabel }}</span>
          <h1>{{ album.albumTitle }}</h1>
          <p>{{ owner ? '我的专辑' : '专辑信息' }} · {{ String(album.isOpen) === '1' ? '公开' : '私密' }}</p>
          <p>{{ album.includeTrackCount ?? '—' }} 个声音 · {{ String(album.isFinished) === '1' ? '已完结' : '连载中' }}</p>
        </div>
      </section>
      <section class="stats" aria-label="专辑统计">
        <div v-for="metric in metrics" :key="metric.key"><strong>{{ stats?.[metric.key] ?? '—' }}</strong><span>{{ metric.name }}</span></div>
      </section>
      <div v-if="statsError" class="inline-warning" role="alert">统计数据加载失败 <button @click="loadStats">重试</button></div>
      <section class="intro-panel">
        <h2>专辑简介</h2>
        <p class="intro" :class="{ collapsed: !expanded }">{{ album.albumIntro || '暂无简介' }}</p>
        <button v-if="album.albumIntro?.length > 90" class="text-button" @click="expanded = !expanded">{{ expanded ? '收起简介' : '展开简介' }} <van-icon :name="expanded ? 'arrow-up' : 'arrow-down'" /></button>
      </section>
      <div v-if="ownershipError" class="inline-warning" role="alert">无法确认管理权限，暂不可编辑。<button @click="checkOwnership">重试</button></div>
      <section v-if="owner" class="management">
        <van-button size="small" plain color="#008aff" icon="edit" @click="router.push(`/createAlbum/${albumId}`)">编辑专辑</van-button>
        <van-button size="small" plain color="#008aff" :loading="mutating" @click="changeVisibility">{{ String(album.isOpen) === '1' ? '设为私密 / 下架' : '公开 / 上架' }}</van-button>
        <van-button size="small" plain type="danger" :disabled="mutating || Number(album.includeTrackCount) > 0" @click="deleteAlbum">删除专辑</van-button>
        <p v-if="Number(album.includeTrackCount) > 0">包含声音的专辑不能删除。</p>
      </section>
      <p v-if="actionError" class="inline-warning" role="alert">{{ actionError }}</p>
      <section class="episodes">
        <div class="section-heading"><h2>节目（{{ album.includeTrackCount ?? '—' }}）</h2><van-button v-if="owner" size="small" color="#008aff" icon="plus" @click="createTrack">上传声音</van-button></div>
        <p class="list-note">{{ owner ? '显示本专辑全部声音，包含私密和未通过作品。' : '当前非本人专辑，仅展示已通过的公开声音。' }}</p>
        <form v-if="owner" class="track-filter" @submit.prevent="resetTracks">
          <label>状态<select v-model="status" @change="resetTracks"><option value="">全部</option><option value="0501">已通过</option><option value="0502">未通过</option></select></label>
          <input v-model="keyword" type="search" aria-label="搜索节目标题" placeholder="搜索节目标题" />
          <button type="submit" class="text-button">搜索</button>
        </form>
        <van-list v-model:loading="trackLoading" v-model:error="trackError" :finished="tracksFinished" :immediate-check="false" error-text="加载失败，点击重试" :finished-text="tracks.length ? '没有更多节目了' : ''" @load="loadTracks">
          <article v-for="(track, index) in tracks" :key="track.trackId" class="track-row">
            <button class="track-main" @click="router.push(`/player/${track.trackId}`)">
              <span class="track-order">{{ track.orderNum ?? index + 1 }}</span>
              <div><h3>{{ track.trackTitle }}</h3><p><van-icon name="clock-o" /> {{ duration(track.mediaDuration) }} <span v-if="owner"> · {{ trackStatus(track.status) }}</span></p></div>
              <van-icon name="play-circle-o" size="24" color="#008aff" />
            </button>
            <div v-if="owner" class="track-actions"><button @click="router.push(`/createTrack/${track.trackId}`)">编辑</button><button class="danger" :disabled="mutating" @click="deleteTrack(track)">删除</button></div>
          </article>
          <van-empty v-if="!trackLoading && !tracks.length && !trackError" :description="owner ? '本专辑暂无符合条件的声音' : '暂无公开节目'" />
        </van-list>
        <div v-if="trackError" class="track-retry"><p role="alert">{{ trackErrorMessage }}</p><van-button size="small" @click="retryTracks">重试加载节目</van-button></div>
      </section>
      <div class="footer"><van-button block plain color="#008aff" @click="router.push(`/detail/${albumId}`)">查看听众端专辑详情</van-button></div>
    </template>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { albumsApi } from '../../api/albums.js'

const route = useRoute()
const router = useRouter()
const albumId = computed(() => String(route.params.albumId || ''))
const album = ref(null)
const stats = ref(null)
const owner = ref(false)
const ownershipError = ref(false)
const initialLoading = ref(true)
const loadError = ref('')
const statsError = ref(false)
const expanded = ref(false)
const mutating = ref(false)
const actionError = ref('')
const status = ref('')
const keyword = ref('')
const tracks = ref([])
const trackLoading = ref(false)
const tracksFinished = ref(false)
const trackError = ref(false)
const trackErrorMessage = ref('')
const metrics = [{ key: 'playStatNum', name: '播放' }, { key: 'subscribeStatNum', name: '订阅' }, { key: 'buyStatNum', name: '购买' }, { key: 'commentStatNum', name: '评论' }]
const payLabel = computed(() => ({ '0101': '免费', '0102': 'VIP 免费', '0103': '付费' }[album.value?.payType] || '付费类型未返回'))
let albumVersion = 0
let trackVersion = 0
let sourcePage = 1
let sourceFinished = false
let pendingTracks = []
let alive = true
const limit = 10
const errorMessage = e => e?.response?.data?.message || e?.message || '网络异常，请重试'
function back() { window.history.state?.back ? router.back() : router.replace('/myWork') }
function trackStatus(value) { return ({ '0501': '已通过', '0502': '未通过' }[value] || (value ? `未知状态（${value}）` : '状态未返回')) }
function duration(value) {
  if (value == null || !Number.isFinite(Number(value))) return '时长未返回'
  const seconds = Math.floor(Number(value))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
function createTrack() { router.push({ path: '/createTrack', query: { albumId: albumId.value } }) }
async function loadStats() {
  const id = albumId.value
  statsError.value = false
  try { const result = await albumsApi.getAlbumInfoStat(id); if (alive && id === albumId.value) stats.value = result.data }
  catch { if (alive && id === albumId.value) statsError.value = true }
}
async function checkOwnership() {
  const id = albumId.value
  ownershipError.value = false
  try {
    const { data } = await albumsApi.getAllAlbumList()
    if (!Array.isArray(data)) throw new Error('专辑列表响应不完整')
    if (!alive || id !== albumId.value) return
    owner.value = data.some(item => String(item.id) === id)
  } catch { if (alive && id === albumId.value) { owner.value = false; ownershipError.value = true } }
  if (alive && id === albumId.value && album.value) resetTracks()
}
async function loadAlbum() {
  const version = ++albumVersion
  trackVersion++
  initialLoading.value = true
  loadError.value = ''
  album.value = null
  stats.value = null
  owner.value = false
  try {
    const { data } = await albumsApi.getAlbumInfo(albumId.value)
    if (!alive || version !== albumVersion) return
    if (!data?.id) throw new Error('专辑不存在或已删除')
    album.value = data
    await checkOwnership()
    if (alive && version === albumVersion) loadStats()
  } catch (e) { if (alive && version === albumVersion) loadError.value = errorMessage(e) }
  finally { if (alive && version === albumVersion) initialLoading.value = false }
}
function resetTracks() {
  trackVersion++
  sourcePage = 1
  sourceFinished = false
  pendingTracks = []
  tracks.value = []
  trackError.value = false
  tracksFinished.value = false
  trackLoading.value = false
  loadTracks()
}
function retryTracks() { trackError.value = false; loadTracks() }
async function loadTracks() {
  if (tracksFinished.value) { trackLoading.value = false; return }
  const version = trackVersion
  const id = albumId.value
  trackLoading.value = true
  trackError.value = false
  try {
    if (owner.value) {
      // TrackInfoQuery 没有 albumId。分页读取本人声音再筛选，不能拿仅含公开声音的听众接口冒充管理列表。
      while (pendingTracks.length < limit && !sourceFinished) {
        const { data } = await albumsApi.getTrackList({ page: sourcePage, limit: 50, status: status.value, trackTitle: keyword.value.trim() })
        if (!alive || version !== trackVersion || id !== albumId.value) return
        if (!Array.isArray(data?.records)) throw new Error('声音列表响应不完整')
        pendingTracks.push(...data.records.filter(item => String(item.albumId) === id))
        sourceFinished = data.records.length < 50 || (data.total != null && sourcePage * 50 >= Number(data.total))
        sourcePage++
      }
      tracks.value.push(...pendingTracks.splice(0, limit))
      tracksFinished.value = sourceFinished && pendingTracks.length === 0
    } else {
      const { data } = await albumsApi.getAlbumTrackList({ albumId: id, page: sourcePage, limit })
      if (!alive || version !== trackVersion || id !== albumId.value) return
      if (!Array.isArray(data?.records)) throw new Error('节目列表响应不完整')
      tracks.value.push(...data.records)
      tracksFinished.value = data.records.length < limit || (data.total != null && tracks.value.length >= Number(data.total))
      sourcePage++
    }
  } catch (e) { if (alive && version === trackVersion) { trackError.value = true; trackErrorMessage.value = errorMessage(e) } }
  finally { if (alive && version === trackVersion) trackLoading.value = false }
}
async function changeVisibility() {
  if (!owner.value || mutating.value) return
  const next = String(album.value.isOpen) === '1' ? '0' : '1'
  try { await showConfirmDialog({ title: next === '1' ? '公开并上架专辑？' : '设为私密并下架？', message: next === '1' ? '公开后，专辑将进入听众端检索。确认现在上架？' : '专辑将从听众端检索下架。确认改为私密？', confirmButtonColor: '#008aff' }) }
  catch { return }
  mutating.value = true
  actionError.value = ''
  try { await albumsApi.setAlbumOpen(albumId.value, next); album.value.isOpen = next; showSuccessToast(next === '1' ? '上架请求已保存' : '下架请求已保存') }
  catch (e) { actionError.value = errorMessage(e) }
  finally { mutating.value = false }
}
async function deleteAlbum() {
  if (!owner.value || mutating.value) return
  try { await showConfirmDialog({ title: '删除专辑', message: '删除后不可恢复，确认删除此专辑？', confirmButtonColor: '#ee0a24' }) }
  catch { return }
  mutating.value = true
  actionError.value = ''
  try { await albumsApi.deleteAlbumInfo(albumId.value); showSuccessToast('删除成功'); router.replace('/myWork') }
  catch (e) { actionError.value = errorMessage(e) }
  finally { mutating.value = false }
}
async function deleteTrack(track) {
  if (!owner.value || mutating.value) return
  try { await showConfirmDialog({ title: '删除声音', message: '将删除声音及其 VOD 媒体文件，无法恢复，是否继续？', confirmButtonColor: '#ee0a24' }) }
  catch { return }
  mutating.value = true
  actionError.value = ''
  try {
    await albumsApi.deleteTrackInfo(track.trackId)
    if (album.value.includeTrackCount != null) album.value.includeTrackCount = Math.max(0, Number(album.value.includeTrackCount) - 1)
    showSuccessToast('删除成功')
    resetTracks()
  } catch (e) { actionError.value = errorMessage(e) }
  finally { mutating.value = false }
}
onBeforeUnmount(() => { alive = false; albumVersion++; trackVersion++ })
watch(albumId, loadAlbum, { immediate: true })
</script>

<style scoped>
.album-page { --van-primary-color: #008aff; min-height: 100vh; background: #fff; color: #333; }
.album-page :deep(.van-nav-bar .van-icon) { color: #008aff; }
.initial-loading { padding: 25px 15px; }
.album-hero { background: #666; color: white; display: flex; gap: 15px; padding: 25px 20px; align-items: center; }
.album-hero :deep(.van-image) { flex-shrink: 0; }
.album-heading { min-width: 0; flex: 1; }
h1 { font-size: 16px; line-height: 1.5; margin: 7px 0; overflow-wrap: anywhere; }
.album-heading p { font-size: 11px; color: #eee; margin: 7px 0 0; }
.pay-tag { background: #f33; font-size: 10px; padding: 2px 5px; border-radius: 2px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); margin: 0 15px; padding: 18px 0; border-bottom: .5px solid #e5e5e5; text-align: center; }
.stats strong, .stats span { display: block; }
.stats strong { font-size: 18px; font-variant-numeric: tabular-nums; font-weight: 500; }
.stats span { margin-top: 7px; color: #999; font-size: 11px; }
h2 { margin: 0 0 12px; font-size: 16px; font-weight: 600; }
.intro-panel { padding: 20px 15px; }
.intro { margin: 0; font-size: 13px; color: #666; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.intro.collapsed { display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.text-button { background: none; border: 0; color: #008aff; padding: 8px 0; font-size: 12px; }
.management { display: flex; gap: 10px; flex-wrap: wrap; padding: 0 15px 20px; border-bottom: 6px solid #f8f8f8; }
.management p, .list-note { font-size: 11px; color: #999; margin: 0; line-height: 1.7; }
.management p { width: 100%; }
.episodes { padding: 20px 15px; }
.section-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.section-heading h2 { margin: 0; }
.track-filter { display: flex; gap: 8px; margin: 15px 0; align-items: center; }
.track-filter label { font-size: 11px; color: #999; }
.track-filter select { max-width: 90px; padding: 7px 0; border: 0; background: white; font-size: 12px; }
.track-filter input { flex: 1; min-width: 0; padding: 9px; border: 0; border-radius: 4px; background: #f8f8f8; font-size: 12px; }
.track-row { padding: 15px 0 10px; border-bottom: .5px solid #e5e5e5; }
.track-main { display: flex; align-items: center; gap: 10px; width: 100%; border: 0; background: none; text-align: left; color: #333; }
.track-main > div { flex: 1; min-width: 0; }
.track-order { font-size: 13px; color: #999; width: 22px; text-align: center; }
h3 { font-size: 14px; line-height: 1.5; font-weight: 400; margin: 0 0 8px; overflow-wrap: anywhere; }
.track-main p { color: #999; font-size: 11px; margin: 0; }
.track-actions { display: flex; gap: 20px; justify-content: flex-end; padding: 8px 0 0; }
.track-actions button, .inline-warning button { border: 0; background: none; color: #008aff; padding: 6px; font-size: 12px; }
.track-actions .danger { color: #bd3b46; }
.inline-warning { margin: 10px 15px; padding: 10px; color: #b42318; background: #fff2f0; font-size: 12px; line-height: 1.7; }
.track-retry { text-align: center; color: #b42318; font-size: 12px; padding: 10px; }
.footer { padding: 10px 15px calc(20px + env(safe-area-inset-bottom)); }
button { cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .5; }
button:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid #008aff; outline-offset: 2px; }
@media (max-width: 350px) { .album-hero { gap: 12px; padding: 20px 15px; } .management { gap: 8px; } }
</style>
