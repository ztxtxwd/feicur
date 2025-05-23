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
package org.springframework.ai.mcp.samples.client.watch.snapshot;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.ai.mcp.samples.client.model.RawComment;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 评论快照类 - 存储某个时刻的评论状态
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentSnapshot {
    
    /**
     * 快照创建时间
     */
    private Instant timestamp;
    
    /**
     * 文档Token
     */
    private String docToken;
    
    /**
     * 评论ID到评论数据的映射
     */
    private Map<String, RawComment> commentMap;
    
    /**
     * 评论ID到更新时间的映射（用于幂等检查）
     */
    private Map<String, Instant> updateTimeMap;
    
    /**
     * 通过评论列表创建快照
     */
    public static CommentSnapshot fromComments(String docToken, List<RawComment> comments) {
        CommentSnapshot snapshot = new CommentSnapshot();
        snapshot.setTimestamp(Instant.now());
        snapshot.setDocToken(docToken);
        snapshot.setCommentMap(new HashMap<>());
        snapshot.setUpdateTimeMap(new HashMap<>());
        
        for (RawComment comment : comments) {
            if (comment.getCommentId() != null) {
                snapshot.getCommentMap().put(comment.getCommentId(), comment);
                if (comment.getUpdateTime() != null) {
                    snapshot.getUpdateTimeMap().put(comment.getCommentId(), comment.getUpdateTime());
                } else if (comment.getCreateTime() != null) {
                    snapshot.getUpdateTimeMap().put(comment.getCommentId(), comment.getCreateTime());
                }
            }
        }
        
        return snapshot;
    }
    
    /**
     * 获取评论数量
     */
    public int getCommentCount() {
        return commentMap != null ? commentMap.size() : 0;
    }
    
    /**
     * 检查是否包含指定评论ID
     */
    public boolean containsComment(String commentId) {
        return commentMap != null && commentMap.containsKey(commentId);
    }
    
    /**
     * 获取指定评论
     */
    public RawComment getComment(String commentId) {
        return commentMap != null ? commentMap.get(commentId) : null;
    }
    
    /**
     * 获取指定评论的更新时间
     */
    public Instant getCommentUpdateTime(String commentId) {
        return updateTimeMap != null ? updateTimeMap.get(commentId) : null;
    }
} 