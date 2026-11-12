<template>
  <div class="detail-page">
    <van-nav-bar title="专辑详情" left-arrow fixed placeholder @click-left="router.back()">
      <template #right><button class="nav-action" type="button" aria-label="分享专辑" @click="shareAlbum"><van-icon name="share-o" /></button></template>
    </van-nav-bar>
    <van-loading v-if="loading" class="detail-loading">正在加载专辑</van-loading>
    <van-empty v-else-if="loadError" image="network" :description="loadError"><van-button size="small" round @click="loadDetail">重新加载</van-button></van-empty>
    <template v-else-if="album">
      <section class="hero">
        <div class="hero-backdrop" :style="{ backgroundImage: `url(${album.coverUrl})` }"></div>
        <div class="hero-content">
          <img v-if="album.coverUrl && !coverFailed" class="cover" :src="album.coverUrl" :alt="album.albumTitle" @error="coverFailed = true" />
          <span v-else class="cover cover-fallback" aria-hidden="true">{{ (plainTitle(album.albumTitle) || '知').slice(0, 1) }}</span>
          <div class="info"><span class="pay-type">{{ payTypeName }}</span><h1>{{ plainTitle(album.albumTitle) }}</h1><p class="author"><van-icon name="user-o" /> {{ album.nickname || album.userInfoVo?.nickname || '知音主播' }}</p></div>
        </div>
        <div class="stats"><span><strong>{{ formatCount(stat.playStatNum) }}</strong> 播放</span><span><strong>{{ formatCount(stat.subscribeStatNum) }}</strong> 订阅</span><span><strong>{{ formatCount(stat.commentStatNum) }}</strong> 评论</span></div>
        <div class="hero-actions"><van-button round block color="#007aff" :disabled="!tracks.length || tracksLoading" @click="playFirst">播放第一集</van-button><van-button round plain color="#007aff" :loading="subscribing" @click="toggleSubscribe">{{ subscribed ? '已订阅' : '+ 订阅' }}</van-button></div>
      </section>
      <div v-if="album.payType === '0102'" class="purchase-banner"><div><strong>VIP 专辑</strong><span>开通会员，收听会员专辑</span></div><van-button round size="small" color="#b88854" @click="router.push('/vipChannel')">查看会员</van-button></div>
      <div v-else-if="album.payType === '0103'" class="purchase-banner"><div><strong>¥{{ formatAmount(album.price) }}{{ album.priceType === '0201' ? '/集' : '/专辑' }}</strong><span>{{ album.buyNotes || '价格与权益以确认订单为准' }}</span></div><van-button round size="small" color="#007aff" @click="buyAlbum">{{ album.priceType === '0201' ? '选择声音' : '购买专辑' }}</van-button></div>
      <van-tabs v-model:active="tab" sticky offset-top="46" color="#007aff">
        <van-tab title="专辑简介" name="intro"><div class="intro tingshu-card"><h2>关于这张专辑</h2><p>{{ plainIntro || '主播还没有留下简介。' }}</p><div v-if="album.sellingPoint" class="selling-point"><van-icon name="diamond-o" /> {{ album.sellingPoint }}</div></div></van-tab>
        <van-tab :title="`声音列表 ${trackTotal}`" name="tracks">
          <van-loading v-if="tracksLoading" class="detail-loading">正在加载声音</van-loading>
          <van-empty v-else-if="tracksError" :description="tracksError"><van-button size="small" @click="loadTracks">重新加载</van-button></van-empty>
          <div v-else class="track-list">
            <button v-for="(track, index) in tracks" :key="track.trackId" type="button" class="track-item" @click="playTrack(track)"><span class="track-index">{{ track.orderNum || (trackPage - 1) * trackSize + index + 1 }}</span><span class="track-info"><span class="track-title">{{ plainTitle(track.trackTitle) }}</span><span class="track-duration">{{ formatDuration(track.mediaDuration) }}<span v-if="track.isShowPaidMark"> · 付费声音</span></span></span><van-icon :name="track.isShowPaidMark ? 'lock' : 'play-circle-o'" size="24" color="#007aff" /></button>
            <van-empty v-if="!tracks.length" description="暂无声音" />
            <van-pagination v-if="trackTotal > trackSize" v-model="trackPage" :total-items="trackTotal" :items-per-page="trackSize" :show-page-size="3" force-ellipses @change="loadTracks" />
          </div>
        </van-tab>
        <van-tab title="听友评论" name="comments"><CommentPanel v-if="tab === 'comments'" :album-id="albumId" :track-id="0" /></van-tab>
      </van-tabs>
      <footer class="detail-footer">知音听书 · 听见更大的世界</footer>
    </template>
    <van-popup v-model:show="purchaseOpen" position="bottom" round :style="{ maxHeight: '70vh' }"><section class="purchase-panel"><header><h2>选择购买方案</h2><button type="button" aria-label="关闭购买方案" @click="purchaseOpen = false"><van-icon name="cross" /></button></header><p>{{ plainTitle(selectedTrack?.trackTitle) }}</p><van-loading v-if="purchaseLoading">正在获取价格</van-loading><p v-else-if="purchaseError" class="purchase-error" role="alert">{{ purchaseError }}</p><button v-for="plan in trackPlans" v-else :key="plan.trackCount" type="button" class="purchase-plan" @click="confirmTrack(plan)"><span>{{ plan.name }}</span><strong>¥{{ formatAmount(plan.price) }}</strong><van-icon name="arrow" /></button><p class="purchase-note">实际价格、集数和折扣以服务端确认订单为准。</p></section></van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { albumsApi } from '../../api/albums.js'
