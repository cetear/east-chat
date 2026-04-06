-- 在 MySQL 中执行前请先创建数据库：
-- CREATE DATABASE easychat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE easychat;

CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(255) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    model_type  VARCHAR(100) NOT NULL,
    summary     TEXT,
    max_rounds  INT          DEFAULT 10,
    created_at  BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT       NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    content       TEXT         NOT NULL,
    message_order INT          NOT NULL,
    created_at    BIGINT       NOT NULL,
    FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS model_config (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_code     VARCHAR(100) NOT NULL UNIQUE,
    model_name     VARCHAR(255) NOT NULL,
    api_key        TEXT         NOT NULL,
    base_url       VARCHAR(500) NOT NULL,
    model_name_api VARCHAR(255),
    temperature    DOUBLE,
    max_tokens     INT,
    is_enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     BIGINT       NOT NULL,
    updated_at     BIGINT       NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
