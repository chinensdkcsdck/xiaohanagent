CREATE TABLE IF NOT EXISTS message_consume_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    consumed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_consume_log_message_id (message_id)
);
