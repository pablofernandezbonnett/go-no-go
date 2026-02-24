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

    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        p([.text('Loading weekly artifacts...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null || index.runs.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        card([
          p([.text('No runs available.')]),
        ]),
      ]);
    }

    final run = selectRun(index.runs, requestedRunId);
    if (run == null) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        card([
          p([.text('Selected run was not found.')]),
        ]),
      ]);
    }

    final reports = run.weeklyDigestReports;
    if (reports.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Weekly Digest')]),
        card([
          p([
            .text('Run '),
            code([.text(run.runId)]),
            .text(' has no weekly digest markdown.'),
          ]),
        ]),
      ]);
    }

    var selected = reports.first;
    if (requestedWeeklyId != null && requestedWeeklyId.isNotEmpty) {
      for (final report in reports) {
        if (report.weeklyId == requestedWeeklyId) {
          selected = report;
          break;
        }
      }
    }

    return section(classes: 'page', [
      h1([.text('Weekly Digest')]),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runTabs(
          runs: index.runs,
          selectedRunId: run.runId,
          destinationPath: '/weekly',
        ),
        if (reports.length > 1)
          div(classes: 'digest-tabs', [
            for (final report in reports)
              Link(
                to: buildRouteWithQuery('/weekly', {
                  'run': run.runId,
                  'weekly': report.weeklyId,
                }),
                classes: report.weeklyId == selected.weeklyId ? 'digest-tab active' : 'digest-tab',
                child: .text(report.weeklyId),
              ),
          ]),
        p([.text('File: ${selected.relativePath}')]),
        pre(classes: 'markdown', [.text(selected.markdownContent)]),
      ]),
    ]);
  }
}
