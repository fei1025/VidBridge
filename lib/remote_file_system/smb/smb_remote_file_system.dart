import 'dart:typed_data';

import 'package:dart_smb2/dart_smb2.dart';

import '../../core/errors/app_failure.dart';
import '../models/remote_file_info.dart';
import '../models/remote_file_item.dart';
import '../remote_file_system.dart';
import '../remote_file_reader.dart';

final class SmbRemoteFileSystem implements RemoteFileSystem {
  SmbRemoteFileSystem({
    required this.host,
    required this.port,
    required this.share,
    required this.isAnonymous,
    this.username,
    this.password,
  });

  final String host;
  final int port;
  final String share;
  final bool isAnonymous;
  final String? username;
  final String? password;
  Smb2Pool? _pool;

  Smb2Pool get _connectedPool {
    final pool = _pool;
    if (pool == null) throw const ConnectionFailure('尚未连接 SMB 服务器。');
    return pool;
  }

  @override
  Future<void> connect() async {
    if (port != 445) {
      throw const UnsupportedProtocolFailure('当前 SMB 引擎仅支持标准端口 445，请修改服务器端口。');
    }
    if (share.trim().isEmpty) {
      throw const ConnectionFailure('请先填写 SMB 共享名称。');
    }
    try {
      _pool = await Smb2Pool.connect(
        host: host,
        share: share,
        user: isAnonymous ? null : username,
        password: isAnonymous ? null : password,
        workers: 1,
        timeoutSeconds: 15,
        version: Smb2Version.any,
      );
      await _pool!.echo();
    } on Smb2Exception catch (error) {
      throw _mapFailure(error);
    } on UnsupportedError catch (error) {
      throw UnsupportedProtocolFailure('当前设备不支持 SMB2/SMB3。', error);
    } catch (error) {
      throw ConnectionFailure('无法连接服务器，请检查地址和网络。', error);
    }
  }

  @override
  Future<List<RemoteFileItem>> listDirectory(String path) async {
    try {
      final entries = await _connectedPool.listDirectory(_normalize(path));
      return entries
          .where((entry) => entry.name != '.' && entry.name != '..')
          .map(
            (entry) => RemoteFileItem(
              name: entry.name,
              fullPath: _join(path, entry.name),
              isDirectory: entry.isDirectory,
              size: entry.isDirectory ? null : entry.size,
              modifiedAt: entry.stat.modified.toLocal(),
              extension: entry.isDirectory ? null : fileExtension(entry.name),
            ),
          )
          .toList(growable: false);
    } on Smb2Exception catch (error) {
      throw _mapFailure(error);
    }
  }

  @override
  Future<RemoteFileInfo> getFileInfo(String path) async {
    try {
      final stat = await _connectedPool.stat(_normalize(path));
      return RemoteFileInfo(
        path: _normalize(path),
        isDirectory: stat.isDirectory,
        size: stat.size,
        modifiedAt: stat.modified.toLocal(),
      );
    } on Smb2Exception catch (error) {
      throw _mapFailure(error);
    }
  }

  @override
  Future<bool> exists(String path) async {
    try {
      return await _connectedPool.exists(_normalize(path));
    } on Smb2Exception catch (error) {
      throw _mapFailure(error);
    }
  }

  @override
  Future<RemoteFileReader> openRead(String path) async {
    try {
      final (handle, size) = await _connectedPool.openFileWithSize(
        _normalize(path),
      );
      return _SmbRemoteFileReader(_connectedPool, handle, size);
    } on Smb2Exception catch (error) {
      throw _mapFailure(error);
    }
  }

  @override
  Future<void> disconnect() async {
    final pool = _pool;
    _pool = null;
    if (pool != null) await pool.disconnect();
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

  AppFailure _mapFailure(Smb2Exception error) => switch (error.type) {
    Smb2ErrorType.auth => AuthenticationFailure('用户名或密码错误。', error),
    Smb2ErrorType.timeout => TimeoutFailure('连接服务器超时，请检查网络。', error),
    Smb2ErrorType.fileNotFound ||
    Smb2ErrorType.notADirectory => FileNotFoundFailure('目录不存在或已被移动。', error),
    Smb2ErrorType.accessDenied => PermissionDeniedFailure(
      '共享目录不存在或无权访问。',
      error,
    ),
    Smb2ErrorType.connection => ConnectionFailure(
      '无法连接服务器，请检查 IP 地址和端口。',
      error,
    ),
    _ => ConnectionFailure('SMB 操作失败，请稍后重试。', error),
  };
}

final class _SmbRemoteFileReader implements RemoteFileReader {
  _SmbRemoteFileReader(this._pool, this._handle, this.size);

  final Smb2Pool _pool;
  final Smb2PoolHandle _handle;

  @override
  final int size;

  @override
  Future<Uint8List> read({required int offset, required int length}) =>
      _pool.readFromHandle(_handle, offset: offset, length: length);

  @override
  Future<void> close() => _pool.closeHandle(_handle);
}
