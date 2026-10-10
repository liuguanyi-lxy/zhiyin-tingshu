package com.atguigu.tingshu.comment.service.impl;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.SensitiveWordFilterUtil;
import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.model.comment.CommentPraise;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论服务单元测试（纯 Mockito，不连真实 MongoDB/Redis/MQ）。
 * 覆盖：敏感词过滤、用户信息冗余、顶层评论统计消息、点赞切换幂等、
 * 越权删除防护、分页聚合回复与点赞状态。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RabbitService rabbitService;

    @Mock
    private UserInfoFeignClient userInfoFeignClient;

    @InjectMocks
    private CommentServiceImpl service;

    @BeforeAll
    static void initSensitiveWordModel() {
        SensitiveWordFilterUtil.buildDFAModel(Arrays.asList("笨", "垃圾"));
    }

    private CommentVo commentVo(String content) {
        CommentVo vo = new CommentVo();
        vo.setAlbumId(1L);
        vo.setTrackId(0L);
        vo.setContent(content);
        vo.setAlbumCommentScore(10);
        return vo;
    }

    private Comment savedComment(String id, Long userId, String replyCommentId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setAlbumId(1L);
        comment.setUserId(userId);
        comment.setReplyCommentId(replyCommentId);
        comment.setPraiseCount(3);
        return comment;
    }

    @Test
    void saveTopComment_filtersSensitiveWord_andSendsStatMessage() {
        when(userInfoFeignClient.getUserInfoVo(8L)).thenReturn(Result.ok(userInfoVo("龙星宇", "http://a.png")));
        when(mongoTemplate.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveComment(8L, commentVo("这个笨产品"));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(mongoTemplate).save(captor.capture());
        Comment saved = captor.getValue();
        // 敏感词被过滤、昵称脱敏、顶层归一
        assertFalse(saved.getContent().contains("笨"));
        assertEquals("龙**", saved.getNickname());
        assertEquals("0", saved.getReplyCommentId());
        // 顶层评论触发 0404 统计消息
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_ALBUM),
                eq(MqConst.ROUTING_ALBUM_STAT_UPDATE), msgCaptor.capture());
        assertTrue(msgCaptor.getValue().contains("0404"));
        assertTrue(msgCaptor.getValue().contains("businessNo"));
        assertTrue(msgCaptor.getValue().contains("\"count\":1"));
        // 专辑级评论(trackId=0)不应触发声音维度统计（0704）
        verify(rabbitService, never()).sendMessage(eq(MqConst.EXCHANGE_TRACK), anyString(), anyString());
    }

    @Test
    void saveReply_neverSendsStatMessage() {
        when(userInfoFeignClient.getUserInfoVo(8L)).thenReturn(Result.ok(userInfoVo("龙星宇", "http://a.png")));
        when(mongoTemplate.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentVo vo = commentVo("说得好");
        vo.setReplyCommentId("parentCommentId");
        service.saveComment(8L, vo);

        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void saveComment_feignDown_stillSavesWithDefaultNickname() {
        when(userInfoFeignClient.getUserInfoVo(8L)).thenThrow(new RuntimeException("timeout"));
        when(mongoTemplate.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveComment(8L, commentVo("正常内容"));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(mongoTemplate).save(captor.capture());
        // 用户服务降级不阻塞发布，昵称兜底"听友"
        assertEquals("听友", captor.getValue().getNickname());
    }

    @Test
    void praiseFirstTime_insertsPraiseRecordAndIncrements() {
        when(mongoTemplate.findById("c1", Comment.class)).thenReturn(savedComment("c1", 8L, "0"));
        when(mongoTemplate.findOne(any(Query.class), eq(CommentPraise.class))).thenReturn(null);

        service.praise(8L, 1L, "c1");

        verify(mongoTemplate).insert(any(CommentPraise.class));
        verify(mongoTemplate).updateFirst(any(Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Comment.class));
    }

    @Test
    void praiseAgain_cancelsPraiseRecordAndDecrements() {
        when(mongoTemplate.findById("c1", Comment.class)).thenReturn(savedComment("c1", 8L, "0"));
        CommentPraise existing = new CommentPraise();
        existing.setCommentId("c1");
        existing.setUserId(8L);
        when(mongoTemplate.findOne(any(Query.class), eq(CommentPraise.class))).thenReturn(existing);

        service.praise(8L, 1L, "c1");

        verify(mongoTemplate).remove(existing);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Comment.class));
    }

    @Test
    void removeComment_ownedByOther_throwsAndKeepsData() {
        when(mongoTemplate.findById("c1", Comment.class)).thenReturn(savedComment("c1", 1L, "0"));

        assertThrows(GuiguException.class, () -> service.removeComment(8L, 1L, "c1"));

        verify(mongoTemplate, never()).remove(any(Comment.class));
        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void removeTopComment_deletesRepliesAndPraises_andDecrementsStat() {
        when(mongoTemplate.findById("c1", Comment.class)).thenReturn(savedComment("c1", 8L, "0"));
        when(mongoTemplate.find(any(Query.class), eq(Comment.class)))
                .thenReturn(List.of(savedComment("r1", 9L, "c1")));

        service.removeComment(8L, 1L, "c1");

        // 顶层评论、回复、两层点赞记录都被清理（remove(Query) ×3 + remove(entity) ×1）
        verify(mongoTemplate, times(2)).remove(any(org.springframework.data.mongodb.core.query.Query.class), eq(CommentPraise.class));
        verify(mongoTemplate, times(1)).remove(any(org.springframework.data.mongodb.core.query.Query.class), eq(Comment.class));
        verify(mongoTemplate, times(1)).remove(any(Comment.class));
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_ALBUM),
                eq(MqConst.ROUTING_ALBUM_STAT_UPDATE), msgCaptor.capture());
        assertTrue(msgCaptor.getValue().contains("\"count\":-1"));
    }

    @Test
    void findCommentPage_aggregatesRepliesAndPraiseState() {
        when(mongoTemplate.count(any(Query.class), eq(Comment.class))).thenReturn(1L);
        Comment top = savedComment("c1", 8L, "0");
        Comment reply = savedComment("r1", 9L, "c1");
        when(mongoTemplate.find(any(Query.class), eq(Comment.class)))
                .thenReturn(List.of(top))
                .thenReturn(List.of(reply));
        CommentPraise praise = new CommentPraise();
        praise.setCommentId("c1");
        praise.setUserId(8L);
        when(mongoTemplate.find(any(Query.class), eq(CommentPraise.class))).thenReturn(List.of(praise));

        Page<Comment> result = service.findCommentPage(1L, 0L, 8L, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getRecords().get(0).getReplyCommentList().size());
        // 当前用户赞过顶层，没赞过回复
        assertTrue(result.getRecords().get(0).getIsPraise());
        assertFalse(result.getRecords().get(0).getReplyCommentList().get(0).getIsPraise());
    }

    @Test
    void findCommentPage_anonymous_isPraiseAlwaysFalse() {
        when(mongoTemplate.count(any(Query.class), eq(Comment.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Comment.class)))
                .thenReturn(List.of(savedComment("c1", 8L, "0")))
                .thenReturn(List.of());

        Page<Comment> result = service.findCommentPage(1L, 0L, null, 1, 10);

        assertNotNull(result.getRecords().get(0).getIsPraise());
        assertFalse(result.getRecords().get(0).getIsPraise());
    }

    private UserInfoVo userInfoVo(String nickname, String avatarUrl) {
        UserInfoVo vo = new UserInfoVo();
        vo.setNickname(nickname);
        vo.setAvatarUrl(avatarUrl);
        return vo;
    }

    @Test
    void findCommentPage_marksDeleteMarkForOwnCommentsOnly() {
        when(mongoTemplate.count(any(Query.class), eq(Comment.class))).thenReturn(1L);
        Comment top = savedComment("c1", 8L, "0");
        Comment reply = savedComment("r1", 9L, "c1");
        when(mongoTemplate.find(any(Query.class), eq(Comment.class)))
                .thenReturn(List.of(top))
                .thenReturn(List.of(reply))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(CommentPraise.class))).thenReturn(List.of());

        Page<Comment> result = service.findCommentPage(1L, 0L, 8L, 1, 10);

        // 自己发的顶层评论可删（deleteMark=1），别人发的回复不可删（0）
        assertEquals("1", result.getRecords().get(0).getDeleteMark());
        assertEquals("0", result.getRecords().get(0).getReplyCommentList().get(0).getDeleteMark());
    }

    @Test
    void findCommentPage_showsReplyToReplyWithParent() {
        when(mongoTemplate.count(any(Query.class), eq(Comment.class))).thenReturn(1L);
        Comment top = savedComment("c1", 8L, "0");
        Comment reply1 = savedComment("r1", 9L, "c1");
        Comment reply2 = savedComment("r2", 10L, "r1");
        when(mongoTemplate.find(any(Query.class), eq(Comment.class)))
                .thenReturn(List.of(top))
                .thenReturn(List.of(reply1))
                .thenReturn(List.of(reply2))
                .thenReturn(List.of());

        Page<Comment> result = service.findCommentPage(1L, 0L, null, 1, 10);

        List<Comment> replies = result.getRecords().get(0).getReplyCommentList();
        // "回复的回复"不再丢失：两级回复都挂到顶层评论下
        assertEquals(2, replies.size());
        // 直接回复无 parent；回复的回复填充 parent 指向被回复的回复
        assertNull(replies.get(0).getParent());
        assertNotNull(replies.get(1).getParent());
        assertEquals("r1", replies.get(1).getParent().getId());
    }

    @Test
    void saveTrackTopComment_sendsAlbumAndTrackStatMessages() {
        when(userInfoFeignClient.getUserInfoVo(8L)).thenReturn(Result.ok(userInfoVo("龙星宇", "http://a.png")));
        when(mongoTemplate.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        CommentVo vo = commentVo("说得好");
        vo.setTrackId(5L);

        service.saveComment(8L, vo);

        // 声音级顶层评论：专辑 0404 + 声音 0704 各发一条
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_ALBUM), eq(MqConst.ROUTING_ALBUM_STAT_UPDATE), msgCaptor.capture());
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_TRACK), eq(MqConst.ROUTING_TRACK_STAT_UPDATE), msgCaptor.capture());
        List<String> all = msgCaptor.getAllValues();
        assertTrue(all.get(0).contains("0404"));
        assertTrue(all.get(1).contains("0704"));
        assertTrue(all.get(1).contains("\"trackId\":5"));
        assertTrue(all.get(1).contains("\"count\":1"));
    }

    @Test
    void removeTrackTopComment_sendsTrackStatDecrement() {
        Comment comment = savedComment("c1", 8L, "0");
        comment.setTrackId(5L);
        when(mongoTemplate.findById("c1", Comment.class)).thenReturn(comment);
        when(mongoTemplate.find(any(Query.class), eq(Comment.class))).thenReturn(List.of());

        service.removeComment(8L, 1L, "c1");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_TRACK), eq(MqConst.ROUTING_TRACK_STAT_UPDATE), msgCaptor.capture());
        assertTrue(msgCaptor.getValue().contains("0704"));
        assertTrue(msgCaptor.getValue().contains("\"count\":-1"));
    }
}
