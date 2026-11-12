<template>
  <div class="my-page">
    <van-nav-bar title="我的" fixed placeholder />

    <div class="page my-body">
      <div class="my-grid">
        <!-- 左栏：个人资料 / 功能入口 / 数据统计 -->
        <aside class="my-side">
          <!-- 顶部用户信息区：头像 + 昵称 + 账号信息；未登录显示"点击登录" -->
          <section class="tingshu-card user-card" @click="userStore.isLogin ? goEntry('/updateInfo') : goLogin()">
            <div class="avatar">
              <img
                v-if="userStore.user.avatarUrl && !coverBroken(userStore.user.avatarUrl)"
                :src="userStore.user.avatarUrl"
                alt=""
                @error="markBroken(userStore.user.avatarUrl)"
              />
              <span v-else-if="userStore.isLogin" class="avatar-text">{{ avatarInitial }}</span>
              <van-icon v-else name="user-o" size="24" />
            </div>
            <div class="user-info">
              <template v-if="userStore.isLogin">
                <div class="nickname">
                  <span class="nickname-text">{{ userStore.user.nickname || '知音用户' }}</span>
                  <span v-if="userStore.user.isVip" class="tag tag-vip">VIP</span>
                </div>
                <div class="account" @click.stop="goEntry('/wallet')">
                  <span class="amount num">¥ {{ balanceText }}</span>
                  <span class="account-label">钱包余额</span>
                  <van-icon name="arrow" size="13" />
                </div>
                <p class="account-meta">账号 ID {{ userStore.user.id ?? '—' }}<span v-if="userStore.user.isVip && userStore.user.vipExpireTime"> · 会员到期 {{ vipDate }}</span></p>
              </template>
              <template v-else>
                <div class="nickname"><span class="nickname-text">点击登录</span></div>
                <div class="account"><span class="account-label">登录后同步收听记录</span></div>
              </template>
            </div>
          </section>

          <!-- 功能入口宫格（对齐小程序 my.wxml：VIP / 钱包 / 订单 + H5 路由补充项） -->
          <section class="tingshu-card entry-card">
            <div class="section-head"><h2 class="section-title">功能入口</h2></div>
            <div class="entry-grid">
              <button
                v-for="entry in entries"
                :key="entry.path"
                type="button"
                class="entry-item"
                :data-tone="entry.tone"
                @click="goEntry(entry.path)"
              >
                <span class="entry-icon"><van-icon :name="entry.icon" size="20" /></span>
                <span class="entry-name">{{ entry.name }}</span>
              </button>
            </div>
          </section>

          <!-- 数据统计 -->
          <section class="tingshu-card stat-card">
            <div class="section-head"><h2 class="section-title">我的数据</h2></div>
            <div class="stat-grid">
              <button type="button" class="stat-item" @click="goCollectTab">
                <strong class="num">{{ collectCount }}</strong><span>收藏</span>
              </button>
              <button type="button" class="stat-item" @click="goSubscribeTab">
                <strong class="num">{{ subscribeCount }}</strong><span>订阅</span>
              </button>
              <button type="button" class="stat-item" @click="goEntry('/myWork')">
                <strong class="num">{{ createdCount }}</strong><span>创作</span>
              </button>
            </div>
          </section>
        </aside>

        <!-- 右栏：最近在听 / 我的收藏 / 订阅收藏历史 -->
        <div class="my-main">
          <section class="tingshu-card block-card">
            <div class="section-head"><h2 class="section-title">最近在听</h2></div>
            <div v-if="lately" class="media-item" @click="playTrack(lately)">
              <div class="cover">
                <img
                  v-if="!coverBroken(lately.coverUrl)"
                  :src="lately.coverUrl"
                  alt=""
                  loading="lazy"
                  @error="markBroken(lately.coverUrl)"
                />
                <span v-else class="cover-fallback">{{ firstChar(lately.trackTitle) }}</span>
              </div>
              <div class="item-info">
                <div class="item-title">{{ lately.trackTitle || '继续上次收听' }}</div>
                <div class="item-meta">{{ lately.albumTitle || '点击继续播放' }}</div>
              </div>
              <van-icon class="play-icon" name="play-circle-o" size="22" />
            </div>
            <p v-else class="empty-line">暂无收听记录</p>
          </section>

          <section class="tingshu-card block-card">
            <div class="section-head">
              <h2 class="section-title">我的收藏</h2>
              <button type="button" class="section-action" @click="goCollectTab">查看全部 <van-icon name="arrow" /></button>
            </div>
            <template v-if="quickCollect.length">
              <div
                v-for="item in quickCollect"
                :key="item.trackId"
                class="media-item"
                @click="playTrack(item)"
              >
                <div class="cover">
                  <img
                    v-if="!coverBroken(item.coverUrl)"
                    :src="item.coverUrl"
                    alt=""
                    loading="lazy"
                    @error="markBroken(item.coverUrl)"
                  />
                  <span v-else class="cover-fallback">{{ firstChar(item.trackTitle) }}</span>
                </div>
                <div class="item-info">
                  <div class="item-title">{{ item.trackTitle || '未知声音' }}</div>
                  <div class="item-meta">收藏于 {{ (item.createTime || '').slice(0, 10) }}</div>
                </div>
                <van-icon class="play-icon" name="play-circle-o" size="22" />
              </div>
            </template>
            <p v-else class="empty-line">暂无收藏</p>
          </section>

          <!-- 订阅 / 收藏 / 历史 列表（支持左滑取消/删除） -->
          <section ref="listSection" class="tingshu-card list-section">
            <van-tabs v-model:active="activeTab" :sticky="!isDesktop" :offset-top="isDesktop ? 60 : 46" @change="onTabChange">
              <van-tab title="订阅" name="subscribe">
                <div v-if="tabState.subscribe.loading && tabState.subscribe.list.length === 0" class="loading-line">
                  <van-loading size="20">加载中...</van-loading>
                </div>
                <template v-else>
                  <van-swipe-cell v-for="item in tabState.subscribe.list" :key="item.albumId">
                    <div class="row" @click="goAlbum(item)">
                      <div class="cover">
                        <img
                          v-if="!coverBroken(item.coverUrl)"
                          :src="item.coverUrl"
                          alt=""
                          loading="lazy"
                          @error="markBroken(item.coverUrl)"
                        />
                        <span v-else class="cover-fallback">{{ firstChar(item.albumTitle) }}</span>
                      </div>
                      <div class="row-info">
                        <div class="row-title">{{ item.albumTitle || '未知专辑' }}</div>
                        <div class="row-meta">订阅于 {{ (item.createTime || '').slice(0, 10) }}</div>
                      </div>
                      <van-icon name="arrow" class="row-arrow" size="16" />
                    </div>
                    <template #right>
                      <van-button square type="danger" text="取消订阅" class="swipe-btn" @click="cancelItem('subscribe', item)" />
                    </template>
                  </van-swipe-cell>
                  <van-empty v-if="tabState.subscribe.list.length === 0" description="暂无订阅专辑" />
                  <div v-else-if="tabState.subscribe.hasMore" class="more-line" @click="loadTab('subscribe')">加载更多</div>
                  <div v-else class="no-more-line">没有更多了</div>
                </template>
              </van-tab>

              <van-tab title="收藏" name="collect">
                <div v-if="tabState.collect.loading && tabState.collect.list.length === 0" class="loading-line">
                  <van-loading size="20">加载中...</van-loading>
                </div>
                <template v-else>
                  <van-swipe-cell v-for="item in tabState.collect.list" :key="item.trackId">
                    <div class="row" @click="playTrack(item)">
                      <div class="cover">
                        <img
                          v-if="!coverBroken(item.coverUrl)"
                          :src="item.coverUrl"
                          alt=""
                          loading="lazy"
                          @error="markBroken(item.coverUrl)"
                        />
                        <span v-else class="cover-fallback">{{ firstChar(item.trackTitle) }}</span>
                      </div>
                      <div class="row-info">
                        <div class="row-title">{{ item.trackTitle || '未知声音' }}</div>
                        <div class="row-meta">收藏于 {{ (item.createTime || '').slice(0, 10) }}</div>
                      </div>
                      <van-icon class="play-icon" name="play-circle-o" size="20" />
                    </div>
                    <template #right>
                      <van-button square type="danger" text="取消收藏" class="swipe-btn" @click="cancelItem('collect', item)" />
                    </template>
                  </van-swipe-cell>
                  <van-empty v-if="tabState.collect.list.length === 0" description="暂无收藏声音" />
                  <div v-else-if="tabState.collect.hasMore" class="more-line" @click="loadTab('collect')">加载更多</div>
                  <div v-else class="no-more-line">没有更多了</div>
                </template>
              </van-tab>

              <van-tab title="历史" name="history">
                <div v-if="tabState.history.loading && tabState.history.list.length === 0" class="loading-line">
                  <van-loading size="20">加载中...</van-loading>
                </div>
                <template v-else>
                  <van-swipe-cell v-for="item in tabState.history.list" :key="item.id ?? item.trackId">
                    <div class="row" @click="playTrack(item)">
                      <div class="cover">
                        <img
                          v-if="!coverBroken(item.coverUrl)"
                          :src="item.coverUrl"
                          alt=""
                          loading="lazy"
                          @error="markBroken(item.coverUrl)"
                        />
                        <span v-else class="cover-fallback">{{ firstChar(item.trackTitle) }}</span>
                      </div>
                      <div class="row-info">
                        <div class="row-title">{{ item.trackTitle || '未知声音' }}</div>
                        <div class="row-meta">
                          专辑：{{ item.albumTitle || '未知专辑' }} · {{ formatDuration(item.mediaDuration) }}
                          <span v-if="item.playRate"> · 已播 {{ item.playRate }}</span>
                        </div>
                      </div>
                      <van-icon class="play-icon" name="play-circle-o" size="20" />
                    </div>
                    <template #right>
                      <van-button square type="danger" text="删除" class="swipe-btn" @click="cancelItem('history', item)" />
                    </template>
                  </van-swipe-cell>
                  <van-empty v-if="tabState.history.list.length === 0" description="暂无播放历史" />
                  <div v-else-if="tabState.history.hasMore" class="more-line" @click="loadTab('history')">加载更多</div>
                  <div v-else class="no-more-line">没有更多了</div>
                </template>
              </van-tab>
            </van-tabs>
          </section>
        </div>
      </div>

      <!-- 退出登录 -->
      <div v-if="userStore.isLogin" class="logout-bar">
        <van-button block type="danger" plain @click="logout">退出登录</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { userApi } from '../../api/user.js'
