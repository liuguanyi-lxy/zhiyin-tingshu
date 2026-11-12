import axios from 'axios'
import { Service } from '../utils/request.js'
import { BASE_URL } from '../config/confjg.js'
import { useUserStore } from '../stores/user.js'
import router from '../router/index.js'

/** updateAlbumInfo 接收完整 VO；公开状态切换也不能丢失定价、试听和属性。 */
export function toAlbumPayload(info) {
  const keys = ['albumTitle', 'category3Id', 'albumIntro', 'coverUrl', 'estimatedTrackCount',
    'albumRichIntro', 'payType', 'priceType', 'price', 'discount', 'vipDiscount',
    'tracksForFree', 'secondsForFree', 'buyNotes', 'sellingPoint', 'isOpen']
  const payload = Object.fromEntries(keys.filter(key => info[key] !== undefined).map(key => [key, info[key]]))
  payload.albumAttributeValueVoList = (info.albumAttributeValueVoList ?? info.albumAttributeValueList ?? [])
    .map(({ attributeId, valueId }) => ({ attributeId, valueId }))
  return payload
}

// 上传单独使用长超时，不改全局 request；FormData 的 boundary 由浏览器生成。
async function uploadCreatorFile(url, file, { onProgress, signal } = {}) {
  const form = new FormData()
  form.append('file', file)
  const response = await axios.post(url, form, {
    baseURL: BASE_URL,
    timeout: 180000,
    signal,
    headers: { token: useUserStore().token },
    onUploadProgress: event => {
      if (event.total && onProgress) onProgress(Math.min(100, Math.round(event.loaded / event.total * 100)))
    }
  })
  const result = response.data
  if (result?.code === 208) {
    useUserStore().clear()
    const current = router.currentRoute.value
    if (current.path !== '/login') await router.replace({ path: '/login', query: { redirect: current.fullPath } })
    throw new Error('登录已过期，请重新登录后上传')
  }
  if (result?.code !== 200) throw new Error(result?.message || '上传服务未成功返回，请重试')
  return result
}

/**
 * 专辑/声音模块（移植自小程序 api/albums/albums.js）
 */
class AlbumApi extends Service {
  /** 专辑信息 */
  getAlbumInfo(id) {
    return this.get({ url: `/api/album/albumInfo/getAlbumInfo/${id}` })
  }
  /** 专辑统计数据（播放/订阅/购买/评论数） */
  getAlbumInfoStat(albumId) {
    return this.get({ url: `/api/album/albumInfo/getAlbumInfoStat/${albumId}` })
  }
  addAlbum(albumInfo) {
    return this.post({ url: '/api/album/albumInfo/saveAlbumInfo', data: albumInfo })
  }
  editAlbum(albumInfo) {
    return this.put({ url: `/api/album/albumInfo/updateAlbumInfo/${albumInfo.id}`, data: albumInfo })
  }
  deleteAlbumInfo(id) {
    return this.delete({ url: `/api/album/albumInfo/removeAlbumInfo/${id}` })
  }
  /** 当前用户分页专辑列表（我的作品） */
  getAlbumList(pageInfo) {
    return this.post({ url: `/api/album/albumInfo/findUserAlbumPage/${pageInfo.page}/${pageInfo.limit}`, data: pageInfo })
  }
  getAllAlbumList() {
    return this.get({ url: '/api/album/albumInfo/findUserAllAlbumList' })
  }
  /** 声音信息 */
  getTrackInfo(id) {
    return this.get({ url: `/api/album/trackInfo/getTrackInfo/${id}` })
  }
  deleteTrackInfo(id) {
    return this.delete({ url: `/api/album/trackInfo/removeTrackInfo/${id}` })
  }
  addTrackInfo(trackInfo) {
    return this.post({ url: '/api/album/trackInfo/saveTrackInfo', data: trackInfo })
  }
  editTrackInfo(trackInfo) {
    return this.put({ url: `/api/album/trackInfo/updateTrackInfo/${trackInfo.id}`, data: trackInfo })
  }
  /** 当前用户声音分页列表 */
  getTrackList(pageInfo) {
    return this.post({ url: `/api/album/trackInfo/findUserTrackPage/${pageInfo.page}/${pageInfo.limit}`, data: pageInfo })
  }
  /** 专辑详情（ES 视图，含主播名）；压测占位专辑可能未入索引，调用方需静默兜底 */
  getAlbumDetail(albumId) {
    return this.get({ url: `/api/search/albumInfo/${albumId}`, silent: true })
  }
  /** 专辑声音分页列表 */
  getAlbumTrackList(trackPage) {
    return this.get({ url: `/api/album/trackInfo/findAlbumTrackPage/${trackPage.albumId}/${trackPage.page}/${trackPage.limit}` })
  }
  /** 声音统计信息 */
  getTrackStaVo(trackId) {
    return this.get({ url: `/api/album/trackInfo/getTrackStatVo/${trackId}` })
  }
  /** 图片服务返回 data: URL，不能用作声音媒体 ID。 */
  async uploadCover(file, options = {}) {
    const result = await uploadCreatorFile('/api/album/fileUpload', file, options)
    if (typeof result.data !== 'string' || !/^https?:\/\//i.test(result.data)) {
      throw new Error('封面服务未返回有效图片地址，请重试')
    }
    return result
  }
  /** 腾讯云 VOD 返回 data: { mediaFileId, mediaUrl }。 */
  async uploadTrack(file, options = {}) {
    const result = await uploadCreatorFile('/api/album/trackInfo/uploadTrack', file, options)
    const { mediaFileId, mediaUrl } = result.data || {}
    if (typeof mediaFileId !== 'string' || !mediaFileId.trim() || /[:/\\]/.test(mediaFileId)
      || typeof mediaUrl !== 'string' || !/^https?:\/\//i.test(mediaUrl)) {
      throw new Error('声音上传未就绪：VOD 未返回有效的 mediaFileId 和播放地址，不能保存声音')
    }
    return result
  }
  /** 复用真实修改接口完成上下架；不调用后台管理端 ES 接口。 */
  async setAlbumOpen(albumId, isOpen) {
    const { data } = await this.getAlbumInfo(albumId)
    if (!data) throw new Error('专辑不存在，无法修改公开状态')
    const payload = toAlbumPayload(data)
    if (!payload.albumAttributeValueVoList.length) throw new Error('专辑标签不完整，请进入编辑页补全后保存')
    return this.editAlbum({ ...payload, id: albumId, isOpen: String(isOpen) })
  }
}

export const albumsApi = new AlbumApi()
