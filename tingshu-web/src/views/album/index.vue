<template>
  <main class="album-page">
    <van-nav-bar title="专辑管理" left-arrow @click-left="back" />

    <div class="page">
      <van-skeleton v-if="initialLoading" title avatar :row="5" class="initial-loading" />
      <van-empty v-else-if="loadError" image="error" :description="loadError">
        <van-button size="small" type="primary" @click="loadAlbum">重新加载</van-button>
      </van-empty>

      <template v-else-if="album">
        <div class="album-layout">
          <!-- 左栏：专辑卡片 + 数据概览 + 管理操作 -->
          <aside class="album-side">
            <section class="tingshu-card album-card">
              <span class="cover-wrap">
                <img
                  v-if="!coverBroken(album.coverUrl)"
                  :src="album.coverUrl"
                  :alt="album.albumTitle"
                  @error="markBroken(album.coverUrl)"
                />
                <span v-else class="cover-fallback" aria-hidden="true">{{ firstChar(album.albumTitle) }}</span>
              </span>
              <div class="album-heading">
                <span class="tag" :class="payTagClass">{{ payLabel }}</span>
                <h1 class="album-title">{{ album.albumTitle || '未命名专辑' }}</h1>
                <p class="album-meta">
                  <van-icon name="records-o" size="13" />
                  <span class="num">{{ album.includeTrackCount ?? '—' }}</span> 集
                  <span class="dot">·</span>
                  {{ String(album.isFinished) === '1' ? '已完结' : '连载中' }}
                </p>
                <p class="album-meta">
                  <van-icon name="eye-o" size="13" />
                  {{ String(album.isOpen) === '1' ? '公开上架' : '私密下架' }}
                  <span class="dot">·</span>
                  {{ owner ? '我是创作者' : '他人专辑' }}
                </p>
              </div>
            </section>

            <section class="tingshu-card stats-card" aria-label="专辑统计">
              <div class="section-head"><h2 class="section-title">数据概览</h2></div>
              <ul class="stats">
                <li v-for="metric in metrics" :key="metric.key">
                  <strong class="num">{{ stats?.[metric.key] ?? '—' }}</strong>
                  <span>{{ metric.name }}</span>
                </li>
              </ul>
              <p v-if="statsError" class="inline-warning" role="alert">
                统计数据加载失败
                <button type="button" @click="loadStats">重试</button>
              </p>
            </section>

            <section v-if="owner" class="tingshu-card manage-card">
              <div class="section-head"><h2 class="section-title">专辑管理</h2></div>
              <div class="manage-actions">
                <van-button size="small" plain type="primary" icon="edit" @click="router.push(`/createAlbum/${albumId}`)">编辑专辑</van-button>
                <van-button size="small" plain type="primary" :loading="mutating" @click="changeVisibility">
                  {{ String(album.isOpen) === '1' ? '设为私密 / 下架' : '公开 / 上架' }}
                </van-button>
                <van-button size="small" plain type="danger" :disabled="mutating || Number(album.includeTrackCount) > 0" @click="deleteAlbum">删除专辑</van-button>
              </div>
              <p v-if="Number(album.includeTrackCount) > 0" class="manage-note">包含声音的专辑不能删除。</p>
            </section>

            <p v-if="ownershipError" class="inline-warning" role="alert">
              无法确认管理权限，暂不可编辑。
              <button type="button" @click="checkOwnership">重试</button>
            </p>
            <p v-if="actionError" class="inline-warning" role="alert">{{ actionError }}</p>
          </aside>

          <!-- 右栏：专辑简介 + 节目列表 -->
          <div class="album-main">
            <section class="tingshu-card intro-panel">
              <div class="section-head"><h2 class="section-title">专辑简介</h2></div>
              <p class="intro" :class="{ collapsed: !expanded }">{{ album.albumIntro || '暂无简介' }}</p>
              <button v-if="album.albumIntro?.length > 90" class="text-button" type="button" @click="expanded = !expanded">
                {{ expanded ? '收起简介' : '展开简介' }}
                <van-icon :name="expanded ? 'arrow-up' : 'arrow-down'" />
              </button>
            </section>

            <section class="episodes">
              <div class="section-head">
                <h2 class="section-title">节目（{{ album.includeTrackCount ?? '—' }}）</h2>
                <van-button v-if="owner" size="small" type="primary" icon="plus" @click="createTrack">上传声音</van-button>
              </div>
              <p class="list-note">{{ owner ? '显示本专辑全部声音，包含私密和未通过作品。' : '当前非本人专辑，仅展示已通过的公开声音。' }}</p>

              <form v-if="owner" class="track-filter" @submit.prevent="resetTracks">
                <label>
                  状态
                  <select v-model="status" @change="resetTracks">
                    <option value="">全部</option>
                    <option value="0501">已通过</option>
                    <option value="0502">未通过</option>
                  </select>
                </label>
                <input v-model="keyword" type="search" aria-label="搜索节目标题" placeholder="搜索节目标题" />
                <button type="submit" class="text-button">搜索</button>
              </form>

              <van-list
                v-model:loading="trackLoading"
                v-model:error="trackError"
                :finished="tracksFinished"
                :immediate-check="false"
                error-text="加载失败，点击重试"
                :finished-text="tracks.length ? '没有更多节目了' : ''"
                @load="loadTracks"
              >
                <div class="track-list">
                  <article v-for="(track, index) in tracks" :key="track.trackId" class="track-card">
                    <button class="track-main" type="button" @click="router.push(`/player/${track.trackId}`)">
                      <span class="track-order num">{{ track.orderNum ?? index + 1 }}</span>
                      <span class="track-copy">
                        <span class="track-title">{{ track.trackTitle }}</span>
                        <span class="track-meta">
                          <van-icon name="clock-o" size="13" />
                          {{ duration(track.mediaDuration) }}
                          <template v-if="owner">
                            <span class="dot">·</span>
                            <span class="tag" :class="track.status === '0502' ? 'tag-paid' : 'tag-free'">{{ trackStatus(track.status) }}</span>
                          </template>
                        </span>
                      </span>
                      <van-icon name="play-circle-o" size="24" color="#007aff" />
                    </button>
                    <div v-if="owner" class="track-actions">
                      <button type="button" @click="router.push(`/createTrack/${track.trackId}`)">编辑</button>
                      <button type="button" class="danger" :disabled="mutating" @click="deleteTrack(track)">删除</button>
                    </div>
                  </article>
                </div>
                <van-empty v-if="!trackLoading && !tracks.length && !trackError" :description="owner ? '本专辑暂无符合条件的声音' : '暂无公开节目'" />
              </van-list>

              <div v-if="trackError" class="track-retry">
                <p role="alert">{{ trackErrorMessage }}</p>
                <van-button size="small" type="primary" @click="retryTracks">重试加载节目</van-button>
              </div>
            </section>

            <div class="footer">
              <van-button block plain type="primary" @click="router.push(`/detail/${albumId}`)">查看听众端专辑详情</van-button>
            </div>
          </div>
        </div>
      </template>
    </div>
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
const payTagClass = computed(() => ({ '0101': 'tag-free', '0102': 'tag-vip', '0103': 'tag-paid' }[album.value?.payType] || 'tag-free'))
// 封面兜底：地址为空或加载失败时回退到纯色首字块，避免出现裂图图标
const brokenCovers = ref(new Set())
let albumVersion = 0
let trackVersion = 0
let sourcePage = 1
let sourceFinished = false
let pendingTracks = []
let alive = true
const limit = 10
const errorMessage = e => e?.response?.data?.message || e?.message || '网络异常，请重试'
function coverBroken(url) { return !url || brokenCovers.value.has(url) }
function markBroken(url) { if (url) brokenCovers.value = new Set(brokenCovers.value).add(url) }
function firstChar(value) { const text = String(value || '').trim(); return text ? text.slice(0, 1) : '知' }
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
  try { await showConfirmDialog({ title: next === '1' ? '公开并上架专辑？' : '设为私密并下架？', message: next === '1' ? '公开后，专辑将进入听众端检索。确认现在上架？' : '专辑将从听众端检索下架。确认改为私密？', confirmButtonColor: '#007aff' }) }
  catch { return }
  mutating.value = true
  actionError.value = ''
  try { await albumsApi.setAlbumOpen(albumId.value, next); album.value.isOpen = next; showSuccessToast(next === '1' ? '上架请求已保存' : '下架请求已保存') }
  catch (e) { actionError.value = errorMessage(e) }
  finally { mutating.value = false }
}
async function deleteAlbum() {
  if (!owner.value || mutating.value) return
  try { await showConfirmDialog({ title: '删除专辑', message: '删除后不可恢复，确认删除此专辑？', confirmButtonColor: '#e0453a' }) }
  catch { return }
  mutating.value = true
  actionError.value = ''
  try { await albumsApi.deleteAlbumInfo(albumId.value); showSuccessToast('删除成功'); router.replace('/myWork') }
  catch (e) { actionError.value = errorMessage(e) }
  finally { mutating.value = false }
}
async function deleteTrack(track) {
  if (!owner.value || mutating.value) return
  try { await showConfirmDialog({ title: '删除声音', message: '将删除声音及其 VOD 媒体文件，无法恢复，是否继续？', confirmButtonColor: '#e0453a' }) }
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
.album-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-6);
}

