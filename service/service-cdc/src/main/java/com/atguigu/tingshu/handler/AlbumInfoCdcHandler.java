package com.atguigu.tingshu.handler;

import com.atguigu.tingshu.model.CDCEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;
import com.atguigu.tingshu.common.constant.RedisConstant;
/**
 * @author: lxy
 * @create: 2026-7-9 10:53
 */
@Slf4j
@Component
@CanalTable("album_info") ////监听mysql数据库变更表
public class AlbumInfoCdcHandler implements EntryHandler<CDCEntity> {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 监听到数据修改
     * 删除缓存后，下次请求会从数据库重新加载数据，然后更新数据到Redis中，确保数据是最新的，与数据库数据一致
     * @param before 变更前数据
     * @param after  变更后数据
     */
    @Override
    public void update(CDCEntity before, CDCEntity after) {
        log.info("监听到数据修改,ID:{}", after.getId());
        // 与 RedisCacheAspect 的 key 拼接规则保持一致：prefix + Arrays.toString(args) = "album:info:[id]"
        String key = RedisConstant.ALBUM_INFO_PREFIX + "[" + after.getId() + "]";
        redisTemplate.delete(key);
    }

    /**
     * 监听到删除操作
     * 该方法用于处理CDCEntity实体的删除事件，当监听到删除操作时，

 * 会从Redis中删除对应的专辑信息缓存
 *
     * @param cdcEntity 删除前数据，包含被删除实体的信息
     */
    @Override
    public void delete(CDCEntity cdcEntity) {
    // 记录删除操作的日志，输出被删除实体的ID
        log.info("监听到数据删除,ID:{}", cdcEntity.getId());
    // 构造Redis中对应的缓存键，格式为"album:info:[实体ID]"，与 RedisCacheAspect 拼接规则一致
        String key = RedisConstant.ALBUM_INFO_PREFIX + "[" + cdcEntity.getId() + "]";
    // 从Redis中删除对应的缓存数据
        redisTemplate.delete(key);
    }
}
