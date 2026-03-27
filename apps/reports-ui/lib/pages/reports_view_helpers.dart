import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/reports_index_payload.dart';

const _monthNames = <String>[
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
];

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

Map<String, String> currentQueryParams(BuildContext context) {
  final routeState = RouteState.maybeOf(context);
  final routeQueryParams = routeState?.queryParams ?? const <String, String>{};
  if (!kIsWeb) {
    return routeQueryParams;
  }

  final urlQueryParams = Uri.base.queryParameters;
  if (urlQueryParams.isNotEmpty) {
    return urlQueryParams;
  }

  return routeQueryParams;
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
      Link(
        to: buildRouteWithQuery(destinationPath, {
          'run': run.runId,
          if (extraQueryKey != null) extraQueryKey: extraQueryValue,
        }),
        classes: run.runId == selectedRunId ? 'run-tab active' : 'run-tab',
        child: .text(run.runId),
      ),
  ]);
}

Component runSelectionTabs({
  required List<ReportRunPayload> runs,
  required String selectedRunId,
  required void Function(ReportRunPayload run) onRunSelected,
}) {
  return div(classes: 'run-tabs', [
    for (final run in runs)
      button(
        classes: run.runId == selectedRunId ? 'run-tab active' : 'run-tab',
        onClick: () => onRunSelected(run),
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
          .text(': ${issue.message}'),
        ]),
    ]),
  ];
}

List<Component> pageHeader(String title, String description) {
  return [
    div(classes: 'page-header', [
      h1([.text(title)]),
      p(classes: 'page-description', [.text(description)]),
    ]),
  ];
}

Component artifactViewer({
  required String title,
  required String formatLabel,
  required String content,
  String? subtitle,
  String preClasses = '',
}) {
  final resolvedPreClasses = preClasses.isEmpty ? 'artifact-code' : 'artifact-code $preClasses';
  return div(classes: 'artifact-frame', [
    div(classes: 'artifact-frame-header', [
      div(classes: 'artifact-frame-copy', [
        h3(classes: 'artifact-frame-title', [.text(title)]),
        if (subtitle != null && subtitle.isNotEmpty)
          p(classes: 'artifact-frame-meta', [.text(subtitle)]),
      ]),
      span(classes: 'artifact-frame-badge', [.text(formatLabel)]),
    ]),
    pre(classes: resolvedPreClasses, [.text(content)]),
  ]);
}

Component card(List<Component> children, {String? classes}) {
  final resolvedClasses = classes == null || classes.isEmpty ? 'card' : 'card $classes';
  return div(classes: resolvedClasses, children);
}

Component pageLoading(String title, String message, {String? description}) => section(classes: 'page', [
  ...pageHeader(title, description ?? ''),
  card([
    p([.text(message)]),
  ]),
]);

Component pageError(String title, String error, void Function() onRetry, {String? description}) => section(classes: 'page', [
  ...pageHeader(title, description ?? ''),
  card([
    p(classes: 'error', [.text(error)]),
    button(onClick: onRetry, [.text('Retry')]),
  ]),
]);

Component pageEmpty(String title, String message, {String? description}) => section(classes: 'page', [
  ...pageHeader(title, description ?? ''),
  card([
    p([.text(message)]),
  ]),
]);

String formatFriendlyDateTime(String rawValue) {
  final trimmed = rawValue.trim();
  if (trimmed.isEmpty) {
    return 'Unknown';
  }

  try {
    final parsed = DateTime.parse(trimmed).toLocal();
    final month = _monthNames[parsed.month - 1];
    final day = parsed.day.toString().padLeft(2, '0');
    final hour = parsed.hour.toString().padLeft(2, '0');
    final minute = parsed.minute.toString().padLeft(2, '0');
    return '$month $day, ${parsed.year} at $hour:$minute';
  } catch (_) {
    return trimmed;
  }
}
