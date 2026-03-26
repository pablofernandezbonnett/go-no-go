import 'dart:convert';

import 'models/report_models.dart';
import 'report_source.dart';

class ReportIndexBuilder {
  const ReportIndexBuilder();

  ReportIndex build(ReportSourceResult sourceResult) {
    final issues = sourceResult.issues
        .map(
          (issue) => ReportIndexIssue(
            code: issue.code,
            relativePath: issue.relativePath,
            message: issue.message,
          ),
        )
        .toList();

    final runs = <String, _RunAccumulator>{};

    for (final artifact in sourceResult.artifacts) {
      final run = runs.putIfAbsent(
        artifact.runId,
        () => _RunAccumulator(runId: artifact.runId),
      );

      switch (artifact.type) {
        case ReportSourceArtifactType.batchEvaluationJson:
          final identity = _identityFromPrefix(
            fileName: artifact.fileName,
            prefix: 'batch-evaluation-',
          );
          final decoded = _tryDecodeJson(
            rawJson: artifact.content,
            relativePath: artifact.relativePath,
            issues: issues,
            invalidCode: 'invalid_batch_json',
            invalidMessage: 'Invalid batch evaluation JSON.',
          );
          run.batchEvaluationJsonReports.add(
            BatchEvaluationJsonReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              reportId: identity.id,
              personaId: identity.personaId,
              rawJson: artifact.content,
              decodedJson: _sanitizeBatchPayload(decoded),
              isValidJson: decoded != null,
            ),
          );
        case ReportSourceArtifactType.batchEvaluationMarkdown:
          final identity = _identityFromPrefix(
            fileName: artifact.fileName,
            prefix: 'batch-evaluation-',
          );
          run.batchEvaluationMarkdownReports.add(
            BatchEvaluationMarkdownReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              reportId: identity.id,
              personaId: identity.personaId,
              markdownContent: artifact.content,
            ),
          );
        case ReportSourceArtifactType.weeklyDigestMarkdown:
          run.weeklyDigestReports.add(
            WeeklyDigestReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              weeklyId: _stem(artifact.fileName),
              markdownContent: artifact.content,
            ),
          );
        case ReportSourceArtifactType.trendHistoryYaml:
          final identity = _identityFromPrefix(
            fileName: artifact.fileName,
            prefix: 'trend-history-',
          );
          run.trendHistoryReports.add(
            TrendHistoryReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              historyId: identity.id,
              personaId: identity.personaId,
              yamlContent: artifact.content,
            ),
          );
        case ReportSourceArtifactType.trendAlertsJson:
          final identity = _identityFromTrendAlerts(artifact.fileName);
          final decoded = _tryDecodeJson(
            rawJson: artifact.content,
            relativePath: artifact.relativePath,
            issues: issues,
            invalidCode: 'invalid_trend_alerts_json',
            invalidMessage: 'Invalid trend alerts JSON.',
          );
          run.trendAlertsReports.add(
            TrendAlertsReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              alertsId: identity.id,
              personaId: identity.personaId,
              rawJson: artifact.content,
              decodedJson: decoded,
              isValidJson: decoded != null,
            ),
          );
        case ReportSourceArtifactType.companyContextText:
          run.companyContextReports.add(
            CompanyContextReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              companyId: _stem(artifact.fileName),
              textContent: artifact.content,
            ),
          );
      }
    }

    final reportRuns =
        runs.values
            .map(
              (run) => ReportRunIndex(
                runId: run.runId,
                batchEvaluationJsonReports: _sortedByPath(
                  run.batchEvaluationJsonReports,
                  (report) => report.relativePath,
                ),
                batchEvaluationMarkdownReports: _sortedByPath(
                  run.batchEvaluationMarkdownReports,
                  (report) => report.relativePath,
                ),
                weeklyDigestReports: _sortedByPath(
                  run.weeklyDigestReports,
                  (report) => report.relativePath,
                ),
                trendHistoryReports: _sortedByPath(
                  run.trendHistoryReports,
                  (report) => report.relativePath,
                ),
                trendAlertsReports: _sortedByPath(
                  run.trendAlertsReports,
                  (report) => report.relativePath,
                ),
                companyContextReports: _sortedByPath(
                  run.companyContextReports,
                  (report) => report.relativePath,
                ),
              ),
            )
            .toList()
          ..sort((left, right) => left.runId.compareTo(right.runId));

    return ReportIndex(
      reportsRoot: sourceResult.reportsRoot,
      reportsRootExists: sourceResult.reportsRootExists,
      runs: reportRuns,
      issues: issues,
    );
  }
}

