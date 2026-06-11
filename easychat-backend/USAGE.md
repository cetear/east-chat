# EasyChat Backend 使用文档

## 1. 项目说明

EasyChat Backend 是一个基于 Java 17 和 Spring Boot 3 的 AI 聊天后端项目，采用 Maven 多模块结构组织代码。

项目主要能力包括：

- 聊天会话创建、查询、更新和删除。
- 同步聊天接口。
- SSE 流式聊天接口。
- 模型、渠道商、模型渠道路由配置管理。
- 多渠道模型路由和失败降级。
- Agent 编排、对话记忆、工具调用扩展、RAG 扩展。
- 核心事件边界和无外部依赖的默认事件发布实现。

当前项目暂不使用 Kafka。业务事件通过 `ChatEventPublisher` 抽象保留扩展点，默认实现是空发布器。

## 2. 技术栈

| 类型 | 技术 |
| --- | --- |
| 开发语言 | Java 17 |
| 应用框架 | Spring Boot 3.2.5 |
| 构建工具 | Maven 多模块 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.x |
| 连接池 | Druid |
| LLM 接入 | LangChain4j 0.36.0 |
| 流式处理 | Reactor `Flux` |
| 搜索扩展 | Elasticsearch Java Client |
| 辅助工具 | Lombok |

## 3. 项目模块结构

```text
easychat-backend
├── easychat-app          # Spring Boot 启动入口
├── easychat-api          # REST Controller 和 API DTO
├── easychat-core         # 领域模型、端口接口、用例服务、Agent、路由
├── easychat-infra        # MySQL 实体/Mapper、Elasticsearch 基础设施
├── easychat-llm          # LLM Client 和 Provider 实现
├── easychat-memory       # 对话记忆实现
├── easychat-rag          # Embedding 和检索接口
├── easychat-tools        # 工具接口和工具注册中心
├── easychat-multimodal   # 多模态扩展模块
├── easychat-common       # 通用返回、异常、常量、工具类
└── easychat-test         # 集成测试模块
```

## 4. 框架结构

当前项目正在向“分层架构 + 端口适配器”方向演进。

整体调用关系：

```text
HTTP API
  -> Facade / Use Case
      -> Domain Model
      -> Port
          -> MySQL Adapter
          -> LLM Router / Client
          -> Memory / RAG / Tool Capability
```

核心包说明：

```text
com.easychat.core.domain       # 领域模型
com.easychat.core.port         # Repository、Client、Event 等端口接口
com.easychat.core.service      # 应用用例服务
com.easychat.core.adapter      # 当前适配器实现
com.easychat.core.capability   # Agent 能力扩展点
com.easychat.core.event        # 核心事件模型
com.easychat.core.router       # 模型渠道路由
com.easychat.core.agent        # Agent 和 ReAct 执行逻辑
```

聊天主链路：

```text
ChatController
  -> AgentFacade
      -> ChatUseCase / StreamChatUseCase / SessionUseCase
          -> ReActAgent
              -> ModelRouter
                  -> ProviderRegistry
                  -> LLMProvider
          -> ChatSessionRepository / ChatMessageRepository
```

## 5. 数据库说明

数据库初始化脚本：

```text
easychat-infra/src/main/resources/sql/schema.sql
```

主要数据表：

| 表名 | 作用 |
| --- | --- |
| `chat_session` | 聊天会话主表 |
| `chat_session_config` | 会话级模型参数配置 |
| `chat_message` | 聊天消息和模型调用信息 |
| `model` | 模型定义表 |
| `provider` | 渠道商配置表 |
| `model_provider` | 模型与渠道商路由配置表 |
| `chat_request_log` | 请求和响应日志 |
| `chat_session_memory` | 会话记忆和摘要 |

启动项目之前，需要先创建数据库并执行 `schema.sql`。

## 6. 环境要求

必需环境：

- JDK 17
- Maven 3.9+
- MySQL 8.x

检查 Java 和 Maven：

```powershell
java -version
mvn -version
```

如果 Maven 仍然使用 Java 8，需要显式设置 Java 17：

```powershell
$env:JAVA_HOME="D:\Java\jdk-17\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

## 7. 配置说明

主配置文件：

```text
easychat-app/src/main/resources/application.yml
```

核心配置示例：

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://host:port/database?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: your_user
    password: your_password

easychat:
  llm:
    api-key: ${OPENAI_API_KEY:XXXXXXXXXXXX}
    base-url: ${OPENAI_BASE_URL:https://api.deepseek.com/v1}
    model-name: ${OPENAI_MODEL:deepseek-chat}
    temperature: 0.7
    max-tokens: 2000
```

推荐使用环境变量配置 LLM：

```powershell
$env:OPENAI_API_KEY="your_api_key"
$env:OPENAI_BASE_URL="https://api.deepseek.com/v1"
$env:OPENAI_MODEL="deepseek-chat"
```

## 8. 构建与启动

编译全部模块：

```powershell
mvn clean compile -DskipTests
```

编译 API 及其依赖：

```powershell
mvn -pl easychat-api -am compile -DskipTests
```

编译测试模块但不执行测试：

```powershell
mvn -pl easychat-test -am test-compile -DskipTests
```

启动应用：

