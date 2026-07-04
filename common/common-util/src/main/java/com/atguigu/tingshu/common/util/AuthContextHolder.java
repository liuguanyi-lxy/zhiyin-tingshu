package com.atguigu.tingshu.common.util;

/**
 * 获取当前用户信息帮助类
 * 在多线程环境下，安全地存储和获取当前执行线程的用户认证信息（如用户ID和用户名）。
 * 一、 实现原理：ThreadLocal 机制
 * 该类的核心是使用了 Java 的 ThreadLocal 类。
 *
 * ThreadLocal 的作用：ThreadLocal 提供了线程局部变量。简单来说，就是为每一个使用该变量的线程都单独提供一份变量的副本。每个线程只能读写自己那份副本，线程之间互不干扰。
 * 代码中的体现：
 * private static ThreadLocal<Long> userId = new ThreadLocal<Long>();：声明了一个静态的、存储 Long 类型值的 ThreadLocal 实例。
 * set 方法：将值存入当前线程的专属存储空间。
 * get 方法：从当前线程的专属存储空间取出值。
 * remove 方法：清除当前线程的专属存储空间中的值。
 */
public class AuthContextHolder {

    private static ThreadLocal<Long> userId = new ThreadLocal<Long>();
    private static ThreadLocal<String> username = new ThreadLocal<String>();

    public static void setUserId(Long _userId) {
        userId.set(_userId);
    }

    public static Long getUserId() {
        return userId.get();
    }

    public static void removeUserId() {
        userId.remove();
    }

    public static void setUsername(String _username) {
        username.set(_username);
    }

    public static String getUsername() {
        return username.get();
    }

    public static void removeUsername() {
        username.remove();
    }

}
