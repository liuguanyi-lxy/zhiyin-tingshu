package com.atguigu.tingshu.order.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "订单管理")
@RestController
@RequestMapping("api/order/orderInfo")
@SuppressWarnings({"all"})
public class OrderInfoApiController {

	@Autowired
	private OrderInfoService orderInfoService;
	/**
     * 根据购买界面给的选购信息，构建结算界面的订单明细，最后返回订单信息给支付界面
     * @param tradeVo
     * @return
     */
	@LoginVerification
	@Operation(summary = "根据购买界面给的选购信息，构建结算界面的订单明细，最后返回订单信息给支付界面")
	@PostMapping("trade")
	public Result<OrderInfoVo> trade(@RequestBody @Validated TradeVo tradeVo) {
		Long userId = AuthContextHolder.getUserId();
		OrderInfoVo orderInfoVo = orderInfoService.trade(tradeVo,userId);
		return Result.ok(orderInfoVo);
	}
	/**
	 * //立即结算后进入支付界面选择支付方式后生成订单
	 * @param orderInfoVo
	 * @return
	 */
	@LoginVerification
	@Operation(summary = "立即结算后进入支付界面选择支付方式后生成订单")
	@PostMapping("submitOrder")
	public Result<Map<String, Object>> submitOrder(@RequestBody @Validated OrderInfoVo orderInfoVo) {
		//  获取到用户Id
		Long userId = AuthContextHolder.getUserId();
		//  调用服务层方法
		String orderNo = orderInfoService.submitOrder(orderInfoVo, userId);
		Map<String, Object> map = new HashMap();
		map.put("orderNo", orderNo);
		//	返回数据
		return Result.ok(map);
	}
	/**
	 * 支付成功后根据返回的订单编号查看我的订单
	 * @param orderNo
	 * @return
	 */
	@LoginVerification
	@Operation(summary = "支付成功后根据返回的订单编号查看我的订单")
	@GetMapping("getOrderInfo/{orderNo}")
	public Result<OrderInfo> getOrderInfo(@PathVariable String orderNo) {
		OrderInfo orderInfo = orderInfoService.getOrderInfoByOrderNo(orderNo);
		return Result.ok(orderInfo);
	}

	@LoginVerification
	@Operation(summary = "进入用户主页获取用户订单")
	@GetMapping("findUserPage/{page}/{limit}")
	public Result index(@PathVariable Long page,
						@PathVariable Long limit) {
		// 获取到用户Id
		Long userId = AuthContextHolder.getUserId();
		Page<OrderInfo> pageParam = new Page<>(page, limit);
		// 调用服务层方法
		IPage<OrderInfo> pageModel = orderInfoService.findUserPage(pageParam, userId);
		return Result.ok(pageModel);
	}
}

