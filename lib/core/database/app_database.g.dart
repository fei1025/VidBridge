// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $ServerConfigsTable extends ServerConfigs
    with TableInfo<$ServerConfigsTable, ServerConfigRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ServerConfigsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _hostMeta = const VerificationMeta('host');
  @override
  late final GeneratedColumn<String> host = GeneratedColumn<String>(
    'host',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _portMeta = const VerificationMeta('port');
  @override
  late final GeneratedColumn<int> port = GeneratedColumn<int>(
    'port',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(445),
  );
  static const VerificationMeta _protocolMeta = const VerificationMeta(
    'protocol',
  );
  @override
  late final GeneratedColumn<String> protocol = GeneratedColumn<String>(
    'protocol',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('SMB'),
  );
  static const VerificationMeta _shareNameMeta = const VerificationMeta(
    'shareName',
  );
  @override
  late final GeneratedColumn<String> shareName = GeneratedColumn<String>(
    'share_name',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _usernameMeta = const VerificationMeta(
    'username',
  );
  @override
  late final GeneratedColumn<String> username = GeneratedColumn<String>(
    'username',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _credentialIdMeta = const VerificationMeta(
    'credentialId',
  );
  @override
  late final GeneratedColumn<String> credentialId = GeneratedColumn<String>(
    'credential_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _initialPathMeta = const VerificationMeta(
    'initialPath',
  );
  @override
  late final GeneratedColumn<String> initialPath = GeneratedColumn<String>(
    'initial_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isAnonymousMeta = const VerificationMeta(
    'isAnonymous',
  );
  @override
  late final GeneratedColumn<bool> isAnonymous = GeneratedColumn<bool>(
    'is_anonymous',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_anonymous" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lastConnectedAtMeta = const VerificationMeta(
    'lastConnectedAt',
  );
  @override
  late final GeneratedColumn<DateTime> lastConnectedAt =
      GeneratedColumn<DateTime>(
        'last_connected_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _lastVisitedPathMeta = const VerificationMeta(
    'lastVisitedPath',
  );
  @override
  late final GeneratedColumn<String> lastVisitedPath = GeneratedColumn<String>(
    'last_visited_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    name,
    host,
    port,
    protocol,
    shareName,
    username,
    credentialId,
    initialPath,
    isAnonymous,
    createdAt,
    updatedAt,
    lastConnectedAt,
    lastVisitedPath,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'server_configs';
  @override
  VerificationContext validateIntegrity(
    Insertable<ServerConfigRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('host')) {
      context.handle(
        _hostMeta,
        host.isAcceptableOrUnknown(data['host']!, _hostMeta),
      );
    } else if (isInserting) {
      context.missing(_hostMeta);
    }
    if (data.containsKey('port')) {
      context.handle(
        _portMeta,
        port.isAcceptableOrUnknown(data['port']!, _portMeta),
      );
    }
    if (data.containsKey('protocol')) {
      context.handle(
        _protocolMeta,
        protocol.isAcceptableOrUnknown(data['protocol']!, _protocolMeta),
      );
    }
    if (data.containsKey('share_name')) {
      context.handle(
        _shareNameMeta,
        shareName.isAcceptableOrUnknown(data['share_name']!, _shareNameMeta),
      );
    }
    if (data.containsKey('username')) {
      context.handle(
        _usernameMeta,
        username.isAcceptableOrUnknown(data['username']!, _usernameMeta),
      );
    }
    if (data.containsKey('credential_id')) {
      context.handle(
        _credentialIdMeta,
        credentialId.isAcceptableOrUnknown(
          data['credential_id']!,
          _credentialIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_credentialIdMeta);
    }
    if (data.containsKey('initial_path')) {
      context.handle(
        _initialPathMeta,
        initialPath.isAcceptableOrUnknown(
          data['initial_path']!,
          _initialPathMeta,
        ),
      );
    }
    if (data.containsKey('is_anonymous')) {
      context.handle(
        _isAnonymousMeta,
        isAnonymous.isAcceptableOrUnknown(
          data['is_anonymous']!,
          _isAnonymousMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    if (data.containsKey('last_connected_at')) {
      context.handle(
        _lastConnectedAtMeta,
        lastConnectedAt.isAcceptableOrUnknown(
          data['last_connected_at']!,
          _lastConnectedAtMeta,
        ),
      );
    }
    if (data.containsKey('last_visited_path')) {
      context.handle(
        _lastVisitedPathMeta,
        lastVisitedPath.isAcceptableOrUnknown(
          data['last_visited_path']!,
          _lastVisitedPathMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ServerConfigRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ServerConfigRow(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      host: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}host'],
      )!,
      port: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}port'],
      )!,
      protocol: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}protocol'],
      )!,
      shareName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}share_name'],
      ),
      username: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}username'],
      ),
      credentialId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}credential_id'],
      )!,
      initialPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}initial_path'],
      ),
      isAnonymous: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_anonymous'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      lastConnectedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}last_connected_at'],
      ),
      lastVisitedPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_visited_path'],
      ),
    );
  }

  @override
  $ServerConfigsTable createAlias(String alias) {
    return $ServerConfigsTable(attachedDatabase, alias);
  }
}

