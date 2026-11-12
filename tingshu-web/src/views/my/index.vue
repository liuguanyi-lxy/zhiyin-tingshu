<template>
  <div class="my-page">
    <van-nav-bar title="我的" fixed placeholder />

    <!-- 顶部用户信息区：头像 + 昵称 + 余额；未登录显示"点击登录" -->
    <div class="user-card" @click="userStore.isLogin ? goEntry('/updateInfo') : goLogin()">
      <div class="avatar">
        <img v-if="userStore.user.avatarUrl" :src="userStore.user.avatarUrl" alt="" />
        <van-icon v-else name="manager" size="30" />
      </div>
      <div class="user-info">
        <template v-if="userStore.isLogin">
          <div class="nickname">
            <span class="nickname-text">{{ userStore.user.nickname || '知音用户' }}</span>
            <span v-if="userStore.user.isVip" class="vip-badge">VIP</span>
          </div>
          <div class="balance" @click.stop="goEntry('/wallet')">
            <span class="amount">¥ {{ balanceText }}</span>
            <span class="balance-label">钱包余额 ›</span>
          </div>
        </template>
        <template v-else>
          <div class="nickname">点击登录</div>
          <div class="balance balance-hint">登录后同步收听记录</div>
        </template>
      </div>
      <van-icon name="arrow" color="#c8c9cc" />
    </div>

    <!-- 功能入口宫格（对齐小程序 my.wxml：VIP / 钱包 / 订单 + H5 路由补充项） -->
    <div class="entry-grid">
      <van-grid :column-num="3" :border="false">
        <van-grid-item v-for="entry in entries" :key="entry.path" @click="goEntry(entry.path)">
          <template #default>
            <div class="entry-item">
              <van-icon :name="entry.icon" size="26" :color="entry.color" />
              <span class="entry-name">{{ entry.name }}</span>
            </div>
          </template>
        </van-grid-item>
      </van-grid>
    </div>

    <!-- 快捷区：最近在听 -->
    <div class="section-card">
      <div class="section-title">最近在听</div>
      <div v-if="lately" class="recent-item" @click="playTrack(lately)">
        <img v-if="lately.coverUrl" class="cover" :src="lately.coverUrl" alt="" />
        <div v-else class="cover cover-fallback"><van-icon name="music-o" size="24" /></div>
        <div class="item-info">
          <div class="item-title">{{ lately.trackTitle || '继续上次收听' }}</div>
          <div class="item-meta">{{ lately.albumTitle || '点击继续播放' }}</div>
        </div>
        <van-icon name="play-circle-o" size="26" color="#1989fa" />
      </div>
      <div v-else class="empty-line">暂无收听记录</div>
    </div>

    <!-- 快捷区：我的收藏（前 3 条） -->
    <div class="section-card">
      <div class="section-title">
        <span>我的收藏</span>
        <span class="section-more" @click="goCollectTab">查看全部 ›</span>
      </div>
      <template v-if="quickCollect.length">
        <div
          v-for="item in quickCollect"
          :key="item.trackId"
          class="recent-item"
          @click="playTrack(item)"
        >
          <img v-if="item.coverUrl" class="cover" :src="item.coverUrl" alt="" />
          <div v-else class="cover cover-fallback"><van-icon name="music-o" size="24" /></div>
          <div class="item-info">
            <div class="item-title">{{ item.trackTitle || '未知声音' }}</div>
            <div class="item-meta">收藏于 {{ (item.createTime || '').slice(0, 10) }}</div>
          </div>
          <van-icon name="play-circle-o" size="26" color="#1989fa" />
        </div>
      </template>
      <div v-else class="empty-line">暂无收藏</div>
    </div>

    <!-- 订阅 / 收藏 / 历史 列表（对齐小程序 z-paging 区块，支持左滑取消/删除） -->
    <div ref="listSection" class="section-card list-section">
      <van-tabs v-model:active="activeTab" sticky offset-top="46" @change="onTabChange">
        <van-tab title="订阅" name="subscribe">
          <div v-if="tabState.subscribe.loading && tabState.subscribe.list.length === 0" class="loading-line">
            <van-loading size="20">加载中...</van-loading>
          </div>
          <template v-else>
            <van-swipe-cell v-for="item in tabState.subscribe.list" :key="item.albumId">
              <div class="row" @click="goAlbum(item)">
                <img v-if="item.coverUrl" class="row-cover" :src="item.coverUrl" alt="" />
                <div v-else class="row-cover cover-fallback"><van-icon name="music-o" size="22" /></div>
                <div class="row-info">
                  <div class="row-title">{{ item.albumTitle || '未知专辑' }}</div>
                  <div class="row-meta">订阅于 {{ (item.createTime || '').slice(0, 10) }}</div>
                </div>
                <van-icon name="arrow" color="#c8c9cc" />
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
                <img v-if="item.coverUrl" class="row-cover" :src="item.coverUrl" alt="" />
                <div v-else class="row-cover cover-fallback"><van-icon name="music-o" size="22" /></div>
                <div class="row-info">
                  <div class="row-title">{{ item.trackTitle || '未知声音' }}</div>
                  <div class="row-meta">收藏于 {{ (item.createTime || '').slice(0, 10) }}</div>
                </div>
                <van-icon name="play-circle-o" size="24" color="#1989fa" />
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
                <img v-if="item.coverUrl" class="row-cover" :src="item.coverUrl" alt="" />
                <div v-else class="row-cover cover-fallback"><van-icon name="music-o" size="22" /></div>
                <div class="row-info">
                  <div class="row-title">{{ item.trackTitle || '未知声音' }}</div>
                  <div class="row-meta">
                    专辑：{{ item.albumTitle || '未知专辑' }} · {{ formatDuration(item.mediaDuration) }}
                    <span v-if="item.playRate"> · 已播 {{ item.playRate }}</span>
                  </div>
                </div>
                <van-icon name="play-circle-o" size="24" color="#1989fa" />
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
    </div>

    <!-- 退出登录 -->
    <div v-if="userStore.isLogin" class="logout-bar">
      <van-button block type="danger" plain @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { userApi } from '../../api/user.js'
