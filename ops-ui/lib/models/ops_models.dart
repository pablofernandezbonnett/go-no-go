class OpsConfigPayload {
  const OpsConfigPayload({
    required this.personaIds,
    required this.companies,
  });

  final List<String> personaIds;
  final List<CompanyPayload> companies;

  factory OpsConfigPayload.fromJson(Map<String, dynamic> json) {
    final personasRaw = json['personaIds'];
    final companiesRaw = json['companies'];

    final personaIds = (personasRaw is List)
        ? personasRaw.map((item) => item.toString()).where((item) => item.trim().isNotEmpty).toList()
        : <String>[];
    final companies = (companiesRaw is List)
        ? companiesRaw
              .whereType<Map<String, dynamic>>()
              .map(CompanyPayload.fromJson)
              .toList()
        : <CompanyPayload>[];

    return OpsConfigPayload(personaIds: personaIds, companies: companies);
  }
}

class HealthPayload {
  const HealthPayload({
    required this.status,
    required this.engineRoot,
  });

  final String status;
  final String engineRoot;

  factory HealthPayload.fromJson(Map<String, dynamic> json) {
    return HealthPayload(
      status: json['status']?.toString() ?? '',
      engineRoot: json['engineRoot']?.toString() ?? '',
    );
  }
}

class CompanyPayload {
  const CompanyPayload({
    required this.id,
    required this.name,
  });

  final String id;
  final String name;

  factory CompanyPayload.fromJson(Map<String, dynamic> json) {
    return CompanyPayload(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
    );
  }
}

class RunPayload {
  const RunPayload({
    required this.runId,
    required this.status,
    required this.createdAt,
    required this.startedAt,
    required this.finishedAt,
    required this.exitCode,
    required this.outputDir,
    required this.request,
    required this.command,
    required this.arguments,
    required this.logs,
  });

  final String runId;
  final String status;
  final String createdAt;
  final String? startedAt;
  final String? finishedAt;
  final int? exitCode;
  final String outputDir;
  final RunRequestPayload request;
  final String command;
  final List<String> arguments;
  final List<String> logs;

  factory RunPayload.fromJson(Map<String, dynamic> json) {
    final requestRaw = json['request'];
    final argumentsRaw = json['arguments'];
    final logsRaw = json['logs'];

    return RunPayload(
      runId: json['runId']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      createdAt: json['createdAt']?.toString() ?? '',
      startedAt: json['startedAt']?.toString(),
      finishedAt: json['finishedAt']?.toString(),
      exitCode: json['exitCode'] is num ? (json['exitCode'] as num).toInt() : null,
      outputDir: json['outputDir']?.toString() ?? '',
      request: requestRaw is Map<String, dynamic>
          ? RunRequestPayload.fromJson(requestRaw)
          : const RunRequestPayload.empty(),
      command: json['command']?.toString() ?? '',
      arguments: (argumentsRaw is List)
          ? argumentsRaw.map((item) => item.toString()).toList()
          : <String>[],
      logs: (logsRaw is List) ? logsRaw.map((item) => item.toString()).toList() : <String>[],
    );
  }
}

class RunRequestPayload {
  const RunRequestPayload({
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

  const RunRequestPayload.empty()
      : personaId = '',
        companyIds = const <String>[],
        fetchWebFirst = false,
        robotsMode = 'strict',
        maxJobsPerCompany = 0,
        timeoutSeconds = 0,
        retries = 0,
        backoffMillis = 0,
        requestDelayMillis = 0,
        topPerSection = 0;

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

  factory RunRequestPayload.fromJson(Map<String, dynamic> json) {
    final companyIdsRaw = json['companyIds'];

    return RunRequestPayload(
      personaId: json['personaId']?.toString() ?? '',
      companyIds: (companyIdsRaw is List)
          ? companyIdsRaw.map((item) => item.toString()).where((item) => item.trim().isNotEmpty).toList()
          : <String>[],
      fetchWebFirst: json['fetchWebFirst'] == true,
      robotsMode: json['robotsMode']?.toString() ?? 'strict',
      maxJobsPerCompany: _toInt(json['maxJobsPerCompany']),
      timeoutSeconds: _toInt(json['timeoutSeconds']),
      retries: _toInt(json['retries']),
      backoffMillis: _toInt(json['backoffMillis']),
      requestDelayMillis: _toInt(json['requestDelayMillis']),
      topPerSection: _toInt(json['topPerSection']),
    );
  }

  static int _toInt(Object? value) {
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
