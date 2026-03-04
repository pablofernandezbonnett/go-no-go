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

    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        p([.text('Loading job details...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null || index.runs.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        card([
          p([.text('No runs available.')]),
        ]),
      ]);
    }

    final run = selectRun(index.runs, requestedRunId);
    if (run == null) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        card([
          p([.text('Selected run was not found.')]),
        ]),
      ]);
    }

    if (run.batchEvaluationJsonReports.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        card([
          p([.text('No batch JSON report found for this run.')]),
        ]),
      ]);
    }

    final items = batchItemsFromDecodedJson(run.batchEvaluationJsonReports.first.decodedJson);
    if (items.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Job Detail')]),
        card([
          p([.text('This run has no evaluated items.')]),
        ]),
      ]);
    }

    BatchItemPayload selected = items.first;
    if (requestedJobId != null && requestedJobId.isNotEmpty) {
      for (final item in items) {
        if (item.jobId == requestedJobId) {
          selected = item;
          break;
        }
      }
    }

    return section(classes: 'page', [
      h1([.text('Job Detail')]),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runTabs(
          runs: index.runs,
          selectedRunId: run.runId,
          destinationPath: '/job',
          extraQueryKey: 'job',
          extraQueryValue: selected.jobId,
        ),
        p([
          Link(
            to: buildRouteWithQuery('/batch', {'run': run.runId}),
            child: .text('Back to Batch'),
          ),
        ]),
      ]),
      card([
        h2([.text(selected.title)]),
        p([
          strong([.text(selected.company)]),
        ]),
        div(classes: 'summary-grid', [
          _metric('Verdict', selected.verdict),
          _metric('Score', selected.score == null ? 'n/a' : '${selected.score}/100'),
          _metric(
            'Language Friction',
            selected.languageFrictionIndex == null ? 'n/a' : '${selected.languageFrictionIndex}/100',
          ),
          _metric(
            'Company Reputation',
            selected.companyReputationIndex == null ? 'n/a' : '${selected.companyReputationIndex}/100',
          ),
          _metric('Change', selected.changeStatus.isEmpty ? '-' : selected.changeStatus),
          _metric('Remote Policy', selected.remotePolicy.isEmpty ? '-' : selected.remotePolicy),
          _metric('Location', selected.location.isEmpty ? '-' : selected.location),
          _metric('Salary', selected.salaryRange.isEmpty ? '-' : selected.salaryRange),
        ]),
      ]),
      card([
        h3([.text('Positive Signals')]),
        _listOrFallback(selected.positiveSignals, 'No positive signals.'),
        h3([.text('Risk Signals')]),
        _listOrFallback(selected.riskSignals, 'No risk signals.'),
        h3([.text('Hard Reject Reasons')]),
        _listOrFallback(selected.hardRejectReasons, 'No hard reject reasons.'),
        h3([.text('Reasoning')]),
        _listOrFallback(selected.reasoning, 'No reasoning details.'),
        h3([.text('Source')]),
        p([
          code([.text(selected.sourceFile.isEmpty ? selected.jobId : selected.sourceFile)]),
        ]),
      ]),
      if (items.length > 1) ...[
        card([
          h3([.text('Other Jobs in Run')]),
          ul([
            for (final item in items)
              li([
                Link(
                  to: buildRouteWithQuery('/job', {
                    'run': run.runId,
                    'job': item.jobId,
                  }),
                  child: .text('${item.company} - ${item.title}'),
                ),
              ]),
          ]),
        ]),
      ],
    ]);
  }

  Component _metric(String labelText, String valueText) {
    return div(classes: 'metric', [
      div(classes: 'metric-label', [.text(labelText)]),
      div(classes: 'metric-value', [.text(valueText)]),
    ]);
  }

  Component _listOrFallback(List<String> values, String fallback) {
    if (values.isEmpty) {
      return p([.text(fallback)]);
    }
    return ul([
      for (final value in values) li([.text(value)]),
    ]);
  }
}
