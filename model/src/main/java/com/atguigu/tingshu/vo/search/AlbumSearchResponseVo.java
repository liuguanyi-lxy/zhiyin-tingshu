package com.atguigu.tingshu.vo.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "专辑检索响应,将检索的结果统一封装到一个实体类便于显示检索内容")
public class AlbumSearchResponseVo implements Serializable {
    //检索出来的专辑信息列表
    private List<AlbumInfoIndexVo> list = new ArrayList<>();
    private Long total;//总记录数
    private Integer pageSize;//每页显示的内容
    private Integer pageNo;//当前页面
    private Long totalPages;//总页面
}
