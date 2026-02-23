import 'dart:async';
// ignore_for_file: unused_element

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import 'models/ops_models.dart';
import 'services/ops_api.dart';

@client
class App extends StatefulComponent {
  const App({super.key});

  @override
  State<App> createState() => AppState();
}

class AppState extends State<App> {
  static const OpsApiClient _api = OpsApiClient();

  static const List<int> _maxJobsOptions = [3, 5, 8, 10, 15];
  static const List<int> _timeoutOptions = [10, 20, 30, 45, 60];
  static const List<int> _retryOptions = [0, 1, 2, 3, 5];
  static const List<int> _backoffOptions = [100, 300, 500, 1000, 2000];
  static const List<int> _delayOptions = [0, 500, 1200, 2000, 3000, 5000];
  static const List<int> _topPerSectionOptions = [3, 5, 8, 10];

  OpsConfigPayload? _config;
  List<RunPayload> _runs = const [];

  String? _selectedRunId;
  bool _isLoading = true;
  bool _isSubmitting = false;
  String? _errorMessage;

  String _selectedPersonaId = '';
  final Set<String> _selectedCompanyIds = <String>{};
  bool _fetchWebFirst = true;
  String _robotsMode = 'strict';
  int _maxJobsPerCompany = 5;
  int _timeoutSeconds = 20;
  int _retries = 2;
  int _backoffMillis = 300;
  int _requestDelayMillis = 1200;
  int _topPerSection = 5;

  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    if (!kIsWeb) {
      return;
    }
    _loadInitial();
    _pollTimer = Timer.periodic(const Duration(seconds: 3), (_) => _refreshRuns(silent: true));
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadInitial() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final config = await _api.fetchConfig();
      final runs = await _api.fetchRuns();

      setState(() {
        _config = config;
        _runs = runs;
        if (_selectedPersonaId.isEmpty) {
          _selectedPersonaId = config.personaIds.isEmpty ? '' : config.personaIds.first;
        }
        _selectedRunId = _selectedRunId ?? (runs.isEmpty ? null : runs.first.runId);
        _isLoading = false;
      });

