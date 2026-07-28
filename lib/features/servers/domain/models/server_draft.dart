class ServerDraft {
  const ServerDraft({
    required this.name,
    required this.host,
    required this.port,
    required this.protocol,
    required this.isAnonymous,
    this.shareName,
    this.username,
    this.password,
    this.initialPath,
  });

  final String name;
  final String host;
  final int port;
  final String protocol;
  final String? shareName;
  final String? username;
  final String? password;
  final String? initialPath;
  final bool isAnonymous;
}