import { userApi } from '../../api/user.js'
import { orderApi, ITEM_TYPE, formatAmount } from '../../api/order.js'
import { useUserStore } from '../../stores/user.js'
import { plainTitle } from '../../utils/format.js'
import CommentPanel from '../../components/CommentPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const albumId = computed(() => Number(route.params.albumId))
const album = ref(null)
const stat = ref({})
const subscribed = ref(false)
const subscribing = ref(false)
const loading = ref(true)
const coverFailed = ref(false)
const loadError = ref('')
const tracks = ref([])
const trackPage = ref(1)
const trackSize = 30
const trackTotal = ref(0)
const tracksLoading = ref(false)
const tracksError = ref('')
const tab = ref('intro')
const purchaseOpen = ref(false)
const purchaseLoading = ref(false)
const purchaseError = ref('')
const selectedTrack = ref(null)
const trackPlans = ref([])
let version = 0
let trackVersion = 0
let purchaseVersion = 0
let active = true
const payTypeName = computed(() => ({ '0101': '免费专辑', '0102': 'VIP 免费', '0103': '付费专辑' })[album.value?.payType] || '有声专辑')
const plainIntro = computed(() => {
  const html = String(album.value?.albumRichIntro || album.value?.albumIntro || '')
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script,style,iframe,object').forEach(element => element.remove())
  doc.querySelectorAll('p,div,br,li,h1,h2,h3').forEach(element => element.append(doc.createTextNode('\n')))
  return (doc.body.textContent || '').replace(/\n{3,}/g, '\n\n').trim()
})
function requireLogin() {
  if (userStore.token) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}
