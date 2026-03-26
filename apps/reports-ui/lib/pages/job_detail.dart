import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/batch_item_payload.dart';
import '../models/reports_index_payload.dart';
import '../services/batch_report_parser.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class JobDetailPage extends StatefulComponent {
  const JobDetailPage({super.key});

  @override
  State<JobDetailPage> createState() => _JobDetailPageState();
}

class _JobDetailPageState extends State<JobDetailPage> {
  static const _client = ReportsApiClient();

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _loadIndex();
    } else {
      _isLoading = false;
    }
  }

  Future<void> _loadIndex() async {
    setState(() {
      _isLoading = true;
      _loadError = null;
    });
    try {
      final index = await _client.fetchIndex();
      setState(() {
        _index = index;
        _isLoading = false;
      });
    } catch (error) {
      setState(() {
        _isLoading = false;
        _loadError = error.toString();
      });
    }
  }

  @override
  Component build(BuildContext context) {
    final queryParams = currentQueryParams(context);
    final requestedRunId = queryParams['run'];
    final requestedJobId = queryParams['job'];
    if (!kIsWeb) return pageLoading('Job Detail', 'Loading report index on the client...');
    if (_isLoading) return pageLoading('Job Detail', 'Loading job details...');
    if (_loadError != null) return pageError('Job Detail', _loadError!, _loadIndex);
    final index = _index;
    if (index == null || index.runs.isEmpty) return pageEmpty('Job Detail', 'No runs available.');
    final run = selectRun(index.runs, requestedRunId);
    if (run == null) return pageEmpty('Job Detail', 'Selected run was not found.');
    if (run.batchEvaluationJsonReports.isEmpty) {
      return pageEmpty('Job Detail', 'No batch JSON report found for this run.');
    }
    final items = batchItemsFromDecodedJson(run.batchEvaluationJsonReports.first.decodedJson);
    if (items.isEmpty) return pageEmpty('Job Detail', 'This run has no evaluated items.');
    final selected = _resolveSelected(items, requestedJobId);
    return _JobDetailBody(run: run, runs: index.runs, items: items, selected: selected);
  }

  BatchItemPayload _resolveSelected(List<BatchItemPayload> items, String? requestedJobId) {
    if (requestedJobId == null || requestedJobId.isEmpty) return items.first;
    for (final item in items) {
      if (item.jobId == requestedJobId) return item;
    }
    return items.first;
  }
}

class _JobDetailBody extends StatelessComponent {
  const _JobDetailBody({
    required this.run,
    required this.runs,
    required this.items,
    required this.selected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<BatchItemPayload> items;
  final BatchItemPayload selected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      h1([.text('Job Detail')]),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runTabs(
          runs: runs,
          selectedRunId: run.runId,
          destinationPath: '/job',
          extraQueryKey: 'job',
          extraQueryValue: selected.jobId,
        ),
        p([Link(to: buildRouteWithQuery('/batch', {'run': run.runId}), child: .text('Back to Batch'))]),
      ]),
      _JobSummaryCard(selected: selected),
      _JobSignalsCard(selected: selected),
      if (items.length > 1) _JobOthersList(run: run, items: items),
    ]);
  }
}

class _JobSummaryCard extends StatelessComponent {
  const _JobSummaryCard({required this.selected});

  final BatchItemPayload selected;

  @override
  Component build(BuildContext context) {
    return card([
      h2([.text(selected.title)]),
      p([strong([.text(selected.company)])]),
      div(classes: 'summary-grid', [
        _metric('Verdict', selected.verdict),
        _metric('Score', selected.score == null ? 'n/a' : '${selected.score}/100'),
        _metric('Language Friction',
            selected.languageFrictionIndex == null ? 'n/a' : '${selected.languageFrictionIndex}/100'),
        _metric('Company Reputation',
            selected.companyReputationIndex == null ? 'n/a' : '${selected.companyReputationIndex}/100'),
        _metric('Change', selected.changeStatus.isEmpty ? '-' : selected.changeStatus),
        _metric('Remote Policy', selected.remotePolicy.isEmpty ? '-' : selected.remotePolicy),
        _metric('Location', selected.location.isEmpty ? '-' : selected.location),
        _metric('Salary', selected.salaryRange.isEmpty ? '-' : selected.salaryRange),
      ]),
    ]);
  }

  Component _metric(String labelText, String valueText) {
    return div(classes: 'metric', [
      div(classes: 'metric-label', [.text(labelText)]),
      div(classes: 'metric-value', [.text(valueText)]),
    ]);
  }
}

class _JobSignalsCard extends StatelessComponent {
  const _JobSignalsCard({required this.selected});

  final BatchItemPayload selected;

  @override
  Component build(BuildContext context) {
    return card([
      h3([.text('Positive Signals')]),
      _listOrFallback(selected.positiveSignals, 'No positive signals.'),
      h3([.text('Risk Signals')]),
      _listOrFallback(selected.riskSignals, 'No risk signals.'),
      h3([.text('Hard Reject Reasons')]),
      _listOrFallback(selected.hardRejectReasons, 'No hard reject reasons.'),
      h3([.text('Reasoning')]),
      _listOrFallback(selected.reasoning, 'No reasoning details.'),
    ]);
  }

  Component _listOrFallback(List<String> values, String fallback) {
    if (values.isEmpty) return p([.text(fallback)]);
    return ul([for (final value in values) li([.text(value)])]);
  }
}

class _JobOthersList extends StatelessComponent {
  const _JobOthersList({required this.run, required this.items});

  final ReportRunPayload run;
  final List<BatchItemPayload> items;

  @override
  Component build(BuildContext context) {
    return card([
      h3([.text('Other Jobs in Run')]),
      ul([
        for (final item in items)
          li([
            Link(
              to: buildRouteWithQuery('/job', {'run': run.runId, 'job': item.jobId}),
              child: .text('${item.company} - ${item.title}'),
            ),
          ]),
      ]),
    ]);
  }
}
