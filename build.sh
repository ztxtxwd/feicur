#!/bin/bash

# Feicur MVP 构建脚本
echo "🚀 开始构建 Feicur MVP..."

# 检查 Java 版本
java_version=$(java -version 2>&1 | grep -oP 'version "?(1\.)?\K\d+' | head -1)
if [ "$java_version" -lt 17 ]; then
    echo "❌ 错误: 需要 Java 17 或更高版本，当前版本: $java_version"
    exit 1
fi

echo "✅ Java 版本检查通过: $java_version"

# 清理并构建
echo "🧹 清理旧文件..."
./mvnw clean

echo "📦 构建项目..."
./mvnw package -DskipTests

if [ $? -eq 0 ]; then
    echo "✅ 构建成功！"
    echo ""
    echo "📋 使用说明:"
    echo "java -jar target/mcp-starter-default-client-0.0.1-SNAPSHOT.jar --token=YOUR_DOCUMENT_TOKEN"
    echo ""
    echo "📖 详细说明请查看 MVP_README.md"
else
    echo "❌ 构建失败！请检查错误信息。"
    exit 1
fi 