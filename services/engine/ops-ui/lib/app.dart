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
  candidateProfile,
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
  static const String _candidateProfileNone = 'none';
  static const List<String> _rankingStrategyOptions = [
    'by_score',
    'by_language_ease',
    'by_reputation',
    'by_composite',
  ];

  OpsScreen _activeScreen = OpsScreen.createRun;

  OpsConfigPayload? _config;
  HealthPayload? _health;
  List<RunPayload> _runs = const [];

  String? _selectedRunId;
  String _selectedCandidateProfileViewId = '';
  bool _isLoading = true;
  bool _isSubmittingRun = false;
  bool _isSubmittingCompany = false;
  bool _isSubmittingPersona = false;
  bool _isLoadingCandidateProfileDetail = false;

  String? _errorMessage;
  String? _successMessage;

  String _selectedPersonaId = '';
  String _selectedCandidateProfileId = '';
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
  String _newPersonaRankingStrategy = 'by_score';
  String _newPersonaMinimumSalaryYen = '';
  // Each entry: (signalName, weightString)
  final List<(String, String)> _newPersonaSignalWeights = [];

  // Signal catalog
  List<Map<String, Object>> _signalCatalog = const [];

  // Tuning panel state
  String? _tuningPersonaId;
  bool _isLoadingTuning = false;
  bool _isSavingTuning = false;
  String _tuningRankingStrategy = 'by_score';
  String _tuningMinimumSalaryYen = '';
  final List<(String, String)> _tuningSignalWeights = [];

  CandidateProfileDetailPayload? _candidateProfileDetail;

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
        _api.fetchSignalCatalog(),
      ]);

      final config = values[0] as OpsConfigPayload;
      final runs = values[1] as List<RunPayload>;
      final health = values[2] as HealthPayload;
      final catalog = values[3] as List<Map<String, Object>>;

      setState(() {
        _config = config;
        _runs = runs;
        _health = health;
        _signalCatalog = catalog;
        if (_selectedPersonaId.isEmpty) {
          _selectedPersonaId = config.personaIds.isEmpty ? '' : config.personaIds.first;
        }
        _selectedCandidateProfileId = _normalizeCandidateProfileSelection(
          _selectedCandidateProfileId,
          config,
        );
        if (_selectedCandidateProfileViewId.isEmpty && config.candidateProfileIds.isNotEmpty) {
          _selectedCandidateProfileViewId = config.candidateProfileIds.first;
        }
        _selectedRunId = _selectedRunId ?? (runs.isEmpty ? null : runs.first.runId);
        _isLoading = false;
      });

      await _refreshSelectedRunDetails();
      await _ensureCandidateProfileDetailLoaded();
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
        _selectedCandidateProfileId = _normalizeCandidateProfileSelection(
          _selectedCandidateProfileId,
          config,
        );
        if (!config.candidateProfileIds.contains(_selectedCandidateProfileViewId)) {
          _selectedCandidateProfileViewId = config.candidateProfileIds.isEmpty ? '' : config.candidateProfileIds.first;
          _candidateProfileDetail = null;
        }
      });
      await _ensureCandidateProfileDetailLoaded();
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
        'candidateProfileId': _selectedCandidateProfileId,
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
      final weights = Map.fromEntries(
        _newPersonaSignalWeights
            .where((e) => e.$1.isNotEmpty && int.tryParse(e.$2) != null)
            .map((e) => MapEntry(e.$1, int.parse(e.$2))),
      );

      final createdId = await _api.createPersona({
        'id': _newPersonaId,
        'description': _newPersonaDescription,
        'priorities': _csvToList(_newPersonaPriorities),
        'hardNo': _csvToList(_newPersonaHardNo),
        'acceptableIf': _csvToList(_newPersonaAcceptableIf),
        'rankingStrategy': _newPersonaRankingStrategy,
        'minimumSalaryYen': _parseOptionalInt(_newPersonaMinimumSalaryYen),
        'signalWeights': weights,
      });

      await _refreshConfig();
      setState(() {
        _newPersonaId = '';
        _newPersonaDescription = '';
        _newPersonaPriorities = 'english_environment,product_company,hybrid_work';
        _newPersonaHardNo = 'consulting_company,onsite_only,salary_missing';
        _newPersonaAcceptableIf = 'hybrid_partial,japanese_not_blocking';
        _newPersonaRankingStrategy = 'by_score';
        _newPersonaMinimumSalaryYen = '';
        _newPersonaSignalWeights.clear();
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

  Future<void> _openTuning(String personaId) async {
    setState(() {
      _tuningPersonaId = personaId;
      _isLoadingTuning = true;
      _tuningSignalWeights.clear();
      _errorMessage = null;
    });

    try {
      final detail = await _api.fetchPersonaDetail(personaId);
      final rawWeights = detail['signal_weights'];
      final loadedWeights = <(String, String)>[];
      if (rawWeights is Map) {
        for (final e in rawWeights.entries) {
          loadedWeights.add((e.key.toString(), e.value.toString()));
        }
      }
      setState(() {
        _tuningRankingStrategy = detail['ranking_strategy']?.toString() ?? 'by_score';
        _tuningMinimumSalaryYen = detail['minimum_salary_yen']?.toString() ?? '';
        _tuningSignalWeights
          ..clear()
          ..addAll(loadedWeights);
        _isLoadingTuning = false;
      });
    } catch (error) {
      setState(() {
        _isLoadingTuning = false;
        _errorMessage = error.toString();
      });
    }
  }

  void _closeTuning() {
    setState(() {
      _tuningPersonaId = null;
      _tuningMinimumSalaryYen = '';
      _tuningSignalWeights.clear();
    });
  }

  Future<void> _saveTuning() async {
    final id = _tuningPersonaId;
    if (id == null || _isSavingTuning) return;

    setState(() {
      _isSavingTuning = true;
      _errorMessage = null;
    });

    try {
      final weights = Map.fromEntries(
        _tuningSignalWeights
            .where((e) => e.$1.isNotEmpty && int.tryParse(e.$2) != null)
            .map((e) => MapEntry(e.$1, int.parse(e.$2))),
      );
      await _api.updatePersonaTuning(
        id,
        weights,
        _tuningRankingStrategy,
        _parseOptionalInt(_tuningMinimumSalaryYen),
      );
      setState(() {
        _successMessage = 'Tuning saved for persona: $id';
        _isSavingTuning = false;
      });
    } catch (error) {
      setState(() {
        _errorMessage = error.toString();
        _isSavingTuning = false;
      });
    }
  }

  List<String> _csvToList(String raw) {
    return raw.split(',').map((item) => item.trim()).where((item) => item.isNotEmpty).toList();
  }

  int? _parseOptionalInt(String raw) {
    final normalized = raw.trim().replaceAll(',', '');
    if (normalized.isEmpty) {
      return null;
    }
    return int.tryParse(normalized);
  }

  String _normalizeCandidateProfileSelection(String current, OpsConfigPayload config) {
    if (current.isEmpty || current == _candidateProfileNone) {
      return current;
    }
    return config.candidateProfileIds.contains(current) ? current : '';
  }

  String _candidateProfileLabel(String value) {
    if (value.isEmpty) {
      return 'Auto';
    }
    if (value == _candidateProfileNone) {
      return 'None';
    }
    return value;
  }

  Future<void> _ensureCandidateProfileDetailLoaded() async {
    final config = _config;
    if (config == null || config.candidateProfileIds.isEmpty) {
      return;
    }
    final selectedId = _selectedCandidateProfileViewId.isEmpty
        ? config.candidateProfileIds.first
        : _selectedCandidateProfileViewId;
    if (_candidateProfileDetail?.id == selectedId) {
      return;
    }
    await _loadCandidateProfileDetail(selectedId);
  }

  Future<void> _loadCandidateProfileDetail(String id) async {
    if (id.isEmpty) {
      return;
    }

    setState(() {
      _selectedCandidateProfileViewId = id;
      _isLoadingCandidateProfileDetail = true;
      _errorMessage = null;
    });

    try {
      final detail = await _api.fetchCandidateProfileDetail(id);
      setState(() {
        _candidateProfileDetail = detail;
        _isLoadingCandidateProfileDetail = false;
      });
    } catch (error) {
      setState(() {
        _candidateProfileDetail = null;
        _isLoadingCandidateProfileDetail = false;
        _errorMessage = error.toString();
      });
    }
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
    if (screen == OpsScreen.candidateProfile) {
      _ensureCandidateProfileDetailLoaded();
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
      aside(classes: 'sidebar', [
        h1(classes: 'brand-title', [.text('Go/No-Go Engine Operations')]),
        p(classes: 'brand-caption', [.text('CLI companion UI')]),
        nav(classes: 'menu', [
          _menuButton(OpsScreen.createRun, 'Create Run'),
          _menuButton(OpsScreen.runs, 'Runs'),
          _menuButton(OpsScreen.company, 'Company'),
          _menuButton(OpsScreen.persona, 'Persona'),
          _menuButton(OpsScreen.candidateProfile, 'Candidate Profile'),
          _menuButton(OpsScreen.settings, 'Settings'),
        ]),
        div(classes: 'sidebar-meta', [
          p([.text('Companies: ${config.companies.length}')]),
          p([.text('Personas: ${config.personaIds.length}')]),
          p([.text('Candidate profiles: ${config.candidateProfileIds.length}')]),
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
      case OpsScreen.candidateProfile:
        return _buildCandidateProfileScreen(config);
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
            .text('Candidate profile'),
            select(
              value: _selectedCandidateProfileId,
              onChange: (values) {
                setState(() {
                  _selectedCandidateProfileId = values.isEmpty ? '' : values.first;
                });
              },
              [
                option(value: '', selected: _selectedCandidateProfileId.isEmpty, [.text('Auto')]),
                option(
                  value: _candidateProfileNone,
                  selected: _selectedCandidateProfileId == _candidateProfileNone,
                  [.text('None')],
                ),
                for (final profileId in config.candidateProfileIds)
                  option(
                    value: profileId,
                    selected: _selectedCandidateProfileId == profileId,
                    [.text(profileId)],
                  ),
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
        if (config.candidateProfileIds.isEmpty)
          p([.text('No candidate profiles are configured. Runs will use persona-only evaluation.')]),
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
                th([.text('Candidate')]),
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
                  td([
                    span(classes: 'status ${run.status}', [.text(run.status)]),
                  ]),
                  td([.text(run.request.personaId)]),
                  td([.text(_candidateProfileLabel(run.request.candidateProfileId))]),
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
          p([.text('Candidate profile: ${_candidateProfileLabel(selectedRun.request.candidateProfileId)}')]),
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
    final signalNames = _signalCatalog
        .map((entry) => entry['name']?.toString() ?? '')
        .where((n) => n.isNotEmpty)
        .toList();

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
          _textField(
            labelText: 'Minimum salary yen (optional)',
            value: _newPersonaMinimumSalaryYen,
            placeholder: '8000000',
            onChanged: (value) => setState(() => _newPersonaMinimumSalaryYen = value),
          ),
          _dropdownField(
            labelText: 'Ranking strategy',
            value: _newPersonaRankingStrategy,
            options: _rankingStrategyOptions,
            onChanged: (v) => setState(() => _newPersonaRankingStrategy = v),
          ),
        ]),
        _buildSignalWeightsEditor(
          weights: _newPersonaSignalWeights,
          signalNames: signalNames,
          onAdd: () => setState(() => _newPersonaSignalWeights.add(('', ''))),
          onRemove: (idx) => setState(() => _newPersonaSignalWeights.removeAt(idx)),
          onSignalChanged: (idx, v) => setState(() {
            _newPersonaSignalWeights[idx] = (v, _newPersonaSignalWeights[idx].$2);
          }),
          onWeightChanged: (idx, v) => setState(() {
            _newPersonaSignalWeights[idx] = (_newPersonaSignalWeights[idx].$1, v);
          }),
        ),
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
          for (final personaId in config.personaIds) ...[
            div(classes: 'company-item', [
              span(classes: 'company-name', [.text(personaId)]),
              button(
                onClick: _tuningPersonaId == personaId ? _closeTuning : () => _openTuning(personaId),
                [.text(_tuningPersonaId == personaId ? 'Close' : 'Tune')],
              ),
            ]),
            if (_tuningPersonaId == personaId) _buildTuningPanel(personaId, signalNames),
          ],
        ]),
      ]),
    ]);
  }

  Component _buildTuningPanel(String personaId, List<String> signalNames) {
    if (_isLoadingTuning) {
      return div(classes: 'tuning-panel', [
        p([.text('Loading persona detail...')]),
      ]);
    }

    return div(classes: 'tuning-panel', [
      h4([.text('Tuning: $personaId')]),
      div(classes: 'form-grid', [
        _dropdownField(
          labelText: 'Ranking strategy',
          value: _tuningRankingStrategy,
          options: _rankingStrategyOptions,
          onChanged: (v) => setState(() => _tuningRankingStrategy = v),
        ),
        _textField(
          labelText: 'Minimum salary yen (optional)',
          value: _tuningMinimumSalaryYen,
          placeholder: '8000000',
          onChanged: (value) => setState(() => _tuningMinimumSalaryYen = value),
        ),
      ]),
      _buildSignalWeightsEditor(
        weights: _tuningSignalWeights,
        signalNames: signalNames,
        onAdd: () => setState(() => _tuningSignalWeights.add(('', ''))),
        onRemove: (idx) => setState(() => _tuningSignalWeights.removeAt(idx)),
        onSignalChanged: (idx, v) => setState(() {
          _tuningSignalWeights[idx] = (v, _tuningSignalWeights[idx].$2);
        }),
        onWeightChanged: (idx, v) => setState(() {
          _tuningSignalWeights[idx] = (_tuningSignalWeights[idx].$1, v);
        }),
      ),
      div(classes: 'actions', [
        button(
          classes: 'primary',
          onClick: _isSavingTuning ? null : _saveTuning,
          [if (_isSavingTuning) .text('Saving...') else .text('Save tuning')],
        ),
        button(onClick: _closeTuning, [.text('Cancel')]),
      ]),
    ]);
  }

  Component _buildCandidateProfileScreen(OpsConfigPayload config) {
    if (config.candidateProfileIds.isEmpty) {
      return div(classes: 'screen-stack', [
        section(classes: 'panel', [
          h2([.text('Candidate Profile')]),
          p([.text('No candidate profiles are configured yet.')]),
        ]),
      ]);
    }

    final detail = _candidateProfileDetail;

    return div(classes: 'screen-stack', [
      section(classes: 'panel', [
        div(classes: 'panel-header-inline', [
          div([
            h2([.text('Candidate Profile')]),
            p([.text('Read-only view of full candidate profiles loaded from YAML.')]),
          ]),
          button(
            onClick: _isLoadingCandidateProfileDetail
                ? null
                : () => _loadCandidateProfileDetail(_selectedCandidateProfileViewId),
            [.text('Refresh profile')],
          ),
        ]),
        div(classes: 'candidate-profile-shell', [
          div(classes: 'candidate-profile-list', [
            p(classes: 'list-heading', [.text('Available profiles')]),
            for (final profileId in config.candidateProfileIds)
              button(
                classes: profileId == _selectedCandidateProfileViewId ? 'profile-link selected' : 'profile-link',
                onClick: () => _loadCandidateProfileDetail(profileId),
                [.text(profileId)],
              ),
          ]),
          div(classes: 'candidate-profile-detail', [
            if (_isLoadingCandidateProfileDetail)
              p([.text('Loading candidate profile...')])
            else if (detail == null)
              p([.text('Select a candidate profile to inspect its full content.')])
            else ...[
              div(classes: 'candidate-hero', [
                div([
                  h3([.text(detail.name.isEmpty ? detail.id : detail.name)]),
                  if (detail.title.isNotEmpty) p([.text(detail.title)]),
                  if (detail.location.isNotEmpty) p([.text(detail.location)]),
                ]),
                div(classes: 'candidate-metrics', [
                  _profileStat('Profile id', detail.id),
                  _profileStat('Experience', '${detail.totalExperienceYears} years'),
                  _profileStat('Production skills', '${detail.productionSkills.length}'),
                  _profileStat(
                    'Domain signals',
                    '${detail.strongDomains.length + detail.moderateDomains.length + detail.limitedDomains.length}',
                  ),
                ]),
              ]),
              div(classes: 'profile-section-grid', [
                _profileTagSection(
                  'Production-proven',
                  detail.productionSkills,
                  tone: 'positive',
                ),
                _profileTagSection(
                  'Actively learning',
                  detail.learningSkills,
                  tone: 'neutral',
                ),
                _profileTagSection(
                  'Honest gaps',
                  detail.gapSkills,
                  tone: 'risk',
                ),
                _profileTagSection(
                  'Strong domains',
                  detail.strongDomains,
                  tone: 'positive',
                ),
                _profileTagSection(
                  'Moderate domains',
                  detail.moderateDomains,
                  tone: 'neutral',
                ),
                _profileTagSection(
                  'Limited domains',
                  detail.limitedDomains,
                  tone: 'risk',
                ),
              ]),
              div(classes: 'profile-content-section', [
                div(classes: 'panel-header-inline', [
                  h3([.text('Full Profile Content')]),
                  code([.text('config/candidate-profiles/${detail.id}.yaml')]),
                ]),
                _buildProfileContent(detail.content),
              ]),
              div(classes: 'yaml-preview', [
                div(classes: 'panel-header-inline', [
                  h3([.text('Source YAML')]),
                  code([.text('config/candidate-profiles/${detail.id}.yaml')]),
                ]),
                pre(classes: 'logs yaml-raw', [
                  .text(detail.rawYaml.isEmpty ? 'No YAML content loaded.' : detail.rawYaml),
                ]),
              ]),
            ],
          ]),
        ]),
      ]),
    ]);
  }

  Component _profileStat(String labelText, String valueText) {
    return div(classes: 'profile-stat', [
      span(classes: 'profile-stat-label', [.text(labelText)]),
      span(classes: 'profile-stat-value', [.text(valueText)]),
    ]);
  }

  Component _profileTagSection(String title, List<String> values, {required String tone}) {
    return div(classes: 'tag-group', [
      h4([.text(title)]),
      if (values.isEmpty)
        p(classes: 'muted-copy', [.text('No values defined.')])
      else
        div(classes: 'tag-list', [
          for (final value in values)
            span(classes: 'tag $tone', [
              .text(value),
            ]),
        ]),
    ]);
  }

  Component _buildProfileContent(Map<String, Object?> content) {
    if (content.isEmpty) {
      return p(classes: 'muted-copy', [.text('No structured profile content is available.')]);
    }

    return div(classes: 'profile-content-root', [
      for (final entry in content.entries)
        _buildProfileNode(
          _formatProfileKey(entry.key),
          entry.value,
          depth: 0,
        ),
    ]);
  }

  Component _buildProfileNode(String label, Object? value, {required int depth}) {
    final mapValue = _asProfileMap(value);
    final listValue = _asProfileList(value);
    final classes = depth == 0 ? 'profile-node' : 'profile-node nested';

    if (mapValue != null) {
      return div(classes: classes, [
        div(classes: 'profile-node-header', [
          span(classes: 'profile-node-label', [.text(label)]),
        ]),
        if (mapValue.isEmpty)
          p(classes: 'muted-copy', [.text('No values defined.')])
        else
          div(classes: 'profile-node-children', [
            for (final entry in mapValue.entries)
              _buildProfileNode(
                _formatProfileKey(entry.key),
                entry.value,
                depth: depth + 1,
              ),
          ]),
      ]);
    }

    if (listValue != null) {
      return div(classes: classes, [
        div(classes: 'profile-node-header', [
          span(classes: 'profile-node-label', [.text(label)]),
        ]),
        if (listValue.isEmpty)
          p(classes: 'muted-copy', [.text('No values defined.')])
        else if (_isScalarList(listValue))
          div(classes: 'profile-value-list', [
            for (final item in listValue)
              div(classes: 'profile-value-item', [
                .text(_profileScalarText(item)),
              ]),
          ])
        else
          div(classes: 'profile-node-children', [
            for (int idx = 0; idx < listValue.length; idx++)
              _buildProfileNode(
                _profileListEntryLabel(listValue[idx], idx),
                listValue[idx],
                depth: depth + 1,
              ),
          ]),
      ]);
    }

    return div(classes: classes, [
      div(classes: 'profile-node-header', [
        span(classes: 'profile-node-label', [.text(label)]),
      ]),
      div(classes: 'profile-scalar', [
        .text(_profileScalarText(value)),
      ]),
    ]);
  }

  Map<String, Object?>? _asProfileMap(Object? value) {
    if (value is Map<String, Object?>) {
      return value;
    }
    if (value is Map) {
      return {
        for (final entry in value.entries) entry.key.toString(): entry.value,
      };
    }
    return null;
  }

  List<Object?>? _asProfileList(Object? value) {
    if (value is List<Object?>) {
      return value;
    }
    if (value is List) {
      return value.cast<Object?>();
    }
    return null;
  }

  bool _isScalarList(List<Object?> values) {
    for (final value in values) {
      if (_asProfileMap(value) != null || _asProfileList(value) != null) {
        return false;
      }
    }
    return true;
  }

  String _profileListEntryLabel(Object? value, int index) {
    final mapValue = _asProfileMap(value);
    if (mapValue == null) {
      return 'Entry ${index + 1}';
    }

    final role = _firstProfileString(mapValue, const ['role', 'name', 'degree', 'id', 'title']);
    final context = _firstProfileString(mapValue, const ['company', 'institution', 'domain']);
    if (role.isNotEmpty && context.isNotEmpty && role != context) {
      return '$role - $context';
    }
    if (role.isNotEmpty) {
      return role;
    }
    if (context.isNotEmpty) {
      return context;
    }
    return 'Entry ${index + 1}';
  }

  String _firstProfileString(Map<String, Object?> values, List<String> keys) {
    for (final key in keys) {
      final raw = values[key];
      final text = raw?.toString().trim() ?? '';
      if (text.isNotEmpty) {
        return text;
      }
    }
    return '';
  }

  String _profileScalarText(Object? value) {
    final text = value?.toString().trim() ?? '';
    return text.isEmpty ? '-' : text;
  }

  String _formatProfileKey(String key) {
    final normalized = key.trim();
    if (normalized.isEmpty) {
      return 'Value';
    }

    final parts = normalized
        .split(RegExp(r'[_\-\s]+'))
        .where((part) => part.trim().isNotEmpty)
        .map((part) => '${part[0].toUpperCase()}${part.substring(1)}')
        .toList();
    return parts.isEmpty ? normalized : parts.join(' ');
  }

  Component _buildSignalWeightsEditor({
    required List<(String, String)> weights,
    required List<String> signalNames,
    required void Function() onAdd,
    required void Function(int) onRemove,
    required void Function(int, String) onSignalChanged,
    required void Function(int, String) onWeightChanged,
  }) {
    return div(classes: 'signal-weights-editor', [
      div(classes: 'panel-header-inline', [
        p(classes: 'signal-weights-label', [.text('Signal weights (optional overrides)')]),
        button(onClick: onAdd, [.text('+ Add signal weight')]),
      ]),
      for (int idx = 0; idx < weights.length; idx++)
        div(classes: 'signal-weight-row', [
          select(
            value: weights[idx].$1,
            onChange: (values) => onSignalChanged(idx, values.isEmpty ? '' : values.first),
            [
              option(value: '', selected: weights[idx].$1.isEmpty, [.text('— select signal —')]),
              for (final name in signalNames) option(value: name, selected: weights[idx].$1 == name, [.text(name)]),
            ],
          ),
          input<String>(
            type: InputType.text,
            value: weights[idx].$2,
            attributes: const {'placeholder': 'weight', 'style': 'width:70px'},
            onInput: (v) => onWeightChanged(idx, v),
          ),
          button(onClick: () => onRemove(idx), [.text('×')]),
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
        p([.text('Known candidate profiles: ${config.candidateProfileIds.length}')]),
        p([.text('Known companies: ${config.companies.length}')]),
        p([.text('Signal catalog: ${_signalCatalog.length} signals loaded')]),
        if (config.candidateProfileIds.isNotEmpty)
          p([.text('Candidate profiles: ${config.candidateProfileIds.join(', ')}')]),
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

  Component _dropdownField({
    required String labelText,
    required String value,
    required List<String> options,
    required void Function(String) onChanged,
  }) {
    return label([
      .text(labelText),
      select(
        value: value,
        onChange: (values) => onChanged(values.isEmpty ? options.first : values.first),
        [
          for (final opt in options) option(value: opt, selected: opt == value, [.text(opt)]),
        ],
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
        'min-height': '100vh',
        'display': 'grid',
        'grid-template-columns': '16rem 1fr',
        'background': '#eef2f7',
      },
    ),
    css('.panel').styles(
      raw: const {
        'background': '#ffffff',
        'border': '1px solid #d9dfeb',
        'border-radius': '12px',
        'padding': '1.05rem',
        'box-shadow': '0 4px 10px rgba(15, 23, 40, 0.08)',
      },
    ),
    css('.sidebar').styles(
      raw: const {
        'width': '16rem',
        'min-height': '100vh',
        'box-sizing': 'border-box',
        'padding': '1.2rem',
        'background': '#0f1728',
        'color': '#ffffff',
        'position': 'sticky',
        'top': '0',
        'display': 'flex',
        'flex-direction': 'column',
        'gap': '1rem',
        'align-self': 'start',
      },
    ),
    css('.brand-title').styles(
      raw: const {
        'margin': '0',
        'font-size': '1.15rem',
        'line-height': '1.25',
        'color': '#ffffff',
      },
    ),
    css('.brand-caption').styles(
      raw: const {
        'margin': '0',
        'color': '#b8c4d8',
        'font-size': '.82rem',
      },
    ),
    css('.menu').styles(
      raw: const {
        'display': 'grid',
        'gap': '.35rem',
      },
    ),
    css('.menu-item').styles(
      raw: const {
        'text-align': 'left',
        'border-radius': '8px',
        'border': '1px solid transparent',
        'background': 'transparent',
        'color': '#dbe3f0',
        'padding': '.55rem .7rem',
        'font-weight': '600',
      },
    ),
    css('.menu-item.selected').styles(
      raw: const {
        'background': '#01589B',
        'border-color': '#01589B',
        'color': '#ffffff',
        'font-weight': '700',
      },
    ),
    css('.menu-item:hover').styles(
      raw: const {
        'background': '#1f2c44',
        'color': '#ffffff',
      },
    ),
    css('.sidebar-meta').styles(
      raw: const {
        'margin-top': '.4rem',
        'padding-top': '.8rem',
        'border-top': '1px solid #233048',
        'font-size': '.88rem',
        'color': '#9fb0ca',
      },
    ),
    css('.main-content').styles(
      raw: const {
        'display': 'grid',
        'gap': '.9rem',
        'padding': '1.25rem',
      },
    ),
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
        'color': '#334155',
        'font-size': '.9rem',
        'font-weight': '600',
      },
    ),
    css('select, input, textarea').styles(
      raw: const {
        'padding': '.45rem .55rem',
        'border-radius': '8px',
        'border': '1px solid #c6ceda',
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
        'border': '1px solid #c6ceda',
        'background': '#ffffff',
        'color': '#223041',
        'padding': '.5rem .75rem',
      },
    ),
    css('.chip.selected').styles(
      raw: const {
        'border-color': '#01589B',
        'background': '#eaf2fb',
        'color': '#01589B',
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
        'border-radius': '8px',
        'border': '1px solid #c6ceda',
        'background': '#ffffff',
        'padding': '.5rem .75rem',
        'cursor': 'pointer',
        'font': 'inherit',
        'color': '#223041',
      },
    ),
    css('button.primary').styles(
      raw: const {
        'background': '#01589B',
        'border-color': '#01589B',
        'color': '#ffffff',
        'font-weight': '700',
      },
    ),
    css('button:disabled').styles(raw: const {'opacity': '.65', 'cursor': 'default'}),
    css('.runs-table').styles(raw: const {'width': '100%', 'border-collapse': 'collapse'}),
    css('.runs-table th, .runs-table td').styles(
      raw: const {
        'border-bottom': '1px solid #d5dbe5',
        'padding': '.48rem .45rem',
        'vertical-align': 'top',
        'text-align': 'left',
      },
    ),
    css('.runs-table th').styles(raw: const {'background': '#edf3fb'}),
    css('.selected-row').styles(raw: const {'background': '#f4f8ff'}),
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
        'border': '1px solid #d8dfeb',
        'border-radius': '9px',
        'background': '#ffffff',
      },
    ),
    css('.company-name').styles(raw: const {'font-weight': '600'}),
    css('.tuning-panel').styles(
      raw: const {
        'padding': '.8rem 1rem',
        'background': '#f8fbff',
        'border': '1px solid #d9dfeb',
        'border-radius': '10px',
        'display': 'grid',
        'gap': '.6rem',
      },
    ),
    css('.candidate-profile-shell').styles(
      raw: const {
        'display': 'grid',
        'grid-template-columns': '240px 1fr',
        'gap': '.9rem',
        'margin-top': '.5rem',
      },
    ),
    css('.candidate-profile-list').styles(
      raw: const {
        'display': 'grid',
        'gap': '.45rem',
        'align-content': 'start',
      },
    ),
    css('.candidate-profile-detail').styles(raw: const {'display': 'grid', 'gap': '.9rem'}),
    css('.list-heading').styles(
      raw: const {
        'margin': '0 0 .15rem 0',
        'font-weight': '700',
        'color': '#0f1728',
      },
    ),
    css('.profile-link').styles(
      raw: const {
        'text-align': 'left',
        'border-radius': '8px',
        'border': '1px solid #c6ceda',
        'background': '#ffffff',
        'padding': '.6rem .7rem',
      },
    ),
    css('.profile-link.selected').styles(
      raw: const {
        'background': '#01589B',
        'border-color': '#01589B',
        'color': '#ffffff',
      },
    ),
    css('.candidate-hero').styles(
      raw: const {
        'display': 'grid',
        'gap': '.9rem',
      },
    ),
    css('.candidate-metrics').styles(
      raw: const {
        'display': 'grid',
        'gap': '.65rem',
        'grid-template-columns': 'repeat(auto-fit, minmax(150px, 1fr))',
      },
    ),
    css('.profile-stat').styles(
      raw: const {
        'border': '1px solid #d8dfeb',
        'border-radius': '8px',
        'padding': '.7rem',
        'background': '#ffffff',
        'display': 'grid',
        'gap': '.2rem',
      },
    ),
    css('.profile-stat-label').styles(raw: const {'color': '#667085', 'font-size': '.82rem'}),
    css('.profile-stat-value').styles(raw: const {'font-weight': '700', 'color': '#0f172a'}),
    css('.profile-section-grid').styles(
      raw: const {
        'display': 'grid',
        'gap': '.7rem',
        'grid-template-columns': 'repeat(auto-fit, minmax(220px, 1fr))',
      },
    ),
    css('.tag-group').styles(
      raw: const {
        'border': '1px solid #d9dfeb',
        'border-radius': '10px',
        'padding': '.8rem',
        'background': '#fbfcfe',
        'display': 'grid',
        'gap': '.55rem',
      },
    ),
    css('.tag-group h4').styles(raw: const {'margin': '0', 'color': '#0f1728'}),
    css('.tag-list').styles(
      raw: const {
        'display': 'flex',
        'flex-wrap': 'wrap',
        'gap': '.45rem',
      },
    ),
    css('.tag').styles(
      raw: const {
        'display': 'inline-block',
        'padding': '.28rem .55rem',
        'border-radius': '999px',
        'font-size': '.85rem',
        'font-weight': '600',
      },
    ),
    css('.tag.positive').styles(raw: const {'background': '#dff7ea', 'color': '#165b3e'}),
    css('.tag.neutral').styles(raw: const {'background': '#eff3fa', 'color': '#334155'}),
    css('.tag.risk').styles(raw: const {'background': '#ffe8e2', 'color': '#8f2e20'}),
    css('.muted-copy').styles(raw: const {'margin': '0', 'color': '#667085'}),
    css('.yaml-preview').styles(raw: const {'display': 'grid', 'gap': '.5rem'}),
    css('.yaml-raw').styles(raw: const {'margin': '0'}),
    css('.profile-content-section').styles(raw: const {'display': 'grid', 'gap': '.6rem'}),
    css('.profile-content-root').styles(raw: const {'display': 'grid', 'gap': '.7rem'}),
    css('.profile-node').styles(
      raw: const {
        'display': 'grid',
        'gap': '.6rem',
        'padding': '.85rem',
        'border': '1px solid #d9dfeb',
        'border-radius': '10px',
        'background': '#ffffff',
      },
    ),
    css('.profile-node.nested').styles(
      raw: const {
        'background': '#f8fbff',
        'border-left': '3px solid #cfe0ff',
      },
    ),
    css('.profile-node-header').styles(
      raw: const {
        'display': 'flex',
        'align-items': 'center',
        'gap': '.4rem',
      },
    ),
    css('.profile-node-label').styles(
      raw: const {
        'font-weight': '700',
        'color': '#0f172a',
      },
    ),
    css('.profile-node-children').styles(raw: const {'display': 'grid', 'gap': '.6rem'}),
    css('.profile-value-list').styles(raw: const {'display': 'grid', 'gap': '.45rem'}),
    css('.profile-value-item').styles(
      raw: const {
        'padding': '.55rem .65rem',
        'border': '1px solid #e1e7f0',
        'border-radius': '8px',
        'background': '#f8fafc',
        'white-space': 'pre-wrap',
        'line-height': '1.5',
      },
    ),
    css('.profile-scalar').styles(
      raw: const {
        'padding': '.55rem .65rem',
        'border': '1px solid #e1e7f0',
        'border-radius': '8px',
        'background': '#f8fafc',
        'white-space': 'pre-wrap',
        'line-height': '1.5',
      },
    ),
    css('.signal-weights-editor').styles(
      raw: const {
        'display': 'grid',
        'gap': '.45rem',
        'margin-top': '.5rem',
      },
    ),
    css('.signal-weights-label').styles(raw: const {'margin': '0', 'font-weight': '600'}),
    css('.signal-weight-row').styles(
      raw: const {
        'display': 'flex',
        'align-items': 'center',
        'gap': '.4rem',
        'flex-wrap': 'wrap',
      },
    ),
    css('.signal-weight-row select').styles(raw: const {'flex': '1', 'min-width': '180px'}),
    css.media(const MediaQuery.raw('(max-width: 1040px)'), [
      css('.shell').styles(raw: const {'grid-template-columns': '1fr'}),
      css('.sidebar').styles(
        raw: const {
          'position': 'static',
          'width': '100%',
          'min-height': '0',
        },
      ),
      css('.menu').styles(
        raw: const {
          'grid-template-columns': 'repeat(3, minmax(120px, 1fr))',
        },
      ),
      css('.main-content').styles(raw: const {'padding': '.8rem'}),
    ]),
    css.media(const MediaQuery.raw('(max-width: 920px)'), [
      css('.form-grid').styles(raw: const {'grid-template-columns': '1fr'}),
      css('.span-2').styles(raw: const {'grid-column': 'span 1'}),
      css('.company-grid').styles(raw: const {'grid-template-columns': '1fr'}),
      css('.candidate-profile-shell').styles(raw: const {'grid-template-columns': '1fr'}),
      css('.runs-table').styles(raw: const {'display': 'block', 'overflow': 'auto', 'white-space': 'nowrap'}),
    ]),
    css.media(const MediaQuery.raw('(max-width: 720px)'), [
      css('.menu').styles(raw: const {'grid-template-columns': '1fr 1fr'}),
      css('.panel').styles(raw: const {'padding': '.8rem'}),
      css('.actions').styles(raw: const {'flex-direction': 'column'}),
      css('.actions button').styles(raw: const {'width': '100%'}),
      css('.candidate-metrics').styles(raw: const {'grid-template-columns': '1fr'}),
      css('.panel-header-inline').styles(raw: const {'flex-direction': 'column', 'align-items': 'stretch'}),
    ]),
  ];
}
