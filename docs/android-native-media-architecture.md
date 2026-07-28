# Android 原生媒体播放器架构设计

## 1. 文档目的

本文档用于指导 LanPlayer 从当前 Flutter 原型演进为 Android 原生媒体播放器。产品目标是构建一款类似 Infuse 的 Android 应用：能够连接局域网、媒体服务器和云端存储，建立本地媒体库，并稳定播放不同封装、编码、字幕和音轨的视频。

本文档重点解决以下问题：

- 使用 Kotlin 和 Jetpack Compose 构建手机、平板及后续 Android TV 界面。
- 支持 SMB、NFS、WebDAV、SFTP 等多种文件访问协议。
- 后续接入 Jellyfin、Emby、Plex、DLNA/UPnP 和云盘时不破坏现有架构。
- 网络协议、媒体库、元数据和播放器之间保持解耦。
- 大文件播放、Seek、缓存、断线恢复和安全凭据具备统一设计。

> 术语说明：NAS 是网络存储设备或服务形态，不是一种网络协议。群晖、威联通或自建服务器可能同时提供 SMB、NFS、WebDAV、SFTP、DLNA、Jellyfin 等入口。应用应让用户选择实际连接协议，而不是提供一个含义不明确的“NAS 协议”。WebDAV 的正确名称是 WebDAV，不是 WebDev。

## 2. 产品边界

### 2.1 长期目标

- 浏览本地文件、Windows/macOS 共享目录和 NAS。
- 建立电影、电视剧和其他视频的统一媒体库。
- 支持海报、简介、演员、季/集、观看状态和收藏。
- 支持常见视频封装、硬件解码、多音轨、内嵌/外挂字幕和章节。
- 支持断点续播、连续播放、播放列表、画中画和后台控制。
- 支持手机、平板，并为 Android TV 保留清晰的适配边界。
- 后续支持媒体服务器、云盘和跨设备进度同步。

### 2.2 第一阶段不承诺

- 不承诺一次性达到 Infuse 的格式、HDR、杜比及音频直通覆盖范围。
- 不在核心播放链尚未稳定时开发复杂海报墙和动画。
- 不把所有协议塞进第一版。
- 不为统一接口而假装所有协议能力相同。
- 不通过完整下载数 GB 文件后再开始播放。

## 3. 技术选型

| 领域 | 建议方案 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin | 原生协程、Flow、类型安全和 Android 工具链支持 |
| UI | Jetpack Compose + Material 3 | 手机和平板主界面；后续使用 Compose for TV |
| 导航 | Navigation Compose | 页面导航和深链 |
| 状态 | ViewModel + StateFlow | 单向数据流，避免页面直接持有协议会话 |
| 数据库 | Room | 来源、索引、媒体信息、历史和收藏 |
| 设置 | DataStore | 用户偏好和播放器默认设置 |
| 后台任务 | WorkManager | 媒体扫描、增量同步、元数据刷新 |
| 图片 | Coil | 海报、背景和本地图片缓存 |
| 播放生命周期 | AndroidX Media3 Session | MediaSession、通知、耳机、遥控器、后台控制 |
| 播放引擎 | 可替换的 PlayerEngine | 第一阶段真机比较 Media3 ExoPlayer 与 libVLC |
| 依赖注入 | Hilt 或显式构造注入 | 核心接口不依赖具体框架 |
| 凭据 | Android Keystore 支持的加密存储 | Room 中只保存 credentialId |
| 性能验证 | Macrobenchmark + Baseline Profile | 必须用 Release 构建评估 Compose 和播放性能 |

Compose 只负责界面与控制层。视频解码和显示仍由 MediaCodec/libVLC 与 `SurfaceView` 或 `TextureView` 完成，因此不应把播放器性能简单归因于 Compose 或 XML。

## 4. 总体架构

```text
Compose UI / Android TV UI / App Widget
                    │
                    ▼
Presentation: ViewModel + UiState + UiAction
                    │
                    ▼
Domain: UseCase + Repository + MediaSource 接口
          │                         │
          ▼                         ▼
Media Library / Metadata       Protocol Adapters
Room / TMDB / NFO              SMB / NFS / WebDAV / SFTP / Local
          │                         │
          └──────────┬──────────────┘
                     ▼
             PlaybackSourceResolver
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
  Direct URI   Seekable DataSource  Local HTTP Range Bridge
        │            │            │
        └────────────┴────────────┘
                     ▼
              PlayerEngine 接口
              Media3 / libVLC
                     │
                     ▼
        MediaSessionService + Surface
```

