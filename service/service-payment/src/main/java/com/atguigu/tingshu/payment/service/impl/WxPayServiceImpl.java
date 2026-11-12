package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.account.client.RechargeInfoFeignClient;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.util.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private RechargeInfoFeignClient rechargeInfoFeignClient;
    @Autowired
    private RedissonClient redissonClient;

    private final WechatPayClient wechatPayClient;
    private final Clock clock;

    /** SDK 边界可替换，纯 mock 测试不会读取证书或访问微信。 */
    interface WechatPayClient {
        com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse prepay(
                com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request);
        Transaction queryPayStatus(String orderNo);
    }

    public WxPayServiceImpl() {
        this.clock = Clock.systemUTC();
        this.wechatPayClient = new WechatPayClient() {
            @Override
            public com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse prepay(
                    com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request) {
                return new NativePayService.Builder().config(rsaAutoCertificateConfig).build().prepay(request);
            }
            @Override
            public Transaction queryPayStatus(String orderNo) {
                JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
                QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
                request.setMchid(wxPayV3Config.getMerchantId());
                request.setOutTradeNo(orderNo);
                return service.queryOrderByOutTradeNo(request);
            }
        };
    }

    WxPayServiceImpl(WechatPayClient wechatPayClient, Clock clock) {
        this.wechatPayClient = Objects.requireNonNull(wechatPayClient);
        this.clock = Objects.requireNonNull(clock);
    }

    // 保留旧服务签名；它不是外部用户入口。
    @Override
    public Transaction queryPayStatus(String orderNo) {
        return wechatPayClient.queryPayStatus(orderNo);
    }

    @Override
    public Transaction queryPayStatus(String orderNo, Long userId) {
        validateIdentity(orderNo, userId);
        PaymentInfo payment = paymentInfoService.getOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, orderNo));
        Assert.notNull(payment, "支付记录不存在，请先发起支付");
        Assert.isTrue(orderNo.equals(payment.getOrderNo()) && userId.equals(payment.getUserId()), "无权查询该支付记录");
        validatePaymentType(payment.getPaymentType());
        Assert.isTrue(SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payment.getPayWay()), "该记录不是微信支付");
        Assert.isTrue(SystemConstant.PAYMENT_STATUS_UNPAID.equals(payment.getPaymentStatus())
                || SystemConstant.PAYMENT_STATUS_PAID.equals(payment.getPaymentStatus()), "支付记录状态异常");
        exactFen(payment.getAmount());
        Transaction transaction = queryPayStatus(orderNo);
        if (transaction != null) {
            Assert.isTrue(orderNo.equals(transaction.getOutTradeNo()), "微信返回的订单号不一致");
        }
        return transaction;
    }

    @Override
    public Map<String, String> createNative(String paymentType, String orderNo, Long userId) {
        validateIdentity(orderNo, userId);
        validatePaymentType(paymentType);
        // 与 JSAPI 使用同一把跨实例锁；现库名为 uniq_order_no 的索引实际并非唯一索引。
        // 不依赖索引名称推断唯一约束，也不在运行时修改数据库结构。
        RLock lock = redissonClient.getLock("payment:wechat:create:" + orderNo);
        boolean locked = false;
        try {
            locked = lock.tryLock(2, TimeUnit.SECONDS);
            Assert.state(locked, "正在创建付款码，请稍后重试");
            PayableRecord source = loadPayableRecord(paymentType, orderNo, userId);
            int totalFen = exactFen(source.amount());
            validateMerchantConfig();
            PaymentInfo existing = paymentInfoService.getOne(new LambdaQueryWrapper<PaymentInfo>()
                    .eq(PaymentInfo::getOrderNo, orderNo));
            if (existing != null) validateLocalPayment(existing, source, paymentType, orderNo, userId);
            PaymentInfo payment = paymentInfoService.savePaymentInfo(paymentType, orderNo);
            validateLocalPayment(payment, source, paymentType, orderNo, userId);
            // 保存支付记录可能触发远端读取；再次核对当前状态，缩小取消订单与预下单之间的竞态窗口。
            PayableRecord latest = loadPayableRecord(paymentType, orderNo, userId);
            Assert.isTrue(source.amount().compareTo(latest.amount()) == 0, "订单金额已变化，请重新确认");
            com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest request =
                    new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
            com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                    new com.wechat.pay.java.service.payments.nativepay.model.Amount();
            amount.setTotal(totalFen);
            amount.setCurrency("CNY");
            request.setAmount(amount);
            request.setAppid(wxPayV3Config.getAppid());
            request.setMchid(wxPayV3Config.getMerchantId());
            request.setDescription(latest.content());
            request.setOutTradeNo(orderNo);
            request.setNotifyUrl(wxPayV3Config.getNotifyUrl());
            if (latest.expiresAt() != null) {
                Assert.isTrue(latest.expiresAt().isAfter(clock.instant().plusSeconds(60)),
                        "订单剩余支付时间不足一分钟，请重新下单");
                request.setTimeExpire(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(latest.expiresAt().atOffset(ZoneOffset.UTC)));
            }
            // 仅此 SDK 边界会创建真实微信交易；单元测试必须替换为 mock。
            com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse response = wechatPayClient.prepay(request);
            Assert.notNull(response, "微信未返回付款码，支付服务暂不可用");
            String codeUrl = response.getCodeUrl();
            Assert.isTrue(isNativeCodeUrl(codeUrl), "微信未返回有效付款码，支付服务暂不可用");
            return Map.of("codeUrl", codeUrl);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("支付请求被中断，请重试", exception);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private PayableRecord loadPayableRecord(String paymentType, String orderNo, Long userId) {
        if (SystemConstant.PAYMENT_TYPE_ORDER.equals(paymentType)) {
            OrderInfo order = checkedData(orderInfoFeignClient.getOrderInfo(orderNo), "订单服务暂不可用或订单不存在");
            Assert.isTrue(orderNo.equals(order.getOrderNo()) && userId.equals(order.getUserId()), "无权支付该订单");
            Assert.isTrue(SystemConstant.ORDER_STATUS_UNPAID.equals(order.getOrderStatus()), "订单不是待支付状态");
            Assert.isTrue(SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(order.getPayWay()), "订单不支持微信支付");
            Assert.hasText(order.getOrderTitle(), "订单商品信息不完整");
            Date created = order.getCreateTime();
            Assert.notNull(created, "订单创建时间缺失，不能确认可支付期限");
            Instant expires = created.toInstant().plus(Duration.ofMinutes(30));
            Assert.isTrue(!created.toInstant().isAfter(clock.instant()) && expires.isAfter(clock.instant()), "订单已过可支付期限");
            exactFen(order.getOrderAmount());
            return new PayableRecord(order.getOrderAmount(), order.getOrderTitle(), expires);
        }
        RechargeInfo recharge = checkedData(rechargeInfoFeignClient.getRechargeInfo(orderNo), "充值服务暂不可用或充值记录不存在");
        Assert.isTrue(orderNo.equals(recharge.getOrderNo()) && userId.equals(recharge.getUserId()), "无权支付该充值记录");
        Assert.isTrue(SystemConstant.ORDER_STATUS_UNPAID.equals(recharge.getRechargeStatus()), "充值记录不是待支付状态");
        Assert.isTrue(SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(recharge.getPayWay()), "充值记录不支持微信支付");
        exactFen(recharge.getRechargeAmount());
        // 当前充值服务没有定义失效时间，不将前端 3 分钟展示期限伪装成交易关闭期限。
        return new PayableRecord(recharge.getRechargeAmount(), "充值", null);
    }

    private static <T> T checkedData(Result<T> result, String message) {
        Assert.isTrue(result != null && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode()) && result.getData() != null, message);
        return result.getData();
    }

    private static void validateIdentity(String orderNo, Long userId) {
        Assert.hasText(orderNo, "订单号不能为空");
        Assert.isTrue(orderNo.length() <= 128 && orderNo.equals(orderNo.trim()), "订单号格式不正确");
        Assert.isTrue(userId != null && userId > 0, "请先登录");
    }

    private static void validatePaymentType(String paymentType) {
        Assert.isTrue(SystemConstant.PAYMENT_TYPE_ORDER.equals(paymentType)
                || SystemConstant.PAYMENT_TYPE_RECHARGE.equals(paymentType), "不支持的支付类型");
    }

    private static int exactFen(BigDecimal amount) {
        Assert.isTrue(amount != null && amount.signum() > 0, "支付金额必须大于零");
        try {
            return amount.movePointRight(2).intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("支付金额精度不正确或超出微信支付范围", exception);
        }
    }

    private static void validateLocalPayment(PaymentInfo payment, PayableRecord source,
                                              String paymentType, String orderNo, Long userId) {
        Assert.notNull(payment, "支付记录不存在");
        Assert.isTrue(orderNo.equals(payment.getOrderNo()) && userId.equals(payment.getUserId())
                && paymentType.equals(payment.getPaymentType()), "本地支付记录与当前订单不一致");
        Assert.isTrue(SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payment.getPayWay()), "本地记录不是微信支付");
        Assert.isTrue(SystemConstant.PAYMENT_STATUS_UNPAID.equals(payment.getPaymentStatus()), "本地支付记录不是待支付状态");
        exactFen(payment.getAmount());
        Assert.isTrue(source.amount().compareTo(payment.getAmount()) == 0, "本地支付记录金额与订单不一致");
    }

    private void validateMerchantConfig() {
        Assert.hasText(wxPayV3Config.getAppid(), "微信支付未配置应用，暂不可用");
        Assert.hasText(wxPayV3Config.getMerchantId(), "微信支付未配置商户号，暂不可用");
        Assert.hasText(wxPayV3Config.getNotifyUrl(), "微信支付未配置回调地址，暂不可用");
    }

    private static boolean isNativeCodeUrl(String value) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim())) return false;
        try {
            URI uri = URI.create(value);
            return "weixin".equalsIgnoreCase(uri.getScheme()) && "wxpay".equalsIgnoreCase(uri.getHost())
                    && "/bizpayurl".equals(uri.getPath()) && StringUtils.hasText(uri.getRawQuery())
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record PayableRecord(BigDecimal amount, String content, Instant expiresAt) { }

	//发起微信支付请求
	@Override
	public Map createJsapi(String paymentType, String orderNo, Long userId) {
        validateIdentity(orderNo, userId);
        validatePaymentType(paymentType);
        RLock lock = redissonClient.getLock("payment:wechat:create:" + orderNo);
        boolean locked = false;
        try {
            locked = lock.tryLock(2, TimeUnit.SECONDS);
            Assert.state(locked, "正在创建支付，请稍后重试");
            return createJsapiUnderLock(paymentType, orderNo, userId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("支付请求被中断，请重试", exception);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private Map createJsapiUnderLock(String paymentType, String orderNo, Long userId) {
        // 两个微信入口共用归属、金额和可付状态校验，避免从旧入口绕过 Native 限制。
        PayableRecord source = loadPayableRecord(paymentType, orderNo, userId);
        validateMerchantConfig();
		PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(paymentType, orderNo);
        validateLocalPayment(paymentInfo, source, paymentType, orderNo, userId);

		//3 发起微信支付请求
		// JsapiServiceExtension对象
		JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
		//创建request对象，设置微信支付需要参数
		PrepayRequest request = new PrepayRequest();
		// 注意在这个包下 com.wechat.pay.java.service.payments.jsapi.model.Amount
		//【修复#5】使用支付记录的真实金额（单位：分），替代原来的硬编码 1 分钱；
		// 回调侧 updatePaymentStatus 会校验微信通知的金额与此处下单金额一致，两处必须同源
		Amount amount = new Amount();
		amount.setTotal(exactFen(paymentInfo.getAmount()));
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
            Assert.hasText(openid, "当前账号尚未绑定微信，请使用 Native 扫码支付");
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
