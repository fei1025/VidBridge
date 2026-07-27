import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/utils/formatters.dart';
import '../../../../remote_file_system/models/remote_file_item.dart';
import '../providers/file_browser_providers.dart';

class FileBrowserPage extends ConsumerWidget {
  const FileBrowserPage({required this.serverId, super.key});
  final String serverId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(fileBrowserControllerProvider(serverId));
    final controller = ref.read(
      fileBrowserControllerProvider(serverId).notifier,
    );
    return FileBrowserView(
      state: state,
      onRetry: controller.retry,
      onRefresh: () => controller.load(state.currentPath),
      onSearch: controller.search,
      onSort: controller.changeSort,
      onOpenDirectory: controller.load,
      onUp: controller.goUp,
      onOpenVideo: (item) {
        if (!item.isVideo) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('字幕文件需要在播放页中加载。')));
          return;
        }
        context.push(
          Uri(
            path: '/servers/$serverId/play',
            queryParameters: {'path': item.fullPath, 'name': item.name},
          ).toString(),
        );
      },
    );
  }
}

class FileBrowserView extends StatelessWidget {
  const FileBrowserView({
    required this.state,
    required this.onRetry,
    required this.onRefresh,
    required this.onSearch,
    required this.onSort,
    required this.onOpenDirectory,
    required this.onUp,
    required this.onOpenVideo,
    super.key,
  });

  final FileBrowserState state;
  final Future<void> Function() onRetry;
  final Future<void> Function() onRefresh;
  final ValueChanged<String> onSearch;
  final ValueChanged<FileSort> onSort;
  final ValueChanged<String> onOpenDirectory;
  final Future<void> Function() onUp;
  final ValueChanged<RemoteFileItem> onOpenVideo;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(state.server?.name ?? '网络目录'),
          if (state.server?.shareName case final share?)
            Text(share, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
      actions: [
        PopupMenuButton<FileSort>(
          tooltip: '排序',
          icon: const Icon(Icons.sort),
          onSelected: onSort,
          itemBuilder: (context) => const [
            PopupMenuItem(value: FileSort.name, child: Text('按名称排序')),
            PopupMenuItem(value: FileSort.modifiedAt, child: Text('按修改时间排序')),
            PopupMenuItem(value: FileSort.size, child: Text('按大小排序')),
          ],
        ),
      ],
    ),
    body: SafeArea(
      child: Column(
        children: [
          BrowserToolbar(
            path: state.currentPath,
            onSearch: onSearch,
            onOpenPath: onOpenDirectory,
          ),
          Expanded(child: _buildBody(context)),
        ],
      ),
    ),
  );

  Widget _buildBody(BuildContext context) {
    if (state.isLoading) {
      return const Center(
        key: ValueKey('file-browser-loading'),
        child: CircularProgressIndicator(),
      );
    }
    if (state.failure case final failure?) {
      return Center(
        key: const ValueKey('file-browser-error'),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 56),
            const SizedBox(height: 12),
            Text(failure.message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      );
    }
    return BrowserFileList(
      state: state,
      onRefresh: onRefresh,
      onUp: onUp,
      onOpenDirectory: onOpenDirectory,
      onOpenVideo: onOpenVideo,
    );
  }
}

class BrowserToolbar extends StatelessWidget {
  const BrowserToolbar({
    required this.path,
    required this.onSearch,
    required this.onOpenPath,
    super.key,
  });

  final String path;
  final ValueChanged<String> onSearch;
  final ValueChanged<String> onOpenPath;

  @override
  Widget build(BuildContext context) {
    final segments = path.split('/').where((part) => part.isNotEmpty).toList();
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      child: Column(
        children: [
          TextField(
            onChanged: onSearch,
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              hintText: '搜索当前目录',
              isDense: true,
            ),
          ),
          const SizedBox(height: 6),
          SizedBox(
            height: 38,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                TextButton(
                  onPressed: () => onOpenPath(''),
                  child: const Text('共享根目录'),
                ),
                for (var index = 0; index < segments.length; index++) ...[
                  const Center(child: Icon(Icons.chevron_right, size: 18)),
                  TextButton(
                    onPressed: () =>
                        onOpenPath(segments.take(index + 1).join('/')),
                    child: Text(segments[index]),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class BrowserFileList extends StatelessWidget {
  const BrowserFileList({
    required this.state,
    required this.onRefresh,
    required this.onUp,
    required this.onOpenDirectory,
    required this.onOpenVideo,
    super.key,
  });

  final FileBrowserState state;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onUp;
  final ValueChanged<String> onOpenDirectory;
  final ValueChanged<RemoteFileItem> onOpenVideo;

  @override
  Widget build(BuildContext context) {
    final items = state.visibleItems;
    if (items.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: const [
            SizedBox(height: 140),
            Icon(Icons.folder_off_outlined, size: 56),
            SizedBox(height: 12),
            Center(child: Text('这个目录中没有可显示的文件')),
          ],
        ),
      );
    }
    final hasParent = state.currentPath.isNotEmpty;
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: items.length + (hasParent ? 1 : 0),
        itemBuilder: (context, index) {
          if (hasParent && index == 0) {
            return ListTile(
              leading: const Icon(Icons.drive_folder_upload_outlined),
              title: const Text('返回上级目录'),
              onTap: onUp,
            );
          }
          final item = items[hasParent ? index - 1 : index];
          return FileTile(
            item: item,
            onTap: () => item.isDirectory
                ? onOpenDirectory(item.fullPath)
                : onOpenVideo(item),
          );
        },
      ),
    );
  }
}

class FileTile extends StatelessWidget {
  const FileTile({required this.item, required this.onTap, super.key});
  final RemoteFileItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(
      item.isDirectory
          ? Icons.folder_outlined
          : item.isSubtitle
          ? Icons.subtitles_outlined
          : Icons.movie_outlined,
      color: item.isDirectory ? Theme.of(context).colorScheme.primary : null,
    ),
    title: Text(item.name, maxLines: 2, overflow: TextOverflow.ellipsis),
    subtitle: Text(
      [
        if (!item.isDirectory) formatFileSize(item.size),
        formatModifiedTime(item.modifiedAt),
      ].where((value) => value.isNotEmpty).join(' · '),
    ),
    trailing: item.isDirectory ? const Icon(Icons.chevron_right) : null,
    onTap: onTap,
  );
}
