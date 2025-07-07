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
package org.springframework.ai.mcp.samples.client.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.api.FeishuApi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP连接状态监控器
 * 定期检查MCP服务器连接状态，并在连接异常时发出警告
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpConnectionMonitor {
    
    private final FeishuApi feishuApi;
    
    // 连接状态
    private final AtomicReference<ConnectionStatus> lastStatus = new AtomicReference<>(ConnectionStatus.UNKNOWN);
    private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    
    // 连续失败计数
    private volatile int consecutiveFailures = 0;
    
    /**
     * 定期检查MCP连接健康状态
     * 每30秒检查一次
     */
    @Scheduled(fixedDelay = 30000)
    public void checkConnectionHealth() {
        try {
            boolean isHealthy = feishuApi.checkMcpConnectionHealth();
            Instant now = Instant.now();
            
            if (isHealthy) {
                handleSuccessfulCheck(now);
            } else {
                handleFailedCheck(now, "Connection check returned false");
            }
            
        } catch (Exception e) {
            handleFailedCheck(Instant.now(), e.getMessage());
        }
    }
    
    /**
     * 获取可用工具列表并记录状态
     * 每5分钟运行一次，提供更详细的健康信息
     */
    @Scheduled(fixedDelay = 300000) // 5分钟
    public void checkAvailableTools() {
        try {
            List<String> tools = feishuApi.getAvailableTools();
            
            if (!tools.isEmpty()) {
                log.info("MCP连接正常，可用工具数量: {}，工具列表: {}", tools.size(), tools);
                handleSuccessfulCheck(Instant.now());
            } else {
                log.warn("MCP连接异常，未能获取到任何可用工具");
                handleFailedCheck(Instant.now(), "No available tools");
            }
            
        } catch (Exception e) {
            log.warn("检查可用工具时发生异常: {}", e.getMessage());
            handleFailedCheck(Instant.now(), e.getMessage());
        }
    }
    
    /**
     * 处理成功的检查
     */
    private void handleSuccessfulCheck(Instant now) {
        ConnectionStatus previousStatus = lastStatus.getAndSet(ConnectionStatus.HEALTHY);
        lastSuccessTime.set(now);
        consecutiveFailures = 0;
        
        // 如果之前是失败状态，现在恢复了，记录日志
        if (previousStatus == ConnectionStatus.UNHEALTHY) {
            log.info("MCP连接已恢复正常");
        }
    }
    
    /**
     * 处理失败的检查
     */
    private void handleFailedCheck(Instant now, String reason) {
        ConnectionStatus previousStatus = lastStatus.getAndSet(ConnectionStatus.UNHEALTHY);
        lastFailureTime.set(now);
        consecutiveFailures++;
        
        // 根据连续失败次数决定日志级别
        if (consecutiveFailures == 1) {
            log.warn("MCP连接检查失败: {}。这可能是临时问题。", reason);
        } else if (consecutiveFailures <= 3) {
            log.warn("MCP连接连续失败 {} 次: {}。请检查MCP服务器状态。", consecutiveFailures, reason);
        } else if (consecutiveFailures == 5) {
            log.error("MCP连接已连续失败 {} 次: {}。建议重启应用程序或检查MCP服务器。", consecutiveFailures, reason);
        } else if (consecutiveFailures % 10 == 0) {
            log.error("MCP连接已连续失败 {} 次。系统可能需要人工干预。", consecutiveFailures);
        }
        
        // 如果是第一次失败，记录更详细的信息
        if (previousStatus == ConnectionStatus.HEALTHY) {
            log.warn("MCP连接从正常状态变为异常。最后成功时间: {}", lastSuccessTime.get());
        }
    }
    
    /**
     * 获取当前连接状态
     */
    public ConnectionStatus getCurrentStatus() {
        return lastStatus.get();
    }
    
    /**
     * 获取最后成功时间
     */
    public Instant getLastSuccessTime() {
        return lastSuccessTime.get();
    }
    
    /**
     * 获取最后失败时间
     */
    public Instant getLastFailureTime() {
        return lastFailureTime.get();
    }
    
    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        UNKNOWN,
        HEALTHY,
        UNHEALTHY
    }
} 