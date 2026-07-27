import 'models/remote_file_info.dart';
import 'models/remote_file_item.dart';
import 'remote_file_reader.dart';

abstract interface class RemoteFileSystem {
  Future<void> connect();
  Future<List<RemoteFileItem>> listDirectory(String path);
  Future<RemoteFileInfo> getFileInfo(String path);
  Future<RemoteFileReader> openRead(String path);
  Future<bool> exists(String path);
  Future<void> disconnect();
}
