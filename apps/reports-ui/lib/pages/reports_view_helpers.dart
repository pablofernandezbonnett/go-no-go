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

enum ReportTableWidth {
  compact,
  medium,
  wide,
  xwide
  ;

  String get className => switch (this) {
    ReportTableWidth.compact => 'table-col-compact',
    ReportTableWidth.medium => 'table-col-medium',
    ReportTableWidth.wide => 'table-col-wide',
    ReportTableWidth.xwide => 'table-col-xwide',
  };
}

class ReportTableColumn {
  const ReportTableColumn(this.label, {this.width = ReportTableWidth.medium});

  final String label;
  final ReportTableWidth width;
}

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
  bool showHeader = true,
}) {
  final resolvedPreClasses = preClasses.isEmpty ? 'artifact-code' : 'artifact-code $preClasses';
  if (!showHeader) {
    return pre(classes: resolvedPreClasses, [.text(content)]);
  }
  return div(classes: 'artifact-frame', [
    div(classes: 'artifact-frame-header', [
      div(classes: 'artifact-frame-copy', [
        h3(classes: 'artifact-frame-title', [.text(title)]),
        if (subtitle != null && subtitle.isNotEmpty) p(classes: 'artifact-frame-meta', [.text(subtitle)]),
      ]),
      span(classes: 'artifact-frame-badge', [.text(formatLabel)]),
    ]),
    pre(classes: resolvedPreClasses, [.text(content)]),
  ]);
}

