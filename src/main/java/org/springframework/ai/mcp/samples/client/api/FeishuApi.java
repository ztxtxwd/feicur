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
        try {
            // 查找正确的飞书评论工具
            ToolCallback feishuTool = findFeishuCommentsToolCallback();
            if (feishuTool == null) {
                log.warn("No mcp_feishu_driveV1FileCommentList tool found, returning empty list");
                return Collections.emptyList();
            }
            
            // 构建正确的请求参数结构
            var request = Map.of(
                "path", Map.of("file_token", token),
                "params", Map.of(
                    "file_type", "docx",
                    "page_size", 50,
                    "user_id_type", "open_id"
                ),
                "useUAT", true
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
        log.warn("Circuit breaker activated for doc: {}, returning empty list. Error: {}", 
                token, ex.getMessage());
        return Collections.emptyList();
    }
    
    /**
     * 查找飞书评论工具回调
     */
    private ToolCallback findFeishuCommentsToolCallback() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        for (ToolCallback callback : callbacks) {
            String toolName = callback.getToolDefinition().name();
            if ("spring_ai_mcp_client_feishu_driveV1FileCommentList".equals(toolName)) {
                return callback;
            }
        }
        return null;
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
} 