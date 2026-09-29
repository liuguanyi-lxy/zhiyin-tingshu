package com.atguigu.tingshu.handler;

import com.atguigu.tingshu.model.CDCEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

/**
 * @author: lxy
 * @create: 2026-7-9 10:53
 */
@Slf4j
@Component
@CanalTable("user_info") ////监听变更表
public class UserCdcHandler implements EntryHandler<CDCEntity> {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 监听到数据修改
     *
     * @param before 变更前数据
     * @param after  变更后数据
     */
    @Override
    public void update(CDCEntity before, CDCEntity after) {
        log.info("监听到数据修改,ID:{}", after.getId());
        // 与 UserInfoServiceImpl#getUserInfoVoByUserId 的 @RedisCache(prefix="userInfoVo:") key
        // 拼接规则保持一致：prefix + Arrays.toString(args) = "userInfoVo:[id]"
        String key = "userInfoVo:[" + after.getId() + "]";
        redisTemplate.delete(key);

    }
    /**
     * 监听到删除操作
     *
     * @param cdcEntity 删除前数据
     */
    @Override
    public void delete(CDCEntity cdcEntity) {
        log.info("监听到数据删除,ID:{}", cdcEntity.getId());
        // key 格式与 update 侧一致：userInfoVo:[id]
        String key = "userInfoVo:[" + cdcEntity.getId() + "]";
        redisTemplate.delete(key);
    }
}
