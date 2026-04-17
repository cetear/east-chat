-- =====================================================
-- 多渠道路由：新增表结构
-- 执行前请确认数据库连接正确
-- =====================================================

-- 1. 模型表
CREATE TABLE IF NOT EXISTS `model` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `model_code`   VARCHAR(64)  NOT NULL COMMENT '统一模型标识，如 gpt-4o、deepseek-chat',
    `display_name` VARCHAR(128) NOT NULL DEFAULT '',
    `max_tokens`   INT          NOT NULL DEFAULT 4096,
    `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    `create_time`  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型表';

-- 2. 渠道商表
CREATE TABLE IF NOT EXISTS `provider` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `provider_code` VARCHAR(64)  NOT NULL COMMENT '渠道标识，如 deepseek-official、openai-azure',
    `base_url`      VARCHAR(256) NOT NULL DEFAULT '',
    `api_key`       VARCHAR(256) NOT NULL DEFAULT '' COMMENT '建议加密存储',
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道商表';

-- 3. 模型-渠道关联表（路由配置核心）
CREATE TABLE IF NOT EXISTS `model_provider` (
    `id`                        BIGINT      NOT NULL AUTO_INCREMENT,
    `model_code`                VARCHAR(64) NOT NULL,
    `provider_code`             VARCHAR(64) NOT NULL,
    `priority`                  INT         NOT NULL DEFAULT 0  COMMENT '优先级，越小越优先',
    `weight`                    INT         NOT NULL DEFAULT 1  COMMENT '权重（未来加权随机使用）',
    `timeout_ms`                INT         NOT NULL DEFAULT 60000,
    `max_retry`                 INT         NOT NULL DEFAULT 0,
    `circuit_breaker_threshold` INT         NOT NULL DEFAULT 3  COMMENT '熔断失败次数阈值',
    `circuit_breaker_window`    INT         NOT NULL DEFAULT 30 COMMENT '熔断冷却时间（秒）',
    `enabled`                   TINYINT     NOT NULL DEFAULT 1,
    `create_time`               BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_provider` (`model_code`, `provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型-渠道路由配置';

-- 4. chat_message 扩展字段（新增 status / provider_code）
		ALTER TABLE `chat_message`
    ADD COLUMN  `status`        VARCHAR(20)  NOT NULL DEFAULT 'DONE'    COMMENT 'PROCESSING/DONE/FAILED',
    ADD COLUMN  `provider_code` VARCHAR(64)  NOT NULL DEFAULT ''        COMMENT '最终成功渠道标识';
-- =====================================================
-- 示例数据（可按实际情况修改后导入）
-- =====================================================

INSERT IGNORE INTO `model` (`model_code`, `display_name`, `max_tokens`, `enabled`, `create_time`)
VALUES ('deepseek-chat', 'DeepSeek Chat', 4096, 1, UNIX_TIMESTAMP()*1000);

INSERT IGNORE INTO `provider` (`provider_code`, `base_url`, `api_key`, `enabled`, `create_time`)
VALUES
    ('deepseek-official', 'https://api.deepseek.com/v1', 'sk-9a4b6afb51fa498aa6a6c28b06449567', 1, UNIX_TIMESTAMP()*1000);

INSERT IGNORE INTO `model_provider`
    (`model_code`, `provider_code`, `priority`, `weight`, `timeout_ms`, `circuit_breaker_threshold`, `circuit_breaker_window`, `enabled`, `create_time`)
VALUES
    ('deepseek-chat', 'deepseek-official', 1, 10, 60000, 3, 30, 1, UNIX_TIMESTAMP()*1000));
