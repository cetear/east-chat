# EasyChat Backend 后端全局介绍

> 更新日期：2026-10-01。
> 2026-10-01：完成 ds_1 / DS_Ofiice 真实聊天排查；新增 default_config.model_name 上游模型名映射，模型保存时校验上下文预算。验证与重启要求见 12.6。
> 2026-09-28：按《EasyChat缺陷登记》完成 14 项维护性整改；实体/共享契约归 common，数据访问归 infra，HTTP/SSE 归 api，统一 Mapper、JSON/HTTP 装配及状态定义。交付与验证见 12.4。
> 核对依据：当前工作区源码（包含已有未提交修改），而非仅依据旧文档或 Git HEAD。
> 第 1—8 节描述当前实现；第 9 节记录 P0/P1/P2 修复与边界；第 10—12 节说明最小架构、验收及升级步骤。P2 已按确认的最小范围落地，隔离测试与真实集成验收分开记录。
> 2026-09-27：保留 P0/P1，新增身份适配、MySQL 租约/outbox、协议工厂及可配置外部能力；不拆服务、不引入消息队列或 Agent 框架。P0/P1 历史记录见 12.1/12.2，当前 P2 交付与迁移见 12.3。

## 1. 项目定位与技术栈

EasyChat 是 Java 17 + Spring Boot 的单体 AI 对话后端，使用 Maven 多模块组织代码。主要功能均是审查与演进重点：

- 会话创建、查询、修改、删除与消息持久化。
- 同步聊天和 SSE 流式聊天。
- 模型、渠道及模型—渠道配置管理。
- 多渠道优先级路由、失败切换、简单熔断。
- ReAct 循环和工具调用。
- 对话历史、摘要生成及保存。
- RAG 检索、来源返回和知识库文档入库。
- 图片输入及模型视觉能力检查。
- Elasticsearch 文档写入、查询与工具查询。
- 能力前后置扩展、业务事件接口。

“存在代码”不等于“已通过真实环境验收”，具体边界见第 9 节。

| 组件 | 当前声明版本或实现 |
| --- | --- |
| Java / Spring Boot | 17 / 3.4.13 |
| 构建 | Maven 多模块 |
| MyBatis-Plus / MySQL 驱动 | 3.5.7 / 8.0.33 |
| 连接池 | Druid |
| LangChain4j | 0.36.0 |
| 流式传输 | Spring MVC SseEmitter + Reactor Flux，并非全链路非阻塞 |
| Elasticsearch Java 客户端 | 8.13.0 |
| 文档解析 | Tika 2.9.2、PDFBox 2.0.29 |
| 常用组件 | Jackson、Lombok |

以上来自 POM，不代表已验证所有外部服务版本的兼容性。

## 2. 模块与现有架构

| 模块 | 当前职责 |
| --- | --- |
| easychat-common | 唯一共享实体及表映射、纯数据访问契约、角色/状态、Result/BusinessException；无 Web/数据库 starter |
| easychat-infra | MyBatis-Plus Mapper、Repository/查询实现、租约数据库协调、数据源、建表脚本、ES 存储访问 |
| easychat-core | 聊天/模型/运维用例、业务事务与状态流转、路由、LLM、Agent、摘要策略、RAG、工具和入库编排；不直接执行 SQL/Mapper/Wrapper |
| easychat-api | Controller、输入输出 DTO/View、身份过滤、HTTP 错误与 SSE 适配（AgentFacade/SSEUtil） |
| easychat-app | 启动与配置 |
| easychat-test | 原有真实环境集成测试，以及 P0/P1/P2 隔离回归测试 |

主要编译依赖为 app → api → core → infra → common，部分模块另有直接依赖。core 仍可依赖 infra 的具体存储入口，不强行新增对应接口；infra 不反向依赖 core。共享实体与四个现有纯数据访问契约在 common，Facade/SseEmitter 在 api。MyBatis 表注解只引入 annotation 依赖；Druid/MySQL 由 infra 声明，H2 仅在测试模块 test scope。

现阶段继续使用这 6 个模块。优先缩短执行路径和稳定接口，不为形式上的分层重新搬迁全部代码，也不新增微服务。

关键源码目录：

| 路径（相对于项目根） | 内容 |
| --- | --- |
| easychat-core/src/main/java/com/easychat/core/service | 聊天与模型管理用例 |
| easychat-core/src/main/java/com/easychat/core/agent | Agent、ReAct 解析、事件与结果 |
| easychat-core/src/main/java/com/easychat/core/router | 渠道注册、路由、熔断包装 |
| easychat-core/src/main/java/com/easychat/core/capability | RAG、摘要等前后置处理 |
| easychat-core/src/main/java/com/easychat/llm | 模型客户端、参数、OpenAI 兼容实现 |
| easychat-core/src/main/java/com/easychat/memory | 摘要策略；历史/摘要数据库访问在 infra 的 DbConversationMemory |
| easychat-core/src/main/java/com/easychat/tools | Tool、ToolRegistry、ES/MySQL 工具 |
| easychat-core/src/main/java/com/easychat/rag | 检索、Embedding、文档管理、解析与分块 |
| easychat-common/src/main/java/com/easychat/common/domain | ChatSession/ChatMessage、ModelDefinition/ModelRoute/ProviderAccount 唯一字段及表映射定义；已合并等价 DO |
| easychat-common/src/main/java/com/easychat/common/entity | 摘要、知识文档、执行租约、outbox 实体 |
| easychat-common/src/main/java/com/easychat/common/port | ChatSessionRepository、ChatMessageRepository、ModelCatalogRepository、ConversationMemory |
| easychat-infra/src/main/java/com/easychat/infra/mysql | 全部关系库 Mapper、持久化及查询实现 |
| easychat-api/src/main/java/com/easychat/api | Controller、DTO/View、Facade 与 SSE 输出 |
| easychat-infra/src/main/resources/sql/schema.sql | 数据库初始化脚本 |

## 3. 当前主要执行流程

### 3.1 聊天与会话

```text
ChatController → AgentFacade → ChatUseCase（聚合）/ StreamChatUseCase（事件流）
  → ChatExecutionService（唯一生命周期）
  → 校验请求/模型、解析会话、占用当前会话
  → ChatContextBuilder：会话配置 + 成功历史/摘要 + 可选 RAG + 附件
  → ChatTurnPersistence：短事务写入 user/assistant，状态 running
  → ReActAgent → ModelRouter → 协议工厂（OpenAI 兼容/Ollama）/ ToolRegistry
  → 短事务保存两条消息状态、内容、实际渠道、耗时和结束原因
  → 成功后执行摘要与业务事件，释放会话
```

只消费 messages 最后一条，要求 role=user 且文字或图片至少一项非空；历史由服务端读取。先读取历史再写入当前轮，因此当前输入只进入模型一次。历史只选成功且未被摘要覆盖的消息；systemPrompt、maxRounds、会话关闭状态均生效。同步和 SSE 共用同一执行与收尾路径。

同一会话执行中再次聊天或删除返回冲突；默认使用进程锁；distributed.enabled=true 时叠加 MySQL 租约与写入令牌校验。消息序号在数据库行锁和短事务中分配，并有唯一约束。失败保留部分输出、取消标记 cancelled，均不进入后续成功历史。模型调用不持有数据库长事务。

### 3.2 ReAct 与工具

普通聊天调用真实模型流；同步接口聚合相同事件流。工具模式使用唯一文本 ReAct 循环，每轮一次模型调用、至多一个工具，默认最多 5 轮。模型必须给出 Action + Action Input JSON 或 Final Answer；Jackson 保留嵌套对象、数组及类型，末行 JSON 可以解析。工具参数格式错误、未知工具和执行异常进入带 success/error 的 Observation；无法完成或达到轮数/上下文上限明确失败。

内置 HelloTool（hello）、MysqlQueryTool（mysql_query）、ES 启用时的 EsQueryTool（es_search）。实现 Tool 并注册 Spring Bean 即可扩展；定义参数 schema，重名启动失败。数据库工具限定可信当前会话，ES 工具限定可信 ownerId、dataset 和服务端知识库索引。工具模式最终答案作为一个完整 message 发送，不伪装为 token 流。取消后不启动下一轮模型或工具；已开始的外部请求是否能物理中止取决于 SDK，未承诺即时停止计费。

