<template>
  <div class="categories-page">
    <van-nav-bar title="分类" fixed placeholder />

    <!-- 页面级加载 / 分类列表兜底 -->
    <div v-if="loading" class="loading-wrap">
      <van-loading size="24">加载中...</van-loading>
    </div>
    <van-empty v-else-if="categories.length === 0" description="分类加载失败，请稍后重试" />

    <div v-else class="category-body">
      <!-- 左侧一级分类竖排列表 -->
      <van-sidebar v-model="activeIndex" class="category-sidebar" @change="onCategoryChange">
        <van-sidebar-item v-for="cat in categories" :key="cat.id" :title="cat.name" />
      </van-sidebar>

      <!-- 右侧频道专辑列表 -->
      <div ref="contentRef" class="category-content">
        <div v-if="contentLoading" class="loading-wrap">
          <van-loading size="24">加载中...</van-loading>
        </div>
        <div v-else class="album-list">
          <div
            v-for="item in albums"
            :key="item.id"
            class="album-item"
            @click="router.push(`/detail/${item.id}`)"
          >
            <img class="cover" :src="item.coverUrl" alt="" />
            <div class="album-info">
              <div class="album-title">{{ item.albumTitle }}</div>
              <div class="album-meta">
                <span>🎧 {{ item.includeTrackCount ?? 0 }} 集</span>
                <span v-if="item.announcerName">主播：{{ item.announcerName }}</span>
              </div>
            </div>
          </div>
          <van-empty v-if="albums.length === 0" description="该分类下暂无专辑" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi } from '../../api/category.js'

const router = useRouter()

const categories = ref([])
const activeIndex = ref(0)
const albums = ref([])
const loading = ref(true)
const contentLoading = ref(false)
const contentRef = ref(null)

onMounted(async () => {
  try {
    const res = await categoryApi.findAllCategory1()
    categories.value = res.data || []
    if (categories.value.length > 0) {
      await loadChannel(categories.value[0].id)
    }
  } catch {
    // 请求层已提示失败，这里兜底展示空态
    categories.value = []
  } finally {
    loading.value = false
  }
})

async function onCategoryChange(index) {
  const cat = categories.value[index]
  if (!cat) return
  // 切换分类后右侧滚动回到顶部
  if (contentRef.value) contentRef.value.scrollTop = 0
  await loadChannel(cat.id)
}

/**
 * channel 接口返回分组结构：[{ baseCategory3: {...}, list: [专辑...] }]
 * 展平时把每个分组的 list 合并为专辑列表
 */
async function loadChannel(category1Id) {
  contentLoading.value = true
  try {
    const res = await categoryApi.getCategory1IdData(category1Id)
    const groups = res.data || []
    albums.value = groups.flatMap((g) => g.list || [])
  } catch {
    // 请求层已提示失败，这里兜底展示空态
    albums.value = []
  } finally {
    contentLoading.value = false
  }
}
</script>

<style scoped>
.categories-page {
  background: #f7f8fa;
}
.loading-wrap {
  padding: 40px 0;
  text-align: center;
}
.category-body {
  display: flex;
  /* 顶部导航 46px + 底部 tabbar 50px */
  height: calc(100vh - 96px);
  overflow: hidden;
}
.category-sidebar {
  flex-shrink: 0;
  overflow-y: auto;
}
.category-content {
  flex: 1;
  overflow-y: auto;
}
.album-item {
  display: flex;
  margin: 10px 12px;
  padding: 10px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
}
.cover {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f0f0f0;
}
.album-info {
  flex: 1;
  margin-left: 10px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.album-title {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.album-meta {
  font-size: 12px;
  color: #969799;
  display: flex;
  gap: 12px;
}
</style>
