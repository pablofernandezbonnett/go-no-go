class ReportIndex {
  const ReportIndex({
    required this.reportsRoot,
    required this.reportsRootExists,
    required this.runs,
    required this.issues,
  });

  final String reportsRoot;
  final bool reportsRootExists;
  final List<ReportRunIndex> runs;
  final List<ReportIndexIssue> issues;

  Map<String, Object?> toJson() {
    return {
      'reportsRootExists': reportsRootExists,
      'runs': runs.map((run) => run.toJson()).toList(),
      'issues': issues.map((issue) => issue.toJson()).toList(),
    };
  }
}

class ReportRunIndex {
  const ReportRunIndex({
    required this.runId,
    required this.batchEvaluationJsonReports,
    required this.batchEvaluationMarkdownReports,
    required this.weeklyDigestReports,
    required this.trendHistoryReports,
    required this.trendAlertsReports,
    required this.companyContextReports,
  });

  final String runId;
  final List<BatchEvaluationJsonReport> batchEvaluationJsonReports;
  final List<BatchEvaluationMarkdownReport> batchEvaluationMarkdownReports;
  final List<WeeklyDigestReport> weeklyDigestReports;
  final List<TrendHistoryReport> trendHistoryReports;
  final List<TrendAlertsReport> trendAlertsReports;
  final List<CompanyContextReport> companyContextReports;

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'batchEvaluationJsonReports': batchEvaluationJsonReports.map((report) => report.toJson()).toList(),
      'batchEvaluationMarkdownReports': batchEvaluationMarkdownReports.map((report) => report.toJson()).toList(),
      'weeklyDigestReports': weeklyDigestReports.map((report) => report.toJson()).toList(),
      'trendHistoryReports': trendHistoryReports.map((report) => report.toJson()).toList(),
      'trendAlertsReports': trendAlertsReports.map((report) => report.toJson()).toList(),
      'companyContextReports': companyContextReports.map((report) => report.toJson()).toList(),
    };
  }
}

class BatchEvaluationJsonReport {
  const BatchEvaluationJsonReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'reportId': reportId,
      'personaId': personaId,
      'decodedJson': decodedJson,
      'isValidJson': isValidJson,
    };
  }
}

class BatchEvaluationMarkdownReport {
  const BatchEvaluationMarkdownReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'reportId': reportId,
      'personaId': personaId,
      'markdownContent': markdownContent,
    };
  }
}

class WeeklyDigestReport {
  const WeeklyDigestReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'weeklyId': weeklyId,
      'markdownContent': markdownContent,
    };
  }
}

class TrendHistoryReport {
  const TrendHistoryReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'historyId': historyId,
      'personaId': personaId,
      'yamlContent': yamlContent,
    };
  }
}

class TrendAlertsReport {
  const TrendAlertsReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'alertsId': alertsId,
      'personaId': personaId,
      'rawJson': rawJson,
      'decodedJson': decodedJson,
      'isValidJson': isValidJson,
    };
  }
}

class CompanyContextReport {
  const CompanyContextReport({
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

  Map<String, Object?> toJson() {
    return {
      'runId': runId,
      'fileName': fileName,
      'companyId': companyId,
      'textContent': textContent,
    };
  }
}

class ReportIndexIssue {
  const ReportIndexIssue({
    required this.code,
    required this.relativePath,
    required this.message,
  });

  final String code;
  final String? relativePath;
  final String message;

  Map<String, Object?> toJson() {
    return {
      'code': code,
      'message': message,
    };
  }
}
