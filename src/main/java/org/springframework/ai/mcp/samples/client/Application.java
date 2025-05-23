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

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.samples.client.watch.DocWatchManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	/**
	 * v0.2版本：支持CLI和Web双模式
	 * - CLI模式：提供--token参数时，直接启动监听
	 * - Web模式：不提供参数时，启动Web服务器等待REST API调用
	 */
	@Bean
	public CommandLineRunner documentWatchRunner(DocWatchManager watchManager) {
		return args -> {
			log.info("=== Feicur 文档监听助手 v0.2 启动 ===");
			
			String docToken = extractDocTokenFromArgs(args);
			
			if (docToken != null && !docToken.trim().isEmpty()) {
				// CLI模式：有token参数，直接启动监听
				startCliMode(watchManager, docToken);
			} else {
				// Web模式：无token参数，启动Web服务器
				startWebMode();
			}
		};
	}
	
	/**
	 * 启动CLI模式
	 */
	private void startCliMode(DocWatchManager watchManager, String docToken) {
		log.info("🖥️  CLI模式启动");
		log.info("从命令行参数读取到文档token: {}", docToken);
		
		try {
			// 启动文档监听
			watchManager.startWatching(docToken);
			
			log.info("✅ 文档监听已启动");
			log.info("🔍 正在监听文档评论变更...");
			log.info("📝 检测到的指令将会在控制台显示");
			log.info("🌐 同时提供REST API服务: http://localhost:7777");
			log.info("⏸️  按 Ctrl+C 停止监听");
			
			// 保持应用运行，等待定时任务执行
			keepApplicationRunning();
			
		} catch (Exception e) {
			log.error("启动文档监听失败", e);
			System.exit(1);
		}
	}
	
	/**
	 * 启动Web模式
	 */
	private void startWebMode() {
		log.info("🌐 Web模式启动");
		log.info("✅ REST API服务已启动: http://localhost:7777");
		log.info("");
		log.info("📋 可用端点:");
		log.info("  🔗 启动监听: http://localhost:7777/watch?url=<飞书文档URL>");
		log.info("  📊 查看状态: http://localhost:7777/status");
		log.info("  ❤️  健康检查: http://localhost:7777/health");
		log.info("");
		log.info("💡 使用方式:");
		log.info("  在飞书文档地址前加上: http://localhost:7777/watch?url=");
		log.info("  例如: http://localhost:7777/watch?url=https://example.feishu.cn/docx/xxx");
		log.info("");
		log.info("🔧 API管理:");
		log.info("  停止监听: curl -X DELETE http://localhost:7777/watch/<token>");
		log.info("");
		log.info("⏸️  按 Ctrl+C 停止服务");
		
		// Web模式下不需要额外的等待，Spring Boot会保持应用运行
		setupShutdownHook();
	}
	
	/**
	 * 从命令行参数中提取文档token
	 */
	private String extractDocTokenFromArgs(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("--token=")) {
				return arg.substring("--token=".length()).trim();
			}
		}
		return null;
	}
	
	/**
	 * 打印使用说明
	 */
	private void printUsage() {
		System.out.println("\n📖 Feicur v0.2 使用说明:");
		System.out.println("");
		System.out.println("🖥️  CLI模式（直接启动监听）:");
		System.out.println("java -jar feicur.jar --token=YOUR_DOCUMENT_TOKEN");
		System.out.println("");
		System.out.println("🌐 Web模式（REST API服务）:");
		System.out.println("java -jar feicur.jar");
		System.out.println("然后访问: http://localhost:7777/watch?url=<飞书文档URL>");
		System.out.println("");
		System.out.println("参数说明:");
		System.out.println("  --token=<文档token>  要监听的飞书文档token（可选）");
		System.out.println("");
		System.out.println("示例:");
		System.out.println("  CLI: java -jar feicur.jar --token=doccnXeWNhHv42eBdRUd6mh0vdb");
		System.out.println("  Web: http://localhost:7777/watch?url=https://example.feishu.cn/docx/doccnXeWNhHv42eBdRUd6mh0vdb");
		System.out.println("");
		System.out.println("💡 提示:");
		System.out.println("  - 确保MCP服务器正在运行（http://localhost:8788）");
		System.out.println("  - Web模式支持同时监听多个文档");
		System.out.println("");
	}
	
	/**
	 * 保持应用运行（CLI模式）
	 */
	private void keepApplicationRunning() {
		setupShutdownHook();
		
		// 使用简单的无限循环保持应用运行
		// Spring的定时任务会在后台执行
		try {
			Object lock = new Object();
			synchronized (lock) {
				lock.wait(); // 永久等待，直到应用被中断
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.info("应用被中断，正在退出...");
		}
	}
	
	/**
	 * 设置关闭钩子
	 */
	private void setupShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			log.info("\n🛑 正在停止Feicur服务...");
			log.info("👋 Feicur已退出，感谢使用！");
		}));
	}
}
