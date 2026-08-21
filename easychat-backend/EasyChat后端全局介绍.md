# EasyChat Backend 后端全局介绍

> 文档目的：帮助新手或 AI 初次接入时快速理解 EasyChat 后端的模块划分、架构设计、核心流程与接口约定。
> 适用项目：`easychat-backend`
> 生成日期：2026-08-21

---

## 1. 项目概述

EasyChat Backend 是一个基于 **Java 17 + Spring Boot 3** 的 AI 对话后端，采用 **Maven 多模块** 结构组织代码。

核心能力：

- 聊天会话的创建、查询、更新、删除。
- 同步聊天（`POST /api/chat`）与 SSE 流式聊天（`POST /api/chat/stream`）。
- 模型（Model）、渠道商（Provider）、模型-渠道路由（ModelProvider）的配置管理。
- 多渠道模型路由、失败降级、简单熔断。
- Agent 编排（ReAct 循环）、对话记忆与自动摘要、工具调用（Tool）、RAG 检索、多模态图片输入。
- 模型能力门控：`support_vision` 区分模型是否支持图片输入，仅对支持模型开放。
- Elasticsearch 文档写入、查询，以及可供大模型调用的 ES 查询工具。
- 业务事件抽象（默认空实现，不依赖 Kafka）。

---

## 2. 技术栈

| 类型 | 技术 |
| --- | --- |
| 开发语言 | Java 17 |
| 应用框架 | Spring Boot 3.4.13 |
| 构建工具 | Maven 多模块 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.x |
| 连接池 | Druid |
| LLM 接入 | LangChain4j 0.36.0 |
| 流式处理 | Reactor `Flux` |
| 搜索引擎 | Elasticsearch 8.13.0（`elasticsearch-java`） |
| 辅助工具 | Lombok、Jackson |

---

## 3. 模块结构总览

根 `pom.xml` 聚合以下 6 个模块：

| 模块 | 职责 | 关键包/类 |
| --- | --- | --- |
| `easychat-common` | 通用模型、异常、常量、工具 | `Result`、`BusinessException`、`MessageRole`、`SSEUtil` |
| `easychat-infra` | 基础设施：MySQL、Elasticsearch | DO/Mapper、`EsClientWrapper`、`EsConfig`、`schema.sql` |
| `easychat-core` | 领域、用例、Agent、路由、LLM 客户端、能力 | `ReActAgent`、`AgentFacade`、`ModelRouter`、`LLMClient`、`ConversationMemory`、`ToolRegistry` |
| `easychat-api` | REST Controller 与 DTO | `ChatController`、`ModelController`、`EsController` |
| `easychat-app` | Spring Boot 启动入口与配置 | `EasyChatApplication`、`application.yml` |
| `easychat-test` | 集成测试 | `AgentFacadeTest` |

模块依赖方向（自下而上）：

```text
common
  └─ infra
       └─ core
            └─ api
                 └─ app
```

> 说明：`easychat-core` 由原 `easychat-core`、`easychat-llm`、`easychat-capability`（含 memory/rag/tools）三合一而来；原 `easychat-multimodal` 因无实现已移除，图片能力实现在 `easychat-llm` 包（现位于 core 内）。

---

## 4. 各模块详细说明

### 4.1 easychat-common

提供全项目共享的基础能力：

- `com.easychat.common.model.Result<T>`：统一响应包装（`success(data)` / `error(msg)`）。
- `com.easychat.common.exception`：`BusinessException`。
- `com.easychat.common.constant`：`MessageRole` 等常量。
- `com.easychat.common.util.SSEUtil`：SSE 事件封装（`message/thought/action/observation/error/finish`）。

### 4.2 easychat-infra

基础设施层，集中存放与外部系统交互的实现：

- MySQL：
  - `mysql.config.MybatisPlusConfig`：MyBatis-Plus 配置（驼峰映射等）。
  - `mysql.entity.*DO`：`ChatSessionDO`、`ChatMessageDO`、`ChatSessionMemoryDO`、`ModelDO`、`ProviderDO`、`ModelProviderDO`。
  - `mysql.mapper.*`：对应的 MyBatis-Plus Mapper 接口。
  - `resources/sql/schema.sql`：数据库初始化脚本（6 张表，`chat_request_log`、`chat_session_config` 未接入已移除）。
- Elasticsearch：
  - `es.EsConfig`：手动构建 `ElasticsearchClient`（`@Primary`，支持 HTTPS、跳过证书校验、用户名密码认证）。
  - `es.EsClientWrapper`：封装常用操作：
    - `search(index, query, size)`：按 `content` 字段 match 查询。
    - `searchAll(index, size)`：`match_all` 查询全部。
    - `indexDocument(index, id, document)`：写入单个文档。

