# AGENTS.md

Minecraft 1.7.10 Forge mod（GTNH 构建脚本，`com.gtnewhorizons.gtnhconvention`），Java 源码位于 `src/main/java`。

## 环境：优先使用 IDEA 配置

**在执行任何 Gradle 命令之前，先读取 `.idea/` 下的 IDEA 项目配置，使命令行环境与 IDE 保持一致。不要直接使用 PATH 上任意版本的 `gradle`/`java`。**

注意：`.idea/` 已被 `.gitignore` 忽略，属于本机开发者的个人配置——读取它，但不要修改、重建或提交它。

### Gradle 路径

读取 `.idea/gradle.xml` 中 `GradleProjectSettings` 下的选项：

- `distributionType` = `LOCAL` 且配置了 `gradleHome` → 使用该路径下的 `gradle`。
- `distributionType` = `WRAPPED` / `DEFAULT_WRAPPED`，或未指定（本项目当前情况）→ 使用项目自带的 wrapper：
  - Windows：`.\gradlew.bat <task>`
  - Linux/macOS：`./gradlew <task>`
- `distributionType` = `WRAPPED_LOCATION` → 使用其指定的 wrapper 路径。

wrapper 对应的 Gradle 版本见 `gradle/wrapper/gradle-wrapper.properties`（当前为 Gradle 9.3.1），不要擅自升级或降级。

### Java 路径（JDK）

按以下优先级确定运行 Gradle 所用的 JDK，并通过 `JAVA_HOME` 环境变量或 `-Dorg.gradle.java.home=<jdk路径>` 传递给 Gradle：

1. `.idea/gradle.xml` 中的 `gradleJvm` 选项（若存在）。
2. `.idea/misc.xml` 中 `ProjectRootManager` 的 `project-jdk-name`（当前为 `25`）。该名称对应 IDEA 中注册的 JDK，需在已安装的 JDK 中找到匹配版本；可用 `Get-Command java`、检查常见安装目录或 `org.gradle.java.installations.paths` 属性定位。
3. `.java-version` 文件中的版本号（当前为 `25`）。
4. `gradle.properties` 中的 `org.gradle.java.home`（本项目未配置）。
5. `JAVA_HOME` 环境变量 / PATH 上的 `java`（仅作为兜底）。

示例（PowerShell）：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"   # 来自上述解析结果
.\gradlew.bat build
```

### 其他 IDEA 项目属性

`gradle.properties` 中与本机 IDEA 相关的可选属性（如 `ideaOverrideBuildType`、`ideaCheckSpotlessOnBuild`）应在 `$HOME/.gradle/gradle.properties` 中设置，不要写入项目内的 `gradle.properties`。

## 常用任务

```powershell
.\gradlew.bat build           # 完整构建（含 check）
.\gradlew.bat compileJava     # 仅编译
.\gradlew.bat test            # 单元测试
.\gradlew.bat runClient       # 启动客户端（工作目录 run/）
.\gradlew.bat runServer       # 启动服务端
.\gradlew.bat spotlessApply   # 自动格式化代码
.\gradlew.bat spotlessCheck   # 仅检查格式
```

## 代码规范

- 项目启用了 Spotless，提交/收尾前运行 `.\gradlew.bat spotlessApply`。
- 构建参数（modId、minecraftVersion、mixins 等）统一在 `gradle.properties` 中配置，修改前先阅读其中注释。
- 不要改动 `build.gradle.kts` / `settings.gradle.kts` 的插件结构；需要额外构建逻辑时优先使用 `addon.gradle(.kts)`（见 GTNH 构建脚本约定）。
- `.idea/`、`build/`、`run/`、`.gradle/` 均不提交。