核心原则：

1. 页面不知道当前使用 SMB、WebDAV 还是 NFS。
2. 播放器不知道账号、密码和服务器配置。
3. 协议实现不负责海报墙或播放控制。
4. 数据库存储稳定的业务标识，不存带密码 URI。
5. 任何播放引擎都通过统一接口接入 MediaSession。

## 5. 模块建议

```text
app/                         应用入口、导航和依赖装配
core/common/                 结果类型、错误、日志、调度器
core/database/               Room 数据库和迁移
core/security/               凭据与 Keystore
core/network/                网络状态、DNS、超时和证书策略
core/media/                  媒体格式、轨道、字幕公共模型

feature/sources/             网络来源管理
feature/browser/             目录浏览
feature/library/             媒体库、扫描和筛选
feature/details/             电影/剧集详情
feature/player/              播放器页面与控制层
feature/history/             历史和继续播放
feature/settings/            设置

protocol/api/                协议公共接口和能力模型
protocol/local/              Android 本地文件
protocol/smb/                SMB 2/3
protocol/webdav/             WebDAV/HTTPS
protocol/nfs/                NFS
protocol/sftp/               SFTP

server/api/                  媒体服务器公共接口
server/jellyfin/             Jellyfin
server/emby/                 Emby
server/plex/                 Plex
server/dlna/                 DLNA/UPnP

playback/api/                PlayerEngine 和播放源模型
playback/media3/             ExoPlayer 实现
playback/vlc/                libVLC 实现
playback/session/            MediaSessionService 和通知
playback/bridge/             必要时使用的本地 HTTP Range 桥

metadata/api/                元数据接口
metadata/local/              NFO、文件名和本地图片
metadata/tmdb/               TMDB 匹配和缓存
```

初期可以使用单 Gradle module 配合上述 package，等边界稳定后再拆分模块，避免一开始承担过高构建复杂度。

## 6. 媒体来源模型

### 6.1 来源与协议分离

一台 NAS 可以配置多个来源：

```text
家庭 NAS
├── SMB: smb://192.168.1.20/Movies
├── WebDAV: https://nas.example.com/dav/Videos
├── SFTP: sftp://nas.example.com/media
└── Jellyfin: https://nas.example.com/jellyfin
```

建议的数据模型：

```kotlin
data class MediaSourceConfig(
    val id: String,
    val displayName: String,
    val type: MediaSourceType,
    val endpoint: Endpoint,
    val rootPath: String,
    val credentialId: String?,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class MediaSourceType {
    LOCAL,
    SMB,
    NFS,
    WEBDAV,
    SFTP,
    JELLYFIN,
    EMBY,
    PLEX,
    DLNA,
}
```

`Endpoint` 应拆分保存 scheme、host、port 和 TLS 设置。不要只保存一条可以随意拼接的 URL，更不能在 URL 中保存用户名和密码。

### 6.2 文件协议与媒体服务器不是同一种抽象

需要区分两类来源：

- 文件协议：SMB、NFS、WebDAV、SFTP、本地存储。它们主要提供目录、文件属性和字节读取。
- 媒体服务器：Jellyfin、Emby、Plex、DLNA。它们可能直接提供媒体库、海报、播放地址、转码、用户和播放进度 API。

不要强迫 Jellyfin 模拟普通文件系统，也不要要求 SMB 提供演员和剧集元数据。两者最终都可以产出统一的 `PlayableItem`，但发现和浏览接口应分别设计。

## 7. 协议公共接口

### 7.1 能力模型

不同协议的功能并不一致，应由实现主动声明能力：

```kotlin
data class SourceCapabilities(
    val canList: Boolean,
    val canStat: Boolean,
    val canSeekRead: Boolean,
    val canStreamRead: Boolean,
    val canWrite: Boolean,
    val canDelete: Boolean,
    val canRename: Boolean,
    val canSearch: Boolean,
    val supportsChangeToken: Boolean,
    val supportsServerSideMetadata: Boolean,
)
```

页面根据能力显示操作。例如只读 DLNA 来源不显示重命名，不能随机读取的来源不承诺精确 Seek。

### 7.2 文件访问接口

