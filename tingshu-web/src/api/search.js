import { Service } from '../utils/request.js'

/**
 * 搜索/排行榜模块（移植自小程序 api/search/search.js）
 */
class SearchApi extends Service {
  /** 搜索专辑（POST，条件检索） */
  getSearchAlbumInfo(params) {
    return this.post({ url: '/api/search/albumInfo', data: params })
  }
  /** 搜索建议补全（高频静默） */
  getSearchSuggestions(keyword) {
    return this.get({ url: `/api/search/albumInfo/completeSuggest/${keyword}`, loading: false })
  }
  /** 排行榜 */
  findRankingList(category1Id, dimension) {
    return this.get({ url: `/api/search/albumInfo/findRankingList/${category1Id}/${dimension}` })
  }
}

export const searchApi = new SearchApi()