import { accountApi } from '../../api/account.js'
import { albumsApi } from '../../api/albums.js'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()

// ===== 顶部用户信息区：余额 =====
const balance = ref(null)
const balanceText = computed(() => (balance.value === null ? '--' : Number(balance.value).toFixed(2)))
const avatarInitial = computed(() => (userStore.user?.nickname || '知').slice(0, 1))
const vipDate = computed(() => String(userStore.user?.vipExpireTime || '').slice(0, 10))

// ===== 快捷区 =====
const lately = ref(null)
const quickCollect = ref([])

// ===== 数据统计 =====
const collectCount = ref(0)
const subscribeCount = ref(0)
const createdCount = ref(0)

// ===== 封面兜底：避免空地址或加载失败出现裂图 =====
const brokenCovers = reactive(new Set())
function coverBroken(url) { return !url || brokenCovers.has(url) }
function markBroken(url) { if (url) brokenCovers.add(url) }
function firstChar(value) {
  const text = String(value || '').trim()
  return text ? text.slice(0, 1) : '知'
}

// ===== 功能入口宫格（对齐小程序 my.wxml 实际入口 + H5 已有路由） =====
const entries = [
  { name: 'VIP 频道', path: '/vipChannel', icon: 'vip-card-o', tone: 'vip' },
  { name: '我的钱包', path: '/wallet', icon: 'balance-o', tone: 'brand' },
  { name: '我的订单', path: '/order', icon: 'orders-o', tone: 'brand' },
  { name: '我的作品', path: '/myWork', icon: 'edit', tone: 'brand' },
  { name: '资金明细', path: '/fund', icon: 'bill-o', tone: 'muted' },
  { name: '设置', path: '/setting', icon: 'setting-o', tone: 'muted' }
]

