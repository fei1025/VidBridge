# 架构说明

## 分层

    presentation
      -> domain interfaces and services
        -> data implementations
          -> Drift / flutter_secure_storage / dart_smb2 / libVLC

页面只依赖 Riverpod provider 暴露的领域服务和仓储接口，不直接调用 Drift、SQLite 或 flutter_secure_storage。

## 当前目录

    lib/
    ├── app/
    │   ├── app.dart
    │   ├── router.dart
    │   └── theme.dart
    ├── core/
    │   ├── database/
    │   ├── errors/
    │   ├── logging/
    │   └── storage/
    └── features/
        ├── servers/
        ├── file_browser/
        └── player/
    └── remote_file_system/
        ├── models/
        └── smb/

只为当前实际使用的功能创建代码。history、favorites 和 settings 会在对应阶段加入，避免产生未使用的空类。

## 服务器保存流程

ServerFormPage 生成 ServerDraft，ServerConfigService 负责规范化字段和协调两个存储：

- 公开配置通过 ServerRepository 保存到 Drift。
- 密码通过 CredentialStore 保存到平台安全存储。
- SQLite 行中仅保存随机 credentialId。

DriftServerRepository 是当前数据实现。后续页面仍只依赖 ServerRepository，可独立迁移数据库实现。

## SMB 与目录浏览

RemoteFileSystem 定义连接、列目录、元数据、存在性检查、随机读取和断开操作。SmbRemoteFileSystem 是唯一依赖 dart_smb2 的实现，页面和状态控制器不导入插件。

RemoteBrowserService 从 CredentialStore 读取密码并创建会话。连接成功后更新 lastConnectedAt，每次成功列目录后更新 lastVisitedPath。FileBrowserController 管理加载、错误、搜索、排序和目录导航；视图只消费状态。

当前使用单 worker 的 Smb2Pool，避免 UI isolate 上执行同步 FFI，并减少浏览阶段不必要的并行连接。相对路径直接以 Unicode 字符串交给 libsmb2，不构造包含凭据的 SMB URI。

## VLC 播放与 Seek

PlaybackService 独立读取凭据并建立 SMB 会话，再通过打开的 SMB 文件句柄提供随机读。LocalMediaBridge 只绑定 `127.0.0.1`，以不可预测路径向 libVLC 提供 GET、HEAD 和单段 HTTP Range；用户名和密码不会进入 URI 或 VLC 日志。

播放器拖动进度条或前进 30 秒时调用 VLC Seek，并在 8 秒窗口内比较实际播放位置与目标位置。允许 3 秒容差，命中后在界面显示“Seek 已验证”。Range 解析、偏移映射和容差判断均有自动化测试。
