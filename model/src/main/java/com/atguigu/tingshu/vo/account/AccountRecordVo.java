package com.atguigu.tingshu.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "当前用户资金记录，不包含其他用户或账户内部字段")
public class AccountRecordVo {

    private Long id;

    @Schema(description = "消费商品标题或按真实支付方式生成的充值说明")
    private String title;

    @Schema(description = "原记录创建时间；充值记录不代表支付或到账时间")
    private Date createTime;

    @Schema(description = "记录金额，单位元；充值/消费方向由请求的记录类型区分")
    private BigDecimal amount;
}
