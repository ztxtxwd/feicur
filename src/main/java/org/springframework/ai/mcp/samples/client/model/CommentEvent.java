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

/**
 * 评论变更事件
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentEvent {
    
    /**
     * 事件类型枚举
     */
    public enum Type {
        NEW("新增"),
        EDIT("编辑"),
        DELETE("删除"),
        RESOLVE("解决"),
        UNRESOLVE("重新打开");
        
        private final String description;
        
        Type(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 事件类型
     */
    private Type type;
    
    /**
     * 相关的评论数据
     */
    private RawComment comment;
    
    /**
     * 事件描述（可选）
     */
    private String description;
} 