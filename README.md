# VidBridge

VidBridge 是使用 Kotlin 和 Jetpack Compose 编写的 Android 原生媒体播放器。工程已经移除 Flutter、Dart 与 Flutter 插件，播放引擎最终采用 libVLC。

## 已实现

- Compose、Material 3、Navigation Compose 与 ViewModel/StateFlow 单向状态
- Storage Access Framework 本地文件夹来源与持久读取授权，无传统存储权限
- 来源新增、编辑、删除、连接测试，以及本地、SMB、NFS、WebDAV、SFTP、Jellyfin、Emby、Plex 共用的浏览与播放流程
- Room v8 保存来源、远端索引、媒体项目、媒体版本、NFO/TMDB 元数据、导演/演员、图片引用、历史、收藏、扫描任务、独立播放列表和离线下载任务，并为播放历史查询建立索引
- WorkManager 分页后台扫描、隐藏/系统目录过滤、文件指纹、断点重试、取消、陈旧索引清理
- 电影/剧集文件名解析、Kodi 风格 NFO、小型本地海报引用和质量版本聚合
- 媒体库搜索、继续观看、视频/文件夹收藏及收藏目录跳转
- 媒体库“最近播放”记录，包含已完成和未完成项目并按最后播放时间排序
- Android Keystore AES-GCM 加密密码；Room、导航和播放 URI 仅保存凭据引用
- SMB 2/3 登录、服务器共享发现、目录浏览和随机读取
- OkHttp WebDAV/HTTPS 的 PROPFIND、Basic 认证、HTTP Range、重定向及证书错误映射
- 所有非本地来源的连接、目录、文件信息和打开句柄的有限超时重试与会话重建
- 协议无关的 `RemoteFileSystem`、能力模型和错误模型；libVLC 统一承载各类来源播放
- SFTP SSH 文件浏览、分页、属性、随机读取和 `sftp://` 播放入口；密码只通过 Keystore 和 libVLC 运行时选项使用，主机密钥采用首次信任后变更拒绝
- NFSv3 AUTH_SYS 只读浏览、分页、属性、随机读取和 `nfs://` 播放入口
- libVLC 播放核心、独立后台播放服务、系统 MediaSession、通知、音频焦点和耳机断开暂停处理
- 非敏感播放会话恢复：进程被系统回收后重新打开应用可回到最近播放项，并沿用播放历史位置
- 播放失败手动重试，以及网络恢复后的后台自动重试
- 按需随机读取与 Seek，不会先完整下载大型视频
- 每 10 秒及退出时保存当前队列项进度，30 秒后断点续播，并连续播放同一来源的媒体库队列
- 画中画入口，以及倍速、画面比例、网络缓冲、完成阈值、常亮、横屏和隐藏文件设置
- 播放器左右双击快退/快进；媒体库在平板宽屏使用自适应海报网格
- libVLC 音轨和字幕轨道读取、选择与播放状态同步
- 设置页可配置默认音轨/字幕语言代码，统一转换为 libVLC 自动选轨参数
- libVLC 章节读取、当前章节同步与章节跳转
- 播放页直接倍速、视频比例、外挂字幕加载；支持同目录同名字幕自动匹配，手动字幕优先
- 播放器支持音频/字幕提前或延后同步调节（±10 秒范围）
- 独立离线下载队列：WorkManager 后台下载、`.part` 临时文件原子提交、断点续传、暂停/重试/删除、进度持久化和已完成文件通过 libVLC 播放；支持本地、文件协议和 Jellyfin/Emby/Plex HTTP Range 下载，媒体库已知文件大小会立即用于进度和断点校验，不占用播放缓冲
- 本地脱敏崩溃报告：保存最近 5 次异常到应用私有 no-backup 目录，不上传且会清理 URI 凭据、密码、Token、Cookie 和授权头
- 播放队列、同剧集连续播放、画中画、Mini Player、后台进程恢复和播放列表
- 桌面播放控制组件：标题、进度、播放/暂停、停止和回到应用
- 媒体库按全部、电影、剧集和普通视频筛选
- 播放列表支持添加、移除、播放以及上移/下移调整顺序
- 剧集按剧集聚合展示，支持按名称、年份、最近更新和文件大小排序
- 平板宽屏媒体库提供左侧海报网格与右侧详情预览双栏布局，手机和 TV 保持各自的单栏交互
- 可选 TMDB 电影/剧集元数据与海报自动补全；Android TV Leanback 启动入口、焦点卡片和大屏网格
- Android TV 首页启动时自动把焦点放到“继续观看”或第一部媒体，遥控器可直接开始导航
- 详情页展示 NFO/TMDB 导演和演员信息；Room v8 提供从旧版本到演员元数据和播放历史索引的迁移
- 首页最近媒体按远端文件更新时间排序，继续观看按播放历史排序
- Room schema 导出、3→4→5→6→7→8 迁移测试、播放队列/播放列表/播放历史测试和 JVM/Android 协议解析测试

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

