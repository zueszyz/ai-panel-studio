package com.panelstudio.application.session;

import com.panelstudio.domain.entity.MessageEntity;
import com.panelstudio.domain.enums.DiscussionStatus;

import java.util.List;

/**
 * SessionActor 需要的持久化出口（窄接口，便于单测替换）。
 * 只暴露"按场次"的方法，杜绝跨场次读写（rules/30 §4）。
 */
public interface SessionStore {

    List<MessageEntity> loadHistory(String discussionId, int limit);

    void updateTurnIndex(String discussionId, int turnIndex);

    void updateStatus(String discussionId, DiscussionStatus status, String endedAt);
}