```kotlin
interface RemoteFileSystem {
    val sourceId: String
    val capabilities: SourceCapabilities

    suspend fun connect()
    suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry>
    suspend fun stat(path: RemotePath): RemoteFileInfo
    suspend fun open(path: RemotePath): RemoteReadHandle
    suspend fun close()
}

interface RemoteReadHandle : AutoCloseable {
    val size: Long?
    val seekable: Boolean

    suspend fun readAt(offset: Long, length: Int): ByteArray
    fun stream(startOffset: Long = 0): Flow<ByteArray>
}
```

设计要求：

- `RemotePath` 是协议无关的逻辑路径，不直接等于 URI。
- 文件唯一标识优先使用协议提供的稳定 ID；没有稳定 ID 时组合来源、路径、大小和修改时间。
- `readAt` 必须明确并发策略、取消行为和最大读取块。
- 大目录需要分页或流式返回，不能假设一次列出所有文件。
- 会话和文件句柄必须明确关闭，网络切换后允许重建。

### 7.3 协议差异

| 来源 | 浏览 | 随机读取 | 典型特点 | 第一阶段建议 |
| --- | --- | --- | --- | --- |
| Local | 是 | 是 | Storage Access Framework 权限 | 支持 |
| SMB 2/3 | 是 | 是 | NAS/Windows 最常见，认证与重连复杂 | 首发 |
| WebDAV | 是 | 依赖 HTTP Range | 易通过 HTTPS 跨网络访问 | 第二个协议 |
| NFS | 是 | 是 | NAS 常见，但 Android 库和权限需验证 | 后续 |
| SFTP | 是 | 是 | 安全，吞吐和 Seek 实现需实测 | 后续 |
| DLNA/UPnP | 有限 | 依赖资源 URL | 发现方便，模型不等同文件系统 | 后续 |
| Jellyfin/Emby/Plex | API | 服务器决定 | 自带元数据、播放进度和转码 | 独立适配 |

## 8. 播放源解析

协议层不能直接启动播放器。统一通过播放源解析器：

```kotlin
sealed interface PlaybackSource {
    data class DirectUri(
        val uri: Uri,
        val headers: Map<String, String> = emptyMap(),
    ) : PlaybackSource

    data class Seekable(
        val handleFactory: suspend () -> RemoteReadHandle,
        val mimeType: String?,
        val length: Long?,
    ) : PlaybackSource

    data class LocalBridge(
        val uri: Uri,
        val close: suspend () -> Unit,
    ) : PlaybackSource
}

interface PlaybackSourceResolver {
    suspend fun resolve(item: PlayableItem): PlaybackSource
}
```

解析优先级建议：

1. 播放引擎能够稳定直接读取的安全 URI。
2. 播放器支持自定义 DataSource 时，使用可随机读取句柄。
3. 无法直接适配时，使用绑定 `127.0.0.1` 的临时 HTTP Range Bridge。

本地 Bridge 是兼容层，不是业务协议。它必须：

- 仅绑定 loopback。
- 使用高强度临时 token。
- 支持 GET、HEAD、206、Content-Range 和取消。
- 限制单段 Range 或明确实现多段 Range。
- 不在 URI、日志或异常中泄漏凭据。
- 播放结束后强制关闭服务器、文件句柄和协议会话。

## 9. 播放器架构

### 9.1 播放器接口

```kotlin
interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val tracks: StateFlow<TrackCatalog>

    suspend fun prepare(source: PlaybackSource, startPositionMs: Long?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun selectAudio(trackId: String?)
    fun selectSubtitle(trackId: String?)
    fun attachSurface(surface: Surface)
    fun detachSurface()
    fun release()
}
```

界面只依赖 `PlayerEngine` 或 Media3 `MediaController`。不在 Composable 中直接创建协议连接、libVLC 实例或数据库事务。

### 9.2 Media3 与 libVLC 的角色

- Media3 Session：负责 Android 系统集成、播放命令、队列、通知、后台服务、耳机和 TV 遥控器。
- ExoPlayer：优先验证 Android 硬件解码、标准容器、Surface、字幕和 Media3 原生集成。
- libVLC：验证更广泛容器/编码、复杂字幕、SMB 兼容性和特殊媒体文件。
- 如果使用 libVLC 作为主引擎，应通过适配层接入 Media3，而不是绕过 MediaSession。

最终选择必须以真机样本矩阵为依据，不能只比较 API 数量。

### 9.3 Compose、PiP 和桌面组件

