package com.atguigu.tingshu.common.constant;

/** 内部扣款接口约定；普通用户 token 不能替代服务凭证。 */
public final class AccountInternalConstant {
    public static final String DEDUCT_PATH = "/api/account/userAccount/checkAndDeduct";
    public static final String HEADER = "X-Account-Internal-Token";
    private AccountInternalConstant() { }
}
