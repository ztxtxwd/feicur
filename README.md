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

## Native Image 支持

Feicur 支持使用 GraalVM Native Image 技术构建原生可执行文件，这可以显著提高启动速度并减少内存占用。

### 使用 Buildpacks 构建 Native Image 容器

使用 Spring Boot 的 Buildpacks 支持可以生成包含原生可执行文件的轻量级容器：

```bash
# 使用 Maven
./mvnw -Pnative spring-boot:build-image

# 运行容器
docker run --rm -p 8080:8080 \
  -e FEICUR_LLM_API_KEY=your-openai-api-key \
  -e FEICUR_LLM_BASE_URL=https://api.openai.com/v1 \
  -e FEICUR_LLM_MODEL=gpt-4o \
  docker.io/library/mcp-starter-default-client:0.0.1-SNAPSHOT
```

### 使用 Native Build Tools 直接构建 Native 可执行文件

#### 前提条件

- GraalVM 或 Liberica Native Image Kit (NIK) 22.3+
- 对于 Linux/macOS，推荐使用 SDKMAN! 安装：
  ```bash
  sdk install java 22.3.r17-nik
  sdk use java 22.3.r17-nik
  ```

#### 构建和运行

```bash
# 使用 Maven 构建 Native 可执行文件
./mvnw -Pnative native:compile

# 运行 Native 可执行文件
FEICUR_LLM_API_KEY=your-openai-api-key \
FEICUR_LLM_BASE_URL=https://api.openai.com/v1 \
FEICUR_LLM_MODEL=gpt-4o \
./target/mcp-starter-default-client
```

### 性能优势

Native Image 相比传统 JVM 应用程序具有以下优势：

- 显著更快的启动时间（通常为毫秒级而非秒级）
- 更低的内存占用
- 更小的部署体积
- 无需安装 JVM 即可运行

## 其他资源

- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [MCP 客户端启动器](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
- [模型上下文协议规范](https://modelcontextprotocol.github.io/specification/)
- [Spring Boot 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Boot GraalVM Native Image 支持](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [飞书开放平台文档](https://open.feishu.cn/document/home/index)
