import 'package:flutter_test/flutter_test.dart';
import 'package:lan_player/features/servers/presentation/validation/server_form_validator.dart';

void main() {
  group('ServerFormValidator', () {
    test('requires server name and host', () {
      expect(ServerFormValidator.required('', '服务器名称'), isNotNull);
      expect(ServerFormValidator.host('  '), isNotNull);
    });

    test('rejects protocol and path in host', () {
      expect(ServerFormValidator.host('smb://192.168.1.2'), isNotNull);
      expect(ServerFormValidator.host(r'192.168.1.2\share'), isNotNull);
      expect(ServerFormValidator.host('nas.local'), isNull);
    });

    test('accepts only valid TCP port range', () {
      expect(ServerFormValidator.port('0'), isNotNull);
      expect(ServerFormValidator.port('65536'), isNotNull);
      expect(ServerFormValidator.port('445'), isNull);
    });
  });
}
