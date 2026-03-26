import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/reports_index_payload.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class WeeklyPage extends StatefulComponent {
  const WeeklyPage({super.key});

  @override
  State<WeeklyPage> createState() => _WeeklyPageState();
}

class _WeeklyPageState extends State<WeeklyPage> {
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
    final requestedWeeklyId = queryParams['weekly'];
    if (!kIsWeb) return pageLoading('Weekly Digest', 'Loading report index on the client...');
    if (_isLoading) return pageLoading('Weekly Digest', 'Loading weekly artifacts...');
    if (_loadError != null) return pageError('Weekly Digest', _loadError!, _loadIndex);
    final index = _index;
    if (index == null || index.runs.isEmpty) return pageEmpty('Weekly Digest', 'No runs available.');
    final run = selectRun(index.runs, requestedRunId);
    if (run == null) return pageEmpty('Weekly Digest', 'Selected run was not found.');
    if (run.weeklyDigestReports.isEmpty) {
      return pageEmpty('Weekly Digest', 'Run ${run.runId} has no weekly digest markdown.');
    }
    final selected = _resolveSelected(run.weeklyDigestReports, requestedWeeklyId);
    return _WeeklyBody(run: run, runs: index.runs, reports: run.weeklyDigestReports, selected: selected);
  }

  WeeklyDigestPayload _resolveSelected(List<WeeklyDigestPayload> reports, String? requestedId) {
    if (requestedId == null || requestedId.isEmpty) return reports.first;
    for (final report in reports) {
      if (report.weeklyId == requestedId) return report;
    }
    return reports.first;
  }
}

class _WeeklyBody extends StatelessComponent {
  const _WeeklyBody({
    required this.run,
    required this.runs,
    required this.reports,
    required this.selected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<WeeklyDigestPayload> reports;
  final WeeklyDigestPayload selected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      h1([.text('Weekly Digest')]),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runTabs(runs: runs, selectedRunId: run.runId, destinationPath: '/weekly'),
        if (reports.length > 1)
          div(classes: 'digest-tabs', [
            for (final report in reports)
              Link(
                to: buildRouteWithQuery('/weekly', {'run': run.runId, 'weekly': report.weeklyId}),
                classes: report.weeklyId == selected.weeklyId ? 'digest-tab active' : 'digest-tab',
                child: .text(report.weeklyId),
              ),
          ]),
        pre(classes: 'markdown', [.text(selected.markdownContent)]),
      ]),
    ]);
  }
}
