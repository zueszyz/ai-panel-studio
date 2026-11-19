package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.AgentRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 同 AgentMapper：手写 SQL 必须显式引用实体的 resultMap，
 * 否则 meta（JSON 列）被静默丢弃，轨迹面板的技术细节永远为空对象。
 */
@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {

    /** 只按场次取，禁止全表查询（rules/30 §4 隔离要求）。 */
    @Select("""
            SELECT * FROM agent_runs
            WHERE discussion_id = #{discussionId}
            ORDER BY started_at ASC, id ASC
            LIMIT #{limit}
            """)
    @ResultMap("mybatis-plus_AgentRunEntity")
    List<AgentRunEntity> findByDiscussionId(@Param("discussionId") String discussionId,
                                            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM agent_runs WHERE discussion_id = #{discussionId} AND error_code IS NOT NULL")
    int countFailures(@Param("discussionId") String discussionId);
}
