<template>
  <section class="comments-panel" aria-label="评论区">
    <header class="comments-heading"><h2>听友评论 <span>{{ total }}</span></h2><button type="button" :disabled="loading" @click="loadComments">刷新</button></header>
    <van-loading v-if="loading" class="comments-loading">正在加载评论</van-loading>
    <van-empty v-else-if="failed" image="network" description="评论加载失败，稍后重试"><van-button size="small" round @click="loadComments">重新加载</van-button></van-empty>
    <template v-else>
      <article v-for="comment in comments" :key="comment.id" class="comment-thread">
        <div v-for="(entry, index) in threadEntries(comment)" :key="entry.id" class="comment-entry" :class="{ 'is-reply': index > 0 }">
          <van-image class="comment-avatar" round :src="entry.avatarUrl" width="32" height="32" fit="cover"><template #error><van-icon name="user-o" /></template></van-image>
          <div class="comment-body">
            <div class="comment-author">{{ entry.nickname || '听友' }}<span v-if="entry.parent?.nickname"> 回复 {{ entry.parent.nickname }}</span></div>
            <van-rate v-if="index === 0 && entry.albumCommentScore" :model-value="entry.albumCommentScore / 2" readonly allow-half :size="11" color="#b0773a" />
            <p>{{ entry.content }}</p>
            <div class="comment-meta"><time>{{ formatDate(entry.createTime) }}</time><div class="comment-actions">
              <button type="button" :disabled="pending.has(String(entry.id))" :aria-label="`${entry.isPraise ? '取消点赞' : '点赞'}${entry.nickname || '听友'}的评论`" :class="{ praised: entry.isPraise }" @click="togglePraise(entry)"><van-icon :name="entry.isPraise ? 'good-job' : 'good-job-o'" /> {{ entry.praiseCount || 0 }}</button>
              <button type="button" @click="replyTo = entry">回复</button>
              <button v-if="String(entry.deleteMark) === '1'" type="button" :disabled="pending.has(String(entry.id))" @click="removeComment(entry)">删除</button>
            </div></div>
          </div>
        </div>
      </article>
      <van-empty v-if="!comments.length" description="还没有评论，分享你的收听感受吧" image-size="72" />
      <van-pagination v-if="total > pageSize" v-model="page" :total-items="total" :items-per-page="pageSize" :show-page-size="3" force-ellipses @change="loadComments" />
    </template>
    <form class="comment-editor" @submit.prevent="sendComment">
      <div v-if="replyTo" class="reply-target"><span>回复 {{ replyTo.nickname || '听友' }}</span><button type="button" aria-label="取消回复" @click="replyTo = null"><van-icon name="cross" /></button></div>
      <div v-else class="comment-score"><label>为专辑评分</label><van-rate v-model="score" allow-half :size="19" color="#b0773a" /></div>
      <label class="sr-only" :for="editorId">评论内容</label>
      <textarea :id="editorId" v-model="draft" maxlength="500" rows="3" :placeholder="replyTo ? '写下你的回复' : '这一集，有什么让你印象深刻？'" :disabled="sending"></textarea>
      <div class="editor-footer"><span>{{ draft.length }}/500</span><van-button native-type="submit" round size="small" color="#007aff" :loading="sending" :disabled="!draft.trim() || sending">{{ userStore.token ? (replyTo ? '发布回复' : '发表评论') : '登录后发表' }}</van-button></div>
      <p v-if="sendError" class="comment-error" role="alert">{{ sendError }}</p>
    </form>
  </section>
</template>

