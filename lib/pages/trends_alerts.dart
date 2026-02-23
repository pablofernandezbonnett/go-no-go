import 'dart:convert';

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

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
  static const _prettyJson = JsonEncoder.withIndent('  ');

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
    final routeState = RouteState.maybeOf(context);
    final queryParams = routeState?.queryParams ?? const <String, String>{};
    final requestedRunId = queryParams['run'];

    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Trends & Alerts')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Trends & Alerts')]),
        p([.text('Loading trend artifacts...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Trends & Alerts')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null || index.runs.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Trends & Alerts')]),
        card([
          p([.text('No runs available.')]),
        ]),
      ]);
    }

    final run = selectRun(index.runs, requestedRunId);
    if (run == null) {
      return section(classes: 'page', [
        h1([.text('Trends & Alerts')]),
        card([
          p([.text('Selected run was not found.')]),
        ]),
      ]);
    }

    final trendHistory = run.trendHistoryReports.isEmpty ? null : run.trendHistoryReports.first;
    final trendAlerts = run.trendAlertsReports.isEmpty ? null : run.trendAlertsReports.first;

    return section(classes: 'page', [
      h1([.text('Trends & Alerts')]),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runTabs(
          runs: index.runs,
          selectedRunId: run.runId,
          destinationPath: '/trends',
        ),
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
    if (!payload.isValidJson || payload.decodedJson == null) {
      return payload.rawJson;
    }

    try {
      return _prettyJson.convert(payload.decodedJson);
    } catch (_) {
      return payload.rawJson;
    }
  }
}
