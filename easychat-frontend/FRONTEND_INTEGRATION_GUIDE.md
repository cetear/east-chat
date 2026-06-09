# EasyChat Backend 前端对接参考文档

生成日期：2026-06-09  
适用项目：`easychat-backend`  
默认后端地址：`http://localhost:8080`

## 1. 项目说明

EasyChat Backend 是一个基于 Spring Boot 的 AI 对话后端，当前前端主要对接范围包括：

- 创建会话
- 查询会话列表
- 查询单个会话
- 更新会话
- 删除会话
- 普通非流式聊天
- SSE 流式聊天
- 设置会话最大上下文轮数

当前代码中模型/渠道管理相关 DTO 仍存在，但 `ModelController.java` 和部分模型管理 Service 在当前工作区为缺失状态，因此前端暂不应依赖模型/渠道管理接口。待后端恢复对应 Controller 后，再补充管理端页面。

## 2. 后端服务信息

### 2.1 基础地址

开发环境默认：

```text
http://localhost:8080
```

前端建议通过环境变量配置：

```env
VITE_API_BASE_URL=http://localhost:8080
```

如果使用 Next.js：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

### 2.2 接口前缀

聊天和会话接口统一前缀：

```text
/api
```

完整示例：

```text
POST http://localhost:8080/api/chat
```

### 2.3 跨域

后端存在 `WebConfig`，通常用于跨域配置。前端本地开发时如遇 CORS 问题，优先检查：

- 前端开发服务地址是否被后端允许，例如 `http://localhost:5173`
- 请求方法是否包含 `GET/POST/PUT/DELETE`
- SSE 接口是否允许跨域

## 3. 数据结构

### 3.1 ChatRequest

用于普通聊天和流式聊天。

```json
{
  "sessionId": "会话业务ID，可为空",
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

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | string | 否 | 已有会话的 `sessionCode`。为空时后端会创建新会话 |
| `model` | string | 新会话时建议必填 | 新建会话使用的模型编码 |
| `messages` | array | 是 | 消息数组，后端当前取最后一条消息作为用户输入 |
| `messages[].role` | string | 是 | 建议值：`user`、`assistant`、`system` |
| `messages[].content` | string | 是 | 消息内容 |
| `toolsEnabled` | boolean | 否 | 是否启用工具调用，默认 `false` |
| `ragEnabled` | boolean | 否 | 是否启用 RAG，默认 `false` |

注意：

- 当前后端没有对 `messages` 做空数组保护。前端必须保证 `messages` 至少有一条消息。
- 当前后端只读取 `messages` 最后一条的 `content`，不会完整消费前端传入的历史消息。历史上下文由后端根据会话 ID 从数据库读取。

### 3.2 MessageDTO

```json
{
  "role": "user",
  "content": "请介绍一下这个项目"
}
```

### 3.3 SessionView

会话响应结构。

```json
{
  "id": 1,
  "sessionCode": "d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "title": "New Chat",
  "modelCode": "deepseek-chat",
  "systemPrompt": null,
  "maxRounds": 10,
  "status": 1,
  "createdAt": "2026-06-09T10:00:00",
  "updatedAt": "2026-06-09T10:05:00"
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 数据库内部 ID |
| `sessionCode` | string | 业务会话 ID，前端后续请求使用这个值 |
| `title` | string | 会话标题 |
| `modelCode` | string | 会话绑定模型 |
| `systemPrompt` | string/null | 系统提示词 |
| `maxRounds` | number | 最大上下文轮数 |
| `status` | number | 状态，通常 `1` 表示正常 |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |

## 4. 聊天接口

### 4.1 普通聊天

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
      "content": "你好，请介绍一下 EasyChat"
    }
  ],
  "toolsEnabled": false,
  "ragEnabled": false
}
```

成功响应：

```json
{
  "content": "你好，EasyChat 是一个 AI 对话后端项目...",
  "sessionId": "d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

失败响应示例：

```json
{
  "error": "错误信息"
}
```

前端处理建议：

- 如果请求时 `sessionId` 为空，使用响应中的 `sessionId` 保存为当前会话 ID。
- 用户输入发送前，先在前端本地追加用户消息，再等待后端响应追加 assistant 消息。
- `messages` 至少传一条用户消息。

TypeScript 示例：

```ts
type ChatMessage = {
  role: 'user' | 'assistant' | 'system';
  content: string;
};

type ChatRequest = {
  sessionId?: string | null;
  model?: string;
  messages: ChatMessage[];
  toolsEnabled?: boolean;
  ragEnabled?: boolean;
};

type ChatResponse = {
  content: string;
  sessionId: string;
};

export async function chat(request: ChatRequest): Promise<ChatResponse> {
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.error || `HTTP ${res.status}`);
  }

  return res.json();
}
```

### 4.2 SSE 流式聊天

```http
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

请求示例：

```json
{
  "sessionId": "d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "请用三句话解释 RAG"
    }
  ],
  "toolsEnabled": false,
  "ragEnabled": true
}
```

后端使用 `SseEmitter` 返回事件流。当前可能出现的事件名：

| 事件名 | 含义 | 前端处理 |
| --- | --- | --- |
| `message` | 模型输出文本片段 | 追加到当前 assistant 消息 |
| `thought` | Agent 思考过程 JSON | 可显示在调试面板，普通用户可隐藏 |
| `action` | 工具调用动作 JSON | 可显示工具调用状态 |
| `observation` | 工具调用结果 JSON | 可显示工具结果 |
| `error` | 错误信息 | 停止 loading，展示错误 |
| `finish` | 完成原因 | 停止 loading，关闭连接 |

注意：

- 浏览器原生 `EventSource` 不支持 POST 请求。该接口是 POST SSE，前端需要使用 `fetch` 读取 `ReadableStream`，或者引入支持 POST SSE 的库。
- 流式接口返回的是标准 SSE 格式，前端需要按 `event:` 和 `data:` 解析。

Fetch 流式读取示例：

```ts
type SseEvent = {
  event: string;
  data: string;
};

