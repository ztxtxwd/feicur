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
package org.springframework.ai.mcp.samples.client.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 用户指令模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserCommand {
    
    /**
     * 指令类型
     */
    private String commandType;
    
    /**
     * 指令内容
     */
    private String content;
    
    /**
     * 原始评论数据
     */
    private RawComment sourceComment;
    
    /**
     * 指令创建时间
     */
    private Instant timestamp;
    
    /**
     * 文档token
     */
    private String docToken;
    
    /**
     * 构造函数，自动设置时间戳
     */
    public UserCommand(String commandType, RawComment sourceComment) {
        this.commandType = commandType;
        this.sourceComment = sourceComment;
        this.timestamp = Instant.now();
        if (sourceComment != null) {
            this.content = sourceComment.getContent();
        }
    }
    
    /**
     * 获取格式化的指令描述
     */
    public String getFormattedDescription() {
        return String.format("[%s] %s - %s", 
            commandType, 
            timestamp,
            content != null ? content.substring(0, Math.min(50, content.length())) + "..." : "无内容"
        );
    }
} 