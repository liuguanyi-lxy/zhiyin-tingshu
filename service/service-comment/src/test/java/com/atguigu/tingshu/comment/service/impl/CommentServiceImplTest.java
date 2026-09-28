package com.atguigu.tingshu.comment.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 评论服务冒烟测试。
 * CommentServiceImpl 目前是空壳（空接口 + 仅注入 MongoTemplate，无业务方法），
 * 此处锁定其依赖注入点：未来补齐评论业务时 MongoTemplate 必须可注入（测试中以 mock 替代，不连真实 MongoDB）。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private CommentServiceImpl service;

    @Test
    void mongoTemplateInjectionPointExists() throws Exception {
        Field field = CommentServiceImpl.class.getDeclaredField("mongoTemplate");
        field.setAccessible(true);
        assertEquals(mongoTemplate, field.get(service));
    }

    @Test
    void instantiationWithoutContainer_isSafe() {
        assertDoesNotThrow(CommentServiceImpl::new);
    }
}
