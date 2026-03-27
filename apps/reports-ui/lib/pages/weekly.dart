import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
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
  static const _description =
      'Read generated weekly digests for each run and switch between digest variants without leaving the current screen.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;
  String? _selectedRunId;
  String? _selectedWeeklyId;

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
    final requestedWeeklyId = queryParams['weekly'];
    if (!kIsWeb) return pageLoading('Weekly Digest', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Weekly Digest', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Weekly Digest', 'Loading weekly artifacts...', description: _description);
    }
    if (index == null || index.runs.isEmpty) return pageEmpty('Weekly Digest', 'No runs available.', description: _description);
    final run = selectRun(index.runs, _selectedRunId ?? requestedRunId);
    if (run == null) return pageEmpty('Weekly Digest', 'Selected run was not found.', description: _description);
    if (run.weeklyDigestReports.isEmpty) {
      return pageEmpty('Weekly Digest', 'Run ${run.runId} has no weekly digest markdown.', description: _description);
    }
    final selected = _resolveSelected(run.weeklyDigestReports, _selectedWeeklyId ?? requestedWeeklyId);
    return _WeeklyBody(
      run: run,
      runs: index.runs,
      reports: run.weeklyDigestReports,
      selected: selected,
      onRunSelected: _selectRun,
      onWeeklySelected: _selectWeeklyDigest,
    );
  }

  WeeklyDigestPayload _resolveSelected(List<WeeklyDigestPayload> reports, String? requestedId) {
    if (requestedId == null || requestedId.isEmpty) return reports.first;
    for (final report in reports) {
      if (report.weeklyId == requestedId) return report;
    }
    return reports.first;
  }

  void _selectWeeklyDigest(String weeklyId) {
    setState(() {
      _selectedWeeklyId = weeklyId;
    });
  }

  void _selectRun(ReportRunPayload run) {
    final defaultWeeklyId = run.weeklyDigestReports.isEmpty ? null : run.weeklyDigestReports.first.weeklyId;
    setState(() {
      _selectedRunId = run.runId;
      _selectedWeeklyId = defaultWeeklyId;
    });
  }
}

class _WeeklyBody extends StatelessComponent {
  const _WeeklyBody({
    required this.run,
    required this.runs,
    required this.reports,
    required this.selected,
    this.onRunSelected,
    this.onWeeklySelected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<WeeklyDigestPayload> reports;
  final WeeklyDigestPayload selected;
  final void Function(ReportRunPayload run)? onRunSelected;
  final void Function(String weeklyId)? onWeeklySelected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      ...pageHeader(
        'Weekly Digest',
        'Read generated weekly digests for each run and switch between digest variants without leaving the current screen.',
      ),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runSelectionTabs(
          runs: runs,
          selectedRunId: run.runId,
          onRunSelected: onRunSelected ?? (_) {},
        ),
        if (reports.length > 1)
          div(classes: 'digest-tabs', [
            for (final report in reports)
              button(
                classes: report.weeklyId == selected.weeklyId ? 'digest-tab active' : 'digest-tab',
                onClick: onWeeklySelected == null ? null : () => onWeeklySelected!(report.weeklyId),
                [.text(report.weeklyId)],
              ),
          ]),
        artifactViewer(
          title: selected.weeklyId,
          subtitle: 'Run ${run.runId} · ${selected.fileName}',
          formatLabel: 'Markdown',
          content: selected.markdownContent,
          preClasses: 'markdown-artifact',
        ),
      ]),
    ]);
  }
}
