import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../domain/models/server_config.dart';
import '../providers/server_providers.dart';

class ServersPage extends ConsumerWidget {
  const ServersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final servers = ref.watch(serversProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('网络位置'),
            Text('SMB 服务器', style: TextStyle(fontSize: 12)),
          ],
        ),
      ),
      body: SafeArea(
        child: servers.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stackTrace) =>
              _ErrorState(onRetry: () => ref.invalidate(serversProvider)),
          data: (items) => items.isEmpty
              ? const _EmptyState()
              : RefreshIndicator(
                  onRefresh: () async => ref.invalidate(serversProvider),
                  child: ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                    itemCount: items.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, index) =>
                        _ServerCard(server: items[index]),
                  ),
                ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/servers/new'),
        icon: const Icon(Icons.add),
        label: const Text('添加服务器'),
      ),
    );
  }
}

class _ServerCard extends ConsumerWidget {
  const _ServerCard({required this.server});

  final ServerConfig server;

  @override
  Widget build(BuildContext context, WidgetRef ref) => Card(
    child: InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: () => ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('SMB 目录浏览将在下一阶段启用'))),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            const CircleAvatar(
              radius: 26,
              child: Icon(Icons.dns_outlined, size: 28),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    server.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text('${server.host}:${server.port}'),
                  Text(
                    server.shareName?.isNotEmpty == true
                        ? '共享：${server.shareName}'
                        : '共享：连接后选择',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 8),
                  const Row(
                    children: [
                      Icon(Icons.circle_outlined, size: 12),
                      SizedBox(width: 6),
                      Text('尚未测试连接', style: TextStyle(fontSize: 12)),
                    ],
                  ),
                ],
              ),
            ),
            PopupMenuButton<String>(
              onSelected: (value) async {
                if (value == 'edit') {
                  await context.push('/servers/${server.id}/edit');
                } else if (value == 'delete' && context.mounted) {
                  await _confirmDelete(context, ref);
                }
              },
              itemBuilder: (context) => const [
                PopupMenuItem(value: 'edit', child: Text('编辑')),
                PopupMenuItem(value: 'delete', child: Text('删除')),
              ],
            ),
          ],
        ),
      ),
    ),
  );

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除网络位置？'),
        content: Text('将删除“${server.name}”及其安全保存的密码。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(serverConfigServiceProvider).delete(server);
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('删除失败，请稍后重试。')));
      }
    }
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.video_library_outlined,
            size: 72,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 20),
          Text('还没有网络位置', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text(
            '添加 Windows、macOS 或 NAS 的 SMB 共享目录。',
            textAlign: TextAlign.center,
          ),
        ],
      ),
    ),
  );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.error_outline, size: 56),
        const SizedBox(height: 12),
        const Text('无法加载网络位置。'),
        const SizedBox(height: 12),
        FilledButton.tonal(onPressed: onRetry, child: const Text('重试')),
      ],
    ),
  );
}
