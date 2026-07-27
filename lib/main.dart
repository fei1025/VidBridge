import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'core/logging/app_logger.dart';

void main() {
  AppLogger.initialize();
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
