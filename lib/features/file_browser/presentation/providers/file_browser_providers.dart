import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/errors/app_failure.dart';
import '../../../../remote_file_system/models/remote_file_item.dart';
import '../../../../remote_file_system/remote_file_system_factory.dart';
import '../../../../remote_file_system/smb/smb_remote_file_system_factory.dart';
import '../../../servers/domain/models/server_config.dart';
import '../../../servers/presentation/providers/server_providers.dart';
import '../../domain/services/remote_browser_service.dart';

enum FileSort { name, modifiedAt, size }

final class FileBrowserState {
  const FileBrowserState({
    this.server,
    this.currentPath = '',
    this.items = const [],
    this.query = '',
    this.sort = FileSort.name,
    this.ascending = true,
    this.isLoading = true,
    this.failure,
  });

  final ServerConfig? server;
  final String currentPath;
  final List<RemoteFileItem> items;
  final String query;
  final FileSort sort;
  final bool ascending;
  final bool isLoading;
  final AppFailure? failure;

  List<RemoteFileItem> get visibleItems {
    final needle = query.trim().toLowerCase();
    final filtered = items.where((item) {
      if (!item.isDirectory && !item.isVideo && !item.isSubtitle) return false;
      return needle.isEmpty || item.name.toLowerCase().contains(needle);
    }).toList();
    filtered.sort((left, right) {
      if (left.isDirectory != right.isDirectory) {
        return left.isDirectory ? -1 : 1;
      }
      final comparison = switch (sort) {
        FileSort.name => left.name.toLowerCase().compareTo(
          right.name.toLowerCase(),
        ),
        FileSort.modifiedAt => (left.modifiedAt ?? DateTime(0)).compareTo(
          right.modifiedAt ?? DateTime(0),
        ),
        FileSort.size => (left.size ?? 0).compareTo(right.size ?? 0),
      };
      return ascending ? comparison : -comparison;
    });
    return filtered;
  }

  FileBrowserState copyWith({
    ServerConfig? server,
    String? currentPath,
    List<RemoteFileItem>? items,
    String? query,
    FileSort? sort,
    bool? ascending,
    bool? isLoading,
    AppFailure? failure,
    bool clearFailure = false,
  }) => FileBrowserState(
    server: server ?? this.server,
    currentPath: currentPath ?? this.currentPath,
    items: items ?? this.items,
    query: query ?? this.query,
    sort: sort ?? this.sort,
    ascending: ascending ?? this.ascending,
    isLoading: isLoading ?? this.isLoading,
    failure: clearFailure ? null : failure ?? this.failure,
  );
}

final remoteFileSystemFactoryProvider = Provider<RemoteFileSystemFactory>(
  (ref) => const SmbRemoteFileSystemFactory(),
);

final remoteBrowserServiceProvider = Provider<RemoteBrowserService>(
  (ref) => RemoteBrowserService(
    ref.watch(serverRepositoryProvider),
    ref.watch(credentialStoreProvider),
    ref.watch(remoteFileSystemFactoryProvider),
  ),
);

final fileBrowserControllerProvider = StateNotifierProvider.autoDispose
    .family<FileBrowserController, FileBrowserState, String>((ref, serverId) {
      return FileBrowserController(
        ref.watch(remoteBrowserServiceProvider),
        serverId,
      );
    });

final class FileBrowserController extends StateNotifier<FileBrowserState> {
  FileBrowserController(this._service, this._serverId)
    : super(const FileBrowserState()) {
    unawaited(_open());
  }

  final RemoteBrowserService _service;
  final String _serverId;
  RemoteBrowserSession? _session;

  Future<void> _open() async {
    state = state.copyWith(isLoading: true, clearFailure: true);
    try {
      final session = await _service.open(_serverId);
      _session = session;
      final initialPath =
          session.server.lastVisitedPath ?? session.server.initialPath ?? '';
      state = state.copyWith(server: session.server, currentPath: initialPath);
      await load(initialPath);
    } on AppFailure catch (failure) {
      state = state.copyWith(isLoading: false, failure: failure);
    } catch (error) {
      state = state.copyWith(
        isLoading: false,
        failure: ConnectionFailure('无法打开网络位置。', error),
      );
    }
  }

  Future<void> retry() async {
    if (_session == null) {
      await _open();
    } else {
      await load(state.currentPath);
    }
  }

  Future<void> load(String path) async {
    final session = _session;
    if (session == null) return;
    state = state.copyWith(
      currentPath: _normalize(path),
      isLoading: true,
      clearFailure: true,
    );
    try {
      final items = await session.listDirectory(state.currentPath);
      state = state.copyWith(items: items, isLoading: false);
    } on AppFailure catch (failure) {
      state = state.copyWith(isLoading: false, failure: failure);
    } catch (error) {
      state = state.copyWith(
        isLoading: false,
        failure: ConnectionFailure('无法加载目录，请稍后重试。', error),
      );
    }
  }

  void search(String query) => state = state.copyWith(query: query);

  void changeSort(FileSort sort) {
    state = state.copyWith(
      sort: sort,
      ascending: sort == state.sort ? !state.ascending : true,
    );
  }

  Future<void> goUp() {
    final segments = _segments(state.currentPath);
    if (segments.isNotEmpty) segments.removeLast();
    return load(segments.join('/'));
  }

  String _normalize(String path) =>
      _segments(path).where((segment) => segment != '.').join('/');

  List<String> _segments(String path) => path
      .replaceAll('\\', '/')
      .split('/')
      .where((segment) => segment.isNotEmpty)
      .toList();

  @override
  void dispose() {
    unawaited(_session?.close());
    super.dispose();
  }
}
