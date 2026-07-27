import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/servers/presentation/pages/server_form_page.dart';
import '../features/servers/presentation/pages/servers_page.dart';

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
        ],
      ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});
