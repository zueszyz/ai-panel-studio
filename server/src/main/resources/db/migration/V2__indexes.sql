-- 索引与唯一约束：包括「一席一人」「幂等键」「一场一个主持人」等业务硬约束

CREATE INDEX        ix_discussions_status_updated ON discussions (status, updated_at DESC);
CREATE INDEX        ix_discussions_creator        ON discussions (created_by, updated_at DESC);
CREATE UNIQUE INDEX ux_agents_order               ON agents (discussion_id, order_index);
CREATE UNIQUE INDEX ux_host_per_discussion        ON agents (discussion_id) WHERE role_type = 'HOST';
CREATE INDEX        ix_messages_timeline          ON messages (discussion_id, created_at);
CREATE INDEX        ix_messages_round             ON messages (discussion_id, round_no);
CREATE INDEX        ix_messages_quote             ON messages (quote_message_id);
CREATE UNIQUE INDEX ux_message_idempotency        ON messages (discussion_id, client_msg_id);
CREATE UNIQUE INDEX ux_minutes_version            ON minutes (discussion_id, version);
CREATE INDEX        ix_runs_timeline              ON agent_runs (discussion_id, started_at);
CREATE INDEX        ix_runs_agent_status          ON agent_runs (agent_id, status);
CREATE UNIQUE INDEX ux_participant_user           ON discussion_participants (discussion_id, user_id);
CREATE UNIQUE INDEX ux_participant_seat           ON discussion_participants (discussion_id, seat_id)
                                                  WHERE seat_id IS NOT NULL;