### 3.3 模型路由

模型不存在/禁用、没有可用绑定均明确失败。仅显式开启 allow-default-model 且请求匹配默认配置模型时允许默认模式，禁用模型不能绕过校验。注册中心按 priority 升序加载启用渠道；管理事务提交后立即刷新，同时保留 5 分钟定时刷新。刷新沿用同模型/渠道的熔断状态，加载失败清空缓存。

流式只有首段内容之前可切换渠道；输出后失败保留部分结果并结束，禁止拼接另一渠道的答案。实际渠道、结束原因和耗时保存到 assistant；同步和 SSE 返回渠道元信息。模型返回 usage 时累计本轮调用并保存；任何调用缺失统计时 complete=false、总量为 null，不伪造零值。默认优先级路由；strategy=weighted 时同优先级按权重抽取，无放回切换；max_retry 为每渠道额外重试 0—3 次，仅输出前重试。熔断为 3 次连续失败、30 秒冷却、单探针半开；刷新共享状态，过期请求不能关闭新的熔断。ProviderFactory 已接入 OpenAI 兼容及 Ollama 原生协议；渠道能力过滤不支持的图片组合。

### 3.4 记忆与摘要

DbConversationMemory 读取最近 maxRounds 轮成功消息，并排除 covered_message_id 已覆盖记录。成功消息提交后才触发增量摘要：默认累计 10 条待总结消息，另保留最近 4 条；单次最多处理 40 条。输入旧摘要与新增片段，按长度预算缩小批次，只推进实际处理的最后消息 ID。无新增消息不重复总结；模型异常不推进游标。

摘要使用 summary-model，未设置时使用本次会话模型。模型输入使用简单字符预算（最多 16000，结合 context_window/max_output_tokens 缩减），不是精确 tokenizer，也不计算图片 token。过长输入明确拒绝；超出摘要预算的单条记录不被错误标为已总结。摘要为有损压缩，不承诺逐字保留所有内容。

### 3.5 RAG 与知识库

默认 BM25，不需要 Embedding；rag.mode=hybrid 才创建内置 Embedding 客户端并启用向量+关键词 RRF。混合检索失败后回退 BM25，并返回 warning；BM25 也失败则请求失败，不伪装为空命中。引用包含 chunkId、docId、title、pageNo；注入文本受 max-context-chars 和会话预算限制。

上传 → PENDING → 条件领取 INDEXING → 解析/分块 → 分批写入 ES → 全部成功后 INDEXED。每次入库生成 index_version，ES 记录 generation=docCode:version；检索同时过滤 ownerId、dataset 和 MySQL 已发布版本，失败/半成品不会被召回。重建仅清理该文档旧版本块；租约内发布新版本，失效工作者不能更新发布状态。空解析明确失败；向量条数、维度和有限值校验；默认每批 16 块。

入库、重试、删除使用同文档进程锁，可选叠加 MySQL 租约。删除先标 DELETING，失败保留 DELETE_FAILED 和元数据，重新 DELETE 可重试。启动及周期恢复中断任务，按线程池异步提交；每 30 秒补投 PENDING，队列满仍保留待处理状态。分布式模式仅恢复没有有效租约的任务，60 秒租约每 10 秒续租，关键数据库写入在持有租约行锁的短事务中校验令牌。

同 ownerId + dataset + file_hash 复用 docCode，并有数据库唯一约束；失败文档显式 retry，删除中的文档返回冲突。上传在 Controller、Service 和 multipart 均受 20MB 限制。保留 P0 路径白名单/真实目录检查。PDF/Markdown/Tika 解析与字符分块沿用；配置 OCR 后扫描 PDF 空白页和图片走 HTTP 识别，音视频可先转文字。DocumentStorage 支持本地与 S3，切换 S3 后仍可访问已有本地引用。默认 standard 分词和可配置向量维度；mapping 不一致拒绝入库，需重建索引。

### 3.6 图片、音视频与事件

支持文字加图片、纯图片、图片加工具（最多一张图片）；验证模型/渠道视觉能力、URL/data URI 格式和数量。Ollama 原生仅接受内嵌图片，远程图片 URL 不会分配给该渠道。图片引用保存到 user.param_json，并恢复为带 TextContent/ImageContent 的 UserMessage；统一使用 system/user/assistant 结构化消息。远程链接有效期与实际渠道视觉兼容性仍须部署验证。

能力扩展使用 supports/beforeRun/afterRun；核心状态仍由执行服务管理。默认 ChatEventPublisher 发布 Spring 本地事件并记录日志，用户自定义 Publisher 时默认实现退让；events.enabled=false 才使用 Noop。同步/SSE 都在成功保存后发两条消息创建事件及完成事件，失败/取消不发成功事件。事件/摘要失败记录日志，不篡改已成功保存的聊天结果。开启 events.outbox.enabled 后，成功消息与待投递事件同事务写入 MySQL，定时领取投递、失败重试，至少一次交付；订阅者须按 eventId 去重。摘要写回也校验租约。

## 4. 数据库实际结构

schema.sql 当前包含 **11 张表**，旧文档的“6 张表，已删除日志和配置表”不再准确。

| 表 | 接入状态 |
| --- | --- |
| chat_session | 会话 CRUD、owner_id、system_prompt、max_rounds、关闭状态已接入 |
| chat_message | 消息及附件引用；状态 0失败/1成功/2运行中/3取消；渠道、耗时、结束原因；完整 usage 时填充 token |
| model | 模型配置与视觉能力标记 |
| provider | 渠道配置及定时熔断监测快照；路由控制状态仍在进程内 |
| model_provider | 优先级、权重、超时、重试次数及启用状态均生效 |
| chat_session_memory | 摘要与 covered_message_id；embedding 字段未接入向量记忆 |
| knowledge_doc | 文件元数据、owner_id、dataset、状态、index_version、分块数、错误 |
| chat_session_config | 表存在，尚无对应业务接入 |
| chat_request_log | 表存在，尚无对应业务接入 |
| execution_lease | 分布式任务/会话互斥、续租和令牌校验 |
| chat_event_outbox | 事务事件、领取、重试、投递状态 |

初始化脚本不是增量迁移脚本。已有数据库需按差异迁移，不能直接重复执行全部 CREATE TABLE。

## 5. 当前 API 与响应约定

默认端口 8080。聊天请求的 sessionId 是 sessionCode 字符串，不是数据库数字主键。

### 5.1 聊天、会话

| 方法 | 路径 | 当前行为 |
| --- | --- | --- |
| POST | /api/chat | 同步返回 content、sessionId、providerCode、finishReason、usage，可选 sources/warning |
| POST | /api/chat/stream | SSE |
| POST | /api/session、/api/createSession | 创建会话 |
| GET | /api/sessions | 当前用户会话列表，未分页 |
| GET | /api/session/{sessionId} | 查询会话元数据 |
| PUT | /api/session/{sessionId} | 修改标题、系统提示词、轮数、状态 |
| DELETE | /api/session/{sessionId} | 事务删除会话、消息与摘要 |
| PUT | /api/session/{sessionId}/max-rounds | 设置并应用历史轮数（1—100） |

GET /api/session/{sessionId}/messages 返回该会话消息，包含附件引用、状态及错误；生成历史仍只使用成功记录。

```json
{
  "sessionId": null,
  "model": "deepseek-chat",
  "messages": [{"role": "user", "content": "你好"}],
  "toolsEnabled": false,
  "ragEnabled": false,
  "dataset": "default"
}
```

dataset 为可选字段，同步和 SSE 均透传到检索上下文；每次请求独立选择，未保存到会话。省略、null 或空白为 default；其余值去掉首尾空白后须为 1—64 位 ASCII 字母、数字、下划线或连字符，以字母或数字开头，禁止 Windows 保留设备名。非法值在聊天入口返回 HTTP 400，不创建会话或调用模型。

dataset 是当前用户下的知识库范围，身份来自服务端校验后的 Bearer token；客户端不能指定 ownerId。会话、文档、RAG 和 ES 工具均按用户隔离；管理员管理模型/ES，不自动取得其他用户会话。切换 dataset 不清除已有历史/摘要，隔离验收使用独立会话。未开启认证时统一使用 demo 身份。

图片放在最后一条消息的 images 数组中，有图片时允许 content 为空。SSE 首先发送 meta，包含自动创建或已有的 sessionId；成功结束前补充 providerCode/finishReason/usage。

