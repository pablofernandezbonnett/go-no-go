import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/reports_index_payload.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class RunsPage extends StatefulComponent {
  const RunsPage({super.key});

  @override
  State<RunsPage> createState() => _RunsPageState();
}

class _RunsPageState extends State<RunsPage> {
  static const _client = ReportsApiClient();
  static const _description =
      'Review discovered report runs, check which artifacts exist, and jump into the screens generated from each run.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;

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
    if (!kIsWeb) return pageLoading('Runs', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Runs', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Runs', 'Loading report index...', description: _description);
    }
    if (index == null) return pageEmpty('Runs', 'No report index data available.', description: _description);
    return _RunsBody(index: index);
  }
}

class _RunsBody extends StatelessComponent {
  const _RunsBody({required this.index});

  final ReportsIndexPayload index;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      ...pageHeader(
        'Runs',
        'Review discovered report runs, check which artifacts exist, and jump into the screens generated from each run.',
      ),
      card([
        p([.text(index.reportsRootExists ? 'Reports root is available.' : 'Reports root does not exist.')]),
        if (!index.reportsRootExists)
          p([
            .text('Tip: run server with '),
            code([.text('REPORTS_ROOT=../../services/engine/output jaspr serve')]),
          ]),
        ...issueList(index.issues),
      ]),
      if (index.runs.isEmpty)
        card([
          p([.text('No runs discovered yet. Generate reports with the engine first.')]),
        ])
      else
        _RunsTable(runs: index.runs),
    ]);
  }
}

class _RunsTable extends StatelessComponent {
  const _RunsTable({required this.runs});

  final List<ReportRunPayload> runs;

  @override
  Component build(BuildContext context) {
    return card([
      reportTable(
        classes: 'runs-table',
        columns: const [
          ReportTableColumn('Run'),
          ReportTableColumn('Batch JSON', width: ReportTableWidth.compact),
          ReportTableColumn('Batch MD', width: ReportTableWidth.compact),
          ReportTableColumn('Weekly', width: ReportTableWidth.compact),
          ReportTableColumn('Context', width: ReportTableWidth.compact),
          ReportTableColumn('Trends', width: ReportTableWidth.compact),
          ReportTableColumn('Actions', width: ReportTableWidth.wide),
        ],
        rows: [
          for (final run in runs)
            tr([
              td([
                code([.text(run.runId)]),
              ]),
              td([.text('${run.batchEvaluationJsonReports.length}')]),
              td([.text('${run.batchEvaluationMarkdownReports.length}')]),
              td([.text('${run.weeklyDigestReports.length}')]),
              td([.text('${run.companyContextReports.length}')]),
              td([.text('${run.trendHistoryReports.length + run.trendAlertsReports.length}')]),
              td([
                div(classes: 'actions', [
                  Link(to: buildRouteWithQuery('/batch', {'run': run.runId}), child: .text('Batch')),
                  Link(to: buildRouteWithQuery('/weekly', {'run': run.runId}), child: .text('Weekly')),
                  Link(to: buildRouteWithQuery('/context', {'run': run.runId}), child: .text('Context')),
                  Link(to: buildRouteWithQuery('/trends', {'run': run.runId}), child: .text('Trends')),
                ]),
              ]),
            ]),
        ],
      ),
    ]);
  }
}
