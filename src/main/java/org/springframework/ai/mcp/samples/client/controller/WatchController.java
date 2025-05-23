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
package org.springframework.ai.mcp.samples.client.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.watch.DocWatchManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.2版本 REST API 控制器
 * 实现重定向模式的文档监听功能
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // 允许跨域请求
public class WatchController {
    
    private final DocWatchManager watchManager;
    
    // 飞书文档URL正则表达式
    private static final Pattern FEISHU_URL_PATTERN = 
            Pattern.compile(".*feishu\\.cn/(?:docx?|docs)/([a-zA-Z0-9]+)");
    
    /**
     * 启动文档监听并重定向到原始飞书文档
     * 
     * @param feishuDocUrl 飞书文档完整URL
     * @return 302重定向响应
     */
    @GetMapping("/watch")
    public ResponseEntity<Void> startWatchingAndRedirect(
            @RequestParam("url") String feishuDocUrl) {
        
        try {
            log.info("接收到监听请求，文档URL: {}", feishuDocUrl);
            
            // 1. 解析飞书文档URL，提取token
            String docToken = extractTokenFromFeishuUrl(feishuDocUrl);
            log.info("解析到文档token: {}", docToken);
            
            // 2. 启动文档监听
            watchManager.startWatching(docToken);
            log.info("✅ 已启动文档监听: {}", docToken);
            
            // 3. 重定向到原始飞书文档
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(feishuDocUrl))
                    .build();
                    
        } catch (IllegalArgumentException e) {
            log.error("❌ URL解析失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ 启动监听失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 停止监听指定文档
     * 
     * @param token 文档token
     * @return JSON响应
     */
    @DeleteMapping("/watch/{token}")
    public ResponseEntity<Map<String, Object>> stopWatching(
            @PathVariable String token) {
        
        try {
            log.info("接收到停止监听请求，文档token: {}", token);
            
            boolean wasWatching = watchManager.isWatching(token);
            watchManager.stopWatching(token);
            
            String message = wasWatching ? 
                "已停止监听文档: " + token : 
                "文档未在监听中: " + token;
                
            log.info("✅ {}", message);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "token", token,
                "wasWatching", wasWatching
            ));
            
        } catch (Exception e) {
            log.error("❌ 停止监听失败", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "停止监听失败: " + e.getMessage(),
                "token", token
            ));
        }
    }
    
    /**
     * 查看当前监听状态
     * 
     * @return JSON状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        
        try {
            Map<String, Object> status = watchManager.getWatcherStatus();
            log.debug("查询监听状态: {}", status);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ 获取状态失败", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "获取状态失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 健康检查端点
     * 
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Feicur Document Watcher",
            "version", "v0.2",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 从飞书文档URL中提取文档token
     * 
     * @param url 飞书文档URL
     * @return 文档token
     * @throws IllegalArgumentException 如果URL格式无效
     */
    private String extractTokenFromFeishuUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("文档URL不能为空");
        }
        
        // 支持各种飞书文档URL格式：
        // https://xxx.feishu.cn/docx/doccnXeWNhHv42eBdRUd6mh0vdb
        // https://xxx.feishu.cn/docs/doccnXeWNhHv42eBdRUd6mh0vdb
        // https://xxx.feishu.cn/doc/doccnXeWNhHv42eBdRUd6mh0vdb
        
        Matcher matcher = FEISHU_URL_PATTERN.matcher(url);
        
        if (matcher.find()) {
            String token = matcher.group(1);
            log.debug("从URL {} 解析到token: {}", url, token);
            return token;
        }
        
        throw new IllegalArgumentException("无效的飞书文档URL格式。" +
                "期望格式: https://xxx.feishu.cn/docx/token 或 https://xxx.feishu.cn/docs/token。" +
                "实际URL: " + url);
    }
} 