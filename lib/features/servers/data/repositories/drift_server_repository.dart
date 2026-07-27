import 'package:drift/drift.dart';

import '../../../../core/database/app_database.dart';
import '../../domain/models/server_config.dart';
import '../../domain/repositories/server_repository.dart';

final class DriftServerRepository implements ServerRepository {
  DriftServerRepository(this._database);

  final AppDatabase _database;

  @override
  Stream<List<ServerConfig>> watchAll() {
    final query = _database.select(_database.serverConfigs)
      ..orderBy([(table) => OrderingTerm.desc(table.updatedAt)]);
    return query.watch().map((rows) => rows.map(_toDomain).toList());
  }

  @override
  Future<ServerConfig?> findById(String id) async {
    final query = _database.select(_database.serverConfigs)
      ..where((table) => table.id.equals(id));
    final row = await query.getSingleOrNull();
    return row == null ? null : _toDomain(row);
  }

  @override
  Future<void> save(ServerConfig server) => _database
      .into(_database.serverConfigs)
      .insertOnConflictUpdate(
        ServerConfigsCompanion.insert(
          id: server.id,
          name: server.name,
          host: server.host,
          port: Value(server.port),
          protocol: Value(server.protocol),
          shareName: Value(server.shareName),
          username: Value(server.username),
          credentialId: server.credentialId,
          initialPath: Value(server.initialPath),
          isAnonymous: Value(server.isAnonymous),
          createdAt: server.createdAt,
          updatedAt: server.updatedAt,
          lastConnectedAt: Value(server.lastConnectedAt),
          lastVisitedPath: Value(server.lastVisitedPath),
        ),
      );

  @override
  Future<void> delete(String id) async {
    await (_database.delete(
      _database.serverConfigs,
    )..where((table) => table.id.equals(id))).go();
  }

  ServerConfig _toDomain(ServerConfigRow row) => ServerConfig(
    id: row.id,
    name: row.name,
    host: row.host,
    port: row.port,
    protocol: row.protocol,
    shareName: row.shareName,
    username: row.username,
    credentialId: row.credentialId,
    initialPath: row.initialPath,
    isAnonymous: row.isAnonymous,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    lastConnectedAt: row.lastConnectedAt,
    lastVisitedPath: row.lastVisitedPath,
  );
}
