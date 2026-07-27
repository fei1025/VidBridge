import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lan_player/features/servers/presentation/pages/server_form_page.dart';
import 'package:lan_player/features/servers/presentation/pages/servers_page.dart';
import 'package:lan_player/features/servers/presentation/providers/server_providers.dart';

void main() {
  testWidgets('server list shows a useful empty state', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          serversProvider.overrideWith((ref) => Stream.value(const [])),
        ],
        child: const MaterialApp(home: ServersPage()),
      ),
    );
    await tester.pump();

    expect(find.text('还没有网络位置'), findsOneWidget);
    expect(find.text('添加服务器'), findsOneWidget);
  });

  testWidgets('add server form validates required fields', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: ServerFormPage())),
    );

    await tester.drag(find.byType(ListView), const Offset(0, -1200));
    await tester.pumpAndSettle();
    await tester.tap(find.text('保存网络位置'));
    await tester.pump();
    await tester.drag(find.byType(ListView), const Offset(0, 1200));
    await tester.pumpAndSettle();

    expect(find.text('请输入服务器名称'), findsOneWidget);
    expect(find.text('请输入IP 地址或主机名'), findsOneWidget);
  });
}