SSE 当前发送 meta、message、action、observation、sources、warning、error、finish。message/error/finish/warning 是文本；meta/action/observation 是 JSON 文本；sources 是 JSON 数组文本。成功发送一个 finish，失败发送一个 error；断连停止后续发送。同步 sources 目前也是序列化后的字符串，不是响应中的原生数组。

### 5.2 模型、渠道和路由

| 对象 | 主要接口 |
| --- | --- |
| 渠道 | POST /model/provider/create；GET /model/provider/list；GET /model/provider/{id}；GET /model/provider/code/{providerCode} |
| 渠道更新/删除 | POST /model/provider/update；PUT /model/provider/{providerCode}；DELETE /model/provider/{providerCode} |
| 模型 | POST /model/create；GET /model/list；GET /model/{id}；GET /model/code/{modelCode} |
| 模型更新/删除 | POST /model/update；PUT /model/{modelCode}；POST /model/delete；DELETE /model/{modelCode} |
| 绑定 | POST /model/addModelToProvider |
| 路由查询 | GET /model/provider/{providerCode}/models；GET /model/{modelCode}/providers |
| 解绑 | DELETE /model/provider/{providerCode}/models/{modelCode} |

ProviderView 不返回 apiKey。管理修改在事务提交后主动刷新 ProviderRegistry。PUT /model/provider/{providerCode}/models/{modelCode} 可更新已有绑定参数；GET /api/models 为普通用户提供启用模型的最小列表。认证开启时 /model 全部要求 admin。

### 5.3 ES 与知识库

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /es/test | ES 连通性 |
| GET | /es/getDocuments?index=easychat&size=100 | 最多返回指定条数，不是全量分页 |
| POST | /es/indexDocument | 向允许索引写入文档 |
| POST | /api/knowledge/upload | multipart：file、可选 dataset；Controller 限制 20MB |
| GET | /api/knowledge/docs?dataset=default | 文档列表 |
| GET | /api/knowledge/docs/{docCode}/status | 文档状态 |
| POST | /api/knowledge/docs/{docCode}/retry | 重新提交入库 |
| DELETE | /api/knowledge/docs/{docCode} | 删除文档、ES 分块及本地文件 |

旧路径 /es/documents、/es/document 当前没有对应映射。multipart max-file-size=20MB、max-request-size=21MB，Controller/Service 同样限制文件 20MB。

ES 原始写入不会自动解析文件或向量化；需要 RAG 入库闭环时使用知识库接口。原始 ES 接口白名单为 easychat.es.index 和 easychat.rag.index；size 为 1—100。es_search 工具固定查询知识库索引并使用当前 dataset/已发布版本，topK 为 1—50，不接受模型指定索引或数据集。

知识库上传复用上述 dataset 校验；生成 UUID 文件名，扩展名仅允许 1—20 位 ASCII 字母或数字（无扩展名时 txt）。归一化路径并检查真实目录位于配置存储根内，拒绝指向根外的目录链接，使用 CREATE_NEW/NOFOLLOW_LINKS 写入文件。原始文件名仅作为元数据保留。成功响应保留 Result；失败由统一异常处理器返回 HTTP 错误及 error 字段。

兼容性：已有无 dataset 字段的 ES 文档不会被 default 范围召回；历史上不符合新 dataset 格式的知识库需显式整理名称及对应 ES 元数据。未执行自动数据迁移或补标，避免将未知归属文档纳入默认范围。认证开启时原始 /es 接口仅 admin 可用；es_search 限定当前用户、dataset 和已发布版本。

成功管理接口保留 Result，同步聊天保留 Map；非法输入 400、未登录 401、无管理权限 403、不存在或不属于当前用户的会话/文档 404、会话忙 409、multipart 超限 413、内部故障 500。SSE 建立后使用 error 终态，不能再更改 HTTP 状态。

新增接口：POST /api/media/transcribe（multipart file，返回 text）；GET /api/ops/status（管理员状态）；GET /api/models（当前用户可选模型）。启用认证后所有业务请求均携带 Bearer token，身份服务不可用返回 503，不回退 demo。

## 6. 配置与启动依赖

配置入口：easychat-app/src/main/resources/application.yml。凭据从环境变量提供，本文不复制已有默认值。

| 配置 | 默认及作用 |
| --- | --- |
| easychat.es.enabled / ES_ENABLED | true；false 关闭 ES 客户端、检索实现、知识库、工具、Controller、线程池和启动任务 |
| easychat.rag.mode / RAG_MODE | bm25；hybrid 才启用内置 Embedding |
| easychat.rag.index / analyzer | easychat_kb / standard；已有 mapping 必须一致 |
| easychat.rag.embedding.dimensions | 1024；同时用于向量校验与索引 mapping |
| easychat.rag.top-k / max-context-chars | 3 / 6000；数量和检索文本长度预算 |
| easychat.rag.storage-dir / chunk.* | 本地文件目录与字符分块配置 |
| easychat.rag.embedding.* / hybrid.* | 向量服务、批大小、双路召回数量 |
| easychat.memory.summary-enabled | true；摘要开关 |
| easychat.memory.summary-model | 空则使用本次聊天模型 |
| easychat.memory.summary-threshold / summary-max-messages / summary-keep-messages | 10 / 40 / 4；新增触发量、单次最多条数、保留近期条数 |
| easychat.llm.allow-default-model | false；显式启用单默认模型演示 |
| easychat.events.enabled | true；默认本地事件，false 使用 Noop |
| spring.elasticsearch.ssl.trust-all / ES_TRUST_ALL | false；自定义客户端真正应用证书策略 |
| spring.elasticsearch.connection-timeout / socket-timeout | 5000 / 30000 毫秒，自定义客户端应用此值 |
| spring.servlet.multipart.* | 文件 20MB，请求 21MB |
| easychat.auth.enabled / AUTH_ENABLED | false；单用户 demo。多用户开启后必须配置 AUTH_INTROSPECTION_URL |
| easychat.distributed.enabled / DISTRIBUTED_ENABLED | false；开启 MySQL 租约，所有实例统一配置，共享数据库及本地文件目录 |
| easychat.events.outbox.enabled / OUTBOX_ENABLED | false；与 events.enabled 同时开启后使用事务事件表 |
| easychat.router.strategy / ROUTER_STRATEGY | priority；weighted 为同优先级权重选择 |
| easychat.providers.protocols.<providerCode> | openai-compatible；可选 ollama，新增工厂 Bean 可扩展 |
| easychat.providers.capabilities.<providerCode>.vision / vision-tools | vision 默认模型视觉标记，vision-tools 默认 true；不兼容渠道显式关闭 |
| easychat.ocr.url / token | OCR_URL / OCR_TOKEN；空地址不启用识别 |
| easychat.media.transcription-url / token / ffmpeg | TRANSCRIPTION_URL / TRANSCRIPTION_TOKEN / FFMPEG_PATH；ffmpeg 默认为可执行命令名 |
| easychat.rag.rerank.url / token | RERANK_URL / RERANK_TOKEN；空地址保持原检索排序 |
| easychat.rag.storage.mode | STORAGE_MODE=local 或 s3 |
| easychat.rag.storage.s3.* | S3_ENDPOINT、S3_BUCKET、S3_REGION、S3_ACCESS_KEY、S3_SECRET_KEY；region 默认 us-east-1 |

基础运行需要 MySQL + 一个已配置聊天模型/渠道；ES_ENABLED=false 可运行聊天、HelloTool、记忆、摘要、图片和本地事件。开启 ES 后默认 BM25，不依赖 IK/Embedding；完整模式再启用 hybrid 和匹配维度的向量服务。Spring 自带 ES 客户端自动配置已排除，由 EsConfig 统一控制。

图片服务兼容性、证书链、真实索引和数据库迁移须在部署环境验证。仅关闭能力不代表该能力已验收。

## 7. 构建与本次验证

```powershell
# 在项目根目录编译应用及依赖
mvn -pl easychat-app -am compile -DskipTests

# 本次实际执行；使用本地已缓存依赖
mvn -o -pl easychat-app -am compile -DskipTests
```

2026-09-27：应用及测试编译成功，P0/P1 历史定向回归结果见 12.2，P2 当前结果见 12.3；ES 封装仍有未检查类型转换警告。没有运行连接真实服务的集成测试，不将替身流程测试当作真实模型/数据库/索引验收。

