import { Service } from '../utils/request.js'

export const ORDER_STATUS = Object.freeze({ Unpaid: '0901', Paid: '0902', Cancelled: '0903' })
export const ITEM_TYPE = Object.freeze({ Album: '1001', Track: '1002', Vip: '1003' })
export const PAY_WAY = Object.freeze({ WeChat: '1101', Balance: '1103' })

/** 仅用于展示，提交订单时不得用格式化结果替换后端原始金额。 */
export function formatAmount(value) {
  if (value === null || value === undefined || value === '') return '—'
  const amount = Number(value)
  return Number.isFinite(amount) ? amount.toFixed(2) : '—'
}

export function orderStatusName(order) {
  return order?.orderStatusName || {
    '0901': '待支付', '0902': '已支付', '0903': '已取消'
  }[order?.orderStatus] || '状态待确认'
}

export function requestError(error, fallback = '加载失败，请稍后重试') {
  return error?.response?.data?.message || error?.message || fallback
}

class OrderApi extends Service {
  /** TradeVo: itemType、itemId；声音购买另带 trackCount（0 表示本集）。 */
  getSubmitOrderInfo(tradeVo) {
    return this.post({ url: '/api/order/orderInfo/trade', data: tradeVo, loading: false, silent: true })
  }

  /** 原样提交后端结算快照，包括 sign、timestamp、tradeNo、所有金额及明细。 */
  submitOrder(orderInfoVo) {
    return this.post({ url: '/api/order/orderInfo/submitOrder', data: orderInfoVo, loading: false, silent: true })
  }

  /**
   * 保留旧签名供已有调用方兼容，但后端没有 GET trade / 切换支付方式接口。
   * 不将 orderNo、payWay 猜测转换为 TradeVo，也不调用账户内部扣款接口。
   */
  trade(orderNo, payWay) {
    void orderNo
    void payWay
    return Promise.reject(new Error('当前后端不支持已有订单切换支付方式或余额支付'))
  }

  getOrders(pageInfo) {
    return this.get({
      url: `/api/order/orderInfo/findUserPage/${pageInfo.page}/${pageInfo.limit}`,
      loading: false, silent: true
    })
  }

  getOrderInfo(orderNo) {
    return this.get({
      url: `/api/order/orderInfo/getOrderInfo/${encodeURIComponent(orderNo)}`,
      loading: false, silent: true
    })
  }

  getTrackSettingList(trackId) {
    return this.get({
      url: `/api/album/trackInfo/findUserTrackPaidList/${encodeURIComponent(trackId)}`,
      loading: false, silent: true
    })
  }

  getAccountBalance() {
    return this.get({ url: '/api/account/userAccount/getAvailableAmount', loading: false, silent: true })
  }
}

export const orderApi = new OrderApi()
