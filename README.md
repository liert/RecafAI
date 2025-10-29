# Recaf AI 插件

Recaf AI 是 [Recaf](https://github.com/Col-E/Recaf) 逆向工程工具的插件。它集成了生成式 AI 以协助完成各种逆向工程任务。

## 功能

- **AI 驱动的反编译:** 使用大型语言模型反编译 Java 字节码。
- **代码分析:** 在 AI 的帮助下分析和理解代码。
- **MCP 集成:** 实现模型上下文协议（Model Context Protocol）以增强 AI 交互。

## 构建

要构建插件，您可以使用提供的 Gradle 包装器。

```bash
./gradlew shadowJar
```

编译后的插件 JAR 将位于 `build/libs/` 目录中。

## 安装

1.  构建插件 JAR 文件。
2.  将 `RecafAI-1.0.0.jar` 文件复制到 Recaf 插件目录 (`<Recaf-Directory>/plugins`)。
3.  启动 Recaf。插件将自动加载。

## 使用

安装插件后，您可以通过 Recaf 用户界面访问其功能。该插件会向类和方法的上下文菜单中添加新项目，允许您执行 AI 辅助操作。

