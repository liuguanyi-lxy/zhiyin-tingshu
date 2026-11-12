<template>
  <main class="track-form-page">
    <van-nav-bar :title="editId ? '修改声音' : '创建声音'" left-arrow @click-left="cancelForm" />
    <van-skeleton v-if="initialLoading" title :row="8" class="page-loading" />
    <van-empty v-else-if="pageError" image="error" :description="pageError"><van-button size="small" color="#008aff" @click="initialize">重新加载</van-button></van-empty>
    <van-form v-else :disabled="saving" :scroll-to-error="true" @submit="submit" @failed="onValidationFailed">
      <section class="form-panel">
        <p class="section-caption">声音信息 <span>带 * 为必填项</span></p>
        <van-field :model-value="form.mediaFileId" name="mediaFileId" label="上传声音" required :rules="[{ validator: () => validMediaId(form.mediaFileId) && !!form.mediaUrl, message: '请通过声音上传接口取得有效 VOD 媒体文件' }]">
          <template #input>
            <div class="audio-upload">
              <label class="audio-picker" :class="{ disabled: audioUploading || saving }"><van-icon name="upgrade" size="26" /><span>{{ form.mediaFileId ? '替换声音文件' : '选择声音文件' }}</span><small>MP3 / M4A / AAC / WAV / FLAC / OGG / Opus / WebM</small><input type="file" accept="audio/*,.mp3,.m4a,.aac,.wav,.flac,.ogg,.opus,.webm" :disabled="audioUploading || saving" aria-label="上传声音文件" @change="uploadAudio" /></label>
              <div v-if="audioUploading" class="upload-progress"><van-loading size="16" /><span>{{ audioProgress === null ? '读取本地音频信息…' : audioProgress < 100 ? `正在传输 ${audioProgress}%` : '腾讯 VOD 正在处理，请稍候…' }}</span><button type="button" @click="cancelAudioUpload">取消上传</button></div>
              <p v-if="pendingAudioName && audioUploading" class="media-note">{{ pendingAudioName }}</p>
              <div v-if="form.mediaUrl" class="audio-preview"><audio controls preload="metadata" :src="form.mediaUrl" @error="previewError = '该声音暂不能在浏览器预览。保存仍以真实 VOD 媒体文件为准。'" /><p v-if="audioInfo.name" class="file-name">{{ audioInfo.name }}</p><div class="media-meta"><span>时长 {{ formatDuration(audioInfo.duration) }}</span><span>大小 {{ formatSize(audioInfo.size) }}</span><span v-if="audioInfo.type">{{ audioInfo.type }}</span></div><p class="media-id">VOD 文件 ID：{{ form.mediaFileId || '缺失，请重新上传' }}</p></div>
              <p class="media-note">声音由腾讯 VOD 存储。时长、大小和类型最终由后端向 VOD 查询，不能用图片链接代替媒体 ID。</p>
              <p v-if="metadataWarning" class="media-note">{{ metadataWarning }}</p>
              <p v-if="previewError" class="field-error" role="alert">{{ previewError }}</p>
              <p v-if="audioError" class="field-error" role="alert">{{ audioError }}<span v-if="form.mediaFileId"> 原声音已保留，保存不会使用本次失败的上传。</span></p>
            </div>
          </template>
        </van-field>
        <van-field v-model="form.trackTitle" name="trackTitle" label="声音标题" placeholder="请输入声音标题" maxlength="200" required :rules="[{ validator: value => !!value.trim(), message: '请填写声音标题' }]" />
        <van-field name="coverUrl" label="声音封面"><template #input><div class="cover-area"><label class="cover-picker" :class="{ disabled: coverUploading || saving }"><van-image v-if="form.coverUrl || selectedAlbum?.coverUrl" :src="form.coverUrl || selectedAlbum.coverUrl" width="80" height="80" radius="5" fit="cover" /><span v-else><van-icon name="plus" size="24" /><small>上传封面</small></span><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" :disabled="coverUploading || saving" aria-label="上传声音封面" @change="uploadCover" /></label><p class="media-note">可选；未设置时列表使用专辑封面。图片最大 10 MB。</p><button v-if="form.coverUrl && !coverUploading" type="button" class="text-button" :disabled="saving" @click="form.coverUrl = ''">使用专辑封面</button><div v-if="coverUploading" class="upload-progress"><van-loading size="16" /> {{ coverProgress < 100 ? `封面传输 ${coverProgress}%` : '封面存储处理中…' }}<button type="button" @click="cancelCoverUpload">取消上传</button></div><p v-if="coverError" class="field-error" role="alert">{{ coverError }}</p></div></template></van-field>
        <van-field v-model="form.albumId" name="albumId" label="所属专辑" required :rules="[{ validator: () => !!selectedAlbum, message: '请选择本人专辑' }]"><template #input><select v-model="form.albumId" class="album-select" aria-label="选择所属专辑" :disabled="!!editId || saving || albumsLoading"><option disabled value="">请选择专辑</option><option v-for="item in albumList" :key="item.id" :value="String(item.id)">{{ item.albumTitle }}</option></select></template></van-field>
        <p v-if="editId" class="indented-note">编辑时不可更换所属专辑，避免集数及排序不一致。</p>
        <div v-if="albumsLoading" class="dependency"><van-loading size="16" /> 正在加载本人专辑</div>
        <div v-if="albumsError" class="dependency error" role="alert">{{ albumsError }} <button type="button" @click="reloadAlbums">重试专辑列表</button></div>
        <div v-else-if="!albumList.length" class="dependency">还没有专辑，请先创建。<router-link to="/createAlbum">创建专辑</router-link></div>
        <p v-if="albumHint" class="dependency">{{ albumHint }}</p>
        <van-field v-model="form.trackIntro" name="trackIntro" label="声音简介" type="textarea" rows="4" autosize placeholder="介绍这一集的内容（选填）" />
        <van-field name="isOpen" label="设为私密"><template #input><van-switch :model-value="form.isOpen === '0'" active-color="#008aff" size="24" :disabled="saving" @update:model-value="form.isOpen = $event ? '0' : '1'" /></template></van-field>
        <p class="indented-note">{{ form.isOpen === '0' ? '私密声音仅在作品管理中显示。' : '公开且通过审核的声音会展示在专辑节目中。' }}</p>
      </section>
      <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>
      <div class="submit-actions"><van-button native-type="button" :disabled="saving" @click="cancelForm">取消</van-button><van-button native-type="submit" color="#008aff" :loading="saving" :disabled="audioUploading || coverUploading || albumsLoading || !!albumsError || !albumList.length">{{ editId ? '保存修改' : '提交声音' }}</van-button></div>
    </van-form>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { albumsApi } from '../../api/albums.js'

