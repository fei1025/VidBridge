import '../../features/servers/domain/models/server_config.dart';
import '../remote_file_system.dart';
import '../remote_file_system_factory.dart';
import 'smb_remote_file_system.dart';
import 'smb_connect_remote_file_system.dart';

final class SmbRemoteFileSystemFactory implements RemoteFileSystemFactory {
  const SmbRemoteFileSystemFactory();

  @override
  RemoteFileSystem create(ServerConfig server, {String? password}) {
    if (server.protocol != 'SMB3') {
      return SmbConnectRemoteFileSystem(
        host: server.host,
        port: server.port,
        share: server.shareName ?? '',
        isAnonymous: server.isAnonymous,
        username: server.username,
        password: password,
        forceSmb1: server.protocol == 'SMB1',
      );
    }
    return SmbRemoteFileSystem(
        host: server.host,
        port: server.port,
        share: server.shareName ?? '',
        isAnonymous: server.isAnonymous,
        username: server.username,
        password: password,
      );
  }
}
