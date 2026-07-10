package com.atguigu.tingshu.common.util;

import lombok.Getter;

/**
 * MongoDB工具类
 * 提供MongoDB集合名称生成等功能
 */
public class MongoUtil {

    /**
     * MongoDB集合枚举
     * 定义了系统中使用的MongoDB集合及其分区信息
     */
    @Getter
    public enum MongoCollectionEnum {

        // 用户订阅集合
        USER_SUBSCRIBE(100,"userSubscribe"),
        // 用户收藏集合
        USER_COLLECT(100,"userCollect"),
        // 用户听歌进度集合
        USER_LISTEN_PROCESS(100,"userListenProcess"),
        // 评论集合
        COMMENT(100,"comment"),
        // 评论点赞集合
        COMMENT_PRAISE(100,"commentPraise"),
        ;

        // 分区数
        private Integer partition;
        // 集合前缀
        private String collectionPrefix;

        /**
         * 构造方法
         * @param partition 分区数
         * @param collectionPrefix 集合前缀
         */
        MongoCollectionEnum(Integer partition, String collectionPrefix) {
            this.partition = partition;
            this.collectionPrefix = collectionPrefix;
        }

    }


    /**
     * 获取mongo集合名
     * @param mongoCollection Collection前缀
     * @param route 路由
     * @return
     */
    public static String getCollectionName(MongoCollectionEnum mongoCollection, Long route) {
        return mongoCollection.getCollectionPrefix() + "_" + route % mongoCollection.getPartition();
    }
}

