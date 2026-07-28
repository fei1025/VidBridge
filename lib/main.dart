import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'core/logging/app_logger.dart';

void main() {
  AppLogger.initialize();
  FlutterError.onError = (details) {
    AppLogger.error(
      'Flutter 未捕获异常',
      error: details.exception,
      stackTrace: details.stack,
    );
  };
  ui.PlatformDispatcher.instance.onError = (error, stackTrace) {
    AppLogger.error(
      '平台异步未捕获异常',
      error: error,
      stackTrace: stackTrace,
    );
    return true;
  };
  runZonedGuarded(
    () {
      WidgetsFlutterBinding.ensureInitialized();
      runApp(const ProviderScope(child: LanPlayerApp()));
    },
    (error, stackTrace) => AppLogger.error(
      'Unhandled application error',
      error: error,
      stackTrace: stackTrace,
    ),
  );
}
