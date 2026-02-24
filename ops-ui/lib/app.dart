import 'dart:async';

import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import 'models/ops_models.dart';
import 'services/ops_api.dart';

enum OpsScreen {
  createRun,
  runs,
  company,
  persona,
  settings,
}

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
  static const List<int> _pollIntervalOptions = [3, 5, 10, 20, 30];
  static const String _robotsStrict = 'strict';
  static const String _robotsWarn = 'warn';
  static const String _robotsOff = 'off';

  OpsScreen _activeScreen = OpsScreen.createRun;

  OpsConfigPayload? _config;
  HealthPayload? _health;
  List<RunPayload> _runs = const [];

  String? _selectedRunId;
  bool _isLoading = true;
  bool _isSubmittingRun = false;
  bool _isSubmittingCompany = false;
  bool _isSubmittingPersona = false;

  String? _errorMessage;
  String? _successMessage;

  String _selectedPersonaId = '';
  final Set<String> _selectedCompanyIds = <String>{};
  bool _fetchWebFirst = true;
  String _robotsMode = _robotsStrict;
  int _maxJobsPerCompany = 5;
  int _timeoutSeconds = 20;
  int _retries = 2;
  int _backoffMillis = 300;
  int _requestDelayMillis = 1200;
  int _topPerSection = 5;

  String _newCompanyId = '';
  String _newCompanyName = '';
  String _newCareerUrl = '';
  String _newCorporateUrl = '';
  String _newTypeHint = 'product';
  String _newRegion = 'japan';
  String _newNotes = '';

  String _newPersonaId = '';
  String _newPersonaDescription = '';
  String _newPersonaPriorities = 'english_environment,product_company,hybrid_work';
  String _newPersonaHardNo = 'consulting_company,onsite_only,salary_missing';
  String _newPersonaAcceptableIf = 'hybrid_partial,japanese_not_blocking';

  bool _autoRefreshRuns = true;
  int _pollIntervalSeconds = 3;

  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    if (!kIsWeb) {
      return;
    }
    _loadInitial();
    _restartPolling();
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
      final values = await Future.wait<dynamic>([
        _api.fetchConfig(),
        _api.fetchRuns(),
        _api.fetchHealth(),
      ]);

      final config = values[0] as OpsConfigPayload;
      final runs = values[1] as List<RunPayload>;
      final health = values[2] as HealthPayload;

      setState(() {
        _config = config;
        _runs = runs;
        _health = health;
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

  void _restartPolling() {
    _pollTimer?.cancel();
    if (!_autoRefreshRuns || _pollIntervalSeconds <= 0) {
      return;
    }
    _pollTimer = Timer.periodic(Duration(seconds: _pollIntervalSeconds), (_) {
      _refreshRuns(silent: true);
    });
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

  Future<void> _refreshConfig() async {
    try {
      final config = await _api.fetchConfig();
      setState(() {
        _config = config;
        if (_selectedPersonaId.isEmpty && config.personaIds.isNotEmpty) {
          _selectedPersonaId = config.personaIds.first;
        }
      });
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
      });
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

  void _toggleSelectAllCompanies() {
    final config = _config;
    if (config == null) {
      return;
    }

    setState(() {
      if (_selectedCompanyIds.length == config.companies.length) {
        _selectedCompanyIds.clear();
      } else {
        _selectedCompanyIds
          ..clear()
          ..addAll(config.companies.map((company) => company.id));
      }
    });
  }

  Future<void> _submitRun() async {
    if (_selectedPersonaId.isEmpty || _isSubmittingRun) {
      return;
    }

    setState(() {
      _isSubmittingRun = true;
      _errorMessage = null;
      _successMessage = null;
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
        _successMessage = 'Run created successfully: ${created.runId}';
        _activeScreen = OpsScreen.runs;
      });
      await _refreshRuns();
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
      });
    } finally {
      setState(() {
        _isSubmittingRun = false;
      });
    }
  }

  Future<void> _createCompany() async {
    if (_isSubmittingCompany) {
      return;
    }

    setState(() {
      _isSubmittingCompany = true;
      _errorMessage = null;
      _successMessage = null;
    });

    try {
      final created = await _api.createCompany({
        'id': _newCompanyId,
        'name': _newCompanyName,
        'careerUrl': _newCareerUrl,
        'corporateUrl': _newCorporateUrl,
        'typeHint': _newTypeHint,
        'region': _newRegion,
        'notes': _newNotes,
      });

      await _refreshConfig();
      setState(() {
        _newCompanyId = '';
        _newCompanyName = '';
        _newCareerUrl = '';
        _newCorporateUrl = '';
        _newTypeHint = 'product';
        _newRegion = 'japan';
        _newNotes = '';
        _successMessage = 'Company added: ${created.id} (${created.name})';
      });
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
      });
    } finally {
      setState(() {
        _isSubmittingCompany = false;
      });
    }
  }

  Future<void> _createPersona() async {
    if (_isSubmittingPersona) {
      return;
    }

    setState(() {
      _isSubmittingPersona = true;
      _errorMessage = null;
      _successMessage = null;
    });

    try {
      final createdId = await _api.createPersona({
        'id': _newPersonaId,
        'description': _newPersonaDescription,
        'priorities': _csvToList(_newPersonaPriorities),
        'hardNo': _csvToList(_newPersonaHardNo),
        'acceptableIf': _csvToList(_newPersonaAcceptableIf),
      });

      await _refreshConfig();
      setState(() {
        _newPersonaId = '';
        _newPersonaDescription = '';
        _newPersonaPriorities = 'english_environment,product_company,hybrid_work';
        _newPersonaHardNo = 'consulting_company,onsite_only,salary_missing';
        _newPersonaAcceptableIf = 'hybrid_partial,japanese_not_blocking';
        _successMessage = 'Persona added: $createdId';
      });
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
      });
    } finally {
      setState(() {
        _isSubmittingPersona = false;
      });
    }
  }

  List<String> _csvToList(String raw) {
    return raw
        .split(',')
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toList();
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

  void _selectScreen(OpsScreen screen) {
    setState(() {
      _activeScreen = screen;
      _errorMessage = null;
      _successMessage = null;
    });
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
        h1([.text('Go/No-Go Engine Operations')]),
        p([.text('Loading configuration and run state...')]),
      ]);
    }

    final config = _config;
    if (config == null) {
      return section(classes: 'page', [
        h1([.text('Go/No-Go Engine Operations')]),
        p(classes: 'error-banner', [.text(_errorMessage ?? 'Unable to load configuration.')]),
        button(onClick: _loadInitial, [.text('Retry')]),
      ]);
    }

    return div(classes: 'shell', [
      aside(classes: 'sidebar panel', [
        h1(classes: 'brand-title', [.text('Go/No-Go Engine Operations')]),
        p(classes: 'brand-caption', [.text('CLI companion UI')]),
        nav(classes: 'menu', [
          _menuButton(OpsScreen.createRun, 'Create Run'),
          _menuButton(OpsScreen.runs, 'Runs'),
          _menuButton(OpsScreen.company, 'Company'),
          _menuButton(OpsScreen.persona, 'Persona'),
          _menuButton(OpsScreen.settings, 'Settings'),
        ]),
        div(classes: 'sidebar-meta', [
          p([.text('Companies: ${config.companies.length}')]),
          p([.text('Personas: ${config.personaIds.length}')]),
          p([.text('Runs: ${_runs.length}')]),
        ]),
      ]),
      section(classes: 'main-content', [
        if (_errorMessage != null) div(classes: 'error-banner', [.text(_errorMessage!)]),
        if (_successMessage != null) div(classes: 'success-banner', [.text(_successMessage!)]),
        _screenContent(config),
      ]),
    ]);
  }

  Component _menuButton(OpsScreen screen, String label) {
    final selected = _activeScreen == screen;
    return button(
      classes: selected ? 'menu-item selected' : 'menu-item',
      onClick: () => _selectScreen(screen),
      [.text(label)],
    );
  }

  Component _screenContent(OpsConfigPayload config) {
    switch (_activeScreen) {
      case OpsScreen.createRun:
        return _buildCreateRunScreen(config);
      case OpsScreen.runs:
        return _buildRunsScreen();
      case OpsScreen.company:
        return _buildCompanyScreen(config);
      case OpsScreen.persona:
        return _buildPersonaScreen(config);
      case OpsScreen.settings:
        return _buildSettingsScreen(config);
    }
  }

  Component _buildCreateRunScreen(OpsConfigPayload config) {
    final allSelected = _selectedCompanyIds.length == config.companies.length && config.companies.isNotEmpty;

    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        h2([.text('Create Run')]),
        p([.text('Run pipeline with explicit parameters and safe defaults.')]),
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
                option(value: 'evaluate_only', selected: !_fetchWebFirst, [.text('Evaluate only')]),
              ],
            ),
          ]),
          label([
            .text('Robots mode'),
            select(
              value: _robotsMode,
              onChange: (values) {
                setState(() {
                  _robotsMode = values.isEmpty ? _robotsStrict : values.first;
                });
              },
              [
                option(value: _robotsStrict, selected: _robotsMode == _robotsStrict, [.text(_robotsStrict)]),
                option(value: _robotsWarn, selected: _robotsMode == _robotsWarn, [.text(_robotsWarn)]),
                option(value: _robotsOff, selected: _robotsMode == _robotsOff, [.text(_robotsOff)]),
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
        div(classes: 'panel-header-inline', [
          h3([.text('Companies')]),
          button(onClick: _toggleSelectAllCompanies, [.text(allSelected ? 'Clear all' : 'Select all')]),
        ]),
        p([
          .text('Selected: ${allSelected ? 'ALL' : _selectedCompanyIds.length} / ${config.companies.length}'),
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
            onClick: _isSubmittingRun ? null : _submitRun,
            [if (_isSubmittingRun) .text('Submitting...') else .text('Create run')],
          ),
          button(onClick: _refreshRuns, [.text('Refresh runs')]),
        ]),
      ]),
    ]);
  }

  Component _buildRunsScreen() {
    final selectedRun = _findRun(_selectedRunId);

    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        div(classes: 'panel-header-inline', [
          h2([.text('Runs')]),
          div(classes: 'actions', [
            button(onClick: _refreshRuns, [.text('Refresh')]),
            button(onClick: () => _selectScreen(OpsScreen.createRun), [.text('New run')]),
          ]),
        ]),
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
        h2([.text('Run Details')]),
        if (selectedRun == null)
          p([.text('Select a run to inspect details and logs.')])
        else ...[
          p([
            .text('Output directory: '),
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

  Component _buildCompanyScreen(OpsConfigPayload config) {
    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        h2([.text('Company')]),
        p([.text('Add a new company to config/companies.yaml.')]),
        div(classes: 'form-grid', [
          _textField(
            labelText: 'Company id',
            value: _newCompanyId,
            placeholder: 'example_company',
            onChanged: (value) => setState(() => _newCompanyId = value),
          ),
          _textField(
            labelText: 'Company name',
            value: _newCompanyName,
            placeholder: 'Example Company',
            onChanged: (value) => setState(() => _newCompanyName = value),
          ),
          _textField(
            labelText: 'Career URL',
            value: _newCareerUrl,
            placeholder: 'https://company.com/careers',
            onChanged: (value) => setState(() => _newCareerUrl = value),
          ),
          _textField(
            labelText: 'Corporate URL',
            value: _newCorporateUrl,
            placeholder: 'https://company.com',
            onChanged: (value) => setState(() => _newCorporateUrl = value),
          ),
          _textField(
            labelText: 'Type hint',
            value: _newTypeHint,
            placeholder: 'product',
            onChanged: (value) => setState(() => _newTypeHint = value),
          ),
          _textField(
            labelText: 'Region',
            value: _newRegion,
            placeholder: 'japan',
            onChanged: (value) => setState(() => _newRegion = value),
          ),
          _textField(
            labelText: 'Notes',
            value: _newNotes,
            placeholder: 'Optional notes for context...',
            onChanged: (value) => setState(() => _newNotes = value),
          ),
        ]),
        div(classes: 'actions', [
          button(
            classes: 'primary',
            onClick: _isSubmittingCompany ? null : _createCompany,
            [if (_isSubmittingCompany) .text('Saving...') else .text('Add company')],
          ),
          button(onClick: _refreshConfig, [.text('Reload config')]),
        ]),
      ]),
      section(classes: 'panel', [
        h3([.text('Registered companies (${config.companies.length})')]),
        div(classes: 'company-list', [
          for (final company in config.companies)
            div(classes: 'company-item', [
              span(classes: 'company-name', [.text(company.name)]),
              code([.text(company.id)]),
            ]),
        ]),
      ]),
    ]);
  }

  Component _buildPersonaScreen(OpsConfigPayload config) {
    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        h2([.text('Persona')]),
        p([.text('Add a new persona to config/personas.yaml.')]),
        div(classes: 'form-grid', [
          _textField(
            labelText: 'Persona id',
            value: _newPersonaId,
            placeholder: 'my_persona_id',
            onChanged: (value) => setState(() => _newPersonaId = value),
          ),
          _textField(
            labelText: 'Description',
            value: _newPersonaDescription,
            placeholder: 'Short persona description',
            onChanged: (value) => setState(() => _newPersonaDescription = value),
          ),
          _textField(
            labelText: 'Priorities (comma-separated)',
            value: _newPersonaPriorities,
            placeholder: 'english_environment,product_company,hybrid_work',
            onChanged: (value) => setState(() => _newPersonaPriorities = value),
          ),
          _textField(
            labelText: 'Hard no (comma-separated)',
            value: _newPersonaHardNo,
            placeholder: 'consulting_company,onsite_only,salary_missing',
            onChanged: (value) => setState(() => _newPersonaHardNo = value),
          ),
          _textField(
            labelText: 'Acceptable if (comma-separated)',
            value: _newPersonaAcceptableIf,
            placeholder: 'hybrid_partial,japanese_not_blocking',
            onChanged: (value) => setState(() => _newPersonaAcceptableIf = value),
          ),
        ]),
        div(classes: 'actions', [
          button(
            classes: 'primary',
            onClick: _isSubmittingPersona ? null : _createPersona,
            [if (_isSubmittingPersona) .text('Saving...') else .text('Add persona')],
          ),
          button(onClick: _refreshConfig, [.text('Reload config')]),
        ]),
      ]),
      section(classes: 'panel', [
        h3([.text('Registered personas (${config.personaIds.length})')]),
        div(classes: 'company-list', [
          for (final personaId in config.personaIds)
            div(classes: 'company-item', [
              span(classes: 'company-name', [.text(personaId)]),
              code([.text(personaId)]),
            ]),
        ]),
      ]),
    ]);
  }

  Component _buildSettingsScreen(OpsConfigPayload config) {
    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        h2([.text('Settings')]),
        p([.text('UI runtime settings for your local operations workflow.')]),
        div(classes: 'form-grid', [
          label([
            .text('Auto refresh runs'),
            select(
              value: _autoRefreshRuns ? 'enabled' : 'disabled',
              onChange: (values) {
                final enabled = values.isNotEmpty && values.first == 'enabled';
                setState(() {
                  _autoRefreshRuns = enabled;
                });
                _restartPolling();
              },
              [
                option(value: 'enabled', selected: _autoRefreshRuns, [.text('Enabled')]),
                option(value: 'disabled', selected: !_autoRefreshRuns, [.text('Disabled')]),
              ],
            ),
          ]),
          _numericSelect(
            labelText: 'Poll interval seconds',
            value: _pollIntervalSeconds,
            options: _pollIntervalOptions,
            onSelected: (value) {
              setState(() {
                _pollIntervalSeconds = value;
              });
              _restartPolling();
            },
          ),
        ]),
        div(classes: 'actions', [
          button(onClick: _loadInitial, [.text('Reload all data')]),
          button(onClick: _refreshRuns, [.text('Refresh runs only')]),
        ]),
      ]),
      section(classes: 'panel', [
        h3([.text('Environment')]),
        p([.text('Health status: ${_health?.status ?? '-'}')]),
        p([
          .text('Engine root: '),
          code([.text(_health?.engineRoot ?? '-')]),
        ]),
        p([.text('Known personas: ${config.personaIds.length}')]),
        p([.text('Known companies: ${config.companies.length}')]),
      ]),
    ]);
  }

  Component _textField({
    required String labelText,
    required String value,
    required String placeholder,
    required void Function(String value) onChanged,
  }) {
    return label([
      .text(labelText),
      input<String>(
        type: InputType.text,
        value: value,
        attributes: {'placeholder': placeholder},
        onInput: onChanged,
      ),
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

  @css
  static List<StyleRule> get styles => [
        css('.shell').styles(
          raw: const {
            'max-width': '1320px',
            'margin': '1rem auto',
            'padding': '0 .8rem 1rem .8rem',
            'display': 'grid',
            'grid-template-columns': '300px 1fr',
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
        css('.sidebar').styles(
          raw: const {
            'height': 'fit-content',
            'position': 'sticky',
            'top': '.8rem',
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '.8rem',
            'align-self': 'start',
          },
        ),
        css('.brand-title').styles(
          raw: const {
            'margin': '0',
            'font-size': '1.35rem',
            'line-height': '1.25',
          },
        ),
        css('.brand-caption').styles(raw: const {'margin': '0', 'opacity': '.72'}),
        css('.menu').styles(raw: const {'display': 'grid', 'gap': '.45rem'}),
        css('.menu-item').styles(
          raw: const {
            'text-align': 'left',
            'border-radius': '10px',
            'border': '1px solid #c9d5cd',
            'background': '#f5faf7',
            'padding': '.52rem .62rem',
          },
        ),
        css('.menu-item.selected').styles(
          raw: const {
            'background': '#d8f3e5',
            'border-color': '#1f6e4e',
            'font-weight': '700',
          },
        ),
        css('.sidebar-meta').styles(
          raw: const {
            'margin-top': '.2rem',
            'padding-top': '.7rem',
            'border-top': '1px solid #e2ebe5',
            'font-size': '.92rem',
          },
        ),
        css('.main-content').styles(raw: const {'display': 'grid', 'gap': '.9rem'}),
        css('.screen-stack').styles(raw: const {'display': 'grid', 'gap': '.9rem'}),
        css('.error-banner').styles(
          raw: const {
            'background': '#ffe6df',
            'color': '#8f2e20',
            'border-radius': '10px',
            'padding': '.7rem .9rem',
            'font-weight': '600',
          },
        ),
        css('.success-banner').styles(
          raw: const {
            'background': '#dcf6e8',
            'color': '#1d5b3d',
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
        css('.span-2').styles(raw: const {'grid-column': 'span 2'}),
        css('.panel-header-inline').styles(
          raw: const {
            'display': 'flex',
            'justify-content': 'space-between',
            'align-items': 'center',
            'gap': '.6rem',
          },
        ),
        css('label').styles(
          raw: const {
            'display': 'flex',
            'flex-direction': 'column',
            'gap': '.28rem',
          },
        ),
        css('select, input, textarea').styles(
          raw: const {
            'padding': '.45rem .55rem',
            'border-radius': '8px',
            'border': '1px solid #c3d0c7',
            'background': '#ffffff',
            'font': 'inherit',
            'color': 'inherit',
          },
        ),
        css('textarea').styles(raw: const {'resize': 'vertical', 'min-height': '84px'}),
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
        css('.actions').styles(
          raw: const {
            'display': 'flex',
            'flex-wrap': 'wrap',
            'gap': '.6rem',
            'margin-top': '.7rem',
          },
        ),
        css('button').styles(
          raw: const {
            'border-radius': '10px',
            'border': '1px solid #b6c4bc',
            'background': '#ffffff',
            'padding': '.5rem .75rem',
            'cursor': 'pointer',
            'font': 'inherit',
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
        css('button:disabled').styles(raw: const {'opacity': '.65', 'cursor': 'default'}),
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
        css('.company-list').styles(raw: const {'display': 'grid', 'gap': '.4rem'}),
        css('.company-item').styles(
          raw: const {
            'display': 'flex',
            'justify-content': 'space-between',
            'align-items': 'center',
            'gap': '.8rem',
            'padding': '.45rem .55rem',
            'border': '1px solid #e2ebe5',
            'border-radius': '9px',
            'background': '#fbfdfb',
          },
        ),
        css('.company-name').styles(raw: const {'font-weight': '600'}),
        css.media(const MediaQuery.raw('(max-width: 1040px)'), [
          css('.shell').styles(raw: const {'grid-template-columns': '1fr'}),
          css('.sidebar').styles(
            raw: const {
              'position': 'static',
            },
          ),
          css('.menu').styles(
            raw: const {
              'grid-template-columns': 'repeat(5, minmax(110px, 1fr))',
            },
          ),
        ]),
        css.media(const MediaQuery.raw('(max-width: 920px)'), [
          css('.form-grid').styles(raw: const {'grid-template-columns': '1fr'}),
          css('.span-2').styles(raw: const {'grid-column': 'span 1'}),
          css('.company-grid').styles(raw: const {'grid-template-columns': '1fr'}),
          css('.runs-table').styles(raw: const {'display': 'block', 'overflow': 'auto', 'white-space': 'nowrap'}),
        ]),
        css.media(const MediaQuery.raw('(max-width: 720px)'), [
          css('.menu').styles(raw: const {'grid-template-columns': '1fr 1fr'}),
          css('.panel').styles(raw: const {'padding': '.8rem'}),
          css('.actions').styles(raw: const {'flex-direction': 'column'}),
          css('.actions button').styles(raw: const {'width': '100%'}),
          css('.panel-header-inline').styles(raw: const {'flex-direction': 'column', 'align-items': 'stretch'}),
        ]),
      ];
}
