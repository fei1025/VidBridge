class RemoteFileItem {
  const RemoteFileItem({
    required this.name,
    required this.fullPath,
    required this.isDirectory,
    this.size,
    this.modifiedAt,
    this.extension,
  });

  final String name;
  final String fullPath;
  final bool isDirectory;
  final int? size;
  final DateTime? modifiedAt;
  final String? extension;

  bool get isVideo => !isDirectory && videoExtensions.contains(extension);
  bool get isSubtitle => !isDirectory && subtitleExtensions.contains(extension);
}

const videoExtensions = <String>{
  'mp4',
  'mkv',
  'avi',
  'mov',
  'wmv',
  'flv',
  'webm',
  'm4v',
  'ts',
  'm2ts',
  'mpeg',
  'mpg',
  '3gp',
};

const subtitleExtensions = <String>{'srt', 'ass', 'ssa', 'vtt', 'sub'};

String? fileExtension(String name) {
  final dot = name.lastIndexOf('.');
  if (dot <= 0 || dot == name.length - 1) return null;
  return name.substring(dot + 1).toLowerCase();
}