- 普通页面、播放器控制层和应用内 Mini Player 使用 Compose。
- 视频画面使用 Media3 `PlayerSurface`，或通过互操作层连接 libVLC Surface。
- 系统小窗口使用 Android Picture-in-Picture，不自行模拟悬浮窗。
- 桌面组件使用 Jetpack Glance，提供封面、标题、播放/暂停和下一集；桌面组件不能承载视频 Surface。
- Android TV 使用独立的 TV 导航和焦点组件，共享领域层、协议层、数据库和播放器服务。

## 10. 媒体库与扫描

建议至少包含以下实体：

```text
media_sources             来源配置，不含密码
credentials               只保存安全存储引用和类型
remote_entries            文件索引和协议稳定标识
media_items               电影、剧集和普通视频
media_versions            同一内容的不同文件/清晰度
metadata_records          TMDB/NFO/服务器元数据及版本
artwork                    海报、背景、本地缓存引用
playback_history           进度、时长、完成状态和最后播放时间
favorites                  用户收藏
scan_jobs                  扫描状态、游标和错误
```

扫描流程：

```text
读取来源能力
→ 增量列目录
→ 过滤排除目录和隐藏文件
→ 保存文件指纹
→ 解析电影/剧集文件名
→ 优先读取 NFO 和本地图片
→ 必要时请求在线元数据
→ 合并为媒体库项目
→ 清理已删除或失效条目
```

扫描必须可暂停、取消和增量恢复。不要在页面打开时同步扫描整台 NAS，也不要对每个文件立即读取完整媒体信息。

## 11. 凭据与安全

- Room 只保存 `credentialId`，不保存密码、令牌或私钥正文。
- 密码、访问令牌和私钥由 Android Keystore 支持的安全组件保护。
- 日志统一经过脱敏器，不记录认证头、Cookie、完整 URI 查询参数和 SMB 凭据。
- WebDAV 和媒体服务器默认使用 HTTPS；自签名证书必须显式确认并按主机固定，不能全局关闭 TLS 校验。
- 本地 HTTP Bridge 只监听 `127.0.0.1`，不得监听 `0.0.0.0`。
- 删除来源时同时删除凭据、活动会话和临时播放 token。
- 协议模块不能自行弹出 UI 请求密码，应返回统一认证错误给上层。

## 12. 错误模型

```kotlin
sealed interface SourceFailure {
    data object AuthenticationRequired : SourceFailure
    data object AuthenticationRejected : SourceFailure
    data object HostUnreachable : SourceFailure
    data object Timeout : SourceFailure
    data object PermissionDenied : SourceFailure
    data object NotFound : SourceFailure
    data object CertificateRejected : SourceFailure
    data object UnsupportedOperation : SourceFailure
    data object ProtocolMismatch : SourceFailure
    data class Unknown(val cause: Throwable) : SourceFailure
}
```

每个协议负责把底层异常映射为统一错误，上层负责生成用户可理解的提示。日志可以保留异常链，但必须先脱敏。

## 13. 缓存和断线恢复

缓存分为不同层次，不能混为一个“缓存开关”：

- 图片缓存：海报、背景和头像，由 Coil 管理。
- 元数据缓存：Room 中保存来源、更新时间和失效规则。
- 目录缓存：短期展示最近结果，后台重新验证。
- 播放缓冲：由播放器和协议读取器共同控制。
- 离线下载：用户明确触发的持久文件，独立于播放缓冲。

断线恢复建议：

1. 监听网络变化，但不因一次回调立即重建所有连接。
2. 播放读取失败后按错误类型决定是否重试。
3. 使用指数退避和最大次数，Seek 后取消旧请求。
4. 重建协议会话和文件句柄后从最后确认的字节/播放位置恢复。
5. 认证失败和文件变化不能无限重试。

## 14. 测试矩阵

### 14.1 协议一致性测试

所有 `RemoteFileSystem` 实现运行同一组契约测试：

- 连接、认证失败和超时。
- 根目录及深层目录浏览。
- 中文、空格、emoji 和特殊符号路径。
- 空文件、未知长度文件和 5GB 以上文件。
- `readAt` 的起始、中间、末尾及越界行为。
- 并发读取、取消和关闭后的行为。
- 网络中断、服务端重启及文件被删除。
- 不在日志和异常中出现密码。

### 14.2 播放真机测试