async function loadDetail() {
  const request = ++version
  loading.value = true
  loadError.value = ''
  album.value = null
  stat.value = {}
  subscribed.value = false
  trackPage.value = 1
  tracks.value = []
  trackTotal.value = 0
  trackVersion++
  purchaseVersion++
  purchaseOpen.value = false
  try {
    const id = albumId.value
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('专辑编号无效')
    const { data } = await albumsApi.getAlbumInfo(id)
    if (!active || request !== version) return
    if (!data?.id) throw new Error('专辑不存在或暂不可用')
    album.value = data
    loading.value = false
    loadTracks(1)
    const extras = await Promise.allSettled([albumsApi.getAlbumDetail(id), albumsApi.getAlbumInfoStat(id), userApi.isSubscribeAlbum(id)])
    if (!active || request !== version) return
    if (extras[0].status === 'fulfilled' && extras[0].value.data) {
      const detail = extras[0].value.data
      album.value = { ...album.value, ...(detail.albumInfo || {}), ...(detail.nickname ? { nickname: detail.nickname } : {}), ...(detail.userInfoVo ? { userInfoVo: detail.userInfoVo } : {}) }
    }
    if (extras[1].status === 'fulfilled') stat.value = extras[1].value.data || {}
    subscribed.value = extras[2].status === 'fulfilled' && extras[2].value.data === true
  } catch (error) {
    if (active && request === version) loadError.value = error?.message || '专辑加载失败，请重试'
  } finally {
    if (active && request === version) loading.value = false
  }
}
async function loadTracks(page = trackPage.value) {
  const request = ++trackVersion
  tracksLoading.value = true
  tracksError.value = ''
  try {
    const { data } = await albumsApi.getAlbumTrackList({ albumId: albumId.value, page: typeof page === 'number' ? page : trackPage.value, limit: trackSize })
    if (!active || request !== trackVersion) return
    tracks.value = Array.isArray(data?.records) ? data.records : []
    trackTotal.value = Number(data?.total) || 0
  } catch {
    if (active && request === trackVersion) tracksError.value = '声音列表加载失败，请重试'
  } finally {
    if (active && request === trackVersion) tracksLoading.value = false
  }
}
async function toggleSubscribe() {
  if (subscribing.value || !requireLogin()) return
  const request = version
  subscribing.value = true
  try {
    const result = await userApi.subscribeAlbum(albumId.value)
    if (!active || request !== version) return
    const next = typeof result.data === 'boolean' ? result.data : !subscribed.value
    stat.value.subscribeStatNum = Math.max(0, Number(stat.value.subscribeStatNum || 0) + (next ? 1 : -1))
    subscribed.value = next
    showToast(next ? '已订阅专辑' : '已取消订阅')
  } catch {} finally { subscribing.value = false }
}
function playFirst() {
  if (trackPage.value !== 1) { trackPage.value = 1; tab.value = 'tracks'; loadTracks(1); return }
  if (tracks.value[0]) playTrack(tracks.value[0])
}
async function playTrack(track) {
  if (!requireLogin()) return
  if (!track.isShowPaidMark) {
    // 不在进入播放器前上报0秒，避免覆盖已保存的断点。
    router.push({ path: `/player/${track.trackId}`, query: { albumId: albumId.value } })
    return
  }
  if (album.value?.payType === '0102') { router.push('/vipChannel'); return }
  if (album.value?.priceType === '0202') { buyAlbum(); return }
  const request = ++purchaseVersion
  selectedTrack.value = track
  trackPlans.value = []
  purchaseError.value = ''
  purchaseOpen.value = true
  purchaseLoading.value = true
  try {
    const { data } = await orderApi.getTrackSettingList(track.trackId)
    if (!active || request !== purchaseVersion) return
    trackPlans.value = Array.isArray(data) ? data : []
    if (!trackPlans.value.length) purchaseError.value = '暂未提供可购买方案，请稍后重试'
  } catch (error) {
    if (active && request === purchaseVersion) purchaseError.value = error?.message || '价格获取失败，请稍后重试'
  } finally {
    if (active && request === purchaseVersion) purchaseLoading.value = false
  }
}
function buyAlbum() {
  if (!requireLogin()) return
  if (album.value?.priceType === '0201') { tab.value = 'tracks'; showToast('请从声音列表选择需要购买的声音'); return }
  router.push({ path: '/confirmOrder', query: { itemType: ITEM_TYPE.Album, itemId: albumId.value } })
}
function confirmTrack(plan) {
  if (!selectedTrack.value || !requireLogin()) return
  purchaseOpen.value = false
  router.push({ path: '/confirmOrder', query: { itemType: ITEM_TYPE.Track, itemId: selectedTrack.value.trackId, trackCount: plan.trackCount } })
}
async function shareAlbum() {
  try {
    if (navigator.share) await navigator.share({ title: album.value?.albumTitle || '知音听书', url: window.location.href })
    else if (navigator.clipboard?.writeText) { await navigator.clipboard.writeText(window.location.href); showToast('专辑链接已复制') }
    else showToast('复制浏览器地址栏链接即可分享这张专辑')
  } catch (error) { if (error?.name !== 'AbortError') showToast('分享未完成，可复制地址栏链接') }
}
function formatCount(value) { const n = Number(value) || 0; return n >= 10000 ? `${(n / 10000).toFixed(1)}万` : n.toLocaleString() }
function formatDuration(value) { const seconds = Math.max(0, Math.floor(Number(value) || 0)); return `${Math.floor(seconds / 60)}分${seconds % 60}秒` }
watch(() => route.params.albumId, loadDetail, { immediate: true })
onBeforeUnmount(() => { active = false; version++; trackVersion++; purchaseVersion++ })
</script>

