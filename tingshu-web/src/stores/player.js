import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 与小程序 stores/player.js 对齐的播放器状态（网页版用 HTML5 Audio）
 */
export const usePlayerStore = defineStore('player', () => {
  const currentTrack = ref(null)
  const playing = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)

  function setTrack(track) {
    currentTrack.value = track
  }

  return { currentTrack, playing, currentTime, duration, setTrack }
})