<script setup>
import { ref, reactive, watch, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { commentApi } from '../api/comment.js'
import { useUserStore } from '../stores/user.js'

const props = defineProps({ albumId: { type: [Number, String], required: true }, trackId: { type: [Number, String], default: 0 } })
const emit = defineEmits(['updated'])
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const comments = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const failed = ref(false)
const draft = ref('')
const score = ref(5)
const replyTo = ref(null)
const sending = ref(false)
const sendError = ref('')
const pending = reactive(new Set())
const editorId = computed(() => `comment-editor-${props.albumId}-${props.trackId}`)
let version = 0
let contextVersion = 0
let active = true

function threadEntries(comment) { return [comment, ...(Array.isArray(comment.replyCommentList) ? comment.replyCommentList : [])] }
function requireLogin() {
  if (userStore.token) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}
async function loadComments() {
  const request = ++version
  if (!(Number(props.albumId) > 0)) return
  loading.value = true
  failed.value = false
  try {
    const { data } = await commentApi.getComments({ albumId: props.albumId, trackId: Number(props.trackId) || 0, page: page.value, limit: pageSize })
    if (!active || request !== version) return
    comments.value = Array.isArray(data?.records) ? data.records : []
    total.value = Number(data?.total) || 0
    emit('updated', total.value)
  } catch {
    if (active && request === version) failed.value = true
  } finally {
    if (active && request === version) loading.value = false
  }
}
async function sendComment() {
  if (sending.value || !draft.value.trim() || !requireLogin()) return
  const context = contextVersion
  sending.value = true
  sendError.value = ''
  try {
    await commentApi.save({ albumId: Number(props.albumId), trackId: Number(props.trackId) > 0 ? Number(props.trackId) : null, content: draft.value.trim(), replyCommentId: replyTo.value?.id || '0', albumCommentScore: Math.max(1, Math.round(score.value * 2)) })
    if (!active || context !== contextVersion) return
    draft.value = ''
    replyTo.value = null
    showToast('评论已发表')
    page.value = 1
    await loadComments()
  } catch (error) {
    if (active && context === contextVersion) sendError.value = error?.message || '发表失败，内容已保留，请稍后重试'
  } finally {
    if (active && context === contextVersion) sending.value = false
  }
}
async function togglePraise(entry) {
  const key = String(entry.id)
  if (pending.has(key) || !requireLogin()) return
  const context = contextVersion
  pending.add(key)
  try {
    await commentApi.praise(props.albumId, entry.id)
    if (!active || context !== contextVersion) return
    entry.praiseCount = Math.max(0, Number(entry.praiseCount || 0) + (entry.isPraise ? -1 : 1))
    entry.isPraise = !entry.isPraise
  } catch {} finally { pending.delete(key) }
}
async function removeComment(entry) {
  const key = String(entry.id)
  if (pending.has(key) || !requireLogin()) return
  const context = contextVersion
  pending.add(key)
  try {
    await showConfirmDialog({ title: '删除评论', message: '删除后无法恢复，确定删除这条评论吗？' })
    if (!active || context !== contextVersion) return
    await commentApi.remove(props.albumId, entry.id)
    if (!active || context !== contextVersion) return
    if (replyTo.value?.id === entry.id) replyTo.value = null
    showToast('评论已删除')
    await loadComments()
  } catch {} finally { pending.delete(key) }
}
function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
watch(() => [props.albumId, props.trackId], () => {
  contextVersion++
  page.value = 1
  comments.value = []
  total.value = 0
  draft.value = ''
  replyTo.value = null
  sending.value = false
  sendError.value = ''
  pending.clear()
  loadComments()
}, { immediate: true })
onBeforeUnmount(() => { active = false; version++; contextVersion++ })
</script>

<style scoped>
.comments-panel { background: var(--surface); padding: var(--sp-4); }
.comments-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); }
.comments-heading h2 { margin: 0; color: var(--ink); font-size: var(--fs-section); font-weight: 600; }
.comments-heading h2 span { font-size: var(--fs-meta); color: var(--ink-4); font-weight: 400; margin-left: 6px; }
.comments-heading button { border: 0; background: none; font-size: var(--fs-meta); color: var(--ink-3); padding: 4px 0; }
.comments-heading button:hover { color: var(--brand); }
.comments-loading { text-align: center; padding: var(--sp-6) 0; }
.comment-thread { padding: var(--sp-4) 0 var(--sp-1); border-bottom: 1px solid var(--line-soft); }
.comment-entry { display: flex; align-items: flex-start; gap: var(--sp-3); margin-bottom: var(--sp-4); }
.comment-avatar { flex-shrink: 0; }
.comment-body { flex: 1; min-width: 0; }
.comment-author { color: var(--ink-2); font-size: var(--fs-meta); line-height: 1.6; }
.comment-author span { color: var(--ink-4); }
.comment-body p { margin: 5px 0 var(--sp-2); color: var(--ink); font-size: var(--fs-body); line-height: 1.7; overflow-wrap: anywhere; white-space: pre-wrap; }
.comment-meta { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--sp-2); font-size: var(--fs-micro); color: var(--ink-4); }
.comment-actions { display: flex; gap: var(--sp-3); }
.comment-actions button { border: 0; padding: 2px 0; background: transparent; color: var(--ink-3); font-size: var(--fs-micro); }
.comment-actions button:hover { color: var(--brand); }
.comment-actions .praised { color: var(--paid); }
.is-reply { margin-left: 36px; padding: var(--sp-3); background: var(--ground); border-radius: var(--r-md); }
.is-reply .comment-avatar { width: 24px !important; height: 24px !important; }
.comment-editor { margin-top: var(--sp-5); }
.comment-score, .reply-target { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); padding-bottom: var(--sp-3); color: var(--ink-3); font-size: var(--fs-meta); }
.reply-target button { border: 0; background: none; color: var(--ink-4); }
.comment-editor textarea { box-sizing: border-box; display: block; width: 100%; resize: vertical; min-height: 84px; max-height: 200px; border: 1px solid var(--line); border-radius: var(--r-md); padding: var(--sp-3); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--fs-body); line-height: 1.6; }
.comment-editor textarea:focus { border-color: var(--brand-line); outline: none; }
.editor-footer { display: flex; align-items: center; justify-content: space-between; margin-top: var(--sp-3); color: var(--ink-4); font-size: var(--fs-micro); }
.comment-error { color: var(--paid); font-size: var(--fs-meta); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
button { cursor: pointer; }
button:disabled { cursor: default; opacity: .55; }
button:focus-visible, textarea:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
</style>
