package com.atguigu.tingshu.dispatch.job;

import com.atguigu.tingshu.common.util.ExceptionUtil;
import com.atguigu.tingshu.dispatch.mapper.XxlJobLogMapper;
import com.atguigu.tingshu.model.dispatch.XxlJobLog;
import com.atguigu.tingshu.search.client.SearchFeignClient;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 调度任务处理器类
 * 使用@Slf4j注解提供日志功能
 * 使用@Component注解将类注册为Spring组件
 */
@Slf4j
@Component
public class DispatchJobHandler {
    // 自动注入SearchFeignClient，用于远程调用服务
    @Autowired
    private SearchFeignClient searchFeignClient;
    // 自动注入XxlJobLogMapper，用于操作任务日志表
    @Autowired
    private XxlJobLogMapper xxlJobLogMapper;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    //更新排行榜
    @XxlJob("updateLatelyAlbumRanking")
    public void updateLatelyAlbumRanking() {
        // 封装日志对象
        XxlJobLog xxlJobLog = new XxlJobLog();
        xxlJobLog.setJobId(XxlJobHelper.getJobId());
        long startTime = System.currentTimeMillis();
        try {
            //远程调用，更新排行榜
            searchFeignClient.updateLatelyAlbumRanking();
        } catch (Exception e) {
            xxlJobLog.setStatus(0);//失败
            xxlJobLog.setError(ExceptionUtil.getErrorMessage(e));
            log.error("定时任务执行失败，任务id为：{}", XxlJobHelper.getJobId());
            e.printStackTrace();
        } finally {
            //耗时
            int times = (int) (System.currentTimeMillis() - startTime);
            xxlJobLog.setTimes(times);
            xxlJobLogMapper.insert(xxlJobLog);
        }
    }

    /**
     * 更新Vip到期的用户的状态
     */
    @XxlJob("updateVipExpireStatusJob")
    public void updateVipExpireStatusJob() {
        log.info("更新Vip到期失效状态：{}", XxlJobHelper.getJobId());

        //记录定时任务相关的日志信息
        //封装日志对象
        XxlJobLog xxlJobLog = new XxlJobLog();
        xxlJobLog.setJobId(XxlJobHelper.getJobId());
        long startTime = System.currentTimeMillis();
        try {
            userInfoFeignClient.updateVipExpireStatus();
            xxlJobLog.setStatus(1);//成功
        } catch (Exception e) {
            xxlJobLog.setStatus(0);//失败
            xxlJobLog.setError(ExceptionUtil.getErrorMessage(e));
            log.error("定时任务执行失败，任务id为：{}", XxlJobHelper.getJobId());
            e.printStackTrace();
        } finally {
            //耗时
            int times = (int) (System.currentTimeMillis() - startTime);
            xxlJobLog.setTimes(times);
            xxlJobLogMapper.insert(xxlJobLog);
        }
    }
}