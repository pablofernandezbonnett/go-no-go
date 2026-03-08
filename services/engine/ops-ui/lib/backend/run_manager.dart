import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;

class RunRequest {
  const RunRequest({
    required this.personaId,
    required this.companyIds,
    required this.fetchWebFirst,
    required this.robotsMode,
    required this.maxJobsPerCompany,
    required this.timeoutSeconds,
    required this.retries,
    required this.backoffMillis,
    required this.requestDelayMillis,
    required this.topPerSection,
  });

  static const String robotsModeStrict = 'strict';
  static const String robotsModeWarn = 'warn';
  static const String robotsModeOff = 'off';

  static const int minMaxJobsPerCompany = 1;
  static const int maxMaxJobsPerCompany = 20;
  static const int minTimeoutSeconds = 5;
  static const int maxTimeoutSeconds = 120;
  static const int minRetries = 0;
  static const int maxRetries = 8;
  static const int minBackoffMillis = 0;
  static const int maxBackoffMillis = 10000;
  static const int minRequestDelayMillis = 0;
  static const int maxRequestDelayMillis = 60000;
  static const int minTopPerSection = 1;
  static const int maxTopPerSection = 20;

  final String personaId;
  final List<String> companyIds;
  final bool fetchWebFirst;
  final String robotsMode;
  final int maxJobsPerCompany;
  final int timeoutSeconds;
  final int retries;
  final int backoffMillis;
  final int requestDelayMillis;
  final int topPerSection;

  static RunRequest fromJson(Map<String, dynamic> json) {
    final personaId = (json['personaId']?.toString() ?? '').trim();
    final companyIds = _asStringList(json['companyIds']);
    final fetchWebFirst = json['fetchWebFirst'] == true;
    final robotsMode = (json['robotsMode']?.toString() ?? robotsModeStrict).trim().toLowerCase();
    final maxJobsPerCompany = _asInt(json['maxJobsPerCompany'], fallback: 5);
    final timeoutSeconds = _asInt(json['timeoutSeconds'], fallback: 20);
    final retries = _asInt(json['retries'], fallback: 2);
    final backoffMillis = _asInt(json['backoffMillis'], fallback: 300);
    final requestDelayMillis = _asInt(json['requestDelayMillis'], fallback: 1200);
    final topPerSection = _asInt(json['topPerSection'], fallback: 5);

    _validate(
      personaId: personaId,
      companyIds: companyIds,
      robotsMode: robotsMode,
      maxJobsPerCompany: maxJobsPerCompany,
      timeoutSeconds: timeoutSeconds,
      retries: retries,
      backoffMillis: backoffMillis,
      requestDelayMillis: requestDelayMillis,
      topPerSection: topPerSection,
    );

    return RunRequest(
      personaId: personaId,
      companyIds: companyIds,
      fetchWebFirst: fetchWebFirst,
      robotsMode: robotsMode,
      maxJobsPerCompany: maxJobsPerCompany,
      timeoutSeconds: timeoutSeconds,
      retries: retries,
      backoffMillis: backoffMillis,
      requestDelayMillis: requestDelayMillis,
      topPerSection: topPerSection,
    );
  }

  Map<String, Object?> toJson() {
    return {
      'personaId': personaId,
      'companyIds': companyIds,
      'fetchWebFirst': fetchWebFirst,
      'robotsMode': robotsMode,
      'maxJobsPerCompany': maxJobsPerCompany,
      'timeoutSeconds': timeoutSeconds,
      'retries': retries,
      'backoffMillis': backoffMillis,
      'requestDelayMillis': requestDelayMillis,
      'topPerSection': topPerSection,
    };
  }

