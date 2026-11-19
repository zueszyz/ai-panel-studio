package com.panelstudio.infra.db;

import com.panelstudio.application.MetricsService;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqliteBusyRetryInterceptorTest {

    private MetricsService metrics;
    private List<Long> delays;
    private SqliteBusyRetryInterceptor interceptor;

    @BeforeEach
    void setUp() {
        metrics = new MetricsService();
        delays = new ArrayList<>();
        interceptor = new SqliteBusyRetryInterceptor(metrics, delays::add);
    }

    @Test
    @DisplayName("SQLITE_BUSY 命中后按 50/100ms 退避重试，成功后不向调用方暴露异常")
    void retriesBusyWriteUntilSuccess() throws Throwable {
        Invocation invocation = invocation();
        PersistenceException busy = busy(SQLiteErrorCode.SQLITE_BUSY);
        when(invocation.proceed()).thenThrow(busy).thenThrow(busy).thenReturn("done");

        assertThat(interceptor.intercept(invocation)).isEqualTo("done");

        verify(invocation, times(3)).proceed();
        assertThat(delays).containsExactly(50L, 100L);
        assertThat(metrics.snapshot().sqliteBusy()).isEqualTo(2);
    }

    @Test
    @DisplayName("写锁一直不释放时 3 次退避后放弃，总计尝试 4 次")
    void givesUpAfterThreeRetries() throws Throwable {
        Invocation invocation = invocation();
        when(invocation.proceed()).thenThrow(busy(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT));

        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(PersistenceException.class);

        verify(invocation, times(4)).proceed();
        assertThat(delays).containsExactly(50L, 100L, 200L);
        assertThat(metrics.snapshot().sqliteBusy()).isEqualTo(3);
    }

    @Test
    @DisplayName("非锁冲突异常不重试，立即原样抛出")
    void doesNotRetryOtherFailures() throws Throwable {
        Invocation invocation = invocation();
        PersistenceException failure = new PersistenceException("SQL 语法错误");
        when(invocation.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> interceptor.intercept(invocation)).isSameAs(failure);

        verify(invocation, times(1)).proceed();
        assertThat(delays).isEmpty();
        assertThat(metrics.snapshot().sqliteBusy()).isZero();
    }

    @Test
    @DisplayName("扩展锁码、标准错误码与 Spring 翻译后的文案都能识别为写锁冲突")
    void recognizesBusyVariants() {
        assertThat(SqliteBusyRetryInterceptor.isBusy(busy(SQLiteErrorCode.SQLITE_BUSY))).isTrue();
        assertThat(SqliteBusyRetryInterceptor.isBusy(busy(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT))).isTrue();
        assertThat(SqliteBusyRetryInterceptor.isBusy(busy(SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE))).isTrue();
        assertThat(SqliteBusyRetryInterceptor.isBusy(new PersistenceException(
                new java.sql.SQLException("database is locked", "SQLITE_BUSY", 5)))).isTrue();
        assertThat(SqliteBusyRetryInterceptor.isBusy(
                new DataRetrievalFailureException("SQLITE_BUSY: database is locked"))).isTrue();
        assertThat(SqliteBusyRetryInterceptor.isBusy(new IllegalStateException("boom"))).isFalse();
    }

    private static Invocation invocation() {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[0]);
        return invocation;
    }

    private static PersistenceException busy(SQLiteErrorCode code) {
        return new PersistenceException(new SQLiteException("database is locked", code));
    }
}