启动可在 IDE 运行 com.easychat.app.EasyChatApplication。命令行开发启动应先安装依赖，再只在 app 模块运行启动目标：

```powershell
mvn -pl easychat-app -am install -DskipTests
mvn -pl easychat-app spring-boot:run
```

上述启动步骤为建议，未在本次运行验证。不要将 spring-boot:run 配合 -am 直接在所有依赖模块执行；当前 POM 也没有显式绑定 repackage execution，不应假定普通 package 就生成可执行 fat jar。

## 8. 当前扩展点与边界

| 扩展点 | 当前最小实现 | 后续主要改动 |
| --- | --- | --- |
| Tool / ToolRegistry | HelloTool、JSON 参数 schema、可信 ToolContext、重名校验 | 新增 Tool Bean，保持 execute 契约即可参与循环 |
| Retriever | RetrievalRequest + RetrievalResult，ownerId/dataset、引用、warning | 新增检索策略，遵守范围及已发布版本约束 |
| EmbeddingClient | hybrid 模式才启用，条数/维度/有限值校验 | 新服务实现；维度改变需新索引重建 |
| DocumentParser | PDF/Markdown/Tika + HTTP OCR/音视频转写分派 | 新格式可扩展分派，不预先建设插件框架 |
| ConversationMemory / SummaryService | 成功历史、摘要覆盖游标、增量合并 | 新存储或摘要策略；接口目前仍暴露 DO |
| LLMClient / LLMProvider / ChatModelClient | 统一结构化消息、优先级路由、OpenAI 兼容实现 | 新增 ProviderFactory Bean 并配置协议名，无需修改路由循环 |
| DocumentStorage | 本地/S3，S3 模式兼容已有本地引用 | 新存储实现与配置；迁移保留旧引用 |
| AgentCapability | supports/beforeRun/afterRun，Spring 排序 | 新增前后置处理；不承担核心状态迁移 |
| ChatEventPublisher | 默认本地事件，自定义实现使默认退让 | 新订阅者/发布适配；已有 MySQL outbox，订阅者按 eventId 去重 |

保持现有模块和分层，不新增同义 Repository/Service 包装；新增 ChatExecutionService 统一生命周期，ChatTurnPersistence 仅提供跨两条消息的短事务边界。

## 9. 全功能审查与修复状态

P0 是明确边界缺陷，P1 是当前最小版本的功能修复，P2 按用户确认范围完成最小实现，具体接入与边界见 12.3。以下“已修复”指代码及隔离回归，不等同于真实 MySQL/LLM/ES 集成验收；迁移和验证见第 12 节。

### 9.1 会话、消息与聊天（P1 已修复）

会话配置/关闭校验接入 ChatContextBuilder；唯一用例解析入口；先读成功历史再写当前轮。用户与 assistant 成对写入/收尾使用短事务；状态明确区分运行、成功、失败、取消。同会话默认单实例互斥，P2 可启用 MySQL 租约跨实例互斥，数据库会话行锁分配序号，新增唯一约束。SessionUseCase 事务删除消息、摘要、会话；提供历史查询 API。数据库并发/回滚尚需真实 MySQL 验证。

### 9.2 同步与 SSE（P1 已修复）

共享 ChatExecutionService/ReActAgent；阻塞准备和工具循环放 boundedElastic。SSE meta 回传会话，首段前允许路由降级，首段后失败终止，成功/错误只发一次终态。取消贯穿循环，初始化事务期间断开也会取消收尾并解锁；同步 sources 字符串契约保持。外部 SDK 物理取消未承诺。

### 9.3 模型/渠道/路由/熔断（P1/P2 最小实现已修复）

拒绝缺失/禁用模型和无启用路由；默认模式需显式开启。管理修改及关联删除处于事务，提交后主动刷新路由，JSON 配置保存时验证对象格式；刷新继承熔断计数。实际渠道、耗时、结束原因入库并返回渠道信息。

P2 已接入同优先级加权路由、有限重试、单探针半开、数据库熔断快照、可选真实 usage 和协议工厂；路由参数支持更新。usage 不完整时显式标记未知；已有渠道请求不因管理更新而强制中断。

### 9.4 ReAct 与工具（P1 已修复）

唯一循环、末行 Action Input 解析、Jackson 复杂 JSON、工具 schema/重名校验、统一 Observation、轮数/上下文上限失败。HelloTool 是真实执行的最小工具，新增工具不改循环。MySQL 查询使用可信会话及成功状态过滤；ES 查询使用可信 dataset。工具模式最终答案整段发送，未实现原生 function calling、多工具并行或多 Agent。

### 9.5 记忆与摘要（P1 已修复）

成功落库后按未覆盖消息量触发；旧摘要+新增片段合并，保存 covered_message_id 并保留近期消息。输入按预算缩小批次，游标不越过未处理内容；显式摘要模型选择。历史及摘要输入有简单字符预算，不是精确 token 或图片成本计算；过长单条摘要片段不会被跳过并推进游标。

### 9.6 RAG/引用（P0/P1 已修复）

保留 P0 DatasetId 与 RetrievalRequest 范围过滤。默认 BM25，hybrid 可选且故障回退可见；真正检索失败抛错。维度/分词配置和 mapping 验证接入，chunkId/来源及注入预算统一。范围还过滤已发布 generation，防止召回半成品。P2 已加用户归属过滤及可选 HTTP 重排；重排失败返回 warning 并保留原顺序。

### 9.7 文档生命周期（P0/P1 已修复）

保留 P0 路径白名单/真实目录包含检查；条件领取、单文档锁、独立线程池提交、启动恢复及周期补投已接入。删除先取消发布，失败保留 DELETE_FAILED，可重复 DELETE；旧块清理与版本发布隔离避免部分入库成为有效知识。空文本失败；向量条数/维度/有限值校验；分批 bulk；20MB 配置统一；同 dataset 文件哈希复用。P2 已接入 OCR、MySQL 租约、用户/数据集/哈希唯一约束、S3 存储以及音视频转写解析。

### 9.8 图片与音视频（P1/P2 最小实现已修复）

支持一张图片、纯图片和图片+工具；结构化附件在 ReAct 多轮中保留。按渠道 vision/vision-tools 能力选择路由，Ollama 原生排除远程图片。音视频采用已确认的最小范围：音频转文字，视频提取音轨再转文字，不包含视频画面理解或语音合成。

### 9.9 ES 服务（P1/P2 最小实现已修复）

原始接口限制服务端配置索引白名单及 size；工具固定知识库范围；TLS trust-all 和超时真正生效。ES 组件组可统一关闭。原始接口认证后仅管理员可访问；普通检索受 ownerId 限制。提供 /api/ops/status 和部署验收步骤；未自动部署或管理 ES 集群。

### 9.10 能力、事件与工程（P1/P2 最小实现已修复）

默认本地事件与条件退让，成功持久化后发布，同步/SSE 一致；失败/取消不发布成功事件。保留前后置钩子，核心状态在固定用例内。主要 API 统一错误处理，新增 P0/P1 隔离测试。P2 新增身份 HTTP 适配、归属隔离、MySQL outbox、租约恢复和最小运维状态接口。遵循最小改动要求，严格模块依赖倒置仍保留为结构性债务，不把它标记为已完成重构。

## 10. 最小、可扩展架构（P0/P1/P2 最小范围已实施）

原则：每项主要能力都有真实的最小闭环；复杂实现按配置选择。关闭某项能力只意味着未运行，不能作为该能力验收通过。

```text
HTTP / SSE 输出适配
       ↓
聊天用例：会话解析、配置校验、状态管理、保存结果
       ↓
上下文准备：历史/摘要 + 可选检索片段 + 附件
       ↓
唯一 Agent 执行器
       ├─ 模型网关 → 单渠道 / 已有多渠道路由 → 协议适配器
       ├─ ToolRegistry → HelloTool / 新业务工具
       └─ 执行事件 → JSON 聚合 / SSE 发送
       ↓
成功持久化 → 摘要触发、业务事件

知识库用例（独立于聊天主链路）
       → 存储 → 解析 → 分块 → 可选向量化 → ES 索引
```

