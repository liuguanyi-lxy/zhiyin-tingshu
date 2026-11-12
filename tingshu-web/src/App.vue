<template>
  <div class="app-shell">
    <!-- 桌面端：左侧固定导航 -->
    <aside class="desktop-sidebar">
      <RouterLink to="/index" class="side-brand">
        <span class="side-brand-mark">知</span>
        <span class="side-brand-text">
          <strong>知音听书</strong>
          <small>听见更大的世界</small>
        </span>
      </RouterLink>
      <nav class="side-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="side-link"
          :class="{ active: isActive(item) }"
        >
          <van-icon :name="item.icon" size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="side-foot">
        <RouterLink to="/vipChannel" class="side-vip">
          <van-icon name="diamond-o" size="16" />
          <span>开通会员</span>
        </RouterLink>
        <p class="side-note">知音听书 · Web 端</p>
      </div>
    </aside>

    <!-- 桌面端顶栏 -->
    <header class="desktop-header">
      <form class="header-search" role="search" @submit.prevent="goSearch">
        <van-icon name="search" size="16" />
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索专辑、主播"
          aria-label="搜索专辑、主播"
        />
      </form>
      <div class="header-right">
        <RouterLink v-if="!userStore.token" to="/login" class="header-signin">登录</RouterLink>
        <RouterLink v-else to="/my" class="header-user">
          <van-icon name="user-o" size="16" />
          <span>{{ userStore.user?.nickname || '我的' }}</span>
        </RouterLink>
      </div>
    </header>

    <!-- 页面内容 -->
    <main class="app-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 移动端底部 TabBar -->
    <van-tabbar v-if="$route.meta.tab" route fixed placeholder>
      <van-tabbar-item to="/index" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/categories" icon="apps-o">分类</van-tabbar-item>
      <van-tabbar-item to="/rank" icon="bar-chart-o">排行</van-tabbar-item>
      <van-tabbar-item to="/my" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from './stores/user.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const keyword = ref('')

const navItems = [
  { path: '/index', label: '首页', icon: 'home-o' },
  { path: '/categories', label: '分类', icon: 'apps-o' },
  { path: '/rank', label: '排行榜', icon: 'bar-chart-o' },
  { path: '/vipChannel', label: '会员频道', icon: 'diamond-o' },
  { path: '/my', label: '我的', icon: 'user-o' }
]

function isActive(item) {
  return route.path === item.path || route.path.startsWith(item.path + '/')
}

function goSearch() {
  const q = keyword.value.trim()
  if (!q) return
  router.push({ path: '/search', query: { keyword: q } })
}
</script>

<style scoped>
/* 桌面端侧边导航 */
.desktop-sidebar {
  display: none;
}

.desktop-header {
  display: none;
}

@media (min-width: 960px) {
  .desktop-sidebar {
    display: flex;
  }

  .desktop-header {
    display: flex;
  }

  .side-brand {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 4px 8px 20px;
  }

  .side-brand-mark {
    width: 30px;
    height: 30px;
    flex-shrink: 0;
    border-radius: 8px;
    background: var(--brand);
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    display: grid;
    place-items: center;
  }

  .side-brand-text {
    display: flex;
    flex-direction: column;
    line-height: 1.3;
    min-width: 0;
  }

  .side-brand-text strong {
    font-size: 15px;
    color: var(--ink);
    letter-spacing: 0.02em;
  }

  .side-brand-text small {
    font-size: 11px;
    color: var(--ink-4);
  }

  .side-nav {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .side-link {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 10px;
    border-radius: var(--r-md);
    color: var(--ink-2);
    font-size: 14px;
    transition: background 0.15s ease, color 0.15s ease;
  }

  .side-link:hover {
    background: var(--ground);
    color: var(--ink);
  }

  .side-link.active {
    background: var(--brand-soft);
    color: var(--brand);
    font-weight: 500;
  }

  .side-foot {
    margin-top: auto;
    padding-top: 20px;
  }

  .side-vip {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px;
    border-radius: var(--r-md);
    background: var(--vip-soft);
    color: var(--vip);
    font-size: 13px;
  }

  .side-vip:hover {
    color: var(--vip);
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  .side-note {
    margin: 12px 10px 0;
    font-size: 11px;
    color: var(--ink-4);
  }

  /* 顶栏搜索 */
  .header-search {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 320px;
    height: 36px;
    padding: 0 12px;
    border-radius: var(--r-pill);
    background: var(--ground);
    border: 1px solid transparent;
    transition: border-color 0.15s ease, background 0.15s ease;
  }

  .header-search:focus-within {
    background: var(--surface);
    border-color: var(--brand-line);
  }

  .header-search input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: none;
    font-size: 13px;
    color: var(--ink);
  }

  .header-search input::placeholder {
    color: var(--ink-4);
  }

  .header-right {
    margin-left: auto;
  }

  .header-signin {
    display: inline-flex;
    align-items: center;
    height: 32px;
    padding: 0 18px;
    border-radius: var(--r-pill);
    background: var(--brand);
    color: #fff;
    font-size: 13px;
    font-weight: 500;
  }

  .header-signin:hover {
    background: var(--brand-strong);
  }

  .header-user {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--ink-2);
  }
}

/* 路由切换淡入 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>
