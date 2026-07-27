class ServerConfig {
  const ServerConfig({
    required this.id,
    required this.name,
    required this.host,
    required this.port,
    required this.protocol,
    required this.credentialId,
    required this.isAnonymous,
    required this.createdAt,
    required this.updatedAt,
    this.shareName,
    this.username,
    this.initialPath,
    this.lastConnectedAt,
    this.lastVisitedPath,
  });

  final String id;
  final String name;
  final String host;
  final int port;
  final String protocol;
  final String? shareName;
  final String? username;
  final String credentialId;
  final String? initialPath;
  final bool isAnonymous;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? lastConnectedAt;
  final String? lastVisitedPath;
}
