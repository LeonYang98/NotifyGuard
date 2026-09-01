# 通知哨兵 NotifyGuard（Demo）

安卓通知渠道管理工具 Demo：识别常驻通知 -> 反查所属渠道 -> 一键直达系统渠道设置页永久关闭；对反复重发的「钉子户」通知支持自动隐藏规则（含频控防对抗）。

- 技术栈：Kotlin + Jetpack Compose（Material 3，支持动态取色），minSdk 26（Android 8.0），targetSdk 35
- 数据全部本地存储，无网络请求
- 默认排除来电、闹钟、媒体播放类通知

## 三种处理动作

| 动作 | 原理 | 效果 |
|-----|------|------|
| 立即隐藏 | cancelNotification 单次消除 | 立即消失，App 重发会再出现 |
| 渠道设置 | ACTION_CHANNEL_NOTIFICATION_SETTINGS 深链 | 永久关闭该渠道（首选） |
| 自动隐藏 | 规则命中即自动消除 + 频控 | 治标自动化，对抗重发 |

## 构建方式：GitHub Actions 云端编译（本机无需 Android 环境）

1. 在 GitHub 新建一个空仓库（例如 notifyguard），不要勾选 README。
2. 在本目录执行：

   git init
   git add .
   git commit -m "NotifyGuard demo"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/notifyguard.git
   git push -u origin main

3. 推送后打开仓库的 Actions 标签页，等待 Build APK 工作流跑完（约 3~5 分钟）。
4. 点进完成的那次运行，在页面底部 Artifacts 里下载 notifyguard-debug-apk，解压得到 app-debug.apk。

## 安装到 vivo X90（OriginOS）

1. 把 app-debug.apk 传到手机（微信/QQ 文件传输、网盘、USB 均可），点击安装。若提示「未知来源」，按指引允许一次。
2. 首次打开 App，点「去授权」，在系统的**通知使用权**列表里打开「通知哨兵」。
3. vivo 防清理设置（保证监听服务长期存活）：
   - i管家 -> 应用管理 -> 通知哨兵：允许**自启动**；
   - 设置 -> 电池 -> 后台耗电管理（或后台高耗电）：找到通知哨兵，设为**允许**。

## 使用

1. 主界面列出当前所有活动通知（应用名、标题摘要、渠道 ID、是否常驻）。
2. 打开「只看常驻」过滤出钉子户通知。
3. 处理方式三选一：
   - 渠道设置：跳到系统该渠道的设置页，关掉开关，一劳永逸（推荐）；
   - 立即隐藏：立刻消除这一条；
   - 自动隐藏：加入规则，该「应用+渠道」的新通知自动消除。「自动隐藏规则」按钮可查看/删除/清空规则。

## 已知限制（与方案一致）

- 只能读到渠道 ID 字符串，读不到渠道显示名；跳到系统设置页后可看到真实名称。
- 对方 App 重发频繁时，自动隐藏会触发频控（10 秒内最多消 3 次，超出暂停 30 秒），此类情况请走「渠道设置」永久关闭。
- 部分 App 常驻与正常通知共用一个渠道，关闭渠道前请留意系统设置页中的说明。

## 本地构建（可选）

装有 Android Studio / JDK 17 + Android SDK 的机器上执行：gradlew :app:assembleDebug，产物在 app/build/outputs/apk/debug/app-debug.apk。
