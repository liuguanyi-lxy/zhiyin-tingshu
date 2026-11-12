import { Service } from '../utils/request.js'

/**
 * 账户模块（移植自小程序 api/account/account.js）
 */
class AccountApi extends Service {
  /** 可用余额 */
  getAvailableAmount() {
    return this.get({ url: '/api/account/userAccount/getAvailableAmount', loading: false, silent: true })
  }
  /** 提交充值订单 */
  submitRecharge(rechargeInfoVo) {
    // RechargeInfoVo 实际字段为 amount、payWay，rechargeAmount 是返回实体字段。
    return this.post({
      url: '/api/account/rechargeInfo/submitRecharge',
      data: { amount: rechargeInfoVo.amount, payWay: rechargeInfoVo.payWay },
      loading: false, silent: true
    })
  }
  /** 消费记录分页 */
  getConsumePage(pageInfo) {
    return this.get({ url: `/api/account/userAccount/findUserConsumePage/${pageInfo.page}/${pageInfo.limit}`, loading: false, silent: true })
  }
  /** 充值记录分页 */
  getRechargePage(pageInfo) {
    return this.get({ url: `/api/account/userAccount/findUserRechargePage/${pageInfo.page}/${pageInfo.limit}`, loading: false, silent: true })
  }
}

export const accountApi = new AccountApi()
