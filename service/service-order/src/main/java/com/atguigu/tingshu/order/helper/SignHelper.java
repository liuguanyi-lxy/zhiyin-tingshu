package com.atguigu.tingshu.order.helper;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.MD5;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Schema(description="签名工具类,校验传入的map中的明文数据和生成明文数据加密后的签名")
public class SignHelper {

    /**
     * 签名密钥。
     * 启动时由 OrderSignKeyConfig 从配置中心注入。未配置时保持空值并拒绝签名，
     * 避免使用任何仓库内可预测的默认密钥。
     */
    public static final int MIN_SIGN_KEY_LENGTH = 32;
    private static String signKey = "";

    /** 供配置类注入真实密钥；测试中也可显式设置 */
    public static void setSignKey(String key) {
        if (!StringUtils.hasText(key) || key.length() < MIN_SIGN_KEY_LENGTH) {
            throw new IllegalArgumentException("Order signing key must contain at least 32 characters");
        }
        signKey = key;
    }

    static String currentSignKey() {
        return signKey;
    }
    /**
     * 验签方法
     * @param parameterMap
     */
    public static void checkSign(Map<String, Object> parameterMap){
        //校验签名时间
        //获取订单信息的时间戳
        Long remoteTimestamp = (Long)parameterMap.get("timestamp");
        log.info("传入的订单信息的时间戳：" + remoteTimestamp);
        if(StringUtils.isEmpty(remoteTimestamp)){
            throw new GuiguException(ResultCodeEnum.SIGN_ERROR);
        }
        long currentTimestamp = getTimestamp();
        if (Math.abs(currentTimestamp - remoteTimestamp) > 500000) {
            log.error("签名已过期，服务器当前时间:{}", currentTimestamp);
            throw new GuiguException(ResultCodeEnum.SIGN_OVERDUE);
        }

        //获取传入的加密后的签名
        String signRemote = (String)parameterMap.get("sign");
        log.info("传入的加密后的签名：" + signRemote);
        log.info("传入的订单信息：" + parameterMap);
        //将传入的明文数据进行加密
        String signLocal = getSign(parameterMap);
        log.info("本地加密后的签名：" + signLocal);
        //传入的签名为null则报错
        if(StringUtils.isEmpty(signRemote)){
            throw new GuiguException(ResultCodeEnum.SIGN_ERROR);
        }
        //比较传入的加密后的签名和本地加密后的签名是否一致
        if(!signRemote.equals(signLocal)){
            throw new GuiguException(ResultCodeEnum.SIGN_ERROR);
        }
    }

    /**
     * 请求数据获取签名
     * @param parameterMap
     * @return
     */
    public static String getSign(Map<String, Object> parameterMap) {
        if (!StringUtils.hasText(signKey) || signKey.length() < MIN_SIGN_KEY_LENGTH) {
            throw new IllegalStateException("Order signing key is not configured");
        }
        //去掉明文数据的sign参数
        if(parameterMap.containsKey("sign")) {
            parameterMap.remove("sign");
        }

        //有序
        TreeMap<String, Object> sorted = new TreeMap<>(parameterMap);
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, Object> param : sorted.entrySet()) {
            //获取键值对中的值
            str.append(param.getValue()).append("|");
        }
        //最后连接signKey
        str.append(signKey);
        log.info("加密前：" + str.toString());
        String md5Str = MD5.encrypt(str.toString());//不可逆加密算法
        log.info("加密后：" + md5Str);
        return md5Str;
    }

    /**
     * 获取时间戳
     * @return
     */
    public static long getTimestamp() {
        return new Date().getTime();
    }

}