class ServerConfigRow extends DataClass implements Insertable<ServerConfigRow> {
  final String id;
  final String name;
  final String host;
  final int port;
  final String protocol;
  final String? shareName;
  final String? username;
  final String credentialId;
  final String? initialPath;
  final bool isAnonymous;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime? lastConnectedAt;
  final String? lastVisitedPath;
  const ServerConfigRow({
    required this.id,
    required this.name,
    required this.host,
    required this.port,
    required this.protocol,
    this.shareName,
    this.username,
    required this.credentialId,
    this.initialPath,
    required this.isAnonymous,
    required this.createdAt,
    required this.updatedAt,
    this.lastConnectedAt,
    this.lastVisitedPath,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['name'] = Variable<String>(name);
    map['host'] = Variable<String>(host);
    map['port'] = Variable<int>(port);
    map['protocol'] = Variable<String>(protocol);
    if (!nullToAbsent || shareName != null) {
      map['share_name'] = Variable<String>(shareName);
    }
    if (!nullToAbsent || username != null) {
      map['username'] = Variable<String>(username);
    }
    map['credential_id'] = Variable<String>(credentialId);
    if (!nullToAbsent || initialPath != null) {
      map['initial_path'] = Variable<String>(initialPath);
    }
    map['is_anonymous'] = Variable<bool>(isAnonymous);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    if (!nullToAbsent || lastConnectedAt != null) {
      map['last_connected_at'] = Variable<DateTime>(lastConnectedAt);
    }
    if (!nullToAbsent || lastVisitedPath != null) {
      map['last_visited_path'] = Variable<String>(lastVisitedPath);
    }
    return map;
  }

  ServerConfigsCompanion toCompanion(bool nullToAbsent) {
    return ServerConfigsCompanion(
      id: Value(id),
      name: Value(name),
      host: Value(host),
      port: Value(port),
      protocol: Value(protocol),
      shareName: shareName == null && nullToAbsent
          ? const Value.absent()
          : Value(shareName),
      username: username == null && nullToAbsent
          ? const Value.absent()
          : Value(username),
      credentialId: Value(credentialId),
      initialPath: initialPath == null && nullToAbsent
          ? const Value.absent()
          : Value(initialPath),
      isAnonymous: Value(isAnonymous),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      lastConnectedAt: lastConnectedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastConnectedAt),
      lastVisitedPath: lastVisitedPath == null && nullToAbsent
          ? const Value.absent()
          : Value(lastVisitedPath),
    );
  }

