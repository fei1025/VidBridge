import 'dart:async';
import 'dart:io';
import 'dart:math';

import '../../../remote_file_system/remote_file_reader.dart';

final class ByteRange {
  const ByteRange(this.start, this.end);

  final int start;
  final int end;

  int get length => end - start + 1;

  static ByteRange? parse(String? header, int size) {
    if (header == null) return null;
    if (size <= 0 || !header.startsWith('bytes=') || header.contains(',')) {
      throw const FormatException('Unsupported byte range');
    }
    final value = header.substring(6).trim();
    final dash = value.indexOf('-');
    if (dash < 0) throw const FormatException('Invalid byte range');
    final startText = value.substring(0, dash).trim();
    final endText = value.substring(dash + 1).trim();
    if (startText.isEmpty) {
      final suffixLength = int.tryParse(endText);
      if (suffixLength == null || suffixLength <= 0) {
        throw const FormatException('Invalid suffix range');
      }
      final start = max(0, size - suffixLength);
      return ByteRange(start, size - 1);
    }
    final start = int.tryParse(startText);
    final requestedEnd = endText.isEmpty ? size - 1 : int.tryParse(endText);
    if (start == null || requestedEnd == null || start < 0 || start >= size) {
      throw const FormatException('Unsatisfied byte range');
    }
    final end = min(requestedEnd, size - 1);
    if (end < start) throw const FormatException('Invalid byte range');
    return ByteRange(start, end);
  }
}

final class LocalMediaBridge {
  LocalMediaBridge._(this._server, this._reader, this.uri);

  static const _chunkSize = 1024 * 1024;
  final HttpServer _server;
  final RemoteFileReader _reader;
  final Uri uri;
  bool _closed = false;

  static Future<LocalMediaBridge> start(
    RemoteFileReader reader, {
    required String fileName,
  }) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final token = _randomToken();
    final uri = Uri(
      scheme: 'http',
      host: InternetAddress.loopbackIPv4.address,
      port: server.port,
      pathSegments: [token, fileName],
    );
    final bridge = LocalMediaBridge._(server, reader, uri);
    server.listen(bridge._handleRequest);
    return bridge;
  }

  Future<void> _handleRequest(HttpRequest request) async {
    final response = request.response;
    response.bufferOutput = false;
    if (request.uri.path != uri.path) {
      response.statusCode = HttpStatus.notFound;
      await response.close();
      return;
    }
    if (request.method != 'GET' && request.method != 'HEAD') {
      response.statusCode = HttpStatus.methodNotAllowed;
      response.headers.set(HttpHeaders.allowHeader, 'GET, HEAD');
      await response.close();
      return;
    }

    response.headers.set(HttpHeaders.acceptRangesHeader, 'bytes');
    response.headers.contentType = _contentType(uri.pathSegments.last);
    late final ByteRange range;
    try {
      range =
          ByteRange.parse(
            request.headers.value(HttpHeaders.rangeHeader),
            _reader.size,
          ) ??
          ByteRange(0, _reader.size - 1);
    } on FormatException {
      response.statusCode = HttpStatus.requestedRangeNotSatisfiable;
      response.headers.set(
        HttpHeaders.contentRangeHeader,
        'bytes */${_reader.size}',
      );
      await response.close();
      return;
    }

    final isPartial = request.headers.value(HttpHeaders.rangeHeader) != null;
    response.statusCode = isPartial ? HttpStatus.partialContent : HttpStatus.ok;
    response.contentLength = range.length;
    if (isPartial) {
      response.headers.set(
        HttpHeaders.contentRangeHeader,
        'bytes ${range.start}-${range.end}/${_reader.size}',
      );
    }
    if (request.method == 'HEAD') {
      await response.close();
      return;
    }

    try {
      var offset = range.start;
      while (!_closed && offset <= range.end) {
        final requested = min(_chunkSize, range.end - offset + 1);
        final bytes = await _reader.read(offset: offset, length: requested);
        if (bytes.isEmpty) break;
        response.add(bytes);
        await response.flush();
        offset += bytes.length;
      }
      await response.close();
    } on Object {
      response.detachSocket().then((socket) => socket.destroy());
    }
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    await _server.close(force: true);
    await _reader.close();
  }

  static String _randomToken() {
    final random = Random.secure();
    return List.generate(
      24,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }

  static ContentType _contentType(String fileName) {
    final extension = fileName.split('.').last.toLowerCase();
    return switch (extension) {
      'mp4' || 'm4v' => ContentType('video', 'mp4'),
      'mkv' => ContentType('video', 'x-matroska'),
      'webm' => ContentType('video', 'webm'),
      'avi' => ContentType('video', 'x-msvideo'),
      'mpeg' || 'mpg' => ContentType('video', 'mpeg'),
      'ts' || 'm2ts' => ContentType('video', 'mp2t'),
      _ => ContentType.binary,
    };
  }
}
