# VidBridge 真机与 NAS 验收清单

本清单只记录真实设备、真实服务器才能证明的行为；JVM 测试和构建通过不等于这些项目已通过。

## 测试准备

- Android 设备：API 24+，优先 Android 15/16；授予通知权限。
- 来源：一个本地 SAF 文件夹、一个 SMB2/3 共享、一个 HTTPS WebDAV 目录。
- 可选网络来源：NFSv3 导出、SFTP 账号、局域网 DLNA MediaServer，或 Jellyfin、Emby、Plex API Token，用于验证媒体列表和服务器播放入口。
- 媒体：普通 H.264、H.265/高码率文件，外挂字幕，至少两集同剧集和两个质量版本。
- 网络：稳定 Wi-Fi、切换移动网络或断开/恢复 Wi-Fi 的条件。

## 播放与媒体库

| 项目 | 操作 | 通过标准 |
| --- | --- | --- |
| 三种来源 | 分别扫描并播放本地、SMB、WebDAV 视频 | 首帧出现，Seek 和暂停/继续正常 |
| 自动外挂字幕 | 在视频同目录放置同名 `.srt`/`.ass` 字幕后播放 | libVLC 自动出现字幕轨道；手动选择其他字幕后以手动字幕为准 |
| 增量扫描 | 同一来源连续扫描两次 | 数量不重复，第二次明显复用未变化文件 |
| 删除同步 | 远端删除一个视频后重新扫描 | 媒体库和质量版本中均消失 |
| 剧集聚合 | 扫描同剧集多季多集 | 海报墙按剧集显示，详情页能切换季和集 |
| 版本选择 | 同一内容放置不同质量文件 | 详情页列出版本并可分别播放 |
| 断点续播 | 播放超过 30 秒后退出再进入继续观看 | 从保存位置附近恢复 |

## 生命周期与网络

| 项目 | 操作 | 通过标准 |
| --- | --- | --- |
| 后台音频 | 播放后退桌面、锁屏、切换应用 | 音频持续，通知和锁屏控制可用 |
| 视频回连 | 播放时离开页面再返回 | 视频 Surface 重新绑定，不创建重复播放会话 |
| 断网恢复 | SMB/WebDAV 播放中断网，再恢复网络 | 错误提示可重试，恢复后继续；认证错误不循环重试 |
| 耳机拔出 | 播放中拔出耳机 | 自动暂停 |
| 系统回收 | 播放中回收应用进程，再打开应用 | 继续观看入口存在，位置可恢复 |
| 方向与 PiP | 横竖屏切换、进入/退出 PiP | 播放器无泄漏、无黑屏、进度继续保存 |
| Mini Player | 播放中返回媒体库或详情页 | 返回只退出全屏，不停止后台 libVLC 会话；底部显示当前标题和进度，可暂停/继续、回到播放器或停止，且不创建第二个 libVLC 实例 |
| 桌面组件 | 添加 VidBridge 播放控制组件，播放中观察并点击控制按钮 | 标题、状态和进度刷新；播放/暂停、停止和打开应用动作有效；组件不承载视频画面 |
| Android TV | TV 设备启动应用并使用遥控器 | Leanback 启动入口可见，首页/媒体库卡片可获得焦点并可进入播放；播放器自动获得焦点，D-pad 左右 Seek、确认键播放/暂停有效 |
| Plex | 使用 Plex Token 扫描电影库和剧集库 | 分区媒体列表进入统一媒体库，电影/剧集虚拟路径可交给 libVLC |
| SFTP | 使用 SSH 用户名/密码浏览和扫描目录 | 目录分页、文件属性、随机读取和 libVLC `sftp://` 播放入口正常 |
| NFSv3 | 使用服务器导出路径浏览和扫描目录 | AUTH_SYS 只读目录、属性、随机读取和 libVLC `nfs://` 播放入口正常 |
| DLNA | 与手机处于同一局域网，设备提供 ContentDirectory 服务 | SSDP 发现设备，SOAP Browse 分页显示目录，媒体资源 URL 可交给 libVLC 播放；记录厂商、鉴权和 Range 行为 |
| 离线下载 | 媒体详情点击下载，退到后台后暂停/恢复，再播放完成文件 | 系统下载通知持续显示，断点继续；未完成任务只保留 `.part` 临时文件，完整后才切换为可播放文件；删除同时清理两类文件 |
| Macrobenchmark | 在实体设备执行 `:benchmark:connectedBenchmarkAndroidTest` | 采集冷启动等性能指标；模拟器只用于验证测试链路，不作为性能结论 |

## 发布检查

- 最新 Release APK/AAB 已重新生成；APK 的 ZIP 条目通过 `zipalign -P 16`。
- 最终 APK 中 `arm64-v8a`、`x86_64` 的所有 ELF `LOAD` 段均为 `0x4000`（16 KB）；32 位 ABI 保留 4 KB 对齐，仅用于非 16 KB 设备。
- Release 仍未绑定用户正式签名密钥，当前产物不能直接作为 Play 正式发布包；签名、Play App Signing 和上传密钥需要在发布环境配置。
- Macrobenchmark 模块已接入，并在 API 36 模拟器执行 1 项冷启动测试通过；实体设备性能数据仍待采集。
- 使用 `:benchmark:connectedBenchmarkAndroidTest` build type 执行通过；默认 `connectedDebugAndroidTest` 的 Debuggable 失败是 Macrobenchmark 配置保护，不计入应用测试失败。

## 已完成的 API 36 模拟器验收（2026-07-29）

- 真实 Windows SMB 只读共享 `Anime` 已保存并完成扫描，媒体库发现 51 个视频。
- 从首页启动真实 SMB 视频，libVLC 加载视频 Surface 和字幕轨道，播放状态正常，实测进度 `00:18 / 23:40`。
- 强制停止应用进程后重新启动，自动恢复同一视频，实测进度 `00:11 / 23:40`，未出现 `AndroidRuntime` 或播放器崩溃。
- Room v5→v6、v6→v7、v7→v8 迁移、播放队列、播放列表顺序/删除/重命名、播放历史查询共 14 项 instrumentation 已通过（API 36 模拟器，2026-07-30）。
- 播放中进入 PiP 后，Activity 进入 Android `pinned` 任务，普通播放器控制层隐藏；前台通知仍显示“正在播放”，并提供“暂停”和“停止”动作。
- 播放中关闭再恢复模拟器 Wi-Fi，日志记录到 SMB socket 中断；网络恢复后同一视频继续播放，进度从约 `00:11` 推进到 `01:13 / 23:40`，通知控制仍有效。

以上结果证明了模拟器与真实 SMB 的扫描、播放和进程恢复链路；实体设备上的硬解、锁屏、厂商后台限制、断网恢复和 PiP 仍需按上表继续验收。

```powershell
Set-Location android
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease bundleRelease
```

安装前检查最终 APK：

```powershell
& "$env:ANDROID_HOME\build-tools\37.0.0\zipalign.exe" -c -P 16 -v 4 `
  app/build/outputs/apk/release/app-release-unsigned.apk
```
