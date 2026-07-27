sealed class AppFailure implements Exception {
  const AppFailure(this.message, [this.cause]);

  final String message;
  final Object? cause;

  @override
  String toString() => message;
}

final class StorageFailure extends AppFailure {
  const StorageFailure(super.message, [super.cause]);
}

final class ValidationFailure extends AppFailure {
  const ValidationFailure(super.message, [super.cause]);
}

final class ConnectionFailure extends AppFailure {
  const ConnectionFailure(super.message, [super.cause]);
}

final class AuthenticationFailure extends AppFailure {
  const AuthenticationFailure(super.message, [super.cause]);
}

final class TimeoutFailure extends AppFailure {
  const TimeoutFailure(super.message, [super.cause]);
}

final class FileNotFoundFailure extends AppFailure {
  const FileNotFoundFailure(super.message, [super.cause]);
}

final class PermissionDeniedFailure extends AppFailure {
  const PermissionDeniedFailure(super.message, [super.cause]);
}

final class UnsupportedProtocolFailure extends AppFailure {
  const UnsupportedProtocolFailure(super.message, [super.cause]);
}

final class PlaybackFailure extends AppFailure {
  const PlaybackFailure(super.message, [super.cause]);
}
