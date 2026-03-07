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
    final requestedRunId = currentQueryParams(context)['run'];
    if (!kIsWeb) return pageLoading('Trends & Alerts', 'Loading report index on the client...');
    if (_isLoading) return pageLoading('Trends & Alerts', 'Loading trend artifacts...');
    if (_loadError != null) return pageError('Trends & Alerts', _loadError!, _loadIndex);
    final index = _index;
    if (index == null || index.runs.isEmpty) return pageEmpty('Trends & Alerts', 'No runs available.');
    final run = selectRun(index.runs, requestedRunId);
    if (run == null) return pageEmpty('Trends & Alerts', 'Selected run was not found.');
    return _TrendsAlertsBody(run: run, runs: index.runs);
  }
}

class _TrendsAlertsBody extends StatelessComponent {
  const _TrendsAlertsBody({required this.run, required this.runs});

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;

  static const _prettyJson = JsonEncoder.withIndent('  ');

  @override
  Component build(BuildContext context) {
    final trendHistory = run.trendHistoryReports.isEmpty ? null : run.trendHistoryReports.first;
    final trendAlerts = run.trendAlertsReports.isEmpty ? null : run.trendAlertsReports.first;
    return section(classes: 'page', [
      h1([.text('Trends & Alerts')]),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runTabs(runs: runs, selectedRunId: run.runId, destinationPath: '/trends'),
      ]),
      card([
        h2([.text('Trend History')]),
        if (trendHistory == null)
          p([.text('No trend history YAML found for this run.')])
        else ...[
          p([.text('File: ${trendHistory.relativePath}')]),
          pre(classes: 'artifact', [.text(trendHistory.yamlContent)]),
        ],
      ]),
      card([
        h2([.text('Trend Alerts')]),
        if (trendAlerts == null)
          p([.text('No trend alerts JSON found for this run.')])
        else ...[
          p([.text('File: ${trendAlerts.relativePath}')]),
          pre(classes: 'artifact', [.text(_renderAlertsPayload(trendAlerts))]),
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
