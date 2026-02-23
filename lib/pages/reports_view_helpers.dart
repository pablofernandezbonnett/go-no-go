import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../models/reports_index_payload.dart';

String buildRouteWithQuery(String path, Map<String, String?> queryValues) {
  final filtered = <String, String>{};
  for (final entry in queryValues.entries) {
    final value = entry.value;
    if (value == null || value.isEmpty) {
      continue;
    }
    filtered[entry.key] = value;
  }
  return Uri(path: path, queryParameters: filtered.isEmpty ? null : filtered).toString();
}

ReportRunPayload? selectRun(List<ReportRunPayload> runs, String? requestedRunId) {
  if (runs.isEmpty) {
    return null;
  }
  if (requestedRunId == null || requestedRunId.isEmpty) {
    return runs.first;
  }
  for (final run in runs) {
    if (run.runId == requestedRunId) {
      return run;
    }
  }
  return runs.first;
}

Component runTabs({
  required List<ReportRunPayload> runs,
  required String selectedRunId,
  required String destinationPath,
  String? extraQueryKey,
  String? extraQueryValue,
}) {
  return div(classes: 'run-tabs', [
    for (final run in runs)
      a(
        href: buildRouteWithQuery(destinationPath, {
          'run': run.runId,
          if (extraQueryKey != null) extraQueryKey: extraQueryValue,
        }),
        classes: run.runId == selectedRunId ? 'run-tab active' : 'run-tab',
        [.text(run.runId)],
      ),
  ]);
}

List<Component> issueList(List<ReportIssuePayload> issues) {
  if (issues.isEmpty) {
    return [];
  }

  return [
    h3([.text('Index Issues')]),
    ul([
      for (final issue in issues)
        li([
          code([.text(issue.code)]),
          .text(issue.relativePath == null ? ': ${issue.message}' : ' (${issue.relativePath}): ${issue.message}'),
        ]),
    ]),
  ];
}

Component card(List<Component> children, {String? classes}) {
  final resolvedClasses = classes == null || classes.isEmpty ? 'card' : 'card $classes';
  return div(classes: resolvedClasses, children);
}