### 4.3 easychat-core

核心模块，聚合三个层次的代码（包结构保持不变）：

**LLM 接入（`com.easychat.llm`）**

- `client.LLMClient`：同步/流式聊天接口（含图片感知重载）。
- `provider.LLMProvider`：渠道商抽象接口（`extends LLMClient`，增加 `getProviderCode`/`isHealthy`）。
- `provider.OpenAICompatibleProvider`：OpenAI 兼容渠道实现（支持 DeepSeek、Azure OpenAI 等；通过 `UserMessage` + `ImageContent` 构建多模态消息），同时作为默认兜底客户端。
- `config.LLMProperties`：读取 `easychat.llm.*` 配置。
- `client.LLMCallOptions`：单次调用的参数覆盖（model/temperature/topP/maxTokens/stop/seed 等）。

**Agent 能力（`com.easychat.memory` / `com.easychat.rag` / `com.easychat.tools`）**

- 记忆与摘要：`ConversationMemory`、`store.DbConversationMemory`、`summary.SummaryService`、`summary.impl.LlmSummaryService`。
- RAG 检索（阶段 A）：`retriever.Retriever`、`retriever.impl.EsRetriever`。
- 工具系统：`Tool`、`ToolRegistry`、`impl.MysqlQueryTool`、`impl.EsQueryTool`。新增工具实现 `Tool` 并标注 `@Component` 即自动注册。

**业务核心（`com.easychat.core`）**

- `domain`：领域模型（`chat.ChatSession`、`chat.ChatMessage`、`model.*`）。
- `port`：端口接口（`ChatSessionRepository`、`ChatMessageRepository`、`ModelCatalogRepository`、`ChatModelClient`、`ChatEventPublisher`）。
- `adapter.mysql`：MySQL 适配器实现（Repository 实现）。
- `agent`：Agent 编排（`ReActAgent`、`StreamingAgent`、`ReActStep`、`ReActOutputParser`、`AgentEvent`、`AgentResult`）。
- `capability`：Agent 能力扩展点（`AgentCapability`、`AgentCapabilityRegistry`；`ConversationMemoryCapability`、`RagRetrievalCapability`、`ToolCallingCapability`，按 `@Order` 排序执行）。
- `context`：`AgentContext`、`ChatExecutionContext`（运行上下文）。
- `event`：领域事件（`ChatMessageCreatedEvent`、`ChatCompletedEvent`）与 `NoopChatEventPublisher`。
- `facade.AgentFacade`：对上层（API）暴露的统一门面。
- `router`：模型路由（`ModelRouter`、`ProviderRegistry`、`ProviderWrapper`、`RouterConfig`）。
- `service.chat`：聊天用例（`ChatUseCase`、`StreamChatUseCase`、`SessionUseCase`、`ChatContextBuilder`）。
- `service.model`：模型/渠道管理（`ModelManageService` 等）。

### 4.4 easychat-api

REST 接口层：

- `config.WebConfig`：跨域等 Web 配置。
- `controller.ChatController`：聊天与会话接口（前缀 `/api`）。
- `controller.ModelController`：模型/渠道/路由管理（前缀 `/model`）。
- `controller.EsController`：Elasticsearch 接口（前缀 `/es`）。
- `dto`：`ChatRequest`、`MessageDTO`、`EsDocumentRequest`、模型/渠道相关 DTO。

### 4.5 easychat-app

启动模块：`EasyChatApplication`（`@SpringBootApplication(scanBasePackages = "com.easychat")`、`@EnableScheduling`），配置集中在 `application.yml`。

### 4.6 easychat-test

集成测试模块，使用 `@SpringBootTest` 启动完整上下文，覆盖会话创建、列表查询与 ES 连通性。

---

## 5. 架构设计

### 5.1 整体分层

```text
HTTP API (easychat-api)
  → Facade / Use Case (AgentFacade → ChatUseCase / StreamChatUseCase / SessionUseCase)
      → Domain Model (domain)
      → Port (port 接口)
          → MySQL Adapter (adapter.mysql / infra)
          → LLM Router / Client (router + llm)
          → Memory / RAG / Tool Capability
```

### 5.2 聊天主链路

```text
ChatController
  → AgentFacade
      → StreamChatUseCase / ChatUseCase
          → ChatContextBuilder（会话解析 + 上下文构建）
          → ReActAgent
              → ModelRouter
                  → ProviderRegistry（渠道缓存）
                  → OpenAICompatibleProvider（实际 LLM 调用）
              → ConversationMemory（读取历史/摘要）
              → ToolRegistry（工具调用）
      → ChatSessionRepository / ChatMessageRepository（持久化）
```

### 5.3 Agent / ReAct 执行模型

`ReActAgent` 支持两种模式：

