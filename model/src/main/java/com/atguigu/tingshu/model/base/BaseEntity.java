package com.atguigu.tingshu.model.base;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    //还需要创建对应的拦截器，拦截器里面会设置创建时间和修改时间的方法
    @TableField(value = "create_time",fill = FieldFill.INSERT)
    private Date createTime;

    @JsonIgnore
    //字段被修改时候，该字段才会被赋值
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    private Date updateTime;

    @JsonIgnore
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;

    @JsonIgnore
    @TableField(exist = false)
    private Map<String,Object> param = new HashMap<>();
}
