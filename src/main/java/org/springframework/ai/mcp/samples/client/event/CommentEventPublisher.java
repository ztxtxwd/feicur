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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * 评论事件发布器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommentEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 发布评论变更事件
     */
    public void publishCommentChange(String docToken, CommentEvent event) {
        log.debug("Publishing comment change event: {} for doc: {}", event.getType(), docToken);
        
        var changeEvent = new CommentChangeEvent(
            docToken, 
            event.getType(), 
            event.getComment(),
            Instant.now()
        );
        
        eventPublisher.publishEvent(changeEvent);
        
        log.info("Published comment {} event for comment ID: {} in doc: {}", 
                event.getType().getDescription(), 
                event.getComment().getCommentId(), 
                docToken);
    }
} 