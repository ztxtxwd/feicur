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
package org.springframework.ai.mcp.samples.client.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.model.UserCommand;
import org.springframework.ai.mcp.samples.client.queue.CommandQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * 指令执行器 - 定时消费队列中的指令
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandExecutor {
    
    private final CommandQueue commandQueue;
    
    @Value("${feicur.execute.interval:3000}")
    private long executeInterval;
    
    /**
     * 定时执行指令消费
     * 使用fixedDelayString从配置文件读取间隔
     */
    @Scheduled(fixedDelayString = "${feicur.execute.interval:3000}")
    public void executeCommands() {
        try {
            // 使用poll避免无限阻塞，超时时间设为执行间隔的一半
            UserCommand command = commandQueue.poll(executeInterval / 2, TimeUnit.MILLISECONDS);
            
            if (command != null) {
                executeCommand(command);
            } else {
                log.debug("No commands in queue, continuing...");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Command execution interrupted", e);
        } catch (Exception e) {
            log.error("Error occurred while executing commands", e);
        }
    }
    
    /**
     * 执行单个指令（MVP版本只打印）
     */
    private void executeCommand(UserCommand command) {
        log.info("=== 执行指令 ===");
        log.info("指令类型: {}", command.getCommandType());
        log.info("文档Token: {}", command.getDocToken());
        log.info("时间戳: {}", command.getTimestamp());
        log.info("指令内容: {}", command.getContent());
        
        if (command.getSourceComment() != null) {
            log.info("评论详情:");
            log.info("  - 评论ID: {}", command.getSourceComment().getCommentId());
            log.info("  - 作者: {}", command.getSourceComment().getAuthorName());
            log.info("  - 创建时间: {}", command.getSourceComment().getCreateTime());
            log.info("  - 更新时间: {}", command.getSourceComment().getUpdateTime());
            log.info("  - 是否已解决: {}", command.getSourceComment().getIsResolved());
        }
        
        log.info("===============");
        
        // 在控制台也打印便于观察
        System.out.println("\n🔔 新指令: " + command.getFormattedDescription());
        System.out.println("📄 文档: " + command.getDocToken());
        System.out.println("👤 作者: " + (command.getSourceComment() != null ? 
                          command.getSourceComment().getAuthorName() : "未知"));
        System.out.println("💬 内容: " + (command.getContent() != null ? 
                          command.getContent().substring(0, Math.min(100, command.getContent().length())) : "无内容"));
        System.out.println("----------------------------------------\n");
    }
    
    /**
     * 获取队列状态信息
     */
    @Scheduled(fixedDelay = 30000) // 每30秒打印一次队列状态
    public void printQueueStatus() {
        int queueSize = commandQueue.size();
        if (queueSize > 0) {
            log.info("当前队列中有 {} 个待执行指令", queueSize);
        }
    }
} 