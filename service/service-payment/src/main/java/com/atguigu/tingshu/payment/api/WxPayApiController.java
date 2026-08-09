package com.atguigu.tingshu.payment.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.wechat.pay.java.service.payments.model.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信支付接口")
@RestController
@RequestMapping("api/payment/wxPay")
@Slf4j
public class WxPayApiController {

    @Autowired
    private WxPayService wxPayService;
    @Autowired
    private PaymentInfoService paymentInfoService;
    /**
     * 微信支付
     * @param paymentType  支付种类：购买 充值
     * @param orderNo       订单编号
     * @return
     */
    @LoginVerification
    @Operation(summary = "调用微信支付接口")
    @PostMapping("/createJsapi/{paymentType}/{orderNo}")
    public Result createJsapi(@PathVariable String paymentType,
                              @PathVariable String orderNo) {
        Long userId = AuthContextHolder.getUserId();
        Map map = wxPayService.createJsapi(paymentType,orderNo,userId);
        return Result.ok(map);
    }

    /**
     * 查询微信支付状态 https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_5_2.shtml
     * @param orderNo
     * @return
     */
    @Operation(summary = "微信支付状态查询")
    @GetMapping("/queryPayStatus/{orderNo}")
    public Result queryPayStatus(@PathVariable String orderNo) {
        //调用方法根据订单编号查询支付状态，返回Transaction
        Transaction transaction = wxPayService.queryPayStatus(orderNo);
        //Transaction对象判断是否成功
        // "trade_state" : "SUCCESS",
        if(transaction != null && transaction.getTradeState()==Transaction.TradeStateEnum.SUCCESS) {
            //微信支付成功调用方法更新订单状态、支付记录状态、和用户购买记录
            paymentInfoService.updatePaymentStatus(transaction);
            return Result.ok(true);
        }
        return Result.ok(false);
    }
}
