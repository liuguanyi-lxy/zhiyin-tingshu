package com.atguigu.tingshu.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderInfoFeignClient;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;

import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

	@Autowired
	private PaymentInfoService paymentInfoService;

	@Autowired
	private OrderInfoFeignClient orderInfoFeignClient;

	@Autowired
	private RSAAutoCertificateConfig rsaAutoCertificateConfig;

	@Autowired
	private WxPayV3Config wxPayV3Config;

	@Autowired
	private UserInfoFeignClient userInfoFeignClient;

	//根据订单编号查询支付状态
	@Override
	public Transaction queryPayStatus(String orderNo) {
		JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
		QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
		queryRequest.setMchid(wxPayV3Config.getMerchantId());
		queryRequest.setOutTradeNo(orderNo);

		Transaction transaction = service.queryOrderByOutTradeNo(queryRequest);
		return transaction;
	}

	//发起微信支付请求
	@Override
	public Map createJsapi(String paymentType, String orderNo, Long userId) {
		//1 如果paymentType支付种类是下订单类型，判断订单不是处于取消状态，如果订单状态是取消状态则不支付
		//// 支付类型 1301-订单 1302-充值
		if(paymentType.equals(SystemConstant.PAYMENT_TYPE_ORDER)) {
			//根据订单编号获取订单信息
			Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderNo);
			OrderInfo orderInfo = orderInfoResult.getData();
			Assert.notNull(orderInfo,"订单为空");
			//订单状态
			String orderStatus = orderInfo.getOrderStatus();
			//如果订单取消状态，不需要支付
			//订单状态 0901-正常 0902-已支付 0903-已取消
			if (SystemConstant.ORDER_STATUS_CANCEL.equals(orderStatus)){
				// 此时订单已取消
				return null;
			}
		}

		//2 添加支付记录到支付记录表(支付记录状态 - 未支付)
		PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(paymentType, orderNo);

		//3 发起微信支付请求
		// JsapiServiceExtension对象
		JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
		//创建request对象，设置微信支付需要参数
		PrepayRequest request = new PrepayRequest();
		// 注意在这个包下 com.wechat.pay.java.service.payments.jsapi.model.Amount
		//TODO 支付金额   支付0.01,作为测试
		Amount amount = new Amount();
		amount.setTotal(1);
		request.setAmount(amount);

		//公众号id  商户号等信息
		request.setAppid(wxPayV3Config.getAppid());
		request.setMchid(wxPayV3Config.getMerchantId());
		//微信显示信息
		request.setDescription(paymentInfo.getContent());
		//微信回调地址
		request.setNotifyUrl(wxPayV3Config.getNotifyUrl());
		//订单编号
		request.setOutTradeNo(paymentInfo.getOrderNo());
		// 获取用户信息
		Result<UserInfoVo> userInfoVoResult = userInfoFeignClient.getUserInfoVo(paymentInfo.getUserId());
		Assert.notNull(userInfoVoResult,"返回用户结果集对象不能为空");
		UserInfoVo userInfoVo = userInfoVoResult.getData();
		Assert.notNull(userInfoVo,"用户对象不能为空");
		String openid = userInfoVo.getWxOpenId();
		Payer payer = new Payer();
		payer.setOpenid(openid);
		request.setPayer(payer);

		//调用方法，传递request对象，发起微信支付请求
		PrepayWithRequestPaymentResponse response = service.prepayWithRequestPayment(request);
		Map<String, Object> result = new HashMap();
		result.put("timeStamp", response.getTimeStamp());
		result.put("nonceStr", response.getNonceStr());
		result.put("package", response.getPackageVal());
		result.put("signType", response.getSignType());
		result.put("paySign", response.getPaySign());
		// 返回数据
		return result;
	}
}
