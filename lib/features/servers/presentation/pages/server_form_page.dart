import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../domain/models/server_config.dart';
import '../../domain/models/server_draft.dart';
import '../providers/server_providers.dart';
import '../validation/server_form_validator.dart';

class ServerFormPage extends ConsumerStatefulWidget {
  const ServerFormPage({super.key, this.serverId});

  final String? serverId;

  @override
  ConsumerState<ServerFormPage> createState() => _ServerFormPageState();
}

class _ServerFormPageState extends ConsumerState<ServerFormPage> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _host = TextEditingController();
  final _port = TextEditingController(text: '445');
  final _share = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _initialPath = TextEditingController();
  bool _anonymous = false;
  String _protocol = 'SMB12';
  bool _obscurePassword = true;
  bool _saving = false;
  ServerConfig? _existing;

  @override
  void dispose() {
    _name.dispose();
    _host.dispose();
    _port.dispose();
    _share.dispose();
    _username.dispose();
    _password.dispose();
    _initialPath.dispose();
    super.dispose();
  }

  void _populate(ServerConfig server) {
    if (_existing != null) return;
    _existing = server;
    _name.text = server.name;
    _host.text = server.host;
    _port.text = server.port.toString();
    _share.text = server.shareName ?? '';
    _username.text = server.username ?? '';
    _initialPath.text = server.initialPath ?? '';
    _anonymous = server.isAnonymous;
    _protocol = server.protocol == 'SMB3' ? 'SMB3' : 'SMB12';
    if (!server.isAnonymous) _loadSavedPassword(server);
  }

  Future<void> _loadSavedPassword(ServerConfig server) async {
    try {
      final password = await ref
          .read(credentialStoreProvider)
          .readPassword(server.credentialId);
      if (!mounted || _existing?.id != server.id) return;
      _password.text = password ?? '';
    } catch (_) {
      // Keep the password field empty; saving without a replacement keeps the
      // existing credential in ServerConfigService.
    }
  }

  @override
  Widget build(BuildContext context) {
    final id = widget.serverId;
    if (id != null) {
      final existing = ref.watch(serverByIdProvider(id));
      return existing.when(
        loading: () =>
            const Scaffold(body: Center(child: CircularProgressIndicator())),
        error: (error, stackTrace) => Scaffold(
          appBar: AppBar(),
          body: const Center(child: Text('无法加载服务器配置。')),
        ),
        data: (server) {
          if (server == null) {
            return Scaffold(
              appBar: AppBar(),
              body: const Center(child: Text('服务器配置不存在。')),
            );
          }
          _populate(server);
          return _buildScaffold(context);
        },
      );
    }
    return _buildScaffold(context);
  }

  Widget _buildScaffold(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(_existing == null ? '添加 SMB 服务器' : '编辑 SMB 服务器'),
    ),
    body: SafeArea(
      child: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            _sectionTitle(context, '网络位置'),
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(
                labelText: '服务器名称',
                hintText: '例如：客厅 NAS',
                prefixIcon: Icon(Icons.label_outline),
              ),
              textInputAction: TextInputAction.next,
              validator: (value) =>
                  ServerFormValidator.required(value, '服务器名称'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _host,
              decoration: const InputDecoration(
                labelText: 'IP 地址或主机名',
                hintText: '192.168.1.10',
                prefixIcon: Icon(Icons.lan_outlined),
              ),
              keyboardType: TextInputType.url,
              textInputAction: TextInputAction.next,
              validator: ServerFormValidator.host,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _port,
              decoration: const InputDecoration(
                labelText: '端口',
                prefixIcon: Icon(Icons.numbers),
              ),
              keyboardType: TextInputType.number,
              textInputAction: TextInputAction.next,
              validator: ServerFormValidator.port,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _protocol,
              decoration: const InputDecoration(
                labelText: 'SMB 协议版本',
                prefixIcon: Icon(Icons.settings_ethernet_outlined),
              ),
              items: const [
                DropdownMenuItem(value: 'SMB12', child: Text('SMB1/SMB2（smb_connect）')),
                DropdownMenuItem(value: 'SMB1', child: Text('仅 SMB1（smb_connect）')),
                DropdownMenuItem(value: 'SMB3', child: Text('SMB3（dart_smb2）')),
              ],
              onChanged: (value) {
                if (value != null) setState(() => _protocol = value);
              },
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _share,
              decoration: const InputDecoration(
                labelText: '共享名称（可选）',
                hintText: '例如：Videos',
                prefixIcon: Icon(Icons.folder_shared_outlined),
              ),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _initialPath,
              decoration: const InputDecoration(
                labelText: '初始目录（可选）',
                hintText: '例如：Movies/2026',
                prefixIcon: Icon(Icons.drive_file_move_outline),
              ),
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 24),
            _sectionTitle(context, '身份验证'),
            SwitchListTile(
              value: _anonymous,
              onChanged: (value) => setState(() => _anonymous = value),
              title: const Text('匿名访问'),
              subtitle: const Text('服务器允许无需账号密码访问时启用'),
              contentPadding: const EdgeInsets.symmetric(horizontal: 4),
            ),
            if (!_anonymous) ...[
              const SizedBox(height: 8),
              TextFormField(
                controller: _username,
                decoration: const InputDecoration(
                  labelText: '用户名（可选）',
                  prefixIcon: Icon(Icons.person_outline),
                ),
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _password,
                decoration: InputDecoration(
                  labelText: _existing == null ? '密码' : '密码（留空则保持不变）',
                  prefixIcon: const Icon(Icons.password_outlined),
                  suffixIcon: IconButton(
                    onPressed: () =>
                        setState(() => _obscurePassword = !_obscurePassword),
                    icon: Icon(
                      _obscurePassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                  ),
                ),
                obscureText: _obscurePassword,
                textInputAction: TextInputAction.done,
              ),
            ],
            const SizedBox(height: 28),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save_outlined),
              label: Text(_saving ? '正在保存…' : '保存网络位置'),
            ),
            const SizedBox(height: 12),
            Text(
              '密码使用 Android 安全存储保存，不会写入本地数据库或日志。'
              '连接测试将在 SMB 模块完成后启用。',
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    ),
  );

  Widget _sectionTitle(BuildContext context, String title) => Padding(
    padding: const EdgeInsets.only(left: 4, bottom: 10),
    child: Text(title, style: Theme.of(context).textTheme.titleMedium),
  );

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await ref
          .read(serverConfigServiceProvider)
          .save(
            ServerDraft(
              name: _name.text,
              host: _host.text,
              port: int.parse(_port.text),
              protocol: _protocol,
              shareName: _share.text,
              username: _username.text,
              password: _password.text,
              initialPath: _initialPath.text,
              isAnonymous: _anonymous,
            ),
            existing: _existing,
          );
      if (mounted) context.pop();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('保存失败，请稍后重试。')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}
