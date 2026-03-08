class ReportsIndexPayload {
  const ReportsIndexPayload({
    required this.reportsRoot,
    required this.reportsRootExists,
    required this.runs,
    required this.issues,
  });

  final String reportsRoot;
  final bool reportsRootExists;
  final List<ReportRunPayload> runs;
  final List<ReportIssuePayload> issues;

  factory ReportsIndexPayload.fromJson(Map<String, dynamic> json) {
    return ReportsIndexPayload(
      reportsRoot: json['reportsRoot']?.toString() ?? '',
      reportsRootExists: json['reportsRootExists'] == true,
      runs: _asList(json['runs']).map((entry) => ReportRunPayload.fromJson(_asMap(entry))).toList(),
      issues: _asList(json['issues']).map((entry) => ReportIssuePayload.fromJson(_asMap(entry))).toList(),
    );
  }
}

class ReportRunPayload {
  const ReportRunPayload({
    required this.runId,
    required this.batchEvaluationJsonReports,
    required this.batchEvaluationMarkdownReports,
    required this.weeklyDigestReports,
    required this.trendHistoryReports,
    required this.trendAlertsReports,
    required this.companyContextReports,
  });

  final String runId;
  final List<BatchEvaluationJsonPayload> batchEvaluationJsonReports;
  final List<BatchEvaluationMarkdownPayload> batchEvaluationMarkdownReports;
  final List<WeeklyDigestPayload> weeklyDigestReports;
  final List<TrendHistoryPayload> trendHistoryReports;
  final List<TrendAlertsPayload> trendAlertsReports;
  final List<CompanyContextPayload> companyContextReports;

  factory ReportRunPayload.fromJson(Map<String, dynamic> json) {
    return ReportRunPayload(
      runId: json['runId']?.toString() ?? 'unknown',
      batchEvaluationJsonReports: _asList(
        json['batchEvaluationJsonReports'],
      ).map((entry) => BatchEvaluationJsonPayload.fromJson(_asMap(entry))).toList(),
      batchEvaluationMarkdownReports: _asList(
        json['batchEvaluationMarkdownReports'],
      ).map((entry) => BatchEvaluationMarkdownPayload.fromJson(_asMap(entry))).toList(),
      weeklyDigestReports: _asList(
        json['weeklyDigestReports'],
      ).map((entry) => WeeklyDigestPayload.fromJson(_asMap(entry))).toList(),
      trendHistoryReports: _asList(
        json['trendHistoryReports'],
      ).map((entry) => TrendHistoryPayload.fromJson(_asMap(entry))).toList(),
      trendAlertsReports: _asList(
        json['trendAlertsReports'],
      ).map((entry) => TrendAlertsPayload.fromJson(_asMap(entry))).toList(),
      companyContextReports: _asList(
        json['companyContextReports'],
      ).map((entry) => CompanyContextPayload.fromJson(_asMap(entry))).toList(),
    );
  }
}

class BatchEvaluationJsonPayload {
  const BatchEvaluationJsonPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.reportId,
    required this.personaId,
    required this.rawJson,
    required this.decodedJson,
    required this.isValidJson,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String reportId;
  final String? personaId;
  final String rawJson;
  final Object? decodedJson;
  final bool isValidJson;

  factory BatchEvaluationJsonPayload.fromJson(Map<String, dynamic> json) {
    final personaValue = json['personaId'];
    return BatchEvaluationJsonPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      reportId: json['reportId']?.toString() ?? '',
      personaId: personaValue == null ? null : personaValue.toString(),
      rawJson: json['rawJson']?.toString() ?? '',
      decodedJson: json['decodedJson'],
      isValidJson: json['isValidJson'] == true,
    );
  }
}

class BatchEvaluationMarkdownPayload {
  const BatchEvaluationMarkdownPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.reportId,
    required this.personaId,
    required this.markdownContent,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String reportId;
  final String? personaId;
  final String markdownContent;

  factory BatchEvaluationMarkdownPayload.fromJson(Map<String, dynamic> json) {
    final personaValue = json['personaId'];
    return BatchEvaluationMarkdownPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      reportId: json['reportId']?.toString() ?? '',
      personaId: personaValue == null ? null : personaValue.toString(),
      markdownContent: json['markdownContent']?.toString() ?? '',
    );
  }
}

class WeeklyDigestPayload {
  const WeeklyDigestPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.weeklyId,
    required this.markdownContent,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String weeklyId;
  final String markdownContent;

  factory WeeklyDigestPayload.fromJson(Map<String, dynamic> json) {
    return WeeklyDigestPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      weeklyId: json['weeklyId']?.toString() ?? '',
      markdownContent: json['markdownContent']?.toString() ?? '',
    );
  }
}

class TrendHistoryPayload {
  const TrendHistoryPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.historyId,
    required this.personaId,
    required this.yamlContent,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String historyId;
  final String? personaId;
  final String yamlContent;

  factory TrendHistoryPayload.fromJson(Map<String, dynamic> json) {
    final personaValue = json['personaId'];
    return TrendHistoryPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      historyId: json['historyId']?.toString() ?? '',
      personaId: personaValue == null ? null : personaValue.toString(),
      yamlContent: json['yamlContent']?.toString() ?? '',
    );
  }
}

class TrendAlertsPayload {
  const TrendAlertsPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.alertsId,
    required this.personaId,
    required this.rawJson,
    required this.decodedJson,
    required this.isValidJson,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String alertsId;
  final String? personaId;
  final String rawJson;
  final Object? decodedJson;
  final bool isValidJson;

  factory TrendAlertsPayload.fromJson(Map<String, dynamic> json) {
    final personaValue = json['personaId'];
    return TrendAlertsPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      alertsId: json['alertsId']?.toString() ?? '',
      personaId: personaValue == null ? null : personaValue.toString(),
      rawJson: json['rawJson']?.toString() ?? '',
      decodedJson: json['decodedJson'],
      isValidJson: json['isValidJson'] == true,
    );
  }
}

class CompanyContextPayload {
  const CompanyContextPayload({
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.companyId,
    required this.textContent,
  });

  final String runId;
  final String relativePath;
  final String fileName;
  final String companyId;
  final String textContent;

  factory CompanyContextPayload.fromJson(Map<String, dynamic> json) {
    return CompanyContextPayload(
      runId: json['runId']?.toString() ?? '',
      relativePath: json['relativePath']?.toString() ?? '',
      fileName: json['fileName']?.toString() ?? '',
      companyId: json['companyId']?.toString() ?? '',
      textContent: json['textContent']?.toString() ?? '',
    );
  }
}

class ReportIssuePayload {
  const ReportIssuePayload({
    required this.code,
    required this.relativePath,
    required this.message,
  });

  final String code;
  final String? relativePath;
  final String message;

  factory ReportIssuePayload.fromJson(Map<String, dynamic> json) {
    final relativePath = json['relativePath'];
    return ReportIssuePayload(
      code: json['code']?.toString() ?? 'unknown_issue',
      relativePath: relativePath == null ? null : relativePath.toString(),
      message: json['message']?.toString() ?? '',
    );
  }
}

Map<String, dynamic> _asMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    final mapped = <String, dynamic>{};
    for (final entry in value.entries) {
      mapped[entry.key.toString()] = entry.value;
    }
    return mapped;
  }
  return const <String, dynamic>{};
}

List<Object?> _asList(Object? value) {
  if (value is List) {
    return value.cast<Object?>();
  }
  return const <Object?>[];
}
