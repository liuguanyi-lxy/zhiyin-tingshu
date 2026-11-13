<template>
  <div class="index-page">
    <!-- 移动端顶栏 -->
    <header class="home-header">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">知</span>
        <span class="brand-text">
          <strong>知音听书</strong>
          <small>听见更大的世界</small>
        </span>
      </div>
      <button class="search-entry" type="button" @click="router.push('/search')">
        <van-icon name="search" aria-hidden="true" /><span>搜索专辑、主播</span>
      </button>
    </header>

    <van-tabs
      v-model:active="activeTab"
      sticky
      class="channel-tabs"
      title-active-color="#007aff"
      @change="loadChannel"
    >
      <van-tab v-for="cat in categories" :key="cat.id" :title="cat.name" :name="cat.id" />
    </van-tabs>

    <div class="page home-body">
      <!-- 精选推荐 -->
      <section class="carousel" aria-label="精选推荐">
        <van-swipe ref="swipeRef" :autoplay="reduceMotion ? 0 : 5000" :show-indicators="false">
          <van-swipe-item v-for="(banner, index) in banners" :key="banner">
            <img :src="banner" :alt="`知音听书精选推荐 ${index + 1}`" :loading="index ? 'lazy' : 'eager'" />
          </van-swipe-item>
          <!-- Vant 默认指示器不支持点击切换，改自定义圆点 + swipeTo -->
          <template #indicator="{ active, total }">
            <div class="swipe-dots">
              <button
                v-for="(_, index) in total"
                :key="index"
                type="button"
                class="swipe-dot"
                :class="{ active: index === active }"
                :aria-label="`查看第 ${index + 1} 张轮播图`"
                @click="goBanner(index)"
              ></button>
            </div>
          </template>
        </van-swipe>
      </section>

      <!-- 频道快捷入口 -->
      <nav v-if="topCategories.length" class="category-shortcuts" aria-label="频道分类">
        <button v-for="cat in topCategories" :key="cat.id" type="button" @click="openSearch(cat)">
          {{ cat.name }}
        </button>
        <button type="button" class="all-categories" @click="goAllCategories">
          全部分类 <van-icon name="arrow" />
        </button>
      </nav>

      <div v-if="loading" class="loading-wrap"><van-skeleton v-for="n in 3" :key="n" title avatar :row="3" /></div>
      <van-empty v-else-if="failed" image="network" description="频道加载失败，请检查网络后重试">
        <van-button size="small" round @click="retry">重新加载</van-button>
      </van-empty>

      <template v-else>
        <section v-for="(group, index) in groups" :key="group.baseCategory3?.id || index" class="recommend-group">
          <div class="section">
            <div class="section-head">
              <h2 class="section-title">{{ group.baseCategory3?.name || '频道精选' }}</h2>
              <button type="button" class="section-action" @click="openSearch(group.baseCategory3, group.list?.[0])">
                查看全部 <van-icon name="arrow" />
              </button>
            </div>
            <div class="album-grid">
              <router-link v-for="album in group.list" :key="album.id" :to="`/detail/${album.id}`" class="album-card">
                <div class="cover-wrap">
                  <img
                    v-if="album.coverUrl && !brokenCovers.has(album.id)"
                    :src="album.coverUrl"
                    :alt="plainTitle(album.albumTitle)"
                    loading="lazy"
                    @error="markBroken(album.id)"
                  />
                  <span v-else class="cover-fallback" aria-hidden="true">
                    {{ (plainTitle(album.albumTitle) || '知').slice(0, 1) }}
                  </span>
                  <span v-if="payLabels[album.payType]" class="pay-label" :class="payClass[album.payType]">
                    {{ payLabels[album.payType] }}
                  </span>
                  <span class="play-badge"><van-icon name="play-circle-o" /> {{ formatCount(album.playStatNum) }}</span>
                </div>
                <h3>
                  <template v-for="(part, index) in titleParts(album.albumTitle)" :key="index">
                    <mark v-if="part.highlight">{{ part.text }}</mark>
                    <template v-else>{{ part.text }}</template>
                  </template>
                </h3>
                <p>
                  {{ album.includeTrackCount ?? 0 }} 集
                  <span v-if="album.announcerName" class="dot">·</span>
                  <span v-if="album.announcerName">{{ album.announcerName }}</span>
                </p>
              </router-link>
            </div>
          </div>
        </section>
        <van-empty v-if="!groups.length" description="这个频道还没有专辑，换个分类看看" />
        <p v-else class="end-note">听见更大的世界</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi } from '../../api/category.js'