  static int _asInt(Object? value, {required int fallback}) {
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString().trim() ?? '') ?? fallback;
  }

  static List<String> _asStringList(Object? value) {
    if (value is! List) {
      return const [];
    }
    final seen = <String>{};
    final result = <String>[];
    for (final item in value) {
      final normalized = item.toString().trim();
      if (normalized.isEmpty || seen.contains(normalized)) {
        continue;
      }
      seen.add(normalized);
      result.add(normalized);
    }
    return result;
  }

  static void _validate({
    required String personaId,
    required List<String> companyIds,
    required String robotsMode,
    required int maxJobsPerCompany,
    required int timeoutSeconds,
    required int retries,
    required int backoffMillis,
    required int requestDelayMillis,
    required int topPerSection,
  }) {
    if (personaId.isEmpty) {
      throw const FormatException('personaId is required.');
    }
    final idPattern = RegExp(r'^[a-zA-Z0-9_-]+$');
    for (final companyId in companyIds) {
      if (!idPattern.hasMatch(companyId)) {
        throw FormatException('Invalid company id: $companyId');
      }
    }

    const allowedRobotsModes = {
      robotsModeStrict,
      robotsModeWarn,
      robotsModeOff,
    };
    if (!allowedRobotsModes.contains(robotsMode)) {
      throw const FormatException('robotsMode must be strict, warn, or off.');
    }
    if (maxJobsPerCompany < minMaxJobsPerCompany || maxJobsPerCompany > maxMaxJobsPerCompany) {
      throw FormatException('maxJobsPerCompany must be between $minMaxJobsPerCompany and $maxMaxJobsPerCompany.');
    }
    if (timeoutSeconds < minTimeoutSeconds || timeoutSeconds > maxTimeoutSeconds) {
      throw FormatException('timeoutSeconds must be between $minTimeoutSeconds and $maxTimeoutSeconds.');
    }
    if (retries < minRetries || retries > maxRetries) {
      throw FormatException('retries must be between $minRetries and $maxRetries.');
    }
    if (backoffMillis < minBackoffMillis || backoffMillis > maxBackoffMillis) {
      throw FormatException('backoffMillis must be between $minBackoffMillis and $maxBackoffMillis.');
    }
    if (requestDelayMillis < minRequestDelayMillis || requestDelayMillis > maxRequestDelayMillis) {
      throw FormatException('requestDelayMillis must be between $minRequestDelayMillis and $maxRequestDelayMillis.');
    }
    if (topPerSection < minTopPerSection || topPerSection > maxTopPerSection) {
      throw FormatException('topPerSection must be between $minTopPerSection and $maxTopPerSection.');
    }
  }
}

enum RunStatus {
  queued,
  running,
  succeeded,
  failed,
}

class RunRecord {
  RunRecord({
    required this.runId,
    required this.request,
    required this.outputDir,
    required this.createdAt,
    required this.command,
    required this.arguments,
  });

  static const int maxLogLines = 1500;

  final String runId;
  final RunRequest request;
  final String outputDir;
  final String command;
  final List<String> arguments;
  final DateTime createdAt;
  final List<String> logs = <String>[];

  RunStatus status = RunStatus.queued;
  DateTime? startedAt;
  DateTime? finishedAt;
  int? exitCode;

  void addLogLine(String line) {
    final value = line.trimRight();
    if (value.isEmpty) {
      return;
    }
    logs.add(value);
    if (logs.length > maxLogLines) {
      logs.removeRange(0, logs.length - maxLogLines);
    }
  }

  Map<String, Object?> toJson({bool includeLogs = true}) {
    return {
      'runId': runId,
      'status': status.name,
      'createdAt': createdAt.toUtc().toIso8601String(),
      'startedAt': startedAt?.toUtc().toIso8601String(),
      'finishedAt': finishedAt?.toUtc().toIso8601String(),
      'exitCode': exitCode,
      'outputDir': outputDir,
      'request': request.toJson(),
      'command': command,
      'arguments': arguments,
      if (includeLogs) 'logs': logs,
    };
  }
}

class RunManager {
  RunManager({
    required this.engineRoot,
    required this.gradlewCommand,
  });

  static const int maxRunsStored = 100;

  final Directory engineRoot;
  final String gradlewCommand;

  final LinkedHashMap<String, RunRecord> _runsById = LinkedHashMap<String, RunRecord>();
  final Queue<String> _queuedRunIds = Queue<String>();

  bool _isRunning = false;

  List<RunRecord> listRuns() {
    final runs = _runsById.values.toList();
    runs.sort((left, right) => right.createdAt.compareTo(left.createdAt));
    return runs;
  }

  RunRecord? getRun(String runId) {
    return _runsById[runId];
  }