1. **简单模式**（默认，`toolsEnabled=false`）：直接拼接历史 + 用户消息调用 LLM，逐 token 返回。
2. **ReAct 工具模式**（`toolsEnabled=true`）：加载 `prompts/react-system.txt` 模板，把已注册工具描述注入 system prompt，按 `Thought → Action → Action Input → Observation → ... → Final Answer` 循环执行工具，最多 `maxIterations`（默认 5）轮。

SSE 事件类型：`message`、`thought`、`action`、`observation`、`error`、`finish`。

### 5.4 模型路由与熔断

- `ProviderRegistry`：启动时从 DB 加载启用渠道与路由，每 5 分钟热刷新，缓存为 `modelCode → List<ProviderWrapper>`（按 `priority` 升序）。
- `ModelRouter`：按 `modelCode` 取可用渠道列表，逐个尝试，失败降级到下一个；全部失败抛出异常。无 DB 路由时回退到默认配置构建的 `OpenAICompatibleProvider`（单渠道兜底）。
- `ProviderWrapper`：简单滑动计数熔断（连续失败达阈值进入冷却期，冷却后自动半开放）。
- `RouterConfig`：`modelRouter` 标记 `@Primary`，替换所有 `LLMClient` 注入点。

### 5.5 能力扩展管线（Capability Pipeline）

`ChatUseCase` / `StreamChatUseCase` 在 Agent 执行前后调用 `AgentCapabilityRegistry`：

```text
beforeRun: RagRetrievalCapability（ragEnabled 时检索 → 注入 retrievedContext）
   → ReActAgent 执行（读取摘要、检索结果、图片）
afterRun:  ConversationMemoryCapability（达阈值 → LLM 摘要 → 持久化）
```

- 摘要与检索结果由 `ReActAgent.buildPrompt` 前置注入 prompt。
- 图片输入通过 `ChatExecutionContext.images` 透传到 `ModelRouter`，再交给 `LLMProvider` 构建多模态消息；`toolsEnabled=true` 的 ReAct 模式不附带图片。

---

## 6. 数据库设计

初始化脚本：`easychat-infra/src/main/resources/sql/schema.sql`，共 6 张表：

| 表名 | 作用 |
| --- | --- |
| `chat_session` | 会话主表（`session_code` 为对外业务 ID） |
| `chat_message` | 消息表（含模型/渠道/token/耗时等追踪字段） |
| `model` | 模型定义表（含默认参数与 `support_vision` 能力标记） |
| `provider` | 渠道商配置表（含熔断状态） |
| `model_provider` | 模型-渠道路由配置表（优先级/权重/超时/重试） |
| `chat_session_memory` | 会话记忆与摘要 |

> 注：原 `chat_request_log`（请求日志）、`chat_session_config`（会话级参数）两张表因未接入业务已从 schema 移除，如需可后续补回。

---

## 7. API 接口总览

默认地址：`http://localhost:8080`

### 7.1 聊天与会话（前缀 `/api`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat` | 同步聊天（支持 `toolsEnabled`/`ragEnabled`） |
| POST | `/api/chat/stream` | SSE 流式聊天 |
| POST | `/api/session` | 创建会话 |
| GET | `/api/sessions` | 会话列表 |
| GET | `/api/session/{sessionId}` | 查询单个会话 |
| PUT | `/api/session/{sessionId}` | 更新会话 |
| DELETE | `/api/session/{sessionId}` | 删除会话 |
| PUT | `/api/session/{sessionId}/max-rounds` | 设置最大上下文轮数 |

聊天请求体（`ChatRequest`）：

```json
{
  "sessionId": null,
  "model": "deepseek-chat",
  "messages": [{ "role": "user", "content": "你好" }],
  "toolsEnabled": false,
  "ragEnabled": false
}
```

多模态（图片）消息示例（仅 `support_vision=1` 的模型支持，否则返回 400）：

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": "这张图里是什么？",
      "images": ["https://example.com/img.png"]
    }
  ],
  "toolsEnabled": false,
  "ragEnabled": false
}
```

> 注意：`sessionId` 实指 `sessionCode`（业务 ID），非数据库数字 ID。为空时后端自动创建会话。

### 7.2 模型/渠道管理（前缀 `/model`）

主要包括：`POST /model/provider/create`、`GET /model/provider/list`、`POST /model/provider/update`、`DELETE /model/provider/{providerCode}`、`POST /model/create`、`GET /model/list`、`POST /model/update`、`POST /model/delete`、`POST /model/addModelToProvider`、`GET /model/provider/{providerCode}/models`、`DELETE /model/provider/{providerCode}/models/{modelCode}` 等。详见 `ModelController.java`。

### 7.3 Elasticsearch（前缀 `/es`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/es/test` | ES 连通性测试 |
| GET | `/es/documents?index=easychat&size=100` | 查询索引内全部文档（验证存储） |
| POST | `/es/document` | 写入单个文档 |

