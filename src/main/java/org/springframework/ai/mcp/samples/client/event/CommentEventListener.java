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
package org.springframework.ai.mcp.samples.client.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.model.CommentEvent;
import org.springframework.ai.mcp.samples.client.model.UserCommand;
import org.springframework.ai.mcp.samples.client.queue.CommandQueue;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 评论事件监听器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentEventListener {
    
    private final CommandQueue commandQueue;
    
    /**
     * 处理评论变更事件
     */
    @EventListener
    @Async
    public void handleCommentChange(CommentChangeEvent event) {
        log.info("Processing comment change: {} for doc: {}", 
                 event.getEventType(), event.getDocToken());
        
        var command = mapEventToCommand(event);
        if (command != null) {
            command.setDocToken(event.getDocToken());
            boolean success = commandQueue.offer(command);
            if (success) {
                log.info("Successfully queued command: {}", command.getCommandType());
            } else {
                log.warn("Failed to queue command: {}, queue might be full", command.getCommandType());
            }
        } else {
            log.debug("No command mapping for event type: {}", event.getEventType());
        }
    }
    
    /**
     * 事件到指令的映射逻辑
     */
    private UserCommand mapEventToCommand(CommentChangeEvent event) {
        return switch (event.getEventType()) {
            case NEW -> new UserCommand("ADD_REQUIREMENT", event.getComment());
            case EDIT -> new UserCommand("UPDATE_REQUIREMENT", event.getComment());
            case DELETE -> new UserCommand("REMOVE_REQUIREMENT", event.getComment());
            case RESOLVE -> new UserCommand("RESOLVE_REQUIREMENT", event.getComment());
            case UNRESOLVE -> new UserCommand("REOPEN_REQUIREMENT", event.getComment());
            default -> {
                log.warn("Unknown event type: {}", event.getEventType());
                yield null;
            }
        };
    }
} 