import { titleParts, plainTitle } from '../../utils/format.js'
import bannerA from '../../assets/carousel/a.png'
import bannerB from '../../assets/carousel/b.png'
import bannerC from '../../assets/carousel/c.png'

const router = useRouter()
const banners = [bannerA, bannerB, bannerC]
const swipeRef = ref(null)
/** 指示器点击切换到指定轮播图（Vant 默认指示器只展示、不可点） */
function goBanner(index) { swipeRef.value?.swipeTo(index) }
const payLabels = { '0101': '免费', '0102': 'VIP免费', '0103': '付费' }
const payClass = { '0101': 'tag-free', '0102': 'tag-vip', '0103': 'tag-paid' }
const categories = ref([])
const activeTab = ref(0)
const groups = ref([])
const topCategories = ref([])
const brokenCovers = ref(new Set())
const loading = ref(true)
const failed = ref(false)
const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
const reduceMotion = ref(motionQuery.matches)
const onMotionChange = (event) => { reduceMotion.value = event.matches }
motionQuery.addEventListener('change', onMotionChange)
let requestVersion = 0

onMounted(retry)
onBeforeUnmount(() => {
  requestVersion++
  motionQuery.removeEventListener('change', onMotionChange)
})

async function retry() {
  loading.value = true
  failed.value = false
  try {
    if (!categories.value.length) {
      const res = await categoryApi.findAllCategory1()
      categories.value = Array.isArray(res.data) ? res.data : []
      activeTab.value = categories.value[0]?.id || 0
    }
    if (activeTab.value) await loadChannel(activeTab.value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function loadChannel(category1Id) {
  const version = ++requestVersion
  loading.value = true
  failed.value = false
  groups.value = []
  topCategories.value = []
  try {
    const [channel, shortcuts] = await Promise.allSettled([
      categoryApi.getCategory1IdData(category1Id),
      categoryApi.getCategory1IdTopList(category1Id)
    ])
    if (version !== requestVersion) return
    if (shortcuts.status === 'fulfilled') {
      topCategories.value = (Array.isArray(shortcuts.value.data) ? shortcuts.value.data : []).slice(0, 7)
    }
    if (channel.status !== 'fulfilled') throw channel.reason
    groups.value = (Array.isArray(channel.value.data) ? channel.value.data : [])
      .map(group => ({ ...group, list: (group.list || []).filter(album => album?.id) }))
      .filter(group => group.list.length)
  } catch {
    if (version === requestVersion) failed.value = true
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function markBroken(id) {
  brokenCovers.value = new Set(brokenCovers.value).add(id)
}

function openSearch(category, album = {}) {
  router.push({ path: '/search', query: {
    category1Id: activeTab.value,
    ...(category?.category2Id || album.category2Id ? { category2Id: category?.category2Id || album.category2Id } : {}),
    ...(category?.id ? { category3Id: category.id } : {}),
    ...(category?.name ? { pageTitle: category.name } : {})
  } })
}

/** 「全部分类」：带着当前所在的一级分类进入分类页 */
function goAllCategories() {
  const current = categories.value.find((cat) => cat.id === activeTab.value)
  router.push({
    path: '/categories',
    query: {
      category1Id: activeTab.value,
      ...(current?.name ? { pageTitle: current.name } : {})
    }
  })
}

function formatCount(value) {
  const n = Number(value) || 0
  return n >= 10000 ? `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万` : n.toLocaleString()
}
</script>

<style scoped>
.index-page {
  min-height: 100vh;
  background: var(--ground);
  padding-bottom: var(--sp-5);
}

/* 移动端顶栏：桌面端隐藏（桌面端由 App.vue 提供顶栏） */
.home-header {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-3) var(--sp-3);
  background: var(--surface);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-shrink: 0;
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  display: grid;
  place-items: center;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 16px;
  color: var(--ink);
  letter-spacing: 0.02em;
}

.brand-text small {
  font-size: 10px;
  color: var(--ink-4);
}

.search-entry {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 32px;
  padding: 0 var(--sp-3);
  border: 0;
  border-radius: var(--r-pill);
  background: var(--ground);
  color: var(--ink-4);
  font-size: var(--fs-meta);
  text-align: left;
}

.search-entry:hover {
  background: var(--line-soft);
}

/* 频道 tabs */
.channel-tabs {
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.channel-tabs :deep(.van-tabs__wrap) {
  height: 44px;
}

.channel-tabs :deep(.van-tab) {
  font-size: 15px;
  color: var(--ink-3);
  padding: 0 var(--sp-4);
}

.channel-tabs :deep(.van-tab--active) {
  font-weight: 600;
}

.channel-tabs :deep(.van-tabs__line) {
  height: 2px;
  border-radius: 1px;
  background: var(--brand);
}

.home-body {
  padding-top: var(--sp-3);
}

/* 轮播 */
.carousel {
  border-radius: var(--r-lg);
  overflow: hidden;
  background: var(--surface);
}

.carousel :deep(.van-swipe) {
  aspect-ratio: 750 / 330;
}

.carousel :deep(.van-swipe-item img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 自定义指示器：slot 分支不生成 Vant 的定位容器，圆点容器自行悬浮底部 */
.carousel .swipe-dots {
  position: absolute;
  bottom: 10px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 1;
}

/* 默认半透明白点，激活实心放大；整块可点击 */
.carousel .swipe-dot {
  width: 7px;
  height: 7px;
  margin: 0 3px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease;
}

.carousel .swipe-dot:hover {
  background: rgba(255, 255, 255, 0.75);
}

.carousel .swipe-dot.active {
  background: #fff;
  transform: scale(1.3);
}

.carousel .swipe-dot:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

/* 分类快捷入口 */
.category-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.category-shortcuts button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--r-pill);
  background: var(--ground);
  color: var(--ink-2);
  font-size: var(--fs-body);
  transition: background 0.15s ease, color 0.15s ease;
}

.category-shortcuts button:hover {
  background: var(--brand-soft);
  color: var(--brand);
}

.category-shortcuts .all-categories {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 500;
}

.loading-wrap {
  padding: var(--sp-4) 0;
}

/* 专辑网格：手机 3 列，桌面 5 列 */
.album-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-4) var(--sp-3);
}

.album-card {
  display: block;
  min-width: 0;
}

.cover-wrap {
  position: relative;
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--line-soft);
  aspect-ratio: 1;
}

.cover-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s cubic-bezier(0.2, 0, 0.2, 1);
}