写入请求体（`EsDocumentRequest`）：

```json
{
  "index": "easychat",
  "id": "doc_001",
  "document": {
    "title": "EasyChat 项目介绍",
    "content": "EasyChat 是一个基于 Spring Boot 的多模块 AI 对话后端项目"
  }
}
```

---

## 8. 配置说明

主配置文件：`easychat-app/src/main/resources/application.yml`

关键配置块：

- `spring.datasource.*`：MySQL 连接与 Druid 连接池。
- `spring.elasticsearch.*`：ES 地址、账号密码、SSL（`uris/username/password`）。
- `mybatis-plus.*`：MyBatis-Plus 配置。
- `easychat.llm.*`：默认 LLM 配置（`api-key`、`base-url`、`model-name`、`temperature`、`max-tokens`）。
- `easychat.memory.*`：摘要触发阈值（`summary-threshold`）、摘要读取消息数（`summary-max-messages`）。
- `easychat.rag.*`：检索索引（`index`）、召回条数（`top-k`）。
- `easychat.es.index`（可选）：`EsQueryTool` 默认索引，缺省 `easychat`。

LLM 推荐使用环境变量注入：

```powershell
$env:OPENAI_API_KEY="..."
$env:OPENAI_BASE_URL="https://api.deepseek.com/v1"
$env:OPENAI_MODEL="deepseek-chat"
```

> 安全提示：`application.yml` 当前含明文数据库/ES/LLM 凭据，生产部署前应改为环境变量占位并轮换密钥。

---

## 9. 构建与启动

```powershell
# 全量编译
mvn clean compile -DskipTests

# 编译指定模块及其依赖
mvn -pl easychat-api -am compile -DskipTests

# 启动应用（端口 8080）
mvn -pl easychat-app -am spring-boot:run
```

启动入口：`easychat-app/src/main/java/com/easychat/app/EasyChatApplication.java`

启动前需准备：JDK 17、Maven 3.9+、MySQL 8.x（执行 `schema.sql`），并确保 ES 与 LLM 服务可访问。

---

## 10. 扩展点说明

### 10.1 新增 Agent 工具

实现 `com.easychat.tools.Tool` 接口并标注 `@Component`，`ToolRegistry` 会自动注册。工具在 `toolsEnabled=true` 时对 ReAct Agent 可见。

### 10.2 Agent Capability

实现 `com.easychat.core.capability.AgentCapability`，提供 `supports / beforeRun / afterRun` 钩子，用于记忆压缩、RAG、审计等切面，通过 `@Order` 控制执行顺序。已实现 `ConversationMemoryCapability`（摘要）、`RagRetrievalCapability`（检索）、`ToolCallingCapability`（工具开关）。

### 10.3 领域事件

实现 `com.easychat.core.port.ChatEventPublisher` 替换默认 `NoopChatEventPublisher`，即可接入消息队列等下游。

---

## 11. 关键术语

- **sessionCode**：会话对外业务 ID，前端/接口使用；数据库另有自增 `id`。
- **modelCode / providerCode**：模型/渠道的唯一编码，路由依赖这两个字段。
- **ReAct**：`Thought/Action/Observation` 推理-行动循环，用于工具调用。
- **Port / Adapter**：端口-适配器架构，`core.port` 定义接口，`adapter/infra` 提供实现。

---

## 12. 已知限制与演进方向

1. `core` 仍直接依赖 `infra` 的 Mapper/DO（`core.adapter.mysql`、`ProviderRegistry`、`ConversationMemory` 等直接使用 infra Mapper），端口-适配器边界未完全收敛。
2. RAG 目前为关键词检索（阶段 A）；向量语义检索（阶段 B）需引入 embedding 模型后实施。
3. Controller 缺少统一 Bean Validation 与全局异常处理。
4. 测试连接真实 MySQL/ES，缺少隔离的 test profile。
5. 配置文件中存在明文凭据，需脱敏。
6. 非流式 `/api/chat` 的工具调用已支持（需 `toolsEnabled=true`），流式/非流式行为一致。
7. ES 查询/检索匹配字段固定为 `content`，写入文档建议包含 `content` 字段。
8. ReAct 工具模式（`toolsEnabled=true`）下图片不附带；图片仅在 `support_vision=1` 的模型上开放。

---

## 13. 常用命令

```powershell
rg "keyword" .                                    # 全局搜索
mvn -pl easychat-core -am compile -DskipTests      # 编译 core
mvn -pl easychat-app -am compile -DskipTests       # 编译 app
mvn -pl easychat-test -am test-compile -DskipTests # 编译测试模块
```
