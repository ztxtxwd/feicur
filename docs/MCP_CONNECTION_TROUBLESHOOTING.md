# MCP连接问题排查指南

## 问题描述

当MCP服务器重启时，客户端可能会遇到连接超时错误，典型的错误信息如下：

```
java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 20000ms in 'source(MonoDeferContextual)'
```

### 潜在的误报问题

**原问题**：断路器降级方法返回空列表会导致评论变更事件检测器误报大量删除事件。

**解决方案**：修改fallback方法返回`null`而不是空列表，并在事件检测前增加连接健康检查。

## 原因分析

1. **连接失效**：MCP服务器重启后，现有的客户端连接变得无效
2. **超时机制**：客户端仍尝试使用旧连接，导致20秒超时
3. **缺乏重连机制**：原有代码没有自动重连和连接健康检查
4. **误报问题**：断路器返回空列表会被误认为所有评论都被删除

## 解决方案

### 1. 代码级改进

我们已经在代码中添加了以下改进：

#### A. 连接健康检查
- 添加了 `checkMcpConnectionHealth()` 方法
- 在每次API调用前检查连接状态
- 在`DocWatcher`中增加MCP连接健康检查
- 避免在连接不健康时进行无意义的调用

#### B. 智能重试机制
- 实现了 `getToolCallbacksWithRetry()` 方法
- 支持指数退避的重试策略
- 最多重试3次，每次延迟递增

#### C. 改进的错误处理
- 增强的fallback方法，能识别不同类型的异常
- **关键修复**：fallback方法返回`null`而不是空列表
- 详细的错误日志，帮助快速定位问题
- 针对超时异常的特殊处理

#### D. 连接状态监控
- 新增 `McpConnectionMonitor` 组件
- 每30秒检查连接健康状态
- 每5分钟检查可用工具列表
- 提供连接状态的实时监控

#### E. 事件检测保护机制
- **防误报机制**：当获取评论返回`null`时，跳过事件检测
- **连接状态检查**：在轮询前检查MCP连接健康状态
- **快照保护**：只在成功获取到数据时更新快照

### 2. 配置优化

#### 断路器配置
```properties
# 降低断路器触发阈值，更快检测到问题
resilience4j.circuitbreaker.instances.feishu-api.minimum-number-of-calls=3
resilience4j.circuitbreaker.instances.feishu-api.permitted-number-of-calls-in-half-open-state=3

# 设置慢调用检测（15秒）
resilience4j.circuitbreaker.instances.feishu-api.slow-call-duration-threshold=15s
resilience4j.circuitbreaker.instances.feishu-api.slow-call-rate-threshold=60
```

#### 重试配置
```properties
spring.retry.maxAttempts=3
spring.retry.backoff.delay=1000
spring.retry.backoff.multiplier=2
```

## 操作建议

### 当MCP服务器重启时

1. **观察日志**：
   - 查看应用日志中的MCP连接状态信息
   - 关注 "MCP服务器连接超时" 的错误信息
   - 注意 "跳过本次事件检测" 的日志，表明防误报机制生效

2. **等待自动恢复**：
   - 断路器会在30秒后尝试半开状态
   - 连接监控会每30秒检查一次健康状态
   - 通常在1-2分钟内会自动恢复
   - **不会产生误报的删除事件**

3. **手动干预**（如果自动恢复失败）：
   - 重启应用程序以重新建立MCP连接
   - 检查MCP服务器是否正常运行
   - 确认网络连接没有问题

### 预防措施

1. **监控MCP服务器状态**：
   - 设置MCP服务器的健康检查
   - 监控MCP服务器的CPU和内存使用情况

2. **应用程序监控**：
   - 监控应用日志中的连接状态信息
   - 设置告警，当连续失败超过5次时发送通知

3. **定期维护**：
   - 定期重启MCP服务器以避免内存泄漏
   - 在重启MCP服务器前，先重启客户端应用

## 日志说明

### 正常情况下的日志
```
INFO  - MCP连接正常，可用工具数量: 15，工具列表: [spring_ai_mcp_client_feishu_docxV1DocumentBlockList, ...]
DEBUG - Found matching tool: spring_ai_mcp_client_feishu_drive_comment_list
```

### 连接问题的日志
```
WARN  - MCP连接检查失败: Connection check returned false。这可能是临时问题。
ERROR - MCP服务器连接超时，token: abc123。可能原因：MCP服务器已重启或网络连接问题
ERROR - 建议操作：1. 检查MCP服务器状态 2. 重启应用程序以重新建立连接
WARN  - 启用断路器降级模式，token: abc123，错误类型: Timeout，返回null以避免误报事件
```

### 防误报机制生效的日志
```
DEBUG - MCP连接不健康，跳过本次评论检测，token: abc123
WARN  - 获取评论返回null，可能是MCP连接问题，跳过本次事件检测，token: abc123
```

### 恢复后的日志
```
INFO  - MCP连接已恢复正常
DEBUG - Successfully retrieved 15 tool callbacks
INFO  - Detected 0 comment events for document: abc123 (正常，没有误报删除事件)
```

## 技术细节

### 超时层次
1. **MCP客户端超时**：20秒（框架设置）
2. **慢调用检测**：15秒（断路器配置）
3. **重试间隔**：1秒、2秒、4秒（指数退避）
4. **健康检查间隔**：30秒

### 断路器状态
- **关闭**：正常运行状态
- **开启**：检测到问题，直接返回fallback结果
- **半开**：测试性恢复，允许少量请求通过

### 防误报机制
1. **Fallback返回null**：表示获取失败，而不是真的没有数据
2. **连接健康检查**：在轮询前检查MCP连接状态
3. **null值处理**：当获取到null时跳过事件检测
4. **快照保护**：只在成功获取数据时更新快照

这个解决方案确保了在MCP服务器重启时，应用程序能够：
1. 快速检测到连接问题
2. 避免无意义的超时等待
3. 自动尝试恢复连接
4. **防止误报大量删除事件**
5. 提供清晰的错误信息和操作建议 