```powershell
mvn -pl easychat-app -am spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

启动入口：

```text
easychat-app/src/main/java/com/easychat/app/EasyChatApplication.java
```

## 9. 接口使用说明

### 9.1 聊天接口

同步聊天：

```http
POST /api/chat
Content-Type: application/json
```

请求示例：

```json
{
  "sessionId": null,
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ],
  "toolsEnabled": false,
  "ragEnabled": false
}
```

响应示例：

```json
{
  "content": "助手回复内容",
  "sessionId": "session-code"
}
```

流式聊天：

```http
POST /api/chat/stream
Content-Type: application/json
```

响应格式为 SSE，事件类型包括：

- `message`
- `thought`
- `action`
- `observation`
- `error`
- `finish`

### 9.2 会话接口

创建会话：

```http
POST /api/session
Content-Type: application/json
```

创建会话不绑定模型，请求体可以为空。模型在聊天请求中通过 `model` 字段实时传入，方便同一会话内切换模型。

```json
{}
```

查询会话列表：

```http
GET /api/sessions
```

查询单个会话：

```http
GET /api/session/{sessionId}
```

更新会话：

```http
PUT /api/session/{sessionId}
Content-Type: application/json
```

设置最大上下文轮数：

```http
PUT /api/session/{sessionId}/max-rounds
Content-Type: application/json
```

请求示例：

```json
{
  "maxRounds": 10
}
```

删除会话：

```http
DELETE /api/session/{sessionId}
```

### 9.3 渠道商接口

创建渠道商：

```http
POST /model/provider/create
Content-Type: application/json
```

请求示例：

```json
{
  "providerCode": "deepseek",
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "your_api_key",
  "enabled": 1
}
```

查询渠道商列表：

```http
GET /model/provider/list
```

渠道商响应不会返回明文 `apiKey`，只返回：

- `apiKeyConfigured`
- `apiKeyMasked`

根据数据库 ID 查询渠道商：

```http
GET /model/provider/{id}
```

更新渠道商：

```http
POST /model/provider/update
Content-Type: application/json
```

删除渠道商：

```http
DELETE /model/provider/{providerCode}
```

### 9.4 模型接口

创建模型：

```http
POST /model/create
Content-Type: application/json
```

请求示例：

```json
{
  "modelCode": "deepseek-chat",
  "maxTokens": 4096,
  "defaultConfig": "{\"temperature\":0.7}",
  "enabled": 1
}
```

更新模型：

```http
POST /model/update
```

删除模型：

```http
POST /model/delete
```

### 9.5 模型渠道路由接口

为渠道商绑定模型：

```http
POST /model/provider/{providerCode}/models
Content-Type: application/json
```

请求示例：

```json
{
  "modelCode": "deepseek-chat",
  "priority": 0,
  "weight": 1,
  "timeoutMs": 60000,
  "maxRetry": 0,
  "enabled": 1
}
```

查询渠道商下的模型路由：

```http
GET /model/provider/{providerCode}/models
```

删除模型渠道路由：

```http
DELETE /model/provider/{providerCode}/models/{modelCode}
```

## 10. 模型路由机制

模型路由依赖以下字段：

- `model.model_code`
- `provider.provider_code`
- `model_provider.model_code`
- `model_provider.provider_code`
- `model_provider.priority`
- `model_provider.enabled`

运行逻辑：

1. `ProviderRegistry` 定时从数据库加载启用的渠道商和模型路由。
2. `ModelRouter` 根据 `modelCode` 查询可用渠道。
3. 渠道按 `priority` 升序排序。
4. 如果没有数据库路由配置，则使用默认 `LangChain4jLLMClient`。
5. 如果某个渠道调用失败，则尝试下一个可用渠道。

## 11. 扩展点说明

### 11.1 Agent Capability

目录：

```text
easychat-core/src/main/java/com/easychat/core/capability
```

用于在 Agent 执行前后扩展能力，例如：

- 对话记忆压缩
- RAG 检索增强
- 工具调用
- 多模态输入处理
- 审计和指标增强

### 11.2 领域事件

目录：

```text
easychat-core/src/main/java/com/easychat/core/event
```

当前事件包括：

- `ChatMessageCreatedEvent`
- `ChatCompletedEvent`
- `ProviderCallFailedEvent`
- `SessionSummaryRequestedEvent`
- `EmbeddingIndexRequestedEvent`

默认事件发布器是空实现，当前项目没有 Kafka 依赖。

### 11.3 工具扩展

目录：

```text
easychat-tools
```

新增工具时实现 `Tool` 接口，并注册为 Spring Bean。`ToolRegistry` 会自动发现可用工具。

## 12. 开发约定

- API 层不要直接返回 infra DO 对象。
- Controller 保持轻量，只负责 HTTP 入参和返回。
- 业务逻辑放在 Use Case 或 Service 中。
- 数据库访问尽量通过 Repository Port 隔离。
- 业务标识优先使用 `session_code`、`model_code`、`provider_code`。
- 不要在接口响应中返回渠道商明文 `apiKey`。
- 后续数据库结构演进建议使用 Flyway 或 Liquibase，而不是只修改 `schema.sql`。

## 13. 常用命令

搜索代码：

```powershell
rg "keyword" .
```

编译 core：

```powershell
mvn -pl easychat-core -am compile -DskipTests
```

编译 app：

```powershell
mvn -pl easychat-app -am compile -DskipTests
```

编译测试模块：

```powershell
mvn -pl easychat-test -am test-compile -DskipTests
```