<style scoped>
.detail-page { min-height: 100vh; background: var(--ground); padding-bottom: var(--sp-5); }
.nav-action { border: 0; background: none; color: var(--ink-2); font-size: 20px; }
.detail-loading { text-align: center; padding: 48px 0; }

/* Hero：专辑封面 + 信息 + 操作 */
.hero { position: relative; isolation: isolate; overflow: hidden; padding: var(--sp-4) var(--sp-3) var(--sp-4); background: var(--surface); }
.hero-backdrop { position: absolute; inset: -30px; background-size: cover; background-position: center; filter: blur(30px); opacity: .1; z-index: -1; }
.hero-content { display: flex; align-items: flex-start; gap: var(--sp-3); }
.cover { width: 112px; height: 112px; flex-shrink: 0; border-radius: var(--r-md); object-fit: cover; background: var(--line-soft); }
.cover-fallback { display: grid; place-items: center; font-size: var(--fs-display); font-weight: 600; color: var(--ink-4); }
.info { flex: 1; min-width: 0; }
.pay-type { display: inline-block; color: var(--vip); background: var(--vip-soft); border-radius: var(--r-sm); padding: 2px 6px; font-size: var(--fs-micro); }
h1 { margin: 6px 0 8px; font-size: var(--fs-title); line-height: 1.4; color: var(--ink); font-weight: 600; overflow-wrap: anywhere; }
.author { color: var(--ink-3); margin: 0; font-size: var(--fs-meta); display: flex; align-items: center; gap: 4px; }
.stats { display: flex; gap: var(--sp-5); color: var(--ink-3); margin: var(--sp-4) 0; font-size: var(--fs-meta); }
.stats strong { color: var(--ink); font-weight: 600; margin-right: 3px; font-variant-numeric: tabular-nums; }
.hero-actions { display: flex; gap: var(--sp-2); }
.hero-actions > :first-child { flex: 1; }
.hero-actions > :last-child { min-width: 96px; }

/* 购买横幅 */
.purchase-banner { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); margin: var(--sp-3) var(--sp-3) 0; padding: var(--sp-3) var(--sp-4); border: 1px solid var(--vip-soft); border-radius: var(--r-lg); background: var(--vip-soft); }
.purchase-banner strong { color: var(--vip); font-size: var(--fs-body); font-weight: 600; }
.purchase-banner span { display: block; max-width: 260px; margin-top: 3px; color: var(--ink-3); font-size: var(--fs-meta); line-height: 1.5; }

/* 简介 */
.intro { padding: var(--sp-4); }
.intro h2 { font-size: var(--fs-section); color: var(--ink); margin: 0 0 var(--sp-3); font-weight: 600; }
.intro p { color: var(--ink-2); white-space: pre-line; overflow-wrap: anywhere; line-height: 1.85; font-size: var(--fs-body); margin: 0; }
.selling-point { padding: var(--sp-3); margin-top: var(--sp-4); background: var(--brand-soft); color: var(--brand); border-radius: var(--r-md); font-size: var(--fs-meta); display: flex; align-items: center; gap: 6px; }