.album-card:hover .cover-wrap img {
  transform: scale(1.04);
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

.album-card h3 mark {
  background: transparent;
  color: var(--brand);
  font-weight: 600;
}

.pay-label {
  position: absolute;
  top: 6px;
  left: 6px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--r-sm);
  font-size: var(--fs-micro);
  font-weight: 500;
  line-height: 18px;
}

.play-badge {
  position: absolute;
  right: 6px;
  bottom: 6px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--r-sm);
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: var(--fs-micro);
  line-height: 18px;
  font-variant-numeric: tabular-nums;
}

.album-card h3 {
  margin: var(--sp-2) 0 3px;
  font-size: var(--fs-body);
  font-weight: 500;
  line-height: 1.4;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.album-card:hover h3 {
  color: var(--brand);
}

.album-card p {
  margin: 0;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.album-card .dot {
  margin: 0 3px;
}

.end-note {
  margin: var(--sp-6) 0 var(--sp-4);
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  letter-spacing: 0.12em;
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .home-header {
    display: none;
  }

  .channel-tabs {
    background: transparent;
    border-bottom: 0;
    margin-bottom: var(--sp-4);
  }

  .channel-tabs :deep(.van-tabs__wrap) {
    height: 40px;
  }

  .channel-tabs :deep(.van-tab) {
    font-size: 15px;
  }

  .home-body {
    padding-top: 0;
  }

  .carousel {
    margin-bottom: var(--sp-3);
  }

  .category-shortcuts {
    margin-top: 0;
    margin-bottom: var(--sp-2);
    gap: var(--sp-3);
    padding: var(--sp-4);
  }

  /* 桌面上整条横带是页面主导航之一：胶囊均分整行宽度，不留右侧空档 */
  .category-shortcuts button {
    flex: 1 1 0;
    justify-content: center;
    height: 40px;
    padding: 0 20px;
    font-size: 15px;
  }

  .album-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--sp-5) var(--sp-4);
  }

  .album-card h3 {
    font-size: var(--fs-body);
  }
}

@media (min-width: 1280px) {
  .album-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
