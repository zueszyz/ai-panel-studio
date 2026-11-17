package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.enums.DiscussionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DiscussionMapper extends BaseMapper<DiscussionEntity> {

    /**
     * 大厅列表：LIVE 优先，再按 updated_at 倒序。
     * 只做「限量查询」，offset 由游标（不透明）传入，避免全表拉取后在内存分页。
     */
    @Select("""
            <script>
            SELECT * FROM discussions
            WHERE deleted_at IS NULL
            <if test="statuses != null and statuses.size() > 0">
              AND status IN
              <foreach collection="statuses" item="item" open="(" separator="," close=")">#{item}</foreach>
            </if>
            <if test="keyword != null and keyword != ''">
              AND topic LIKE '%' || #{keyword} || '%'
            </if>
            <if test="ids != null">
              <choose>
                <when test="ids.size() > 0">
                  AND id IN
                  <foreach collection="ids" item="item" open="(" separator="," close=")">#{item}</foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            ORDER BY CASE WHEN status = 'LIVE' THEN 0 ELSE 1 END, updated_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DiscussionEntity> search(@Param("statuses") List<DiscussionStatus> statuses,
                                  @Param("keyword") String keyword,
                                  @Param("ids") List<String> ids,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 「我参与的」候选集合：我创建的 + 我作为参与者加入的。 */
    @Select("""
            SELECT DISTINCT d.id FROM discussions d
            LEFT JOIN discussion_participants p
                   ON p.discussion_id = d.id AND p.user_id = #{userId}
            WHERE d.deleted_at IS NULL AND (d.created_by = #{userId} OR p.user_id IS NOT NULL)
            """)
    List<String> findMyDiscussionIds(@Param("userId") String userId);

    /** 席位生成完成：一次更新状态、目标与议题分析（避免多次写）。 */
    @Update("""
            UPDATE discussions
               SET status = #{status},
                   goal = #{goal},
                   topic_analysis = #{topicAnalysis},
                   updated_at = #{updatedAt}
             WHERE id = #{id} AND deleted_at IS NULL
            """)
    int markPanelReady(@Param("id") String id,
                       @Param("status") DiscussionStatus status,
                       @Param("goal") String goal,
                       @Param("topicAnalysis") String topicAnalysis,
                       @Param("updatedAt") String updatedAt);

    /** 换一批成功后计数 +1（失败不计数，避免用户为错误付费）。 */
    @Update("UPDATE discussions SET regen_count = regen_count + 1 WHERE id = #{id} AND deleted_at IS NULL")
    int increaseRegenCount(@Param("id") String id);

    /** 状态迁移用 CAS：影响 0 行即视为并发冲突。 */
    @Update("""
            UPDATE discussions SET status = #{next}, updated_at = #{updatedAt}
             WHERE id = #{id} AND status = #{expected} AND deleted_at IS NULL
            """)
    int compareAndSetStatus(@Param("id") String id,
                            @Param("expected") DiscussionStatus expected,
                            @Param("next") DiscussionStatus next,
                            @Param("updatedAt") String updatedAt);
}
