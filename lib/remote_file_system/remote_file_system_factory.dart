import '../features/servers/domain/models/server_config.dart';
import 'remote_file_system.dart';

abstract interface class RemoteFileSystemFactory {
  RemoteFileSystem create(ServerConfig server, {String? password});
}