  factory ServerConfigRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ServerConfigRow(
      id: serializer.fromJson<String>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      host: serializer.fromJson<String>(json['host']),
      port: serializer.fromJson<int>(json['port']),
      protocol: serializer.fromJson<String>(json['protocol']),
      shareName: serializer.fromJson<String?>(json['shareName']),
      username: serializer.fromJson<String?>(json['username']),
      credentialId: serializer.fromJson<String>(json['credentialId']),
      initialPath: serializer.fromJson<String?>(json['initialPath']),
      isAnonymous: serializer.fromJson<bool>(json['isAnonymous']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      lastConnectedAt: serializer.fromJson<DateTime?>(json['lastConnectedAt']),
      lastVisitedPath: serializer.fromJson<String?>(json['lastVisitedPath']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'name': serializer.toJson<String>(name),
      'host': serializer.toJson<String>(host),
      'port': serializer.toJson<int>(port),
      'protocol': serializer.toJson<String>(protocol),
      'shareName': serializer.toJson<String?>(shareName),
      'username': serializer.toJson<String?>(username),
      'credentialId': serializer.toJson<String>(credentialId),
      'initialPath': serializer.toJson<String?>(initialPath),
      'isAnonymous': serializer.toJson<bool>(isAnonymous),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'lastConnectedAt': serializer.toJson<DateTime?>(lastConnectedAt),
      'lastVisitedPath': serializer.toJson<String?>(lastVisitedPath),
    };
  }

  ServerConfigRow copyWith({
    String? id,
    String? name,
    String? host,
    int? port,
    String? protocol,
    Value<String?> shareName = const Value.absent(),
    Value<String?> username = const Value.absent(),
    String? credentialId,
    Value<String?> initialPath = const Value.absent(),
    bool? isAnonymous,
    DateTime? createdAt,
    DateTime? updatedAt,
    Value<DateTime?> lastConnectedAt = const Value.absent(),
    Value<String?> lastVisitedPath = const Value.absent(),
  }) => ServerConfigRow(
    id: id ?? this.id,
    name: name ?? this.name,
    host: host ?? this.host,
    port: port ?? this.port,
    protocol: protocol ?? this.protocol,
    shareName: shareName.present ? shareName.value : this.shareName,
    username: username.present ? username.value : this.username,
    credentialId: credentialId ?? this.credentialId,
    initialPath: initialPath.present ? initialPath.value : this.initialPath,
    isAnonymous: isAnonymous ?? this.isAnonymous,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    lastConnectedAt: lastConnectedAt.present
        ? lastConnectedAt.value
        : this.lastConnectedAt,
    lastVisitedPath: lastVisitedPath.present
        ? lastVisitedPath.value
        : this.lastVisitedPath,
  );
  ServerConfigRow copyWithCompanion(ServerConfigsCompanion data) {
    return ServerConfigRow(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      host: data.host.present ? data.host.value : this.host,
      port: data.port.present ? data.port.value : this.port,
      protocol: data.protocol.present ? data.protocol.value : this.protocol,
      shareName: data.shareName.present ? data.shareName.value : this.shareName,
      username: data.username.present ? data.username.value : this.username,
      credentialId: data.credentialId.present
          ? data.credentialId.value
          : this.credentialId,
      initialPath: data.initialPath.present
          ? data.initialPath.value
          : this.initialPath,
      isAnonymous: data.isAnonymous.present
          ? data.isAnonymous.value
          : this.isAnonymous,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      lastConnectedAt: data.lastConnectedAt.present
          ? data.lastConnectedAt.value
          : this.lastConnectedAt,
      lastVisitedPath: data.lastVisitedPath.present
          ? data.lastVisitedPath.value
          : this.lastVisitedPath,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ServerConfigRow(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('host: $host, ')
          ..write('port: $port, ')
          ..write('protocol: $protocol, ')
          ..write('shareName: $shareName, ')
          ..write('username: $username, ')
          ..write('credentialId: $credentialId, ')
          ..write('initialPath: $initialPath, ')
          ..write('isAnonymous: $isAnonymous, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('lastConnectedAt: $lastConnectedAt, ')
          ..write('lastVisitedPath: $lastVisitedPath')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    name,
    host,
    port,
    protocol,
    shareName,
    username,
    credentialId,
    initialPath,
    isAnonymous,
    createdAt,
    updatedAt,
    lastConnectedAt,
    lastVisitedPath,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ServerConfigRow &&
          other.id == this.id &&
          other.name == this.name &&
          other.host == this.host &&
          other.port == this.port &&
          other.protocol == this.protocol &&
          other.shareName == this.shareName &&
          other.username == this.username &&
          other.credentialId == this.credentialId &&
          other.initialPath == this.initialPath &&
          other.isAnonymous == this.isAnonymous &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.lastConnectedAt == this.lastConnectedAt &&
          other.lastVisitedPath == this.lastVisitedPath);
}

class ServerConfigsCompanion extends UpdateCompanion<ServerConfigRow> {
  final Value<String> id;
  final Value<String> name;
  final Value<String> host;
  final Value<int> port;
  final Value<String> protocol;
  final Value<String?> shareName;
  final Value<String?> username;
  final Value<String> credentialId;
  final Value<String?> initialPath;
  final Value<bool> isAnonymous;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<DateTime?> lastConnectedAt;
  final Value<String?> lastVisitedPath;
  final Value<int> rowid;
  const ServerConfigsCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.host = const Value.absent(),
    this.port = const Value.absent(),
    this.protocol = const Value.absent(),
    this.shareName = const Value.absent(),
    this.username = const Value.absent(),
    this.credentialId = const Value.absent(),
    this.initialPath = const Value.absent(),
    this.isAnonymous = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.lastConnectedAt = const Value.absent(),
    this.lastVisitedPath = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ServerConfigsCompanion.insert({
    required String id,
    required String name,
    required String host,
    this.port = const Value.absent(),
    this.protocol = const Value.absent(),
    this.shareName = const Value.absent(),
    this.username = const Value.absent(),
    required String credentialId,
    this.initialPath = const Value.absent(),
    this.isAnonymous = const Value.absent(),
    required DateTime createdAt,
    required DateTime updatedAt,
    this.lastConnectedAt = const Value.absent(),
    this.lastVisitedPath = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       name = Value(name),
       host = Value(host),
       credentialId = Value(credentialId),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<ServerConfigRow> custom({
    Expression<String>? id,
    Expression<String>? name,
    Expression<String>? host,
    Expression<int>? port,
    Expression<String>? protocol,
    Expression<String>? shareName,
    Expression<String>? username,
    Expression<String>? credentialId,
    Expression<String>? initialPath,
    Expression<bool>? isAnonymous,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<DateTime>? lastConnectedAt,
    Expression<String>? lastVisitedPath,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (host != null) 'host': host,
      if (port != null) 'port': port,
      if (protocol != null) 'protocol': protocol,
      if (shareName != null) 'share_name': shareName,
      if (username != null) 'username': username,
      if (credentialId != null) 'credential_id': credentialId,
      if (initialPath != null) 'initial_path': initialPath,
      if (isAnonymous != null) 'is_anonymous': isAnonymous,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (lastConnectedAt != null) 'last_connected_at': lastConnectedAt,
      if (lastVisitedPath != null) 'last_visited_path': lastVisitedPath,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ServerConfigsCompanion copyWith({
    Value<String>? id,
    Value<String>? name,
    Value<String>? host,
    Value<int>? port,
    Value<String>? protocol,
    Value<String?>? shareName,
    Value<String?>? username,
    Value<String>? credentialId,
    Value<String?>? initialPath,
    Value<bool>? isAnonymous,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<DateTime?>? lastConnectedAt,
    Value<String?>? lastVisitedPath,
    Value<int>? rowid,
  }) {
    return ServerConfigsCompanion(
      id: id ?? this.id,
      name: name ?? this.name,
      host: host ?? this.host,
      port: port ?? this.port,
      protocol: protocol ?? this.protocol,
      shareName: shareName ?? this.shareName,
      username: username ?? this.username,
      credentialId: credentialId ?? this.credentialId,
      initialPath: initialPath ?? this.initialPath,
      isAnonymous: isAnonymous ?? this.isAnonymous,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      lastConnectedAt: lastConnectedAt ?? this.lastConnectedAt,
      lastVisitedPath: lastVisitedPath ?? this.lastVisitedPath,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (host.present) {
      map['host'] = Variable<String>(host.value);
    }
    if (port.present) {
      map['port'] = Variable<int>(port.value);
    }
    if (protocol.present) {
      map['protocol'] = Variable<String>(protocol.value);
    }
    if (shareName.present) {
      map['share_name'] = Variable<String>(shareName.value);
    }
    if (username.present) {
      map['username'] = Variable<String>(username.value);
    }
    if (credentialId.present) {
      map['credential_id'] = Variable<String>(credentialId.value);
    }
    if (initialPath.present) {
      map['initial_path'] = Variable<String>(initialPath.value);
    }
    if (isAnonymous.present) {
      map['is_anonymous'] = Variable<bool>(isAnonymous.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (lastConnectedAt.present) {
      map['last_connected_at'] = Variable<DateTime>(lastConnectedAt.value);
    }
    if (lastVisitedPath.present) {
      map['last_visited_path'] = Variable<String>(lastVisitedPath.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ServerConfigsCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('host: $host, ')
          ..write('port: $port, ')
          ..write('protocol: $protocol, ')
          ..write('shareName: $shareName, ')
          ..write('username: $username, ')
          ..write('credentialId: $credentialId, ')
          ..write('initialPath: $initialPath, ')
          ..write('isAnonymous: $isAnonymous, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('lastConnectedAt: $lastConnectedAt, ')
          ..write('lastVisitedPath: $lastVisitedPath, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $ServerConfigsTable serverConfigs = $ServerConfigsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [serverConfigs];
}

typedef $$ServerConfigsTableCreateCompanionBuilder =
    ServerConfigsCompanion Function({
      required String id,
      required String name,
      required String host,
      Value<int> port,
      Value<String> protocol,
      Value<String?> shareName,
      Value<String?> username,
      required String credentialId,
      Value<String?> initialPath,
      Value<bool> isAnonymous,
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<DateTime?> lastConnectedAt,
      Value<String?> lastVisitedPath,
      Value<int> rowid,
    });
typedef $$ServerConfigsTableUpdateCompanionBuilder =
    ServerConfigsCompanion Function({
      Value<String> id,
      Value<String> name,
      Value<String> host,
      Value<int> port,
      Value<String> protocol,
      Value<String?> shareName,
      Value<String?> username,
      Value<String> credentialId,
      Value<String?> initialPath,
      Value<bool> isAnonymous,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<DateTime?> lastConnectedAt,
      Value<String?> lastVisitedPath,
      Value<int> rowid,
    });

class $$ServerConfigsTableFilterComposer
    extends Composer<_$AppDatabase, $ServerConfigsTable> {
  $$ServerConfigsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get host => $composableBuilder(
    column: $table.host,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get port => $composableBuilder(
    column: $table.port,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get protocol => $composableBuilder(
    column: $table.protocol,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get shareName => $composableBuilder(
    column: $table.shareName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get username => $composableBuilder(
    column: $table.username,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get credentialId => $composableBuilder(
    column: $table.credentialId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get initialPath => $composableBuilder(
    column: $table.initialPath,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isAnonymous => $composableBuilder(
    column: $table.isAnonymous,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get lastConnectedAt => $composableBuilder(
    column: $table.lastConnectedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastVisitedPath => $composableBuilder(
    column: $table.lastVisitedPath,
    builder: (column) => ColumnFilters(column),
  );
}

class $$ServerConfigsTableOrderingComposer
    extends Composer<_$AppDatabase, $ServerConfigsTable> {
  $$ServerConfigsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get host => $composableBuilder(
    column: $table.host,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get port => $composableBuilder(
    column: $table.port,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get protocol => $composableBuilder(
    column: $table.protocol,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get shareName => $composableBuilder(
    column: $table.shareName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get username => $composableBuilder(
    column: $table.username,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get credentialId => $composableBuilder(
    column: $table.credentialId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get initialPath => $composableBuilder(
    column: $table.initialPath,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isAnonymous => $composableBuilder(
    column: $table.isAnonymous,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get lastConnectedAt => $composableBuilder(
    column: $table.lastConnectedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastVisitedPath => $composableBuilder(
    column: $table.lastVisitedPath,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$ServerConfigsTableAnnotationComposer
    extends Composer<_$AppDatabase, $ServerConfigsTable> {
  $$ServerConfigsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get host =>
      $composableBuilder(column: $table.host, builder: (column) => column);

  GeneratedColumn<int> get port =>
      $composableBuilder(column: $table.port, builder: (column) => column);

  GeneratedColumn<String> get protocol =>
      $composableBuilder(column: $table.protocol, builder: (column) => column);

  GeneratedColumn<String> get shareName =>
      $composableBuilder(column: $table.shareName, builder: (column) => column);

  GeneratedColumn<String> get username =>
      $composableBuilder(column: $table.username, builder: (column) => column);

  GeneratedColumn<String> get credentialId => $composableBuilder(
    column: $table.credentialId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get initialPath => $composableBuilder(
    column: $table.initialPath,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isAnonymous => $composableBuilder(
    column: $table.isAnonymous,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get lastConnectedAt => $composableBuilder(
    column: $table.lastConnectedAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get lastVisitedPath => $composableBuilder(
    column: $table.lastVisitedPath,
    builder: (column) => column,
  );
}

class $$ServerConfigsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $ServerConfigsTable,
          ServerConfigRow,
          $$ServerConfigsTableFilterComposer,
          $$ServerConfigsTableOrderingComposer,
          $$ServerConfigsTableAnnotationComposer,
          $$ServerConfigsTableCreateCompanionBuilder,
          $$ServerConfigsTableUpdateCompanionBuilder,
          (
            ServerConfigRow,
            BaseReferences<_$AppDatabase, $ServerConfigsTable, ServerConfigRow>,
          ),
          ServerConfigRow,
          PrefetchHooks Function()
        > {
  $$ServerConfigsTableTableManager(_$AppDatabase db, $ServerConfigsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ServerConfigsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ServerConfigsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ServerConfigsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> host = const Value.absent(),
                Value<int> port = const Value.absent(),
                Value<String> protocol = const Value.absent(),
                Value<String?> shareName = const Value.absent(),
                Value<String?> username = const Value.absent(),
                Value<String> credentialId = const Value.absent(),
                Value<String?> initialPath = const Value.absent(),
                Value<bool> isAnonymous = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<DateTime?> lastConnectedAt = const Value.absent(),
                Value<String?> lastVisitedPath = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ServerConfigsCompanion(
                id: id,
                name: name,
                host: host,
                port: port,
                protocol: protocol,
                shareName: shareName,
                username: username,
                credentialId: credentialId,
                initialPath: initialPath,
                isAnonymous: isAnonymous,
                createdAt: createdAt,
                updatedAt: updatedAt,
                lastConnectedAt: lastConnectedAt,
                lastVisitedPath: lastVisitedPath,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String name,
                required String host,
                Value<int> port = const Value.absent(),
                Value<String> protocol = const Value.absent(),
                Value<String?> shareName = const Value.absent(),
                Value<String?> username = const Value.absent(),
                required String credentialId,
                Value<String?> initialPath = const Value.absent(),
                Value<bool> isAnonymous = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<DateTime?> lastConnectedAt = const Value.absent(),
                Value<String?> lastVisitedPath = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ServerConfigsCompanion.insert(
                id: id,
                name: name,
                host: host,
                port: port,
                protocol: protocol,
                shareName: shareName,
                username: username,
                credentialId: credentialId,
                initialPath: initialPath,
                isAnonymous: isAnonymous,
                createdAt: createdAt,
                updatedAt: updatedAt,
                lastConnectedAt: lastConnectedAt,
                lastVisitedPath: lastVisitedPath,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$ServerConfigsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $ServerConfigsTable,
      ServerConfigRow,
      $$ServerConfigsTableFilterComposer,
      $$ServerConfigsTableOrderingComposer,
      $$ServerConfigsTableAnnotationComposer,
      $$ServerConfigsTableCreateCompanionBuilder,
      $$ServerConfigsTableUpdateCompanionBuilder,
      (
        ServerConfigRow,
        BaseReferences<_$AppDatabase, $ServerConfigsTable, ServerConfigRow>,
      ),
      ServerConfigRow,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$ServerConfigsTableTableManager get serverConfigs =>
      $$ServerConfigsTableTableManager(_db, _db.serverConfigs);
}
