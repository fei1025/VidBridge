import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/database/app_database.dart';
import '../../../../core/storage/credential_store.dart';
import '../../../../core/storage/secure_credential_store.dart';
import '../../data/repositories/drift_server_repository.dart';
import '../../domain/models/server_config.dart';
import '../../domain/repositories/server_repository.dart';
import '../../domain/services/server_config_service.dart';

final databaseProvider = Provider<AppDatabase>((ref) {
  final database = AppDatabase.defaults();
  ref.onDispose(database.close);
  return database;
});

final credentialStoreProvider = Provider<CredentialStore>(
  (ref) => SecureCredentialStore(),
);

final serverRepositoryProvider = Provider<ServerRepository>(
  (ref) => DriftServerRepository(ref.watch(databaseProvider)),
);

final serverConfigServiceProvider = Provider<ServerConfigService>(
  (ref) => ServerConfigService(
    ref.watch(serverRepositoryProvider),
    ref.watch(credentialStoreProvider),
  ),
);

final serversProvider = StreamProvider<List<ServerConfig>>(
  (ref) => ref.watch(serverRepositoryProvider).watchAll(),
);

final serverByIdProvider = FutureProvider.family<ServerConfig?, String>(
  (ref, id) => ref.watch(serverRepositoryProvider).findById(id),
);