Object? _sanitizeBatchPayload(Object? value) {
  if (value is Map<String, dynamic>) {
    final sanitized = <String, Object?>{};
    for (final entry in value.entries) {
      if (entry.key == 'source_file') {
        continue;
      }
      sanitized[entry.key] = _sanitizeBatchPayload(entry.value);
    }
    return sanitized;
  }
  if (value is Map) {
    final sanitized = <String, Object?>{};
    for (final entry in value.entries) {
      final key = entry.key.toString();
      if (key == 'source_file') {
        continue;
      }
      sanitized[key] = _sanitizeBatchPayload(entry.value);
    }
    return sanitized;
  }
  if (value is List) {
    return value.map(_sanitizeBatchPayload).toList();
  }
  return value;
}

Object? _tryDecodeJson({
  required String rawJson,
  required String relativePath,
  required List<ReportIndexIssue> issues,
  required String invalidCode,
  required String invalidMessage,
}) {
  try {
    return jsonDecode(rawJson);
  } catch (_) {
    issues.add(
      ReportIndexIssue(
        code: invalidCode,
        relativePath: relativePath,
        message: invalidMessage,
      ),
    );
    return null;
  }
}

_ArtifactIdentity _identityFromPrefix({
  required String fileName,
  required String prefix,
}) {
  final fileStem = _stem(fileName);
  if (!fileStem.startsWith(prefix)) {
    return _ArtifactIdentity(id: fileStem, personaId: null);
  }

  final suffix = fileStem.substring(prefix.length);
  return _ArtifactIdentity(
    id: fileStem,
    personaId: suffix.isEmpty ? null : suffix,
  );
}

_ArtifactIdentity _identityFromTrendAlerts(String fileName) {
  final fileStem = _stem(fileName);
  const exact = 'trend-alerts';
  const prefix = 'trend-alerts-';
  if (fileStem == exact) {
    return const _ArtifactIdentity(id: exact, personaId: null);
  }
  if (!fileStem.startsWith(prefix)) {
    return _ArtifactIdentity(id: fileStem, personaId: null);
  }
  final suffix = fileStem.substring(prefix.length);
  return _ArtifactIdentity(
    id: fileStem,
    personaId: suffix.isEmpty ? null : suffix,
  );
}

String _stem(String fileName) {
  final separator = fileName.lastIndexOf('.');
  if (separator <= 0) {
    return fileName;
  }
  return fileName.substring(0, separator);
}

List<T> _sortedByPath<T>(List<T> reports, String Function(T report) pathOf) {
  reports.sort((left, right) {
    return pathOf(left).compareTo(pathOf(right));
  });
  return reports;
}

class _RunAccumulator {
  _RunAccumulator({
    required this.runId,
  });

  final String runId;
  final List<BatchEvaluationJsonReport> batchEvaluationJsonReports = [];
  final List<BatchEvaluationMarkdownReport> batchEvaluationMarkdownReports = [];
  final List<WeeklyDigestReport> weeklyDigestReports = [];
  final List<TrendHistoryReport> trendHistoryReports = [];
  final List<TrendAlertsReport> trendAlertsReports = [];
  final List<CompanyContextReport> companyContextReports = [];
}

class _ArtifactIdentity {
  const _ArtifactIdentity({
    required this.id,
    required this.personaId,
  });

  final String id;
  final String? personaId;
}
