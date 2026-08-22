# Java 智能 Agent 系统（Java AI Agent Assistant）

基于 **Spring Boot + 通义千问（Qwen）** 的企业级 AI 应用 —— 智能 Agent 系统。

本项目不是简单地调用大模型接口，而是构建一个符合企业 AI 应用开发模式的 Agent 应用，具备自主理解、上下文记忆、知识检索与工具调用能力，并为后续扩展 RAG、Tool Calling、MCP、多 Agent 协作提供基础。

---

## 一、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.2.x |
| 编程语言 | Java 17 |
| 大模型 | 阿里云通义千问（DashScope OpenAI 兼容接口） |
| 数据存储 | MySQL（长期记忆） |
| 缓存（后续） | Redis |
| 向量数据库（后续） | Milvus |
| Agent 框架（可选） | Spring AI Alibaba / LangChain4j |

## 二、系统架构

```
        用户
         |
  Web / API 接口
         |
  Spring Boot 服务
         |
      Agent 核心 (SimpleAgent)
         |
   -----------------
   |               |
Memory 模块      LLM 模块
短期/长期记忆    千问/Qwen
   |               |
 MySQL          （后续：RAG / Tool Calling / MCP）
```

## 三、项目结构

```
src/main/java/com/example/agent/
├── AgentApplication.java       # 启动类
├── config/
│   ├── QwenProperties.java     # 大模型配置
│   ├── AgentProperties.java    # Agent 行为配置
│   └── AppConfig.java          # RestTemplate / 异步支持
├── controller/
│   └── AgentController.java    # POST /agent/chat
├── agent/
│   └── SimpleAgent.java        # Agent 核心：Prompt 构造 + 流程编排
├── llm/
│   ├── QwenClient.java         # 大模型客户端
│   ├── QwenChatRequest.java
│   └── QwenChatResponse.java
├── memory/
│   ├── ShortMemory.java        # 短期记忆（会话上下文，内存实现）
│   └── LongMemoryService.java  # 长期记忆（用户画像，MySQL + LLM 抽取）
├── entity/UserMemory.java      # user_memory 表实体
├── repository/UserMemoryRepository.java
├── model/Message.java          # 消息结构 role/content
├── dto/                        # 请求 / 响应 DTO
├── exception/                  # 全局异常处理
├── knowledge/KnowledgeService.java    # 第三阶段：RAG 知识库（占位）
├── tool/ToolCallingService.java       # 第四阶段：Tool Calling（占位）
└── mcp/McpService.java                # 第五阶段：MCP 扩展（占位）
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

## 六、模块说明

| 模块 | 职责 | 对应需求 |
| --- | --- | --- |
| Controller | 接收用户请求，返回 Agent 结果 | 6.1 |
| Agent（SimpleAgent） | 管理流程、构造 Prompt、调度 LLM | 6.2 |
| LLM（QwenClient） | 调用大模型 | 6.3 |
| Memory | 短期记忆（会话）+ 长期记忆（画像） | 6.4 |
| Knowledge | 文档解析、向量化、检索 | 6.5（后续版本） |

**长期记忆抽取原理**：每轮对话结束后，异步调用大模型从用户消息中抽取长期稳定的个人事实（姓名、技能、方向等），以「类型-内容」键值对存入 `user_memory` 表；下次构造 Prompt 时把用户画像注入系统提示词。

## 七、开发路线图

| 阶段 | 目标 | 状态 |
| --- | --- | --- |
| 第一阶段 | 基础 Agent（Spring Boot + Qwen API + 聊天接口） | ✅ 已实现 |
| 第二阶段 | Memory 能力（短期记忆 + 长期记忆 + MySQL） | ✅ 已实现 |
| 第三阶段 | RAG 知识库（文档上传 + 向量检索） | ⏳ 占位 `knowledge/` |
| 第四阶段 | Tool Calling（Function Calling + 业务接口） | ⏳ 占位 `tool/` |
| 第五阶段 | MCP 扩展（MCP Server + 外部工具） | ⏳ 占位 `mcp/` |

## 八、后续优化方向

- 多 Agent 协作
- Agent 工作流编排
- Redis 缓存优化（短期记忆迁移）
- 权限管理与用户管理系统
- Web 前端界面
- Docker / 云服务器部署
