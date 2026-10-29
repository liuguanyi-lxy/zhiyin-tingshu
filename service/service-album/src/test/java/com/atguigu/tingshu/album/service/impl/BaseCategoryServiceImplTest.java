package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.BaseAttributeMapper;
import com.atguigu.tingshu.album.mapper.BaseCategory1Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategory2Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategory3Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategoryViewMapper;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategory2;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类业务单元测试（纯 Mockito）。
 * 覆盖：一二三级分类嵌套结构组装、置顶三级分类、分类视图查询、属性查询。
 */
@ExtendWith(MockitoExtension.class)
class BaseCategoryServiceImplTest {

    @Mock
    private BaseCategory1Mapper baseCategory1Mapper;
    @Mock
    private BaseCategory2Mapper baseCategory2Mapper;
    @Mock
    private BaseCategory3Mapper baseCategory3Mapper;
    @Mock
    private BaseCategoryViewMapper baseCategoryViewMapper;
    @Mock
    private BaseAttributeMapper baseAttributeMapper;
    @InjectMocks
    private BaseCategoryServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BaseCategoryView.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BaseCategory2.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BaseCategory3.class);
    }

    private BaseCategoryView view(long c1, String c1Name, long c2, String c2Name, long c3, String c3Name) {
        BaseCategoryView v = new BaseCategoryView();
        v.setCategory1Id(c1);
        v.setCategory1Name(c1Name);
        v.setCategory2Id(c2);
        v.setCategory2Name(c2Name);
        v.setCategory3Id(c3);
        v.setCategory3Name(c3Name);
        return v;
    }

    @Test
    void findAllCategory1_delegatesToMapper() {
        BaseCategory1 c1 = new BaseCategory1();
        when(baseCategory1Mapper.selectList(any())).thenReturn(Collections.singletonList(c1));

        List<BaseCategory1> result = service.findAllCategory1();

        assertEquals(1, result.size());
    }

    @Test
    void getBaseCategoryListByCategory1Id_buildsNestedStructure() {
        when(baseCategoryViewMapper.selectList(any())).thenReturn(Arrays.asList(
                view(1, "音乐", 11, "音乐音效", 111, "运动音乐"),
                view(1, "音乐", 11, "音乐音效", 112, "古典音乐"),
                view(1, "音乐", 12, "有声书", 121, "小说")));

        JSONObject result = service.getBaseCategoryListByCategory1Id(1L);

        assertEquals(1L, result.getLongValue("categoryId"));
        assertEquals("音乐", result.getString("categoryName"));
        JSONArray category2Child = result.getJSONArray("categoryChild");
        assertEquals(2, category2Child.size());
        JSONObject firstCategory2 = category2Child.getJSONObject(0);
        assertEquals(11L, firstCategory2.getLongValue("categoryId"));
        assertEquals("音乐音效", firstCategory2.getString("categoryName"));
        JSONArray category3Child = firstCategory2.getJSONArray("categoryChild");
        assertEquals(2, category3Child.size());
        assertEquals("运动音乐", category3Child.getJSONObject(0).getString("categoryName"));
    }

    @Test
    void selectTopBaseCategory3_queriesTopUnderCategory1() {
        BaseCategory2 c2 = new BaseCategory2();
        c2.setId(11L);
        when(baseCategory2Mapper.selectList(any())).thenReturn(Collections.singletonList(c2));
        BaseCategory3 top = new BaseCategory3();
        top.setId(111L);
        top.setIsTop(1);
        when(baseCategory3Mapper.selectList(any())).thenReturn(Collections.singletonList(top));

        List<BaseCategory3> result = service.selectTopBaseCategory3(1L);

        assertEquals(1, result.size());
        assertEquals(111L, result.get(0).getId());
    }

    @Test
    void getCategoryView_selectsById() {
        BaseCategoryView v = view(1, "音乐", 11, "音乐音效", 111, "运动音乐");
        when(baseCategoryViewMapper.selectById(111L)).thenReturn(v);

        assertSame(v, service.getCategoryView(111L));
    }

    @Test
    void getBaseCategoryList_buildsFullTreeForMultipleRoots() {
        when(baseCategoryViewMapper.selectList(any())).thenReturn(Arrays.asList(
                view(1, "音乐", 11, "音乐音效", 111, "运动音乐"),
                view(2, "军事", 21, "军事历史", 211, "抗战")));

        List<JSONObject> result = service.getBaseCategoryList();

        assertEquals(2, result.size());
        JSONObject music = result.get(0);
        assertEquals("音乐", music.getString("categoryName"));
        assertEquals(1, music.getJSONArray("categoryChild").size());
        assertEquals("运动音乐",
                music.getJSONArray("categoryChild").getJSONObject(0)
                        .getJSONArray("categoryChild").getJSONObject(0)
                        .getString("categoryName"));
    }

    @Test
    void findAttribute_delegatesToMapper() {
        BaseAttribute attribute = new BaseAttribute();
        when(baseAttributeMapper.selectAttribute(1L)).thenReturn(Collections.singletonList(attribute));

        List<BaseAttribute> result = service.findAttribute(1L);

        assertEquals(1, result.size());
        verify(baseAttributeMapper).selectAttribute(1L);
    }
}
