package com.panelstudio.application.session;

import com.panelstudio.domain.entity.DiscussionEntity;
import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.DiscussionStatus;
import com.panelstudio.infra.mapper.DiscussionMapper;
import com.panelstudio.infra.mapper.MessageMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** SessionStore 的 MyBatis-Plus 实现：短事务、单条 UPDATE，不在事务里做任何等待。 */
@Component
public class MybatisSessionStore implements SessionStore {

    private final MessageMapper messageMapper;
    private final DiscussionMapper discussionMapper;

    public MybatisSessionStore(MessageMapper messageMapper, DiscussionMapper discussionMapper) {
        this.messageMapper = messageMapper;
        this.discussionMapper = discussionMapper;
    }

    @Override
    public List<MessageEntity> loadHistory(String discussionId, int limit) {
        List<MessageEntity> all = messageMapper.findByDiscussionId(discussionId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }

    @Override
    public void updateTurnIndex(String discussionId, int turnIndex) {
        DiscussionEntity patch = new DiscussionEntity();
        patch.setId(discussionId);
        patch.setTurnIndex(turnIndex);
        discussionMapper.updateById(patch);
    }

    @Override
    public void updateStatus(String discussionId, DiscussionStatus status, String endedAt) {
        DiscussionEntity patch = new DiscussionEntity();
        patch.setId(discussionId);
        patch.setStatus(status);
        if (endedAt != null) {
            patch.setEndedAt(endedAt);
        }
        discussionMapper.updateById(patch);
    }
}
