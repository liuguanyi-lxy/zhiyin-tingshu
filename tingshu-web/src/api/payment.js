import { Service } from '../utils/request.js'

export const PAYMENT_TYPE = Object.freeze({ Order: '1301', Recharge: '1302' })

// Native 后端已实现并通过离线测试；默认关闭真实交易入口。
// 仅在服务部署、商户资质、证书和回调链路验收后，由部署环境显式开启。
export const NATIVE_ENDPOINT_AVAILABLE = import.meta.env.VITE_NATIVE_PAYMENT_ENABLED === 'true'

// 按当前仓库审计：submitOrder 强制微信支付；未暴露已有订单余额扣款端点。
// 不通过篡改已签名的 payWay 或调用 account 内部接口绕过此限制。
export const BALANCE_PAYMENT_BLOCKER = '当前后端仅支持微信下单，尚未提供订单余额支付接口，暂不能使用余额结算。'
export const NATIVE_PAYMENT_NOTICE = '扫码支付代码已接入，当前环境尚未完成商户、证书及回调验收，真实支付入口暂未开启。请勿转账或重复创建订单。'

export function paymentError(error) {
  const status = error?.response?.status
  if (status === 404 || status === 405) {
    return '当前服务未提供微信 Native 扫码支付接口，暂不可支付，请联系平台处理。'
  }
  const message = error?.response?.data?.message || error?.message
  return message
    ? `微信支付暂不可用：${message}。请联系平台核对支付服务及商户配置。`
    : '微信支付暂不可用。请联系平台核对支付服务、商户资质和证书配置。'
}

class PaymentApi extends Service {
  /** 保留原参数顺序；Controller 的真实 HTTP 方法是 POST。H5 不调用 JSAPI。 */
  createJsapi(paymentType, orderNo) {
    return this.post({
      url: `/api/payment/wxPay/createJsapi/${encodeURIComponent(paymentType)}/${encodeURIComponent(orderNo)}`,
      loading: false, silent: true
    })
  }

  /**
   * POST 路径和参数顺序与新增 Native Controller 一致。
   * 入口需通过环境门禁且由用户显式触发；无真实 data.codeUrl 不展示付款码。
   */
  createNative(paymentType, orderNo) {
    return this.post({
      url: `/api/payment/wxPay/createNative/${encodeURIComponent(paymentType)}/${encodeURIComponent(orderNo)}`,
      loading: false, silent: true
    })
  }

  /** data 为严格布尔值；仅 data === true 表示微信已确认成功。 */
  queryPayStatus(orderNo) {
    return this.get({
      url: `/api/payment/wxPay/queryPayStatus/${encodeURIComponent(orderNo)}`,
      loading: false, silent: true
    })
  }

  /** 充值验证只读，不创建充值、不查询任意账户余额推断充值成功。 */
  getRechargeInfo(orderNo) {
    return this.get({
      url: `/api/account/rechargeInfo/getRechargeInfo/${encodeURIComponent(orderNo)}`,
      loading: false, silent: true
    })
  }
}

export const paymentApi = new PaymentApi()