1. 启动应用，选择“添加来源”，再选择本地、SMB、NFS、WebDAV、SFTP 或媒体服务器。
2. 本地来源通过系统文件夹选择器授权；网络来源的主机只填写 IP 或主机名。
3. SMB 端口通常为 445；共享名称可留空以自动列出服务器共享，也可填写 `Videos` 等名称直接进入。NFS 填写导出路径（例如 `/volume1/video`），默认端口 2049；WebDAV 默认使用 HTTPS 443；SFTP 默认 SSH 22；Jellyfin/Emby 默认 8096，Plex 默认 32400，媒体服务器使用 API Token。
4. 保存后可浏览并直接播放，也可从来源菜单启动媒体库扫描。
5. 扫描状态显示在来源卡片；媒体库提供全部、继续观看和收藏三个分区。
6. 在媒体详情点击“下载到本机”，可在媒体库右上角“下载”页查看、暂停、重试、删除和播放离线文件。

Windows 需同时授予共享权限与 NTFS 读取权限。NAS 建议只启用 SMB2/3，并使用只有媒体读取权限的专用账号。

## 安全边界

- 密码、认证头和 Cookie 不进入 Room、URI、导航参数或日志。
- TMDB API Key 通过 Android Keystore 加密凭据保存，网络请求和日志不输出 Key。
- TMDB 海报和背景使用 HTTPS 引用并进入独立图片缓存；同名本地海报优先于在线海报。
- 凭据由 AndroidKeyStore 设备密钥保护；删除来源会清理对应凭据与 SAF 授权。
- WebDAV 默认 HTTPS，应用禁用明文 HTTP，不会全局绕过 TLS 校验。
- 播放器只通过来源 ID、逻辑路径和协议抽象读取字节。
- 应用禁用系统自动备份。

### Release 签名

Release 签名只从本机环境变量或 Gradle 属性读取，不将密钥材料提交到仓库。配置
`VIDBRIDGE_KEYSTORE`、`VIDBRIDGE_KEYSTORE_PASSWORD`、`VIDBRIDGE_KEY_ALIAS` 和
`VIDBRIDGE_KEY_PASSWORD` 后执行 `:app:assembleRelease :app:bundleRelease`；未配置时产出
unsigned Release，便于本地验证但不能直接发布。

## 仍需外部环境验证或后续扩展

以下项目依赖真实设备、服务器或第三方服务，不能由仓库内软件测试替代：5GB+ 高码率视频长时间播放，HDR/杜比与特殊音频直通，厂商硬解兼容性，复杂外挂字幕，NAS 休眠/网络切换恢复，真实 NFS/SFTP/Jellyfin/Emby/Plex/DLNA 认证与播放，以及实体设备上的 Release Macrobenchmark 性能数据。NFS 当前为 NFSv3 AUTH_SYS 只读首版；DLNA 已接入局域网 SSDP 发现、ContentDirectory SOAP 目录浏览、DIDL-Lite 解析和资源 URL 直连 libVLC，Manifest 允许明文传输以兼容局域网设备，但代码只接受私有地址，仍需真实设备验证不同厂商兼容性。桌面播放控制组件已使用原生 AppWidget 实现，不依赖 Glance。SFTP 主机密钥已采用首次信任后变更拒绝，仍需真机验证首次连接和服务器换钥提示。后台播放服务、系统通知和 MediaSession 已接入，但仍需真机验证锁屏、进程回收和不同厂商后台限制。Macrobenchmark 已在 API 36 模拟器跑通 1 项冷启动测试，模拟器结果不作为真实性能结论。