  Future<RunRecord> submit(RunRequest request) async {
    final runId = _buildRunId();
    final runOutputDir = p.join(engineRoot.path, 'output', 'runs', runId);
    final args = _buildPipelineArguments(request: request, runOutputDir: runOutputDir);

    final record = RunRecord(
      runId: runId,
      request: request,
      outputDir: runOutputDir,
      createdAt: DateTime.now().toUtc(),
      command: gradlewCommand,
      arguments: ['run', '--args=${_buildGradleRunArgs(args)}'],
    );

    _runsById[runId] = record;
    _queuedRunIds.add(runId);
    _trimRuns();
    _pumpQueue();
    return record;
  }

  void _trimRuns() {
    while (_runsById.length > maxRunsStored) {
      final oldestRunId = _runsById.keys.first;
      _runsById.remove(oldestRunId);
      _queuedRunIds.remove(oldestRunId);
    }
  }

  void _pumpQueue() {
    if (_isRunning || _queuedRunIds.isEmpty) {
      return;
    }

    final runId = _queuedRunIds.removeFirst();
    final record = _runsById[runId];
    if (record == null) {
      _pumpQueue();
      return;
    }

    _isRunning = true;
    _execute(record).whenComplete(() {
      _isRunning = false;
      _pumpQueue();
    });
  }

  Future<void> _execute(RunRecord record) async {
    record.status = RunStatus.running;
    record.startedAt = DateTime.now().toUtc();
    await Directory(record.outputDir).create(recursive: true);

    final process = await Process.start(
      record.command,
      record.arguments,
      workingDirectory: engineRoot.path,
      runInShell: false,
    );

    final stdoutSub = process.stdout
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen((line) => record.addLogLine('[OUT] $line'));
    final stderrSub = process.stderr
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen((line) => record.addLogLine('[ERR] $line'));

    final exitCode = await process.exitCode;
    await stdoutSub.cancel();
    await stderrSub.cancel();

    record.exitCode = exitCode;
    record.finishedAt = DateTime.now().toUtc();
    record.status = exitCode == 0 ? RunStatus.succeeded : RunStatus.failed;
  }

  List<String> _buildPipelineArguments({
    required RunRequest request,
    required String runOutputDir,
  }) {
    final rawInputDir = p.join(runOutputDir, 'raw');
    final jobsOutputDir = p.join(runOutputDir, 'jobs');
    final weeklyOutputFile = p.join(runOutputDir, 'weekly.md');

    final args = <String>[
      'pipeline',
      'run',
      '--persona',
      request.personaId,
      '--raw-input-dir',
      rawInputDir,
      '--jobs-output-dir',
      jobsOutputDir,
      '--batch-output-dir',
      runOutputDir,
      '--weekly-output-file',
      weeklyOutputFile,
      '--top-per-section',
      request.topPerSection.toString(),
      '--fetch-web-max-jobs-per-company',
      request.maxJobsPerCompany.toString(),
      '--fetch-web-timeout-seconds',
      request.timeoutSeconds.toString(),
      '--fetch-web-retries',
      request.retries.toString(),
      '--fetch-web-backoff-millis',
      request.backoffMillis.toString(),
      '--fetch-web-request-delay-millis',
      request.requestDelayMillis.toString(),
      '--fetch-web-robots-mode',
      request.robotsMode,
    ];

    if (request.fetchWebFirst) {
      args.add('--fetch-web-first');
    }
    if (request.companyIds.isNotEmpty) {
      args.add('--fetch-web-company-ids');
      args.add(request.companyIds.join(','));
    }

    return args;
  }

  String _buildRunId() {
    final now = DateTime.now().toUtc();
    final ts = now
        .toIso8601String()
        .replaceAll(':', '')
        .replaceAll('-', '')
        .replaceAll('.', '')
        .replaceAll('Z', '');
    final suffix = now.microsecondsSinceEpoch.remainder(100000).toString().padLeft(5, '0');
    return 'run-$ts-$suffix';
  }

  String _buildGradleRunArgs(List<String> args) {
    return args.map(_quoteIfNeeded).join(' ');
  }

  String _quoteIfNeeded(String input) {
    if (!input.contains(RegExp(r'[\s"\\]'))) {
      return input;
    }
    final escaped = input.replaceAll(r'\', r'\\').replaceAll('"', r'\"');
    return '"$escaped"';
  }
}
