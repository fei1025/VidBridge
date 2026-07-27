abstract final class ServerFormValidator {
  static String? required(String? value, String label) {
    if (value == null || value.trim().isEmpty) return '请输入$label';
    return null;
  }

  static String? host(String? value) {
    final requiredError = required(value, 'IP 地址或主机名');
    if (requiredError != null) return requiredError;
    final host = value!.trim();
    if (host.contains('://') || host.contains('/') || host.contains(r'\')) {
      return '只填写 IP 地址或主机名，不要包含协议或路径';
    }
    return null;
  }

  static String? port(String? value) {
    final port = int.tryParse(value ?? '');
    if (port == null || port < 1 || port > 65535) {
      return '端口必须是 1 到 65535 之间的数字';
    }
    return null;
  }
}
