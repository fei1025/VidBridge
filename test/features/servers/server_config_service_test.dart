import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lan_player/core/database/app_database.dart';
import 'package:lan_player/core/storage/credential_store.dart';
import 'package:lan_player/features/servers/data/repositories/drift_server_repository.dart';
import 'package:lan_player/features/servers/domain/models/server_draft.dart';
import 'package:lan_player/features/servers/domain/services/server_config_service.dart';

void main() {
  late AppDatabase database;
  late MemoryCredentialStore credentials;

  setUp(() {
    database = AppDatabase(NativeDatabase.memory());
    credentials = MemoryCredentialStore();
  });

  tearDown(() => database.close());

  test('password is stored securely and never written to SQLite', () async {
    final service = ServerConfigService(
      DriftServerRepository(database),
      credentials,
    );

    await service.save(
      const ServerDraft(
        name: 'NAS',
        host: '192.168.1.8',
        port: 445,
        username: 'viewer',
        password: 'top-secret-password',
        isAnonymous: false,
      ),
    );

    final rows = await database
        .customSelect('SELECT * FROM server_configs')
        .get();
    expect(rows, hasLength(1));
    expect(rows.single.data.keys, isNot(contains('password')));
    expect(rows.single.data.values, isNot(contains('top-secret-password')));
    expect(credentials.passwords.values, contains('top-secret-password'));
  });
}

final class MemoryCredentialStore implements CredentialStore {
  final Map<String, String> passwords = {};

  @override
  Future<void> deletePassword(String credentialId) async {
    passwords.remove(credentialId);
  }

  @override
  Future<String?> readPassword(String credentialId) async =>
      passwords[credentialId];

  @override
  Future<void> writePassword(String credentialId, String password) async {
    passwords[credentialId] = password;
  }
}
