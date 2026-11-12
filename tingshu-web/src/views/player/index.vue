<template>
  <div class="player-page">
    <van-nav-bar title="正在收听" left-arrow fixed placeholder @click-left="router.back()"><template #right><button type="button" class="nav-button" aria-label="查看专辑" @click="openAlbum"><van-icon name="description-o" size="20" /></button></template></van-nav-bar>
    <van-loading v-if="loading" class="player-loading">正在加载声音</van-loading>
    <van-empty v-else-if="loadError" image="network" :description="loadError"><van-button round size="small" @click="loadTrack">重新加载</van-button></van-empty>
    <template v-else-if="trackInfo">
      <div class="player-body">
        <div class="cover-stage"><img class="cover" :src="trackInfo.coverUrl || album?.coverUrl" :alt="trackInfo.trackTitle" /><span class="cover-ring" aria-hidden="true"></span></div>
        <h1>{{ trackInfo.trackTitle }}</h1>
        <button type="button" class="album-name" @click="openAlbum">{{ album?.albumTitle || '查看所属专辑' }} <van-icon name="arrow" /></button>
        <div class="progress-row"><time>{{ formatTime(progress) }}</time><van-slider v-model="progress" :max="duration || 1" :disabled="!duration" bar-height="3px" active-color="#ff0036" aria-label="播放进度" @drag-start="dragging = true" @drag-end="seek" @change="seek" /><time>{{ formatTime(duration) }}</time></div>
        <div class="controls">
          <button type="button" class="side-control" aria-label="打开声音列表" @click="playlistOpen = true"><van-icon name="bars" size="25" /></button>
          <button type="button" aria-label="上一集" :disabled="switching" @click="adjacent(-1)"><van-icon name="arrow-left" size="27" /></button>
          <button type="button" class="play-button" :aria-label="playing ? '暂停' : '播放'" :disabled="!audioUrl || starting" @click="togglePlay"><van-loading v-if="starting" color="#fff" size="28" /><van-icon v-else :name="playing ? 'pause' : 'play'" size="32" /></button>
          <button type="button" aria-label="下一集" :disabled="switching" @click="adjacent(1)"><van-icon name="arrow" size="27" /></button>
          <button type="button" class="side-control" :aria-label="collected ? '取消收藏声音' : '收藏声音'" :class="{ selected: collected }" :disabled="collecting" @click="toggleCollect"><van-icon :name="collected ? 'star' : 'star-o'" size="25" /></button>
        </div>
        <p v-if="audioError" class="audio-error" role="status">{{ audioError }}</p>
        <div class="playback-options"><label>倍速 <select v-model="rate" aria-label="播放速度" @change="applyRate"><option v-for="value in [0.75, 1, 1.25, 1.5, 2]" :key="value" :value="value">{{ value }}×</option></select></label><label><van-icon name="clock-o" /> 定时 <select v-model="sleepMinutes" aria-label="定时关闭" @change="setSleepTimer"><option :value="0">关闭</option><option v-for="value in [15, 30, 60]" :key="value" :value="value">{{ value }}分钟</option></select></label></div>
        <div class="album-follow"><div><strong>{{ album?.nickname || album?.userInfoVo?.nickname || '订阅专辑' }}</strong><p>{{ Number(albumStats.subscribeStatNum || 0).toLocaleString() }} 位听友订阅</p></div><van-button round size="small" :plain="subscribed" color="#ff0036" :loading="subscribing" @click="toggleSubscribe">{{ subscribed ? '已订阅' : '+ 订阅' }}</van-button></div>
      </div>
      <van-tabs v-model:active="tab" color="#ff0036"><van-tab title="声音简介" name="intro"><p class="track-intro">{{ trackInfo.trackIntro || '这个声音还没有文字简介，戴上耳机开始收听吧。' }}</p></van-tab><van-tab title="听友评论" name="comments"><CommentPanel v-if="tab === 'comments'" :album-id="currentAlbumId" :track-id="trackId" /></van-tab></van-tabs>
      <audio ref="audio" :src="audioUrl" preload="metadata" @loadedmetadata="onMetadata" @timeupdate="onTimeUpdate" @play="onPlay" @pause="onPause" @ended="onEnded" @error="onAudioError"></audio>
    </template>
    <van-popup v-model:show="playlistOpen" position="bottom" round :style="{ maxHeight: '75vh' }"><section class="playlist"><header><h2>专辑声音 <span>{{ playlistTotal }} 集</span></h2><button type="button" aria-label="关闭声音列表" @click="playlistOpen = false"><van-icon name="cross" /></button></header><van-loading v-if="playlistLoading" class="player-loading">加载声音列表</van-loading><van-empty v-else-if="playlistError" :description="playlistError"><van-button size="small" @click="loadPlaylist(playlistPage)">重试</van-button></van-empty><div v-else><button v-for="item in playlist" :key="item.trackId" type="button" class="playlist-track" :class="{ current: Number(item.trackId) === trackId }" @click="switchTrack(item)"><span>{{ item.orderNum }}</span><span>{{ item.trackTitle }}</span><van-icon :name="item.isShowPaidMark ? 'lock' : (Number(item.trackId) === trackId ? 'volume-o' : 'play-circle-o')" /></button><van-pagination v-if="playlistTotal > playlistSize" v-model="playlistPage" :total-items="playlistTotal" :items-per-page="playlistSize" :show-page-size="3" @change="loadPlaylist" /></div></section></van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { albumsApi } from '../../api/albums.js'
