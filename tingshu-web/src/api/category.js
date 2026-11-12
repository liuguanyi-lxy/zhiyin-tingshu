import { Service } from '../utils/request.js'

/**
 * 分类模块（移植自小程序 api/category/category.js）
 */
class CategoryApi extends Service {
  /** 首页一级分类列表 */
  findAllCategory1() {
    return this.get({ url: '/api/album/category/findAllCategory1' })
  }
  /** 全部分类信息（三级树） */
  getBaseCategoryList() {
    return this.get({ url: '/api/album/category/getBaseCategoryList' })
  }
  /** 与小程序同名的兼容入口，保留 getBaseCategoryList 签名。 */
  findAllCategory() {
    return this.getBaseCategoryList()
  }
  getAllCategory() {
    return this.getBaseCategoryList()
  }
  /** 三级分类回显的一、二、三级分类信息。 */
  getCategoryView(category3Id) {
    return this.get({ url: `/api/album/category/getCategoryView/${category3Id}` })
  }
  /** 根据一级分类Id获取全部分类信息 */
  getCategory1IdAllInfo(category1Id) {
    return this.get({ url: `/api/album/category/getBaseCategoryList/${category1Id}` })
  }
  /** 一级分类下的分类属性 */
  getAttrList(category1Id) {
    return this.get({ url: `/api/album/category/findAttribute/${category1Id}` })
  }
  /** 一级分类下置顶到频道页的三级分类列表 */
  getCategory1IdTopList(category1Id) {
    return this.get({ url: `/api/album/category/findTopBaseCategory3/${category1Id}` })
  }
  /** 频道推荐专辑列表（一级分类） */
  getCategory1IdData(id) {
    return this.get({ url: `/api/search/albumInfo/channel/${id}` })
  }
  /** 二级分类树结构数据 */
  getCategory2IdTreeList(id) {
    return this.get({ url: `/api/search/albumInfo/channel2/${id}` })
  }
}

export const categoryApi = new CategoryApi()
