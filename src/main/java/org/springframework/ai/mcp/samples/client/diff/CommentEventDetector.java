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
package org.springframework.ai.mcp.samples.client.diff;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.model.CommentEvent;
import org.springframework.ai.mcp.samples.client.model.RawComment;
import org.springframework.ai.mcp.samples.client.watch.snapshot.CommentSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 评论变更事件检测器
 */
@Component
@Slf4j
public class CommentEventDetector {
    
    /**
     * 检测两个快照之间的差异，生成事件列表
     * 
     * @param oldSnapshot 旧快照（可以为null，表示第一次）
     * @param newSnapshot 新快照
     * @return 变更事件列表
     */
    public List<CommentEvent> detectChanges(CommentSnapshot oldSnapshot, CommentSnapshot newSnapshot) {
        List<CommentEvent> events = new ArrayList<>();
        
        if (newSnapshot == null) {
            log.warn("New snapshot is null, no events to detect");
            return events;
        }
        
        Map<String, RawComment> newComments = newSnapshot.getCommentMap();
        if (newComments == null) {
            log.debug("New snapshot has no comments");
            return events;
        }
        
        // 如果是第一次快照，忽略所有评论（不生成新增事件）
        if (oldSnapshot == null) {
            log.info("First snapshot detected, ignoring all {} comments (no events generated)", newComments.size());
            return events; // 返回空的事件列表
        }
        
        Map<String, RawComment> oldComments = oldSnapshot.getCommentMap();
        if (oldComments == null) {
            oldComments = new HashMap<>();
        }
        
        // 检测新增和修改的评论
        for (Map.Entry<String, RawComment> entry : newComments.entrySet()) {
            String commentId = entry.getKey();
            RawComment newComment = entry.getValue();
            RawComment oldComment = oldComments.get(commentId);
            
            if (oldComment == null) {
                // 新增评论
                events.add(new CommentEvent(CommentEvent.Type.NEW, newComment, "新增评论"));
                log.debug("Detected new comment: {}", commentId);
            } else {
                // 检查是否有修改
                CommentEvent changeEvent = detectCommentChange(oldComment, newComment);
                if (changeEvent != null) {
                    events.add(changeEvent);
                    log.debug("Detected comment change: {} - {}", commentId, changeEvent.getType());
                }
            }
        }
        
        // 检测删除的评论
        for (String oldCommentId : oldComments.keySet()) {
            if (!newComments.containsKey(oldCommentId)) {
                // 评论被删除
                RawComment deletedComment = oldComments.get(oldCommentId);
                events.add(new CommentEvent(CommentEvent.Type.DELETE, deletedComment, "评论被删除"));
                log.debug("Detected deleted comment: {}", oldCommentId);
            }
        }
        
        log.debug("Detected {} change events", events.size());
        return events;
    }
    
    /**
     * 检测单个评论的变更
     */
    private CommentEvent detectCommentChange(RawComment oldComment, RawComment newComment) {
        // 检查更新时间（幂等检查）
        if (isUpdateTimeEqual(oldComment.getUpdateTime(), newComment.getUpdateTime())) {
            return null; // 没有变更
        }
        
        // 检查解决状态变更
        if (!Objects.equals(oldComment.getIsResolved(), newComment.getIsResolved())) {
            if (Boolean.TRUE.equals(newComment.getIsResolved())) {
                return new CommentEvent(CommentEvent.Type.RESOLVE, newComment, "评论被解决");
            } else {
                return new CommentEvent(CommentEvent.Type.UNRESOLVE, newComment, "评论重新打开");
            }
        }
        
        // 检查内容变更
        if (!Objects.equals(oldComment.getContent(), newComment.getContent())) {
            return new CommentEvent(CommentEvent.Type.EDIT, newComment, "评论内容被修改");
        }
        
        // 检查其他字段变更（作者、位置等）
        if (hasOtherChanges(oldComment, newComment)) {
            return new CommentEvent(CommentEvent.Type.EDIT, newComment, "评论信息被修改");
        }
        
        return null;
    }
    
    /**
     * 检查更新时间是否相等（考虑精度问题）
     */
    private boolean isUpdateTimeEqual(Instant time1, Instant time2) {
        if (time1 == null && time2 == null) {
            return true;
        }
        if (time1 == null || time2 == null) {
            return false;
        }
        // 允许1秒的误差
        return Math.abs(time1.getEpochSecond() - time2.getEpochSecond()) <= 1;
    }
    
    /**
     * 检查其他字段是否有变更
     */
    private boolean hasOtherChanges(RawComment oldComment, RawComment newComment) {
        return !Objects.equals(oldComment.getAuthorId(), newComment.getAuthorId()) ||
               !Objects.equals(oldComment.getAuthorName(), newComment.getAuthorName()) ||
               !Objects.equals(oldComment.getParentId(), newComment.getParentId()) ||
               !Objects.equals(oldComment.getPosition(), newComment.getPosition());
    }
} 