const route = useRoute()
const router = useRouter()
const editId = computed(() => String(route.params.id || ''))
const defaults = () => ({ mediaFileId: '', mediaUrl: '', trackTitle: '', coverUrl: '', albumId: '', trackIntro: '', isOpen: '1' })
const form = reactive(defaults())
const albumList = ref([])
const albumsLoading = ref(false)
const albumsError = ref('')
const albumHint = ref('')
const selectedAlbum = computed(() => albumList.value.find(item => String(item.id) === form.albumId))
const initialLoading = ref(true)
const pageError = ref('')
const saving = ref(false)
const submitError = ref('')
const audioUploading = ref(false)
const audioProgress = ref(null)
const pendingAudioName = ref('')
const audioError = ref('')
const previewError = ref('')
const metadataWarning = ref('')
const audioInfo = reactive({ name: '', duration: null, size: null, type: '' })
const coverUploading = ref(false)
const coverProgress = ref(0)
const coverError = ref('')
const originalSnapshot = ref('')
let originalInfo = {}
let generation = 0
let alive = true
let leaveApproved = false
let audioController
let coverController
const metadataCleanups = new Set()
const snapshot = () => JSON.stringify(form)
const dirty = computed(() => !!originalSnapshot.value && snapshot() !== originalSnapshot.value)
const message = e => e?.response?.status === 413 ? '文件超过上传服务限制，请压缩文件或联系管理员调整限制' : e?.response?.data?.message || e?.message || '请求失败，请检查网络或上传服务后重试'
const validMediaId = value => typeof value === 'string' && !!value.trim() && !/[:/\\]/.test(value)
function formatDuration(value) {
  if (value == null || !Number.isFinite(Number(value))) return '待读取'
  const seconds = Math.floor(Number(value))
  const hours = Math.floor(seconds / 3600)
  return `${hours ? hours + ':' : ''}${hours ? String(Math.floor(seconds / 60) % 60).padStart(2, '0') : Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
function formatSize(value) {
  if (value == null || !Number.isFinite(Number(value))) return '未返回'
  return Number(value) >= 1024 * 1024 ? `${(Number(value) / 1024 / 1024).toFixed(2)} MB` : `${(Number(value) / 1024).toFixed(1)} KB`
}
async function reloadAlbums() {
  albumsLoading.value = true
  albumsError.value = ''
  try {
    const { data } = await albumsApi.getAllAlbumList()
    if (!alive) return false
    if (!Array.isArray(data)) throw new Error('本人专辑列表响应不完整')
    albumList.value = data
    return true
  } catch (e) { if (alive) albumsError.value = message(e); return false }
  finally { if (alive) albumsLoading.value = false }
}
async function initialize() {
  const version = ++generation
  initialLoading.value = true
  originalSnapshot.value = ''
  pageError.value = ''
  submitError.value = ''
  albumHint.value = ''
  leaveApproved = false
  originalInfo = {}
  Object.assign(form, defaults())
  Object.assign(audioInfo, { name: '', duration: null, size: null, type: '' })
  try {
    const detail = editId.value ? await albumsApi.getTrackInfo(editId.value) : null
    if (!alive || version !== generation) return
    if (editId.value) {
      if (!detail?.data?.id) throw new Error('声音不存在或已删除')
      originalInfo = detail.data
      for (const key of Object.keys(defaults())) if (detail.data[key] != null) form[key] = String(detail.data[key])
      Object.assign(audioInfo, { duration: detail.data.mediaDuration, size: detail.data.mediaSize, type: detail.data.mediaType || '' })
      if (!validMediaId(form.mediaFileId)) audioError.value = '原声音缺少有效 VOD 文件 ID，必须重新上传后才能保存。'
    }
    const loaded = await reloadAlbums()
    if (!alive || version !== generation) return
    if (editId.value && loaded && !selectedAlbum.value) throw new Error('该声音不属于本人专辑，不能编辑')
    if (!editId.value && route.query.albumId) {
      const id = String(route.query.albumId)
      if (albumList.value.some(item => String(item.id) === id)) form.albumId = id
      else albumHint.value = '传入的专辑不在本人专辑列表中，请重新选择。'
    }
    originalSnapshot.value = snapshot()
  } catch (e) { if (alive && version === generation) pageError.value = message(e) }
  finally { if (alive && version === generation) initialLoading.value = false }
}
function readAudioMetadata(file, signal) {
  return new Promise(resolve => {
    const audio = new Audio()
    const url = URL.createObjectURL(file)
    let settled = false
    let timer
    const finish = duration => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      audio.onloadedmetadata = null
      audio.onerror = null
      signal.removeEventListener('abort', cancel)
      audio.removeAttribute('src')
      audio.load()
      URL.revokeObjectURL(url)
      metadataCleanups.delete(cancel)
      resolve(duration)
    }
    const cancel = () => finish(null)
    metadataCleanups.add(cancel)
    signal.addEventListener('abort', cancel, { once: true })
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => finish(Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : null)
    audio.onerror = () => finish(null)
    timer = setTimeout(cancel, 12000)
    audio.src = url
    if (signal.aborted) cancel()
  })
}
async function uploadAudio(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  audioError.value = ''
  if (!file.size || !/\.(mp3|m4a|aac|wav|flac|ogg|opus|webm)$/i.test(file.name)) { audioError.value = '请选择支持的音频文件，不能上传空文件'; return }
  if (editId.value && form.mediaFileId) {
    try { await showConfirmDialog({ title: '替换原声音？', message: '后端在保存修改时会删除旧 VOD 媒体文件。上传失败不会替换原声音，是否继续选择？', confirmButtonColor: '#008aff' }) }
    catch { return }
  }
  audioUploading.value = true
  audioProgress.value = null
  pendingAudioName.value = file.name
  audioController = new AbortController()
  try {
    const duration = await readAudioMetadata(file, audioController.signal)
    if (audioController.signal.aborted) throw Object.assign(new Error('上传已取消'), { code: 'ERR_CANCELED' })
    audioProgress.value = 0
    const { data } = await albumsApi.uploadTrack(file, { signal: audioController.signal, onProgress: value => { audioProgress.value = value } })
    if (!alive) return
    form.mediaFileId = data.mediaFileId
    form.mediaUrl = data.mediaUrl
    Object.assign(audioInfo, { name: file.name, duration, size: file.size, type: file.type || file.name.split('.').pop().toLowerCase() })
    metadataWarning.value = duration == null ? '浏览器无法读取此格式的本地时长，保存时由后端向 VOD 查询。' : ''
    previewError.value = ''
    if (!form.trackTitle.trim()) form.trackTitle = file.name.replace(/\.[^.]+$/, '')
    showSuccessToast('声音上传成功')
  } catch (e) { if (alive) audioError.value = e?.code === 'ERR_CANCELED' ? '声音上传已取消' : message(e) }
  finally { if (alive) audioUploading.value = false }
}
async function uploadCover(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  coverError.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type) || file.size > 10 * 1024 * 1024 || !file.size) { coverError.value = '请选择 10 MB 以内的 JPG、PNG、WebP 或 GIF 图片'; return }
  coverUploading.value = true
  coverProgress.value = 0
  coverController = new AbortController()
  try {
    const { data } = await albumsApi.uploadCover(file, { signal: coverController.signal, onProgress: value => { coverProgress.value = value } })
    if (alive) { form.coverUrl = data; showSuccessToast('封面上传成功') }
  } catch (e) { if (alive) coverError.value = e?.code === 'ERR_CANCELED' ? '封面上传已取消' : message(e) }
  finally { if (alive) coverUploading.value = false }
}
async function cancelAudioUpload() {
  try { await showConfirmDialog({ title: '取消声音上传？', message: '将停止本次上传，原声音不会更改。', confirmButtonColor: '#008aff' }); audioController?.abort() } catch { /* 保持上传 */ }
}
async function cancelCoverUpload() {
  try { await showConfirmDialog({ title: '取消封面上传？', message: '将停止本次上传，原封面不会更改。', confirmButtonColor: '#008aff' }); coverController?.abort() } catch { /* 保持上传 */ }
}
function onValidationFailed() { submitError.value = '请检查必填项；已填写的内容及成功上传的文件已保留。' }
async function submit() {
  if (saving.value || audioUploading.value || coverUploading.value || albumsLoading.value) return
  submitError.value = ''
  if (!validMediaId(form.mediaFileId) || !form.mediaUrl || !selectedAlbum.value || albumsError.value) { submitError.value = '声音上传或本人专辑尚未就绪，请补全后重试。'; return }
  try {
    await showConfirmDialog({ title: editId.value ? '保存声音修改？' : '提交声音？', message: form.isOpen === '1' ? '公开且通过审核后，听众可在专辑中收听。请确认内容及所属专辑。' : '声音将以私密状态保存，是否提交？', confirmButtonColor: '#008aff' })
  } catch { return }
  saving.value = true
  // TrackInfoVo 不接收浏览器时长和 mediaUrl；服务端根据真实 fileId 回查 VOD。
  const payload = { albumId: form.albumId, mediaFileId: form.mediaFileId, trackTitle: form.trackTitle.trim(), trackIntro: form.trackIntro.trim(), trackRichIntro: originalInfo.trackRichIntro ?? null, coverUrl: form.coverUrl || '', isOpen: form.isOpen }
  try {
    if (editId.value) await albumsApi.editTrackInfo({ ...payload, id: editId.value })
    else await albumsApi.addTrackInfo(payload)
    originalSnapshot.value = snapshot()
    leaveApproved = true
    showSuccessToast(editId.value ? '声音修改已保存' : '声音提交成功')
    await router.replace(route.query.albumId ? `/album/${form.albumId}` : { path: '/myWork', query: { tab: 'track' } })
  } catch (e) { submitError.value = `${message(e)}；表单和已上传媒体已保留，请确认后重试。` }
  finally { saving.value = false }
}
async function confirmLeave(force = false) {
  if (leaveApproved) return true
  if (saving.value) return false
  if (!force && !dirty.value && !audioUploading.value && !coverUploading.value) return true
  try {
    await showConfirmDialog({ title: audioUploading.value || coverUploading.value ? '停止上传并离开？' : '放弃本次编辑？', message: '未提交的内容不会保存。已上传的文件不会自动成为作品，是否离开？', confirmButtonColor: '#008aff' })
    audioController?.abort()
    coverController?.abort()
    return true
  } catch { return false }
}
async function cancelForm() { if (await confirmLeave(true)) { leaveApproved = true; window.history.state?.back ? router.back() : router.replace('/myWork?tab=track') } }
onBeforeRouteLeave(() => confirmLeave())
onBeforeRouteUpdate(() => confirmLeave())
function beforeUnload(event) { if (dirty.value || audioUploading.value || coverUploading.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { alive = false; generation++; audioController?.abort(); coverController?.abort(); for (const cancel of metadataCleanups) cancel(); window.removeEventListener('beforeunload', beforeUnload) })
watch(editId, initialize, { immediate: true })
</script>

<style scoped>
.track-form-page { --van-primary-color: #008aff; --van-field-label-width: 85px; min-height: 100vh; color: #333; background: #f8f8f8; padding-bottom: env(safe-area-inset-bottom); }
.track-form-page :deep(.van-nav-bar .van-icon) { color: #008aff; }
.page-loading { padding: 25px 15px; }
.form-panel { background: white; padding: 5px 0 15px; }
.section-caption { display: flex; justify-content: space-between; align-items: center; font-size: 14px; font-weight: 600; margin: 15px; }
.section-caption span { color: #999; font-size: 11px; font-weight: 400; }
.track-form-page :deep(.van-field) { padding: 14px 15px; align-items: flex-start; }
.track-form-page :deep(.van-field__label) { color: #333; }
.audio-upload, .cover-area { width: 100%; min-width: 0; }
.audio-picker { position: relative; display: flex; flex-direction: column; align-items: center; gap: 9px; background: #f8f8f8; border: .5px dashed #ccc; border-radius: 5px; padding: 18px 10px; color: #008aff; font-size: 13px; text-align: center; overflow: hidden; cursor: pointer; }
.audio-picker small { color: #999; font-size: 10px; line-height: 1.6; }
.audio-picker input, .cover-picker input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.audio-picker:focus-within, .cover-picker:focus-within { outline: 2px solid #008aff; }
.disabled { opacity: .6; }
.audio-preview { background: #f8f8f8; padding: 10px; margin-top: 10px; border-radius: 5px; }
.audio-preview audio { display: block; width: 100%; height: 38px; }
.file-name { overflow-wrap: anywhere; font-size: 12px; margin: 8px 0; }
.media-meta { display: flex; gap: 6px 12px; flex-wrap: wrap; font-size: 10px; color: #666; margin: 8px 0; }
.media-id { font-size: 10px; color: #999; margin: 0; overflow-wrap: anywhere; }
.media-note { color: #999; font-size: 10px; line-height: 1.7; margin: 8px 0 0; overflow-wrap: anywhere; }
.upload-progress { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; color: #008aff; font-size: 11px; margin-top: 10px; }
.upload-progress button, .text-button, .dependency button { border: 0; padding: 4px 0; color: #008aff; background: none; font-size: 12px; }
.field-error { color: #b42318; font-size: 12px; line-height: 1.7; margin: 8px 0 0; overflow-wrap: anywhere; }
.cover-picker { display: flex; position: relative; align-items: center; justify-content: center; width: 80px; height: 80px; color: #999; border-radius: 5px; background: #f8f8f8; border: .5px dashed #ccc; overflow: hidden; cursor: pointer; }
.cover-picker > span { display: flex; gap: 6px; align-items: center; flex-direction: column; }
.cover-picker small { font-size: 11px; }
.album-select { width: 100%; min-width: 0; box-sizing: border-box; color: #333; background: white; border: 1px solid #e5e5e5; padding: 9px 6px; border-radius: 5px; font-size: 13px; }
.album-select:disabled { color: #666; background: #f8f8f8; opacity: 1; }
.indented-note { margin: 0 15px 6px 100px; color: #999; font-size: 11px; line-height: 1.7; }
.dependency { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 10px 15px; font-size: 12px; color: #999; }
.dependency a { color: #008aff; }
.dependency.error { color: #b42318; }
.submit-error { margin: 15px; padding: 12px; background: #fff2f0; color: #b42318; border-radius: 5px; font-size: 13px; line-height: 1.7; }
.submit-actions { display: flex; gap: 12px; padding: 20px 15px 30px; }
.submit-actions > :first-child { flex: 1; }
.submit-actions > :last-child { flex: 2; }
button:focus-visible, select:focus-visible { outline: 2px solid #008aff; outline-offset: 2px; }
@media (max-width: 350px) { .track-form-page { --van-field-label-width: 75px; } .indented-note { margin-left: 90px; } }
</style>
