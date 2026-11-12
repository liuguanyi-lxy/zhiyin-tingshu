<template>
  <main class="album-form-page">
    <van-nav-bar :title="editId ? '修改专辑' : '创建专辑'" left-arrow @click-left="cancelForm" />
    <van-skeleton v-if="initialLoading" title :row="8" class="page-loading" />
    <van-empty v-else-if="pageError" image="error" :description="pageError"><van-button size="small" color="#008aff" @click="initialize">重新加载</van-button></van-empty>
    <van-form v-else :disabled="saving" :scroll-to-error="true" :show-error-message="true" @submit="submit" @failed="onValidationFailed">
      <section class="form-panel">
        <p class="section-caption">专辑信息 <span>带 * 为必填项</span></p>
        <van-field v-model="form.albumTitle" name="albumTitle" label="专辑名称" placeholder="请输入专辑名称（至少 2 个字）" required maxlength="200" :rules="[{ validator: value => value.trim().length >= 2, message: '请填写至少 2 个字的标题' }]" />
        <van-field v-model="form.coverUrl" name="coverUrl" label="专辑封面" required :rules="[{ required: true, message: '请上传专辑封面' }]">
          <template #input>
            <div class="upload-area">
              <label class="cover-picker" :class="{ disabled: coverUploading || saving }">
                <van-image v-if="form.coverUrl" :src="form.coverUrl" fit="cover" width="90" height="90" radius="5" />
                <span v-else><van-icon name="plus" size="26" /><small>上传封面</small></span>
                <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" aria-label="上传专辑封面" :disabled="coverUploading || saving" @change="uploadCover" />
              </label>
              <div class="upload-copy"><p>正方形图片，建议 960 × 960</p><p>JPG / PNG / WebP / GIF，最大 10 MB</p><button v-if="form.coverUrl && !coverUploading" type="button" class="text-button" :disabled="saving" @click="form.coverUrl = ''">移除封面</button></div>
              <div v-if="coverUploading" class="upload-progress"><van-loading size="16" /> {{ coverProgress < 100 ? `正在传输 ${coverProgress}%` : '存储服务处理中…' }} <button type="button" @click="cancelCoverUpload">取消上传</button></div>
              <p v-if="coverError" class="field-error" role="alert">{{ coverError }}<span v-if="form.coverUrl"> 原封面已保留。</span></p>
            </div>
          </template>
        </van-field>
        <van-field :model-value="categoryText" name="category3Id" label="专辑分类" placeholder="请选择三级分类" readonly is-link required :rules="[{ validator: () => !!form.category3Id && !!selectedRoot, message: '请选择完整的三级分类' }]" @click="categoryVisible = true" />
        <div v-if="categoryLoading" class="dependency"><van-loading size="16" /> 正在加载分类</div>
        <div v-if="categoryError" class="dependency error" role="alert">{{ categoryError }} <button type="button" @click="reloadCategories">重试分类</button></div>
        <van-field name="attributes" label="专辑标签" required :rules="[{ validator: attributesValid, message: '请为每个属性选择一个标签' }]">
          <template #input>
            <div class="attributes">
              <span v-if="!form.category3Id" class="muted">选择分类后设置标签</span>
              <van-loading v-else-if="attributeLoading" size="18">加载标签</van-loading>
              <div v-else-if="attributeError" class="field-error" role="alert">{{ attributeError }} <button type="button" @click="reloadAttributes">重试标签</button></div>
              <p v-else-if="!attributes.length" class="muted">该分类尚未配置标签，后端要求属性非空，暂不能提交。请换分类或联系管理员。</p>
              <div v-for="attribute in attributes" :key="attribute.id" class="attribute-group">
                <p>{{ attribute.attributeName }}</p>
                <van-radio-group :disabled="saving" v-model="selections[attribute.id]" direction="horizontal">
                  <van-radio v-for="value in attribute.attributeValueList" :key="value.id" :name="String(value.id)" checked-color="#008aff">{{ value.valueName }}</van-radio>
                </van-radio-group>
              </div>
            </div>
          </template>
        </van-field>
        <van-field v-model="form.albumIntro" name="albumIntro" label="专辑简介" type="textarea" rows="4" autosize placeholder="介绍这张专辑的内容" required :rules="[{ validator: value => !!value.trim(), message: '请填写专辑简介' }]" />
        <van-field name="isOpen" label="设为私密"><template #input><van-switch :model-value="form.isOpen === '0'" size="24" active-color="#008aff" :disabled="saving" @update:model-value="form.isOpen = $event ? '0' : '1'" /></template></van-field>
        <p class="privacy-note">{{ form.isOpen === '0' ? '私密专辑不展示在听众端检索中。' : '公开提交会触发专辑上架，请确认内容及版权。' }}</p>
      </section>
      <section class="form-panel">
        <p class="section-caption">付费设置</p>
        <van-field name="payType" label="付费类型" required><template #input><van-radio-group :disabled="saving" v-model="form.payType" direction="horizontal"><van-radio name="0101" checked-color="#008aff">免费</van-radio><van-radio name="0102" checked-color="#008aff">VIP 免费</van-radio><van-radio name="0103" checked-color="#008aff">付费</van-radio></van-radio-group></template></van-field>
        <template v-if="form.payType !== '0101'">
          <van-field name="priceType" label="价格类型" required><template #input><van-radio-group :disabled="saving" v-model="form.priceType" direction="horizontal"><van-radio name="0201" checked-color="#008aff">单集</van-radio><van-radio name="0202" checked-color="#008aff">整专辑</van-radio></van-radio-group></template></van-field>
          <van-field v-model="form.price" name="price" label="专辑原价" inputmode="decimal" placeholder="输入金额，单位元" required :rules="[{ validator: validPrice, message: '请填写大于 0 的价格，最多两位小数' }]"><template #right-icon>元</template></van-field>
          <van-field v-model="form.discount" name="discount" label="专辑折扣" inputmode="decimal" placeholder="0.1～9.9，不打折填 -1" required :rules="[{ validator: validDiscount, message: '折扣为 0.1～9.9，不打折填 -1' }]" />
          <van-field v-model="form.vipDiscount" name="vipDiscount" label="会员折扣" inputmode="decimal" placeholder="0.1～9.9，不打折填 -1" required :rules="[{ validator: validDiscount, message: '会员折扣为 0.1～9.9，不打折填 -1' }]" />
          <p class="privacy-note">VIP 免费也需设置原价与折扣，沿用原版收费规则。</p>
        </template>
      </section>
      <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>
      <div class="submit-actions"><van-button native-type="button" :disabled="saving" @click="cancelForm">取消</van-button><van-button native-type="submit" color="#008aff" :loading="saving" :disabled="coverUploading || categoryLoading || attributeLoading || !!categoryError || !!attributeError || !attributes.length">{{ editId ? '保存修改' : '提交专辑' }}</van-button></div>
    </van-form>
    <van-popup v-model:show="categoryVisible" position="bottom" round class="category-popup">
      <van-cascader v-model="categoryChoice" title="请选择专辑分类" :options="categories" :field-names="{ text: 'categoryName', value: 'categoryId', children: 'categoryChild' }" active-color="#008aff" @close="categoryVisible = false" @finish="finishCategory" />
      <div v-if="categoryError || !categories.length" class="dependency">{{ categoryError || '暂无可用分类' }} <button type="button" @click="reloadCategories">重新加载分类</button></div>
    </van-popup>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { albumsApi, toAlbumPayload } from '../../api/albums.js'
