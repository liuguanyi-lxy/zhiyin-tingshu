<template>
  <main class="creator-page">
    <van-nav-bar title="我的作品" left-arrow @click-left="back">
      <template #right>
        <router-link to="/index" aria-label="回到首页"><van-icon name="wap-home-o" size="20" /></router-link>
      </template>
    </van-nav-bar>

    <div class="page">
      <van-tabs v-model:active="tab" color="#007aff" title-active-color="#007aff" @change="resetList">
        <van-tab title="专辑" name="album" />
        <van-tab title="声音" name="track" />
        <template v-if="!simple">
          <van-tab title="视频" name="video" />
          <van-tab title="有声PPT" name="ppt" />
          <van-tab title="咔嚓笔记" name="note" />
        </template>
      </van-tabs>

      <template v-if="supported">
        <div class="toolbar">
          <label class="status-filter">
            <span class="filter-label">审核状态</span>
            <select v-model="statuses[tab]" @change="resetList">
              <option value="">全部</option>
              <option :value="tab === 'album' ? '0301' : '0501'">已通过</option>
              <option :value="tab === 'album' ? '0302' : '0502'">未通过</option>
            </select>
          </label>
          <button type="button" class="btn-primary" @click="createItem">
            <van-icon name="plus" size="14" />新建{{ tab === 'album' ? '专辑' : '声音' }}
          </button>
        </div>

        <form class="search-bar" @submit.prevent="resetList">
          <van-icon name="search" size="16" color="#9aa1ab" />
          <input
            v-model="keyword"
            type="search"
            :aria-label="`搜索${tab === 'album' ? '专辑' : '声音'}标题`"
            :placeholder="`搜索${tab === 'album' ? '专辑' : '声音'}标题`"
          />
          <button type="submit">搜索</button>
        </form>

        <p v-if="error" class="alert-error" role="alert">{{ error }}</p>

        <van-pull-refresh v-model="refreshing" @refresh="refresh">
          <van-list
            v-model:loading="loading"
            v-model:error="listError"
            :finished="finished"
            :immediate-check="false"
            error-text="加载失败，点击重试"
            :finished-text="items.length ? '没有更多作品了' : ''"
            @load="loadMore"
          >
            <div class="work-list">
              <article v-for="item in items" :key="itemId(item)" class="work-card" :aria-busy="busyId === itemId(item)">
                <button class="card-main" type="button" @click="openItem(item)">
                  <span class="cover">
                    <img
                      v-if="item.coverUrl && !brokenCovers.has(itemId(item))"
                      :src="item.coverUrl"
                      :alt="displayTitle(item)"
                      @error="markBroken(itemId(item))"
                    />
                    <span v-else class="cover-fallback"><van-icon name="music-o" size="20" /></span>
                  </span>
                  <span class="card-copy">
                    <span class="card-title">{{ displayTitle(item) }}</span>
                    <span class="card-meta">
                      {{ tab === 'album' ? `${item.includeTrackCount ?? '—'} 个声音` : item.albumTitle || '所属专辑未返回' }}
                    </span>
                    <span class="status" :class="{ rejected: ['0302', '0502'].includes(String(item.status)) }">
                      {{ statusText(item.status) }}
                    </span>
                  </span>
                  <van-icon name="arrow" color="#9aa1ab" />
                </button>

                <div class="metrics" aria-label="作品数据">
                  <span v-if="tab === 'track'"><van-icon name="clock-o" /> {{ duration(item.mediaDuration) }}</span>
                  <span><van-icon name="service-o" /> {{ item.playStatNum ?? '—' }} 播放</span>
                  <span>
                    <van-icon :name="tab === 'album' ? 'cart-o' : 'star-o'" />
                    {{ (tab === 'album' ? item.buyStatNum : item.collectStatNum) ?? '—' }}
                    {{ tab === 'album' ? '购买' : '收藏' }}
                  </span>
                  <span><van-icon name="comment-o" /> {{ item.commentStatNum ?? '—' }}</span>
                </div>

                <footer class="card-actions">
                  <button type="button" @click="share(item)"><van-icon name="share-o" /> 分享</button>
                  <button v-if="tab === 'album'" type="button" @click="openItem(item)"><van-icon name="bar-chart-o" /> 管理</button>
                  <button type="button" @click="editItem(item)"><van-icon name="edit" /> 编辑</button>
                  <button type="button" class="danger" :disabled="!!busyId" @click="removeItem(item)">
                    <van-icon name="delete-o" /> 删除
                  </button>
                </footer>
              </article>

              <van-empty
                v-if="!loading && !items.length && !listError"
                :description="`还没有符合条件的${tab === 'album' ? '专辑' : '声音'}`"
              >
                <button type="button" class="btn-primary" @click="createItem">
                  创建{{ tab === 'album' ? '专辑' : '声音' }}
                </button>
              </van-empty>
            </div>
          </van-list>
        </van-pull-refresh>

        <div v-if="listError && !items.length" class="retry">
          <button type="button" class="btn-ghost" @click="retry">重新加载</button>
        </div>
      </template>

      <van-empty v-else :description="`${unsupportedName}创作尚未就绪`">
        <p class="unavailable">当前服务仅提供专辑、声音接口，暂不能创建或管理{{ unsupportedName }}。</p>
        <button type="button" class="btn-primary" @click="tab = 'album'; resetList()">管理专辑</button>
      </van-empty>
    </div>

    <van-popup v-model:show="shareVisible" round position="bottom" closeable class="share-sheet">
      <h2>分享作品</h2>
      <p>可复制以下链接分享给好友</p>
      <input :value="shareUrl" readonly aria-label="作品分享链接" @focus="$event.target.select()" />
      <button type="button" class="btn-primary btn-block" @click="copyShareLink">复制链接</button>
      <p v-if="shareError" class="alert-error" role="alert">{{ shareError }}</p>
    </van-popup>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { albumsApi } from '../../api/albums.js'
