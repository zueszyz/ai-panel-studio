package com.panelstudio.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panelstudio.domain.entity.MinuteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 纪要仓储：只暴露"按场次"的查询，禁止跨场次读取（隔离要求）。 */
@Mapper
public interface MinuteMapper extends BaseMapper<MinuteEntity> {

    @Select("""
            SELECT * FROM minutes
             WHERE discussion_id = #{discussionId} AND status = 'READY'
             ORDER BY version DESC
             LIMIT 1
            """)
    MinuteEntity findLatestReady(@Param("discussionId") String discussionId);

    @Select("""
            SELECT * FROM minutes
             WHERE discussion_id = #{discussionId} AND version = #{version}
             LIMIT 1
            """)
    MinuteEntity findByVersion(@Param("discussionId") String discussionId, @Param("version") int version);

    @Select("SELECT COALESCE(MAX(version), 0) FROM minutes WHERE discussion_id = #{discussionId}")
    int maxVersion(@Param("discussionId") String discussionId);

    /** 版本列表（新到旧），供纪要页切换历史版本。 */
    @Select("""
            SELECT version FROM minutes
             WHERE discussion_id = #{discussionId} AND status = 'READY'
             ORDER BY version DESC
            """)
    List<Integer> findVersions(@Param("discussionId") String discussionId);
}
