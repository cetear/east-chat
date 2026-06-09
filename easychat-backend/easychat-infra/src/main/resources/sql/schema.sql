-- 在 MySQL 中执行前请先创建数据库：
-- CREATE DATABASE easychat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE easychat;
--1. chat_session              会话主表
--2. chat_session_config       会话参数表
--3. chat_message              消息表
--4. model                     模型表
--5. provider                  渠道商表
--6. model_provider            路由配置表
--7. chat_request_log          请求日志
--8. chat_session_memory       上下文压缩/RAG

CREATE TABLE chat_session (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID（数据库内部唯一标识）',
    session_code    VARCHAR(64) NOT NULL UNIQUE COMMENT '业务会话ID（对外暴露，用于API/前端）',
    title           VARCHAR(255) COMMENT '会话标题（可由用户输入或模型自动生成）',
    model_code      VARCHAR(64) NOT NULL COMMENT '默认使用的模型标识（如 gpt-4o / deepseek-chat）',
    system_prompt   TEXT COMMENT '系统提示词（用于控制模型行为，如角色设定）',
    max_rounds      INT DEFAULT 10 COMMENT '最大对话轮数（用于上下文裁剪，防止token过大）',
    status          TINYINT DEFAULT 1 COMMENT '会话状态：1=正常 0=关闭/归档',
    created_at      DATETIME(3) NOT NULL COMMENT '创建时间（毫秒级，便于排序/统计）',
    updated_at      DATETIME(3) NOT NULL COMMENT '更新时间（最后一次交互时间）',
    INDEX idx_model (model_code) COMMENT '模型索引（用于按模型统计/查询）'
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
    finish_reason       VARCHAR(20) COMMENT '结束原因：stop/length/tool_calls/content_filter',
    status              TINYINT DEFAULT 1 COMMENT '执行状态：1=成功 0=失败',
    error_msg           VARCHAR(512) COMMENT '错误信息（失败时记录）',
    latency_ms          INT COMMENT '请求耗时（毫秒，用于性能分析和路由优化）',
    param_json          JSON COMMENT '消息级参数覆盖（用于重试/Agent动态控制）',
    created_at          DATETIME(3) NOT NULL COMMENT '创建时间',
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
    fail_count      INT DEFAULT 0 COMMENT '连续失败次数（用于熔断判断）',
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
    avg_latency_ms  INT COMMENT '平均响应时间（用于动态路由）',
    success_rate    DOUBLE COMMENT '成功率（用于健康评估）',
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
    embedding       BLOB COMMENT '向量数据（用于语义检索，可接向量数据库）',
    version         INT DEFAULT 1 COMMENT '版本号（支持多次摘要迭代）',
    created_at      DATETIME(3) COMMENT '创建时间',
    INDEX idx_session (session_id) COMMENT '会话索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话记忆表';
