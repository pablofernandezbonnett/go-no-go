import 'dart:io';

import 'reports_root_config.dart';

enum ReportSourceArtifactType {
  batchEvaluationJson,
  batchEvaluationMarkdown,
  weeklyDigestMarkdown,
  trendHistoryYaml,
  trendAlertsJson,
  companyContextText,
}

class ReportSourceArtifact {
  const ReportSourceArtifact({
    required this.type,
    required this.runId,
    required this.relativePath,
    required this.fileName,
    required this.content,
  });

  final ReportSourceArtifactType type;
  final String runId;
  final String relativePath;
  final String fileName;
  final String content;
}

class ReportSourceIssue {
  const ReportSourceIssue({
    required this.code,
    required this.relativePath,
    required this.message,
  });

  final String code;
  final String? relativePath;
  final String message;
}

class ReportSourceResult {
  const ReportSourceResult({
    required this.reportsRoot,
    required this.reportsRootExists,
    required this.artifacts,
    required this.issues,
  });

  final String reportsRoot;
  final bool reportsRootExists;
  final List<ReportSourceArtifact> artifacts;
  final List<ReportSourceIssue> issues;
}

class ReportSource {
  const ReportSource({
    required this.config,
  });

  final ReportsRootConfig config;

  Future<ReportSourceResult> discoverArtifacts() async {
    final reportsRootDirectory = config.resolveDirectory();
    final reportsRootPath = _normalizePath(reportsRootDirectory.path);
    final issues = <ReportSourceIssue>[];
    final artifacts = <ReportSourceArtifact>[];

    if (!await reportsRootDirectory.exists()) {
      issues.add(
        const ReportSourceIssue(
          code: 'reports_root_missing',
          relativePath: null,
          message: 'Configured reports root does not exist.',
        ),
      );
      return ReportSourceResult(
        reportsRoot: reportsRootPath,
        reportsRootExists: false,
        artifacts: artifacts,
        issues: issues,
      );
    }

    await for (final entity in reportsRootDirectory.list(
      recursive: true,
      followLinks: false,
    )) {
      if (entity is! File) {
        continue;
      }

      final fileName = _fileName(entity.path);
      if (fileName.startsWith('.')) {
        continue;
      }

      final relativePath = _relativePath(
        reportsRootDirectory.path,
        entity.path,
      );
      final artifactType = _detectType(
        fileName: fileName,
        relativePath: relativePath,
      );
      if (artifactType == null) {
        continue;
      }

      try {
        final content = await entity.readAsString();
        artifacts.add(
          ReportSourceArtifact(
            type: artifactType,
            runId: _deriveRunId(relativePath),
            relativePath: relativePath,
            fileName: fileName,
            content: content,
          ),
        );
      } catch (_) {
        issues.add(
          ReportSourceIssue(
            code: 'file_read_failed',
            relativePath: relativePath,
            message: 'Failed to read report artifact.',
          ),
        );
      }
    }

    artifacts.sort((left, right) => left.relativePath.compareTo(right.relativePath));

    return ReportSourceResult(
      reportsRoot: reportsRootPath,
      reportsRootExists: true,
      artifacts: artifacts,
      issues: issues,
    );
  }
}

ReportSourceArtifactType? _detectType({
  required String fileName,
  required String relativePath,
}) {
  final lowerFileName = fileName.toLowerCase();

  if (lowerFileName.startsWith('batch-evaluation-') && lowerFileName.endsWith('.json')) {
    return ReportSourceArtifactType.batchEvaluationJson;
  }
  if (lowerFileName.startsWith('batch-evaluation-') && lowerFileName.endsWith('.md')) {
    return ReportSourceArtifactType.batchEvaluationMarkdown;
  }
  if (lowerFileName.startsWith('weekly') && lowerFileName.endsWith('.md')) {
    return ReportSourceArtifactType.weeklyDigestMarkdown;
  }
  if (lowerFileName.startsWith('trend-history-') &&
      (lowerFileName.endsWith('.yaml') || lowerFileName.endsWith('.yml'))) {
    return ReportSourceArtifactType.trendHistoryYaml;
  }
  if ((lowerFileName == 'trend-alerts.json' || lowerFileName.startsWith('trend-alerts-')) &&
      lowerFileName.endsWith('.json')) {
    return ReportSourceArtifactType.trendAlertsJson;
  }
  if (_isContextFile(relativePath, lowerFileName)) {
    return ReportSourceArtifactType.companyContextText;
  }

  return null;
}

bool _isContextFile(String relativePath, String lowerFileName) {
  if (!lowerFileName.endsWith('.txt')) {
    return false;
  }

  final segments = _normalizePath(relativePath).split('/');
  return segments.contains('company-context') || segments.contains('context');
}

String _deriveRunId(String relativePath) {
  final normalized = _normalizePath(relativePath);
  final segments = normalized.split('/');
  if (segments.length <= 1) {
    return 'root';
  }

  final parentSegments = segments.sublist(0, segments.length - 1);
  if (parentSegments.isEmpty) {
    return 'root';
  }

  final contextNames = {'company-context', 'context'};
  final lastParent = parentSegments.last;
  if (contextNames.contains(lastParent.toLowerCase()) && parentSegments.length == 1) {
    return 'root';
  }
  if (contextNames.contains(lastParent.toLowerCase()) && parentSegments.length > 1) {
    return parentSegments.sublist(0, parentSegments.length - 1).join('/');
  }

  return parentSegments.join('/');
}

String _relativePath(String rootPath, String filePath) {
  final normalizedRoot = _normalizePath(Directory(rootPath).absolute.path);
  final normalizedFile = _normalizePath(File(filePath).absolute.path);
  if (normalizedFile == normalizedRoot) {
    return '';
  }

  final prefix = normalizedRoot.endsWith('/') ? normalizedRoot : '$normalizedRoot/';
  if (normalizedFile.startsWith(prefix)) {
    return normalizedFile.substring(prefix.length);
  }

  return normalizedFile;
}

String _normalizePath(String path) {
  return path.replaceAll('\\', '/');
}

String _fileName(String path) {
  final normalized = _normalizePath(path);
  if (normalized.isEmpty) {
    return normalized;
  }
  final separator = normalized.lastIndexOf('/');
  if (separator == -1) {
    return normalized;
  }
  return normalized.substring(separator + 1);
}
