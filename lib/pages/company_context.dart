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
    final routeState = RouteState.maybeOf(context);
    final queryParams = routeState?.queryParams ?? const <String, String>{};
    final requestedRunId = queryParams['run'];
    final requestedCompanyId = queryParams['company'];

    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        p([.text('Loading context artifacts...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null || index.runs.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        card([
          p([.text('No runs available.')]),
        ]),
      ]);
    }

    final run = selectRun(index.runs, requestedRunId);
    if (run == null) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        card([
          p([.text('Selected run was not found.')]),
        ]),
      ]);
    }

    final reports = run.companyContextReports;
    if (reports.isEmpty) {
      return section(classes: 'page', [
        h1([.text('Company Context')]),
        card([
          p([
            .text('Run '),
            code([.text(run.runId)]),
            .text(' has no company context files.'),
          ]),
        ]),
      ]);
    }

    var selected = reports.first;
    if (requestedCompanyId != null && requestedCompanyId.isNotEmpty) {
      for (final report in reports) {
        if (report.companyId == requestedCompanyId) {
          selected = report;
          break;
        }
      }
    }

    return section(classes: 'page', [
      h1([.text('Company Context')]),
      card([
        p([
          .text('Run: '),
          code([.text(run.runId)]),
        ]),
        runTabs(
          runs: index.runs,
          selectedRunId: run.runId,
          destinationPath: '/context',
        ),
        div(classes: 'context-tabs', [
          for (final report in reports)
            a(
              href: buildRouteWithQuery('/context', {
                'run': run.runId,
                'company': report.companyId,
              }),
              classes: report.companyId == selected.companyId ? 'context-tab active' : 'context-tab',
              [.text(report.companyId)],
            ),
        ]),
        p([.text('File: ${selected.relativePath}')]),
        pre(classes: 'context-text', [.text(selected.textContent)]),
      ]),
    ]);
  }
}
