import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lan_player/core/errors/app_failure.dart';
import 'package:lan_player/core/utils/formatters.dart';
import 'package:lan_player/features/file_browser/presentation/pages/file_browser_page.dart';
import 'package:lan_player/features/file_browser/presentation/providers/file_browser_providers.dart';
import 'package:lan_player/remote_file_system/models/remote_file_item.dart';

void main() {
  const noopFuture = _NoopCallbacks();

  group('remote file filtering and sorting', () {
    test(
      'recognizes video and subtitle extensions without case sensitivity',
      () {
        expect(fileExtension('电影.MKV'), 'mkv');
        expect(fileExtension('中文字幕.Ass'), 'ass');
        expect(fileExtension('README'), isNull);
        expect(
          const RemoteFileItem(
            name: '电影.MKV',
            fullPath: '中文 目录/电影.MKV',
            isDirectory: false,
            extension: 'mkv',
          ).isVideo,
          isTrue,
        );
      },
    );

    test('keeps folders first and hides unsupported files', () {
      const state = FileBrowserState(
        isLoading: false,
        items: [
          RemoteFileItem(
            name: 'z.mp4',
            fullPath: 'z.mp4',
            isDirectory: false,
            size: 2,
            extension: 'mp4',
          ),
          RemoteFileItem(
            name: 'notes.txt',
            fullPath: 'notes.txt',
            isDirectory: false,
            extension: 'txt',
          ),
          RemoteFileItem(
            name: 'A folder',
            fullPath: 'A folder',
            isDirectory: true,
          ),
        ],
      );
      expect(state.visibleItems.map((item) => item.name), [
        'A folder',
        'z.mp4',
      ]);
    });

    test('formats file sizes', () {
      expect(formatFileSize(0), '0 B');
      expect(formatFileSize(1024), '1.0 KB');
      expect(formatFileSize(5 * 1024 * 1024), '5.0 MB');
    });
  });

  testWidgets('file list shows loading state', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: FileBrowserView(
          state: const FileBrowserState(),
          onRetry: noopFuture.future,
          onRefresh: noopFuture.future,
          onSearch: noopFuture.string,
          onSort: noopFuture.sort,
          onOpenDirectory: noopFuture.string,
          onUp: noopFuture.future,
          onOpenVideo: noopFuture.file,
        ),
      ),
    );
    expect(find.byKey(const ValueKey('file-browser-loading')), findsOneWidget);
  });

  testWidgets('file list shows understandable error and retry', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: FileBrowserView(
          state: const FileBrowserState(
            isLoading: false,
            failure: ConnectionFailure('无法连接服务器，请检查 IP 地址和端口。'),
          ),
          onRetry: noopFuture.future,
          onRefresh: noopFuture.future,
          onSearch: noopFuture.string,
          onSort: noopFuture.sort,
          onOpenDirectory: noopFuture.string,
          onUp: noopFuture.future,
          onOpenVideo: noopFuture.file,
        ),
      ),
    );
    expect(find.byKey(const ValueKey('file-browser-error')), findsOneWidget);
    expect(find.text('重试'), findsOneWidget);
  });
}

class _NoopCallbacks {
  const _NoopCallbacks();
  Future<void> future() async {}
  void string(String _) {}
  void sort(FileSort _) {}
  void file(RemoteFileItem _) {}
}
