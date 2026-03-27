import 'dart:convert';

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../models/reports_index_payload.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class TrendsAlertsPage extends StatefulComponent {
  const TrendsAlertsPage({super.key});

  @override
  State<TrendsAlertsPage> createState() => _TrendsAlertsPageState();
}

class _TrendsAlertsPageState extends State<TrendsAlertsPage> {
  static const _client = ReportsApiClient();
  static const _description =
      'Inspect trend history and alert artifacts generated from run deltas without exposing raw engine internals in the UI.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;
  String? _selectedRunId;

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
    if (!kIsWeb) return pageLoading('Trends & Alerts', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Trends & Alerts', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Trends & Alerts', 'Loading trend artifacts...', description: _description);
    }
    if (index == null || index.runs.isEmpty) return pageEmpty('Trends & Alerts', 'No runs available.', description: _description);
    final run = selectRun(index.runs, _selectedRunId ?? requestedRunId);
    if (run == null) return pageEmpty('Trends & Alerts', 'Selected run was not found.', description: _description);
    return _TrendsAlertsBody(run: run, runs: index.runs, onRunSelected: _selectRun);
  }

  void _selectRun(ReportRunPayload run) {
    setState(() {
      _selectedRunId = run.runId;
    });
  }
}

class _TrendsAlertsBody extends StatelessComponent {
  const _TrendsAlertsBody({
    required this.run,
    required this.runs,
    required this.onRunSelected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final void Function(ReportRunPayload run) onRunSelected;

  static const _prettyJson = JsonEncoder.withIndent('  ');

  @override
  Component build(BuildContext context) {
    final trendHistory = run.trendHistoryReports.isEmpty ? null : run.trendHistoryReports.first;
    final trendAlerts = run.trendAlertsReports.isEmpty ? null : run.trendAlertsReports.first;
    return section(classes: 'page', [
      ...pageHeader(
        'Trends & Alerts',
        'Inspect trend history and alert artifacts generated from run deltas without exposing raw engine internals in the UI.',
      ),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runSelectionTabs(runs: runs, selectedRunId: run.runId, onRunSelected: onRunSelected),
      ]),
      card([
        h2([.text('Trend History')]),
        if (trendHistory == null)
          p([.text('No trend history YAML found for this run.')])
        else ...[
          artifactViewer(
            title: trendHistory.historyId,
            subtitle: 'Run ${run.runId} · ${trendHistory.fileName}',
            formatLabel: 'YAML',
            content: trendHistory.yamlContent,
            preClasses: 'yaml-artifact',
          ),
        ],
      ]),
      card([
        h2([.text('Trend Alerts')]),
        if (trendAlerts == null)
          p([.text('No trend alerts JSON found for this run.')])
        else ...[
          artifactViewer(
            title: trendAlerts.alertsId,
            subtitle: 'Run ${run.runId} · ${trendAlerts.fileName}',
            formatLabel: 'JSON',
            content: _renderAlertsPayload(trendAlerts),
            preClasses: 'json-artifact',
          ),
        ],
      ]),
    ]);
  }

  String _renderAlertsPayload(TrendAlertsPayload payload) {
    if (!payload.isValidJson || payload.decodedJson == null) return payload.rawJson;
    try {
      return _prettyJson.convert(payload.decodedJson);
    } catch (_) {
      return payload.rawJson;
    }
  }
}
