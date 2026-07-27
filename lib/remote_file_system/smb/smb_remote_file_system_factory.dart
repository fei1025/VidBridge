import '../../features/servers/domain/models/server_config.dart';
import '../remote_file_system.dart';
import '../remote_file_system_factory.dart';
import 'smb_remote_file_system.dart';

final class SmbRemoteFileSystemFactory implements RemoteFileSystemFactory {
  const SmbRemoteFileSystemFactory();

  @override
  RemoteFileSystem create(ServerConfig server, {String? password}) =>
      SmbRemoteFileSystem(
        host: server.host,
        port: server.port,
        share: server.shareName ?? '',
        isAnonymous: server.isAnonymous,
        username: server.username,
        password: password,
      );
}
