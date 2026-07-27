import '../models/server_config.dart';

abstract interface class ServerRepository {
  Stream<List<ServerConfig>> watchAll();
  Future<ServerConfig?> findById(String id);
  Future<void> save(ServerConfig server);
  Future<void> delete(String id);
}