import { categoryApi } from '../../api/category.js'

const route = useRoute()
const router = useRouter()
const editId = computed(() => String(route.params.id || ''))
const defaults = () => ({ albumTitle: '', category3Id: '', albumIntro: '', coverUrl: '', payType: '0101', priceType: '0201', price: '', discount: '-1', vipDiscount: '-1', isOpen: '1' })
const form = reactive(defaults())
const categories = ref([])
const attributes = ref([])
const selections = reactive({})
const categoryText = ref('')
const categoryChoice = ref('')
const selectedRoot = ref('')
const categoryVisible = ref(false)
const initialLoading = ref(true)
const pageError = ref('')
const categoryLoading = ref(false)
const categoryError = ref('')
const attributeLoading = ref(false)
const attributeError = ref('')
const coverUploading = ref(false)
const coverProgress = ref(0)
const coverError = ref('')
const saving = ref(false)
const submitError = ref('')
let originalInfo = {}
const originalSnapshot = ref('')
let leaveApproved = false
let coverController
let generation = 0
let attrVersion = 0
let alive = true
const message = e => e?.response?.data?.message || e?.message || '请求失败，请检查网络后重试'
const snapshot = () => JSON.stringify({ ...form, selections: { ...selections } })
const dirty = computed(() => !!originalSnapshot.value && snapshot() !== originalSnapshot.value)
function clearSelections() { for (const key of Object.keys(selections)) delete selections[key] }
function findPath(id) {
  for (const root of categories.value) for (const second of root.categoryChild || []) {
    const third = (second.categoryChild || []).find(item => String(item.categoryId) === String(id))
    if (third) return [root, second, third]
  }
  return []
}
async function reloadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const { data } = await categoryApi.getBaseCategoryList()
    if (!alive) return
    if (!Array.isArray(data) || !data.length) throw new Error('暂无可用分类，请联系管理员')
    // 分类 ID 统一成字符串，防止编辑回显的数字/字符串类型不一致。
    const normalize = nodes => nodes.map(node => ({ ...node, categoryId: String(node.categoryId), ...(node.categoryChild?.length ? { categoryChild: normalize(node.categoryChild) } : { categoryChild: undefined }) }))
    categories.value = normalize(data)
    if (form.category3Id) {
      const path = findPath(form.category3Id)
      if (path.length !== 3) throw new Error('原分类已不可用，请重新选择完整三级分类')
      categoryText.value = path.map(item => item.categoryName).join(' / ')
      selectedRoot.value = path[0].categoryId
      categoryChoice.value = String(form.category3Id)
      if (!attributes.value.length) await reloadAttributes()
    }
  } catch (e) { if (alive) categoryError.value = message(e) }
  finally { if (alive) categoryLoading.value = false }
}
async function reloadAttributes() {
  if (!selectedRoot.value) return
  const version = ++attrVersion
  attributeLoading.value = true
  attributeError.value = ''
  const previous = Object.entries(selections).map(([attributeId, valueId]) => ({ attributeId, valueId }))
  try {
    const { data } = await categoryApi.getAttrList(selectedRoot.value)
    if (!alive || version !== attrVersion) return
    if (!Array.isArray(data)) throw new Error('标签响应不完整，请重试')
    attributes.value = data
    clearSelections()
    for (const attr of data) {
      if (!Array.isArray(attr.attributeValueList) || !attr.attributeValueList.length) throw new Error('该分类有未配置选项的属性，请换分类或联系管理员')
      const selected = previous.find(item => String(item.attributeId) === String(attr.id))
      selections[attr.id] = attr.attributeValueList.some(value => String(value.id) === String(selected?.valueId)) ? String(selected.valueId) : ''
    }
  } catch (e) { if (alive && version === attrVersion) attributeError.value = message(e) }
  finally { if (alive && version === attrVersion) attributeLoading.value = false }
}
async function finishCategory({ selectedOptions }) {
  if (selectedOptions.length !== 3) { categoryError.value = '必须选择到第三级分类'; return }
  const rootChanged = selectedRoot.value !== String(selectedOptions[0].categoryId)
  categoryVisible.value = false
  categoryError.value = ''
  form.category3Id = String(selectedOptions[2].categoryId)
  selectedRoot.value = String(selectedOptions[0].categoryId)
  categoryText.value = selectedOptions.map(item => item.categoryName).join(' / ')
  if (rootChanged) { attributes.value = []; clearSelections() }
  await reloadAttributes()
}
function attributesValid() {
  return !attributeError.value && !attributeLoading.value && attributes.value.length > 0 && attributes.value.every(attr => attr.attributeValueList?.some(value => String(value.id) === selections[attr.id]))
}
const validPrice = value => /^\d+(\.\d{1,2})?$/.test(String(value)) && Number(value) > 0 && Number.isFinite(Number(value))
const validDiscount = value => String(value) === '-1' || (/^\d+(\.\d{1,2})?$/.test(String(value)) && Number(value) >= .1 && Number(value) <= 9.9)
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
async function cancelCoverUpload() {
  try { await showConfirmDialog({ title: '取消封面上传？', message: '将停止本次上传，已保存的封面不会更改。', confirmButtonColor: '#008aff' }); coverController?.abort() } catch { /* 保持上传 */ }
}
async function initialize() {
  const version = ++generation
  attrVersion++
  initialLoading.value = true
  originalSnapshot.value = ''
  pageError.value = ''
  submitError.value = ''
  leaveApproved = false
  originalInfo = {}
  Object.assign(form, defaults())
  attributes.value = []
  clearSelections()
  selectedRoot.value = ''
  categoryText.value = ''
  categoryChoice.value = ''
  try {
    if (editId.value) {
      const [detail, owned] = await Promise.all([albumsApi.getAlbumInfo(editId.value), albumsApi.getAllAlbumList()])
      if (!alive || version !== generation) return
      if (!detail.data?.id) throw new Error('专辑不存在或已删除')
      if (!Array.isArray(owned.data) || !owned.data.some(item => String(item.id) === editId.value)) throw new Error('只能编辑本人专辑')
      originalInfo = detail.data
      for (const key of Object.keys(defaults())) if (detail.data[key] != null) form[key] = String(detail.data[key])
      for (const item of detail.data.albumAttributeValueList || []) selections[item.attributeId] = String(item.valueId)
    }
    await reloadCategories()
    if (!alive || version !== generation) return
    originalSnapshot.value = snapshot()
  } catch (e) { if (alive && version === generation) pageError.value = message(e) }
  finally { if (alive && version === generation) initialLoading.value = false }
}
function onValidationFailed() { submitError.value = '请检查标红的必填项，填写内容已保留。' }
async function submit() {
  if (saving.value || coverUploading.value || categoryLoading.value || attributeLoading.value) return
  submitError.value = ''
  if (!attributesValid() || categoryError.value) { submitError.value = '分类及标签未就绪，请重试加载后完整选择。'; return }
  if (form.isOpen === '1') {
    try { await showConfirmDialog({ title: editId.value ? '保存公开专辑？' : '提交并公开专辑？', message: '公开专辑将展示给听众，并可能触发上架。请确认内容和付费设置。', confirmButtonColor: '#008aff' }) }
    catch { return }
  }
  saving.value = true
  const payload = toAlbumPayload({ ...originalInfo, ...form, albumTitle: form.albumTitle.trim(), albumIntro: form.albumIntro.trim(), price: form.price === '' ? null : Number(form.price), discount: Number(form.discount), vipDiscount: Number(form.vipDiscount), albumAttributeValueVoList: attributes.value.map(attr => ({ attributeId: attr.id, valueId: selections[attr.id] })) })
  try {
    if (editId.value) await albumsApi.editAlbum({ ...payload, id: editId.value })
    else await albumsApi.addAlbum(payload)
    originalSnapshot.value = snapshot()
    leaveApproved = true
    showSuccessToast(editId.value ? '专辑修改已保存' : '专辑提交成功')
    await router.replace('/myWork')
  } catch (e) { submitError.value = `${message(e)}；表单已保留，请确认后重试。` }
  finally { saving.value = false }
}
async function confirmLeave(force = false) {
  if (leaveApproved) return true
  if (saving.value) return false
  if (!force && !dirty.value && !coverUploading.value) return true
  try {
    await showConfirmDialog({ title: coverUploading.value ? '停止上传并离开？' : '放弃本次编辑？', message: '尚未提交的修改不会保存。已上传但未提交的封面也不会自动成为作品。', confirmButtonColor: '#008aff' })
    coverController?.abort()
    return true
  } catch { return false }
}
async function cancelForm() { if (await confirmLeave(true)) { leaveApproved = true; window.history.state?.back ? router.back() : router.replace('/myWork') } }
onBeforeRouteLeave(() => confirmLeave())
onBeforeRouteUpdate(() => confirmLeave())
function beforeUnload(event) { if (dirty.value || coverUploading.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { alive = false; generation++; attrVersion++; coverController?.abort(); window.removeEventListener('beforeunload', beforeUnload) })
watch(editId, initialize, { immediate: true })
</script>

<style scoped>
.album-form-page { --van-primary-color: #008aff; --van-field-label-width: 85px; color: #333; min-height: 100vh; background: #f8f8f8; padding-bottom: env(safe-area-inset-bottom); }
.album-form-page :deep(.van-nav-bar .van-icon) { color: #008aff; }
.page-loading { padding: 25px 15px; }
.form-panel { background: white; padding: 5px 0 10px; margin-bottom: 10px; }
.section-caption { display: flex; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 600; margin: 15px; }
.section-caption span { color: #999; font-size: 11px; font-weight: 400; }
.album-form-page :deep(.van-field) { padding: 14px 15px; align-items: flex-start; }
.album-form-page :deep(.van-field__label) { color: #333; }
.album-form-page :deep(.van-radio-group--horizontal) { gap: 10px 15px; flex-wrap: wrap; }
.album-form-page :deep(.van-radio--horizontal) { margin: 0; font-size: 12px; }
.upload-area { width: 100%; }
.cover-picker { display: flex; position: relative; width: 90px; height: 90px; justify-content: center; align-items: center; border-radius: 5px; background: #f8f8f8; border: .5px dashed #ccc; overflow: hidden; color: #999; cursor: pointer; }
.cover-picker > span { display: flex; align-items: center; flex-direction: column; gap: 8px; }
.cover-picker small { font-size: 11px; }
.cover-picker input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.cover-picker:focus-within { outline: 2px solid #008aff; }
.cover-picker.disabled { opacity: .6; }
.upload-copy p { margin: 6px 0; color: #999; font-size: 10px; line-height: 1.5; }
.text-button, .dependency button, .upload-progress button, .field-error button { padding: 4px 0; border: 0; color: #008aff; background: none; font-size: 12px; }
.upload-progress { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; font-size: 11px; color: #008aff; margin-top: 8px; }
.field-error { color: #b42318; font-size: 12px; line-height: 1.6; margin: 6px 0 0; overflow-wrap: anywhere; }
.attributes { min-width: 0; width: 100%; }
.muted { margin: 0; font-size: 12px; color: #999; line-height: 1.7; }
.attribute-group { margin: 0 0 16px; }
.attribute-group:last-child { margin-bottom: 0; }
.attribute-group p { margin: 0 0 10px; color: #666; font-size: 12px; }
.privacy-note { margin: 4px 15px 6px 100px; color: #999; font-size: 11px; line-height: 1.7; }
.dependency { margin: 4px 15px 10px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; color: #999; font-size: 12px; }
.error { color: #b42318; }
.submit-error { margin: 15px; padding: 12px; border-radius: 5px; background: #fff2f0; color: #b42318; font-size: 13px; line-height: 1.7; }
.submit-actions { display: flex; gap: 12px; padding: 15px 15px 30px; }
.submit-actions > :first-child { flex: 1; }
.submit-actions > :last-child { flex: 2; }
.category-popup { max-width: 480px; left: 50%; transform: translateX(-50%); padding-bottom: env(safe-area-inset-bottom); }
button:focus-visible { outline: 2px solid #008aff; outline-offset: 2px; }
@media (max-width: 350px) { .album-form-page { --van-field-label-width: 75px; } .privacy-note { margin-left: 90px; } }
</style>
