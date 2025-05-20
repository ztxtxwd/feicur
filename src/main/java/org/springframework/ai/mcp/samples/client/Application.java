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
package org.springframework.ai.mcp.samples.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Value("${ai.user.input}")
	private String userInput;

	@Bean
	public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools,
			ConfigurableApplicationContext context) {
		// 打印可用工具信息
		System.out.println("Available tools:");
		for (int i = 0; i < tools.getToolCallbacks().length; i++) {
			System.out.println(i + ": " + tools.getToolCallbacks()[i].getToolDefinition().name());
			System.out.println("   Schema: " + tools.getToolCallbacks()[i].getToolDefinition().inputSchema());
		}
		
		return args -> {
			var chatClient = chatClientBuilder
					.defaultToolCallbacks(tools)
					.build();

			System.out.println("\n>>> QUESTION: " + userInput);
			
			// 创建请求并打印详细信息
			ChatClientRequestSpec prompt = chatClient.prompt(userInput);
			var response = prompt.call();
			
			// System.out.println("\n>>> COMPLETE RESPONSE: ");
			// System.out.println(response.chatClientResponse().chatResponse().toString());
			System.out.println("\n>>> ASSISTANT CONTENT: " + response.content());

			context.close();
		};
	}
}