function parseSseChunk(chunk: string): SseEvent[] {
  return chunk
    .split('\n\n')
    .map(block => {
      const event = block.match(/^event:\s*(.*)$/m)?.[1] || 'message';
      const data = block.match(/^data:\s*([\s\S]*)$/m)?.[1] || '';
      return { event, data };
    })
    .filter(item => item.data);
}

export async function streamChat(
  request: ChatRequest,
  onEvent: (event: SseEvent) => void
) {
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(request)
  });

  if (!res.ok || !res.body) {
    throw new Error(`HTTP ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split('\n\n');
    buffer = parts.pop() || '';

    for (const part of parts) {
      for (const event of parseSseChunk(`${part}\n\n`)) {
        onEvent(event);
      }
    }
  }
}
```

前端 UI 建议：

- 开始请求时创建一条空的 assistant 消息。
- 收到 `message` 事件时追加文本。
- 收到 `finish` 时结束 loading。
- 收到 `error` 时标记当前消息失败。
- 提供“停止生成”按钮，使用 `AbortController` 中断 fetch。

Abort 示例：

```ts
const controller = new AbortController();

fetch(url, {
  method: 'POST',
  body: JSON.stringify(payload),
  signal: controller.signal
});

controller.abort();
```

## 5. 会话接口

### 5.1 创建会话

```http
POST /api/session
Content-Type: application/json
```

当前后端字段名是 `modelType`，实际会作为 `modelCode` 创建会话。

请求示例：

```json
{
  "modelType": "deepseek-chat"
}
```

响应：

```json
{
  "id": 1,
  "sessionCode": "d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "title": "New Chat",
  "modelCode": "deepseek-chat",
  "systemPrompt": null,
  "maxRounds": 10,
  "status": 1,
  "createdAt": "2026-06-09T10:00:00",
  "updatedAt": "2026-06-09T10:00:00"
}
```

前端建议：

- 前端内部统一使用 `modelCode` 命名，但请求当前接口时映射为 `modelType`。
- 创建成功后，将 `sessionCode` 作为当前会话 ID。

### 5.2 查询会话列表

```http
GET /api/sessions
```

响应：

```json
[
  {
    "id": 1,
    "sessionCode": "d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "title": "New Chat",
    "modelCode": "deepseek-chat",
    "systemPrompt": null,
    "maxRounds": 10,
    "status": 1,
    "createdAt": "2026-06-09T10:00:00",
    "updatedAt": "2026-06-09T10:05:00"
  }
]
```

前端建议：

- 会话列表按 `updatedAt` 倒序展示。如果后端未排序，前端可自行排序。
- 侧边栏展示 `title`，没有标题时展示 `New Chat` 或首条用户消息摘要。

### 5.3 查询单个会话

```http
GET /api/session/{sessionId}
```

其中 `sessionId` 是 `sessionCode`，不是数据库数字 ID。

示例：

```http
GET /api/session/d9a8c3f2-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

### 5.4 更新会话

```http
PUT /api/session/{sessionId}
Content-Type: application/json
```

请求体使用 `ChatSession` 结构：

```json
{
  "title": "新的会话标题",
  "modelCode": "deepseek-chat",
  "systemPrompt": "你是一个简洁的助手",
  "maxRounds": 10,
  "status": 1
}
```

注意：

- 后端会强制把路径中的 `{sessionId}` 设置到 `sessionCode`。
- 前端更新标题时，建议先获取当前会话对象，再只修改需要变更的字段后提交，避免把其他字段覆盖成空值。

### 5.5 删除会话

```http
DELETE /api/session/{sessionId}
```

成功响应：

```http
204 No Content
```

前端建议：

- 删除成功后从本地会话列表移除。
- 如果删除的是当前会话，切换到最近一个会话或新建会话页。

### 5.6 设置最大上下文轮数

```http
PUT /api/session/{sessionId}/max-rounds
Content-Type: application/json
```

请求：

```json
{
  "maxRounds": 10
}
```

响应：`SessionView`

前端建议：

- 输入范围建议限制为 `1-50`。
- 当前后端没有参数校验，前端需要避免传 `null`、负数或过大值。

## 6. 前端状态管理建议

建议前端至少维护这些状态：

```ts
type ChatSessionState = {
  currentSessionId: string | null;
  sessions: SessionView[];
  messagesBySession: Record<string, ChatMessage[]>;
  loading: boolean;
  streaming: boolean;
  error?: string;
};
```

推荐流程：

1. 页面加载时调用 `GET /api/sessions`。
2. 用户发送第一条消息时，如果没有 `currentSessionId`，调用 `/api/chat` 或 `/api/chat/stream` 时让后端自动创建会话。
3. 后端返回新 `sessionId` 后，更新 `currentSessionId` 并刷新会话列表。
4. 后续发送消息时带上 `sessionId`。
5. 流式输出时，前端本地增量更新 assistant 消息内容。

## 7. 错误处理约定

当前后端错误响应并未完全统一：

- `/api/chat` 失败时返回 HTTP 400，body 中包含 `{ "error": "..." }`
- 会话接口部分异常可能直接由 Spring 返回默认错误结构
- 模型管理接口当前缺失，不建议前端调用

前端建议统一封装：

```ts
async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.error || body?.message || `HTTP ${res.status}`);
  }
  return res.json();
}
```

## 8. 模型参数相关说明

后端当前已经将模型表中的运行参数接入 LLM 调用链：

- `max_output_tokens`
- `default_temperature`
- `default_top_p`
- `default_config`

`default_config` 当前支持的常用键：

```json
{
  "stop": ["\\nUser:"],
  "response_format": "json_object",
  "seed": 1234,
  "presence_penalty": 0.1,
  "frequency_penalty": 0.1
}
```

前端普通聊天页面通常不需要直接传这些参数。建议由后端模型管理页面或管理员配置模型默认参数。

## 9. 模型/渠道管理接口状态

当前 DTO 存在：

- `ModelRequestDTO`
- `AddModelRequestDTO`
- `ProviderDTO`
- `ModelProviderDTO`

但当前工作区中：

- `easychat-api/src/main/java/com/easychat/api/controller/ModelController.java` 缺失
- `easychat-core/src/main/java/com/easychat/core/service/model/*` 多个文件缺失

因此前端当前不要实现依赖这些接口的页面，包括：

- 模型创建/更新/删除
- 渠道创建/更新/删除
- 模型绑定渠道
- 路由优先级和权重维护

待后端恢复接口后，建议统一设计为：

```text
GET    /api/models
POST   /api/models
PUT    /api/models/{modelCode}
DELETE /api/models/{modelCode}

GET    /api/providers
POST   /api/providers
PUT    /api/providers/{providerCode}
DELETE /api/providers/{providerCode}
```

## 10. 前端页面建议

### 10.1 聊天主页面

建议功能：

- 左侧会话列表
- 中间消息流
- 底部输入框
- 模型选择器
- 工具/RAG 开关
- 流式输出开关
- 停止生成按钮

### 10.2 会话设置

建议功能：

- 编辑会话标题
- 编辑系统提示词
- 设置最大上下文轮数
- 删除会话

### 10.3 管理页面

当前暂缓，等待后端恢复模型/渠道管理接口。

## 11. 前端对接注意事项

1. `sessionId` 在接口中实际指 `sessionCode`，不是数据库数字 ID。
2. `/api/session` 创建会话请求字段当前是 `modelType`，但语义是模型编码。
3. `/api/chat` 如果不传 `sessionId`，后端会自动创建会话。
4. `/api/chat/stream` 是 POST SSE，不能直接用原生 `EventSource`。
5. 前端必须保证 `messages` 非空，否则后端会数组越界。
6. 当前后端没有登录鉴权，生产环境前需要补充认证机制。
7. 当前测试和配置仍依赖真实数据库，前端联调前应确认数据库 schema 已升级到最新。
8. 如果后端报 `Unknown column 'model_name'`，说明数据库 `model` 表还未执行新版 schema。

## 12. 最小联调清单

前端开始联调前确认：

- 后端启动成功，端口为 `8080`
- 数据库已执行最新 `schema.sql`
- `model` 表存在可用模型记录
- `provider` 表存在可用渠道记录
- `model_provider` 表存在模型和渠道绑定记录
- LLM API Key 有效
- 前端环境变量 `VITE_API_BASE_URL` 指向后端地址

最小聊天请求：

```json
{
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

请求地址：

```text
POST http://localhost:8080/api/chat
```

