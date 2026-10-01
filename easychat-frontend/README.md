# EasyChat 前端

Vue 3 + TypeScript + Pinia。接口契约以 [EasyChat前端对接文档.md](./EasyChat前端对接文档.md)（2026-09-27）为准。

2026-09-28 已接入运行中的后端进行 HTTP 联调。已确认的后端阻塞、复现请求和清理事项见项目根目录 [EasyChat后端待修复缺陷.md](./EasyChat后端待修复缺陷.md)。真实成功聊天仍受模型配置及后端故障阻塞；下文的浏览器功能验证指隔离模拟测试，不是完整真实服务验收。

## 启动与验证

```sh
npm install
npm run dev
npm test
npm run build
```

默认通过 Vite 将同源的 `/api`、`/model`、`/es` 代理到 `http://localhost:8080`。修改目标地址请编辑 `vite.config.ts`。不要用跨域直连绕过代理：后端认证过滤器可能拒绝 OPTIONS。生产环境也应反向代理这三个前缀并保留 Authorization；SSE 关闭代理缓冲，配置足够的读取超时。Vite preview 不包含后端反向代理。

## 部署配置

可以在 `.env.development.local` / `.env.production.local` 中覆盖配置，修改后重启开发服务或重新构建。

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| VITE_API_BASE_URL | 空 | 使用同源路径；可设同源网关前缀 |
| VITE_AUTH_ENABLED | true | false 仅用于后端同样关闭认证的单用户演示 |
| VITE_KNOWLEDGE_ENABLED | false | 部署方确认 ES/知识库配置后启用知识库和 RAG，也显示管理员 ES 调试 |
| VITE_MEDIA_ENABLED | false | 部署方确认转写/FFmpeg 配置后启用音视频转写 |
| VITE_LOGIN_URL | 空 | 现有身份系统的登录入口，可选 |

不会向普通用户调用运维接口探测能力。所有 VITE_ 变量均进入前端构建，不能放供应商密钥或 Bearer token。

## 认证接入

EasyChat 没有登录、刷新 token、退出登录或用户信息接口。宿主身份系统可以在应用模块加载前注入：

```js
window.easychatIdentity = { token: '<已有 Bearer token>', roles: ['user'] }
```

身份建立、刷新、切换或退出后：

```js
window.dispatchEvent(new CustomEvent('easychat:identity', {
  detail: { token: '<新 token>', roles: ['admin'] }
}))
// 退出或失效：
window.dispatchEvent(new CustomEvent('easychat:identity', { detail: null }))
```

角色来自已有身份系统。前端角色只控制入口显示，最终权限由后端校验。凭证只保留在内存，不写入 URL、localStorage 或 sessionStorage。身份切换清空会话与模型缓存。401 清除当前凭证并显示重新登录提示，同时触发 `easychat:unauthorized` 事件，宿主可接回登录流程；不自动重发聊天。

独立调试可在页面“接入登录”粘贴已有 token，此方式默认普通用户权限。管理员需通过宿主身份接入传递角色。清除页面凭证仅影响本页，不声称调用了不存在的后端退出接口。

## 已接入的功能

- 普通用户模型目录：按 modelCode 发送，supportVision 按数字 0/1 判断。
- 会话创建、列表、标题/系统提示词/历史轮数/开关设置、删除；路径一律使用 sessionCode。
- 独立消息历史读取。每次只发送新增 user 消息；生成完成、失败、断流或取消后用后端历史替换乐观记录，避免重复。
- 历史读取失败会持续显示错误并保留刷新入口，确认历史恢复前禁止发送。模型目录读取失败与空目录分别展示，支持手动刷新。
- 同步与 POST SSE 聊天。支持 meta、message、action、observation、sources、warning、error、finish；thought 预留解析但不依赖或构造推理过程。
- SSE 支持 UTF-8 分片、LF/CRLF、多行 data，原样保留文本空白；仅 finish 是成功终态，error 立即结束，无终态 EOF 提示中断。通过 AbortController 停止，同步请求同样可以停止本地等待；从不自动重发 POST。
- 保留未落库的部分输出并明确标识；历史状态为运行中时可手动刷新。工具事件与引用仅在当前页面最近一轮临时保留，切换会话/下一轮/刷新后不承诺恢复。
- 引用标题/页码、降级提示、渠道、结束原因、缺失 usage 展示。没有文档下载或预览接口，不伪造跳转。
- 一张 PNG/JPEG/WebP 内嵌图片或 http(s) URL，支持纯图片。编码字符串不超过 2,000,000 字符；历史附件从 paramJson 解码。含图片历史能否切换模型最终由后端判断。
- 知识库上传、按数据集/全部列出、状态轮询、处理失败重试、重建、删除失败重新删除。文件上限 20 MiB；上传返回 docCode 后读取 status，不把上传成功当成可检索。可见时每 3 秒轮询待处理记录，网络错误及 408/429/5xx 指数退避至 30 秒，参数/鉴权错误停止自动轮询，离开页面取消请求。
- 音视频转写返回文本填入输入框，由用户确认发送；没有 60 秒客户端总超时。
- 管理员模型/渠道/路由增删改查、运行状态和 ES 调试。数值/JSON 配置校验，defaultConfig 发送 JSON 对象字符串，编辑留空 API Key 省略；供应商密钥不持久化。
- Markdown 转义原始 HTML，链接只允许 http/https/mailto；消息与工具内容不作为脚本执行。

后端时间没有时区，侧栏保留后端日期时间文字，不追加 Z，也不根据浏览器时区计算“几分钟前”。数据库数字 ID 不用于路径或前端关联键。

## 验证范围

`npm test` 使用 Node 测试运行器及本地构建依赖，验证 SSE 分片/终态/取消、HTTP 与 Result、鉴权、会话历史替换及身份隔离、模型目录、FormData、知识库/转写、参数校验和 Markdown 输出。

可启动隔离的浏览器测试服务：

```sh
node tests/mock-server.mjs
```

此服务占用本地 5173 端口，注入**虚拟管理员**，所有 API 由内存模拟接口拦截，不连接真实后端。仅用于开发测试，绝不能用于部署。聊天输入包含“停止”时延迟结束，包含“断流”或“错误”时模拟对应失败；还提供同步聊天、会话设置、知识库列表及管理员只读测试数据。未实现的模拟管理写入/文件处理返回 404，不代表真实服务行为。

已在浏览器验证模型目录、新建会话、普通/工具流式聊天、引用展示、主动停止、异常断流、同步聊天、关闭会话禁发、知识库列表与管理员编辑表单。真实身份服务、模型供应商、数据库、ES、OCR、S3、FFmpeg/转写仍需部署环境联调。
