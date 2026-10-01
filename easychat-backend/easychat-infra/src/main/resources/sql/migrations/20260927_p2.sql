-- Apply once AFTER P1 migration. Existing data stays with the demo identity.
ALTER TABLE chat_session ADD COLUMN owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL DEFAULT 'demo', ADD INDEX idx_session_owner(owner_id);
ALTER TABLE knowledge_doc ADD COLUMN owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL DEFAULT 'demo';
-- Review duplicate rows before creating the unique key; do not auto-delete user documents.
SELECT owner_id,dataset,file_hash,COUNT(*) FROM knowledge_doc GROUP BY owner_id,dataset,file_hash HAVING COUNT(*)>1;
ALTER TABLE knowledge_doc ADD UNIQUE KEY uk_owner_dataset_hash(owner_id,dataset,file_hash);
CREATE TABLE execution_lease (
 lock_key VARCHAR(512) COLLATE utf8mb4_bin PRIMARY KEY,
 owner_token VARCHAR(64) NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 INDEX idx_lease_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE chat_event_outbox (
 event_id VARCHAR(64) PRIMARY KEY,
 event_type VARCHAR(64) NOT NULL,
 payload LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(3) NOT NULL,
 owner_token VARCHAR(64),
 lease_until DATETIME(3),
 created_at DATETIME(3) NOT NULL,
 delivered_at DATETIME(3),
 last_error VARCHAR(512),
 INDEX idx_outbox_pending(status,available_at),
 INDEX idx_outbox_lease(status,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;