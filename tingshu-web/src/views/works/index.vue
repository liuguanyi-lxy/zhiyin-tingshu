<template>
  <!-- 原 works 为专辑/声音双栏列表，与我的作品共用真实分页和操作，避免两份状态漂移。 -->
  <div class="works-page">
    <MyWork simple />
  </div>
</template>

<script setup>
import MyWork from '../myWork/index.vue'
</script>

<style scoped>
.works-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--ground);
}

/* 作品集视图：桌面端把作品卡片铺成宽屏网格，信息层级更清楚 */
@media (min-width: 960px) {
  .works-page :deep(.page) {
    padding: 0 var(--sp-5) var(--sp-6);
  }

  .works-page :deep(.work-list) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--sp-4);
    align-items: start;
  }

  .works-page :deep(.work-card) {
    padding: var(--sp-4);
  }

  .works-page :deep(.card-title) {
    font-size: var(--fs-section);
  }

  .works-page :deep(.metrics) {
    font-variant-numeric: tabular-nums;
  }
}

@media (min-width: 1440px) {
  .works-page :deep(.work-list) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .works-page :deep(.cover) {
    width: 64px;
    height: 64px;
  }
}
</style>
