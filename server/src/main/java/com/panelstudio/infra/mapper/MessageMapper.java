package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    /** 大厅列表用：一次取出这批圆桌各自的最新一条消息（避免 N+1）。 */
    @Select("""
            <script>
            SELECT m.* FROM messages m
            JOIN (SELECT discussion_id, MAX(created_at) AS max_at
                    FROM messages
                   WHERE discussion_id IN
                   <foreach collection="discussionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                   GROUP BY discussion_id) latest
              ON latest.discussion_id = m.discussion_id AND latest.max_at = m.created_at
            </script>
            """)
    List<MessageEntity> findLatestPerDiscussion(@Param("discussionIds") List<String> discussionIds);

    /** 只按场次取消息：仓储层不向业务层开放全表查询。 */
    @Select("SELECT * FROM messages WHERE discussion_id = #{discussionId} ORDER BY created_at ASC, seq ASC")
    List<MessageEntity> findByDiscussionId(@Param("discussionId") String discussionId);

    /** 幂等键查询（I-5）：同一场次同一 clientMsgId 只允许一条。 */
    @Select("""
            SELECT * FROM messages
            WHERE discussion_id = #{discussionId} AND client_msg_id = #{clientMsgId}
            LIMIT 1
            """)
    MessageEntity findByClientMsgId(@Param("discussionId") String discussionId,
                                    @Param("clientMsgId") String clientMsgId);

    @Select("SELECT COUNT(*) FROM messages WHERE discussion_id = #{discussionId}")
    int countByDiscussion(@Param("discussionId") String discussionId);
}
