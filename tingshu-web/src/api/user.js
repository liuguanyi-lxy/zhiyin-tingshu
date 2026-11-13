import { Service } from '../utils/request.js'

/** 用户模块：H5 手机号账号与小程序共用 Redis token 会话。 */
class UserApi extends Service {
  /** 手机号密码登录；不发送昵称或确认密码。 */
  phoneLogin({ phone, password }) {
    return this.post({ url: '/api/user/wxLogin/phoneLogin', data: { phone, password }, loading: false, silent: true })
  }
  /** 注册成功直接返回 data.token，无需再调用登录。短信开启时需带 smsCode。 */
  register({ phone, password, nickname, smsCode }) {
    return this.post({
      url: '/api/user/wxLogin/register',
      data: { phone, password, nickname, ...(smsCode ? { smsCode } : {}) },
      loading: false, silent: true
    })
  }
  /** 发送短信验证码；scene=register/login。 */
  sendSmsCode(phone, scene) {
    return this.post({ url: '/api/user/wxLogin/sendSmsCode', data: { phone, scene }, loading: false, silent: true })
  }
  /** 短信验证码登录；验证码一次性消费。 */
  smsLogin({ phone, smsCode }) {
    return this.post({ url: '/api/user/wxLogin/smsLogin', data: { phone, smsCode }, loading: false, silent: true })
  }
  /** 短信能力探测：未配置阿里云短信时返回 enabled=false。 */
  getSmsEnabled() {
    return this.get({ url: '/api/user/wxLogin/smsEnabled', loading: false, silent: true })
  }
  /** 与小程序一致：multipart 的 file 字段，成功时 data 是图片 URL 字符串。 */
  uploadAvatar(file) {
    return this.upload({ url: '/api/album/fileUpload', file })
  }
  /** 【安全审计 M4】开发联调 devLogin 已从前端移除：网关白名单条目仍在（后端属性门禁默认关），
   *  前端不再保留该调用链，避免配置漂移时残留可用的任意账号接管入口。 */
  /** 小程序微信登录（H5 不可用，保留对齐） */
  getLogin(code) {
    return this.get({ url: `/api/user/wxLogin/wxLogin/${code}` })
  }
  /** 获取用户登录信息 */
  getUserInfo() {
    return this.get({ url: '/api/user/wxLogin/getUserInfo' })
  }
  /** 更新用户信息 */
  updateUserInfo(userInfo) {
    return this.post({ url: '/api/user/wxLogin/updateUser', data: userInfo })
  }
  /** 订阅/取消订阅专辑 */
  subscribeAlbum(albumId) {
    return this.get({ url: `/api/user/userInfo/subscribe/${albumId}` })
  }
  isSubscribeAlbum(albumId) {
    return this.get({ url: `/api/user/userInfo/isSubscribe/${albumId}` })
  }
  /** 收藏/取消收藏声音 */
  collectTrack(trackId) {
    return this.get({ url: `/api/user/userInfo/collect/${trackId}` })
  }
  isCollectTrack(trackId) {
    return this.get({ url: `/api/user/userInfo/isCollect/${trackId}` })
  }
  /** 订阅专辑分页 */
  getSubscribeAlbums(pageInfo) {
    return this.get({ url: `/api/user/userInfo/findUserSubscribePage/${pageInfo.page}/${pageInfo.limit}` })
  }
  /** 收藏声音分页 */
  getCollectTrack(pageInfo) {
    return this.get({ url: `/api/user/userInfo/findUserCollectPage/${pageInfo.page}/${pageInfo.limit}` })
  }
  /** 播放历史分页 */
  getHistoryTrack(pageInfo) {
    return this.get({ url: `/api/user/userListenProcess/findUserPage/${pageInfo.page}/${pageInfo.limit}` })
  }
  deleteHistoryTrack(id) {
    return this.delete({ url: `/api/user/userListenProcess/delete/${id}` })
  }
  /** 最近一次播放 */
  getLatelyTrack() {
    return this.get({ url: '/api/user/userListenProcess/getLatelyTrack' })
  }
  /** 更新播放进度（高频，静默） */
  updateListenProcess(data) {
    return this.post({ url: '/api/user/userListenProcess/updateListenProcess', data, loading: false })
  }
  /** 声音上次跳出时间（断点续播） */
  getTrackBreakSecond(trackId) {
    return this.get({ url: `/api/user/userListenProcess/getTrackBreakSecond/${trackId}` })
  }
  /** VIP 服务配置列表 */
  getVipServiceConfig() {
    return this.get({ url: '/api/user/vipServiceConfig/findAll' })
  }
}

export const userApi = new UserApi()
