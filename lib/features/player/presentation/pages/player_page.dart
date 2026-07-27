import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_vlc_player/flutter_vlc_player.dart';

import '../../../../core/errors/app_failure.dart';
import '../../domain/playback_service.dart';
import '../providers/player_providers.dart';

enum SeekValidationStatus { idle, pending, verified, failed }

final class SeekValidation {
  const SeekValidation(
    this.target, {
    this.tolerance = const Duration(seconds: 3),
  });

  final Duration target;
  final Duration tolerance;

  bool accepts(Duration actual) => (actual - target).abs() <= tolerance;
}

class PlayerPage extends ConsumerStatefulWidget {
  const PlayerPage({
    required this.serverId,
    required this.path,
    required this.fileName,
    super.key,
  });

  final String serverId;
  final String path;
  final String fileName;

  @override
  ConsumerState<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends ConsumerState<PlayerPage> {
  PlaybackSession? _session;
  VlcPlayerController? _controller;
  Object? _error;
  Duration? _dragPosition;
  SeekValidation? _pendingSeek;
  SeekValidationStatus _seekStatus = SeekValidationStatus.idle;
  Timer? _seekTimer;

  @override
  void initState() {
    super.initState();
    unawaited(_prepare());
  }

  Future<void> _prepare() async {
    try {
      final session = await ref
          .read(playbackServiceProvider)
          .open(
            serverId: widget.serverId,
            path: widget.path,
            fileName: widget.fileName,
          );
      if (!mounted) {
        await session.close();
        return;
      }
      final controller = VlcPlayerController.network(
        session.uri.toString(),
        hwAcc: HwAcc.full,
        autoPlay: true,
        options: VlcPlayerOptions(
          advanced: VlcAdvancedOptions([
            VlcAdvancedOptions.networkCaching(1500),
          ]),
          http: VlcHttpOptions([VlcHttpOptions.httpReconnect(true)]),
        ),
      );
      controller.addListener(_onPlayerChanged);
      setState(() {
        _session = session;
        _controller = controller;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    }
  }

  void _onPlayerChanged() {
    if (!mounted) return;
    final controller = _controller;
    final pending = _pendingSeek;
    if (controller != null &&
        pending != null &&
        pending.accepts(controller.value.position)) {
      _seekTimer?.cancel();
      _pendingSeek = null;
      _seekStatus = SeekValidationStatus.verified;
    }
    setState(() {});
  }

  Future<void> _seekTo(Duration target) async {
    final controller = _controller;
    if (controller == null) return;
    final duration = controller.value.duration;
    final bounded = duration > Duration.zero && target > duration
        ? duration
        : target;
    _seekTimer?.cancel();
    setState(() {
      _pendingSeek = SeekValidation(bounded);
      _seekStatus = SeekValidationStatus.pending;
      _dragPosition = null;
    });
    _seekTimer = Timer(const Duration(seconds: 8), () {
      if (!mounted || _pendingSeek == null) return;
      setState(() {
        _pendingSeek = null;
        _seekStatus = SeekValidationStatus.failed;
      });
    });
    try {
      await controller.seekTo(bounded);
    } catch (_) {
      _seekTimer?.cancel();
      if (mounted) {
        setState(() {
          _pendingSeek = null;
          _seekStatus = SeekValidationStatus.failed;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    return Scaffold(
      appBar: AppBar(title: Text(widget.fileName)),
      backgroundColor: Colors.black,
      body: _error != null
          ? _buildError(context)
          : controller == null
          ? const Center(child: CircularProgressIndicator())
          : ValueListenableBuilder<VlcPlayerValue>(
              valueListenable: controller,
              builder: (context, value, _) => Column(
                children: [
                  Expanded(
                    child: Center(
                      child: VlcPlayer(
                        controller: controller,
                        aspectRatio: value.aspectRatio,
                        placeholder: const CircularProgressIndicator(),
                      ),
                    ),
                  ),
                  _PlayerControls(
                    value: value,
                    dragPosition: _dragPosition,
                    seekStatus: _seekStatus,
                    onTogglePlay: value.isPlaying
                        ? controller.pause
                        : controller.play,
                    onSeekChanged: (position) =>
                        setState(() => _dragPosition = position),
                    onSeekEnd: _seekTo,
                    onForward: () =>
                        _seekTo(value.position + const Duration(seconds: 30)),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildError(BuildContext context) {
    final message = _error is AppFailure
        ? (_error! as AppFailure).message
        : '播放器初始化失败，请返回重试。';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(message, style: const TextStyle(color: Colors.white)),
      ),
    );
  }

  @override
  void dispose() {
    _seekTimer?.cancel();
    _controller?.removeListener(_onPlayerChanged);
    final controller = _controller;
    final session = _session;
    unawaited(() async {
      await controller?.dispose();
      await session?.close();
    }());
    super.dispose();
  }
}

class _PlayerControls extends StatelessWidget {
  const _PlayerControls({
    required this.value,
    required this.dragPosition,
    required this.seekStatus,
    required this.onTogglePlay,
    required this.onSeekChanged,
    required this.onSeekEnd,
    required this.onForward,
  });

  final VlcPlayerValue value;
  final Duration? dragPosition;
  final SeekValidationStatus seekStatus;
  final Future<void> Function() onTogglePlay;
  final ValueChanged<Duration> onSeekChanged;
  final ValueChanged<Duration> onSeekEnd;
  final VoidCallback onForward;

  @override
  Widget build(BuildContext context) {
    final durationMs = value.duration.inMilliseconds;
    final shown = dragPosition ?? value.position;
    final sliderValue = durationMs <= 0
        ? 0.0
        : shown.inMilliseconds.clamp(0, durationMs).toDouble();
    return SafeArea(
      top: false,
      child: ColoredBox(
        color: Colors.black87,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Slider(
                value: sliderValue,
                max: durationMs <= 0 ? 1 : durationMs.toDouble(),
                onChanged: durationMs <= 0
                    ? null
                    : (value) =>
                          onSeekChanged(Duration(milliseconds: value.round())),
                onChangeEnd: durationMs <= 0
                    ? null
                    : (value) =>
                          onSeekEnd(Duration(milliseconds: value.round())),
              ),
              Row(
                children: [
                  IconButton(
                    color: Colors.white,
                    onPressed: onTogglePlay,
                    icon: Icon(
                      value.isPlaying ? Icons.pause : Icons.play_arrow,
                    ),
                  ),
                  IconButton(
                    color: Colors.white,
                    tooltip: '前进 30 秒并验证 Seek',
                    onPressed: onForward,
                    icon: const Icon(Icons.forward_30),
                  ),
                  Text(
                    '${_formatDuration(shown)} / ${_formatDuration(value.duration)}',
                    style: const TextStyle(color: Colors.white),
                  ),
                  const Spacer(),
                  Text(
                    switch (seekStatus) {
                      SeekValidationStatus.idle => '',
                      SeekValidationStatus.pending => '正在验证 Seek…',
                      SeekValidationStatus.verified => 'Seek 已验证',
                      SeekValidationStatus.failed => 'Seek 验证失败',
                    },
                    style: TextStyle(
                      color: seekStatus == SeekValidationStatus.failed
                          ? Colors.redAccent
                          : Colors.lightGreenAccent,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _formatDuration(Duration value) {
    String two(int number) => number.toString().padLeft(2, '0');
    final hours = value.inHours;
    final minutes = value.inMinutes.remainder(60);
    final seconds = value.inSeconds.remainder(60);
    return hours > 0
        ? '$hours:${two(minutes)}:${two(seconds)}'
        : '${two(minutes)}:${two(seconds)}';
  }
}
