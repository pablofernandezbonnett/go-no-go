class OpsConfigPayload {
  const OpsConfigPayload({
    required this.personaIds,
    required this.candidateProfileIds,
    required this.companies,
  });

  final List<String> personaIds;
  final List<String> candidateProfileIds;
  final List<CompanyPayload> companies;

  factory OpsConfigPayload.fromJson(Map<String, dynamic> json) {
    final personasRaw = json['personaIds'];
    final candidateProfilesRaw = json['candidateProfileIds'];
    final companiesRaw = json['companies'];

    final personaIds = (personasRaw is List)
        ? personasRaw.map((item) => item.toString()).where((item) => item.trim().isNotEmpty).toList()
        : <String>[];
    final candidateProfileIds = (candidateProfilesRaw is List)
        ? candidateProfilesRaw.map((item) => item.toString()).where((item) => item.trim().isNotEmpty).toList()
        : <String>[];
    final companies = (companiesRaw is List)
        ? companiesRaw.whereType<Map<String, dynamic>>().map(CompanyPayload.fromJson).toList()
        : <CompanyPayload>[];

    return OpsConfigPayload(
      personaIds: personaIds,
      candidateProfileIds: candidateProfileIds,
      companies: companies,
    );
  }
}

class HealthPayload {
  const HealthPayload({
    required this.status,
  });

  final String status;

  factory HealthPayload.fromJson(Map<String, dynamic> json) {
    return HealthPayload(
      status: json['status']?.toString() ?? '',
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
    required this.request,
  });

  final String runId;
  final String status;
  final String createdAt;
  final String? startedAt;
  final String? finishedAt;
  final int? exitCode;
  final RunRequestPayload request;

  factory RunPayload.fromJson(Map<String, dynamic> json) {
    final requestRaw = json['request'];

    return RunPayload(
      runId: json['runId']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      createdAt: json['createdAt']?.toString() ?? '',
      startedAt: json['startedAt']?.toString(),
      finishedAt: json['finishedAt']?.toString(),
      exitCode: json['exitCode'] is num ? (json['exitCode'] as num).toInt() : null,
      request: requestRaw is Map<String, dynamic>
          ? RunRequestPayload.fromJson(requestRaw)
          : const RunRequestPayload.empty(),
    );
  }
}

class RunRequestPayload {
  const RunRequestPayload({
    required this.personaId,
    required this.candidateProfileId,
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
      candidateProfileId = '',
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
  final String candidateProfileId;
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
      candidateProfileId: json['candidateProfileId']?.toString() ?? '',
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
}

int _toInt(Object? value) {
  if (value is num) {
    return value.toInt();
  }
  return int.tryParse(value?.toString() ?? '') ?? 0;
}
