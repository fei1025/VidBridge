import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/servers/presentation/pages/server_form_page.dart';
import '../features/servers/presentation/pages/servers_page.dart';
import '../features/file_browser/presentation/pages/file_browser_page.dart';
import '../features/player/presentation/pages/player_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const ServersPage(),
        routes: [
          GoRoute(
            path: 'servers/new',
            builder: (context, state) => const ServerFormPage(),
          ),
          GoRoute(
            path: 'servers/:id/edit',
            builder: (context, state) =>
                ServerFormPage(serverId: state.pathParameters['id']),
          ),
          GoRoute(
            path: 'servers/:id/browse',
            builder: (context, state) =>
                FileBrowserPage(serverId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'servers/:id/play',
            builder: (context, state) => PlayerPage(
              serverId: state.pathParameters['id']!,
              path: state.uri.queryParameters['path'] ?? '',
              fileName: state.uri.queryParameters['name'] ?? '网络视频',
            ),
          ),
        ],
      ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});
