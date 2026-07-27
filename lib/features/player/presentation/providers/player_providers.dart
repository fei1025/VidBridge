import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../file_browser/presentation/providers/file_browser_providers.dart';
import '../../../servers/presentation/providers/server_providers.dart';
import '../../domain/playback_service.dart';

final playbackServiceProvider = Provider<PlaybackService>(
  (ref) => PlaybackService(
    ref.watch(serverRepositoryProvider),
    ref.watch(credentialStoreProvider),
    ref.watch(remoteFileSystemFactoryProvider),
  ),
);
