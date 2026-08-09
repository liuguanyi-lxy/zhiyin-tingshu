package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@Tag(name = "充值管理")
@RestController
@RequestMapping("api/account/rechargeInfo")
@SuppressWarnings({"all"})
public class RechargeInfoApiController {

	@Autowired
	private RechargeInfoService rechargeInfoService;

	/**
	 * 给用户充值
	 * @param rechargeInfoVo
	 * @return
	 */
	@LoginVerification
	@Operation(summary = "余额充值,第一步向account微服务添加账户充值记录，状态为未支付,然后再调用微信支付")
	@PostMapping("submitRecharge")
	public Result submitRecharge(@RequestBody RechargeInfoVo rechargeInfoVo){
		// 获取到用户Id
		Long userId = AuthContextHolder.getUserId();
		// 调用充值方法
		String orderNo = this.rechargeInfoService.submitRecharge(rechargeInfoVo,userId);
		// 创建map 集合对象
		HashMap<String, Object> map = new HashMap<>();
		// 存储订单Id
		map.put("orderNo",orderNo);
		// 返回数据
		return Result.ok(map);
	}

	//根据充值记录编号获取充值记录
	@Operation(summary = "根据充值记录编号获取充值记录")
	@GetMapping("getRechargeInfo/{rechargeNo}")
	public Result<RechargeInfo> getRechargeInfo(@PathVariable String rechargeNo){
		RechargeInfo rechargeInfo = rechargeInfoService.getRechargeInfo(rechargeNo);
		return Result.ok(rechargeInfo);
	}
}

