<template>
  <div class="categories-page">
    <van-nav-bar :title="activeCategory1Name" fixed placeholder />

    <div v-if="loading" class="loading-wrap">
      <van-loading size="24">正在加载分类</van-loading>
    </div>
    <van-empty v-else-if="!categories.length" description="分类加载失败，请稍后重试" />

    <template v-else>
      <!-- 一级分类：横向切换 -->
      <nav class="level1-bar" aria-label="一级分类">
        <button
          v-for="cat in categories"
          :key="cat.id"
          type="button"
          class="level1-chip"
          :class="{ active: cat.id === activeCategory1Id }"
          @click="selectCategory1(cat)"
        >
          {{ cat.name }}
        </button>
      </nav>

      <div class="page">
        <!-- 二级分类 + 三级分类 -->
        <section class="section">
          <div class="section-head">
            <h2 class="section-title">{{ activeCategory1Name }}的分类</h2>
            <span class="section-count num">{{ subCategories.length }} 个二级分类</span>
          </div>
          <div v-if="treeLoading" class="loading-wrap">
            <van-loading size="20">加载分类目录</van-loading>
          </div>
          <div v-else-if="subCategories.length" class="sub-grid">
            <article v-for="sub in subCategories" :key="sub.categoryId" class="sub-card">
              <h3 class="sub-name">{{ sub.categoryName }}</h3>
              <div v-if="sub.categoryChild?.length" class="leaf-list">
                <button
                  v-for="leaf in sub.categoryChild"
                  :key="leaf.categoryId"
                  type="button"
                  class="leaf-chip"
                  @click="openLeaf(sub, leaf)"
                >
                  {{ leaf.categoryName }}
                </button>
              </div>
              <p v-else class="sub-empty">该分类下暂无细分内容</p>
            </article>
          </div>
          <van-empty v-else description="该分类下暂无二级分类" />
        </section>

        <!-- 频道专辑 -->
        <template v-if="!contentLoading && groups.length">
          <section v-for="group in groups" :key="group.baseCategory3?.id" class="section">
            <div class="section-head">
              <h2 class="section-title">{{ group.baseCategory3?.name || '专辑推荐' }}</h2>
              <button type="button" class="section-action" @click="openGroup(group)">
                查看全部 <van-icon name="arrow" />
              </button>
            </div>
            <div class="album-grid">
              <router-link
                v-for="album in group.list"
                :key="album.id"
                :to="`/detail/${album.id}`"
                class="album-card"
              >
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
                </div>
                <h3 class="album-title">
                  <template v-for="(part, index) in titleParts(album.albumTitle)" :key="index">
                    <mark v-if="part.highlight">{{ part.text }}</mark>
                    <template v-else>{{ part.text }}</template>
                  </template>
                </h3>
                <p class="album-meta">
                  <span class="num">{{ album.includeTrackCount ?? 0 }}</span> 集
                  <span v-if="album.announcerName" class="dot">·</span>
                  <span v-if="album.announcerName">{{ album.announcerName }}</span>
                </p>
              </router-link>
            </div>
          </section>
        </template>
        <div v-else-if="contentLoading" class="loading-wrap">
          <van-loading size="24">正在加载专辑</van-loading>
        </div>
        <van-empty v-else description="该分类下暂无专辑" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { categoryApi } from '../../api/category.js'
import { titleParts, plainTitle } from '../../utils/format.js'

const route = useRoute()
const router = useRouter()

const payLabels = { '0101': '免费', '0102': 'VIP免费', '0103': '付费' }
const payClass = { '0101': 'tag-free', '0102': 'tag-vip', '0103': 'tag-paid' }

const categories = ref([])
const activeCategory1Id = ref(0)
const subCategories = ref([])
const groups = ref([])
const brokenCovers = ref(new Set())
const loading = ref(true)
const treeLoading = ref(false)
const contentLoading = ref(false)

const activeCategory1Name = computed(
  () => categories.value.find((item) => item.id === activeCategory1Id.value)?.name || '分类'
)

onMounted(async () => {
  try {
    const res = await categoryApi.findAllCategory1()
    categories.value = Array.isArray(res.data) ? res.data : []
    const queryId = Number(route.query.category1Id)
    const matched = categories.value.find((item) => item.id === queryId)
    activeCategory1Id.value = matched ? matched.id : categories.value[0]?.id || 0
    if (activeCategory1Id.value) {
      await Promise.all([loadTree(activeCategory1Id.value), loadChannel(activeCategory1Id.value)])
    }
  } catch {
    categories.value = []
  } finally {
    loading.value = false
  }
})

// 从首页「全部分类」带 category1Id 进来时，切换到对应一级分类
watch(
  () => route.query.category1Id,
  async (value) => {
    const id = Number(value)
    if (!id || id === activeCategory1Id.value) return
    if (!categories.value.some((item) => item.id === id)) return
    activeCategory1Id.value = id
    await Promise.all([loadTree(id), loadChannel(id)])
  }
)

