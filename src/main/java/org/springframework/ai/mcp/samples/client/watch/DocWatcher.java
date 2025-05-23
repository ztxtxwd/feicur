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
package org.springframework.ai.mcp.samples.client.watch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.api.FeishuApi;
import org.springframework.ai.mcp.samples.client.diff.CommentEventDetector;
import org.springframework.ai.mcp.samples.client.event.CommentEventPublisher;
import org.springframework.ai.mcp.samples.client.model.CommentEvent;
import org.springframework.ai.mcp.samples.client.model.RawComment;
import org.springframework.ai.mcp.samples.client.watch.snapshot.CommentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单文档监听器 - 定时轮询文档评论变更
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocWatcher {
    
    private final FeishuApi feishuApi;
    private final CommentEventDetector eventDetector;
    private final CommentEventPublisher eventPublisher;
    
    @Value("${feicur.poll.interval:6000}")
    private long pollInterval;
    
    @Value("${feicur.idle.limit:10}")
    private int idleLimit;
    
    // 当前监听的文档token
    private final AtomicReference<String> currentDocToken = new AtomicReference<>();
    
    // 上一次的快照
    private final AtomicReference<CommentSnapshot> lastSnapshot = new AtomicReference<>();
    
    // 空闲计数器（连续无变更的次数）
    private final AtomicInteger idleCount = new AtomicInteger(0);
    
    // 监听状态
    private volatile boolean isWatching = false;
    
    /**
     * 开始监听指定文档
     */
    public void startWatching(String docToken) {
        if (docToken == null || docToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Document token cannot be null or empty");
        }
        
        String oldToken = currentDocToken.getAndSet(docToken);
        if (oldToken != null && !oldToken.equals(docToken)) {
            log.info("Switching from doc {} to doc {}", oldToken, docToken);
        }
        
        // 重置状态
        lastSnapshot.set(null);
        idleCount.set(0);
        isWatching = true;
        
        log.info("Started watching document: {}", docToken);
    }
    
    /**
     * 停止监听
     */
    public void stopWatching() {
        isWatching = false;
        String docToken = currentDocToken.getAndSet(null);
        lastSnapshot.set(null);
        idleCount.set(0);
        
        if (docToken != null) {
            log.info("Stopped watching document: {}", docToken);
        }
    }
    
    /**
     * 定时轮询任务
     */
    @Scheduled(fixedDelayString = "${feicur.poll.interval:6000}")
    public void pollComments() {
        if (!isWatching) {
            return;
        }
        
        String docToken = currentDocToken.get();
        if (docToken == null) {
            return;
        }
        
        try {
            log.debug("Polling comments for document: {}", docToken);
            
            // 获取当前评论
            List<RawComment> currentComments = feishuApi.listComments(docToken);
            
            // 创建新快照
            CommentSnapshot newSnapshot = CommentSnapshot.fromComments(docToken, currentComments);
            CommentSnapshot oldSnapshot = lastSnapshot.get();
            
            // 检测变更
            List<CommentEvent> events = eventDetector.detectChanges(oldSnapshot, newSnapshot);
            
            if (events.isEmpty()) {
                // 无变更，增加空闲计数
                int currentIdleCount = idleCount.incrementAndGet();
                log.debug("No changes detected, idle count: {}/{}", currentIdleCount, idleLimit);
                
                if (currentIdleCount >= idleLimit) {
                    log.info("Reached idle limit ({}) for document: {}, stopping watch", 
                            idleLimit, docToken);
                    // 达到空闲限制，自动停止监听
                    stopWatching();
                    return; // 提前退出，避免更新快照
                }
            } else {
                // 有变更，重置空闲计数
                idleCount.set(0);
                log.info("Detected {} comment events for document: {}", events.size(), docToken);
                
                // 发布事件
                for (CommentEvent event : events) {
                    eventPublisher.publishCommentChange(docToken, event);
                }
            }
            
            // 更新快照
            lastSnapshot.set(newSnapshot);
            
        } catch (Exception e) {
            log.error("Error occurred while polling comments for document: {}", docToken, e);
            // 发生错误时增加空闲计数，避免频繁重试
            idleCount.incrementAndGet();
        }
    }
    
    /**
     * 获取当前监听状态
     */
    public boolean isWatching() {
        return isWatching && currentDocToken.get() != null;
    }
    
    /**
     * 获取当前监听的文档token
     */
    public String getCurrentDocToken() {
        return currentDocToken.get();
    }
    
    /**
     * 获取当前空闲计数
     */
    public int getIdleCount() {
        return idleCount.get();
    }
    
    /**
     * 获取最后快照信息
     */
    public CommentSnapshot getLastSnapshot() {
        return lastSnapshot.get();
    }
    
    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        if (isWatching()) {
            log.info("Application shutting down, stopping document watcher");
            stopWatching();
        }
    }
} 