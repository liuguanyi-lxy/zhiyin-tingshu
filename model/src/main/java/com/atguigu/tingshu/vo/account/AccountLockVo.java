package com.atguigu.tingshu.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "传递给account微服务判断余额扣减余额以及添加账号购买记录")
public class AccountLockVo {

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "用户id")
	private Long userId;

	@Schema(description = "订单金额")
	private BigDecimal amount;

	@Schema(description = "订单内容")
	private String content;

}