// ===== 订阅 / 收藏 / 历史 分页列表 =====
const activeTab = ref('subscribe')
const listSection = ref(null)
const LIMIT = 10
const tabState = reactive({
  subscribe: { list: [], page: 0, hasMore: false, loaded: false, loading: false, total: 0 },
  collect: { list: [], page: 0, hasMore: false, loaded: false, loading: false, total: 0 },
  history: { list: [], page: 0, hasMore: false, loaded: false, loading: false, total: 0 }
})
const tabApis = {
  subscribe: (pageInfo) => userApi.getSubscribeAlbums(pageInfo),
  collect: (pageInfo) => userApi.getCollectTrack(pageInfo),
  history: (pageInfo) => userApi.getHistoryTrack(pageInfo)
}

// ===== 桌面端断点：tabs 吸顶偏移与布局 =====
const desktopQuery = window.matchMedia('(min-width: 960px)')
const isDesktop = ref(desktopQuery.matches)
function handleDesktopChange(event) { isDesktop.value = event.matches }
desktopQuery.addEventListener('change', handleDesktopChange)

async function loadTab(key, reload = false) {
  const st = tabState[key]
  if (!st || st.loading) return
  if (reload) {
    st.page = 0
    st.list = []
    st.loaded = false
  }
  st.loading = true
  try {
    const page = st.page + 1
    const res = await tabApis[key]({ page, limit: LIMIT })
    const records = res.data?.records || []
    st.list = page === 1 ? records : st.list.concat(records)
    st.page = page
    st.hasMore = records.length >= LIMIT
    st.total = Number(res.data?.total) || st.list.length
    st.loaded = true
    if (key === 'subscribe') subscribeCount.value = st.total
  } catch (e) {
    st.loaded = true
    st.hasMore = false
    showToast('列表加载失败，请稍后重试')
  } finally {
    st.loading = false
  }
}

