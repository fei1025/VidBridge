import '../../../core/errors/app_failure.dart';
import '../../../core/storage/credential_store.dart';
import '../../../remote_file_system/remote_file_system.dart';
import '../../../remote_file_system/remote_file_system_factory.dart';
import '../../servers/domain/models/server_config.dart';
import '../../servers/domain/repositories/server_repository.dart';
import '../data/local_media_bridge.dart';

final class PlaybackService {
  PlaybackService(
    this._serverRepository,
    this._credentialStore,
    this._fileSystemFactory,
  );

  final ServerRepository _serverRepository;
  final CredentialStore _credentialStore;
  final RemoteFileSystemFactory _fileSystemFactory;

  Future<PlaybackSession> open({
    required String serverId,
    required String path,
    required String fileName,
  }) async {
    final server = await _serverRepository.findById(serverId);
    if (server == null) throw const FileNotFoundFailure('网络位置已被删除。');
    final password = await _passwordFor(server);
    final fileSystem = _fileSystemFactory.create(server, password: password);
    LocalMediaBridge? bridge;
    try {
      await fileSystem.connect();
      final reader = await fileSystem.openRead(path);
      try {
        bridge = await LocalMediaBridge.start(reader, fileName: fileName);
      } catch (_) {
        await reader.close();
        rethrow;
      }
      return PlaybackSession(fileSystem, bridge);
    } on AppFailure {
      await bridge?.close();
      await fileSystem.disconnect();
      rethrow;
    } catch (error) {
      await bridge?.close();
      await fileSystem.disconnect();
      throw PlaybackFailure('无法准备播放源。', error);
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

final class PlaybackSession {
  PlaybackSession(this._fileSystem, this._bridge);

  final RemoteFileSystem _fileSystem;
  final LocalMediaBridge _bridge;
  bool _closed = false;

  Uri get uri => _bridge.uri;

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    await _bridge.close();
    await _fileSystem.disconnect();
  }
}
