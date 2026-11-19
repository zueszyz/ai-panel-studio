package com.panelstudio.infra.db;

import com.panelstudio.application.MetricsService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * SQLITE_BUSY 的应用层退避重试（ARCHITECTURE §3.7 / rules/40 §1 / TESTING I-13）。
 *
 * <p>连接级 {@code busy_timeout=5000} 只能等普通写锁；{@code SQLITE_BUSY_SNAPSHOT}
 * 这类快照冲突它无效，且多个 agent 同时 end 时并发 UPDATE 仍可能撞锁。
 * 这里在 MyBatis 写语句外层统一重试：50 / 100 / 200ms 共三次，命中只记
 * {@code sqlite.busy} 日志与计数，不把数据库细节抛给接口（对用户静默）。</p>
 *
 * <p>重试只包住单条 mapper 写，符合「短事务」纪律——绝不把 LLM 调用或 SSE 发送
 * 放进重试范围；读语句不重试（WAL 下读写互不阻塞）。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class SqliteBusyRetryInterceptor implements Interceptor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SqliteBusyRetryInterceptor.class);

    /** 退避序列：三次重试，累计等待 350ms，远小于 busy_timeout，不会掩盖真正的故障。 */
    static final long[] BACKOFF_MS = {50L, 100L, 200L};

    private final MetricsService metrics;
    private final Sleeper sleeper;

    @Autowired
    public SqliteBusyRetryInterceptor(MetricsService metrics) {
        this(metrics, Thread::sleep);
    }

    /** 测试用：注入可控的等待器，既能断言退避序列，又不真的拖慢单测。 */
    SqliteBusyRetryInterceptor(MetricsService metrics, Sleeper sleeper) {
        this.metrics = metrics;
        this.sleeper = sleeper;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        int attempt = 0;
        while (true) {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                if (!isBusy(ex) || attempt >= BACKOFF_MS.length) {
                    throw ex;
                }
                long delay = BACKOFF_MS[attempt++];
                metrics.sqliteBusy();
                log.warn("sqlite.busy event=sqlite.busy statement={} attempt={}/{} delayMs={} cause={}",
                        statementOf(invocation), attempt, BACKOFF_MS.length, delay, rootMessage(ex));
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String statementOf(Invocation invocation) {
        Object[] args = invocation.getArgs();
        return args.length > 0 && args[0] instanceof MappedStatement ms ? ms.getId() : "unknown";
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        for (int i = 0; i < 10 && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * 识别 SQLite 的写锁冲突。优先用 sqlite-jdbc 的结果码（覆盖 BUSY_SNAPSHOT /
     * LOCKED_SHAREDCACHE 等扩展码），再退回标准 SQLITE_BUSY/LOCKED 错误码与文案，
     * 兼容异常被 Spring 翻译为 DataAccessException 的场景。
     */
    static boolean isBusy(Throwable ex) {
        for (int depth = 0; ex != null && depth < 10; depth++) {
            if (ex instanceof org.sqlite.SQLiteException sqlite) {
                String code = String.valueOf(sqlite.getResultCode());
                if (code.contains("BUSY") || code.contains("LOCKED")) {
                    return true;
                }
            }
            if (ex instanceof SQLException sql && (sql.getErrorCode() == 5 || sql.getErrorCode() == 6)) {
                return true;
            }
            if (ex instanceof DataAccessException && ex.getMessage() != null
                    && ex.getMessage().contains("SQLITE_BUSY")) {
                return true;
            }
            ex = ex.getCause() == ex ? null : ex.getCause();
        }
        return false;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
