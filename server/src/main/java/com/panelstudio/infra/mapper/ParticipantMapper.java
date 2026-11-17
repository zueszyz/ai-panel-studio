package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.ParticipantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ParticipantMapper extends BaseMapper<ParticipantEntity> {

    @Select("SELECT * FROM discussion_participants WHERE discussion_id = #{discussionId} AND user_id = #{userId}")
    ParticipantEntity findByDiscussionAndUser(@Param("discussionId") String discussionId,
                                              @Param("userId") String userId);

    /** 一席一人：用于校验席位是否已被别人认领（数据库侧还有部分唯一索引兜底）。 */
    @Select("SELECT * FROM discussion_participants WHERE discussion_id = #{discussionId} AND seat_id IS NOT NULL")
    List<ParticipantEntity> findClaimedSeats(@Param("discussionId") String discussionId);

    @Select("""
            <script>
            SELECT * FROM discussion_participants
             WHERE user_id = #{userId} AND seat_id IS NOT NULL
               AND discussion_id IN
               <foreach collection="discussionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ParticipantEntity> findMySeats(@Param("userId") String userId,
                                        @Param("discussionIds") List<String> discussionIds);
}
