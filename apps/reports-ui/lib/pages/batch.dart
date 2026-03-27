import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/batch_item_payload.dart';
import '../models/reports_index_payload.dart';
import '../services/batch_report_parser.dart';
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
  static const _description =
      'Scan one batch report, filter verdicts, compare scores, and jump into the full reasoning behind each evaluated job.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;
  String? _selectedRunId;

  String _verdictFilter = 'ALL';
  String _sortBy = 'score_desc';

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
    final requestedRunId = currentQueryParams(context)['run'];
    if (!kIsWeb) return pageLoading('Batch', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Batch', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Batch', 'Loading batch artifacts...', description: _description);
    }
    if (index == null || index.runs.isEmpty) return pageEmpty('Batch', 'No runs available.', description: _description);
    final run = selectRun(index.runs, _selectedRunId ?? requestedRunId);
    if (run == null) return pageEmpty('Batch', 'Selected run was not found.', description: _description);
    final batchReport = run.batchEvaluationJsonReports.isEmpty ? null : run.batchEvaluationJsonReports.first;
    final allItems = batchReport == null
        ? const <BatchItemPayload>[]
        : batchItemsFromDecodedJson(batchReport.decodedJson);
    final generatedAt = batchReport == null ? '' : batchGeneratedAtFromDecodedJson(batchReport.decodedJson);
    final rows = _applyFiltersAndSort(allItems);
    return _BatchBody(
      run: run,
      runs: index.runs,
      issues: index.issues,
      batchReport: batchReport,
      generatedAt: generatedAt,
      allItems: allItems,
      rows: rows,
      verdictFilter: _verdictFilter,
      sortBy: _sortBy,
      onRunSelected: _selectRun,
      onVerdictFilterChange: (v) => setState(() {
        _verdictFilter = v;
      }),
      onSortByChange: (v) => setState(() {
        _sortBy = v;
      }),
    );
  }

  void _selectRun(ReportRunPayload run) {
    setState(() {
      _selectedRunId = run.runId;
    });
  }

  List<BatchItemPayload> _applyFiltersAndSort(List<BatchItemPayload> items) {
    final filtered = items.where((item) {
      if (_verdictFilter == 'ALL') return true;
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

class _BatchBody extends StatelessComponent {
  const _BatchBody({
    required this.run,
    required this.runs,
    required this.issues,
    required this.batchReport,
    required this.generatedAt,
    required this.allItems,
    required this.rows,
    required this.verdictFilter,
    required this.sortBy,
    required this.onRunSelected,
    required this.onVerdictFilterChange,
    required this.onSortByChange,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<ReportIssuePayload> issues;
  final BatchEvaluationJsonPayload? batchReport;
  final String generatedAt;
  final List<BatchItemPayload> allItems;
  final List<BatchItemPayload> rows;
  final String verdictFilter;
  final String sortBy;
  final void Function(ReportRunPayload run) onRunSelected;
  final void Function(String) onVerdictFilterChange;
  final void Function(String) onSortByChange;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      ...pageHeader(
        'Batch',
        'Scan one batch report, filter verdicts, compare scores, and jump into the full reasoning behind each evaluated job.',
      ),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runSelectionTabs(runs: runs, selectedRunId: run.runId, onRunSelected: onRunSelected),
        ...issueList(issues),
        if (batchReport == null)
          p([.text('No batch JSON report found for this run.')])
        else ...[
          div(classes: 'summary-grid', [
            _metric('Run', run.runId),
            _metric('Report file', batchReport!.fileName),
            _metric('Last update', generatedAt.isEmpty ? 'Unknown' : formatFriendlyDateTime(generatedAt)),
            _metric('Visible rows', '${rows.length} / ${allItems.length}'),
          ]),
          _BatchFilters(
            verdictFilter: verdictFilter,
            sortBy: sortBy,
            onVerdictFilterChange: onVerdictFilterChange,
            onSortByChange: onSortByChange,
          ),
          _BatchTable(rows: rows, allItems: allItems, runId: run.runId, generatedAt: generatedAt),
        ],
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

class _BatchFilters extends StatelessComponent {
  const _BatchFilters({
    required this.verdictFilter,
    required this.sortBy,
    required this.onVerdictFilterChange,
    required this.onSortByChange,
  });

  final String verdictFilter;
  final String sortBy;
  final void Function(String) onVerdictFilterChange;
  final void Function(String) onSortByChange;

  @override
  Component build(BuildContext context) {
    return div(classes: 'controls', [
      label([
        .text('Verdict'),
        select(
          value: verdictFilter,
          onChange: (values) => onVerdictFilterChange(values.isEmpty ? 'ALL' : values.first),
          [
            option(value: 'ALL', selected: verdictFilter == 'ALL', [.text('All')]),
            option(value: 'GO', selected: verdictFilter == 'GO', [.text('GO')]),
            option(value: 'GO_WITH_CAUTION', selected: verdictFilter == 'GO_WITH_CAUTION', [.text('GO_WITH_CAUTION')]),
            option(value: 'NO_GO', selected: verdictFilter == 'NO_GO', [.text('NO_GO')]),
          ],
        ),
      ]),
      label([
        .text('Sort'),
        select(
          value: sortBy,
          onChange: (values) => onSortByChange(values.isEmpty ? 'score_desc' : values.first),
          [
            option(value: 'score_desc', selected: sortBy == 'score_desc', [.text('Score high to low')]),
            option(value: 'score_asc', selected: sortBy == 'score_asc', [.text('Score low to high')]),
            option(value: 'company_asc', selected: sortBy == 'company_asc', [.text('Company A-Z')]),
            option(value: 'verdict_asc', selected: sortBy == 'verdict_asc', [.text('Verdict A-Z')]),
          ],
        ),
      ]),
    ]);
  }
}

class _BatchTable extends StatelessComponent {
  const _BatchTable({
    required this.rows,
    required this.allItems,
    required this.runId,
    required this.generatedAt,
  });

  final List<BatchItemPayload> rows;
  final List<BatchItemPayload> allItems;
  final String runId;
  final String generatedAt;

  @override
  Component build(BuildContext context) {
    final formattedGeneratedAt = generatedAt.isEmpty ? 'Unknown' : formatFriendlyDateTime(generatedAt);
    return div([
      p([.text('Showing ${rows.length} items (from ${allItems.length}).')]),
      if (rows.isEmpty)
        p([.text('No items after filters.')])
      else
        reportTable(
          classes: 'batch-table',
          columns: const [
            ReportTableColumn('Company'),
            ReportTableColumn('Title', width: ReportTableWidth.xwide),
            ReportTableColumn('Verdict', width: ReportTableWidth.compact),
            ReportTableColumn('Score', width: ReportTableWidth.compact),
            ReportTableColumn('Change'),
            ReportTableColumn('Last update'),
            ReportTableColumn('Detail', width: ReportTableWidth.compact),
          ],
          rows: [
            for (final item in rows)
              tr([
                td([.text(item.company)]),
                td([.text(item.title)]),
                td([.text(item.verdict)]),
                td([.text(item.score == null ? 'n/a' : '${item.score}/100')]),
                td([.text(item.changeStatus.isEmpty ? '-' : item.changeStatus)]),
                td([.text(formattedGeneratedAt)]),
                td([
                  Link(
                    to: buildRouteWithQuery('/job', {'run': runId, 'job': item.jobId}),
                    child: .text('Open'),
                  ),
                ]),
              ]),
          ],
        ),
    ]);
  }
}
