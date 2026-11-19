package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 自定义 @Select 必须显式引用实体的 resultMap：MyBatis-Plus 的 autoResultMap
 * 只对内置方法生效，手写 SQL 走自动映射时 focus（JSON 列）会被静默丢弃，
 * 导致盯住「关注点」的发言打分与席位卡展示失效。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {

    @Select("SELECT * FROM agents WHERE discussion_id = #{discussionId} ORDER BY order_index ASC")
    @ResultMap("mybatis-plus_AgentEntity")
    List<AgentEntity> findByDiscussionId(@Param("discussionId") String discussionId);

    @Select("""
            <script>
            SELECT * FROM agents
             WHERE discussion_id IN
             <foreach collection="discussionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             ORDER BY discussion_id ASC, order_index ASC
            </script>
            """)
    @ResultMap("mybatis-plus_AgentEntity")
    List<AgentEntity> findByDiscussionIds(@Param("discussionIds") List<String> discussionIds);
}
