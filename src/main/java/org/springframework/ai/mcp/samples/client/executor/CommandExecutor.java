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
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.mcp.samples.client.api.FeishuApi;
import org.springframework.ai.mcp.samples.client.queue.CommandQueue;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.observation.ObservationRegistry;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.mcp.samples.client.model.UserCommand;

import java.util.concurrent.TimeUnit;

/**
 * 指令执行器 - 定时消费队列中的指令
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandExecutor {
    private final ObjectMapper objectMapper;
    private final CommandQueue commandQueue;
    private final FeishuApi feishuApi;
    @Value("${feicur.execute.interval:3000}")
    private long executeInterval;

    private final ChatClient.Builder chatClientBuilder;
    private final ToolCallbackProvider toolCallbackProvider;
    private ChatClient chatClient;

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
        switch (command.getCommandType()) {
            case "EDIT":
                log.info("编辑指令: {}", command.getContent());
                // 获取评论关联的文档块
                List<JsonNode> docBlocks = feishuApi.获取文档所有块(command.getDocToken());
                docBlocks.stream().filter(block -> {
                    if (block.has("comment_ids")) {
                        JsonNode commentIds = block.get("comment_ids");
                        if (commentIds.isArray()) {
                            for (JsonNode commentId : commentIds) {
                                if (commentId.asText().equals(command.getCommentId())) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }).forEach(block -> {
                    // log.info("文档块: {}", block);
                    // 给有可能需要删除的部分标红
                    // 获取文本块中的elements
                    JsonNode elements = block.get("text").get("elements");
                    if (elements != null && elements.isArray()) {

                        for (JsonNode element : elements) {
                            // 检查text_run中的text_element_style是否包含comment_ids
                            JsonNode textRun = element.get("text_run");
                            if (textRun != null) {
                                JsonNode style = textRun.get("text_element_style");
                                if (style != null && style.has("comment_ids")) {
                                    JsonNode commentIds = style.get("comment_ids");
                                    if (commentIds.isArray()) {
                                        // 检查是否包含当前command的commentId
                                        for (JsonNode commentId : commentIds) {
                                            if (commentId.asText().equals(command.getCommentId())) {
                                                // 给匹配的element添加红色背景
                                                ((ObjectNode) style).put("background_color", 1);
                                                log.info("已标记需要修改的文本: {}", textRun.get("content").asText());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        log.info("修改的块id: {}", block.get("block_id").asText());
                        log.debug("修改后的文档块: {}", elements);

                        // 创建updateData
                        JsonNode updateData = objectMapper.createObjectNode();
                        ObjectNode updateTextElements = objectMapper.createObjectNode();
                        updateTextElements.set("elements", elements);
                        ((ObjectNode) updateData).set("update_text_elements", updateTextElements);
                        // feishuApi.更新文档块的内容(command.getDocToken(), block.get("block_id").asText(),
                        // updateData);

                        // if (chatClient == null) {
                        //     chatClient = chatClientBuilder
                        //             .defaultToolCallbacks(new ToolCallback[] { feishuApi.findFeishuToolCallback(
                        //                     "spring_ai_mcp_client_feishu_docxV1DocumentBlockPatch") })
                        //             .build();
                        // }
                        // ChatClientRequestSpec prompt = chatClient.prompt(command.getContent()
                        //         + ",判断用户是否是要修改现有内容而不是只插入新内容，如果是，按照下面的信息使用spring_ai_mcp_client_feishu_docxV1DocumentBlockPatch工具更新块，"
                        //         + "document:" + command.getDocToken() + "\nblock:" + block.get("block_id").asText()
                        //         + "\n" + elements.toPrettyString());
                        // var result = prompt.call();

                        var openAiApi = OpenAiApi.builder()
                                .apiKey("sk-Gd9U7RYvm0cM0T3tMwWhLocvYzhhbDqafo6g1krmcBDqZjnv")
                                .baseUrl("https://token.geminiapi.top")
                                .build();
                        var openAiChatOptions = OpenAiChatOptions.builder()
                                .model("gemini-2.0-flash-001")
                                .temperature(0.0)
                                .maxTokens(1)
                                .build();
                                
                        var chatModel = new OpenAiChatModel(openAiApi, openAiChatOptions,ToolCallingManager.builder().build(),RetryTemplate.defaultInstance(),ObservationRegistry.create());
                        SystemMessage systemMessage = new SystemMessage("你是一个分类器。根据用户请求，判断它是否是要修改现有内容。如果是，输出1；如果只是插入新内容，输出0。仅输出一个数字0或1，不要有其他文字或内容。");
                        UserMessage userMessage = new UserMessage(command.getContent());
                        ChatResponse response = chatModel.call(new Prompt(systemMessage, userMessage));
                        log.info("result: {}", response.getResult().getOutput().getText());
                        if ("1".equals(response.getResult().getOutput().getText())) {
                            log.info("需要修改现有内容");
                            feishuApi.更新文档块的内容(command.getDocToken(), block.get("block_id").asText(),updateData);
                        } else {
                            log.info("只是插入新内容");
                        }
                        
                    }
                });

                break;
            case "REJECT_AND_EDIT":
                log.info("拒绝并编辑指令: {}", command.getContent());
                break;
            case "REJECT_EDIT_RESULT":
                log.info("拒绝编辑结果指令: {}", command.getContent());
                break;
            case "ACCEPT_EDIT_RESULT":
                log.info("接受编辑结果指令: {}", command.getContent());
        }
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