package com.atguigu.tingshu.payment.api;

import com.atguigu.tingshu.common.annotation.Log;
import com.atguigu.tingshu.common.enums.BusinessType;
import com.atguigu.tingshu.common.enums.OperatorType;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @Autowired
    private WxPayV3Config wxPayV3Config;
    @Autowired
    private com.wechat.pay.java.core.RSAAutoCertificateConfig rsaAutoCertificateConfig;
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
     * 【修复#5】微信支付成功回调通知（APIV3）
     * https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/payment-notice.html
     * 原工程完全没有实现该端点，支付成功只能依赖前端轮询 queryPayStatus 触发改库。
     * 处理流程：
     * 1. NotificationParser 用平台证书验签 + 时间戳防重放（SDK 内部完成），非法请求直接 FAIL；
     * 2. updatePaymentStatus 内部校验回调金额与本地支付记录一致；
     * 3. 条件更新（未支付→已支付）保证幂等，重复通知直接忽略。
     * 应答约定：成功返回 {"code":"SUCCESS"}，失败返回 HTTP 500 + {"code":"FAIL"}，微信会按策略重试。
     */
    @Log(title = "微信支付回调", businessType = BusinessType.UPDATE, operatorType = OperatorType.OTHER)
    @Operation(summary = "微信支付回调通知")
    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(HttpServletRequest request,
                                                      @RequestBody String body) {
        try {
            //1. 构造验签参数（微信回调签名放在请求头中）
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(request.getHeader("Wechatpay-Serial"))
                    .nonce(request.getHeader("Wechatpay-Nonce"))
                    .signature(request.getHeader("Wechatpay-Signature"))
                    .timestamp(request.getHeader("Wechatpay-Timestamp"))
                    .signType(request.getHeader("Wechatpay-Signature-Type"))
                    .body(body)
                    .build();
            //2. 验签 + 解密报文（非法/伪造请求在 parse 内抛出异常）
            NotificationParser parser = new NotificationParser(rsaAutoCertificateConfig);
            Transaction transaction = parser.parse(requestParam, Transaction.class);
            log.info("[支付回调] 验签通过，orderNo={}, tradeState={}",
                    transaction.getOutTradeNo(), transaction.getTradeState());
            //3. 金额校验 + 幂等更新支付记录并发送 MQ（见 PaymentInfoServiceImpl）
            paymentInfoService.updatePaymentStatus(transaction);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("[支付回调] 处理失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "失败"));
        }
    }

    /**
     * 查询微信支付状态 https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_5_2.shtml
     * @param orderNo
     * @return
     */
    @LoginVerification
    @Operation(summary = "微信支付状态查询")
    @GetMapping("/queryPayStatus/{orderNo}")
    public Result queryPayStatus(@PathVariable String orderNo) {
        //调用方法根据订单编号查询支付状态，返回Transaction
        Transaction transaction = wxPayService.queryPayStatus(orderNo);
        //Transaction对象判断是否成功
        // "trade_state" : "SUCCESS",
        if(transaction != null && transaction.getTradeState()==Transaction.TradeStateEnum.SUCCESS) {
            //微信支付成功调用方法更新订单状态、支付记录状态、和用户购买记录
            //【修复#5】updatePaymentStatus 已幂等化：轮询与 notify 回调并发/重复到达不会重复发 MQ
            paymentInfoService.updatePaymentStatus(transaction);
            return Result.ok(true);
        }
        return Result.ok(false);
    }
}
