package com.atguigu.tingshu.common.login;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//元注解：定义注解特性的注解 @Target @Retention
@Target({ElementType.METHOD})//设置注解可以使用在什么地方，比如类上面，方法上面，属性上面
@Retention(RetentionPolicy.RUNTIME) //在什么时候生效
public @interface LoginVerification {
    //属性定义格式
    //类型   属性名称() 默认值
    //String value() default "";

    /**
     * 被加上该注解的方法是否必须完成对应的增强,此注解中就是否必须登录
     * @return
     */
    boolean required() default true;
}