落地时复用 ChatContextBuilder、现有用例与接口；“唯一执行器”“模型网关”是职责名称，不要求新增一层同义包装。Facade 已迁 api 并保留兼容入口；SSE 格式转换在 API 层，core 不创建或发送 SseEmitter。

只在有替换需求的边界使用接口：工具、模型协议、检索、记忆、文档解析、存储、事件发布。一般 CRUD 和固定编排不为每个类再造一个接口。

当前采用的轻量数据契约：

| 契约 | 最少内容 | 用途 |
| --- | --- | --- |
| 运行上下文 | 会话、可信调用者范围、模型、消息、已选能力、截止时间/取消状态 | 避免 Map 字符串键跨层传递 |
| 模型消息 | role、文本、附件、可选工具调用/结果关联 | 普通聊天、图片、ReAct 共用 |
| 模型结果 | 内容/工具调用、实际渠道、结束原因、可选 usage | 错误与追踪信息不丢失 |
| 检索请求/片段 | query、topK、dataset/范围；chunkId、docId、文本、来源 | BM25 和混合检索共享 |
| 工具定义/结果 | 名称、参数 schema、执行结果或明确错误 | 新工具无需改循环 |
| 摘要记录 | 摘要文本、覆盖消息 ID | 增量摘要与去重 |
| 文档任务 | docCode、状态、版本或领取标记、错误 | 重试和删除可追踪 |

先保持单体、MySQL、现有线程池和 ES；无需新增 Redis、Kafka、工作流引擎或多 Agent 框架。

## 11. 各主要功能的最小实现、扩展与验收

| 功能 | 现在必须跑通的最小案例 | 后续主要新增内容 | 最小验收 |
| --- | --- | --- | --- |
| 会话/消息 | 建会话、两轮对话、更新配置、查询历史、删除 | 分页、共享授权、搜索 | 第二轮读取正确历史；轮数/系统提示生效；删除无孤立摘要 |
| 同步聊天 | 一个模型真实回答并保存 | 新模型适配 | 真实响应与数据库一致；故障明确失败 |
| SSE | 普通聊天真实增量输出及终态、会话元信息 | 新输出适配/事件类型 | 首段及时可见；断开后不启动下一轮；只出现一个终态 |
| 模型/渠道管理 | 一模型、一渠道、一绑定，参数可配置 | 新协议工厂、更多配置 | 修改生效；禁用后不可调用；不泄露密钥 |
| 路由/降级 | 两个兼容渠道按优先级尝试 | 新路由策略 | 第一渠道故障切第二；全部故障明确失败 |
| 熔断 | 连续失败阈值+冷却 | 集中式指标与告警 | 冷却期跳过故障渠道；恢复后可调用 |
| ReAct | 最多若干轮，一次只执行一个工具 | 新协议响应转换、复杂编排 | 真实模型选择工具并消费 Observation；上限有明确原因 |
| 工具 | HelloTool 返回 hello | 新增 Tool Bean 与依赖 | 真实执行次数可验证，未知工具/坏参数有明确结果 |
| 历史记忆 | MySQL 最近 N 轮 | 新记忆存储 | 当前消息只出现一次；失败占位不进入历史 |
| 自动摘要 | 短阈值触发一次，保存摘要与覆盖游标 | 新摘要策略/模型 | 旧事实保留；没有新消息时不重复摘要 |
| RAG/引用 | 一份文本、一个 dataset、BM25 命中并引用 | 混合召回、重排、新 Retriever | 回答使用原文且返回可定位来源；数据集互不混入 |
| 文档入库 | 小 TXT 上传→分块→索引→状态查询→删除 | 新解析器、文件存储实现、向量步骤 | 上传后可检索；失败可重试；删除后不可检索 |
| 混合检索 | 作为可选模式用同一文件验证向量+关键词召回 | 新 Embedding 模型/索引版本 | 维度一致；指定故障时回退可观察，不伪装正常命中 |
| 图片 | 识别一张图片并可调用 hello | 新附件类型、协议适配 | 真正传图；不支持模型/组合明确拒绝；附件可恢复 |
| ES 基础服务/工具 | 写入一条文档，再经查询接口或工具取回 | 新查询方法 | 内容一致；只能访问允许索引 |
| 能力管线 | 一个 before/after 示例记录顺序 | 新增 AgentCapability | 开关与顺序生效；不重复执行 |
| 业务事件 | 保存后本地日志订阅者收到完成事件 | 新增发布适配器/订阅者 | 同步与流式一致；失败不发成功完成 |
| 配置/错误/测试 | 单用户本地模式、外部凭据、统一错误 | 新身份系统适配、完整告警体系 | 每项能力有确定性测试与最少一次真实集成验收 |

“主要新增”允许增加配置、数据库迁移、依赖选择及注册信息；不承诺跨存储、跨协议、并行执行等所有变化都零修改。

已实现的运行组合（真实环境验收仍需按第 11 节执行）：

- 基础聊天组合：MySQL + 聊天模型 + HelloTool；用于快速调试聊天、记忆、摘要、Agent、图片、事件。
- 知识库组合：基础组合 + ES + 一份 TXT，先用 BM25 验收入库和 RAG。
- 完整功能组合：再启用 Embedding、混合检索和两个模型渠道，验收全部主要功能。
- 隔离测试使用可控模型/仓储/工具替身；替身验证流程，不代替真实模型与 ES 的最终验收。

## 12. 建议实施顺序与验证边界

1. 上传路径边界、知识库 dataset 检索范围已完成本次 P0 修复；P1 聊天、模型及主要 API 校验已接入。
2. 统一聊天上下文和结果生命周期，修复历史重复、会话配置失效、失败状态、SSE 取消与渠道切换行为。
3. 修复路由配置生效和摘要游标；统一 ReAct 循环并加入 HelloTool。
4. 以 TXT + BM25 跑通知识库完整生命周期，再验证现有混合检索；补齐删除、重试、恢复的协调。
5. 验证图片、ES 基础接口、前后置能力和本地事件，完善每项能力的演示入口。
6. 对各扩展边界加确定性测试，并用真实服务逐项勾选第 11 节验收表。

本轮按已确认范围补齐加权路由、OCR、图片工具组合等最小实现；仍不做严格 DDD 重构、拆服务、MQ 或多 Agent 调度。已有复杂代码保留，通过配置选择实现，避免为了简化演示删除未来可复用工作。

验证方法：先在隔离测试中复现关键分支（故障/取消/重试/范围隔离），再测试真实集成。会修改真实数据库、文档和模型用量的测试应使用专用数据与服务配置；本次未执行真实服务集成测试。

### 12.1 本次 P0 交付与验证（2026-09-27）

此处保留 P0 阶段的历史交付记录；P1 历史见 12.2，当前实现以 12.3 为准。当时只处理第 9.6、9.7 节两个 P0：上传路径越界、RAG 跨 dataset 召回。未修改 ReAct 循环、摘要、路由、入库状态机或删除逻辑，也未引入权限框架、额外服务或数据库迁移。

新增 DatasetId 统一上传/查询标识校验；新增 RetrievalRequest 作为检索范围契约。保留 Facade 的原签名及 Retriever.search(query, topK) 调用入口，默认仅查询 default。新增 Retriever 实现应实现 search(RetrievalRequest)，必须遵守其中的 dataset 范围，不可自行回退全索引。

| 测试类（easychat-test/src/test/java/com/easychat/test） | 验证内容 | 本次结果 |
| --- | --- | --- |
| P0ChatDatasetTest | 同步/SSE 从 Controller、Facade、上下文到 RAG 的参数传递；非法范围在会话操作前拒绝 | 4 通过 |
| P0DatasetRetrievalTest | 真实 ES 请求对象中的 BM25 filter、kNN 预过滤；两数据集分别检索、默认范围、未命中及非法范围；使用模拟 ES 返回值 | 3 通过 |
| P0KnowledgeUploadTest | 路径跳转、绝对路径、设备名、危险扩展名拒绝；默认/指定数据集实际写文件和提交入库 | 23 通过，1 跳过 |

总计 31 项：30 通过、0 失败、0 错误、1 跳过；应用与测试编译通过。跳过项为真实根外目录符号链接测试，本机缺少创建符号链接的权限，未将其算作验证通过。真实目录包含检查代码已接入。测试未连接真实 MySQL、ES、LLM 或 Embedding，不能代替部署环境的最终集成验收。

