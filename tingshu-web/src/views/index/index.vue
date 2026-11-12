<template>
  <div class="index-page">
    <header class="home-header">
      <div class="brand"><van-icon name="service" aria-hidden="true" /><strong>知音听书</strong></div>
      <button class="search-entry" type="button" @click="router.push('/search')">
        <van-icon name="search" aria-hidden="true" /><span>搜索专辑、主播和声音</span>
      </button>
    </header>
    <van-tabs v-model:active="activeTab" sticky color="#ff3547" title-active-color="#222" @change="loadChannel">
      <van-tab v-for="cat in categories" :key="cat.id" :title="cat.name" :name="cat.id" />
    </van-tabs>
    <section class="carousel" aria-label="精选推荐">
      <van-swipe :autoplay="reduceMotion ? 0 : 5000" indicator-color="#fff" :show-indicators="true">
        <van-swipe-item v-for="(banner, index) in banners" :key="banner">
          <img :src="banner" :alt="`知音听书精选推荐 ${index + 1}`" :loading="index ? 'lazy' : 'eager'" />
        </van-swipe-item>
      </van-swipe>
    </section>
    <nav v-if="topCategories.length" class="category-shortcuts" aria-label="频道分类">
      <button v-for="cat in topCategories" :key="cat.id" type="button" @click="openSearch(cat)">{{ cat.name }}</button>
      <button type="button" class="all-categories" @click="router.push({ path: '/categories', query: { category1Id: activeTab } })">全部分类 <van-icon name="arrow" /></button>
    </nav>
    <div v-if="loading" class="loading-wrap"><van-skeleton v-for="n in 3" :key="n" title avatar :row="3" /></div>
    <van-empty v-else-if="failed" image="network" description="频道加载失败，请检查网络后重试">
      <van-button size="small" round @click="retry">重新加载</van-button>
    </van-empty>
    <template v-else>
      <section v-for="(group, index) in groups" :key="group.baseCategory3?.id || index" class="recommend-group">
        <div class="section-heading">
          <h2>{{ group.baseCategory3?.name || '频道精选' }}</h2>
          <button type="button" @click="openSearch(group.baseCategory3, group.list?.[0])">查看全部 <van-icon name="arrow" /></button>
        </div>
        <div class="album-grid">
          <router-link v-for="album in group.list" :key="album.id" :to="`/detail/${album.id}`" class="album-card">
            <div class="cover-wrap">
              <img :src="album.coverUrl" :alt="album.albumTitle" loading="lazy" />
              <span v-if="payLabels[album.payType]" class="pay-label">{{ payLabels[album.payType] }}</span>
              <span class="play-badge"><van-icon name="play-circle-o" /> {{ formatCount(album.playStatNum) }}</span>
            </div>
            <h3>{{ album.albumTitle }}</h3>
            <p>{{ album.includeTrackCount ?? 0 }} 集<span v-if="album.announcerName"> · {{ album.announcerName }}</span></p>
          </router-link>
        </div>
      </section>
      <van-empty v-if="!groups.length" description="这个频道还没有专辑，换个分类看看" />
      <p v-else class="end-note">听见更大的世界</p>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi } from '../../api/category.js'
import bannerA from '../../assets/carousel/a.png'
import bannerB from '../../assets/carousel/b.png'
import bannerC from '../../assets/carousel/c.png'

const router = useRouter()
const banners = [bannerA, bannerB, bannerC]
const payLabels = { '0101': '免费', '0102': 'VIP免费', '0103': '付费' }
const categories = ref([])
const activeTab = ref(0)
const groups = ref([])
const topCategories = ref([])
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

function openSearch(category, album = {}) {
  router.push({ path: '/search', query: {
    category1Id: activeTab.value,
    ...(category?.category2Id || album.category2Id ? { category2Id: category?.category2Id || album.category2Id } : {}),
    ...(category?.id ? { category3Id: category.id } : {}),
    ...(category?.name ? { pageTitle: category.name } : {})
  } })
}

function formatCount(value) {
  const n = Number(value) || 0
  return n >= 10000 ? `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万` : n.toLocaleString()
}
</script>

<style scoped>
.index-page { min-height: 100vh; background: #fff; padding-bottom: 18px; }
.home-header { display: flex; align-items: center; gap: 16px; padding: 18px 16px 12px; }
.brand { display: flex; align-items: center; gap: 6px; white-space: nowrap; color: #222; font-size: 17px; }
.brand .van-icon { color: #ff3547; font-size: 24px; }
.search-entry { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; border: 0; border-radius: 20px; background: #f4f4f4; padding: 10px 12px; color: #888; font-size: 12px; cursor: pointer; }
.search-entry span { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.search-entry .van-icon { font-size: 17px; }
.carousel { margin: 12px 12px 8px; overflow: hidden; border-radius: 9px; }
.carousel img { display: block; width: 100%; aspect-ratio: 750 / 330; object-fit: cover; }
.category-shortcuts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); padding: 4px 10px 16px; gap: 8px; border-bottom: 8px solid #f7f8fa; }
.category-shortcuts button { padding: 9px 2px; border: 0; border-radius: 4px; font-size: 12px; color: #444; background: #f6f6f6; cursor: pointer; }
.category-shortcuts .all-categories { color: #f3384c; background: #fff3f4; }
.loading-wrap { padding: 28px 0; display: grid; gap: 28px; }
.recommend-group { padding: 19px 12px 4px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 0 14px; }
.section-heading h2 { margin: 0; color: #222; font-size: 17px; font-weight: 650; }
.section-heading button { background: none; border: 0; color: #909090; font-size: 12px; cursor: pointer; }
.album-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px 10px; }
.album-card { min-width: 0; color: inherit; text-decoration: none; }
.cover-wrap { position: relative; aspect-ratio: 1; overflow: hidden; border-radius: 6px; background: #f2f3f5; }
.cover-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pay-label { position: absolute; top: 0; left: 0; background: #ff3547; color: #fff; padding: 3px 6px; font-size: 10px; border-radius: 0 0 5px 0; }
.play-badge { position: absolute; bottom: 0; left: 0; right: 0; display: flex; gap: 4px; align-items: center; padding: 10px 6px 5px; color: #fff; background: linear-gradient(transparent, #000b); font-size: 11px; }
.album-card h3 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 14px; line-height: 1.5; font-weight: 500; margin: 8px 0 4px; }
.album-card p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: 0; font-size: 11px; color: #999; }
.end-note { margin: 32px 0 16px; text-align: center; color: #bbb; font-size: 11px; letter-spacing: 2px; }
button:focus-visible, a:focus-visible { outline: 2px solid #ff3547; outline-offset: 3px; }
@media (max-width: 350px) { .home-header { gap: 10px; padding-inline: 12px; } .brand { font-size: 15px; } .album-grid { gap: 16px 8px; } }
</style>
