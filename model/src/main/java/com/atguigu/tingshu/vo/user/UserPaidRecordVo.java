package com.atguigu.tingshu.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户购买记录")
public class UserPaidRecordVo {
    @Schema(description = "订单号")
    private String orderNo;
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "订单类型，专辑、vip、声音")
    private String itemType;
    @Schema(description = "商品id列表")
    private List<Long> itemIdList;
}
