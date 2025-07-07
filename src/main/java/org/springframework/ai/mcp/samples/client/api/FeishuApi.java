/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.mcp.samples.client.api;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.model.RawComment;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Instant;
import java.util.*;

/**
 * 飞书API调用封装
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuApi {
    
    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String,ToolCallback> toolCallbackMap = new HashMap<>();

    /**
     * 获取文档所有块
     * @param token 文档token
     * @return 文档块列表
     */
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @CircuitBreaker(name = "feishu-api", fallbackMethod = "getDocBlockFallback")
    public List<JsonNode> 获取文档所有块(String token) {
        log.info("Fetching doc blocks for doc: {}", token);
        
        // 先检查MCP连接健康状态
        if (!checkMcpConnectionHealth()) {
            log.warn("MCP connection is not healthy, skipping doc blocks fetch for token: {}", token);
            return Collections.emptyList();
        }
        
        try {
            // 查找飞书文档块工具
            ToolCallback feishuTool = findFeishuToolCallback("spring_ai_mcp_client_feishu_docx_block_tree_get");
            if (feishuTool == null) {
                log.warn("No mcp_feishu_docx_block_tree_get tool found, returning empty list");
                return Collections.emptyList();
            }
            
            // 构建请求参数
            var request = Map.of(
                "path", Map.of("document_id", token),
                "params", Map.of(
                    "page_size", 500,
                    "user_id_type", "open_id",
                    "document_revision_id", -1  // -1表示最新版本
                ),
                "useUAT", true
            );
            
            var response = feishuTool.call(objectMapper.writeValueAsString(request));
            log.debug("MCP tool response: {}", response);
            
            return parseDocBlocksResponse(response);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request for doc: {}", token, e);
            throw new RuntimeException("JSON processing error", e);
        } catch (Exception e) {
            log.error("Failed to fetch doc blocks for doc: {}", token, e);
            throw e;
        }
    }
    
    /**
     * 获取文档评论列表
     */
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @CircuitBreaker(name = "feishu-api", fallbackMethod = "listCommentsFallback")
    public List<RawComment> listComments(String token) {
        log.debug("Fetching comments for doc: {}", token);
        
        // 先检查MCP连接健康状态
        // if (!checkMcpConnectionHealth()) {
        //     log.warn("MCP connection is not healthy, skipping comments fetch for token: {}", token);
        //     return Collections.emptyList();
        // }
        
        try {
            // 查找正确的飞书评论工具
            ToolCallback feishuTool = findFeishuToolCallback("spring_ai_mcp_client_feishu_drive_comment_list");
            if (feishuTool == null) {
                log.warn("No mcp_feishu_drive_comment_list tool found, returning empty list");
                return Collections.emptyList();
            }
            
            // 构建正确的请求参数结构
            var request = Map.of(
                "file_token",token,
                "file_type","docx"
            );
            var response = feishuTool.call(objectMapper.writeValueAsString(request));
            
            log.debug("MCP tool response: {}", response);
            return parseCommentsResponse(response);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request for doc: {}", token, e);
            throw new RuntimeException("JSON processing error", e);
        } catch (Exception e) {
            log.error("Failed to fetch comments for doc: {}", token, e);
            throw e;
        }
    }
    
    /**
     * 断路器降级方法
     */
    public List<RawComment> listCommentsFallback(String token, Exception ex) {
        String errorMessage = "Unknown error";
        String errorType = "Generic";
        
        if (ex != null) {
            errorMessage = ex.getMessage();
            Throwable cause = ex.getCause();
            
            // 检查是否是超时异常
            if (cause instanceof java.util.concurrent.TimeoutException) {
                errorType = "Timeout";
                log.error("MCP服务器连接超时，token: {}。可能原因：MCP服务器已重启或网络连接问题", token);
                log.error("建议操作：1. 检查MCP服务器状态 2. 重启应用程序以重新建立连接");
            } else if (ex.getClass().getSimpleName().contains("ReactiveException")) {
                errorType = "Reactive";
                log.error("Reactive stream异常，token: {}。错误信息: {}", token, errorMessage);
            } else {
                log.error("获取评论时发生异常，token: {}。错误类型: {}, 错误信息: {}", 
                    token, ex.getClass().getSimpleName(), errorMessage);
            }
        }
        
        log.warn("启用断路器降级模式，token: {}，错误类型: {}，返回null以避免误报事件", token, errorType);
        // 返回null而不是空列表，表示获取失败而不是真的没有评论
        return null;
    }
    
    /**
     * 获取文档所有块的断路器降级方法
     */
    public List<JsonNode> getDocBlockFallback(String token, Exception ex) {
        String errorMessage = "Unknown error";
        String errorType = "Generic";
        
        if (ex != null) {
            errorMessage = ex.getMessage();
            Throwable cause = ex.getCause();
            
            // 检查是否是超时异常
            if (cause instanceof java.util.concurrent.TimeoutException) {
                errorType = "Timeout";
                log.error("MCP服务器连接超时，获取文档块失败，token: {}。可能原因：MCP服务器已重启或网络连接问题", token);
                log.error("建议操作：1. 检查MCP服务器状态 2. 重启应用程序以重新建立连接");
            } else if (ex.getClass().getSimpleName().contains("ReactiveException")) {
                errorType = "Reactive";
                log.error("Reactive stream异常，获取文档块失败，token: {}。错误信息: {}", token, errorMessage);
            } else {
                log.error("获取文档块时发生异常，token: {}。错误类型: {}, 错误信息: {}", 
                    token, ex.getClass().getSimpleName(), errorMessage);
            }
        }
        
        log.warn("启用断路器降级模式，获取文档块，token: {}，错误类型: {}，返回null以避免误报事件", token, errorType);
        // 返回null而不是空列表，表示获取失败
        return null;
    }
    
    /**
     * 查找飞书评论工具回调
     */
    public ToolCallback findFeishuToolCallback(String toolName) {
        if (toolCallbackMap.containsKey(toolName)) {
            return toolCallbackMap.get(toolName);
        }
        try {
            log.debug("Attempting to find tool: {}", toolName);
            ToolCallback[] callbacks = getToolCallbacksWithRetry();
            
            if (callbacks == null || callbacks.length == 0) {
                log.warn("No tool callbacks available, possibly due to MCP server connection issue");
                return null;
            }
            
            for (ToolCallback callback : callbacks) {
                String callbackToolName = callback.getToolDefinition().name();
                log.debug("Checking callback tool: {}", callbackToolName);
                if (callbackToolName.equals(toolName)) {
                    log.debug("Found matching tool: {}", toolName);
                    toolCallbackMap.put(toolName, callback);
                    return callback;
                }
            }
            
            log.warn("Tool not found: {}. Available tools: {}", toolName, 
                Arrays.stream(callbacks)
                    .map(cb -> cb.getToolDefinition().name())
                    .collect(java.util.stream.Collectors.toList()));
            return null;
            
        } catch (Exception e) {
            log.error("Failed to find tool callback for: {}. Error: {}", toolName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 带重试机制的获取工具回调方法
     */
    private ToolCallback[] getToolCallbacksWithRetry() {
        int maxRetries = 3;
        int retryDelay = 1000; // 1秒
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Attempting to get tool callbacks, attempt: {}/{}", attempt, maxRetries);
                ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
                log.debug("Successfully retrieved {} tool callbacks", callbacks != null ? callbacks.length : 0);
                return callbacks;
                
            } catch (Exception e) {
                log.warn("Failed to get tool callbacks on attempt {}/{}: {}", 
                    attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        log.debug("Waiting {}ms before retry...", retryDelay);
                        Thread.sleep(retryDelay);
                        retryDelay *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting for retry");
                        break;
                    }
                } else {
                    log.error("All attempts to get tool callbacks failed after {} retries", maxRetries);
                    // 在最后一次尝试失败时，尝试检查是否是超时异常
                    if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                        log.error("MCP server appears to be unresponsive. Please check MCP server status and restart if necessary.");
                    }
                }
            }
        }
        
        return new ToolCallback[0]; // 返回空数组而不是null
    }
    
    /**
     * 解析文档块响应
     */
    private List<JsonNode> parseDocBlocksResponse(String response) {
        try {
            if (response == null || response.trim().isEmpty()) {
                log.warn("Received empty response from MCP tool");
                return Collections.emptyList();
            }
            
            log.debug("Raw MCP response: {}", response);
            
            // 将响应转换为JSON节点进行解析
            JsonNode jsonNode = objectMapper.readTree(response);
            log.debug("Parsed JSON response: {}", jsonNode);
            
            // MCP工具返回的格式是数组，需要先提取出实际的响应内容
            final JsonNode actualResponse;
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                JsonNode firstElement = jsonNode.get(0);
                if (firstElement.has("text")) {
                    String textContent = firstElement.get("text").asText();
                    log.debug("Extracted text content: {}", textContent);
                    
                    // 解析 "Success: {...}" 格式
                    if (textContent.startsWith("Success: ")) {
                        String actualJsonString = textContent.substring("Success: ".length());
                        actualResponse = objectMapper.readTree(actualJsonString);
                        log.debug("Parsed actual response: {}", actualResponse);
                    } else {
                        actualResponse = null;
                    }
                } else {
                    actualResponse = null;
                }
            } else {
                actualResponse = null;
            }
            
            if (actualResponse == null) {
                log.warn("Could not extract actual response from MCP format");
                return Collections.emptyList();
            }
            
            // 解析文档块
            List<JsonNode> blocks = new ArrayList<>();
            
            if (actualResponse.has("items")) {
                JsonNode itemsNode = actualResponse.get("items");
                log.debug("Found items node with {} blocks", itemsNode.size());
                
                if (itemsNode.isArray()) {
                    for (JsonNode blockNode : itemsNode) {
                        blocks.add(blockNode);
                    }
                }
            }
            
            log.debug("Parsed {} blocks from response", blocks.size());
            return blocks;
            
        } catch (Exception e) {
            log.error("Failed to parse doc blocks response: {}", response, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析MCP响应为RawComment列表
     */
    private List<RawComment> parseCommentsResponse(String response) {
        try {
            if (response == null || response.trim().isEmpty()) {
                log.warn("Received empty response from MCP tool");
                return Collections.emptyList();
            }
            
            // 先打印完整的响应内容进行调试
            log.debug("Raw MCP response: {}", response);
            
            // 将响应转换为JSON节点进行解析
            JsonNode jsonNode = objectMapper.readTree(response);
            log.debug("Parsed JSON response: {}", jsonNode);
            
            // MCP工具返回的格式是数组，需要先提取出实际的响应内容
            final JsonNode actualResponse;
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                JsonNode firstElement = jsonNode.get(0);
                if (firstElement.has("text")) {
                    String textContent = firstElement.get("text").asText();
                    log.debug("Extracted text content: {}", textContent);
                    
                    // 解析 "Success: {...}" 格式
                    actualResponse = objectMapper.readTree(textContent);
                    log.debug("Parsed actual response: {}", actualResponse);
                } else {
                    actualResponse = null;
                }
            } else {
                actualResponse = null;
            }
            
            if (actualResponse == null) {
                log.warn("Could not extract actual response from MCP format");
                return Collections.emptyList();
            }
            
            // 打印响应的所有顶级字段
            actualResponse.fieldNames().forEachRemaining(fieldName -> {
                log.debug("Response field: {} = {}", fieldName, actualResponse.get(fieldName));
            });
            
            // 根据飞书API的实际响应结构进行解析
            List<RawComment> comments = new ArrayList<>();
            
            // 飞书API返回的结构有 items 字段
            if (actualResponse.has("items")) {
                JsonNode itemsNode = actualResponse.get("items");
                log.debug("Found items node: {}", itemsNode);
                if (itemsNode.isArray()) {
                    log.debug("Items is array with {} elements", itemsNode.size());
                    for (JsonNode commentNode : itemsNode) {
                        log.debug("Processing comment node: {}", commentNode);
                        RawComment comment = parseCommentNode(commentNode);
                        if (comment != null) {
                            comments.add(comment);
                        }
                    }
                } else {
                    log.warn("Items node is not an array: {}", itemsNode);
                }
            } else {
                log.warn("No 'items' field found in response. Available fields: {}", 
                    actualResponse.fieldNames());
                // 尝试检查其他可能的字段名
                if (actualResponse.has("data")) {
                    log.debug("Found 'data' field: {}", actualResponse.get("data"));
                    JsonNode dataNode = actualResponse.get("data");
                    if (dataNode.has("items")) {
                        log.debug("Found items in data: {}", dataNode.get("items"));
                    }
                }
            }
            
            log.debug("Parsed {} comments from response", comments.size());
            return comments;
            
        } catch (Exception e) {
            log.error("Failed to parse comments response: {}", response, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据块类型数字返回可读名称
     */
    private String getBlockTypeName(Integer blockType) {
        if (blockType == null) return "未知";
        return switch (blockType) {
            case 1 -> "页面(Page)";
            case 2 -> "文本(Text)";
            case 31 -> "表格(Table)";
            case 32 -> "表格单元格(TableCell)";
            case 40 -> "插件(AddOns)";
            default -> "类型" + blockType;
        };
    }
    
    /**
     * 从文本元素中提取预览文本
     */
    private String extractTextPreview(Map<String, Object> textInfo) {
        try {
            List<Map<String, Object>> elements = (List<Map<String, Object>>) textInfo.get("elements");
            if (elements == null || elements.isEmpty()) {
                return null;
            }
            
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> element : elements) {
                if (element.containsKey("text_run")) {
                    Map<String, Object> textRun = (Map<String, Object>) element.get("text_run");
                    if (textRun != null && textRun.containsKey("content")) {
                        text.append(textRun.get("content"));
                    }
                }
            }
            return text.toString();
        } catch (Exception e) {
            log.debug("提取文本预览失败", e);
            return null;
        }
    }
    
    /**
     * 解析单个评论节点
     */
    private RawComment parseCommentNode(JsonNode commentNode) {
        try {
            RawComment comment = new RawComment();
            
            // 基础字段
            comment.setCommentId(getStringValue(commentNode, "comment_id"));
            comment.setAuthorId(getStringValue(commentNode, "user_id"));
            comment.setPosition(getStringValue(commentNode, "quote")); // 使用 quote 作为位置信息
            
            // 从 reply_list 中提取评论内容
            String content = extractCommentContent(commentNode);
            comment.setContent(content);
            
            // 时间字段解析 - 飞书返回的是 Unix 时间戳（秒）
            comment.setCreateTime(parseUnixTimestamp(commentNode, "create_time"));
            comment.setUpdateTime(parseUnixTimestamp(commentNode, "update_time"));
            
            // 布尔字段 - 飞书使用 is_solved
            comment.setIsResolved(getBooleanValue(commentNode, "is_solved"));
            
            return comment;
            
        } catch (Exception e) {
            log.warn("Failed to parse comment node: {}", commentNode, e);
            return null;
        }
    }
    
    /**
     * 从 reply_list 中提取评论内容
     */
    private String extractCommentContent(JsonNode commentNode) {
        JsonNode replyList = commentNode.get("reply_list");
        if (replyList != null && replyList.has("replies")) {
            JsonNode replies = replyList.get("replies");
            if (replies.isArray() && replies.size() > 0) {
                JsonNode firstReply = replies.get(0);
                JsonNode content = firstReply.get("content");
                if (content != null && content.has("elements")) {
                    JsonNode elements = content.get("elements");
                    if (elements.isArray() && elements.size() > 0) {
                        JsonNode firstElement = elements.get(0);
                        if (firstElement.has("text_run")) {
                            JsonNode textRun = firstElement.get("text_run");
                            if (textRun.has("text")) {
                                return textRun.get("text").asText();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 获取字符串值，支持多个可能的字段名
     */
    private String getStringValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                return field.asText();
            }
        }
        return null;
    }
    
    /**
     * 获取布尔值
     */
    private Boolean getBooleanValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                if (field.isBoolean()) {
                    return field.asBoolean();
                } else if (field.isTextual()) {
                    String text = field.asText().toLowerCase();
                    return "true".equals(text) || "resolved".equals(text) || "1".equals(text);
                }
            }
        }
        return false;
    }
    
    /**
     * 解析 Unix 时间戳字段
     */
    private Instant parseUnixTimestamp(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                try {
                    long timestamp = field.asLong();
                    if (timestamp > 0) {
                        return Instant.ofEpochSecond(timestamp);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse timestamp field {}: {}", fieldName, field.asText());
                }
            }
        }
        return null;
    }
    
    /**
     * 解析时间字段（保留原方法以兼容其他可能的时间格式）
     */
    private Instant parseTimeField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && !field.isNull()) {
                try {
                    String timeStr = field.asText();
                    if (timeStr != null && !timeStr.isEmpty()) {
                        // 尝试不同的时间格式
                        return Instant.parse(timeStr);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse time field {}: {}", fieldName, field.asText());
                }
            }
        }
        return null;
    }

    /**
     * 检查MCP连接健康状态
     */
    public boolean checkMcpConnectionHealth() {
        try {
            log.debug("Checking MCP connection health...");
            ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
            boolean isHealthy = callbacks != null && callbacks.length > 0;
            log.debug("MCP connection health check result: {} (found {} tools)", 
                isHealthy ? "healthy" : "unhealthy", callbacks != null ? callbacks.length : 0);
            return isHealthy;
        } catch (Exception e) {
            log.warn("MCP connection health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取可用的MCP工具列表
     */
    public List<String> getAvailableTools() {
        try {
            ToolCallback[] callbacks = getToolCallbacksWithRetry();
            return Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get available tools: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 更新文档块的内容
     * @param documentId 文档ID
     * @param blockId 块ID
     * @param updateData 更新数据
     * @param documentRevisionId 文档版本ID，-1表示最新版本
     * @return 更新结果
     */
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @CircuitBreaker(name = "feishu-api", fallbackMethod = "updateBlockContentFallback")
    public boolean 更新文档块的内容(String documentId, String blockId, JsonNode updateData, Integer documentRevisionId) {
        log.info("Updating block content for doc: {}, block: {}", documentId, blockId);
        
        // 先检查MCP连接健康状态
        if (!checkMcpConnectionHealth()) {
            log.warn("MCP connection is not healthy, skipping block update for doc: {}, block: {}", documentId, blockId);
            return false;
        }
        
        try {
            // 查找飞书文档块更新工具
            ToolCallback feishuTool = findFeishuToolCallback("spring_ai_mcp_client_feishu_docxV1DocumentBlockPatch");
            if (feishuTool == null) {
                log.warn("No mcp_feishu_docxV1DocumentBlockPatch tool found, cannot update block");
                return false;
            }
            
            // 构建请求参数
            var request = Map.of(
                "path", Map.of(
                    "document_id", documentId,
                    "block_id", blockId
                ),
                "params", Map.of(
                    "document_revision_id", documentRevisionId != null ? documentRevisionId : -1,
                    "user_id_type", "open_id"
                ),
                "data", updateData,
                "useUAT", true
            );
            
            var response = feishuTool.call(objectMapper.writeValueAsString(request));
            log.debug("MCP tool response: {}", response);
            
            return parseUpdateBlockResponse(response);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request for doc: {}, block: {}", documentId, blockId, e);
            throw new RuntimeException("JSON processing error", e);
        } catch (Exception e) {
            log.error("Failed to update block for doc: {}, block: {}", documentId, blockId, e);
            throw e;
        }
    }
    
    /**
     * 更新文档块内容 - 简化版本，使用文本元素更新
     */
    public boolean 更新文档块的内容(String documentId, String blockId, JsonNode updateData) {
        return 更新文档块的内容(documentId, blockId, updateData, -1);
    }
    
    /**
     * 更新块内容的断路器降级方法
     */
    public boolean updateBlockContentFallback(String documentId, String blockId, JsonNode updateData, Integer documentRevisionId, Exception ex) {
        String errorMessage = "Unknown error";
        String errorType = "Generic";
        
        if (ex != null) {
            errorMessage = ex.getMessage();
            Throwable cause = ex.getCause();
            
            if (cause instanceof java.util.concurrent.TimeoutException) {
                errorType = "Timeout";
                log.error("MCP服务器连接超时，更新块失败，doc: {}, block: {}。可能原因：MCP服务器已重启或网络连接问题", documentId, blockId);
                log.error("建议操作：1. 检查MCP服务器状态 2. 重启应用程序以重新建立连接");
            } else if (ex.getClass().getSimpleName().contains("ReactiveException")) {
                errorType = "Reactive";
                log.error("Reactive stream异常，更新块失败，doc: {}, block: {}。错误信息: {}", documentId, blockId, errorMessage);
            } else {
                log.error("更新块时发生异常，doc: {}, block: {}。错误类型: {}, 错误信息: {}", 
                    documentId, blockId, ex.getClass().getSimpleName(), errorMessage);
            }
        }
        
        log.warn("启用断路器降级模式，更新块，doc: {}, block: {}，错误类型: {}，返回false", documentId, blockId, errorType);
        return false;
    }
    
    /**
     * 解析块更新响应
     */
    private boolean parseUpdateBlockResponse(String response) {
        try {
            if (response == null || response.trim().isEmpty()) {
                log.warn("Received empty response from MCP tool for block update");
                return false;
            }
            
            log.debug("Raw MCP response for block update: {}", response);
            
            // 将响应转换为JSON节点进行解析
            JsonNode jsonNode = objectMapper.readTree(response);
            log.debug("Parsed JSON response for block update: {}", jsonNode);
            
            // MCP工具返回的格式是数组，需要先提取出实际的响应内容
            final JsonNode actualResponse;
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                JsonNode firstElement = jsonNode.get(0);
                if (firstElement.has("text")) {
                    String textContent = firstElement.get("text").asText();
                    log.debug("Extracted text content for block update: {}", textContent);
                    
                    // 检查是否包含成功信息
                    if (textContent.startsWith("Success: ")) {
                        String actualJsonString = textContent.substring("Success: ".length());
                        actualResponse = objectMapper.readTree(actualJsonString);
                        log.debug("Parsed actual response for block update: {}", actualResponse);
                        
                        // 检查响应中是否有错误信息
                        if (actualResponse.has("code") && actualResponse.get("code").asInt() == 0) {
                            log.info("Block update successful");
                            return true;
                        } else if (actualResponse.has("msg")) {
                            log.warn("Block update failed with message: {}", actualResponse.get("msg").asText());
                            return false;
                        } else {
                            log.debug("Block update response received: {}", response);
                            return true; // 假设没有错误代码就是成功
                        }
                    } else if (textContent.toLowerCase().contains("success")) {
                        log.info("Block update appears successful based on response text");
                        return true;
                    } else {
                        log.warn("Block update response does not indicate success: {}", textContent);
                        return false;
                    }
                } else {
                    log.warn("No text field in block update response");
                    return false;
                }
            } else {
                log.warn("Invalid response format for block update");
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to parse block update response: {}", response, e);
            return false;
        }
    }

} 