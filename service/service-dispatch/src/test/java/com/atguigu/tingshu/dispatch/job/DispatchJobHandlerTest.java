package com.atguigu.tingshu.dispatch.job;

import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.dispatch.mapper.XxlJobLogMapper;
import com.atguigu.tingshu.model.dispatch.XxlJobLog;
import com.atguigu.tingshu.search.client.SearchFeignClient;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 调度任务处理器单元测试（纯 Mockito，无 XxlJob 上下文时 getJobId() 返回 -1）。
 * 覆盖：成功/失败两条路径下的任务日志落库（status/error/times）与 MDC traceId 清理。
 */
@ExtendWith(MockitoExtension.class)
class DispatchJobHandlerTest {

    @Mock
    private SearchFeignClient searchFeignClient;
    @Mock
    private XxlJobLogMapper xxlJobLogMapper;
    @Mock
    private UserInfoFeignClient userInfoFeignClient;
    @InjectMocks
    private DispatchJobHandler handler;

    @AfterEach
    void cleanMdc() {
        TraceContextUtil.end();
    }

    @Test
    void updateVipExpireStatusJob_success_insertsSuccessLog() {
        handler.updateVipExpireStatusJob();

        verify(userInfoFeignClient).updateVipExpireStatus();
        ArgumentCaptor<XxlJobLog> captor = ArgumentCaptor.forClass(XxlJobLog.class);
        verify(xxlJobLogMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertNull(captor.getValue().getError());
        assertTrue(captor.getValue().getTimes() >= 0);
    }

    @Test
    void updateVipExpireStatusJob_failure_recordsErrorLog() {
        doThrow(new RuntimeException("用户服务不可用")).when(userInfoFeignClient).updateVipExpireStatus();

        handler.updateVipExpireStatusJob();

        // 失败也要落库，便于排障
        ArgumentCaptor<XxlJobLog> captor = ArgumentCaptor.forClass(XxlJobLog.class);
        verify(xxlJobLogMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("用户服务不可用"));
    }

    @Test
    void updateLatelyAlbumRanking_success_insertsLogWithTimes() {
        handler.updateLatelyAlbumRanking();

        verify(searchFeignClient).updateLatelyAlbumRanking();
        ArgumentCaptor<XxlJobLog> captor = ArgumentCaptor.forClass(XxlJobLog.class);
        verify(xxlJobLogMapper).insert(captor.capture());
        assertTrue(captor.getValue().getTimes() >= 0);
        assertNull(captor.getValue().getError());
    }

    @Test
    void updateLatelyAlbumRanking_failure_recordsFailedStatus() {
        doThrow(new IllegalStateException("搜索服务超时")).when(searchFeignClient).updateLatelyAlbumRanking();

        handler.updateLatelyAlbumRanking();

        ArgumentCaptor<XxlJobLog> captor = ArgumentCaptor.forClass(XxlJobLog.class);
        verify(xxlJobLogMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        assertTrue(captor.getValue().getError().contains("搜索服务超时"));
    }

    @Test
    void jobExecution_cleansTraceIdFromMdc() {
        handler.updateVipExpireStatusJob();

        assertNull(TraceContextUtil.getTraceId());
    }

    @Test
    void traceContext_beginWithUpstreamId_reusesIt_andEndClears() {
        String reused = TraceContextUtil.begin("upstream-trace-id");
        assertEquals("upstream-trace-id", reused);
        assertEquals("upstream-trace-id", TraceContextUtil.getTraceId());

        TraceContextUtil.end();
        assertNull(TraceContextUtil.getTraceId());
    }
}