import { userApi } from '../../api/user.js'
import CommentPanel from '../../components/CommentPanel.vue'

const route = useRoute()
const router = useRouter()
const trackId = computed(() => Number(route.params.trackId))
const trackInfo = ref(null)
const album = ref(null)
const albumStats = ref({})
const currentAlbumId = ref(0)
const audio = ref(null)
const audioUrl = computed(() => trackInfo.value?.mediaUrl || '')
const loading = ref(true)
const loadError = ref('')
const audioError = ref('')
const playing = ref(false)
const starting = ref(false)
const progress = ref(0)
const duration = ref(0)
const breakSecond = ref(0)
const dragging = ref(false)
const collected = ref(false)
const collecting = ref(false)
const subscribed = ref(false)
const subscribing = ref(false)
const switching = ref(false)
const tab = ref('intro')
const rate = ref(1)
const sleepMinutes = ref(0)
const playlistOpen = ref(false)
const playlist = ref([])
const playlistPage = ref(1)
const playlistTotal = ref(0)
const playlistLoading = ref(false)
const playlistError = ref('')
const playlistSize = 50
let loadVersion = 0
let listVersion = 0
let playbackContext = null
let progressTimer
let sleepTimer
let reporting = false
let active = true

async function loadTrack() {
  const version = ++loadVersion
  clearInterval(progressTimer)
  reportProgress()
  audio.value?.pause()
  playing.value = false
  starting.value = false
  loading.value = true
  loadError.value = ''
  audioError.value = ''
  progress.value = 0
  duration.value = 0
  breakSecond.value = 0
  playbackContext = null
  trackInfo.value = null
  playlist.value = []
  listVersion++
  try {
    const id = trackId.value
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('声音编号无效')
    const [result, resume, collect] = await Promise.allSettled([albumsApi.getTrackInfo(id), userApi.getTrackBreakSecond(id), userApi.isCollectTrack(id)])
    if (!active || version !== loadVersion) return
    if (result.status !== 'fulfilled' || !result.value.data) throw new Error(result.reason?.message || '声音暂不可用，请返回专辑检查收听权限')
    const info = result.value.data
    const albumId = Number(info.albumId || route.query.albumId)
    if (!(albumId > 0)) throw new Error('未找到所属专辑，请从专辑页面重新进入')
    currentAlbumId.value = albumId
    playbackContext = { trackId: id, albumId }
    breakSecond.value = resume.status === 'fulfilled' ? Math.max(0, Number(resume.value.data) || 0) : 0
    collected.value = collect.status === 'fulfilled' && collect.value.data === true
    trackInfo.value = info
    if (!info.mediaUrl) audioError.value = '这个声音暂无可用播放地址，请稍后再试'
    playlistPage.value = Math.floor(Math.max(0, Number(info.orderNum || 1) - 1) / playlistSize) + 1
    loadAlbum(albumId, version)
    loadPlaylist(playlistPage.value)
    progressTimer = setInterval(() => { if (playing.value) reportProgress() }, 10000)
  } catch (error) {
    if (active && version === loadVersion) loadError.value = error?.message || '加载失败，请重试'
  } finally {
    if (active && version === loadVersion) loading.value = false
  }
}
async function loadAlbum(id, version) {
  const results = await Promise.allSettled([albumsApi.getAlbumInfo(id), albumsApi.getAlbumInfoStat(id), userApi.isSubscribeAlbum(id)])
  if (!active || version !== loadVersion) return
  album.value = results[0].status === 'fulfilled' ? results[0].value.data : null
  albumStats.value = results[1].status === 'fulfilled' ? results[1].value.data || {} : {}
  subscribed.value = results[2].status === 'fulfilled' && results[2].value.data === true
}
async function loadPlaylist(page = 1) {
  const version = ++listVersion
  playlistLoading.value = true
  playlistError.value = ''
  try {
    const { data } = await albumsApi.getAlbumTrackList({ albumId: currentAlbumId.value, page, limit: playlistSize })
    if (!active || version !== listVersion) return false
    playlist.value = Array.isArray(data?.records) ? data.records : []
    playlistTotal.value = Number(data?.total) || 0
    playlistPage.value = page
    return true
  } catch {
    if (active && version === listVersion) playlistError.value = '声音列表加载失败'
    return false
  } finally {
    if (active && version === listVersion) playlistLoading.value = false
  }
}
async function play() {
  if (!audio.value || !audioUrl.value || starting.value) return
  const version = loadVersion
  starting.value = true
  audioError.value = ''
  try { await audio.value.play() } catch (error) {
    if (active && version === loadVersion) {
      playing.value = false
      audioError.value = error?.name === 'NotAllowedError' ? '浏览器阻止了自动播放，点播放按钮开始收听' : '音频未能播放，请检查网络或重新加载'
    }
  } finally { if (active && version === loadVersion) starting.value = false }
}
function togglePlay() { playing.value ? audio.value?.pause() : play() }
function onPlay() { playing.value = true; audioError.value = '' }
function onPause() { playing.value = false; reportProgress() }
function onMetadata() {
  if (!audio.value) return
  duration.value = Number.isFinite(audio.value.duration) ? Math.floor(audio.value.duration) : Math.floor(trackInfo.value?.mediaDuration || 0)
  if (breakSecond.value > 0 && duration.value > 0) audio.value.currentTime = Math.min(breakSecond.value, Math.max(0, duration.value - 1))
  applyRate()
  play()
}
function onTimeUpdate() { if (audio.value && !dragging.value) progress.value = Math.floor(audio.value.currentTime) }
function seek() { dragging.value = false; if (audio.value && duration.value) { audio.value.currentTime = Math.min(progress.value, duration.value); reportProgress() } }
function onAudioError() { playing.value = false; starting.value = false; audioError.value = '音频地址不可用或格式不受支持，可重新加载后再试' }
function onEnded() { playing.value = false; reportProgress(); adjacent(1) }
function applyRate() { if (audio.value) audio.value.playbackRate = Number(rate.value) }
function setSleepTimer() {
  clearTimeout(sleepTimer)
  if (sleepMinutes.value) sleepTimer = setTimeout(() => { audio.value?.pause(); sleepMinutes.value = 0; showToast('定时关闭，已暂停播放') }, Number(sleepMinutes.value) * 60000)
}
async function reportProgress() {
  if (!playbackContext || !audio.value || reporting || !Number.isFinite(audio.value.currentTime)) return
  const payload = { ...playbackContext, breakSecond: Math.floor(audio.value.currentTime) }
  if (!payload.breakSecond && !playing.value) return
  reporting = true
  try { await userApi.updateListenProcess(payload) } catch {} finally { reporting = false }
}
function openAlbum() { if (currentAlbumId.value) router.push(`/detail/${currentAlbumId.value}`) }
async function switchTrack(item) {
  if (Number(item.trackId) === trackId.value) { playlistOpen.value = false; return }
  if (item.isShowPaidMark) { showToast('该声音需要购买，请在专辑页选择购买方案'); openAlbum(); return }
  reportProgress()
  playlistOpen.value = false
  await router.replace({ path: `/player/${item.trackId}`, query: { albumId: currentAlbumId.value } })
}
async function adjacent(direction) {
  if (switching.value || playlistLoading.value) return
  switching.value = true
  try {
    let index = playlist.value.findIndex(item => Number(item.trackId) === trackId.value)
    if (index < 0) { showToast('当前声音不在已加载列表中，请从声音列表选择'); playlistOpen.value = true; return }
    const next = playlist.value[index + direction]
    if (next) { await switchTrack(next); return }
    const nextPage = playlistPage.value + direction
    if (nextPage < 1 || nextPage > Math.ceil(playlistTotal.value / playlistSize)) { showToast(direction < 0 ? '已经是第一集了' : '已经听到最新一集了'); return }
    if (await loadPlaylist(nextPage)) {
      const item = direction > 0 ? playlist.value[0] : playlist.value[playlist.value.length - 1]
      if (item) await switchTrack(item)
    }
  } finally { switching.value = false }
}
async function toggleCollect() {
  if (collecting.value) return
  const version = loadVersion
  collecting.value = true
  try { const result = await userApi.collectTrack(trackId.value); if (active && version === loadVersion) { collected.value = typeof result.data === 'boolean' ? result.data : !collected.value; showToast(collected.value ? '已收藏声音' : '已取消收藏') } } catch {} finally { collecting.value = false }
}
async function toggleSubscribe() {
  if (subscribing.value) return
  const version = loadVersion
  subscribing.value = true
  try { const result = await userApi.subscribeAlbum(currentAlbumId.value); if (active && version === loadVersion) { const next = typeof result.data === 'boolean' ? result.data : !subscribed.value; albumStats.value.subscribeStatNum = Math.max(0, Number(albumStats.value.subscribeStatNum || 0) + (next ? 1 : -1)); subscribed.value = next; showToast(next ? '已订阅专辑' : '已取消订阅') } } catch {} finally { subscribing.value = false }
}
function formatTime(value) { const seconds = Math.max(0, Math.floor(Number(value) || 0)); return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` }
watch(() => [route.params.trackId, route.query.albumId], loadTrack, { immediate: true })
onBeforeUnmount(() => { reportProgress(); active = false; loadVersion++; listVersion++; clearInterval(progressTimer); clearTimeout(sleepTimer); audio.value?.pause() })
</script>

<style scoped>
.player-page { min-height: 100vh; background: #fff; padding-bottom: 24px; }
.player-loading { padding: 48px 0; text-align: center; }
.nav-button, .playlist header button { border: 0; background: none; color: #555; cursor: pointer; }
.player-body { padding: 24px 24px 0; background: linear-gradient(#f9f5f0, #fff 75%); }
.cover-stage { position: relative; width: 70%; max-width: 275px; margin: 0 auto; aspect-ratio: 1; }
.cover { position: relative; z-index: 1; box-sizing: border-box; width: 100%; height: 100%; object-fit: cover; border: 1px solid #e2bb92; border-radius: 10px; background: #eee8e2; box-shadow: 0 12px 28px #4c2d1522; }
.cover-ring { position: absolute; inset: 10px -12px -10px 12px; border: 1px solid #e2bb9260; border-radius: 10px; }
h1 { margin: 28px 0 8px; font-size: 18px; font-weight: 600; line-height: 1.6; text-align: center; color: #333; }
.album-name { display: block; border: 0; background: none; color: #9b8a79; max-width: 100%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; margin: 0 auto; font-size: 12px; cursor: pointer; }
.progress-row { display: flex; align-items: center; gap: 14px; margin: 28px 0 20px; }
.progress-row time { font-size: 10px; color: #999; font-variant-numeric: tabular-nums; }
.controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.controls button { display: flex; justify-content: center; align-items: center; flex-shrink: 0; border: 0; padding: 8px; background: none; color: #333; cursor: pointer; }
.controls .play-button { width: 68px; height: 68px; border-radius: 50%; background: #ff0036; color: #fff; box-shadow: 0 5px 14px #ff003626; }
.controls .side-control { color: #888; }
.controls .selected { color: #e2bb92; }
.controls button:disabled { opacity: .4; }
.audio-error { text-align: center; color: #b88854; font-size: 12px; line-height: 1.7; }
.playback-options { display: flex; justify-content: space-around; margin: 22px 0; color: #999; font-size: 11px; }
.playback-options label { display: flex; align-items: center; gap: 5px; }
.playback-options select { border: 0; color: #777; background: transparent; padding: 4px 0; font-size: 11px; }
.album-follow { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 0 20px; border-top: 1px solid #eee9e5; }
.album-follow strong { color: #555; font-size: 13px; font-weight: 500; }
.album-follow p { margin: 5px 0 0; color: #aaa; font-size: 11px; }
.track-intro { padding: 8px 20px 20px; font-size: 13px; line-height: 1.9; color: #777; white-space: pre-wrap; }
.playlist { padding: 18px 16px 28px; }
.playlist header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.playlist h2 { font-size: 16px; margin: 0; }
.playlist h2 span { font-size: 12px; color: #999; font-weight: 400; margin-left: 6px; }
.playlist-track { display: flex; align-items: center; gap: 10px; width: 100%; border: 0; border-bottom: 1px solid #f5f5f5; background: #fff; padding: 16px 4px; text-align: left; font-size: 13px; color: #555; }
.playlist-track span:first-child { width: 24px; flex-shrink: 0; text-align: center; color: #aaa; }
.playlist-track span:nth-child(2) { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.playlist-track.current { color: #ff0036; background: #fff8f9; }
.playlist :deep(.van-pagination) { margin-top: 20px; }
button:focus-visible, select:focus-visible { outline: 2px solid #ff0036; outline-offset: 3px; }
@media (max-width: 350px) { .player-body { padding-inline: 18px; } .controls { gap: 6px; } }
</style>
