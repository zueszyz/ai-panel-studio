package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {

    @Select("SELECT * FROM agents WHERE discussion_id = #{discussionId} ORDER BY order_index ASC")
    List<AgentEntity> findByDiscussionId(@Param("discussionId") String discussionId);

    @Select("""
            <script>
            SELECT * FROM agents
             WHERE discussion_id IN
             <foreach collection="discussionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             ORDER BY discussion_id ASC, order_index ASC
            </script>
            """)
    List<AgentEntity> findByDiscussionIds(@Param("discussionIds") List<String> discussionIds);
}
