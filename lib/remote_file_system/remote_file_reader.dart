import 'dart:typed_data';

abstract interface class RemoteFileReader {
  int get size;

  Future<Uint8List> read({required int offset, required int length});
  Future<void> close();
}
