# EasyChat Backend 全局检查报告

生成时间：2026-06-08  
检查范围：`easychat-backend` 根目录下全部 Maven 模块、主要配置、SQL schema、核心 Java 代码、构建与测试状态。

## 1. 总体结论

当前项目是一个 Spring Boot 3.2.5 + Java 17 的多模块后端工程，包含 API、应用启动、核心编排、基础设施、LLM、RAG、Memory、Tools、Multimodal 和测试模块。项目整体可以编译并通过现有测试，说明当前主干代码没有明显编译级阻断问题。

本次检查发现的主要风险集中在：

1. 配置文件中存在明文数据库账号、密码和默认 API Key，属于高风险安全问题。
2. 测试会连接真实 MySQL，缺少隔离的 test profile 或 H2/Fake 实现。
3. 父 POM 直接声明运行时依赖，导致所有子模块被动继承 Druid，模块边界不够干净。
4. `core` 模块仍直接依赖 `infra`、`llm`、`rag`、`memory`、`tools`，端口适配边界还没有完全收敛。
5. Controller 入参缺少 Bean Validation，部分接口对空消息、空 model、非法 maxRounds 等情况容易出现运行时异常。
6. 代码中字段注入较多，异常处理和日志规范仍需统一。

## 2. 模块结构检查

当前根 POM 聚合模块：

| 模块 | 当前职责 | 检查结论 |
| --- | --- | --- |
| `easychat-common` | 通用模型、异常、工具类 | 职责清晰，但引入 `spring-boot-starter-web` 只为 `SseEmitter`，会让 common 偏重 Web |
| `easychat-infra` | MySQL、Elasticsearch、DO/Mapper/schema | 基础设施职责明确，SQL 与实体大体一致 |
| `easychat-memory` | 会话记忆与摘要 | 功能仍较薄，摘要服务仍是 TODO |
| `easychat-rag` | Embedding/Retriever 抽象 | 当前实现偏骨架，需要补齐存储和召回实现 |
| `easychat-tools` | Tool 注册与 MySQL 查询工具 | 工具框架可用，但示例工具查询逻辑较粗 |
| `easychat-llm` | LangChain4j LLM 客户端 | 可用，但模型运行参数和模型表配置尚未打通 |
| `easychat-multimodal` | 多模态处理 | 当前只有基础 ImageProcessor，仍是占位模块 |
| `easychat-core` | Agent、UseCase、Router、领域与端口 | 核心职责集中，但仍直接依赖多个实现模块 |
| `easychat-api` | REST Controller、DTO | 接口简单可用，缺少参数校验和统一异常处理 |
| `easychat-app` | Spring Boot 启动模块 | 启动入口清晰，配置集中在 application.yml |
| `easychat-test` | 集成测试 | 有 2 个测试用例，但连接真实数据库 |

## 3. 构建与测试结果

已执行：

```powershell
mvn compile -DskipTests
```

结果：通过，12 个模块全部 `BUILD SUCCESS`。

已执行：

```powershell
mvn test -DskipTests=false
```

结果：通过，`AgentFacadeTest` 共 2 个测试，全部成功。

注意：测试启动时使用了默认 profile，并连接了 `application.yml` 中的真实 MySQL 数据库。这说明测试结果依赖外部环境，不具备稳定的本地/CI 可重复性。

## 4. 高风险问题

### 4.1 配置文件存在明文敏感信息

位置：`easychat-app/src/main/resources/application.yml`

发现内容：

- 明文 MySQL 地址、用户名、密码。
- Druid 控制台默认账号密码。
- LLM `api-key` 配置带默认真实格式 Key。

风险：

- 一旦提交到远程仓库，会造成数据库和模型服务凭据泄露。
- 测试、开发、生产环境无法清晰隔离。
- 默认 Druid 控制台账号密码容易被扫描利用。

建议：

1. 立即将数据库密码、API Key、Druid 控制台密码改成环境变量占位。
2. 对已经暴露过的数据库密码和 API Key 做轮换。
3. 增加 `application-local.yml` 到 `.gitignore`，仓库只保留 `application-example.yml`。
4. 生产环境关闭 Druid stat-view 或至少使用强密码、内网访问限制。

建议格式：

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

easychat:
  llm:
    api-key: ${OPENAI_API_KEY}
