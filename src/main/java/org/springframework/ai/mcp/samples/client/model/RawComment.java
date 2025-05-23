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
 * 飞书文档评论的原始数据模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RawComment {
    
    /**
     * 评论ID
     */
    private String commentId;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 评论作者ID
     */
    private String authorId;
    
    /**
     * 评论作者名称
     */
    private String authorName;
    
    /**
     * 创建时间
     */
    private Instant createTime;
    
    /**
     * 更新时间
     */
    private Instant updateTime;
    
    /**
     * 是否已解决
     */
    private Boolean isResolved;
    
    /**
     * 父评论ID（回复场景）
     */
    private String parentId;
    
    /**
     * 评论位置信息
     */
    private String position;
} 