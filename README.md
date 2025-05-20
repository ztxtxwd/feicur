# Feicur - 飞书文档智能助手

Feicur 是一个基于 Spring AI MCP (Model Context Protocol) 的智能助手，专为飞书文档协作设计。它通过轮询用户指定的飞书文档评论，自动接收用户指令，并利用强大的工具集来完成文档修改操作。

## 项目概述

Feicur 基于 Spring Boot 3.3.6 和 Spring AI 1.0.0-SNAPSHOT 构建，是一个命令行应用程序，展示了 MCP 服务器集成的强大功能。该应用程序：

- 通过轮询飞书文档评论接收用户指令
- 将用户指令作为 MCP 客户端的输入
- 连接到 MCP 服务器使用 STDIO 和/或 SSE (HttpClient-based) 传输
- 集成 Spring AI 的聊天功能
- 通过 MCP 服务器执行工具调用
- 自动修改飞书文档内容

## 工作原理

1. 应用程序启动并配置多个 MCP 客户端（每个提供的 STDIO 或 SSE 连接配置一个）
2. 它使用配置的 MCP 工具构建一个 ChatClient
3. 轮询指定的飞书文档评论，获取用户指令
4. 将用户指令发送给 AI 模型
5. 执行 AI 返回的工具调用，完成文档修改操作
6. 将操作结果反馈给用户

## 前提条件

- Java 17 或更高版本
- Maven 3.6+
- OpenAI API 密钥或 Anthropic API 密钥
- 飞书开放平台应用凭证

## 依赖项

项目使用以下主要依赖项：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-anthropic</artifactId>
    </dependency>
    <!-- 或使用 OpenAI 模型 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>
```

## 配置

### 应用程序属性

应用程序可以通过 `application.properties` 或 `application.yml` 进行配置：

#### 基本配置
```properties
# 应用程序配置
spring.application.name=mcp
spring.main.web-application-type=none

# AI 提供商配置
spring.ai.openai.api-key=${FEICUR_LLM_API_KEY}
spring.ai.openai.base-url=${FEICUR_LLM_BASE_URL}
spring.ai.openai.chat.options.model=${FEICUR_LLM_MODEL}
# 或使用 Anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}

# 启用 MCP 客户端工具回调自动配置
spring.ai.mcp.client.toolcallback.enabled=true
```

#### STDIO 传输属性

通过外部 JSON 文件配置 STDIO 连接：

```properties
spring.ai.mcp.client.stdio.servers-configuration=classpath:/mcp-servers-config.json
```

示例 `mcp-servers-config.json`：

```json
{
  "mcpServers": {
    "feishu": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://localhost:8788/sse"
      ],
      "env": {
      }
    }
  }
}
```

## 运行应用程序

1. 设置所需的环境变量：
   ```bash
   # 对于 OpenAI
   export FEICUR_LLM_API_KEY=your-openai-api-key
   export FEICUR_LLM_BASE_URL=https://api.openai.com/v1
   export FEICUR_LLM_MODEL=gpt-4o

   # 或对于 Anthropic
   export ANTHROPIC_API_KEY=your-anthropic-api-key
   ```

2. 构建应用程序：
   ```bash
   ./mvnw clean install
   ```

3. 运行应用程序：
   ```bash
   # 使用默认配置运行
   java -jar target/mcp-starter-default-client-0.0.1-SNAPSHOT.jar

   # 或指定自定义输入
   java -Dai.user.input='查询飞书文档信息' -jar target/mcp-starter-default-client-0.0.1-SNAPSHOT.jar
   ```

## 其他资源

- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [MCP 客户端启动器](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
- [模型上下文协议规范](https://modelcontextprotocol.github.io/specification/)
- [Spring Boot 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [飞书开放平台文档](https://open.feishu.cn/document/home/index)