```

### 4.2 测试连接真实数据库

位置：`easychat-test/src/test/java/com/easychat/test/AgentFacadeTest.java`

现象：

- `@SpringBootTest(classes = EasyChatApplication.class)` 使用默认配置启动。
- 测试日志显示启动了真实 MySQL 连接池。

风险：

- 本地和 CI 测试受外部网络、数据库状态、数据污染影响。
- 测试可能误写真实数据。

建议：

1. 新增 `easychat-test/src/test/resources/application-test.yml`。
2. 使用 H2 或 Testcontainers MySQL。
3. 测试类增加 `@ActiveProfiles("test")`。
4. 对需要 MySQL 特性的 SQL 单独做集成测试，不和普通单元测试混在一起。

## 5. 中风险问题

### 5.1 父 POM 直接声明 Druid 依赖

位置：根目录 `pom.xml`

问题：

父 POM 当前在 `<dependencies>` 中直接声明了 `druid-spring-boot-starter`，这会让所有子模块继承该依赖。对于 `common`、`api`、`llm`、`rag`、`multimodal` 等模块来说，数据库连接池并不是它们的直接职责。

建议：

- 将 Druid 版本移入 `<dependencyManagement>`。
- 只在 `easychat-infra` 或 `easychat-app` 中按需声明 Druid 依赖。

### 5.2 core 模块仍直接依赖 infra 与具体能力模块

位置：`easychat-core/pom.xml`

当前 `core` 依赖：

- `easychat-infra`
- `easychat-llm`
- `easychat-tools`
- `easychat-rag`
- `easychat-memory`

问题：

虽然代码中已经有 `port` 包，但 `core` 仍然直接依赖实现模块，架构上还不是严格的端口-适配器模式。

建议演进顺序：

1. `core` 只保留领域模型、UseCase、Port 接口。
2. MySQL Repository 实现迁移到 `infra`。
3. LLM/RAG/Memory/Tools 通过 core 中的 Port 接口接入。
4. 由 `app` 模块完成装配。

### 5.3 Controller 缺少参数校验

位置：

- `easychat-api/src/main/java/com/easychat/api/controller/ChatController.java`
- `easychat-api/src/main/java/com/easychat/api/controller/ModelController.java`

问题示例：

- `chat`、`streamChat` 已对 `model`、`messages` 和最后一条消息 `content` 做基础校验，后续仍建议统一为 Bean Validation。
- `createSession` 已调整为不接收模型参数；模型改为每次聊天实时传入。
- `setMaxRounds` 未校验 `maxRounds` 是否为空或小于 1。
- 模型、渠道管理接口没有 `@Valid` 和 DTO 字段约束。

建议：

1. 引入 `spring-boot-starter-validation`。
2. DTO 使用 `@NotBlank`、`@NotNull`、`@Min`、`@Size`。
3. Controller 使用 `@Valid @RequestBody`。
4. 增加全局异常处理器，统一返回 `Result` 或统一错误响应。

### 5.4 字段注入较多

发现大量 `@Autowired` 字段注入，分布在 Controller、Service、Repository、Agent、LLM、Tools、Memory 等模块。

风险：

- 不利于单元测试。
- 依赖关系不够显式。
- 部分可选依赖使用 `@Autowired(required = false)`，启动行为不够直观。

建议：

- 优先改为构造器注入。
- 使用 Lombok `@RequiredArgsConstructor` 或显式构造器。
- 可选能力用 `ObjectProvider<T>` 或空实现 Bean 表达。

### 5.5 异常处理与日志规范不统一

发现点：

- `SSEUtil` 多处 `e.printStackTrace()`。
- 部分代码 `catch (Exception e)` 后返回字符串错误。
- 部分底层异常直接包装成 `RuntimeException`。
- Controller 中有局部 try-catch，也有直接抛出路径。

建议：

1. `SSEUtil` 改用 `Slf4j` 日志。
2. 建立统一异常类型，例如 `BusinessException`、`InfrastructureException`、`LLMException`。
3. API 层增加 `@RestControllerAdvice`。
4. 对外响应不要暴露底层异常细节。

## 6. 低风险与改进项

### 6.1 `.gitignore` 已忽略 target，但当前工作区已有 target 产物

`.gitignore` 已包含：

```gitignore
target/
.idea/
*.iml
```

当前本地目录中存在各模块 `target` 构建产物，这通常没有问题，只要没有被 Git 跟踪即可。

建议：

- 提交前执行 `git status --short` 确认没有 target 文件进入暂存区。
- 如果历史上曾提交过 target，需要用 `git rm --cached` 清理。

### 6.2 Kafka 已无明显残留

本次搜索未发现 Java、YAML、POM 中存在 Kafka 依赖或配置残留。说明前面移除 Kafka 的工作基本完成。

### 6.3 model 表优化已同步到代码

`schema.sql` 中 `model` 表已包含：

- `model_name`
- `model_type`
- `model_family`
- `context_window`
- `max_output_tokens`
- `default_temperature`
- `default_top_p`
- `default_config`

对应 Java 映射已同步到：

- `ModelDO`
- `ModelDefinition`
- `ModelCommand`
- `ModelView`
- `ModelRequestDTO`
- `ModelCatalogMysqlRepository`
- `ModelManageService`

未发现 `support_stream/support_tools/support_vision/support_json_output` 字段残留。

### 6.4 LLM 运行参数尚未与模型表打通

当前 `easychat-llm` 仍主要使用 `easychat.llm.max-tokens`、`temperature` 等配置项。模型表中的 `max_output_tokens`、`default_temperature`、`default_top_p` 目前主要进入模型管理链路，尚未完整进入实际调用链路。

建议：

1. 在路由时读取 `ModelDefinition`。
2. 将模型默认参数合并进 `ChatExecutionContext`。
3. 按优先级合并参数：消息级参数 > 会话级参数 > 模型默认参数 > 应用默认参数。

### 6.5 Memory/RAG/Multimodal 仍是早期骨架

发现：

- `SimpleSummaryService` 仍有 TODO。
- RAG 模块目前只有基础接口和简单类。
- Multimodal 模块功能较少。

建议：

- 如果近期不使用，保持模块轻量但避免对 core 产生硬依赖。
- 如果要继续演进，优先定义 Port 和测试，再补实现。

## 7. 数据库与 SQL 检查

当前 schema 包含 8 张表：

1. `chat_session`
2. `chat_session_config`
3. `chat_message`
4. `model`
5. `provider`
6. `model_provider`
7. `chat_request_log`
8. `chat_session_memory`

总体评价：

- `chat_session_config`、`chat_message` 对 `chat_session` 有外键并设置级联删除。
- `model_provider` 通过 `(model_code, provider_code)` 唯一约束避免重复路由。
- `model` 表已按模型定义与默认参数拆分。

建议补充：

1. `chat_session` 已不保存固定模型；实际调用模型记录在 `chat_message.model_code` 和请求日志中。
2. `model_provider.model_code` 和 `provider_code` 可考虑增加外键，或者在服务层保证一致性。
3. `chat_session_memory.session_id` 当前只有索引，没有外键；建议按数据生命周期决定是否加 `ON DELETE CASCADE`。
4. `provider.api_key` 建议加密存储，不建议明文入库。

## 8. API 设计检查

当前 API 主要包括：

- `/api/chat`
- `/api/chat/stream`
- `/api/session`
- `/api/sessions`
- `/model/provider/*`
- `/model/*`

建议：

1. 统一 API 前缀，例如全部使用 `/api/...`，模型管理改为 `/api/models`、`/api/providers`。
2. 删除接口建议使用 `DELETE /api/models/{modelCode}`，避免 `POST /delete`。
3. 更新接口建议使用 `PUT /api/models/{modelCode}`。
4. 对流式接口补充心跳、超时和客户端断开处理。
5. 对管理类接口增加鉴权，当前没有看到 Spring Security 或网关鉴权。

## 9. 推荐整改优先级

### P0：立即处理

1. 移除仓库中的明文数据库密码、API Key、Druid 默认密码。
2. 轮换已经暴露过的数据库密码和模型 API Key。
3. 增加 test profile，避免测试连接真实数据库。

### P1：近期处理

1. 父 POM 移除直接依赖，只保留 dependencyManagement。
2. Controller 增加 Bean Validation 和全局异常处理。
3. 将字段注入逐步改为构造器注入。
4. `SSEUtil` 移除 `printStackTrace`。

### P2：架构演进

1. 收敛 `core` 模块依赖，强化 Port/Adapter 边界。
2. 将模型表默认参数接入实际 LLM 调用链路。
3. 为 Model/Provider/Session/Chat 增加单元测试和集成测试。
4. 完善 Memory、RAG、Tools 的能力边界和可选装配。

## 10. 本次检查命令摘要

执行过的主要命令：

```powershell
mvn compile -DskipTests
mvn test -DskipTests=false
rg -n "kafka|Kafka|spring.kafka|bootstrap-servers" .
rg -n "password:|api-key:|username:|url: jdbc:mysql" easychat-app/src/main/resources/application.yml
rg -n "@Autowired|printStackTrace|TODO|FIXME|catch \\(Exception" . -g "*.java"
rg -n "support_(stream|tools|vision|json_output)" .
```

检查结果：

- 编译通过。
- 测试通过。
- 未发现 Kafka 残留。
- 未发现 `support_*` 模型字段残留。
- 发现明文敏感配置。
- 发现测试连接真实 MySQL。
- 发现字段注入、宽泛异常捕获、`printStackTrace` 和 TODO。

## 11. 结论

项目当前处于“可以运行和继续开发”的状态，但还不适合直接作为生产级后端发布。最需要优先处理的是配置安全和测试隔离，其次是依赖边界、参数校验、异常处理和模块解耦。

建议下一步先做 P0：

1. 配置脱敏和密钥轮换。
2. 建立 `application-test.yml`。
3. 让 `mvn test` 不依赖真实数据库。