.album-page :deep(.van-nav-bar) {
  background: var(--surface);
}

.album-page :deep(.van-nav-bar__title) {
  font-size: var(--fs-section);
  font-weight: 600;
  color: var(--ink);
}

.album-page :deep(.van-nav-bar .van-icon) {
  color: var(--brand);
}

.album-page :deep(.van-empty__description) {
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.initial-loading {
  padding: var(--sp-5) 0;
}

.album-layout {
  display: grid;
  gap: var(--sp-4);
  padding-top: var(--sp-4);
}

.album-side {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
}

/* 专辑卡片 */
.album-card {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: var(--sp-3);
  align-items: start;
}

.cover-wrap {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: var(--r-lg);
  overflow: hidden;
  background: var(--line-soft);
}

.cover-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink-4);
}

.album-heading {
  min-width: 0;
}

.album-title {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-title);
  font-weight: 600;
  line-height: 1.35;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.album-meta {
  margin: var(--sp-2) 0 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.album-meta .num {
  color: var(--ink);
}

.dot {
  color: var(--line);
}

/* 数据概览 */
.stats-card .section-head {
  margin-bottom: var(--sp-2);
}

.stats {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.stats li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-2) var(--sp-3);
  border-right: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
}

.stats li:nth-child(2n) {
  border-right: 0;
}

