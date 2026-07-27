import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../errors/app_failure.dart';
import 'credential_store.dart';

final class SecureCredentialStore implements CredentialStore {
  SecureCredentialStore([FlutterSecureStorage? storage])
    : _storage = storage ?? const FlutterSecureStorage();

  static const _prefix = 'smb_credential_';
  final FlutterSecureStorage _storage;

  @override
  Future<void> writePassword(String credentialId, String password) async {
    try {
      await _storage.write(key: '$_prefix$credentialId', value: password);
    } catch (error) {
      throw StorageFailure('无法安全保存密码。', error);
    }
  }

  @override
  Future<String?> readPassword(String credentialId) async {
    try {
      return await _storage.read(key: '$_prefix$credentialId');
    } catch (error) {
      throw StorageFailure('无法读取已保存的密码。', error);
    }
  }

  @override
  Future<void> deletePassword(String credentialId) async {
    try {
      await _storage.delete(key: '$_prefix$credentialId');
    } catch (error) {
      throw StorageFailure('无法删除已保存的密码。', error);
    }
  }
}
