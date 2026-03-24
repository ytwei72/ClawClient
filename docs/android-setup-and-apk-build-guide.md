# Android 开发环境搭建与 APK 构建指南

> 适用于在仓库 `android/` 目录下开发 OpenClaw / OpenIM 相关会话类原生应用，并在 Windows 上产出调试或发布版 APK。

---

## 一、硬件与系统

| 项目 | 建议 |
|------|------|
| 操作系统 | Windows 10 / 11（64 位） |
| 内存 | ≥ 8 GB，推荐 16 GB |
| 磁盘 | 预留 ≥ 15 GB（Android Studio + SDK + 模拟器镜像） |
| USB 调试 | 真机需开启「开发者选项」与「USB 调试」，并安装手机厂商提供的 USB 驱动（部分机型） |

---

## 二、必装软件与版本

### 1. JDK（Java 开发套件）

- **推荐**：**JDK 17**（与当前 Android Gradle Plugin 8.x 主流组合一致）。
- 安装后确认：

```powershell
java -version
```

输出应包含 `17` 或你刻意对齐的 LTS 版本。若命令找不到，把 JDK 的 `bin` 目录加入系统 **PATH**，并设置 **JAVA_HOME** 指向 JDK 根目录（例如 `C:\Program Files\Java\jdk-17`）。

### 2. Android Studio

1. 从 [Android Studio 官方下载页](https://developer.android.com/studio) 下载并安装稳定版。
2. 首次启动完成 **Setup Wizard**，按提示安装 **Android SDK**、**Android SDK Platform**、**Android Virtual Device** 等组件。

### 3. Android SDK 组件（在 Studio 内安装）

打开 **Settings → Languages & Frameworks → Android SDK**（或 **SDK Manager**），建议至少勾选：

| 组件 | 用途 |
|------|------|
| **Android SDK Platform** | 与项目 `compileSdk` 一致的一个版本（如 API 34 或 35） |
| **Android SDK Build-Tools** | 构建工具，通常选最新稳定版 |
| **Android SDK Platform-Tools** | 含 `adb` |
| **Android SDK Command-line Tools** | 命令行 `sdkmanager` 等 |

模拟器开发时，在 **SDK Tools** 中勾选 **Android Emulator**，并在 **AVD Manager** 中创建虚拟设备。

---

## 三、环境变量（命令行构建 APK 推荐）

假设 SDK 默认路径为（请按本机 **SDK Location** 修改）：

`C:\Users\<用户名>\AppData\Local\Android\Sdk`

在「系统环境变量」中新增或修改：

| 变量名 | 值 |
|--------|-----|
| `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` | 上述 SDK 根目录路径 |

在 **PATH** 中追加（便于使用 `adb` 等）：

```
%ANDROID_SDK_ROOT%\platform-tools
```

新开终端验证：

```powershell
adb version
```

---

## 四、与 OpenClaw 会话相关的开发提示

- 连接参数、OpenIM / Tailscale / 直连等方案见项目内 **[OpenClaw 连接参数配置指南](./openclaw-connection-guide.md)**。
- 若使用 **`ws://` 明文 WebSocket**，需在 **Network Security Config** 或调试配置中允许明文流量；**生产环境优先使用 `wss://`**。
- **勿在 APK 中硬编码**长期有效的 Gateway Token、API Key；建议使用构建变体、远程配置或登录后下发。

---

## 五、在仓库中创建 `android` 工程（若尚未存在）

1. 在 Android Studio 中选择 **New Project**，模板可选 **Empty Activity**。
2. **Language** 建议选 **Kotlin**，**Build configuration** 选 **Gradle Kotlin DSL** 或 **Groovy**（与团队统一即可）。
3. **Save location** 选到本仓库下的 **`android`** 目录（或先建空文件夹再指向）。
4. **Minimum SDK** 按产品要求选择（例如 API 24+ 更利于现代网络与 TLS 栈）。

首次同步 Gradle 会下载 Wrapper 指定版本的 Gradle，需保持网络畅通。

---

## 六、构建 APK

### 方式 A：Android Studio 图形界面

1. 用 Android Studio 打开 **`android`** 目录（含 `settings.gradle` / `settings.gradle.kts` 的根）。
2. 等待 **Gradle Sync** 完成。
3. 菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。
4. 构建结束后点击通知中的 **locate**，得到 **`app-debug.apk`**（默认调试签名）。

### 方式 B：命令行（Windows）

在 **`android`** 工程根目录（存在 `gradlew.bat`）执行：

**调试包（Debug APK）：**

```powershell
.\gradlew.bat assembleDebug
```

输出路径一般为：

`android\app\build\outputs\apk\debug\app-debug.apk`

**发布包（Release APK）：**

需先在 `app` 模块中配置 **签名**（`signingConfigs` + `buildTypes.release`），再执行：

```powershell
.\gradlew.bat assembleRelease
```

输出路径一般为：

`android\app\build\outputs\apk\release\app-release.apk`

---

## 七、Release 签名简要说明

1. 使用 **Build → Generate Signed Bundle / APK**，按向导创建或选择 **keystore**（`.jks` / `.keystore`）。
2. 妥善备份 keystore 与密码；**丢失后无法用相同签名升级应用**。
3. 在 `build.gradle`（或 `build.gradle.kts`）中为 `release` 配置 `signingConfig`，避免将密码明文提交到公开仓库；可使用 `local.properties` 或 CI 密钥注入。

---

## 八、常见问题

| 现象 | 处理方向 |
|------|----------|
| Gradle Sync 失败 / 下载超时 | 检查网络与代理；必要时配置 Gradle 镜像或 HTTP 代理。 |
| `SDK location not found` | 在 `android/local.properties` 中设置 `sdk.dir=你的SDK路径`（注意 Windows 路径转义）。 |
| 模拟器极慢 | 在 BIOS 开启虚拟化；优先 x86_64 镜像；或改用真机调试。 |
| 安装 APK 提示解析失败 | 确认 CPU 架构与 APK 是否包含对应 `abiFilters`；或使用 universal/debug 包测试。 |

---

## 九、文档维护

- 若升级 **Android Gradle Plugin** 或 **Gradle** 主版本，请对照 [官方兼容性说明](https://developer.android.com/build/releases/gradle-plugin) 同步调整 JDK 要求。
- 本文档不绑定具体 `compileSdk` / AGP 版本号，以工程内 `android` 目录实际配置为准。
