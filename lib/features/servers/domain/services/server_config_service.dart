import 'package:uuid/uuid.dart';

import '../../../../core/storage/credential_store.dart';
import '../models/server_config.dart';
import '../models/server_draft.dart';
import '../repositories/server_repository.dart';

final class ServerConfigService {
  ServerConfigService(
    this._repository,
    this._credentialStore, [
    this._uuid = const Uuid(),
  ]);

  final ServerRepository _repository;
  final CredentialStore _credentialStore;
  final Uuid _uuid;

  Future<void> save(ServerDraft draft, {ServerConfig? existing}) async {
    final now = DateTime.now();
    final id = existing?.id ?? _uuid.v4();
    final credentialId = existing?.credentialId ?? _uuid.v4();
    final server = ServerConfig(
      id: id,
      name: draft.name.trim(),
      host: draft.host.trim(),
      port: draft.port,
      protocol: 'SMB',
      shareName: _emptyToNull(draft.shareName),
      username: draft.isAnonymous ? null : _emptyToNull(draft.username),
      credentialId: credentialId,
      initialPath: _emptyToNull(draft.initialPath),
      isAnonymous: draft.isAnonymous,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      lastConnectedAt: existing?.lastConnectedAt,
      lastVisitedPath: existing?.lastVisitedPath,
    );

    if (draft.isAnonymous) {
      await _credentialStore.deletePassword(credentialId);
    } else if (draft.password case final password? when password.isNotEmpty) {
      await _credentialStore.writePassword(credentialId, password);
    }
    await _repository.save(server);
  }

  Future<void> delete(ServerConfig server) async {
    await _repository.delete(server.id);
    await _credentialStore.deletePassword(server.credentialId);
  }

  String? _emptyToNull(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }
}
