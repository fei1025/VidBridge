# 架构说明

## 分层

    presentation
      -> domain interfaces and services
        -> data implementations
          -> Drift / flutter_secure_storage

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
        └── servers/
            ├── data/
            ├── domain/
            └── presentation/

只为本阶段实际使用的功能创建代码。file_browser、player、history、favorites 和 settings 会在对应阶段加入，避免产生未使用的空类。

## 服务器保存流程

ServerFormPage 生成 ServerDraft，ServerConfigService 负责规范化字段和协调两个存储：

- 公开配置通过 ServerRepository 保存到 Drift。
- 密码通过 CredentialStore 保存到平台安全存储。
- SQLite 行中仅保存随机 credentialId。

DriftServerRepository 是当前数据实现。后续页面仍只依赖 ServerRepository，可独立迁移数据库实现。

## 下一阶段边界

下一阶段会先定义 RemoteFileSystem 和远程文件模型，再根据实际插件源码选择 SMB2/SMB3 实现。服务器页面和播放器不会直接依赖 SMB 插件。只有完成真实认证测试后，界面才会显示连接成功并保存最近连接时间。
