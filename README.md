# Java 智能 Agent 系统（Java AI Agent Assistant）

基于 **Spring Boot + 通义千问（Qwen）** 的企业级 AI 应用 —— 智能 Agent 系统。

本项目不是简单地调用大模型接口，而是构建一个符合企业 AI 应用开发模式的 Agent 应用，具备自主理解、上下文记忆、知识检索（RAG）、工具调用（Tool Calling）、MCP 扩展能力，并为后续扩展多 Agent 协作提供基础。

---

## 一、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.2.x |
| 编程语言 | Java 17 |
| 大模型 | 阿里云通义千问（DashScope OpenAI 兼容接口） |
| 数据存储 | MySQL（长期记忆） |
| 缓存（后续） | Redis |
| 向量检索 | Qwen Embedding + 内存向量库（Milvus 可替换） |
| Agent 框架（可选） | Spring AI Alibaba / LangChain4j |
| 前端 | HTML / CSS / JavaScript（静态页面，无需构建） |

## 二、系统架构

```
                       用户（浏览器前端 / curl）
                                  |
                     Web / API 接口层（REST + 静态页面）
                                  |
                         Spring Boot 服务
                                  |
                          Agent 核心（SimpleAgent）
                                  |
        +------------+------------+------------+-------------+
        |            |            |            |             |
      Memory       LLM       RAG 知识库   Tool Calling      MCP
   短期/长期记忆   通义千问   KnowledgeService FunctionCallingAgent  McpService
        |           Qwen      + 内存向量库   + ToolCallingService
      MySQL      Embedding

前端页面（static/index.html + css + js）由 Spring Boot 直接托管
```

## 三、项目结构

```
src/main/java/com/example/agent/
├── AgentApplication.java       # 启动类
├── config/
│   ├── QwenProperties.java     # 大模型 + Embedding 配置
│   ├── AgentProperties.java    # Agent 行为配置
│   └── AppConfig.java          # RestTemplate / 异步支持
├── controller/
│   ├── AgentController.java    # 对话 / 记忆接口
│   ├── KnowledgeController.java # RAG 知识库接口
│   ├── ToolController.java      # 工具调用接口
│   └── McpController.java       # MCP 接口
├── agent/
│   ├── SimpleAgent.java        # Agent 核心：Prompt 构造 + 流程编排
│   └── FunctionCallingAgent.java # Function Calling 循环
├── llm/
│   ├── QwenClient.java         # 大模型客户端（对话 / 工具 / Embedding）
│   ├── QwenChatRequest.java
│   ├── QwenChatResponse.java
│   ├── QwenEmbeddingResponse.java
│   └── ToolDefinition.java
├── memory/
│   ├── ShortMemory.java        # 短期记忆（会话上下文，内存实现）
│   └── LongMemoryService.java  # 长期记忆（用户画像，MySQL + LLM 抽取）
├── knowledge/KnowledgeService.java   # RAG：切分 + 向量化 + 检索
├── tool/ToolCallingService.java      # 工具注册与执行
├── mcp/McpService.java               # MCP 服务器（模拟）连接与调用
├── entity/UserMemory.java      # user_memory 表实体
├── repository/UserMemoryRepository.java
├── model/
│   ├── Message.java            # 消息结构（含 tool_calls）
│   └── ToolCall.java           # 工具调用指令
├── dto/                        # 请求 / 响应 DTO
└── exception/                  # 全局异常处理

src/main/resources/static/            # 前端页面（Spring Boot 直接托管）
├── index.html                        # 四视图界面（对话/知识库/工具/MCP）
├── css/style.css                     # 样式
└── js/app.js                         # 交互逻辑（fetch 调用后端接口）
```

## 四、快速开始

### 4.1 环境要求

- JDK 17+
- Maven 3.6+
- （正式运行长期记忆需要）MySQL 5.7+ / 8.0
- 阿里云 DashScope API Key（通义千问）

### 4.2 获取 API Key

