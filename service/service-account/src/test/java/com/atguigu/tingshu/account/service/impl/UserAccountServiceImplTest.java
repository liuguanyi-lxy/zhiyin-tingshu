package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {
    @Mock private UserAccountMapper accountMapper;
    @Mock private UserAccountDetailMapper detailMapper;
    @InjectMocks private UserAccountServiceImpl service;

    @BeforeAll
    static void tableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAccountDetail.class);
    }

    private AccountLockVo request() {
        AccountLockVo request = new AccountLockVo();
        request.setUserId(8L);
        request.setOrderNo("ORDER123");
        request.setAmount(new BigDecimal("19.90"));
        request.setContent("album");
        return request;
    }

    @Test
    void negativeAmountIsRejectedBeforeAccessingDatabase() {
        AccountLockVo request = request();
        request.setAmount(new BigDecimal("-1.00"));
        assertInvalid(request);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "0.00", "-0.01", "1.001"})
    void invalidAmountIsRejected(String amount) {
        AccountLockVo request = request();
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        assertInvalid(request);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidUserIsRejected(Long userId) {
        AccountLockVo request = request();
        request.setUserId(userId);
        assertInvalid(request);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOrderIsRejected(String orderNo) {
        AccountLockVo request = request();
        request.setOrderNo(orderNo);
        assertInvalid(request);
    }

    @Test
    void nullRequestIsRejected() { assertInvalid(null); }

    @Test
    void oversizedOrderIsRejected() {
        AccountLockVo request = request();
        request.setOrderNo("x".repeat(65));
        assertInvalid(request);
    }

    private void assertInvalid(AccountLockVo request) {
        GuiguException error = assertThrows(GuiguException.class, () -> service.checkAndDeduct(request));
        assertEquals(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode(), error.getCode());
        verifyNoInteractions(accountMapper, detailMapper);
    }

    @Test
    void missingAccountDoesNotDeduct() {
        GuiguException error = assertThrows(GuiguException.class, () -> service.checkAndDeduct(request()));
        assertEquals(ResultCodeEnum.ACCOUNT_ERROR.getCode(), error.getCode());
        verifyNoInteractions(detailMapper);
        verify(accountMapper, never()).checkAndDeduct(any(), any());
    }

    private void existingAccount() {
        when(accountMapper.selectForDeduction(8L)).thenReturn(new UserAccount());
    }

    @Test
    void successfulDeductionLocksAccountBeforeCheckingLedgerAndWriting() {
        existingAccount();
        when(accountMapper.checkAndDeduct(any(), eq(8L))).thenReturn(1);
        when(detailMapper.insert(any(UserAccountDetail.class))).thenReturn(1);
        assertEquals(ResultCodeEnum.SUCCESS.getCode(), service.checkAndDeduct(request()).getCode());
        var calls = inOrder(accountMapper, detailMapper);
        calls.verify(accountMapper).selectForDeduction(8L);
        calls.verify(detailMapper).selectOne(any());
        calls.verify(accountMapper).checkAndDeduct(new BigDecimal("19.90"), 8L);
        ArgumentCaptor<UserAccountDetail> ledger = ArgumentCaptor.forClass(UserAccountDetail.class);
        calls.verify(detailMapper).insert(ledger.capture());
        assertEquals("ORDER123", ledger.getValue().getOrderNo());
        assertEquals(8L, ledger.getValue().getUserId());
        assertEquals(SystemConstant.ACCOUNT_TRADE_TYPE_MINUS, ledger.getValue().getTradeType());
        assertEquals(new BigDecimal("19.90"), ledger.getValue().getAmount());
    }

    @Test
    void repeatedOrderWithEquivalentAmountReturnsSuccessWithoutDeductingAgain() {
        existingAccount();
        UserAccountDetail previous = new UserAccountDetail();
        previous.setAmount(new BigDecimal("19.9000"));
        when(detailMapper.selectOne(any())).thenReturn(previous);
        assertEquals(ResultCodeEnum.SUCCESS.getCode(), service.checkAndDeduct(request()).getCode());
        verify(accountMapper, never()).checkAndDeduct(any(), any());
        verify(detailMapper, never()).insert(any(UserAccountDetail.class));
    }

    @Test
    void repeatedOrderWithDifferentAmountIsRejected() {
        existingAccount();
        UserAccountDetail previous = new UserAccountDetail();
        previous.setAmount(BigDecimal.ONE);
        when(detailMapper.selectOne(any())).thenReturn(previous);
        GuiguException error = assertThrows(GuiguException.class, () -> service.checkAndDeduct(request()));
        assertEquals(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), error.getCode());
        verify(accountMapper, never()).checkAndDeduct(any(), any());
        verify(detailMapper, never()).insert(any(UserAccountDetail.class));
    }

    @Test
    void insufficientFundsDoNotCreateLedger() {
        existingAccount();
        assertEquals(ResultCodeEnum.ACCOUNT_LESS.getCode(), service.checkAndDeduct(request()).getCode());
        verify(detailMapper, never()).insert(any(UserAccountDetail.class));
    }

    @Test
    void missingLedgerInsertIsNotTreatedAsSuccess() {
        existingAccount();
        when(accountMapper.checkAndDeduct(any(), eq(8L))).thenReturn(1);
        assertThrows(GuiguException.class, () -> service.checkAndDeduct(request()));
    }

    @Test
    void ledgerInsertFailureTriggersTransactionRollback() {
        existingAccount();
        when(accountMapper.checkAndDeduct(any(), eq(8L))).thenReturn(1);
        when(detailMapper.insert(any(UserAccountDetail.class)))
                .thenThrow(new DataAccessResourceFailureException("simulated database failure"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        UserAccountService transactional = (UserAccountService) factory.getProxy();
        assertThrows(DataAccessResourceFailureException.class, () -> transactional.checkAndDeduct(request()));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }
}
