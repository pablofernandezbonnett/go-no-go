import 'dart:convert';

import 'models/report_models.dart';
import 'report_source.dart';

const _artifactPathKeys = {'source_file', 'batch_json', 'weekly_digest'};

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
              markdownContent: _sanitizeArtifactText(artifact.content),
            ),
          );
        case ReportSourceArtifactType.weeklyDigestMarkdown:
          run.weeklyDigestReports.add(
            WeeklyDigestReport(
              runId: artifact.runId,
              relativePath: artifact.relativePath,
              fileName: artifact.fileName,
              weeklyId: _stem(artifact.fileName),
              markdownContent: _sanitizeArtifactText(artifact.content),
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
              yamlContent: _sanitizeArtifactText(artifact.content),
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
              rawJson: _sanitizeJsonArtifactText(artifact.content),
              decodedJson: _sanitizeArtifactPayload(decoded, blockedKeys: _artifactPathKeys),
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
  return _sanitizeArtifactPayload(value, blockedKeys: const {'source_file'});
}

Object? _sanitizeArtifactPayload(Object? value, {required Set<String> blockedKeys}) {
  if (value is Map<String, dynamic>) {
    final sanitized = <String, Object?>{};
    for (final entry in value.entries) {
      if (blockedKeys.contains(entry.key)) {
        continue;
      }
      sanitized[entry.key] = _sanitizeArtifactPayload(entry.value, blockedKeys: blockedKeys);
    }
    return sanitized;
  }
  if (value is Map) {
    final sanitized = <String, Object?>{};
    for (final entry in value.entries) {
      final key = entry.key.toString();
      if (blockedKeys.contains(key)) {
        continue;
      }
      sanitized[key] = _sanitizeArtifactPayload(entry.value, blockedKeys: blockedKeys);
    }
    return sanitized;
  }
  if (value is List) {
    return value.map((item) => _sanitizeArtifactPayload(item, blockedKeys: blockedKeys)).toList();
  }
  return value;
}

String _sanitizeArtifactText(String content) {
  final lines = content.split(RegExp(r'\r?\n'));
  final sanitized = <String>[];
  for (final line in lines) {
    final trimmed = line.trimLeft();
    if (trimmed.startsWith('- source_file:')
        || trimmed.startsWith('source_file:')
        || trimmed.startsWith('batch_json:')
        || trimmed.startsWith('weekly_digest:')) {
      continue;
    }
    sanitized.add(line);
  }
  return sanitized.join('\n');
}

String _sanitizeJsonArtifactText(String rawJson) {
  try {
    final decoded = jsonDecode(rawJson);
    final sanitized = _sanitizeArtifactPayload(decoded, blockedKeys: _artifactPathKeys);
    return jsonEncode(sanitized);
  } catch (_) {
    // Fall back to line-oriented scrubbing when the artifact is malformed.
  }

  var sanitized = rawJson;
  for (final key in _artifactPathKeys) {
    sanitized = sanitized.replaceAll(
      RegExp('"$key"\\s*:\\s*"([^"\\\\]|\\\\.)*"\\s*,?'),
      '',
    );
  }
  return sanitized.replaceAll(RegExp(',\\s*([}\\]])'), r'$1');
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