- MP4、MKV、AVI、MOV、TS/M2TS 和 WebM。
- H.264、H.265、AV1 及设备不支持硬解时的降级行为。
- 5GB 以上高码率视频连续播放至少 60 分钟。
- 高频 Seek、暂停、恢复和连续切换视频。
- 多音轨、内嵌字幕、外挂字幕和字幕偏移。
- 横竖屏、后台、锁屏、PiP、来电/音频焦点。
- Wi-Fi 切换、短暂断网、NAS 休眠和恢复。
- 不同 Android 版本、芯片厂商和低内存设备。

### 14.3 性能指标

- 冷启动和首屏时间。
- 1000+ 项目录滚动掉帧率。
- 海报墙图片内存峰值。
- 开始播放首帧时间。
- Seek 到新画面的时间。
- 长时间播放内存曲线和句柄数量。
- 同一视频在 Media3 与 libVLC 下的 CPU、耗电和掉帧。

性能结论只使用 Release 构建、Macrobenchmark 和真机数据，不使用 Compose Debug 模式体感下结论。

## 15. 实施阶段

### 阶段 0：播放引擎技术验证

- 创建最小 Kotlin + Compose 项目。
- 接入 Media3 Session 和视频 Surface。
- 分别验证 ExoPlayer 与 libVLC。
- 用本地文件和 HTTP Range 完成格式、Seek、字幕、音轨、PiP 测试。
- 确定主播放器与备用策略。

### 阶段 1：SMB MVP

- 来源管理和安全凭据。
- SMB 2/3 目录浏览与随机读取。
- 播放、暂停、Seek、音轨、字幕和断点续播。
- 完成 5GB+ MKV 长时间真机验证。

### 阶段 2：第二协议 WebDAV

- 用 WebDAV 验证协议抽象是否真正独立。
- 支持 HTTPS、Basic/Digest 或服务实际需要的认证方式。
- 验证 HTTP Range、重定向、自签名证书和断线恢复。
- 禁止为 WebDAV 在页面中添加协议专属播放逻辑。

### 阶段 3：媒体库

- Room 索引、增量扫描、文件名解析和本地 NFO。
- TMDB 元数据、海报缓存、电影和电视剧聚合。
- 历史、收藏、继续播放和连续播放。

### 阶段 4：Android 平台体验

- MediaSessionService、通知、耳机和音频焦点。
- PiP、应用内 Mini Player、桌面 Glance 组件。
- 平板自适应布局和播放器手势。

### 阶段 5：更多来源与 TV

- 按真实需求增加 NFS、SFTP、DLNA。
- Jellyfin/Emby/Plex 使用独立媒体服务器接口。
- Android TV Compose、遥控器焦点和刷新率匹配。

## 16. Flutter 原型迁移策略

当前 Flutter 项目不直接删除，保留为可运行原型和行为参考：

- 复用服务器字段、表单规则和安全约束。
- 复用 SMB Range 映射、Seek 容差和格式化测试用例的业务含义。
- 将 Dart 接口逐一映射为 Kotlin 接口，但不机械翻译页面代码。
- 原生项目首先完成阶段 0 和阶段 1，达到 Flutter 版本的核心能力后再决定归档旧实现。
- 在原生核心链验证前，不同时维护两套完整 UI 功能。

## 17. 架构验收规则

新增一种文件协议时，应满足：

1. 新增一个协议适配实现和依赖装配。
2. 复用来源管理、浏览器、播放器和媒体库页面。
3. 不修改播放器引擎以识别具体协议。
4. 通过统一协议契约测试。
5. 通过协议专属真机测试。

如果新增 WebDAV 必须在多个页面编写 `if (type == WEBDAV)`，说明抽象失败。允许出现协议专属的配置字段和诊断页面，但浏览、索引和播放主流程必须依赖能力与公共模型。

## 18. 参考资料

- [AndroidX Media3 Player 与 MediaSession](https://developer.android.com/media/media3/session/player)
- [Media3 Compose UI](https://developer.android.com/media/media3/ui/compose)
- [Media3 视频 Surface](https://developer.android.com/media/media3/ui/surface)
- [Jetpack Compose Picture-in-Picture](https://developer.android.com/develop/ui/compose/system/picture-in-picture)
- [Jetpack Compose 性能](https://developer.android.com/develop/ui/compose/performance)
- [Compose for Android TV](https://developer.android.com/training/tv/playback/compose)
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
- [Infuse 产品能力参考](https://firecore.com/infuse)