async function selectCategory1(cat) {
  if (!cat || cat.id === activeCategory1Id.value) return
  activeCategory1Id.value = cat.id
  router.replace({ path: '/categories', query: { category1Id: cat.id } })
  // watcher 会因 replace 再次触发相同 id 而跳过，这里直接加载一次即可
  await Promise.all([loadTree(cat.id), loadChannel(cat.id)])
}

/** 二级分类与三级分类目录 */
async function loadTree(category1Id) {
  treeLoading.value = true
  subCategories.value = []
  try {
    const res = await categoryApi.getCategory1IdAllInfo(category1Id)
    subCategories.value = Array.isArray(res.data?.categoryChild) ? res.data.categoryChild : []
  } catch {
    subCategories.value = []
  } finally {
    treeLoading.value = false
  }
}

/** 频道推荐专辑，按三级分类分组 */
async function loadChannel(category1Id) {
  contentLoading.value = true
  groups.value = []
  try {
    const res = await categoryApi.getCategory1IdData(category1Id)
    groups.value = (Array.isArray(res.data) ? res.data : [])
      .map((group) => ({ ...group, list: (group.list || []).filter((album) => album?.id) }))
      .filter((group) => group.list.length)
  } catch {
    groups.value = []
  } finally {
    contentLoading.value = false
  }
}

/** 三级分类 → 搜索结果页 */
function openLeaf(sub, leaf) {
  router.push({
    path: '/search',
    query: {
      category1Id: activeCategory1Id.value,
      category2Id: sub.categoryId,
      category3Id: leaf.categoryId,
      pageTitle: leaf.categoryName
    }
  })
}

/** 整个三级分类分组 → 搜索结果页 */
function openGroup(group) {
  const category = group.baseCategory3
  if (!category?.id) return
  router.push({
    path: '/search',
    query: {
      category1Id: activeCategory1Id.value,
      category3Id: category.id,
      pageTitle: category.name
    }
  })
}

function markBroken(id) {
  brokenCovers.value = new Set(brokenCovers.value).add(id)
}
</script>

<style scoped>
.categories-page {
  min-height: 100vh;
  background: var(--ground);
}

.loading-wrap {
  padding: 40px 0;
  text-align: center;
}

/* 一级分类：横向切换条 */
.level1-bar {
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-3);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  overflow-x: auto;
  scrollbar-width: none;
  position: sticky;
  top: 0;
  z-index: 2;
}

.level1-bar::-webkit-scrollbar {
  display: none;
}

.level1-chip {
  flex-shrink: 0;
  height: 30px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: var(--fs-body);
  line-height: 28px;
  white-space: nowrap;
  transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
}

.level1-chip:hover {
  border-color: var(--brand-line);
  color: var(--brand);
}

.level1-chip.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-weight: 500;
}

.section-count {
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

/* 二级分类卡片 */
.sub-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--sp-3);
}

.sub-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-3);
}

.sub-name {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.sub-name::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--brand);
}

.leaf-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.leaf-chip {
  height: 28px;
  padding: 0 var(--sp-3);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--ground);
  color: var(--ink-2);
  font-size: var(--fs-meta);
  line-height: 26px;
  transition: color 0.15s ease, border-color 0.15s ease, background-color 0.15s ease;
}

.leaf-chip:hover {
  color: var(--brand);
  border-color: var(--brand-line);
  background: var(--brand-soft);
}

.sub-empty {
  margin: 0;
  font-size: var(--fs-meta);
  color: var(--ink-4);
}

/* 专辑网格 */
.album-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3);
}

.album-card {
  display: block;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.cover-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
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

.pay-label {
  position: absolute;
  left: 6px;
  top: 6px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--r-sm);
  font-size: var(--fs-micro);
  line-height: 18px;
  font-weight: 500;
}

.album-title {
  margin: var(--sp-2) 0 0;
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

.album-title mark {
  background: transparent;
  color: var(--brand);
  font-weight: 600;
}

.album-card:hover .album-title {
  color: var(--brand);
}

.album-meta {
  margin: 4px 0 0;
  font-size: var(--fs-meta);
  color: var(--ink-4);
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dot {
  color: var(--line);
}

/* ============================================================
   桌面端
   ============================================================ */
@media (min-width: 960px) {
  .categories-page :deep(.van-nav-bar) {
    max-width: none;
    left: var(--sidebar-w);
    right: 0;
    background: transparent;
  }

  .categories-page :deep(.van-nav-bar__title) {
    font-size: var(--fs-title);
    font-weight: 600;
    color: var(--ink);
  }

  .level1-bar {
    top: 60px;
    padding: var(--sp-3) var(--sp-5);
    justify-content: flex-start;
  }

  .sub-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--sp-4);
  }

  .album-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--sp-4);
  }
}

@media (min-width: 1280px) {
  .sub-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .album-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
