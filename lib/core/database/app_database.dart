import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'app_database.g.dart';

@DataClassName('ServerConfigRow')
class ServerConfigs extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  TextColumn get host => text()();
  IntColumn get port => integer().withDefault(const Constant(445))();
  TextColumn get protocol => text().withDefault(const Constant('SMB'))();
  TextColumn get shareName => text().nullable()();
  TextColumn get username => text().nullable()();
  TextColumn get credentialId => text()();
  TextColumn get initialPath => text().nullable()();
  BoolColumn get isAnonymous => boolean().withDefault(const Constant(false))();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime()();
  DateTimeColumn get lastConnectedAt => dateTime().nullable()();
  TextColumn get lastVisitedPath => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

@DriftDatabase(tables: [ServerConfigs])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.executor);

  AppDatabase.defaults() : super(driftDatabase(name: 'lan_player'));

  @override
  int get schemaVersion => 1;
}
