import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

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
  static const _description =
      'Read the saved company context artifacts for a run and switch companies instantly without remounting the whole page.';

  ReportsIndexPayload? _index;
  String? _loadError;
  bool _isLoading = true;
  String? _selectedRunId;
  String? _selectedCompanyId;

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
    final requestedCompanyId = queryParams['company'];
    if (!kIsWeb) return pageLoading('Company Context', 'Loading report index on the client...', description: _description);
    if (_loadError != null) return pageError('Company Context', _loadError!, _loadIndex, description: _description);
    final index = _index;
    if (_isLoading && index == null) {
      return pageLoading('Company Context', 'Loading context artifacts...', description: _description);
    }
    if (index == null || index.runs.isEmpty) return pageEmpty('Company Context', 'No runs available.', description: _description);
    final run = selectRun(index.runs, _selectedRunId ?? requestedRunId);
    if (run == null) return pageEmpty('Company Context', 'Selected run was not found.', description: _description);
    if (run.companyContextReports.isEmpty) {
      return pageEmpty('Company Context', 'Run ${run.runId} has no company context files.', description: _description);
    }
    final selected = _resolveSelected(run.companyContextReports, _selectedCompanyId ?? requestedCompanyId);
    return _CompanyContextBody(
      run: run,
      runs: index.runs,
      reports: run.companyContextReports,
      selected: selected,
      isRefreshing: _isLoading,
      onRunSelected: _handleRunSelected,
      onCompanySelected: (companyId) => _handleCompanySelected(run.runId, companyId),
    );
  }

  CompanyContextPayload _resolveSelected(List<CompanyContextPayload> reports, String? requestedId) {
    if (requestedId == null || requestedId.isEmpty) return reports.first;
    for (final report in reports) {
      if (report.companyId == requestedId) return report;
    }
    return reports.first;
  }

  void _handleRunSelected(ReportRunPayload run) {
    final defaultCompanyId = run.companyContextReports.isEmpty ? null : run.companyContextReports.first.companyId;
    setState(() {
      _selectedRunId = run.runId;
      _selectedCompanyId = defaultCompanyId;
    });
  }

  void _handleCompanySelected(String runId, String companyId) {
    setState(() {
      _selectedRunId = runId;
      _selectedCompanyId = companyId;
    });
  }
}

class _CompanyContextBody extends StatelessComponent {
  const _CompanyContextBody({
    required this.run,
    required this.runs,
    required this.reports,
    required this.selected,
    required this.isRefreshing,
    required this.onRunSelected,
    required this.onCompanySelected,
  });

  final ReportRunPayload run;
  final List<ReportRunPayload> runs;
  final List<CompanyContextPayload> reports;
  final CompanyContextPayload selected;
  final bool isRefreshing;
  final void Function(ReportRunPayload run) onRunSelected;
  final void Function(String companyId) onCompanySelected;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      ...pageHeader(
        'Company Context',
        'Read the saved company context artifacts for a run and switch companies instantly without remounting the whole page.',
      ),
      card(classes: 'context-shell', [
        _ContextSelectorGroup(
          label: 'Run',
          helperText: 'Switch runs without remounting the whole page.',
          containerClasses: 'context-run-tabs',
          children: [
            for (final item in runs)
              button(
                classes: item.runId == run.runId ? 'context-run-tab active' : 'context-run-tab',
                onClick: () => onRunSelected(item),
                [.text(item.runId)],
              ),
          ],
        ),
        _ContextSelectorGroup(
          label: 'Company',
          helperText: 'Company context switches instantly inside the current viewer.',
          containerClasses: 'context-tabs',
          children: [
            for (final report in reports)
              button(
                classes: report.companyId == selected.companyId ? 'context-tab active' : 'context-tab',
                onClick: () => onCompanySelected(report.companyId),
                [.text(report.companyId)],
              ),
          ],
        ),
        _ContextViewer(
          runId: run.runId,
          selected: selected,
          totalReports: reports.length,
          isRefreshing: isRefreshing,
        ),
      ]),
    ]);
  }
}

class _ContextSelectorGroup extends StatelessComponent {
  const _ContextSelectorGroup({
    required this.label,
    required this.helperText,
    required this.containerClasses,
    required this.children,
  });

  final String label;
  final String helperText;
  final String containerClasses;
  final List<Component> children;

  @override
  Component build(BuildContext context) {
    return div(classes: 'context-selector-group', [
      div(classes: 'context-selector-copy', [
        span(classes: 'context-selector-label', [.text(label)]),
        p(classes: 'context-selector-helper', [.text(helperText)]),
      ]),
      div(classes: containerClasses, children),
    ]);
  }
}

class _ContextViewer extends StatelessComponent {
  const _ContextViewer({
    required this.runId,
    required this.selected,
    required this.totalReports,
    required this.isRefreshing,
  });

  final String runId;
  final CompanyContextPayload selected;
  final int totalReports;
  final bool isRefreshing;

  @override
  Component build(BuildContext context) {
    return div(classes: 'context-viewer', [
      div(classes: 'context-viewer-header', [
        div(classes: 'context-viewer-copy', [
          span(classes: 'context-viewer-kicker', [.text('Context Artifact Viewer')]),
          h2(classes: 'context-viewer-title', [.text(selected.companyId)]),
          p(
            classes: 'context-viewer-meta',
            [.text('Run $runId · ${selected.fileName} · $totalReports companies in this run')],
          ),
        ]),
        span(
          classes: isRefreshing ? 'context-viewer-badge refreshing' : 'context-viewer-badge',
          [.text(isRefreshing ? 'Refreshing…' : 'YAML')],
        ),
      ]),
      pre(classes: 'context-text', [.text(selected.textContent)]),
    ]);
  }
}