本次通过的 PowerShell 命令（在项目根执行；本地须已有对应测试依赖，首次可去掉 -o 下载）：

```powershell
New-Item -ItemType Directory -Force -Path 'easychat-test/target/p0-temp' | Out-Null
$p0Temp = Join-Path (Get-Location) 'easychat-test/target/p0-temp'
mvn -o -pl easychat-test -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test '-Dtest=P0*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.redirectTestOutputToFile=true' "-DargLine=-Djava.io.tmpdir=$p0Temp"
```

定向执行避免运行原有真实环境 AgentFacadeTest。受限环境下将临时文件放在项目 target 内；普通宿主环境可以省略 argLine。测试报告位于 easychat-test/target/surefire-reports。

### 12.2 P1 交付、升级与验证（2026-09-27）

本节保留 P1 阶段记录；P2 已扩展的能力、当前限制与最终测试结果以 12.3 为准。

第 9.1—9.10 的 P1 最小修复已实施，保持原单体/模块结构。后续新增业务工具实现 Tool Bean 即可；跨协议、跨存储、多实例与多模态联合推理仍按明确边界扩展，不承诺全部零修改。

**部署前必须完成的升级（本次没有操作真实数据库/索引）：**

1. 已有数据库先检查/备份，再执行 `easychat-infra/src/main/resources/sql/migrations/20260927_p1.sql` 一次。脚本先查询重复会话序号；有重复先人工整理，不自动删除消息。新增 `(session_id,message_order)` 唯一约束、`covered_message_id` 和 `index_version`。新建环境使用更新后的 schema.sql，无须重复执行迁移。
2. 旧知识库索引缺少 generation 或分词/维度不匹配时，配置新的 `easychat.rag.index`，启动后重试已有文档。只有重新入库成功且版本已发布的文档可检索；旧未标记版本数据不会自动并入 default。BM25 切换 hybrid 后也须重新入库生成向量。旧索引不自动删除。
3. 单用户基础演示可设 `ES_ENABLED=false`；知识库设 true，`RAG_MODE=bm25` 为默认。使用自签名证书应配置可信证书链，只有明确需要时设 `ES_TRUST_ALL=true`。模型默认配置模式另需 `ALLOW_DEFAULT_MODEL=true`，不会覆盖已禁用的数据库模型。

**确定性回归结果：** 应用和测试编译成功；58 项测试中 57 通过、0 失败、0 错误、1 跳过。跳过为 Windows 根外目录符号链接权限测试；没有将其算为通过。

| 测试类 | 验证内容 | 结果 |
| --- | --- | --- |
| P0ChatDatasetTest | 同步/SSE dataset 传递与非法请求提前拒绝 | 4 通过 |
| P0DatasetRetrievalTest | BM25/kNN dataset 过滤、已发布 generation 过滤与默认范围 | 3 通过 |
| P0KnowledgeUploadTest | 上传路径、扩展名与真实文件写入 | 23 通过，1 跳过 |
| P1AgentRoutingTest | HelloTool 实际执行、嵌套 JSON、坏参数、轮数上限、取消、重名、渠道切换与熔断状态 | 5 通过 |
| P1ChatLifecycleTest | 持久化/事件顺序、部分失败、取消、初始化期间取消、失败解锁、同会话互斥 | 6 通过 |
| P1ContextSummaryTest | 会话配置/历史、结构化图片及附件恢复、预算、摘要增量游标 | 4 通过 |
| P1KnowledgeTest | 分批发布、部分失败、空文本/错误向量、领取/队列满补投、删除重试、回退 warning、文件复用 | 7 通过 |
| P1ConfigurationTest | ES 整组关闭、本地事件及默认退让、能力顺序、HTTP 错误分类 | 3 通过 |
| P1MemoryModelTest | 成功历史/游标 SQL 条件、摘要清理、配置 JSON 校验、事务提交后刷新 | 2 通过 |

在项目根执行（依赖已缓存时可使用 -o）：

```powershell
New-Item -ItemType Directory -Force -Path 'easychat-test/target/p0-temp' | Out-Null
$p1Temp = Join-Path (Get-Location) 'easychat-test/target/p0-temp'
mvn -o -pl easychat-test -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test '-Dtest=P0*Test,P1*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.redirectTestOutputToFile=true' "-DargLine=-Djava.io.tmpdir=$p1Temp"
```

报告位于 easychat-test/target/surefire-reports。本次模型、仓储和 ES 使用替身，HelloTool、ReAct、路由/生命周期代码、文件保存及部分 Spring 配置真实执行；未连接真实 MySQL、LLM、ES、Embedding，未执行线上迁移。第 11 节真实回答、数据库并发/回滚、浏览器 SSE 断连、TLS、图片识别和入库检索闭环仍是部署环境集成验收事项。

**最小演示入口：**

- ReAct：向 `/api/chat` 或 `/api/chat/stream` 发送 `toolsEnabled=true`，内容“调用 hello 工具，然后根据工具结果回答”；通过 action/observation 及最终答案验证真实执行。已有已启用模型和渠道为前提。
- 历史/摘要：同 sessionId 连续对话，用 `/api/session/{sessionId}/messages` 检查角色、状态、附件；降低 summary-threshold 后观察覆盖游标，保持 summary-keep-messages 对应近期条数。
- RAG：上传一份含唯一事实的 TXT 到 alpha，等待 INDEXED；新会话设置 `ragEnabled=true,dataset=alpha` 提问并检查 sources；另一个新会话用 beta 验证不混入，最后删除文件并验证不能召回。
- 图片：选择视觉模型，最后一条 user 消息设置文字和一个 images URL；续聊验证历史附件仍传递。P1 阶段不同时开启 toolsEnabled；P2 已支持该组合，见 12.3。
- 事件：成功聊天后查看本地 ChatMessageCreatedEvent/ChatCompletedEvent 日志，或新增 Spring 本地订阅者；故障/取消不产生成功完成事件。
### 12.3 P2 最小交付、接入约定与验证（2026-09-27）

范围采用本轮确认：现有登录系统 HTTP 适配；MySQL 租约和事件表、共享文件目录；OCR HTTP、S3 兼容存储、音频转写/视频抽音轨、Ollama 原生、HTTP 重排。保持 6 模块单体，新增工具仍只需 Tool Bean；协议新增 ProviderFactory Bean，存储新增 DocumentStorage 实现。

**交付清单与真实边界**

| 原 P2 项 | 当前最小实现 | 明确保留的边界 |
| --- | --- | --- |
| 路由、重试、半开 | 同优先级加权无放回选择；每渠道最多 3 次额外重试；单探针半开；状态跨刷新共享；路由绑定可更新 | 输出后不重试/切换；熔断达到阈值可提前终止重试；不是跨节点集中式熔断 |
| 渠道指标/usage | 运行成功/失败/平均耗时；每 30 秒落库熔断快照；返回并保存实际模型 usage/finishReason | 快照多实例最后写入者覆盖；未返回或失败调用 usage 未知，不伪造完整账单；摘要调用不并入聊天 token |
| 非兼容协议 | Ollama /api/chat，NDJSON 流、真实 usage、异常/截断检查、取消；OpenAI 兼容工厂 | 文本 ReAct；不承诺原生 function calling；Ollama 图片只接受内嵌数据 |
| 身份与多用户 | 校验 Bearer，user/admin 角色；会话/文档归属、检索已发布版本按 ownerId 过滤 | 不建设登录/发 token 系统；不含组织、共享知识库、细粒度 ACL；auth=false 仅 demo |
| 文档跨节点执行 | MySQL 60 秒租约、独立调度线程每 10 秒续租；令牌校验及短事务保护发布/失败/删除；30 秒恢复与补投；哈希唯一键 | 多实例须统一启用；外部 ES/文件动作不与 MySQL 做分布式事务，失效工作者可能留下不可检索旧块，需维护清理 |
| 会话跨节点执行 | session 租约，开始/完成/摘要写回受保护；无有效租约的 running 消息标 interrupted | SDK 外部请求不承诺即时物理停止；锁丢失后禁止持久化成功 |
| 可靠事件 | 成功消息与 3 个事件同事务落库；条件领取、60 秒投递租约、失败退避重试 | 至少一次、可能重复、跨事件不保证顺序；同步监听器成功才确认，业务订阅者自行以 eventId 幂等；无 MQ |
| OCR | 图片及 PDF 无文字页转 PNG，POST HTTP 服务，保留页号 | 图片编码需本机 ImageIO 支持（WebP 可能不支持）；混合图文页不会对已有文字页重复 OCR；需要真实 OCR 服务 |
| 存储 | 本地及 S3 私有 PUT/GET/DELETE；签名、响应限制、临时文件清理；S3 模式兼容旧本地文件 | 路径式地址和静态 access/secret；无 STS、分片上传、预签名下载；切换 bucket/退回 local 需另行迁移 |
| 音视频 | POST /api/media/transcribe；知识库媒体解析也复用转写；视频 FFmpeg 抽音轨 | 无语音合成、实时音视频、画面理解；上传最多 20MB，提取最多 60 秒、音轨达到 20MB 明确拒绝，禁止静默截断 |
| 图片与工具 | 纯图片或文字+图片可进入 ReAct，附件保留；按 vision/vision-tools 过滤路由 | 一次一张图片，实际识图和组合能力依赖模型；预算是字符及固定图片估算，不是精确 tokenizer |
| 检索重排 | HTTP 对已召回片段重新排序，验证完整下标排列 | 不新增跨编码模型；重排失败 warning + 原顺序，不伪造排序成功 |
| 运维与架构 | admin /api/ops/status：数据库连通、文档状态、路由计数、outbox 积压、有效租约数 | esEnabled 仅开关，不是 ES 集群健康；未建设告警平台/自动集群运维；严格模块依赖倒置仍是明确结构性债务 |

