package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MongoDB 集合名分片路由单元测试：集合名 = 前缀_userId%分区数。
 */
class MongoUtilTest {

    @Test
    void getCollectionName_routesByModulo() {
        assertEquals("userListenProcess_9",
                MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, 1009L));
        assertEquals("userListenProcess_0",
                MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, 100L));
        assertEquals("comment_1",
                MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.COMMENT, 101L));
    }

    @Test
    void partitionSize_is100() {
        assertEquals(100, MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS.getPartition());
        assertEquals("userSubscribe", MongoUtil.MongoCollectionEnum.USER_SUBSCRIBE.getCollectionPrefix());
    }
}