Component reportTable({
  required List<ReportTableColumn> columns,
  required List<Component> rows,
  String? classes,
}) {
  final resolvedClasses = classes == null || classes.isEmpty ? 'report-table' : 'report-table $classes';
  return div(classes: 'table-scroll', [
    table(classes: resolvedClasses, [
      thead([
        tr([
          for (final column in columns) _tableHeaderCell(column),
        ]),
      ]),
      tbody(rows),
    ]),
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

Component pageError(String title, String error, void Function() onRetry, {String? description}) =>
    section(classes: 'page', [
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

String humanizeIdentifier(String raw) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) {
    return '';
  }
  final words = trimmed.replaceAll(RegExp(r'[_/\-]+'), ' ').split(RegExp(r'\s+')).where((word) => word.isNotEmpty);
  return words
      .map((word) {
        final normalized = word.toLowerCase();
        if (normalized.length <= 3) {
          return normalized.toUpperCase();
        }
        return '${normalized[0].toUpperCase()}${normalized.substring(1)}';
      })
      .join(' ');
}

String describeDecisionFactor(String raw) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) {
    return '';
  }

  final mapped = _decisionFactorCopy[trimmed.toLowerCase()];
  if (mapped != null) {
    return mapped;
  }
  if (trimmed.contains(' ')) {
    return '${trimmed[0].toUpperCase()}${trimmed.substring(1)}';
  }
  return humanizeIdentifier(trimmed);
}

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

const Map<String, String> _decisionFactorCopy = {
  'salary_transparency': 'The salary range is clearly published.',
  'hybrid_work': 'Hybrid work is available.',
  'remote_friendly': 'Remote work is available.',
  'english_environment': 'English can be used day to day.',
  'product_company': 'This looks like a product company rather than pure contracting.',
  'engineering_culture': 'The post gives real engineering signals.',
  'engineering_environment': 'The engineering setup is described clearly.',
  'inhouse_product_engineering': 'The work appears close to an in-house product team.',
  'global_team_collaboration': 'The team appears used to working across countries.',
  'english_support_environment': 'The company mentions support for English-speaking hires.',
  'visa_sponsorship_support': 'Visa support is mentioned.',
  'work_life_balance': 'The post signals a sustainable work setup.',
  'stability': 'The company appears reasonably stable.',
  'company_reputation_positive': 'Employer reputation reads as positive.',
  'company_reputation_positive_strong': 'Employer reputation reads as strongly positive.',
  'candidate_stack_fit': 'Your shipped stack overlaps meaningfully with the role.',
  'candidate_domain_fit': 'The domain lines up with your recent experience.',
  'candidate_seniority_fit': 'The seniority level looks aligned.',
  'product_pm_collaboration': 'The role appears close to product collaboration.',
  'engineering_maturity': 'The team sounds operationally mature.',
  'casual_interview': 'The interview flow looks relatively low-friction.',
  'async_communication': 'Async communication appears to be part of the culture.',
  'real_flextime': 'Flexible hours look real, not cosmetic.',
  'low_overtime_disclosed': 'Overtime looks low or explicitly bounded.',
  'salary_low_confidence': 'Salary is unclear or incomplete.',
  'salary_below_persona_floor': 'The published salary range sits below this persona floor.',
  'onsite_bias': 'The role is onsite-first, which makes it less attractive by default.',
  'onsite_only': 'The role is fully onsite.',
  'japanese_assignment_dependency': 'Some responsibilities may depend on Japanese ability.',
  'language_friction': 'Japanese language expectations may slow the path to offer or onboarding.',
  'language_friction_critical': 'Japanese language expectations look like a major blocker.',
  'consulting_risk': 'The role may lean toward consulting or client-service work.',
  'overtime_risk': 'The workload may be heavier than ideal.',
  'engineering_environment_risk': 'The post gives weak evidence about engineering quality.',
  'startup_risk': 'The company setup may carry more startup volatility.',
  'role_mismatch_manager_vs_ic_title': 'The title and likely day-to-day scope may not match.',
  'role_identity_mismatch': 'The role identity looks off relative to your target path.',
  'intermediary_contract_risk': 'The employer setup may involve an intermediary or indirect contract.',
  'anonymous_employer_risk': 'The post does not clearly identify the employer.',
  'generic_marketing_post_risk': 'The post reads more like marketing than a concrete role brief.',
  'vague_conditions_risk': 'Working conditions are too vague to assess with confidence.',
  'inclusion_contradiction': 'The inclusion messaging appears inconsistent.',
  'pre_ipo_risk': 'The company stage may add execution risk.',
  'manager_scope_salary_misaligned': 'The scope looks senior for the listed salary.',
  'workload_policy_risk': 'Time-off or workload policies raise caution.',
  'holiday_policy_risk': 'Leave or holiday policy looks weak or unclear.',
  'location_mobility_risk': 'Location or relocation expectations may be restrictive.',
  'salary_range_anomaly': 'The salary range looks inconsistent or unusually wide.',
  'debt_first_culture_risk': 'The team may be carrying significant legacy or debt load.',
  'hypergrowth_execution_risk': 'The pace of change may create execution noise.',
  'company_reputation_risk': 'Employer reputation raises caution.',
  'company_reputation_risk_high': 'Employer reputation raises a strong caution.',
  'candidate_stack_gap': 'The stack overlap looks too thin.',
  'candidate_domain_gap': 'The domain differs from your recent focus.',
  'candidate_seniority_mismatch': 'The level looks meaningfully off.',
  'algorithmic_interview_risk': 'The interview process may over-index on algorithms.',
  'pressure_culture_risk': 'The culture may skew high-pressure.',
  'fake_flextime_risk': 'Flexibility may be more nominal than real.',
  'traditional_corporate_process_risk': 'The process looks more traditional and slower-moving.',
  'customer_site_risk': 'The work may depend on spending time at customer sites.',
};

Component _tableHeaderCell(ReportTableColumn column) {
  return th(scope: 'col', classes: column.width.className, [
    div(
      classes: 'table-header-cell',
      attributes: const {'title': 'Drag the corner of this header to resize the column'},
      [
        span(classes: 'table-header-label', [.text(column.label)]),
        span(classes: 'table-header-grip', attributes: const {'aria-hidden': 'true'}, [.text('::')]),
      ],
    ),
  ]);
}