.stats li:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.stats strong {
  font-size: var(--fs-section);
  color: var(--ink);
}

.stats span {
  font-size: var(--fs-micro);
  color: var(--ink-3);
}

/* 管理操作 */
.manage-card .section-head {
  margin-bottom: var(--sp-3);
}

.manage-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.manage-note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-micro);
  color: var(--ink-4);
  line-height: 1.6;
}

.inline-warning {
  margin: 0;
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  background: var(--paid-soft);
  color: var(--paid);
  font-size: var(--fs-meta);
  line-height: 1.6;
}

.inline-warning button {
  border: 0;
  background: none;
  padding: 0 0 0 var(--sp-2);
  color: var(--paid);
  font-size: var(--fs-meta);
  font-weight: 600;
}

/* 简介 */
.intro-panel .section-head {
  margin-bottom: var(--sp-2);
}

.intro {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--ink-2);
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.intro.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.text-button {
  border: 0;
  background: none;
  padding: var(--sp-2) 0 0;
  color: var(--brand);
  font-size: var(--fs-meta);
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

/* 节目列表 */
.episodes {
  margin-top: var(--sp-5);
}

.episodes .section-head {
  margin-bottom: var(--sp-2);
}

.list-note {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-micro);
  color: var(--ink-4);
}

.track-filter {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.track-filter label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.track-filter select {
  max-width: 92px;
  padding: 6px 8px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-meta);
}

.track-filter input {
  flex: 1;
  min-width: 0;
  padding: 7px var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink);
  font-size: var(--fs-meta);
}

.track-filter input::placeholder {
  color: var(--ink-4);
}

.track-list {
  display: grid;
  gap: var(--sp-3);
}

.track-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-3);
}

.track-main {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  border: 0;
  padding: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.track-order {
  flex-shrink: 0;
  width: 24px;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

.track-copy {
  flex: 1;
  min-width: 0;
}

.track-title {
  display: block;
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--ink);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.track-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: var(--sp-1);
  font-size: var(--fs-micro);
  color: var(--ink-4);
}

.track-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-4);
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
}

.track-actions button {
  border: 0;
  background: none;
  padding: 0;
  color: var(--ink-2);
  font-size: var(--fs-meta);
}

.track-actions button:hover {
  color: var(--brand);
}

.track-actions .danger {
  color: var(--paid);
}

.track-actions button:disabled {
  color: var(--ink-4);
  cursor: not-allowed;
}

.track-retry {
  padding: var(--sp-4) 0;
  text-align: center;
  color: var(--paid);
  font-size: var(--fs-meta);
}

.track-retry p {
  margin: 0 0 var(--sp-3);
}

.footer {
  margin-top: var(--sp-5);
}

/* ============================================================
   桌面端：左侧固定专辑信息栏 + 右侧内容区
   ============================================================ */
@media (min-width: 960px) {
  .album-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .album-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
  }

  .album-layout {
    grid-template-columns: 320px minmax(0, 1fr);
    gap: var(--sp-5);
    align-items: start;
  }

  .album-side {
    position: sticky;
    top: 80px;
  }

  .track-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
  }
}

@media (min-width: 1280px) {
  .album-layout {
    grid-template-columns: 360px minmax(0, 1fr);
  }
}
</style>