function onTabChange(name) {
  const st = tabState[name]
  if (st && !st.loaded && !st.loading) loadTab(name)
}

// 取消订阅 / 取消收藏 / 删除历史（对齐小程序确认弹窗 + toggle 接口）
const cancelConfigs = {
  subscribe: {
    message: '你确定要取消订阅吗？',
    idOf: (item) => item.albumId,
    request: (item) => userApi.subscribeAlbum(item.albumId)
  },
  collect: {
    message: '你确定要取消收藏吗？',
    idOf: (item) => item.trackId,
    request: (item) => userApi.collectTrack(item.trackId)
  },
  history: {
    message: '你确定要删除此历史吗？',
    idOf: (item) => item.id ?? item.trackId,
    request: (item) => userApi.deleteHistoryTrack(item.id ?? item.trackId)
  }
}

async function cancelItem(key, item) {
  const cfg = cancelConfigs[key]
  try {
    await showConfirmDialog({ title: '注意', message: cfg.message })
  } catch {
    return
  }
  try {
    await cfg.request(item)
    tabState[key].list = tabState[key].list.filter((it) => cfg.idOf(it) !== cfg.idOf(item))
    if (key === 'subscribe') subscribeCount.value = Math.max(0, subscribeCount.value - 1)
    showToast('操作成功')
    if (key === 'collect') loadQuickCollect()
  } catch {
    showToast('操作失败，请重试')
  }
}

// ===== 跳转 =====
function goLogin() {
  router.push('/login')
}
function goEntry(path) {
  router.push(path)
}
function playTrack(item) {
  if (!item?.trackId) return
  const query = item.albumId ? `?albumId=${item.albumId}` : ''
  router.push(`/player/${item.trackId}${query}`)
}
function goAlbum(item) {
  if (item?.albumId) router.push(`/detail/${item.albumId}`)
}
function goCollectTab() {
  activeTab.value = 'collect'
  if (!tabState.collect.loaded && !tabState.collect.loading) loadTab('collect')
  nextTick(() => listSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}
function goSubscribeTab() {
  activeTab.value = 'subscribe'
  if (!tabState.subscribe.loaded && !tabState.subscribe.loading) loadTab('subscribe')
  nextTick(() => listSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

// ===== 退出登录：清 store 后跳登录页 =====
async function logout() {
  try {
    await showConfirmDialog({ title: '提示', message: '确定要退出登录吗？' })
  } catch {
    return
  }
  userStore.clear()
  showToast('已退出登录')
  router.push('/login')
}

// ===== 初始化 =====
async function loadBalance() {
  try {
    const res = await accountApi.getAvailableAmount()
    balance.value = res.data ?? 0
  } catch {
    balance.value = null
  }
}
async function loadLately() {
  try {
    const res = await userApi.getLatelyTrack()
    lately.value = res.data?.trackId ? res.data : null
  } catch {
    lately.value = null
  }
}
async function loadQuickCollect() {
  try {
    const res = await userApi.getCollectTrack({ page: 1, limit: 3 })
    quickCollect.value = res.data?.records || []
    collectCount.value = Number(res.data?.total) || quickCollect.value.length
  } catch {
    quickCollect.value = []
  }
}
async function loadCreatedCount() {
  try {
    const res = await albumsApi.getAlbumList({ page: 1, limit: 1, status: '', albumTitle: null })
    createdCount.value = Number(res.data?.total) || 0
  } catch {
    createdCount.value = 0
  }
}

onMounted(() => {
  if (!userStore.isLogin) return
  loadBalance()
  loadLately()
  loadQuickCollect()
  loadCreatedCount()
  loadTab('subscribe', true)
})
onBeforeUnmount(() => desktopQuery.removeEventListener('change', handleDesktopChange))

function formatDuration(sec) {
  if (!sec && sec !== 0) return '--:--'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
</script>

<style scoped>
.my-page {
  min-height: 100vh;
  background: var(--ground);
  color: var(--ink);
  padding-bottom: var(--sp-5);
}
.my-body { padding-top: var(--sp-4); }
.my-grid { display: grid; gap: var(--sp-4); }

/* 用户信息卡片 */
.user-card {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  cursor: pointer;
}
.avatar {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: var(--r-lg);
  background: var(--line-soft);
  color: var(--ink-4);
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-text {
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink-3);
}
.user-info {
  flex: 1;
  min-width: 0;
}
.nickname {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink);
}
.nickname-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin-top: var(--sp-1);
  font-size: var(--fs-meta);
  color: var(--ink-3);
}
.account .amount {
  color: var(--brand);
  font-size: var(--fs-section);
}
.account-label { color: var(--ink-4); }
.account-meta {
  margin: 4px 0 0;
  font-size: var(--fs-micro);
  color: var(--ink-4);
}

/* 功能入口宫格 */
.entry-card .section-head { margin-bottom: var(--sp-3); }
.entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-2);
}
.entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--sp-2) 0;
  border: 0;
  border-radius: var(--r-md);
  background: transparent;
  transition: background 0.15s ease;
}
.entry-item:hover { background: var(--ground); }
.entry-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: var(--r-md);
  background: var(--line-soft);
  color: var(--ink-2);
}
.entry-item[data-tone='brand'] .entry-icon {
  background: var(--brand-soft);
  color: var(--brand);
}
.entry-item[data-tone='vip'] .entry-icon {
  background: var(--vip-soft);
  color: var(--vip);
}
.entry-name {
  font-size: var(--fs-meta);
  color: var(--ink-2);
}