import { plainTitle } from '../../utils/format.js'

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
const brokenCovers = ref(new Set())
const supported = computed(() => ['album', 'track'].includes(tab.value))
const unsupportedName = computed(() => ({ video: '视频', ppt: '有声PPT', note: '咔嚓笔记' }[tab.value]))
let page = 1
let generation = 0
let alive = true
const limit = 10
const message = (e) => e?.response?.data?.message || e?.message || '加载失败，请检查网络后重试'
const itemId = (item) => String(tab.value === 'album' ? item.albumId : item.trackId)
const statusText = (value) =>
  ({ '0301': '已通过', '0302': '未通过', '0501': '已通过', '0502': '未通过' })[value] ||
  (value ? `未知状态（${value}）` : '状态未返回')

function displayTitle(item) {
  const raw = tab.value === 'album' ? item.albumTitle : item.trackTitle
  return plainTitle(raw) || (tab.value === 'album' ? '未命名专辑' : '未命名声音')
}
function markBroken(id) {
  brokenCovers.value = new Set(brokenCovers.value).add(id)
}
function duration(value) {
  if (value == null || !Number.isFinite(Number(value))) return '时长未返回'
  const seconds = Math.floor(Number(value))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
function back() {
  window.history.state?.back ? router.back() : router.replace('/my')
}
function createItem() {
  router.push(tab.value === 'album' ? '/createAlbum' : '/createTrack')
}
function editItem(item) {
  router.push(`/${tab.value === 'album' ? 'createAlbum' : 'createTrack'}/${itemId(item)}`)
}
function openItem(item) {
  router.push(tab.value === 'album' ? `/album/${item.albumId}` : `/player/${item.trackId}`)
}
function resetList() {
  generation++
  page = 1
  items.value = []
  error.value = ''
  loading.value = false
  listError.value = false
  brokenCovers.value = new Set()
  finished.value = !supported.value
  if (supported.value) loadMore()
}
function refresh() {
  resetList()
}
function retry() {
  listError.value = false
  loadMore()
}
async function loadMore() {
  if (!supported.value || finished.value) {
    loading.value = false
    refreshing.value = false
    return
  }
  const version = generation
  const activeTab = tab.value
  loading.value = true
  listError.value = false
  error.value = ''
  try {
    const params = {
      page,
      limit,
      status: statuses.value[activeTab],
      [activeTab === 'album' ? 'albumTitle' : 'trackTitle']: keyword.value.trim()
    }
    const { data } = await (activeTab === 'album' ? albumsApi.getAlbumList(params) : albumsApi.getTrackList(params))
    if (!alive || version !== generation) return
    if (!Array.isArray(data?.records)) throw new Error('作品列表响应不完整，请重试')
    items.value.push(...data.records)
    finished.value = data.records.length < limit || (data.total != null && items.value.length >= Number(data.total))
    page++
  } catch (e) {
    if (alive && version === generation) {
      error.value = message(e)
      listError.value = true
    }
  } finally {
    if (alive && version === generation) {
      loading.value = false
      refreshing.value = false
    }
  }
}
async function removeItem(item) {
  const isAlbum = tab.value === 'album'
  const id = itemId(item)
  try {
    await showConfirmDialog({
      title: `删除${isAlbum ? '专辑' : '声音'}`,
      message: isAlbum
        ? '删除后不可恢复。包含声音的专辑不能删除，是否继续？'
        : '将同时删除声音及其 VOD 媒体文件，无法恢复，是否继续？',
      confirmButtonColor: '#007aff'
    })
  } catch {
    return
  }
  busyId.value = id
  try {
    await (isAlbum ? albumsApi.deleteAlbumInfo(id) : albumsApi.deleteTrackInfo(id))
    showSuccessToast('删除成功')
    resetList()
  } catch (e) {
    error.value = message(e)
  } finally {
    busyId.value = ''
  }
}
function share(item) {
  const target = router.resolve(tab.value === 'album' ? `/detail/${item.albumId}` : `/player/${item.trackId}`)
  shareUrl.value = new URL(target.href, window.location.href).href
  shareError.value = ''
  shareVisible.value = true
}
async function copyShareLink() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    showSuccessToast('链接已复制')
  } catch {
    shareError.value = '当前浏览器不支持自动复制，请长按上方链接手动复制。'
  }
}
onBeforeUnmount(() => {
  alive = false
  generation++
})
resetList()
</script>

