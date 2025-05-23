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
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档监听管理器 - 管理多个文档的监听器
 * MVP版本只支持单文档监听
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocWatchManager {
    
    private final DocWatcher docWatcher;
    
    // 当前监听的文档列表（MVP版本只有一个）
    private final Set<String> watchedDocs = ConcurrentHashMap.newKeySet();
    
    /**
     * 开始监听文档
     * MVP版本：只支持一个文档，新的文档会替换旧的
     */
    public void startWatching(String docToken) {
        if (docToken == null || docToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Document token cannot be null or empty");
        }
        
        // MVP版本：先停止之前的监听
        stopAllWatching();
        
        // 开始新的监听
        docWatcher.startWatching(docToken);
        watchedDocs.add(docToken);
        
        log.info("DocWatchManager started watching document: {}", docToken);
    }
    
    /**
     * 停止监听指定文档
     */
    public void stopWatching(String docToken) {
        if (docToken == null) {
            return;
        }
        
        if (watchedDocs.remove(docToken)) {
            // 检查是否是当前监听的文档
            if (docToken.equals(docWatcher.getCurrentDocToken())) {
                docWatcher.stopWatching();
                log.info("DocWatchManager stopped watching document: {}", docToken);
            }
        } else {
            log.warn("Document {} is not being watched", docToken);
        }
    }
    
    /**
     * 停止所有监听
     */
    public void stopAllWatching() {
        if (!watchedDocs.isEmpty()) {
            docWatcher.stopWatching();
            watchedDocs.clear();
            log.info("DocWatchManager stopped watching all documents");
        }
    }
    
    /**
     * 检查是否正在监听指定文档
     */
    public boolean isWatching(String docToken) {
        return watchedDocs.contains(docToken) && 
               docToken.equals(docWatcher.getCurrentDocToken()) && 
               docWatcher.isWatching();
    }
    
    /**
     * 获取当前监听的文档列表
     */
    public Set<String> getWatchedDocuments() {
        return new HashSet<>(watchedDocs);
    }
    
    /**
     * 获取当前监听的文档数量
     */
    public int getWatchedDocumentCount() {
        return watchedDocs.size();
    }
    
    /**
     * 获取当前活跃的监听器状态
     */
    public Map<String, Object> getWatcherStatus() {
        Map<String, Object> status = new HashMap<>();
        
        String currentDoc = docWatcher.getCurrentDocToken();
        status.put("currentDocument", currentDoc);
        status.put("isWatching", docWatcher.isWatching());
        status.put("idleCount", docWatcher.getIdleCount());
        status.put("watchedDocuments", getWatchedDocuments());
        
        if (docWatcher.getLastSnapshot() != null) {
            status.put("lastSnapshotTime", docWatcher.getLastSnapshot().getTimestamp());
            status.put("lastCommentCount", docWatcher.getLastSnapshot().getCommentCount());
        }
        
        return status;
    }
    
    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("DocWatchManager shutting down...");
        stopAllWatching();
    }
} 