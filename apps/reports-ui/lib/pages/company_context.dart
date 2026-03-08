import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';
import 'package:jaspr_router/jaspr_router.dart';

import '../models/reports_index_payload.dart';
import '../services/reports_api.dart';
import 'reports_view_helpers.dart';

@client
class CompanyContextPage extends StatefulComponent {
  const CompanyContextPage({super.key});

  @override
  State<CompanyContextPage> createState() => _CompanyContextPageState();
}

class _CompanyContextPageState extends State<CompanyContextPage> {
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
    final requestedCompanyId = queryParams['company'];
    if (!kIsWeb) return pageLoading('Company Context', 'Loading report index on the client...');
    if (_isLoading) return pageLoading('Company Context', 'Loading context artifacts...');
    if (_loadError != null) return pageError('Company Context', _loadError!, _loadIndex);
    final index = _index;
    if (index == null || index.runs.isEmpty) return pageEmpty('Company Context', 'No runs available.');
    final run = selectRun(index.runs, requestedRunId);
    if (run == null) return pageEmpty('Company Context', 'Selected run was not found.');
    if (run.companyContextReports.isEmpty) {
      return pageEmpty('Company Context', 'Run ${run.runId} has no company context files.');
    }
    final selected = _resolveSelected(run.companyContextReports, requestedCompanyId);
    return _CompanyContextBody(run: run, runs: index.runs, reports: run.companyContextReports, selected: selected);
  }

  CompanyContextPayload _resolveSelected(List<CompanyContextPayload> reports, String? requestedId) {
    if (requestedId == null || requestedId.isEmpty) return reports.first;
    for (final report in reports) {
      if (report.companyId == requestedId) return report;
    }
    return reports.first;
  }
}

class _CompanyContextBody extends StatelessComponent {
  const _CompanyContextBody({
    required this.run,
    required this.runs,
    required this.reports,
    required this.selected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<CompanyContextPayload> reports;
  final CompanyContextPayload selected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      h1([.text('Company Context')]),
      card([
        p([.text('Run: '), code([.text(run.runId)])]),
        runTabs(runs: runs, selectedRunId: run.runId, destinationPath: '/context'),
        div(classes: 'context-tabs', [
          for (final report in reports)
            Link(
              to: buildRouteWithQuery('/context', {'run': run.runId, 'company': report.companyId}),
              classes: report.companyId == selected.companyId ? 'context-tab active' : 'context-tab',
              child: .text(report.companyId),
            ),
        ]),
        p([.text('File: ${selected.relativePath}')]),
        pre(classes: 'context-text', [.text(selected.textContent)]),
      ]),
    ]);
  }
}