**数据库与部署顺序（本次未执行真实迁移）**

1. 已有环境先备份并完成 12.2 的 P1 迁移，再执行 `easychat-infra/src/main/resources/sql/migrations/20260927_p2.sql` 一次。执行唯一键前检查脚本中的重复查询，人工确认重复文档的保留方式，不自动删数据。新环境直接使用当前 schema.sql，不重复跑增量脚本。
2. owner_id 默认 demo，大小写敏感。旧会话/文档不会自动归给第一个登录用户；管理员须按真实归属制定迁移，未知归属仍留 demo。认证开关在全部实例一致，生产多用户必须开启。
3. 配置身份校验 URL 后设 AUTH_ENABLED=true；模型管理、原始 ES 和运维接口只允许 admin。普通用户用 /api/models 选择模型。关闭认证保持原单用户演示路径。
4. 多实例共用同一 MySQL/ES，设 DISTRIBUTED_ENABLED=true；所有实例使用相同绝对共享文件目录及配置。可使用 S3 存放新文件，但既有本地引用仍要求共享目录可达。租约依赖数据库时间，不依赖各节点本地时钟。
5. 需要可靠事件时保持 events.enabled=true 并设 OUTBOX_ENABLED=true。监听器抛错会重投；不要用异步监听器返回成功来表示下游事务已经成功。自定义 ChatEventPublisher 属于非 outbox 路径，outbox 使用 Spring 同步事件订阅。已完成事件与过期租约不自动清理，制定保留期后按状态归档，禁止直接清理未投递事件。
6. 配置外部服务并逐项验收后再启用对应能力。FFMPEG_PATH 必须指向部署节点实际程序；当前机器未找到 FFmpeg，未完成真实视频提取验证。S3 模式要求预建私有 bucket，静态凭据使用环境变量；不要将密钥写入文档或请求体。

**身份系统适配契约**

EasyChat 向 AUTH_INTROSPECTION_URL 发 POST，Content-Type: application/json，body 为 `{}`，原样转发调用者 `Authorization: Bearer <token>`。成功 HTTP 200 示例：

```json
{"active":true,"userId":"stable-user-id","roles":["user"]}
```

管理员 roles 可为 `["admin"]`。userId 为稳定非空字符串，最长 128 字符；只由可信身份接口提供。active=false/无效 token 返回 401；无角色权限 403；超时、服务异常或无有效配置返回 503。完整响应期限 5 秒、响应体上限 64KB，不跟随重定向，不记录 token。异步聊天/入库通过显式上下文传身份，不依赖工作线程的 ThreadLocal。

**OCR、转写、重排 HTTP 契约**

下列地址由服务端配置，不能由请求或模型选择。POST application/json，可选 token 对应 Authorization: Bearer。成功 HTTP 2xx，完整调用期限 60 秒、响应体上限 4MB；失败明确报错，只有重排按约定可见降级。

```json
// OCR_URL：请求 -> 响应
{"imageBase64":"PNG字节的Base64，不带data前缀","mimeType":"image/png"}
{"text":"识别结果"}

// TRANSCRIPTION_URL：请求 -> 响应
{"audioBase64":"音频字节的Base64","mimeType":"audio/wav"}
{"text":"转写结果"}

// RERANK_URL：请求 -> 响应（下标必须覆盖所有输入且不重复）
{"query":"问题","documents":["片段A","片段B"]}
{"indices":[1,0]}
```

转写支持 wav/mp3/m4a/ogg/flac，直接音频的 mimeType 为 audio/<扩展名>；视频支持 mp4/webm/mov/mkv，转换后为 audio/wav。接供应商原生服务时，若字段不同，新增或替换对应适配类即可，不修改聊天或入库编排。POST /api/media/transcribe 只返回转写文字；需要聊天时再把文字提交 /api/chat，需要入库时通过 /api/knowledge/upload 上传媒体。

