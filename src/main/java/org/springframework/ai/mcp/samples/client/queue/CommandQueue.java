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
package org.springframework.ai.mcp.samples.client.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.model.UserCommand;
import org.springframework.stereotype.Component;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 指令队列封装类
 */
@Component
@Slf4j
public class CommandQueue {
    
    private final BlockingQueue<UserCommand> queue;
    
    public CommandQueue() {
        // 创建容量为1000的有界队列
        this.queue = new LinkedBlockingQueue<>(1000);
        log.info("Command queue initialized with capacity: 1000");
    }
    
    /**
     * 非阻塞添加指令到队列
     * 
     * @param command 用户指令
     * @return 成功返回true，队列满返回false
     */
    public boolean offer(UserCommand command) {
        boolean success = queue.offer(command);
        if (success) {
            log.debug("Command offered to queue: {}", command.getCommandType());
        } else {
            log.warn("Failed to offer command to queue, queue is full. Command: {}", command.getCommandType());
        }
        return success;
    }
    
    /**
     * 阻塞获取队列中的指令
     * 
     * @return 用户指令，如果队列为空则阻塞等待
     * @throws InterruptedException 如果等待被中断
     */
    public UserCommand take() throws InterruptedException {
        UserCommand command = queue.take();
        log.debug("Command taken from queue: {}", command.getCommandType());
        return command;
    }
    
    /**
     * 带超时的获取队列中的指令
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 用户指令，如果超时返回null
     * @throws InterruptedException 如果等待被中断
     */
    public UserCommand poll(long timeout, TimeUnit unit) throws InterruptedException {
        UserCommand command = queue.poll(timeout, unit);
        if (command != null) {
            log.debug("Command polled from queue: {}", command.getCommandType());
        }
        return command;
    }
    
    /**
     * 获取队列当前大小
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * 清空队列
     */
    public void clear() {
        int size = queue.size();
        queue.clear();
        log.info("Command queue cleared, removed {} commands", size);
    }
} 