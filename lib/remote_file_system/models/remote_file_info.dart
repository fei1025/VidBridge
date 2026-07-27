class RemoteFileInfo {
  const RemoteFileInfo({
    required this.path,
    required this.isDirectory,
    required this.size,
    required this.modifiedAt,
  });

  final String path;
  final bool isDirectory;
  final int size;
  final DateTime modifiedAt;
}
