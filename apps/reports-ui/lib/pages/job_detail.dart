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
  static const _description =
      'Inspect one evaluated job in depth, including score breakdown, supporting signals, and the reasoning behind the verdict.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;
  String? _selectedRunId;
  String? _selectedJobId;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      final cachedIndex = _client.peekCachedIndex();
      if (cachedIndex != null) {
        _index = cachedIndex;
        _isLoading = false;
      } else {
        _loadIndex();
      }
    } else {
      _isLoading = false;
    }
  }

  Future<void> _loadIndex({bool forceRefresh = false}) async {
    setState(() {
      _isLoading = _index == null || forceRefresh;
      _loadError = null;
    });
    try {
      final index = await _client.fetchIndex(forceRefresh: forceRefresh);
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
    if (!kIsWeb) return pageLoading('Job Detail', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Job Detail', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Job Detail', 'Loading job details...', description: _description);
    }
    if (index == null || index.runs.isEmpty) return pageEmpty('Job Detail', 'No runs available.', description: _description);
    final run = selectRun(index.runs, _selectedRunId ?? requestedRunId);
    if (run == null) return pageEmpty('Job Detail', 'Selected run was not found.', description: _description);
    if (run.batchEvaluationJsonReports.isEmpty) {
      return pageEmpty('Job Detail', 'No batch JSON report found for this run.', description: _description);
    }
    final items = batchItemsFromDecodedJson(run.batchEvaluationJsonReports.first.decodedJson);
    if (items.isEmpty) return pageEmpty('Job Detail', 'This run has no evaluated items.', description: _description);
    final selected = _resolveSelected(items, _selectedJobId ?? requestedJobId);
    return _JobDetailBody(
      run: run,
      runs: index.runs,
      items: items,
      selected: selected,
      onRunSelected: _selectRun,
      onJobSelected: _selectJob,
    );
  }

  BatchItemPayload _resolveSelected(List<BatchItemPayload> items, String? requestedJobId) {
    if (requestedJobId == null || requestedJobId.isEmpty) return items.first;
    for (final item in items) {
      if (item.jobId == requestedJobId) return item;
    }
    return items.first;
  }

  void _selectRun(ReportRunPayload run) {
    final batchReport = run.batchEvaluationJsonReports.isEmpty ? null : run.batchEvaluationJsonReports.first;
    final nextItems = batchReport == null ? const <BatchItemPayload>[] : batchItemsFromDecodedJson(batchReport.decodedJson);
    setState(() {
      _selectedRunId = run.runId;
      _selectedJobId = nextItems.isEmpty ? null : nextItems.first.jobId;
    });
  }

  void _selectJob(String jobId) {
    setState(() {
      _selectedJobId = jobId;
    });
  }
}

class _JobDetailBody extends StatelessComponent {
  const _JobDetailBody({
    required this.run,
    required this.runs,
    required this.items,
    required this.selected,
    required this.onRunSelected,
    required this.onJobSelected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<BatchItemPayload> items;
  final BatchItemPayload selected;
  final void Function(ReportRunPayload run) onRunSelected;
  final void Function(String jobId) onJobSelected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      ...pageHeader(
        'Job Detail',
        'Inspect one evaluated job in depth, including score breakdown, supporting signals, and the reasoning behind the verdict.',
      ),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runSelectionTabs(runs: runs, selectedRunId: run.runId, onRunSelected: onRunSelected),
        p([Link(to: buildRouteWithQuery('/batch', {'run': run.runId}), child: .text('Back to Batch'))]),
      ]),
      _JobSummaryCard(selected: selected),
      _JobSignalsCard(selected: selected),
      if (items.length > 1) _JobOthersList(run: run, items: items, selectedJobId: selected.jobId, onJobSelected: onJobSelected),
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
  const _JobOthersList({
    required this.run,
    required this.items,
    required this.selectedJobId,
    required this.onJobSelected,
  });

  final ReportRunPayload run;
  final List<BatchItemPayload> items;
  final String selectedJobId;
  final void Function(String jobId) onJobSelected;

  @override
  Component build(BuildContext context) {
    return card([
      h3([.text('Other Jobs in Run')]),
      ul([
        for (final item in items)
          li([
            button(
              classes: item.jobId == selectedJobId ? 'run-tab active' : 'run-tab',
              onClick: () => onJobSelected(item.jobId),
              [.text('${item.company} - ${item.title}')],
            ),
          ]),
      ]),
    ]);
  }
}
