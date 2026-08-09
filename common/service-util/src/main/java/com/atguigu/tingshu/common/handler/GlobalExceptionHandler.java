package com.atguigu.tingshu.common.handler;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 全局异常处理类,自定义的异常被全局异常拦截器拦截，将自定义的错误码和错误信息传递给前端，前端在根据此信息做出对应响应
 * 使用@ControllerAdvice注解实现全局异常处理
 * 通过@ExceptionHandler注解指定不同异常的处理方法
 */
@Slf4j  // 使用Lombok提供的日志注解
@ControllerAdvice  // 控制器增强，用于全局异常处理
public class GlobalExceptionHandler {

    /**
     * 处理所有Exception类型的异常
     * @param e 捕获的异常对象
     * @return 返回统一的错误结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e){
        e.printStackTrace();  // 打印异常堆栈信息
        return Result.fail();  // 返回失败结果
    }

    /**
     * 自定义异常处理方法
     * @param e 自定义异常对象
     * @return 返回包含错误码和错误信息的统一结果
     */
    @ExceptionHandler(GuiguException.class)
    @ResponseBody
    public Result error(GuiguException e){
        log.error("触发异常拦截: " + e.getMessage(), e);  // 记录错误日志
        return Result.build(null,e.getCode(), e.getMessage());  // 使用自定义异常的错误码和信息
    }

    /**
     * 处理非法参数异常
     * @param e 非法参数异常对象
     * @return 返回参数验证错误结果
     */
    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseBody
    public Result llegalArgumentException(Exception e) {
        return Result.build(null, ResultCodeEnum.ARGUMENT_VALID_ERROR);  // 返回参数验证错误码
    }

    /**
     * spring security异常
     * @param e
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public Result error(AccessDeniedException e) throws AccessDeniedException {
        return Result.build(null, ResultCodeEnum.PERMISSION);
    }

/**
 * 处理数据绑定异常的方法
 * 当请求参数绑定失败时，会调用此方法进行处理
 * @param exception 绑定异常对象，包含详细的字段错误信息
 * @return 返回一个包含错误信息的Result对象
 */
    @ExceptionHandler(value = BindException.class)  // 指定处理BindException类型的异常
    @ResponseBody  // 将返回的结果直接转换为JSON格式响应给前端
    public Result error(BindException exception) {
    // 获取绑定结果对象，包含所有字段的验证错误
        BindingResult result = exception.getBindingResult();
    // 创建一个Map用于存储字段名和对应的错误信息
        Map<String, Object> errorMap = new HashMap<>();
    // 获取所有字段错误列表
        List<FieldError> fieldErrors = result.getFieldErrors();
    // 遍历所有字段错误，将字段名和错误信息存入Map中
        fieldErrors.forEach(error -> {
        // 记录错误日志，包含字段名和错误信息
            log.error("field: " + error.getField() + ", msg:" + error.getDefaultMessage());
        // 将字段名和错误信息存入Map
            errorMap.put(error.getField(), error.getDefaultMessage());
        });
    // 返回一个Result对象，包含错误信息和参数验证失败的错误码
        return Result.build(errorMap, ResultCodeEnum.ARGUMENT_VALID_ERROR);
    }

/**
 * 全局异常处理类，处理方法参数校验失败异常
 * 当接口请求参数不满足校验规则时，会捕获此异常并返回统一格式的错误信息
 *
 * @param exception 方法参数校验异常对象，包含详细的校验错误信息
 * @return 返回统一的错误结果对象，包含错误字段和对应的具体错误信息
 */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseBody
    public Result error(MethodArgumentNotValidException exception) {
    // 获取校验结果对象，包含所有字段的校验错误信息
        BindingResult result = exception.getBindingResult();
    // 创建Map用于存储字段名和对应的错误信息
        Map<String, Object> errorMap = new HashMap<>();
    // 获取所有字段错误信息的列表
        List<FieldError> fieldErrors = result.getFieldErrors();
    // 遍历所有字段错误信息，记录日志并填充到错误Map中
        fieldErrors.forEach(error -> {
            log.error("field: " + error.getField() + ", msg:" + error.getDefaultMessage());
            errorMap.put(error.getField(), error.getDefaultMessage());
        });
    // 返回统一的错误结果，包含错误字段映射和错误码
        return Result.build(errorMap, ResultCodeEnum.ARGUMENT_VALID_ERROR);
    }


}
