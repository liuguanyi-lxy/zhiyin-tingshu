package com.atguigu.tingshu.model;

import lombok.Data;

import javax.persistence.Column;

/**
 * CDCEntity类 - 映射数据库实体类，数据库中数据修改，通过binlog日志将数据传输到canal，使用该实体类进行封装

 * 用于映射数据库表结构，使用JPA注解进行ORM映射
 *
 * @author: lxy    // 作者信息
 * @create: 2026-7-9 10:53    // 创建日期和时间
 */
@Data    // Lombok注解，自动生成getter、setter、toString等方法
public class CDCEntity {    // CDC实体类，可能用于变更数据捕获(Change Data Capture)
    // 注意Column 注解必须是persistence包下的    // 提示开发者注意Column注解的包路径
    @Column(name = "id")    // JPA注解，指定数据库列名为"id"
    private Long id;    // 实体类属性，对应数据库表的id字段，类型为Long
}
