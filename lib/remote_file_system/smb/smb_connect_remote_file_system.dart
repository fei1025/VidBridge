import 'dart:io';
import 'dart:typed_data';

import 'package:smb_connect/smb_connect.dart';

import '../../core/errors/app_failure.dart';
import '../models/remote_file_info.dart';
import '../models/remote_file_item.dart';
import '../remote_file_reader.dart';
import '../remote_file_system.dart';

/// smb_connect backend for SMB1/SMB2. SMB3 remains on dart_smb2.
final class SmbConnectRemoteFileSystem implements RemoteFileSystem {
  SmbConnectRemoteFileSystem({
    required this.host,
    required this.port,
    required this.share,
    required this.isAnonymous,
    this.username,
    this.password,
    this.forceSmb1 = false,
  });

  final String host;
  final int port;
  final String share;
  final bool isAnonymous;
  final String? username;
  final String? password;
  final bool forceSmb1;
  SmbConnect? _connection;

  SmbConnect get _connected {
    final connection = _connection;
    if (connection == null) throw const ConnectionFailure('尚未连接 SMB 服务器。');
    return connection;
  }

  @override
  Future<void> connect() async {
    if (port != 445) {
      throw const UnsupportedProtocolFailure('SMB 当前仅支持 TCP 445 端口。');
    }
    try {
      _connection = await SmbConnect.connectAuth(
        host: host,
        domain: '',
        username: isAnonymous ? '' : username ?? '',
        password: isAnonymous ? '' : password ?? '',
        forceSmb1: forceSmb1,
      );
    } catch (error) {
      _connection = null;
      throw ConnectionFailure('SMB1/SMB2 连接失败，请检查共享和凭据。', error);
    }
  }

  @override
  Future<List<RemoteFileItem>> listDirectory(String path) async {
    try {
      final normalizedPath = _normalize(path);
      if (share.trim().isEmpty && normalizedPath.isEmpty) {
        final shares = await _connected.listShares();
        return shares
            .map(
              (file) => RemoteFileItem(
                name: file.name,
                fullPath: file.name,
                isDirectory: true,
                modifiedAt: null,
              ),
            )
            .toList(growable: false);
      }
      final folder = await _connected.file(_path(path));
      final files = await _connected.listFiles(folder);
      return files
          .where((file) => file.name != '.' && file.name != '..')
          .map(
            (file) => RemoteFileItem(
              name: file.name,
              fullPath: _join(path, file.name),
              isDirectory: file.isDirectory(),
              size: file.isDirectory() ? null : file.size,
              modifiedAt: DateTime.fromMillisecondsSinceEpoch(file.lastModified),
              extension: file.isDirectory() ? null : fileExtension(file.name),
            ),
          )
          .toList(growable: false);
    } catch (error) {
      throw ConnectionFailure('SMB1/SMB2 目录读取失败。', error);
    }
  }

  @override
  Future<RemoteFileInfo> getFileInfo(String path) async {
    try {
      final file = await _connected.file(_path(path));
      if (!file.isExists) throw const FileNotFoundFailure('文件或目录不存在。');
      return RemoteFileInfo(
        path: _normalize(path),
        isDirectory: file.isDirectory(),
        size: file.size,
        modifiedAt: DateTime.fromMillisecondsSinceEpoch(file.lastModified),
      );
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw ConnectionFailure('SMB1/SMB2 文件信息读取失败。', error);
    }
  }

  @override
  Future<bool> exists(String path) async {
    try {
      await getFileInfo(path);
      return true;
    } on AppFailure {
      return false;
    }
  }

  @override
  Future<RemoteFileReader> openRead(String path) async {
    try {
      final file = await _connected.file(_path(path));
      final raf = await _connected.open(file);
      return _SmbConnectRemoteFileReader(raf, file.size);
    } catch (error) {
      throw ConnectionFailure('SMB1/SMB2 文件打开失败。', error);
    }
  }

  @override
  Future<void> disconnect() async {
    final connection = _connection;
    _connection = null;
    if (connection != null) await connection.close();
  }

  String _path(String path) {
    final normalized = _normalize(path);
    if (share.trim().isEmpty) {
      return '/$normalized';
    }
    return normalized.isEmpty ? '/$share/' : '/$share/$normalized';
  }

  String _normalize(String path) => path
      .replaceAll('\\', '/')
      .split('/')
      .where((segment) => segment.isNotEmpty && segment != '.')
      .join('/');

  String _join(String parent, String name) {
    final normalized = _normalize(parent);
    return normalized.isEmpty ? name : '$normalized/$name';
  }
}

final class _SmbConnectRemoteFileReader implements RemoteFileReader {
  _SmbConnectRemoteFileReader(this._file, this.size);

  final RandomAccessFile _file;

  @override
  final int size;

  @override
  Future<Uint8List> read({required int offset, required int length}) async {
    await _file.setPosition(offset);
    return _file.read(length);
  }

  @override
  Future<void> close() => _file.close();
}
