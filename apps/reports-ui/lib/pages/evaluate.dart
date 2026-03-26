import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../constants/evaluation_contract.dart';
import '../models/evaluation_payload.dart';
import '../services/evaluation_api.dart';
import 'reports_view_helpers.dart';

const _allSourcesFilter = 'all_sources';

@client
class EvaluatePage extends StatefulComponent {
  const EvaluatePage({super.key});

  @override
  State<EvaluatePage> createState() => _EvaluatePageState();
}

class _EvaluatePageState extends State<EvaluatePage> {
  static const _client = EvaluationApiClient();

  EvaluationOptionsPayload? _options;
  EvaluationSessionPayload? _session;
  List<EvaluationUrlHistoryItemPayload> _urlHistory = const [];
  String? _loadError;
  String? _historyError;
  String? _submitError;
  bool _isLoading = true;
  bool _isSubmitting = false;

  String _inputMode = inputModeRawText;
  String _selectedPersonaId = allPersonasOptionId;
  String _selectedCandidateProfileId = candidateProfileNone;
  String _jobUrl = '';
  String _rawText = '';
  String _urlHistoryQuery = '';
  String _selectedHistorySource = _allSourcesFilter;
  bool _isInputPanelExpanded = true;
  bool _isHistoryPanelExpanded = false;
  Set<String> _expandedResultPersonas = const <String>{};

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _loadPageData();
    } else {
      _isLoading = false;
    }
  }

  Future<void> _loadPageData() async {
    setState(() {
      _isLoading = true;
      _loadError = null;
    });
    try {
      final options = await _client.fetchOptions();
      List<EvaluationUrlHistoryItemPayload> urlHistory = const [];
      String? historyError;
      try {
        final history = await _client.fetchUrlHistory();
        urlHistory = history.items;
      } catch (error) {
        historyError = error.toString();
      }
      setState(() {
        _options = options;
        _selectedPersonaId = _resolveSelectedPersona(options);
        _selectedCandidateProfileId = _resolveSelectedCandidateProfile(options);
        _urlHistory = urlHistory;
        _historyError = historyError;
        _isLoading = false;
      });
    } catch (error) {
      setState(() {
        _isLoading = false;
        _loadError = error.toString();
      });
    }
  }

  Future<void> _refreshUrlHistory() async {
    try {
      final history = await _client.fetchUrlHistory();
      setState(() {
        _urlHistory = history.items;
        _historyError = null;
      });
    } catch (error) {
      setState(() {
        _historyError = error.toString();
      });
    }
  }

  Future<void> _submit() async {
    setState(() {
      _isSubmitting = true;
      _submitError = null;
    });
    try {
      final result = await _client.evaluate({
        'inputMode': _inputMode,
        'personaId': _selectedPersonaId,
        'candidateProfileId': _selectedCandidateProfileId,
        'jobUrl': _jobUrl,
        'rawText': _rawText,
      });
      setState(() {
        _session = result;
        _expandedResultPersonas = result.results.isEmpty ? const <String>{} : {result.results.first.persona};
        _isSubmitting = false;
      });
      if (_inputMode == inputModeUrl) {
        await _refreshUrlHistory();
      }
    } catch (error) {
      setState(() {
        _isSubmitting = false;
        _submitError = error.toString();
      });
    }
  }

  void _useHistoryUrl(String url) {
    setState(() {
      _inputMode = inputModeUrl;
      _jobUrl = url;
      _rawText = '';
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updateInputMode(String value) {
    setState(() {
      _inputMode = value;
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updatePersona(String value) {
    setState(() {
      _selectedPersonaId = value;
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updateCandidateProfile(String value) {
    setState(() {
      _selectedCandidateProfileId = value;
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updateJobUrl(String value) {
    setState(() {
      _jobUrl = value;
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updateRawText(String value) {
    setState(() {
      _rawText = value;
      _submitError = null;
      _session = null;
      _expandedResultPersonas = const <String>{};
    });
  }

  void _updateUrlHistoryQuery(String value) {
    setState(() {
      _urlHistoryQuery = value;
    });
  }

  void _updateHistorySource(String value) {
    setState(() {
      _selectedHistorySource = value;
    });
  }

  void _toggleInputPanel() {
    setState(() {
      _isInputPanelExpanded = !_isInputPanelExpanded;
    });
  }

  void _toggleHistoryPanel() {
    setState(() {
      _isHistoryPanelExpanded = !_isHistoryPanelExpanded;
    });
  }

  void _toggleResultPanel(String personaId) {
    setState(() {
      final expanded = {..._expandedResultPersonas};
      if (expanded.contains(personaId)) {
        expanded.remove(personaId);
      } else {
        expanded.add(personaId);
      }
      _expandedResultPersonas = expanded;
    });
  }

  String _resolveSelectedPersona(EvaluationOptionsPayload options) {
    if (_selectedPersonaId == allPersonasOptionId) {
      return allPersonasOptionId;
    }
    for (final persona in options.personas) {
      if (persona.id == _selectedPersonaId) {
        return _selectedPersonaId;
      }
    }
    return allPersonasOptionId;
  }

  String _resolveSelectedCandidateProfile(EvaluationOptionsPayload options) {
    for (final profile in options.candidateProfiles) {
      if (profile.id == _selectedCandidateProfileId) {
        return _selectedCandidateProfileId;
      }
    }
    return options.candidateProfiles.isEmpty ? candidateProfileNone : options.candidateProfiles.first.id;
  }

  @override
  Component build(BuildContext context) {
    if (!kIsWeb) return pageLoading('Evaluate', 'Loading evaluation tools on the client...');
    if (_isLoading) return pageLoading('Evaluate', 'Loading evaluation options...');
    if (_loadError != null) return pageError('Evaluate', _loadError!, _loadPageData);
    final options = _options;
    if (options == null || options.personas.isEmpty) {
      return pageEmpty('Evaluate', 'No personas are available for ad-hoc evaluation.');
    }
    return _EvaluateBody(
      options: options,
      inputMode: _inputMode,
      selectedPersonaId: _selectedPersonaId,
      selectedCandidateProfileId: _selectedCandidateProfileId,
      jobUrl: _jobUrl,
      rawText: _rawText,
      isInputPanelExpanded: _isInputPanelExpanded,
      isHistoryPanelExpanded: _isHistoryPanelExpanded,
      expandedResultPersonas: _expandedResultPersonas,
      urlHistory: _urlHistory,
      urlHistoryQuery: _urlHistoryQuery,
      selectedHistorySource: _selectedHistorySource,
      historyError: _historyError,
      submitError: _submitError,
      session: _session,
      isSubmitting: _isSubmitting,
      onInputModeChanged: _updateInputMode,
      onPersonaChanged: _updatePersona,
      onCandidateProfileChanged: _updateCandidateProfile,
      onJobUrlChanged: _updateJobUrl,
      onRawTextChanged: _updateRawText,
      onUrlHistoryQueryChanged: _updateUrlHistoryQuery,
      onHistorySourceChanged: _updateHistorySource,
      onToggleInputPanel: _toggleInputPanel,
      onToggleHistoryPanel: _toggleHistoryPanel,
      onToggleResultPanel: _toggleResultPanel,
      onUseUrl: _useHistoryUrl,
      onSubmit: _submit,
    );
  }
}

class _EvaluateBody extends StatelessComponent {
  const _EvaluateBody({
    required this.options,
    required this.inputMode,
    required this.selectedPersonaId,
    required this.selectedCandidateProfileId,
    required this.jobUrl,
    required this.rawText,
    required this.isInputPanelExpanded,
    required this.isHistoryPanelExpanded,
    required this.expandedResultPersonas,
    required this.urlHistory,
    required this.urlHistoryQuery,
    required this.selectedHistorySource,
    required this.historyError,
    required this.submitError,
    required this.session,
    required this.isSubmitting,
    required this.onInputModeChanged,
    required this.onPersonaChanged,
    required this.onCandidateProfileChanged,
    required this.onJobUrlChanged,
    required this.onRawTextChanged,
    required this.onUrlHistoryQueryChanged,
    required this.onHistorySourceChanged,
    required this.onToggleInputPanel,
    required this.onToggleHistoryPanel,
    required this.onToggleResultPanel,
    required this.onUseUrl,
    required this.onSubmit,
  });

  final EvaluationOptionsPayload options;
  final String inputMode;
  final String selectedPersonaId;
  final String selectedCandidateProfileId;
  final String jobUrl;
  final String rawText;
  final bool isInputPanelExpanded;
  final bool isHistoryPanelExpanded;
  final Set<String> expandedResultPersonas;
  final List<EvaluationUrlHistoryItemPayload> urlHistory;
  final String urlHistoryQuery;
  final String selectedHistorySource;
  final String? historyError;
  final String? submitError;
  final EvaluationSessionPayload? session;
  final bool isSubmitting;
  final void Function(String) onInputModeChanged;
  final void Function(String) onPersonaChanged;
  final void Function(String) onCandidateProfileChanged;
  final void Function(String) onJobUrlChanged;
  final void Function(String) onRawTextChanged;
  final void Function(String) onUrlHistoryQueryChanged;
  final void Function(String) onHistorySourceChanged;
  final void Function() onToggleInputPanel;
  final void Function() onToggleHistoryPanel;
  final void Function(String) onToggleResultPanel;
  final void Function(String) onUseUrl;
  final void Function() onSubmit;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      h1([.text('Evaluate')]),
      _PanelCard(
        title: 'Evaluate Input',
        description:
            'Paste a job post or submit a URL for ad-hoc evaluation against one persona or all configured personas.',
        isExpanded: isInputPanelExpanded,
        onToggle: onToggleInputPanel,
        children: [
          _EvaluateForm(
            options: options,
            inputMode: inputMode,
            selectedPersonaId: selectedPersonaId,
            selectedCandidateProfileId: selectedCandidateProfileId,
            jobUrl: jobUrl,
            rawText: rawText,
            isSubmitting: isSubmitting,
            onInputModeChanged: onInputModeChanged,
            onPersonaChanged: onPersonaChanged,
            onCandidateProfileChanged: onCandidateProfileChanged,
            onJobUrlChanged: onJobUrlChanged,
            onRawTextChanged: onRawTextChanged,
            onSubmit: onSubmit,
          ),
          if (submitError != null) p(classes: 'error', [.text(submitError!)]),
        ],
      ),
      _EvaluateUrlHistoryCard(
        items: urlHistory,
        query: urlHistoryQuery,
        selectedSource: selectedHistorySource,
        isExpanded: isHistoryPanelExpanded,
        error: historyError,
        onQueryChanged: onUrlHistoryQueryChanged,
        onSourceChanged: onHistorySourceChanged,
        onToggle: onToggleHistoryPanel,
        onUseUrl: onUseUrl,
      ),
      if (session != null)
        _EvaluateResultsSection(
          session: session!,
          expandedPersonas: expandedResultPersonas,
          onToggleResultPanel: onToggleResultPanel,
        ),
    ]);
  }
}

class _EvaluateForm extends StatelessComponent {
  const _EvaluateForm({
    required this.options,
    required this.inputMode,
    required this.selectedPersonaId,
    required this.selectedCandidateProfileId,
    required this.jobUrl,
    required this.rawText,
    required this.isSubmitting,
    required this.onInputModeChanged,
    required this.onPersonaChanged,
    required this.onCandidateProfileChanged,
    required this.onJobUrlChanged,
    required this.onRawTextChanged,
    required this.onSubmit,
  });

  final EvaluationOptionsPayload options;
  final String inputMode;
  final String selectedPersonaId;
  final String selectedCandidateProfileId;
  final String jobUrl;
  final String rawText;
  final bool isSubmitting;
  final void Function(String) onInputModeChanged;
  final void Function(String) onPersonaChanged;
  final void Function(String) onCandidateProfileChanged;
  final void Function(String) onJobUrlChanged;
  final void Function(String) onRawTextChanged;
  final void Function() onSubmit;

  @override
  Component build(BuildContext context) {
    return div(classes: 'controls form-grid', [
      ..._selectorFields(),
      _sourceInputField(),
      _submitButton(),
    ]);
  }

  Component _textField({
    required String labelText,
    required String value,
    required String placeholder,
    required void Function(String) onChanged,
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

  Component _selectField({
    required String labelText,
    required String value,
    required List<(String, String)> options,
    required void Function(String) onChanged,
  }) {
    return label([
      .text(labelText),
      select(
        value: value,
        onChange: (values) => onChanged(values.isEmpty ? options.first.$1 : values.first),
        [
          for (final optionEntry in options)
            option(
              value: optionEntry.$1,
              selected: optionEntry.$1 == value,
              [.text(optionEntry.$2)],
            ),
        ],
      ),
    ]);
  }

  List<Component> _selectorFields() {
    return [
      _selectField(
        labelText: 'Input mode',
        value: inputMode,
        options: const [(inputModeRawText, 'Raw text'), (inputModeUrl, 'URL')],
        onChanged: onInputModeChanged,
      ),
      _selectField(
        labelText: 'Persona',
        value: selectedPersonaId,
        options: [
          const (allPersonasOptionId, 'All personas'),
          for (final persona in options.personas) (persona.id, persona.label),
        ],
        onChanged: onPersonaChanged,
      ),
      _selectField(
        labelText: 'Candidate profile',
        value: selectedCandidateProfileId,
        options: [
          for (final profile in options.candidateProfiles) (profile.id, profile.label),
          const (candidateProfileNone, 'None'),
        ],
        onChanged: onCandidateProfileChanged,
      ),
    ];
  }

  Component _sourceInputField() {
    if (inputMode == inputModeUrl) {
      return div(classes: 'span-full', [
        _textField(
          labelText: 'Job URL',
          value: jobUrl,
          placeholder: 'https://company.example/jobs/backend-engineer',
          onChanged: onJobUrlChanged,
        ),
      ]);
    }
    return label(classes: 'span-full', [
      .text('Raw job text'),
      textarea(
        [.text(rawText)],
        placeholder: 'Paste the full job description here...',
        onInput: onRawTextChanged,
      ),
    ]);
  }

  Component _submitButton() {
    return div(classes: 'span-full', [
      button(
        onClick: isSubmitting ? null : onSubmit,
        [if (isSubmitting) .text('Evaluating...') else .text('Evaluate')],
      ),
    ]);
  }
}

class _PanelCard extends StatelessComponent {
  const _PanelCard({
    required this.title,
    required this.description,
    required this.isExpanded,
    required this.onToggle,
    required this.children,
  });

  final String title;
  final String description;
  final bool isExpanded;
  final void Function() onToggle;
  final List<Component> children;

  @override
  Component build(BuildContext context) {
    return card([
      div(classes: 'collapsible-header', [
        div(classes: 'collapsible-header-copy', [
          h2([.text(title)]),
          p(classes: 'collapsible-summary', [.text(description)]),
        ]),
        button(
          onClick: onToggle,
          [.text(isExpanded ? 'Hide' : 'Show')],
        ),
      ]),
      if (isExpanded) div(classes: 'collapsible-body', children),
    ]);
  }
}

class _EvaluateResultsSection extends StatelessComponent {
  const _EvaluateResultsSection({
    required this.session,
    required this.expandedPersonas,
    required this.onToggleResultPanel,
  });

  final EvaluationSessionPayload session;
  final Set<String> expandedPersonas;
  final void Function(String) onToggleResultPanel;

  @override
  Component build(BuildContext context) {
    return div([
      card([
        h2([.text('Results')]),
        p([.text(_summaryText())]),
      ]),
      for (final result in session.results)
        _EvaluateResultCard(
          result: result,
          isExpanded: expandedPersonas.contains(result.persona),
          onToggle: () => onToggleResultPanel(result.persona),
        ),
    ]);
  }

  String _summaryText() {
    final count = session.results.length;
    if (count == 0) {
      return 'No evaluation results were returned.';
    }
    final candidateProfile = session.requestedCandidateProfileId.isEmpty
        ? candidateProfileNone
        : session.requestedCandidateProfileId;
    if (session.requestedPersonaId == allPersonasOptionId) {
      return 'Evaluated $count personas against candidate profile $candidateProfile.';
    }
    return 'Evaluated 1 persona against candidate profile $candidateProfile.';
  }
}

class _EvaluateResultCard extends StatelessComponent {
  const _EvaluateResultCard({
    required this.result,
    required this.isExpanded,
    required this.onToggle,
  });

  final EvaluationResponsePayload result;
  final bool isExpanded;
  final void Function() onToggle;

  @override
  Component build(BuildContext context) {
    return _PanelCard(
      title: 'Result · ${result.persona}',
      description: '${result.evaluation.verdict} · ${result.evaluation.score}/100',
      isExpanded: isExpanded,
      onToggle: onToggle,
      children: [
        p([
          .text('Generated at: ${formatFriendlyDateTime(result.generatedAt)}'),
          if (result.generatedAt.isNotEmpty) ...[
            .text(' '),
            code([.text(result.generatedAt)]),
          ],
        ]),
        _EvaluateSourceBlock(source: result.source),
        pre([.text(_consoleOutput(result))]),
      ],
    );
  }

  String _consoleOutput(EvaluationResponsePayload payload) {
    final lines = <String>[
      'verdict: ${payload.evaluation.verdict}',
      'score: ${payload.evaluation.score}/100',
      'raw_score: ${payload.evaluation.rawScore} (range ${payload.evaluation.rawScoreMin}..${payload.evaluation.rawScoreMax})',
      'language_friction_index: ${payload.evaluation.languageFrictionIndex}/100',
      'company_reputation_index: ${payload.evaluation.companyReputationIndex}/100',
      'persona: ${payload.persona}',
      'candidate_profile: ${payload.candidateProfile}',
      'source_kind: ${payload.source.kind}',
      if (payload.source.url.isNotEmpty) 'source_url: ${payload.source.url}',
      if (payload.source.rawText.isNotEmpty) _multilineSourceText(payload.source.rawText),
      'company: ${payload.jobInput.companyName}',
      'role: ${payload.jobInput.title}',
      _listLine('hard_reject_reasons', payload.evaluation.hardRejectReasons),
      _listLine('positive_signals', payload.evaluation.positiveSignals),
      _listLine('risk_signals', payload.evaluation.riskSignals),
      _listLine('reasoning', payload.evaluation.reasoning),
    ];
    if (payload.normalizationWarnings.isNotEmpty) {
      lines.add(_listLine('normalization_warnings', payload.normalizationWarnings));
    }
    return lines.join('\n');
  }

  String _listLine(String key, List<String> values) {
    if (values.isEmpty) {
      return '$key: []';
    }
    final buffer = StringBuffer('$key:\n');
    for (final value in values) {
      buffer.writeln(' - $value');
    }
    return buffer.toString().trimRight();
  }

  String _multilineSourceText(String rawText) {
    final buffer = StringBuffer('source_raw_text:\n');
    for (final line in rawText.split(RegExp(r'\r?\n'))) {
      buffer.writeln(' | $line');
    }
    return buffer.toString().trimRight();
  }
}

class _EvaluateUrlHistoryCard extends StatelessComponent {
  const _EvaluateUrlHistoryCard({
    required this.items,
    required this.query,
    required this.selectedSource,
    required this.isExpanded,
    required this.error,
    required this.onQueryChanged,
    required this.onSourceChanged,
    required this.onToggle,
    required this.onUseUrl,
  });

  final List<EvaluationUrlHistoryItemPayload> items;
  final String query;
  final String selectedSource;
  final bool isExpanded;
  final String? error;
  final void Function(String) onQueryChanged;
  final void Function(String) onSourceChanged;
  final void Function() onToggle;
  final void Function(String) onUseUrl;

  @override
  Component build(BuildContext context) {
    final filteredItems = _filteredItems();
    final sourceOptions = _sourceOptions();

    return _PanelCard(
      title: 'Recent URLs',
      description: 'Reuse URLs already seen in processed job artifacts or ad-hoc evaluations.',
      isExpanded: isExpanded,
      onToggle: onToggle,
      children: [
        div(classes: 'controls form-grid', [
          label([
            .text('Search URLs'),
            input<String>(
              type: InputType.text,
              value: query,
              attributes: const {'placeholder': 'Filter by company, title, or URL'},
              onInput: onQueryChanged,
            ),
          ]),
          label([
            .text('Source'),
            select(
              value: selectedSource,
              onChange: (values) => onSourceChanged(values.isEmpty ? _allSourcesFilter : values.first),
              [
                option(
                  value: _allSourcesFilter,
                  selected: selectedSource == _allSourcesFilter,
                  [.text('All sources')],
                ),
                for (final source in sourceOptions)
                  option(
                    value: source,
                    selected: selectedSource == source,
                    [.text(source)],
                  ),
              ],
            ),
          ]),
        ]),
        p([
          .text('Showing ${filteredItems.length} of ${items.length} URLs'),
        ]),
        if (error != null) p(classes: 'error', [.text(error!)]),
        if (filteredItems.isEmpty && error == null)
          p([
            .text(items.isEmpty ? 'No reusable URLs were found yet.' : 'No URLs match the current filters.'),
          ])
        else if (filteredItems.isNotEmpty)
          table([
            thead([
              tr([
                th([.text('Seen')]),
                th([.text('Company')]),
                th([.text('Title')]),
                th([.text('Source')]),
                th([.text('URL')]),
                th([.text('Action')]),
              ]),
            ]),
            tbody([
              for (final item in filteredItems) _historyRow(item),
            ]),
          ]),
      ],
    );
  }

  List<String> _sourceOptions() {
    final values = <String>{};
    for (final item in items) {
      final company = item.companyName.trim();
      if (company.isNotEmpty) {
        values.add(company);
      }
      final host = _hostLabel(item.url);
      if (host.isNotEmpty) {
        values.add(host);
      }
      values.add(_sourceSummary(item));
    }
    final sources = values.toList()..sort();
    return sources;
  }

  List<EvaluationUrlHistoryItemPayload> _filteredItems() {
    final normalizedQuery = query.trim().toLowerCase();
    return items
        .where((item) {
          if (selectedSource != _allSourcesFilter && !_matchesSelectedSource(item)) {
            return false;
          }
          if (normalizedQuery.isEmpty) {
            return true;
          }
          final haystack = [
            item.companyName,
            item.title,
            item.url,
          ].join(' ').toLowerCase();
          return haystack.contains(normalizedQuery);
        })
        .toList(growable: false);
  }

  bool _matchesSelectedSource(EvaluationUrlHistoryItemPayload item) {
    return item.companyName == selectedSource ||
        _hostLabel(item.url) == selectedSource ||
        _sourceSummary(item) == selectedSource;
  }

  Component _historyRow(EvaluationUrlHistoryItemPayload item) {
    return tr([
      td([.text(formatFriendlyDateTime(item.generatedAt))]),
      td([.text(item.companyName.isEmpty ? 'Unknown' : item.companyName)]),
      td([.text(item.title.isEmpty ? 'Untitled job' : item.title)]),
      td([.text(_sourceSummary(item))]),
      td([
        a(
          href: item.url,
          target: Target.blank,
          attributes: const {'rel': 'noreferrer noopener'},
          [.text(item.url)],
        ),
      ]),
      td([
        button(
          onClick: () => onUseUrl(item.url),
          [.text('Use')],
        ),
      ]),
    ]);
  }

  String _sourceSummary(EvaluationUrlHistoryItemPayload item) {
    if (item.sourceKind == historySourceKindAdHoc) {
      final scope = [
        if (item.persona.isNotEmpty) item.persona,
        if (item.candidateProfile.isNotEmpty) item.candidateProfile,
      ].join(' / ');
      if (scope.isNotEmpty) {
        return 'Ad hoc ($scope)';
      }
      return 'Ad hoc';
    }
    return 'Pipeline job';
  }

  String _hostLabel(String rawUrl) {
    final uri = Uri.tryParse(rawUrl);
    return uri?.host ?? '';
  }
}

class _EvaluateSourceBlock extends StatelessComponent {
  const _EvaluateSourceBlock({required this.source});

  final EvaluationSourcePayload source;

  @override
  Component build(BuildContext context) {
    return div([
      p([.text('Source kind: ${source.kind.isEmpty ? 'unknown' : source.kind}')]),
      if (source.url.isNotEmpty)
        p([
          .text('Source URL: '),
          a(
            href: source.url,
            target: Target.blank,
            attributes: const {'rel': 'noreferrer noopener'},
            [.text(source.url)],
          ),
        ]),
      if (source.file.isNotEmpty)
        p([
          .text('Source file: '),
          code([.text(source.file)]),
        ]),
      if (source.rawText.isNotEmpty) ...[
        h3([.text('Original Input')]),
        pre([.text(source.rawText)]),
      ],
    ]);
  }
}
