<template>
  <router-view />
  <!-- 底部 TabBar：仅在四个主页面显示（与小程序自定义 tabbar 对齐） -->
  <van-tabbar v-if="$route.meta.tab" route fixed placeholder>
    <van-tabbar-item to="/index" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/categories" icon="apps-o">分类</van-tabbar-item>
    <van-tabbar-item to="/rank" icon="bar-chart-o">排行</van-tabbar-item>
    <van-tabbar-item to="/my" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup>
</script>