1. 前往 [阿里云百炼（DashScope）](https://bailian.console.aliyun.com/) 开通通义千问服务；
2. 创建 API Key；
3. 通过环境变量注入（推荐）：

```bash
# Windows (PowerShell)
$env:QWEN_API_KEY="sk-xxxxxxxxxxxxxxxx"

# Linux / macOS
export QWEN_API_KEY="sk-xxxxxxxxxxxxxxxx"
```

> 也可直接修改 `src/main/resources/application.yml` 中的 `qwen.api-key`（注意不要提交到仓库）。

### 4.3 方式一：免安装 MySQL 快速演示（H2 内存库）

使用内置的 `local` profile，无需安装 MySQL，长期记忆数据仅存于内存（重启即丢失）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4.4 方式二：使用 MySQL

1. 初始化数据库（首次）：

```bash
mysql -uroot -proot < sql/init.sql
```

2. 按需修改 `application.yml` 中的数据库账号密码，然后启动：

```bash
mvn spring-boot:run
```

### 4.5 打包运行

```bash
mvn clean package
java -jar target/java-ai-agent-1.0.0.jar
```

## 五、接口示例

### 5.1 基础对话

```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"001","message":"什么是Spring Boot？"}'
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "Spring Boot 是基于 Spring 框架的快速开发框架……"
  }
}
```

### 5.2 短期记忆（上下文）

连续对话，Agent 能记住之前说过的话：

```bash
# 第一轮
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"userId":"001","message":"我叫王浩"}'
# 第二轮（同一 userId）
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"userId":"001","message":"我叫什么？"}'
# Agent 回复：你叫王浩。
```

### 5.3 长期记忆（用户画像）

```bash
# 用户陈述职业信息
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"userId":"001","message":"我是Java后端开发"}'

# 之后请求推荐，Agent 会结合画像个性化回答
curl -X POST http://localhost:8080/agent/chat -H "Content-Type: application/json" \
  -d '{"userId":"001","message":"推荐学习路线"}'
# Agent 回复：根据你的 Java 背景，建议学习 Spring AI 和 Agent 开发。
```

### 5.4 查询长期记忆（画像）

```bash
curl http://localhost:8080/agent/memory/001
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "key": "技能", "value": "Java" },
    { "key": "方向", "value": "后端开发" }
  ]
}
```

### 5.5 清空会话 / 清空记忆

```bash
# 只清空短期会话（保留长期画像）
curl -X DELETE http://localhost:8080/agent/session/001

# 清空全部记忆（短期 + 长期）
curl -X DELETE http://localhost:8080/agent/memory/001
```

### 5.6 前端页面

启动后直接访问 `http://localhost:8080/` 即可使用浏览器版界面，顶部导航提供四个视图：**对话**（含记忆面板、新对话、清空记忆）、**知识库**（文档上传 + 问答）、**工具调用**、**MCP**。

### 5.7 RAG 知识库

```bash
# 上传文档
curl -X POST http://localhost:8080/agent/knowledge/upload -H "Content-Type: application/json" \
  -d '{"name":"公司制度","content":"请假需要提前一天在 OA 系统提交申请……"}'

# 基于知识库提问（返回 answer + sources）
curl -X POST http://localhost:8080/agent/knowledge/ask -H "Content-Type: application/json" \
  -d '{"question":"请假流程是什么？"}'

# 列出文档
curl http://localhost:8080/agent/knowledge/documents
```

### 5.8 Tool Calling（Function Calling）

```bash
# 带工具调用的对话（返回 answer + steps）
curl -X POST http://localhost:8080/agent/tool/call -H "Content-Type: application/json" \
  -d '{"message":"帮我查一下订单 10086 的状态"}'

# 列出工具
curl http://localhost:8080/agent/tool/list
```

### 5.9 MCP

```bash
# 连接服务器
curl -X POST http://localhost:8080/agent/mcp/connect -H "Content-Type: application/json" \
  -d '{"name":"local-tools","endpoint":"http://localhost:9000"}'

# 列出工具
curl http://localhost:8080/agent/mcp/tools

# 调用工具
curl -X POST http://localhost:8080/agent/mcp/call -H "Content-Type: application/json" \
  -d '{"server":"local-tools","tool":"get_time","arguments":null}'
```

## 六、模块说明

| 模块 | 职责 | 对应需求 |
| --- | --- | --- |
| Controller | 接收用户请求，返回 Agent 结果 | 6.1 |
| Agent | SimpleAgent（流程编排）+ FunctionCallingAgent（工具循环） | 6.2 |
| LLM（QwenClient） | 调用大模型（对话 / 工具 / Embedding） | 6.3 |
| Memory | 短期记忆（会话）+ 长期记忆（画像） | 6.4 |
| Knowledge | 文档切分、向量化、相似度检索 | 6.5 |
| Tool Calling | 工具注册与执行、Function Calling 循环 | 6.6 |
| MCP | 服务器连接、工具发现与调用 | 6.7 |
| 前端 | 四视图界面（对话 / 知识库 / 工具调用 / MCP） | 6.8 |

**长期记忆抽取原理**：每轮对话结束后，异步调用大模型从用户消息中抽取长期稳定的个人事实（姓名、技能、方向等），以「类型-内容」键值对存入 `user_memory` 表；下次构造 Prompt 时把用户画像注入系统提示词。

**RAG / Tool Calling / MCP 说明**：三者均采用「轻量自包含」实现，**不新增任何 Maven 依赖**——RAG 复用 Qwen Embedding 做向量化 + 内存向量库检索；Tool Calling 复用 OpenAI 兼容的 `tools`/`tool_calls` 协议；MCP 用内存模型模拟服务器连接与工具调用。生产环境可平滑替换为 Milvus、Spring AI、官方 MCP SDK。

## 七、开发路线图

| 阶段 | 目标 | 状态 |
| --- | --- | --- |
| 第一阶段 | 基础 Agent（Spring Boot + Qwen API + 聊天接口） | ✅ 已实现 |
| 第二阶段 | Memory 能力（短期记忆 + 长期记忆 + MySQL） | ✅ 已实现 |
| 第三阶段 | RAG 知识库（文档上传 + 向量检索） | ✅ 已实现 `knowledge/` |
| 第四阶段 | Tool Calling（Function Calling + 业务接口） | ✅ 已实现 `tool/` + `FunctionCallingAgent` |
| 第五阶段 | MCP 扩展（MCP Server + 外部工具） | ✅ 已实现 `mcp/` |
| 附加 | Web 前端界面（对话 / 知识库 / 工具调用 / MCP 四视图） | ✅ 已实现 |

## 八、后续优化方向

- 多 Agent 协作
- Agent 工作流编排
- Redis 缓存优化（短期记忆迁移）
- 权限管理与用户管理系统
- 前端界面增强（流式输出、历史会话列表、用户登录）
- Docker / 云服务器部署
