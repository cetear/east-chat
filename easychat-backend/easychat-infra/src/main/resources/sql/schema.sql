-- EasyChat 数据库完整初始化脚本（MySQL 8，2026-09-28）
-- 依据：common 实体、infra Mapper 及 migrations/20260927_p1.sql、20260927_p2.sql。
-- 用途：在空数据库中一次性创建全部表；不删除、覆盖或升级已有表。
-- 已合并 P1/P2 结构变更，新库执行本脚本后不要再执行上述历史迁移。
-- 已有数据库请核对当前结构后使用增量迁移，不要重复执行本脚本。
-- 执行前创建并选择数据库（数据库名可按部署配置调整）：
-- CREATE DATABASE easychat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE easychat;
-- 命令行示例：mysql --default-character-set=utf8mb4 -u <user> -p easychat < schema.sql
-- 时间由应用或 Mapper 写入；租约/事件调度以数据库 NOW(3) 为准。
-- 本脚本不包含模型、渠道或密钥等初始化业务数据。
--
--  1. chat_session          会话及用户归属
--  2. chat_session_config   会话参数预留表（尚无业务接入）
--  3. chat_message          消息、附件引用、执行状态及 usage
--  4. model                 模型定义
--  5. provider              渠道及熔断监测快照
--  6. model_provider        模型渠道路由配置
--  7. chat_request_log      请求日志预留表（尚无业务接入）
--  8. chat_session_memory   摘要及已覆盖消息游标
--  9. knowledge_doc         知识文档及索引发布版本
-- 10. execution_lease       分布式执行租约
-- 11. chat_event_outbox     事务事件及可靠投递状态