import { accountApi } from '../../api/account.js'
import { useUserStore } from '../../stores/user.js'

const router = useRouter()
const userStore = useUserStore()

// ===== 顶部用户信息区：余额 =====
const balance = ref(null)
const balanceText = computed(() => (balance.value === null ? '--' : Number(balance.value).toFixed(2)))

// ===== 快捷区 =====
const lately = ref(null)
const quickCollect = ref([])

// ===== 功能入口宫格（对齐小程序 my.wxml 实际入口 + H5 已有路由） =====
const entries = [
  { name: 'VIP 频道', path: '/vipChannel', icon: 'vip-card-o', color: '#ee9a01' },
  { name: '我的钱包', path: '/wallet', icon: 'balance-o', color: '#ff976a' },
  { name: '我的订单', path: '/order', icon: 'orders-o', color: '#1989fa' },
  { name: '我的作品', path: '/myWork', icon: 'edit', color: '#07c160' },
  { name: '资金明细', path: '/fund', icon: 'bill-o', color: '#ee0a24' },
  { name: '设置', path: '/setting', icon: 'setting-o', color: '#646566' }
]

// ===== 订阅 / 收藏 / 历史 分页列表 =====
const activeTab = ref('subscribe')
const listSection = ref(null)
const LIMIT = 10
const tabState = reactive({
  subscribe: { list: [], page: 0, hasMore: false, loaded: false, loading: false },
  collect: { list: [], page: 0, hasMore: false, loaded: false, loading: false },
  history: { list: [], page: 0, hasMore: false, loaded: false, loading: false }
})
const tabApis = {
  subscribe: (pageInfo) => userApi.getSubscribeAlbums(pageInfo),
  collect: (pageInfo) => userApi.getCollectTrack(pageInfo),
  history: (pageInfo) => userApi.getHistoryTrack(pageInfo)
}

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
    st.loaded = true
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
  } catch {
    quickCollect.value = []
  }
}

onMounted(() => {
  if (!userStore.isLogin) return
  loadBalance()
  loadLately()
  loadQuickCollect()
  loadTab('subscribe', true)
})

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
  background: #f7f8fa;
  padding-bottom: 20px;
}

/* 用户信息卡片 */
.user-card {
  display: flex;
  align-items: center;
  margin: 10px 12px 0;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #c8c9cc;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-info {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}
.nickname {
  font-size: 17px;
  font-weight: 600;
  color: #323233;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nickname-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vip-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: normal;
  color: #fff;
  background: #ee9a01;
  padding: 1px 5px;
  border-radius: 4px;
}
.balance {
  margin-top: 6px;
  font-size: 13px;
  color: #969799;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.balance .amount {
  color: #ee0a24;
  font-size: 16px;
  font-weight: 600;
}
.balance-hint {
  color: #c8c9cc;
}

/* 功能入口宫格 */
.entry-grid {
  margin: 10px 12px 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
}
.entry-name {
  font-size: 12px;
  color: #646566;
}

/* 快捷区块 */
.section-card {
  margin: 10px 12px 0;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.section-more {
  font-size: 12px;
  font-weight: normal;
  color: #969799;
  cursor: pointer;
}
.recent-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
}
.cover {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f0f0f0;
}
.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c8c9cc;
}
.item-info {
  flex: 1;
  margin-left: 10px;
  min-width: 0;
}
.item-title {
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-meta {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty-line {
  padding: 12px 0;
  text-align: center;
  font-size: 13px;
  color: #c8c9cc;
}

/* 订阅 / 收藏 / 历史 列表 */
.list-section {
  padding: 0 0 8px;
}
.row {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.row-cover {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f0f0f0;
}
.row-info {
  flex: 1;
  margin-left: 10px;
  min-width: 0;
}
.row-title {
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.swipe-btn {
  height: 100%;
}
.loading-line {
  padding: 16px 0;
  text-align: center;
}
.more-line {
  padding: 10px 0;
  text-align: center;
  font-size: 13px;
  color: #1989fa;
  cursor: pointer;
}
.no-more-line {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  color: #c8c9cc;
}

/* 退出登录 */
.logout-bar {
  margin: 20px 12px 0;
}
</style>
