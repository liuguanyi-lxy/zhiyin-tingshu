package com.atguigu.tingshu.user.intercepter;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;
//这段代码是一个实现了 MyBatis-Plus 的 MetaObjectHandler 接口的类，主要用于在数据库插入和更新操作时自动填充某些字段
//insertFill 方法在执行插入操作时被调用，用于填充插入时需要的字段
//updateFill 方法在执行更新操作时被调用，用于填充更新时需要的字段
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        // 插入时自动填充创建时间
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时自动填充逻辑
    }
}