/* 数据统计 */
.stat-card .section-head { margin-bottom: var(--sp-2); }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: var(--sp-2) 0;
  border: 0;
  border-right: 1px solid var(--line-soft);
  background: transparent;
}
.stat-item:last-child { border-right: 0; }
.stat-item:hover strong { color: var(--brand); }
.stat-item strong {
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--ink);
}
.stat-item span {
  font-size: var(--fs-micro);
  color: var(--ink-3);
}

/* 最近在听 / 我的收藏 */
.block-card .section-head { margin-bottom: var(--sp-2); }
.media-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
  cursor: pointer;
}
.media-item + .media-item { border-top: 1px solid var(--line-soft); }
.cover {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: var(--r-md);
  background: var(--line-soft);
  color: var(--ink-4);
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-fallback {
  font-size: var(--fs-section);
  font-weight: 600;
  color: var(--ink-4);
}
.item-info {
  flex: 1;
  min-width: 0;
}
.item-title {
  font-size: var(--fs-body);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-meta {
  margin-top: 3px;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.play-icon {
  flex-shrink: 0;
  color: var(--brand);
}
.empty-line {
  margin: 0;
  padding: var(--sp-4) 0;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

/* 订阅 / 收藏 / 历史 列表 */
.list-section {
  padding: 0;
}
.list-section :deep(.van-tabs__wrap) {
  border-radius: var(--r-lg) var(--r-lg) 0 0;
}
.row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}
.row:hover { background: var(--ground); }
.row-info {
  flex: 1;
  min-width: 0;
}
.row-title {
  font-size: var(--fs-body);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  margin-top: 3px;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-arrow { color: var(--ink-4); flex-shrink: 0; }
.swipe-btn { height: 100%; }
.loading-line {
  padding: var(--sp-4) 0;
  text-align: center;
}
.more-line {
  padding: var(--sp-3) 0;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--brand);
  cursor: pointer;
}
.no-more-line {
  padding: var(--sp-3) 0;
  text-align: center;
  font-size: var(--fs-micro);
  color: var(--ink-4);
}

/* 退出登录 */
.logout-bar {
  margin-top: var(--sp-5);
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .my-grid {
    grid-template-columns: 320px minmax(0, 1fr);
    gap: var(--sp-5);
    align-items: start;
  }
  .my-side {
    display: flex;
    flex-direction: column;
    gap: var(--sp-4);
    position: sticky;
    top: 80px;
  }
  .entry-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .list-section :deep(.van-tabs__wrap) {
    background: var(--surface);
  }
  .list-section :deep(.van-tab) { font-size: var(--fs-body); }
  .list-section :deep(.van-tab--active) { font-weight: 600; }
  .list-section :deep(.van-tabs__line) {
    height: 2px;
    border-radius: 1px;
    background: var(--brand);
  }
}
</style>
