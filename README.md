# NotifyGuard

安卓通知管理 Demo（Jetpack Compose 单页面应用）：查看所有通知、识别常驻通知，并提供三种处理动作——立即隐藏、跳转系统渠道设置页、加入自动隐藏规则。

## 功能

- **授权引导**：检测"通知使用权"状态，一键跳转系统授权页；监听服务断连时支持"立即修复"（触发系统重新绑定）
- **通知列表**：实时列出当前所有通知（应用图标/名称、标题摘要、渠道 ID），支持"只看常驻"过滤；常驻项标记"常驻不可滑除"
- **每条通知三个动作**
  - `立即隐藏`：当场从通知栏消除（对方若重发，规则内的匹配项会被反复压制）
  - `渠道设置`：直达系统"该 App 该渠道"设置页，手动关闭渠道，永久生效（推荐用于根治常驻通知）
  - `自动隐藏`：建立"应用 + 渠道"规则，后续匹配通知自动消除
- **规则管理**：规则列表支持单条删除（取消自动）、一键清空
- **频控保护**：对方 App 高频重发时自动暂停该规则的压制并标记"重发频繁，暂停中"，避免死循环

## 下载安装

到 [Releases](../../releases) 下载 `NotifyGuard-v<版本>.apk` 直接安装即可（单个通用包，arm64 / arm32 设备都能装；本应用没有 native 代码，不需要按 CPU 架构分包）。

debug 包和 Release 包用的是同一把固定签名（`debug.keystore`，已入库），所以新版本可以直接覆盖安装，不用先卸载旧版。这把密钥和它的口令在本仓库里是公开的，仅适合个人自用分发。

## 环境要求

- Android Studio 最新稳定版（自带 JDK 17 和 SDK 管理器）
- Android 8.0（API 26）及以上真机或模拟器

## 如何运行

1. 用 Android Studio 打开本目录，首次同步会自动下载依赖（联网，需耐心）。
2. 若提示缺少 compileSdk 35，点界面中的安装提示即可。
3. Run 安装到设备，或命令行执行 `gradlew.bat assembleDebug` 产出 APK（位于 `app\build\outputs\apk\debug\`）。
4. 打开 App → 点「去授权」开启「NotifyGuard」的通知使用权 → 返回即可看到通知列表。

> 本工程已包含 Gradle Wrapper（`gradlew.bat` + `gradle-wrapper.jar`，Gradle 8.9），无需单独安装 Gradle。

## 代码结构

| 文件 | 职责 |
|------|------|
| `app/src/main/java/com/example/notifyguard/MainActivity.kt` | 入口 Activity；授权状态检测、跳授权页、组装 Compose UI |
| `app/src/main/java/com/example/notifyguard/GuardListenerService.kt` | 通知监听服务：捕获/消除通知、执行自动隐藏规则、频控保护 |
| `app/src/main/java/com/example/notifyguard/RulesStore.kt` | 自动隐藏规则的本地持久化（SharedPreferences） |
| `app/src/main/java/com/example/notifyguard/NotificationCenter.kt` | 通知快照的内存中心，向 UI 推送变更 |
| `app/src/main/java/com/example/notifyguard/AppInfoCache.kt` | 应用名称/图标缓存 |
| `app/src/main/java/com/example/notifyguard/ui/MainScreen.kt` | Compose 主界面：授权卡片、列表、规则区 |
| `.github/workflows/build.yml` | 云端构建：每次推分支产出 debug APK（Actions artifact） |
| `.github/workflows/release.yml` | 推 `v*` 标签时构建 release APK 并发布到 Releases |

## 发布新版本

1. 改 `app/build.gradle.kts` 里的 `versionCode` / `versionName`，提交。
2. 打标签推上去，标签名必须是 `v` + `versionName`（对不上 CI 会直接失败）：

   ```bash
   git tag v0.4.0
   git push origin v0.4.0
   ```

3. Actions 跑完后 Releases 页会出现 `NotifyGuard-v0.4.0.apk`，变更说明由 commit 自动生成。

## 技术要点

- 权限：`BIND_NOTIFICATION_LISTENER_SERVICE` 声明 + 系统`通知使用权`设置页引导
- 消除：`NotificationListenerService.cancelNotification(key)`
- 渠道直达：`ACTION_CHANNEL_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE` + `EXTRA_CHANNEL_ID`
- 常驻判定：`StatusBarNotification.isOngoing`
- 数据全部本地存储，无网络请求

## 已知边界

- 前台服务类顽固通知可能被系统/对方重发；此时走"渠道设置"由用户一次性关闭渠道
- 渠道的显示名只有系统设置页可见（App 侧只能拿到渠道 ID）
- 部分国产 ROM 需要额外开启"自启动/后台运行"，监听服务才稳定