<style scoped>
.creator-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: calc(var(--sp-6) + env(safe-area-inset-bottom));
  box-sizing: border-box;
  --van-primary-color: var(--brand);
}

.creator-page :deep(.van-nav-bar) {
  background: var(--surface);
}

.creator-page :deep(.van-nav-bar__title) {
  font-size: var(--fs-section);
  font-weight: 600;
  color: var(--ink);
}

.creator-page :deep(.van-nav-bar .van-icon),
.creator-page :deep(.van-nav-bar__text) {
  color: var(--brand);
}

.creator-page :deep(.van-tabs__wrap) {
  background: var(--surface);
}

.creator-page :deep(.van-tab) {
  font-size: var(--fs-body);
  color: var(--ink-3);
}

.creator-page :deep(.van-tab--active) {
  color: var(--brand);
  font-weight: 600;
}

.page {
  padding: 0 var(--sp-3) var(--sp-5);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) 0 var(--sp-2);
}

.status-filter {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.filter-label {
  font-size: var(--fs-meta);
  color: var(--ink-3);
  white-space: nowrap;
}

.status-filter select {
  appearance: none;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-body);
  padding: 6px 10px;
  max-width: 140px;
}

.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 32px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-meta);
  font-weight: 500;
  border: 1px solid transparent;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn-primary {
  background: var(--brand);
  color: #fff;
}

.btn-primary:hover {
  background: var(--brand-strong);
}

.btn-ghost {
  background: var(--surface);
  border-color: var(--line);
  color: var(--ink-2);
}

.btn-ghost:hover {
  border-color: var(--brand-line);
  color: var(--brand);
}

.btn-block {
  width: 100%;
  height: 42px;
  font-size: var(--fs-body);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.search-bar input {
  min-width: 0;
  flex: 1;
  border: 0;
  background: none;
  color: var(--ink);
  font-size: var(--fs-body);
  outline: none;
}

.search-bar input::placeholder {
  color: var(--ink-4);
}

.search-bar button {
  border: 0;
  background: none;
  color: var(--brand);
  font-size: var(--fs-meta);
  padding: 0;
}

.alert-error {
  margin: var(--sp-3) 0 0;
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-sm);
  background: var(--paid-soft);
  color: var(--paid);
  font-size: var(--fs-meta);
  line-height: 1.6;
}

.work-list {
  display: grid;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
}

.work-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-3) var(--sp-4);
}

.card-main {
  width: 100%;
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  text-align: left;
  border: 0;
  padding: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.cover {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--line-soft);
  display: block;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--ink-4);
}

.card-copy {
  min-width: 0;
  flex: 1;
  display: block;
}

.card-title {
  display: block;
  font-size: var(--fs-body);
  font-weight: 600;
  line-height: 1.5;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.card-meta {
  display: block;
  font-size: var(--fs-meta);
  color: var(--ink-3);
  margin: 3px 0 6px;
  overflow-wrap: anywhere;
}

.status {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: var(--r-sm);
  font-size: var(--fs-micro);
  font-weight: 500;
  color: var(--brand);
  background: var(--brand-soft);
}

.status.rejected {
  color: var(--paid);
  background: var(--paid-soft);
}

.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-4);
  font-size: var(--fs-meta);
  color: var(--ink-3);
  padding: var(--sp-3) 0;
  margin-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
}

.card-actions button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  border: 0;
  padding: 0;
  background: none;
  color: var(--ink-2);
  font-size: var(--fs-meta);
  cursor: pointer;
}

.card-actions button:hover {
  color: var(--brand);
}

.card-actions .danger {
  color: var(--paid);
}

.card-actions .danger:hover {
  color: var(--brand-strong);
}

.card-actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.retry {
  text-align: center;
  padding: var(--sp-4) 0;
}

.unavailable {
  max-width: 280px;
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-meta);
  line-height: 1.7;
  margin: 0 auto var(--sp-3);
}

.share-sheet {
  box-sizing: border-box;
  padding: var(--sp-5) var(--sp-4) calc(var(--sp-4) + env(safe-area-inset-bottom));
  max-width: 480px;
  left: 50%;
  transform: translateX(-50%);
}

.share-sheet h2 {
  margin: 0 0 var(--sp-1);
  font-size: var(--fs-section);
  font-weight: 600;
}

.share-sheet p {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

.share-sheet input {
  box-sizing: border-box;
  width: 100%;
  padding: var(--sp-3);
  margin: var(--sp-2) 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-meta);
}

.creator-page :deep(.van-empty__description) {
  font-size: var(--fs-meta);
  color: var(--ink-3);
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .page {
    padding: 0 var(--sp-5) var(--sp-6);
    max-width: var(--shell-max);
    margin: 0 auto;
  }

  .creator-page :deep(.van-nav-bar) {
    background: transparent;
  }

  .creator-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
  }

  .creator-page :deep(.van-tabs__wrap) {
    background: transparent;
  }

  .work-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
    align-items: start;
  }
}

@media (min-width: 1280px) {
  .work-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
