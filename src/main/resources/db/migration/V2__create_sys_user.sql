CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    username VARCHAR(64) NOT NULL,
    id_card VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_user (account, password_hash, username, id_card, status)
VALUES
('zhangsan', '123456', '张三', '310101199001010001', 1),
('lisi', '123456', '李四', '310101199202020002', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    username = VALUES(username),
    id_card = VALUES(id_card),
    status = VALUES(status);