/* 声音列表 */
.track-list { background: var(--surface); }
.track-item { display: flex; align-items: center; gap: var(--sp-2); box-sizing: border-box; width: 100%; border: 0; border-bottom: 1px solid var(--line-soft); background: var(--surface); padding: var(--sp-3); text-align: left; }
.track-item:hover { background: var(--ground); }
.track-index { width: 24px; flex-shrink: 0; text-align: center; color: var(--ink-4); font-size: var(--fs-meta); font-variant-numeric: tabular-nums; }
.track-info { flex: 1; min-width: 0; }
.track-title { display: block; font-size: var(--fs-body); color: var(--ink); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.track-duration { display: block; margin-top: 3px; color: var(--ink-4); font-size: var(--fs-micro); }
.track-list :deep(.van-pagination) { padding: var(--sp-4); }
.detail-footer { padding: var(--sp-6) var(--sp-3) var(--sp-3); color: var(--ink-4); font-size: var(--fs-micro); text-align: center; }

/* 购买弹层 */
.purchase-panel { padding: var(--sp-5) var(--sp-4) var(--sp-6); }
.purchase-panel header { display: flex; align-items: center; justify-content: space-between; }
.purchase-panel header h2 { font-size: var(--fs-title); margin: 0; font-weight: 600; }
.purchase-panel header button { border: 0; background: none; color: var(--ink-3); }
.purchase-panel > p { font-size: var(--fs-meta); line-height: 1.7; color: var(--ink-3); }
.purchase-plan { display: flex; width: 100%; align-items: center; gap: var(--sp-3); padding: var(--sp-4) var(--sp-3); margin-bottom: var(--sp-2); border: 1px solid var(--line); border-radius: var(--r-lg); color: var(--ink); background: var(--surface); font-size: var(--fs-body); text-align: left; }
.purchase-plan:hover { border-color: var(--brand-line); background: var(--brand-soft); }
.purchase-plan span { flex: 1; }
.purchase-plan strong { color: var(--brand); font-size: var(--fs-title); font-variant-numeric: tabular-nums; }
.purchase-panel .purchase-error { color: var(--paid); }
button { cursor: pointer; }
button:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }

/* ============================================================
   桌面端：专辑信息与内容双栏
   ============================================================ */
@media (min-width: 960px) {
  .detail-page :deep(.van-nav-bar) {
    max-width: none;
    left: var(--sidebar-w);
    right: 0;
    background: var(--surface);
    border-bottom: 1px solid var(--line);
  }

  .detail-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-section);
    font-weight: 600;
    color: var(--ink);
  }

  .hero {
    max-width: var(--shell-max);
    margin: 0 auto;
    padding: var(--sp-5);
    display: grid;
    grid-template-columns: 200px minmax(0, 1fr);
    grid-template-areas:
      'cover info'
      'cover stats'
      'cover actions';
    gap: 0 var(--sp-5);
    align-items: start;
  }

  .hero-content { display: contents; }
  .cover { grid-area: cover; width: 200px; height: 200px; }
  .info { grid-area: info; }
  .stats { grid-area: stats; justify-content: flex-start; margin: var(--sp-3) 0; }
  .hero-actions { grid-area: actions; max-width: 420px; }

  .purchase-banner { max-width: var(--shell-max); margin: var(--sp-4) auto 0; padding: var(--sp-4) var(--sp-5); }

  .detail-page :deep(.van-tabs) { max-width: var(--shell-max); margin: var(--sp-4) auto 0; background: transparent; }
  .detail-page :deep(.van-tabs__wrap) { background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-lg) var(--r-lg) 0 0; }
  .detail-page :deep(.van-tab__panel) { background: transparent; }
  .intro, .track-list { border: 1px solid var(--line); border-top: 0; border-radius: 0 0 var(--r-lg) var(--r-lg); overflow: hidden; }
  .track-item { padding: var(--sp-3) var(--sp-4); }
}

@media (max-width: 350px) { .cover { width: 96px; height: 96px; } h1 { font-size: var(--fs-section); } }
</style>