CREATE TABLE chat_session (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（数据库内部唯一标识）',
    session_code    VARCHAR(64) NOT NULL UNIQUE COMMENT '业务会话ID（对外暴露，用于API/前端）',
    owner_id        VARCHAR(128) COLLATE utf8mb4_bin NOT NULL DEFAULT 'demo' COMMENT '稳定用户ID，区分大小写；未启用认证时为demo',
    title           VARCHAR(255) COMMENT '会话标题（可由用户输入或模型自动生成）',
    system_prompt   TEXT COMMENT '系统提示词（用于控制模型行为，如角色设定）',
    max_rounds      INT DEFAULT 10 COMMENT '最大对话轮数（用于上下文裁剪，防止token过大）',
    status          TINYINT DEFAULT 1 COMMENT '会话状态：1=正常 0=关闭/归档',
    created_at      DATETIME(3) NOT NULL COMMENT '创建时间（毫秒级，便于排序/统计）',
    updated_at      DATETIME(3) NOT NULL COMMENT '更新时间（最后一次交互时间）',
    INDEX idx_session_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

CREATE TABLE chat_session_config (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id   BIGINT NOT NULL COMMENT '关联会话ID（chat_session.id）',
    config_json  JSON COMMENT '会话级推理参数(JSON)，如 temperature/top_p/max_tokens 等',
    created_at   DATETIME(3) COMMENT '创建时间',
    updated_at   DATETIME(3) COMMENT '更新时间',
    UNIQUE KEY uk_session (session_id) COMMENT '每个会话仅一份配置',
    CONSTRAINT fk_session_config
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话推理参数配置表';

CREATE TABLE chat_message (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id          BIGINT NOT NULL COMMENT '所属会话ID（chat_session.id）',
    role                VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant/system/tool',
    content             LONGTEXT NOT NULL COMMENT '消息内容（文本或JSON字符串）',
    content_type        VARCHAR(20) DEFAULT 'text' COMMENT '内容类型：text/json/tool（支持结构化消息）',
    message_order       INT NOT NULL COMMENT '消息顺序（保证上下文顺序）',
    model_code          VARCHAR(64) COMMENT '本次实际使用的模型（用于多模型追踪）',
    provider_code       VARCHAR(64) COMMENT '本次调用的渠道商（用于问题定位/路由分析）',
    prompt_tokens       INT COMMENT '输入token数（上下文消耗）',
    completion_tokens   INT COMMENT '输出token数（模型生成）',
    total_tokens        INT COMMENT '总token数（=输入+输出，用于计费）',
    finish_reason       VARCHAR(20) COMMENT '模型结束原因或error/cancelled/interrupted/max_iterations/context_limit',
    status              TINYINT DEFAULT 1 COMMENT '消息状态：0=失败 1=成功 2=运行中 3=取消',
    error_msg           VARCHAR(512) COMMENT '错误信息（失败时记录）',
    latency_ms          INT COMMENT '请求耗时（毫秒，用于性能分析和路由优化）',
    param_json          JSON COMMENT '消息扩展参数JSON，当前保存images附件引用',
    created_at          DATETIME(3) NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_session_message_order (session_id, message_order) COMMENT '同会话消息顺序唯一',
    INDEX idx_session_order (session_id, message_order) COMMENT '会话+顺序索引（高效加载上下文）',
    INDEX idx_created (created_at) COMMENT '时间索引（用于统计/归档）',
    CONSTRAINT fk_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

CREATE TABLE model (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_code            VARCHAR(64) NOT NULL UNIQUE COMMENT '模型唯一编码，如 deepseek-chat / gpt-4o',
    model_name            VARCHAR(128) COMMENT '模型展示名称',
    model_type            VARCHAR(32) NOT NULL DEFAULT 'chat' COMMENT '模型类型：chat/embedding/rerank/image/audio',
    model_family          VARCHAR(64) COMMENT '模型系列，如 deepseek/gpt/claude/qwen',
    context_window        INT COMMENT '上下文窗口 token 上限',
    max_output_tokens     INT COMMENT '最大输出 token 上限',
    default_temperature   DECIMAL(4,3) COMMENT '默认 temperature',
    default_top_p         DECIMAL(4,3) COMMENT '默认 top_p',
    default_config        JSON COMMENT '模型默认扩展参数，如 stop/seed/response_format/reasoning_effort',
    support_vision        TINYINT DEFAULT 0 COMMENT '是否支持图片输入：1=支持 0=不支持',
    enabled               TINYINT DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
    created_at            DATETIME(3) COMMENT '创建时间',
    updated_at            DATETIME(3) COMMENT '更新时间',
    INDEX idx_model_type (model_type),
    INDEX idx_model_family (model_family),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型定义表';

CREATE TABLE provider (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    provider_code   VARCHAR(64) NOT NULL UNIQUE COMMENT '渠道标识（如 openai / azure / deepseek）',
    base_url        VARCHAR(256) COMMENT 'API基础地址',
    api_key         VARCHAR(512) COMMENT '访问密钥（建议加密存储）',
    enabled         TINYINT DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
    fail_count      INT DEFAULT 0 COMMENT '连续失败次数监测快照；实时熔断状态由进程维护',
    last_fail_time  DATETIME(3) COMMENT '最后失败时间',
    circuit_status  VARCHAR(20) DEFAULT 'CLOSED' COMMENT '熔断状态：CLOSED/OPEN/HALF_OPEN',
    created_at      DATETIME(3) COMMENT '创建时间',
    updated_at      DATETIME(3) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道商配置表';

CREATE TABLE model_provider (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_code      VARCHAR(64) NOT NULL COMMENT '模型标识',
    provider_code   VARCHAR(64) NOT NULL COMMENT '渠道标识',
    priority        INT DEFAULT 0 COMMENT '优先级（越小越优先）',
    weight          INT DEFAULT 1 COMMENT '权重（用于负载均衡）',
    timeout_ms      INT DEFAULT 60000 COMMENT '超时时间（毫秒）',
    max_retry       INT DEFAULT 0 COMMENT '最大重试次数',
    enabled         TINYINT DEFAULT 1 COMMENT '是否启用',
    avg_latency_ms  INT COMMENT '平均响应时间预留指标',
    success_rate    DOUBLE COMMENT '成功率预留指标',
    last_used_time  DATETIME(3) COMMENT '最后调用时间',
    created_at      DATETIME(3) COMMENT '创建时间',
    updated_at      DATETIME(3) COMMENT '更新时间',
    UNIQUE KEY uk_mp (model_code, provider_code) COMMENT '唯一约束（一个模型对应一个渠道唯一配置）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型-渠道路由配置表';

CREATE TABLE chat_request_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id      BIGINT COMMENT '会话ID',
    message_id      BIGINT COMMENT '消息ID',
    model_code      VARCHAR(64) COMMENT '使用模型',
    provider_code   VARCHAR(64) COMMENT '使用渠道',
    request_json    JSON COMMENT '请求参数（完整记录）',
    response_json   JSON COMMENT '响应结果（完整记录）',
    status          TINYINT COMMENT '执行状态：1=成功 0=失败',
    error_msg       VARCHAR(512) COMMENT '错误信息',
    latency_ms      INT COMMENT '请求耗时',
    created_at      DATETIME(3) COMMENT '请求时间',
    INDEX idx_session (session_id) COMMENT '会话索引',
    INDEX idx_created (created_at) COMMENT '时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求日志表';

CREATE TABLE chat_session_memory (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id      BIGINT NOT NULL COMMENT '会话ID',
    summary         TEXT COMMENT '对话摘要（用于压缩上下文）',
    covered_message_id BIGINT DEFAULT 0 COMMENT '摘要已覆盖的最后消息ID；0表示尚未覆盖',
    embedding       BLOB COMMENT '向量记忆预留字段，当前未接入',
    version         INT DEFAULT 1 COMMENT '版本号（支持多次摘要迭代）',
    created_at      DATETIME(3) COMMENT '创建时间',
    INDEX idx_session (session_id) COMMENT '会话索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话记忆表';

CREATE TABLE knowledge_doc (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    doc_code    VARCHAR(64) NOT NULL UNIQUE COMMENT '对外业务ID(UUID)，API 全用它',
    owner_id    VARCHAR(128) COLLATE utf8mb4_bin NOT NULL DEFAULT 'demo' COMMENT '稳定用户ID，区分大小写',
    file_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type   VARCHAR(20) COMMENT '文件扩展名：文档/图片/音视频，允许范围由上传校验定义',
    file_path   VARCHAR(512) COMMENT '存储引用：本地路径或s3://bucket/key',
    file_hash   VARCHAR(64) COMMENT 'sha256 去重',
    dataset     VARCHAR(64) DEFAULT 'default' COMMENT '知识库标识',
    index_version VARCHAR(64) DEFAULT NULL COMMENT '当前入库/发布版本，与ES generation联合过滤',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/INDEXING/INDEXED/FAILED/DELETING/DELETE_FAILED',
    error_msg   VARCHAR(512) COMMENT '失败原因',
    created_at  DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at  DATETIME(3) NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_owner_dataset_hash (owner_id, dataset, file_hash) COMMENT '同用户同知识库文件去重',
    INDEX idx_status (status) COMMENT '状态索引（用于启动恢复）',
    INDEX idx_dataset (dataset) COMMENT '知识库索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

CREATE TABLE execution_lease (
    lock_key     VARCHAR(512) COLLATE utf8mb4_bin PRIMARY KEY COMMENT '资源键：session:内部ID或doc:文档编码；区分大小写',
    owner_token  VARCHAR(64) NOT NULL COMMENT '持有者令牌；创建占位行时为空字符串',
    expires_at   DATETIME(3) NOT NULL COMMENT '数据库时间计算的到期时间，续租和释放均校验令牌',
    INDEX idx_lease_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式执行租约表';

CREATE TABLE chat_event_outbox (
    event_id      VARCHAR(64) PRIMARY KEY COMMENT '稳定事件ID；重投保持不变，订阅者据此去重',
    event_type    VARCHAR(64) NOT NULL COMMENT 'ChatMessageCreatedEvent或ChatCompletedEvent',
    payload       LONGTEXT NOT NULL COMMENT '事件JSON载荷，由统一Jackson序列化',
    status        VARCHAR(16) NOT NULL COMMENT '投递状态：PENDING/INFLIGHT/DONE',
    attempts      INT NOT NULL DEFAULT 0 COMMENT '累计领取次数，每次成功领取递增',
    available_at  DATETIME(3) NOT NULL COMMENT '下次允许领取时间，失败后退避',
    owner_token   VARCHAR(64) COMMENT '当前领取者令牌，确认及失败更新须匹配',
    lease_until   DATETIME(3) COMMENT '领取租约到期时间，过期后允许重领',
    created_at    DATETIME(3) NOT NULL COMMENT '事件写入时间，与成功消息同事务',
    delivered_at  DATETIME(3) COMMENT '最近成功确认投递时间',
    last_error    VARCHAR(512) COMMENT '最近投递失败摘要',
    INDEX idx_outbox_pending (status, available_at),
    INDEX idx_outbox_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务事件投递表（至少一次交付）';
