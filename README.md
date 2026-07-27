# LanPlayer

LanPlayer 是一个以 Android 为第一目标平台的 Flutter 局域网视频播放器。当前已完成项目骨架和 SMB 服务器配置管理；SMB2/SMB3 实际连接、文件浏览和 VLC 播放会在后续阶段逐步接入。

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

尚未实现：

- SMB2/SMB3 连接、认证测试和目录浏览
- VLC 播放、Seek、音轨和字幕切换
- 播放历史、收藏、设置和本地视频

界面中的“尚未测试连接”是明确的阶段状态，不代表连接成功。

## 环境要求

- Flutter 3.44.6 或兼容的 stable 版本
- Dart 3.12 或更高版本
- Android SDK
- Android 6.0（API 23）或更高版本

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

当前版本只保存配置，下一阶段完成 SMB 模块后才会执行真实连接测试。

## Windows 共享准备

1. 在 Windows 中为目标文件夹启用“高级共享”。
2. 设置共享名称，并给用于 LanPlayer 登录的 Windows 用户授予共享权限和 NTFS 读取权限。
3. 确保网络配置为专用网络，并允许“文件和打印机共享”通过防火墙。
4. 保持 SMB 2/3 可用。不要为了兼容此应用启用过时的 SMB 1。
5. 在手机与电脑连接同一局域网后，使用电脑局域网 IP、端口 445、共享名称和 Windows 账号填写应用表单。

## NAS 连接准备

在 NAS 管理界面启用 SMB 服务并将最低协议设置为 SMB2、最高协议设置为 SMB3。创建仅有目标媒体目录读取权限的专用账号，并记录共享名称。不要把 NAS 管理员账号用于日常播放。

SMB 实际连接代码尚未落地，因此当前不能据此判断 NAS 兼容性。接入后将重点验证中文路径、深目录和 5GB 以上 MKV 的读取与 Seek。

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

- 尚未选择并验证 SMB2/SMB3 Dart/Android 实现。
- 尚未在 Android 真机和真实 Windows/NAS 共享上做连接验证。
- “测试连接”和点击服务器进入目录将在下一阶段启用。
- 当前包名和发布签名均为开发占位配置。
- Android 构建暂时固定为 AGP 8.13.2 / Gradle 8.13：当前安全存储与 jni 传递依赖使用不同的 AGP 9 Kotlin 迁移方式。Flutter 会提示未来升级 Gradle；升级前需先验证这些插件均已支持 AGP 9 内置 Kotlin。
