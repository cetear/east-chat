-- Apply once to an existing database before starting the P1 code. Back up first.
-- Review duplicates before adding the unique key. Do not auto-delete existing messages.
SELECT session_id, message_order, COUNT(*) AS duplicates FROM chat_message
GROUP BY session_id, message_order HAVING COUNT(*) > 1;
ALTER TABLE chat_message ADD UNIQUE KEY uk_session_message_order (session_id, message_order);
ALTER TABLE chat_session_memory ADD COLUMN covered_message_id BIGINT DEFAULT 0;
ALTER TABLE knowledge_doc ADD COLUMN index_version VARCHAR(64) DEFAULT NULL;
-- Status conventions: chat_message 0 failed / 1 succeeded / 2 running / 3 cancelled.
-- knowledge_doc additionally supports DELETING / DELETE_FAILED.
-- Old knowledge documents must be reingested into an index with generation keyword mapping.