      await _refreshSelectedRunDetails();
    } catch (error) {
      setState(() {
        _isLoading = false;
        _errorMessage = error.toString();
      });
    }
  }

  Future<void> _refreshRuns({bool silent = false}) async {
    if (!silent) {
      setState(() {
        _errorMessage = null;
      });
    }

    try {
      final runs = await _api.fetchRuns();
      setState(() {
        _runs = runs;
        if (_selectedRunId == null && runs.isNotEmpty) {
          _selectedRunId = runs.first.runId;
        }
      });
      await _refreshSelectedRunDetails();
    } catch (error) {
      if (!silent) {
        setState(() {
          _errorMessage = error.toString();
        });
      }
    }
  }

  Future<void> _refreshSelectedRunDetails() async {
    final runId = _selectedRunId;
    if (runId == null || runId.isEmpty) {
      return;
    }

    try {
      final details = await _api.fetchRun(runId);
      setState(() {
        _runs = _runs.map((run) => run.runId == runId ? details : run).toList();
      });
    } catch (_) {
      // Keep existing detail when refresh fails.
    }
  }

  void _toggleCompany(String companyId) {
    setState(() {
      if (_selectedCompanyIds.contains(companyId)) {
        _selectedCompanyIds.remove(companyId);
      } else {
        _selectedCompanyIds.add(companyId);
      }
    });
  }

  Future<void> _submitRun() async {
    if (_selectedPersonaId.isEmpty || _isSubmitting) {
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final created = await _api.createRun({
        'personaId': _selectedPersonaId,
        'companyIds': _selectedCompanyIds.toList()..sort(),
        'fetchWebFirst': _fetchWebFirst,
        'robotsMode': _robotsMode,
        'maxJobsPerCompany': _maxJobsPerCompany,
        'timeoutSeconds': _timeoutSeconds,
        'retries': _retries,
        'backoffMillis': _backoffMillis,
        'requestDelayMillis': _requestDelayMillis,
        'topPerSection': _topPerSection,
      });

      setState(() {
        _selectedRunId = created.runId;
      });
      await _refreshRuns();
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
      });
    } finally {
      setState(() {
        _isSubmitting = false;
      });
    }
  }

  @override
  Component build(BuildContext context) {
    if (!kIsWeb) {
      return section(classes: 'page', [
        h1([.text('Go/No-Go Operations UI')]),
        p([.text('This UI is intended to run in a browser.')]),
      ]);
    }

    if (_isLoading) {
      return section(classes: 'page', [
        h1([.text('Go/No-Go Operations UI')]),
        p([.text('Loading configuration and run state...')]),
      ]);
    }

    final config = _config;
    if (config == null) {
      return section(classes: 'page', [
        h1([.text('Go/No-Go Operations UI')]),
        p(classes: 'error-banner', [.text(_errorMessage ?? 'Unable to load configuration.')]),
        button(onClick: _loadInitial, [.text('Retry')]),
      ]);
    }

    final selectedRun = _findRun(_selectedRunId);

    return div(classes: 'layout', [
      section(classes: 'hero panel', [
        h1([.text('Go/No-Go Engine Operations')]),
        p([.text('Configure and run pipeline jobs while preserving CLI-first architecture.')]),
      ]),
      if (_errorMessage != null) div(classes: 'error-banner', [.text(_errorMessage!)]),
      section(classes: 'panel', [
        h2([.text('Create Run')]),
        div(classes: 'form-grid', [
          label([
            .text('Persona'),
            select(
              value: _selectedPersonaId,
              onChange: (values) {
                setState(() {
                  _selectedPersonaId = values.isEmpty ? '' : values.first;
                });
              },
              [
                for (final personaId in config.personaIds)
                  option(value: personaId, selected: _selectedPersonaId == personaId, [.text(personaId)]),
              ],
            ),
          ]),
          label([
            .text('Mode'),
            select(
              value: _fetchWebFirst ? 'fetch_and_evaluate' : 'evaluate_only',
              onChange: (values) {
                setState(() {
                  _fetchWebFirst = values.isNotEmpty && values.first == 'fetch_and_evaluate';
                });
              },
              [
                option(value: 'fetch_and_evaluate', selected: _fetchWebFirst, [.text('Fetch + Evaluate')]),
                option(value: 'evaluate_only', selected: !_fetchWebFirst, [.text('Evaluate existing files only')]),
              ],
            ),
          ]),
          label([
            .text('Robots mode'),
            select(
              value: _robotsMode,
              onChange: (values) {
                setState(() {
                  _robotsMode = values.isEmpty ? 'strict' : values.first;
                });
              },
              [
                option(value: 'strict', selected: _robotsMode == 'strict', [.text('strict')]),
                option(value: 'warn', selected: _robotsMode == 'warn', [.text('warn')]),
                option(value: 'off', selected: _robotsMode == 'off', [.text('off')]),
              ],
            ),
          ]),
          _numericSelect(
            labelText: 'Max jobs / company',
            value: _maxJobsPerCompany,
            options: _maxJobsOptions,
            onSelected: (value) => setState(() => _maxJobsPerCompany = value),
          ),
          _numericSelect(
            labelText: 'Timeout seconds',
            value: _timeoutSeconds,
            options: _timeoutOptions,
            onSelected: (value) => setState(() => _timeoutSeconds = value),
          ),
          _numericSelect(
            labelText: 'Retries',
            value: _retries,
            options: _retryOptions,
            onSelected: (value) => setState(() => _retries = value),
          ),
          _numericSelect(
            labelText: 'Backoff millis',
            value: _backoffMillis,
            options: _backoffOptions,
            onSelected: (value) => setState(() => _backoffMillis = value),
          ),
          _numericSelect(
            labelText: 'Request delay millis',
            value: _requestDelayMillis,
            options: _delayOptions,
            onSelected: (value) => setState(() => _requestDelayMillis = value),
          ),
          _numericSelect(
            labelText: 'Top per section',
            value: _topPerSection,
            options: _topPerSectionOptions,
            onSelected: (value) => setState(() => _topPerSection = value),
          ),
        ]),
        h3([.text('Companies')]),
        p([
          .text('Selected: ${_selectedCompanyIds.length == config.companies.length ? 'ALL' : _selectedCompanyIds.length}'),
        ]),
        div(classes: 'company-grid', [
          for (final company in config.companies)
            button(
              classes: _selectedCompanyIds.contains(company.id) ? 'chip selected' : 'chip',
              onClick: () => _toggleCompany(company.id),
              [.text('${company.name} (${company.id})')],
            ),
        ]),
        div(classes: 'actions', [
          button(
            classes: 'primary',
            onClick: _isSubmitting ? null : _submitRun,
            [if (_isSubmitting) .text('Submitting...') else .text('Run pipeline')],
          ),
          button(onClick: _refreshRuns, [.text('Refresh runs')]),
        ]),
      ]),
      section(classes: 'panel', [
        h2([.text('Runs')]),
        if (_runs.isEmpty)
          p([.text('No runs created yet.')])
        else
          table(classes: 'runs-table', [
            thead([
              tr([
                th([.text('Run id')]),
                th([.text('Status')]),
                th([.text('Persona')]),
                th([.text('Companies')]),
                th([.text('Created')]),
              ]),
            ]),
            tbody([
              for (final run in _runs)
                tr(classes: run.runId == _selectedRunId ? 'selected-row' : null, [
                  td([
                    button(onClick: () => setState(() => _selectedRunId = run.runId), [.text(run.runId)]),
                  ]),
                  td([span(classes: 'status ${run.status}', [.text(run.status)])]),
                  td([.text(run.request.personaId)]),
                  td([.text(run.request.companyIds.isEmpty ? 'ALL' : run.request.companyIds.join(', '))]),
                  td([.text(run.createdAt)]),
                ]),
            ]),
          ]),
      ]),
      section(classes: 'panel', [
        h2([.text('Run Detail')]),
        if (selectedRun == null)
          p([.text('Select a run to inspect details and logs.')])
        else ...[
          p([
            .text('Output: '),
            code([.text(selectedRun.outputDir)]),
          ]),
          p([
            .text('Command: '),
            code([.text('${selectedRun.command} ${selectedRun.arguments.join(' ')}')]),
          ]),
          p([.text('Exit code: ${selectedRun.exitCode?.toString() ?? '-'}')]),
          pre(classes: 'logs', [
            .text(selectedRun.logs.isEmpty ? 'No logs yet.' : selectedRun.logs.join('\n')),
          ]),
        ],
      ]),
    ]);
  }

  Component _numericSelect({
    required String labelText,
    required int value,
    required List<int> options,
    required void Function(int value) onSelected,
  }) {
    return label([
      .text(labelText),
      select(
        value: value.toString(),
        onChange: (values) {
          final parsed = int.tryParse(values.isEmpty ? '' : values.first);
          if (parsed != null) {
            onSelected(parsed);
          }
        },
        [
          for (final optionValue in options)
            option(
              value: optionValue.toString(),
              selected: optionValue == value,
              [.text(optionValue.toString())],
            ),
        ],
      ),
    ]);
  }

  RunPayload? _findRun(String? runId) {
    if (runId == null) {
      return null;
    }
    for (final run in _runs) {
      if (run.runId == runId) {
        return run;
      }
    }
    return null;
  }

  @css
  static List<StyleRule> get styles => [
        css('.layout').styles(
          raw: const {
            'max-width': '1180px',
            'margin': '1.2rem auto',
            'padding': '0 1rem 1rem 1rem',
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '1rem',
          },
        ),
        css('.panel').styles(
          raw: const {
            'background': '#ffffff',
            'border-radius': '16px',
            'padding': '1.1rem',
            'box-shadow': '0 6px 16px rgba(27, 46, 36, 0.10)',
          },
        ),
        css('.hero').styles(raw: const {'background': '#dfeee4'}),
        css('.error-banner').styles(
          raw: const {
            'background': '#ffe6df',
            'color': '#8f2e20',
            'border-radius': '10px',
            'padding': '.7rem .9rem',
            'font-weight': '600',
          },
        ),
        css('.form-grid').styles(
          raw: const {
            'display': 'grid',
            'grid-template-columns': 'repeat(2, minmax(220px, 1fr))',
            'gap': '.75rem',
          },
        ),
        css('label').styles(
          raw: const {
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '.28rem',
          },
        ),
        css('select').styles(
          raw: const {
            'padding': '.45rem .55rem',
            'border-radius': '8px',
            'border': '1px solid #c3d0c7',
            'background': '#ffffff',
          },
        ),
        css('.company-grid').styles(
          raw: const {
            'display': 'grid',
            'grid-template-columns': 'repeat(2, minmax(240px, 1fr))',
            'gap': '.5rem',
          },
        ),
        css('.chip').styles(
          raw: const {
            'text-align': 'left',
            'border-radius': '999px',
            'border': '1px solid #b7c5bc',
            'background': '#f7fbf8',
            'color': '#1f2a2e',
            'padding': '.5rem .75rem',
          },
        ),
        css('.chip.selected').styles(
          raw: const {
            'border-color': '#1f6e4e',
            'background': '#d8f3e5',
            'color': '#124735',
            'font-weight': '600',
          },
        ),
        css('.actions').styles(raw: const {'display': 'flex', 'gap': '.6rem', 'margin-top': '.9rem'}),
        css('button').styles(
          raw: const {
            'border-radius': '10px',
            'border': '1px solid #b6c4bc',
            'background': '#ffffff',
            'padding': '.5rem .75rem',
            'cursor': 'pointer',
          },
        ),
        css('button.primary').styles(
          raw: const {
            'background': '#1f6e4e',
            'border-color': '#1f6e4e',
            'color': '#ffffff',
            'font-weight': '700',
          },
        ),
        css('.runs-table').styles(raw: const {'width': '100%', 'border-collapse': 'collapse'}),
        css('.runs-table th, .runs-table td').styles(
          raw: const {
            'border-bottom': '1px solid #e2ebe5',
            'padding': '.48rem .45rem',
            'vertical-align': 'top',
            'text-align': 'left',
          },
        ),
        css('.selected-row').styles(raw: const {'background': '#eff8f3'}),
        css('.status').styles(
          raw: const {
            'display': 'inline-block',
            'padding': '.18rem .48rem',
            'border-radius': '999px',
            'font-size': '.78rem',
            'text-transform': 'uppercase',
            'letter-spacing': '.02rem',
          },
        ),
        css('.status.queued').styles(raw: const {'background': '#ece9ff', 'color': '#4f3b8f'}),
        css('.status.running').styles(raw: const {'background': '#fff5df', 'color': '#7b5a1a'}),
        css('.status.succeeded').styles(raw: const {'background': '#dcf6e8', 'color': '#1d5b3d'}),
        css('.status.failed').styles(raw: const {'background': '#ffe6df', 'color': '#8f2e20'}),
        css('.logs').styles(
          raw: const {
            'max-height': '420px',
            'overflow': 'auto',
            'background': '#11191c',
            'color': '#d6efe0',
            'padding': '.9rem',
            'border-radius': '10px',
            'white-space': 'pre-wrap',
          },
        ),
        css.media(const MediaQuery.raw('(max-width: 920px)'), [
          css('.layout').styles(raw: const {'padding': '0 .6rem .8rem .6rem'}),
          css('.form-grid').styles(raw: const {'grid-template-columns': '1fr'}),
          css('.company-grid').styles(raw: const {'grid-template-columns': '1fr'}),
          css('.runs-table').styles(raw: const {'display': 'block', 'overflow': 'auto', 'white-space': 'nowrap'}),
        ]),
        css.media(const MediaQuery.raw('(max-width: 620px)'), [
          css('.panel').styles(raw: const {'padding': '.8rem'}),
          css('.actions').styles(raw: const {'flex-direction': 'column'}),
          css('.actions button').styles(raw: const {'width': '100%'}),
        ]),
      ];
}
