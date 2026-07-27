# LanPlayer

LanPlayer 是一个以 Android 为第一目标平台的 Flutter 局域网视频播放器。当前已完成服务器管理、SMB2/SMB3 目录浏览，以及通过 VLC 播放 SMB 视频和 Seek 回验。

## 当前状态

已实现：

- Flutter Android 项目、Material 3 深浅色主题
- Riverpod 状态管理和 go_router 路由
- Drift/SQLite 服务器配置表
- 网络位置空状态、加载状态、错误状态和服务器卡片
- 添加、编辑、删除 SMB 服务器配置
- 服务器名称、主机和端口表单校验
- 匿名访问和用户名密码配置
- 密码通过 flutter_secure_storage 写入 Android Keystore 支持的安全存储
- SQLite 仅保存随机 credentialId，不保存密码
- Android 网络、网络状态和 Wi-Fi 状态权限
- 全局异常入口和不记录认证参数的日志基础设施
- 单元测试和 Widget 测试
- 基于 dart_smb2/libsmb2 的 SMB 2.02–3.1.1 认证和连接测试
- SMB 共享目录浏览、面包屑、返回上级、刷新和失败重试
- 文件名搜索、名称/时间/大小排序和文件夹优先
- 常见视频与字幕扩展名过滤
- 最近连接时间和最近访问目录持久化
- 基于 flutter_vlc_player/libVLC 的 Android 视频播放
- SMB 文件句柄随机读与本机回环 HTTP Range 播放桥接
- 进度条 Seek、前进 30 秒和实际播放位置回验

尚未实现：

- 音轨和字幕切换
- 播放历史、收藏、设置和本地视频

界面中的“尚未测试连接”表示尚未完成过真实认证。服务器菜单中的“测试连接”会连接共享并读取初始目录。

## 环境要求

- Flutter 3.44.6 或兼容的 stable 版本
- Dart 3.12 或更高版本
- Android SDK
- Android 7.0（API 24）或更高版本（dart_smb2 原生库要求）

## 启动项目

在 PowerShell 中执行：

    flutter pub get
    flutter pub run build_runner build
    flutter analyze
    flutter test
    flutter run

数据库表变更后需要重新运行 Drift 代码生成命令。

## 添加网络位置

1. 打开应用，点击“添加服务器”。
2. 填写便于识别的服务器名称。
3. 主机只填写 IP 或主机名，例如 192.168.1.10 或 nas.local，不要填写协议和目录。
4. 端口通常保持 445。
5. 共享名称填写 Windows/NAS 对外发布的共享名，例如 Videos，不是本机磁盘路径。
6. 初始目录是共享目录下的相对路径，例如 Movies/2026。
7. 匿名共享打开“匿名访问”；否则填写账号密码。

保存后可在服务器菜单中测试连接，或点击服务器卡片直接连接并浏览目录。当前 SMB 引擎只支持标准端口 445。

## Windows 共享准备

1. 在 Windows 中为目标文件夹启用“高级共享”。
2. 设置共享名称，并给用于 LanPlayer 登录的 Windows 用户授予共享权限和 NTFS 读取权限。
3. 确保网络配置为专用网络，并允许“文件和打印机共享”通过防火墙。
4. 保持 SMB 2/3 可用。不要为了兼容此应用启用过时的 SMB 1。
5. 在手机与电脑连接同一局域网后，使用电脑局域网 IP、端口 445、共享名称和 Windows 账号填写应用表单。

## NAS 连接准备

在 NAS 管理界面启用 SMB 服务并将最低协议设置为 SMB2、最高协议设置为 SMB3。创建仅有目标媒体目录读取权限的专用账号，并记录共享名称。不要把 NAS 管理员账号用于日常播放。

目录浏览和播放源已支持中文、空格和深层相对路径。点击视频即可进入 VLC 播放页；拖动进度条或点击“前进 30 秒”后，页面会在实际播放位置到达目标附近时显示“Seek 已验证”。请继续用真实 NAS 验证不同编码和 5GB 以上 MKV 的长时间播放稳定性。

## 安全说明

- 密码不进入 Drift 数据库。
- 日志接口不接收或输出密码、带密码 URI 和完整认证参数。
- Android 自动备份已关闭，避免加密数据恢复后与设备密钥不匹配。
- 删除服务器时会同时删除关联的安全凭据。

## 打包

调试 APK：

    flutter build apk --debug

发布前应创建自己的签名配置，再执行：

    flutter build appbundle --release

默认包名是占位值 com.example.lan_player。修改包名时需要同步调整：

- android/app/build.gradle.kts 中的 namespace 和 applicationId
- android/app/src/main/kotlin 下的包目录
- MainActivity.kt 的 package 声明

应用显示名称在 AndroidManifest.xml 的 android:label 中修改。

## 架构

项目遵循 Feature First 和 presentation/domain/data 分层。详情见 docs/architecture.md。

## 已知问题

- 已选用 dart_smb2 0.1.1（libsmb2），但尚未在本仓库环境的 Android 真机和真实 Windows/NAS 共享上做互操作验证。
- dart_smb2 当前不暴露自定义 SMB 端口，连接仅支持 445。
- 插件原生库支持 Android API 24 起，不再支持 Android 6.0。
- 自动化测试已验证 HTTP Range 到 SMB offset/length 的映射，真实 NAS 的吞吐、休眠重连和超大文件仍需真机互操作验证。
- 当前包名和发布签名均为开发占位配置。
- Android 构建暂时固定为 AGP 8.13.2 / Gradle 8.13：当前安全存储与 jni 传递依赖使用不同的 AGP 9 Kotlin 迁移方式。Flutter 会提示未来升级 Gradle；升级前需先验证这些插件均已支持 AGP 9 内置 Kotlin。
