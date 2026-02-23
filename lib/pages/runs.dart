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
    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Runs')]),
        p([.text('Loading report index on the client...')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Runs')]),
        p([.text('Loading report index...')]),
      ]);
    }

    if (_loadError != null) {
      return section(classes: 'page', [
        h1([.text('Runs')]),
        p(classes: 'error', [.text(_loadError!)]),
        button(onClick: _loadIndex, [.text('Retry')]),
      ]);
    }

    final index = _index;
    if (index == null) {
      return section(classes: 'page', [
        h1([.text('Runs')]),
        card([
          p([.text('No report index data available.')]),
        ]),
      ]);
    }

    return section(classes: 'page', [
      h1([.text('Runs')]),
      card([
        p([
          .text('Reports root: '),
          code([.text(index.reportsRoot)]),
        ]),
        p([
          .text(
            index.reportsRootExists ? 'Reports root is available.' : 'Reports root does not exist.',
          ),
        ]),
        if (!index.reportsRootExists)
          p([
            .text('Tip: run server with '),
            code([
              .text('REPORTS_ROOT=/Users/pmfb/Documents/coding/go-no-go-engine/output jaspr serve'),
            ]),
          ]),
        ...issueList(index.issues),
      ]),
      if (index.runs.isEmpty)
        card([
          p([.text('No runs discovered yet. Generate reports with the engine first.')]),
        ])
      else
        card([
          table(classes: 'runs-table', [
            thead([
              tr([
                th([.text('Run')]),
                th([.text('Batch JSON')]),
                th([.text('Batch MD')]),
                th([.text('Weekly')]),
                th([.text('Context')]),
                th([.text('Trends')]),
                th([.text('Actions')]),
              ]),
            ]),
            tbody([
              for (final run in index.runs)
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
                      Link(
                        to: buildRouteWithQuery('/batch', {'run': run.runId}),
                        child: .text('Batch'),
                      ),
                      Link(
                        to: buildRouteWithQuery('/weekly', {'run': run.runId}),
                        child: .text('Weekly'),
                      ),
                      Link(
                        to: buildRouteWithQuery('/context', {'run': run.runId}),
                        child: .text('Context'),
                      ),
                      Link(
                        to: buildRouteWithQuery('/trends', {'run': run.runId}),
                        child: .text('Trends'),
                      ),
                    ]),
                  ]),
                ]),
            ]),
          ]),
        ]),
    ]);
  }
}
