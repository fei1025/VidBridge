# VidBridge

VidBridge 是使用 Kotlin 和 Jetpack Compose 编写的 Android 原生媒体播放器。工程已经移除 Flutter、Dart 与 Flutter 插件，播放生命周期由 AndroidX Media3 管理。

## 已实现

- Compose、Material 3、Navigation Compose 与 ViewModel/StateFlow 单向状态
- Storage Access Framework 本地文件夹来源与持久读取授权，无传统存储权限
- 来源新增、编辑、删除、连接测试，以及本地、SMB、WebDAV 共用的浏览与播放流程
- Room v3 保存来源、远端索引、媒体项目、媒体版本、NFO 元数据、图片引用、历史、收藏和扫描任务
- WorkManager 分页后台扫描、隐藏/系统目录过滤、文件指纹、断点重试、取消、陈旧索引清理
- 电影/剧集文件名解析、Kodi 风格 NFO、小型本地海报引用和质量版本聚合
- 媒体库搜索、继续观看、视频/文件夹收藏及收藏目录跳转
- Android Keystore AES-GCM 加密密码；Room、导航和播放 URI 仅保存凭据引用
- SMBJ 的 SMB 2/3 登录、目录浏览和随机读取
- OkHttp WebDAV/HTTPS 的 PROPFIND、Basic 认证、HTTP Range、重定向及证书错误映射
- 协议无关的 `RemoteFileSystem`、能力模型、错误模型与 Media3 `DataSource`
- Media3 `MediaSessionService`、ExoPlayer、系统播放控制、通知、音频焦点和耳机断开处理
- 按需随机读取与 Seek，不会先完整下载大型视频
- 每 10 秒及退出时保存当前队列项进度，30 秒后断点续播，并连续播放同一来源的媒体库队列
- 画中画入口，以及倍速、画面比例、网络缓冲、完成阈值、常亮、横屏和隐藏文件设置
- Room schema 导出、2→3 迁移测试基建和 JVM 协议/解析规则测试

总体边界和演进设计见 [Android 原生媒体架构](docs/android-native-media-architecture.md)。

## 环境与构建

- Android Studio / Android SDK 36
- JDK 17
- Android 7.0（API 24）或更高版本

在 PowerShell 中执行：

    Set-Location android
    .\gradlew.bat testDebugUnitTest assembleDebug

APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。Room 迁移仪器测试需要连接模拟器或真机后执行 `connectedDebugAndroidTest`。

## 使用

1. 启动应用，选择“添加来源”，再选择本地、SMB 或 WebDAV。
2. 本地来源通过系统文件夹选择器授权；网络来源的主机只填写 IP 或主机名。
3. SMB 端口通常为 445，共享名称例如 `Videos`；WebDAV 默认使用 HTTPS 443。
4. 保存后可浏览并直接播放，也可从来源菜单启动媒体库扫描。
5. 扫描状态显示在来源卡片；媒体库提供全部、继续观看和收藏三个分区。

Windows 需同时授予共享权限与 NTFS 读取权限。NAS 建议只启用 SMB2/3，并使用只有媒体读取权限的专用账号。

## 安全边界

- 密码、认证头和 Cookie 不进入 Room、URI、导航参数或日志。
- 凭据由 AndroidKeyStore 设备密钥保护；删除来源会清理对应凭据与 SAF 授权。
- WebDAV 默认 HTTPS，应用禁用明文 HTTP，不会全局绕过 TLS 校验。
- 播放器只通过来源 ID、逻辑路径和协议抽象读取字节。
- 应用禁用系统自动备份。

## 仍需外部环境验证或后续扩展

以下项目依赖真实设备、服务器或第三方服务，不能由仓库内软件测试替代：5GB+ 高码率视频长时间播放，HDR/杜比与特殊音频直通，厂商硬解兼容性，复杂外挂字幕，NAS 休眠/网络切换恢复，以及 Release Macrobenchmark。NFS、SFTP、Jellyfin/Emby/Plex、DLNA、TMDB、Glance 桌面组件和 Android TV 属于架构文档明确列出的后续来源或平台阶段，当前工厂会对未实现类型返回统一“不支持”错误，不会伪装成文件协议。
