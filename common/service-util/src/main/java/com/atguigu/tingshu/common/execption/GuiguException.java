package com.atguigu.tingshu.common.execption;

import com.atguigu.tingshu.common.result.ResultCodeEnum;
import lombok.Data;

/**
 * 自定义全局异常类,自定义的异常被全局异常拦截器拦截，将自定义的错误码和错误信息传递给前端，前端在根据此信息做出对应响应
 *
 */
@Data
public class GuiguException extends RuntimeException {

    private Integer code;

    private String message;

    /**
     * 通过状态码和错误消息创建异常对象
     * @param code
     * @param message
     */
    public GuiguException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 接收枚举类型对象
     * @param resultCodeEnum
     */
    public GuiguException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
    }

    @Override
    public String toString() {
        return "GuliException{" +
                "code=" + code +
                ", message=" + this.getMessage() +
                '}';
    }
}
