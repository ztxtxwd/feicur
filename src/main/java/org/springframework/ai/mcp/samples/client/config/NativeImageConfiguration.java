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
package org.springframework.ai.mcp.samples.client.config;

import org.springframework.ai.mcp.samples.client.Application;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * 配置类，用于注册GraalVM Native Image所需的运行时提示。
 * 这些提示帮助GraalVM在构建原生镜像时正确处理反射、资源和代理等。
 */
@Configuration
@ImportRuntimeHints(NativeImageConfiguration.NativeImageHints.class)
public class NativeImageConfiguration {

    /**
     * 运行时提示注册器，用于注册应用程序所需的反射和资源访问。
     */
    static class NativeImageHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // 注册主应用类
            hints.reflection().registerType(Application.class, 
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS);
            
            // 注册配置文件资源
            hints.resources().registerPattern("application.properties");
            hints.resources().registerPattern("mcp-servers-config.json");
        }
    }
}