Ollama 渠道 base_url 为服务根地址，配置 `easychat.providers.protocols.<providerCode>=ollama`，模型代码为已部署模型名；新协议实现 ProviderFactory + LLMProvider。接口依据 [Ollama Chat API](https://docs.ollama.com/api/chat)。S3 签名采用 [AWS SigV4 请求头约定](https://docs.aws.amazon.com/AmazonS3/latest/developerguide/sig-v4-header-based-auth.html)；当前仅实现所需对象操作，不引入完整云管理 SDK。

**部署验收最短路径**

- 身份：两个 token 创建各自会话/上传同 dataset 文件；互查会话/文档返回 404，RAG/工具仅命中各自发布版本；user 访问 /model、/es、/api/ops 返回 403。
- 路由：两个同优先级渠道配置不同权重；模拟首段前故障检查有限重试和切换；输出后故障不拼接；冷却结束并发只放一个探针。检查 meta.usage.complete 与真实模型返回一致。
- 图像/媒体/检索：视觉模型提交纯图片及 toolsEnabled=true，检查 hello action/observation；扫描 PDF 上传后 OCR 内容可检索；小音频/视频转写后核对文字；两片段重排结果符合返回 indices。
- 存储：S3 模式上传小 TXT，确认对象、入库状态、来源、删除；切换前已有本地文档仍可读取并删除。
- 多实例：两节点争用同一会话/文档，仅一方领取；中止工作节点，租约到期后恢复；模拟旧工作者返回，确认不能发布/覆盖新结果。
- 事件：成功消息与 outbox 同事务；让同步订阅者先抛错再恢复，确认相同 eventId 重投；模拟提交后进程退出，另一节点仍可领取。定期查看 /api/ops/status，并分别检查真实 ES 集群健康、数据库备份与磁盘空间。

**自动验证**

应用与测试编译成功；P0/P1/P2 共 77 项测试，76 通过、0 失败、0 错误、1 跳过。跳过项仍是 Windows 根外目录符号链接权限测试，没有计为通过。新增 P2RoutingTest、P2IdentityTest、P2DurabilityTest、P2HttpAdaptersTest、P2BoundariesTest，覆盖路由与半开、身份/归属、真实 H2 事务和租约、进程中断恢复、本地 HTTP 协议调用、响应大小/超时、旧存储兼容、摘要写入保护、图片工具上下文。H2 MySQL 模式不是 MySQL 实例；本地 HTTP 服务不是供应商联调。S3 测试检查请求/哈希及读写删除，不代表已通过真实服务的 SigV4 鉴权验收。

```powershell
New-Item -ItemType Directory -Force -Path 'easychat-test/target/p0-temp' | Out-Null
$p2Temp = Join-Path (Get-Location) 'easychat-test/target/p0-temp'
mvn -o -pl easychat-test -am test-compile org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test '-Dtest=P0*Test,P1*Test,P2*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.redirectTestOutputToFile=true' "-DargLine=-Djava.io.tmpdir=$p2Temp"
```

未连接真实登录系统、MySQL、ES、Ollama/模型、OCR、S3、转写或重排服务；未修改线上数据。真实集成与部署验收仍按上面的最短路径执行，不能把配置开关和替身测试等同于生产验收。
### 12.4 实现统一性整改（2026-09-28）

依据 [EasyChat缺陷登记](EasyChat缺陷登记.md) 的 DB-001—004、ARCH-001—004、UNI-001—006，完成 14 项维护性整改。继续保留六模块及全部既有能力，没有新增框架、外部服务、表结构或接口版本；本节不替代历史功能复审 R1—R5 的状态。

- 关系数据库生产访问统一 MyBatis-Plus/Mapper，全部查询条件和执行细节集中 infra。OperationsController 只调用 OperationsService；后者复用 OperationsQueries 和现有表 Mapper。文档调用 KnowledgeStore，路由加载调用 RouteCatalog，MySQL 工具调用现有消息 Repository。
- 事件和租约各有一份 common 实体/infra Mapper。DurableChatEvents 保留类型转换、同步发布和重试编排；MysqlLeaseService 保留令牌、独立续租及 TransactionTemplate。消息与事件同事务，领取仍是原子条件更新，guarded 的行锁与写入仍在同一短事务，旧令牌不能确认或释放新持有者；文档/聊天恢复仍排除有效租约。
- 五组等价 DO/domain 合并为 common/domain 的唯一映射实体；摘要、文档、事件和租约在 common/entity。保留既有四个共享数据访问契约并移入 common/port；消息新增/删除通过统一 Repository，删除未使用的 addMessage 旁路。API 显式 DTO/View 防止公开 ownerId、密钥或物理路径。
- AgentFacade/SSEUtil 与事件 JSON 编码移到 api；同步和 SSE 仍共用 ChatExecutionService。ChatResponse、ChatMessageView、SessionUpdateRequest 及各 View 是实际 API 契约；删除闲置 AddModelRequestDTO。保留响应字段、Result 包装差异、sources 字符串、multipart 和 204。
- 模型旧重载仅把 prompt/images 转换为结构化消息后委托同一实现，保留 options、usage、错误和取消语义。普通 JSON 使用 Spring 管理的 ObjectMapper；meta 内部使用对象载荷，action 保留 tool/input 字段，到 API 才序列化。
- HttpTransportConfiguration 集中创建共享 JDK HttpClient；OCR/转写/重排注入 JsonHttpService，身份、S3、Ollama 复用该客户端并保留各自请求超时/凭据、签名和 BoundedHttp 保护。SDK 自有传输仍由 SDK 管理，不新增另一套模型框架。
- MessageRole、MessageStatus、SessionStatus、DocumentStatus 统一定义于 common，原数据库值和前端格式不变。父 POM 仅管理 Druid 版本，由 infra 引入连接池/驱动；H2 仅在 easychat-test/test，common 无 Web/数据库 starter。

**验证：** 清理旧编译产物后，全模块编译及 P0/P1/P2 + Architecture 定向回归通过：84 项，83 通过、0 失败、0 错误、1 因符号链接权限跳过。新增测试实际执行 Mapper，验证 Spring 事务代理下消息与事件共同回滚/提交、领取竞争与过期恢复、旧令牌保护、租约行锁直到提交、文档恢复；同时检查运维 8 种开关组合、响应字段和模型兼容入口。Maven dependency:tree 确认依赖归属。命令与逐项映射见缺陷登记第 10 节。

**验证边界：** 数据库隔离测试采用 H2 MySQL 模式及 SqlSessionTemplate，不是仅 mock Mapper。未连接真实 MySQL/ES/模型服务；部署前仍需验证 MySQL 方言、隔离级别、跨实例续租/竞争及慢订阅者重投。原有外部服务联调要求保持，outbox 仍为至少一次交付。

**升级：** 本批无需数据库迁移。包名和 Java 类型已迁移/合并，仓库内引用已同步；部署须 clean 后整体构建，避免旧类残留。外部自定义扩展若直接引用旧 core/domain、infra/mysql/entity、core/port 或 core/facade 包，需按第 2 节对应新归属更新 import；HTTP 前端无需因本批重构改变协议。

### 12.5 前端真实联调修复（2026-09-28）

详见 [后端待修复缺陷复验记录](EasyChat后端待修复缺陷.md)。19 处路径参数已显式命名，Maven 开启 parameters；已有会话先校验用户范围及关闭状态，再检查模型，新会话仍通过模型校验后才创建。意外 500 记录服务端异常链与 errorId，响应保留原 error 字段并通过 X-Error-Id 关联日志。

ES 使用 SDK TransportUtils 支持 ES_CA_FINGERPRINT（HTTP CA SHA-256 指纹）或 ES_CA_CERTIFICATE（本地 PEM/CRT 路径），与 ES_TRUST_ALL 互斥；默认保留主机名校验。当前部署的用户确认指纹已作为默认值，可由环境变量覆盖。使用 CA 文件时须将 ES_CA_FINGERPRINT 设为空。ES_URIS 可覆盖地址。真实验证中 CA 已可信，但当前证书不包含公网 IP 106.55.40.72，仍需部署方提供匹配且可达的地址或重签证书；ES、知识库完整链路不能标为通过。

回归 91 项：90 通过、1 项符号链接权限跳过。构建启动后 8080 实际 HTTP 冒烟 15 项中 14 项通过，仅 ES 探活失败；原报告遗留临时会话已精确清理。模型/渠道/路由仍为空，真实模型联调待部署配置。

### 12.6 ds_1 / DS_Ofiice 真实聊天排查（2026-10-01）

已读取介绍文档并连接正在运行的 8080 服务，确认模型、渠道和绑定均启用。首次 POST /api/chat 使用 ds_1 返回 HTTP 400，错误为 Invalid model context window：context_window=1024、max_output_tokens=1024，预留输出后没有输入预算。另一个代码问题是直接将内部 model_code=ds_1 当作供应商模型名发送；model_name=DeepSeek 是展示名称，不能作为上游模型 ID。

修复后 default_config 支持可选的 model_name 字符串。ModelRouter 仍用 model_code 查找绑定、校验启用状态和保存消息，但将 default_config.model_name 传给供应商；不配置时沿用原有 model_code，兼容已有配置。同步、SSE 和工具模式共用这一映射。模型创建/更新拒绝非正输出上限，以及 context_window 与有效输出上限相差不足 128 的配置；更新检查合并后的字段。聊天入口继续拒绝已有无效配置并明确说明预算要求。

本次通过管理接口将测试模型 ds_1 的 contextWindow 调整为 8192（保守测试预算，不表示供应商实际最大上下文），保持 maxOutputTokens=1024，并设置 defaultConfig 为 {"model_name":"deepseek-chat"}；保留 DS_Ofiice 的地址、密钥和绑定。修改前配置保存在 easychat-app/target/ds_1-before-chat-fix.json，不含渠道密钥。未执行表结构迁移。

验证使用当前编译代码在临时 8081 实例执行，关闭 ES，仅验证不启用工具/RAG 的聊天。同步返回 content=OK、providerCode=DS_Ofiice、finishReason=stop；SSE 返回 message=OK、最终 meta 和唯一 finish，无 error。两次请求的 user/assistant 均成功保存，modelCode 保持 ds_1，assistant 保存实际渠道和完整 usage（13 输入、1 输出、14 总 token）。仅清理本次创建的两个测试会话，未改动原有会话。临时实例验证后停止。

相关回归 25 项全部通过，含新增 ChatModelConfigurationTest 的别名同步/流式调用、兼容原模型代码、无效预算创建与局部更新、非法别名拒绝。首次边界测试因系统临时目录权限失败，指定工作区内临时目录后复验通过。测试输出见 easychat-test/target/surefire-reports；真实响应见 easychat-app/target/chat-fix-sync.json 和 chat-fix-stream.txt。

当前 8080 服务是修改前已经启动的实例，必须重启并加载本次编译的完整模块才能使用别名映射；仅修改数据库不会使旧 Java 类获得新行为。普通用户请求仍传 model=ds_1。若使用已有打包产物或本地仓库中的旧依赖启动，需整体重新构建后部署，避免只更新 app 而继续加载旧 core。真实 ES/RAG 和工具模式不计入本次验收。
