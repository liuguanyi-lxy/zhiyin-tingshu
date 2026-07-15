package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.service.VipServiceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "VIP服务配置管理接口")
@RestController
@RequestMapping("api/user/vipServiceConfig")
@SuppressWarnings({"all"})
public class VipServiceConfigApiController {

	@Autowired
	private VipServiceConfigService vipServiceConfigService;
	@Operation(summary = "获取购买VIP各种套餐的信息")
	@GetMapping("findAll")
	public Result<List<VipServiceConfig>> findAll(){
		//	调用服务层方法
		List<VipServiceConfig> list = this.vipServiceConfigService.list();
		return Result.ok(list);
	}
	/**
	 * 根据itemId获取VIP服务配置信息
	 * @param id
	 * @return
	 */
	@Operation(summary = "根据ItemId获取VIP服务配置信息")
	@GetMapping("getVipServiceConfig/{id}")
	public Result<VipServiceConfig> getVipServiceConfig(@PathVariable Long id) {
		return Result.ok(vipServiceConfigService.getById(id));
	}
}

