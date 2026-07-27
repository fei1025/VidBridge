import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:lan_player/features/player/data/local_media_bridge.dart';
import 'package:lan_player/features/player/presentation/pages/player_page.dart';
import 'package:lan_player/remote_file_system/remote_file_reader.dart';

void main() {
  group('byte range parsing', () {
    test('parses bounded, open-ended, and suffix ranges', () {
      expect(ByteRange.parse('bytes=100-199', 1000)?.start, 100);
      expect(ByteRange.parse('bytes=100-199', 1000)?.length, 100);
      expect(ByteRange.parse('bytes=900-', 1000)?.end, 999);
      expect(ByteRange.parse('bytes=-50', 1000)?.start, 950);
    });

    test('rejects an unsatisfied range', () {
      expect(() => ByteRange.parse('bytes=1000-', 1000), throwsFormatException);
    });
  });

  test('local bridge maps HTTP Range to remote offset and length', () async {
    final bytes = Uint8List.fromList(
      List.generate(4096, (index) => index.remainder(256)),
    );
    final reader = _MemoryReader(bytes);
    final bridge = await LocalMediaBridge.start(reader, fileName: '测试.mp4');
    final client = HttpClient();
    addTearDown(() async {
      client.close(force: true);
      await bridge.close();
    });

    final request = await client.getUrl(bridge.uri);
    request.headers.set(HttpHeaders.rangeHeader, 'bytes=1024-2047');
    final response = await request.close();
    final body = await response.fold<List<int>>(
      <int>[],
      (all, chunk) => all..addAll(chunk),
    );

    expect(response.statusCode, HttpStatus.partialContent);
    expect(response.headers.value(HttpHeaders.acceptRangesHeader), 'bytes');
    expect(
      response.headers.value(HttpHeaders.contentRangeHeader),
      'bytes 1024-2047/4096',
    );
    expect(body, bytes.sublist(1024, 2048));
    expect(reader.reads.single, (offset: 1024, length: 1024));
  });

  test('seek verification accepts positions within tolerance', () {
    const validation = SeekValidation(Duration(minutes: 10));
    expect(validation.accepts(const Duration(minutes: 10, seconds: 2)), isTrue);
    expect(
      validation.accepts(const Duration(minutes: 9, seconds: 56)),
      isFalse,
    );
  });
}

final class _MemoryReader implements RemoteFileReader {
  _MemoryReader(this._bytes);

  final Uint8List _bytes;
  final List<({int offset, int length})> reads = [];

  @override
  int get size => _bytes.length;

  @override
  Future<Uint8List> read({required int offset, required int length}) async {
    reads.add((offset: offset, length: length));
    final end = (offset + length).clamp(0, size);
    return Uint8List.sublistView(_bytes, offset, end);
  }

  @override
  Future<void> close() async {}
}
