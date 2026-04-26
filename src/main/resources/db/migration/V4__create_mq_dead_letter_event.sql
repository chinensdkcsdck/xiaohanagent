CREATE TABLE IF NOT EXISTS mq_dead_letter_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NULL,
    biz_type VARCHAR(32) NULL,
    action VARCHAR(32) NULL,
    queue_name VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    error_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mq_dle_created_at (created_at),
    INDEX idx_mq_dle_event_id (event_id)
);
