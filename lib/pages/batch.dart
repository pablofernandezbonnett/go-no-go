import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/batch_item_payload.dart';
import '../models/reports_index_payload.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class BatchPage extends StatefulComponent {
  const BatchPage({super.key});

  @override
  State<BatchPage> createState() => _BatchPageState();
}

class _BatchPageState extends State<BatchPage> {
  static const _client = ReportsApiClient();

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;

  String _verdictFilter = 'ALL';
  String _sortBy = 'score_desc';

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
    final routeState = RouteState.maybeOf(context);
    final queryParams = routeState?.queryParams ?? const <String, String>{};
    final requestedRunId = queryParams['run'];

    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Batch')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Batch')]),
        p([.text('Loading batch artifacts...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Batch')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null || index.runs.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Batch')]),
        card([
          p([.text('No runs available.')]),
        ]),
      ]);
    }

    final run = selectRun(index.runs, requestedRunId);
    if (run == null) {
      return section(classes: 'page', [
        h1([.text('Batch')]),
        card([
          p([.text('Selected run was not found.')]),
        ]),
      ]);
    }

    final batchReport = run.batchEvaluationJsonReports.isEmpty ? null : run.batchEvaluationJsonReports.first;
    final allItems = batchReport == null
        ? const <BatchItemPayload>[]
        : batchItemsFromDecodedJson(batchReport.decodedJson);
    final rows = _applyFiltersAndSort(allItems);

    return section(classes: 'page', [
      h1([.text('Batch')]),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runTabs(
          runs: index.runs,
          selectedRunId: run.runId,
          destinationPath: '/batch',
        ),
        ...issueList(index.issues),
        if (batchReport == null)
          p([.text('No batch JSON report found for this run.')])
        else ...[
          p([.text('Report: ${batchReport.relativePath}')]),
          div(classes: 'controls', [
            label([
              .text('Verdict'),
              select(
                value: _verdictFilter,
                onChange: (values) {
                  setState(() {
                    _verdictFilter = values.isEmpty ? 'ALL' : values.first;
                  });
                },
                [
                  option(value: 'ALL', selected: _verdictFilter == 'ALL', [.text('All')]),
                  option(value: 'GO', selected: _verdictFilter == 'GO', [.text('GO')]),
                  option(
                    value: 'GO_WITH_CAUTION',
                    selected: _verdictFilter == 'GO_WITH_CAUTION',
                    [.text('GO_WITH_CAUTION')],
                  ),
                  option(value: 'NO_GO', selected: _verdictFilter == 'NO_GO', [.text('NO_GO')]),
                ],
              ),
            ]),
            label([
              .text('Sort'),
              select(
                value: _sortBy,
                onChange: (values) {
                  setState(() {
                    _sortBy = values.isEmpty ? 'score_desc' : values.first;
                  });
                },
                [
                  option(value: 'score_desc', selected: _sortBy == 'score_desc', [.text('Score high to low')]),
                  option(value: 'score_asc', selected: _sortBy == 'score_asc', [.text('Score low to high')]),
                  option(value: 'company_asc', selected: _sortBy == 'company_asc', [.text('Company A-Z')]),
                  option(value: 'verdict_asc', selected: _sortBy == 'verdict_asc', [.text('Verdict A-Z')]),
                ],
              ),
            ]),
          ]),
          p([.text('Showing ${rows.length} items (from ${allItems.length}).')]),
          if (rows.isEmpty)
            p([.text('No items after filters.')])
          else
            table(classes: 'batch-table', [
              thead([
                tr([
                  th([.text('Company')]),
                  th([.text('Title')]),
                  th([.text('Verdict')]),
                  th([.text('Score')]),
                  th([.text('Change')]),
                  th([.text('Detail')]),
                ]),
              ]),
              tbody([
                for (final item in rows)
                  tr([
                    td([.text(item.company)]),
                    td([.text(item.title)]),
                    td([.text(item.verdict)]),
                    td([.text(item.score == null ? 'n/a' : '${item.score}/100')]),
                    td([.text(item.changeStatus.isEmpty ? '-' : item.changeStatus)]),
                    td([
                      a(
                        href: buildRouteWithQuery('/job', {
                          'run': run.runId,
                          'job': item.jobId,
                        }),
                        [.text('Open')],
                      ),
                    ]),
                  ]),
              ]),
            ]),
        ],
      ]),
    ]);
  }

  List<BatchItemPayload> _applyFiltersAndSort(List<BatchItemPayload> items) {
    final filtered = items.where((item) {
      if (_verdictFilter == 'ALL') {
        return true;
      }
      return item.verdict == _verdictFilter;
    }).toList();

    filtered.sort((left, right) {
      switch (_sortBy) {
        case 'score_asc':
          return (left.score ?? -1).compareTo(right.score ?? -1);
        case 'company_asc':
          return left.company.compareTo(right.company);
        case 'verdict_asc':
          return left.verdict.compareTo(right.verdict);
        case 'score_desc':
        default:
          return (right.score ?? -1).compareTo(left.score ?? -1);
      }
    });

    return filtered;
  }
}
