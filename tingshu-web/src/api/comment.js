import { Service } from '../utils/request.js'

/**
 * 评论模块（移植自小程序 api/comment/comment.js）
 */
class CommentApi extends Service {
  /** 评论分页（专辑+声音维度） */
  getComments(params) {
    return this.get({ url: `/api/comment/findCommentPage/${params.albumId}/${params.trackId}/${params.page}/${params.limit}` })
  }
  /** 点赞 */
  praise(albumId, commentId) {
    return this.get({ url: `/api/comment/praise/${albumId}/${commentId}` })
  }
  /** 删除评论 */
  remove(albumId, commentId) {
    return this.get({ url: `/api/comment/remove/${albumId}/${commentId}` })
  }
  /** 发表评论 */
  save(data) {
    return this.post({ url: '/api/comment/save', data })
  }
}

export const commentApi = new CommentApi()
