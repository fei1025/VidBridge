import '../../../../core/errors/app_failure.dart';
import '../../../../core/storage/credential_store.dart';
import '../../../../remote_file_system/models/remote_file_item.dart';
import '../../../../remote_file_system/remote_file_system.dart';
import '../../../../remote_file_system/remote_file_system_factory.dart';
import '../../../servers/domain/models/server_config.dart';
import '../../../servers/domain/repositories/server_repository.dart';

final class RemoteBrowserService {
  RemoteBrowserService(
    this._serverRepository,
    this._credentialStore,
    this._fileSystemFactory,
  );

  final ServerRepository _serverRepository;
  final CredentialStore _credentialStore;
  final RemoteFileSystemFactory _fileSystemFactory;

  Future<RemoteBrowserSession> open(String serverId) async {
    final server = await _serverRepository.findById(serverId);
    if (server == null) {
      throw const FileNotFoundFailure('网络位置已被删除。');
    }
    if (server.shareName?.trim().isEmpty ?? true) {
      throw const ConnectionFailure('请先编辑服务器并填写共享名称。');
    }
    final password = await _passwordFor(server);
    final fileSystem = _fileSystemFactory.create(server, password: password);
    try {
      await fileSystem.connect();
      final now = DateTime.now();
      final connected = server.copyWith(updatedAt: now, lastConnectedAt: now);
      await _serverRepository.save(connected);
      return RemoteBrowserSession(connected, fileSystem, _serverRepository);
    } catch (_) {
      await fileSystem.disconnect();
      rethrow;
    }
  }

  Future<void> testConnection(ServerConfig server) async {
    final password = await _passwordFor(server);
    final fileSystem = _fileSystemFactory.create(server, password: password);
    try {
      await fileSystem.connect();
      await fileSystem.listDirectory(server.initialPath ?? '');
      final now = DateTime.now();
      await _serverRepository.save(
        server.copyWith(updatedAt: now, lastConnectedAt: now),
      );
    } finally {
      await fileSystem.disconnect();
    }
  }

  Future<String?> _passwordFor(ServerConfig server) async {
    if (server.isAnonymous) return null;
    final password = await _credentialStore.readPassword(server.credentialId);
    if (password == null || password.isEmpty) {
      throw const AuthenticationFailure('未找到保存的密码，请重新编辑服务器。');
    }
    return password;
  }
}

final class RemoteBrowserSession {
  RemoteBrowserSession(this.server, this._fileSystem, this._repository);

  final ServerConfig server;
  final RemoteFileSystem _fileSystem;
  final ServerRepository _repository;

  Future<List<RemoteFileItem>> listDirectory(String path) async {
    final items = await _fileSystem.listDirectory(path);
    await _repository.save(
      server.copyWith(updatedAt: DateTime.now(), lastVisitedPath: path),
    );
    return items;
  }

  Future<void> close() => _fileSystem.disconnect();
}
