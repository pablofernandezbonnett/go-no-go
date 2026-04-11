import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../components/evaluation_human_reading.dart';
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
  static const _description =
      'Submit a URL or pasted job post for ad-hoc evaluation and compare the output across one persona or all personas.';

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
  final Map<String, EvaluationResponsePayload> _historyDetailCache = <String, EvaluationResponsePayload>{};
  EvaluationUrlHistoryItemPayload? _selectedHistoryItem;
  EvaluationResponsePayload? _selectedHistoryDetail;
  String? _historyDetailError;
  bool _isLoadingHistoryDetail = false;

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

  Future<void> _openHistoryDetail(EvaluationUrlHistoryItemPayload item) async {
    setState(() {
      _selectedHistoryItem = item;
      _selectedHistoryDetail = _historyDetailCache[item.url];
      _historyDetailError = null;
      _isLoadingHistoryDetail = _selectedHistoryDetail == null;
    });

    final cached = _historyDetailCache[item.url];
    if (cached != null) {
      return;
    }

    try {
      final detail = await _client.fetchUrlHistoryDetail(item.url);
      _historyDetailCache[item.url] = detail;
      setState(() {
        _selectedHistoryDetail = detail;
        _historyDetailError = null;
        _isLoadingHistoryDetail = false;
      });
    } catch (error) {
      setState(() {
        _selectedHistoryDetail = null;
        _historyDetailError = error.toString();
        _isLoadingHistoryDetail = false;
      });
    }
  }

  void _closeHistoryDetail() {
    setState(() {
      _selectedHistoryItem = null;
      _selectedHistoryDetail = null;
      _historyDetailError = null;
      _isLoadingHistoryDetail = false;
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
    if (!kIsWeb) return pageLoading('Evaluate', 'Loading evaluation tools on the client...', description: _description);
    if (_isLoading) return pageLoading('Evaluate', 'Loading evaluation options...', description: _description);
    if (_loadError != null) return pageError('Evaluate', _loadError!, _loadPageData, description: _description);
    final options = _options;
    if (options == null || options.personas.isEmpty) {
      return pageEmpty('Evaluate', 'No personas are available for ad-hoc evaluation.', description: _description);
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
      onOpenHistoryDetail: _openHistoryDetail,
      onCloseHistoryDetail: _closeHistoryDetail,
      onSubmit: _submit,
      selectedHistoryItem: _selectedHistoryItem,
      selectedHistoryDetail: _selectedHistoryDetail,
      historyDetailError: _historyDetailError,
      isLoadingHistoryDetail: _isLoadingHistoryDetail,
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
    required this.onOpenHistoryDetail,
    required this.onCloseHistoryDetail,
    required this.onSubmit,
    required this.selectedHistoryItem,
    required this.selectedHistoryDetail,
    required this.historyDetailError,
    required this.isLoadingHistoryDetail,
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
  final void Function(EvaluationUrlHistoryItemPayload) onOpenHistoryDetail;
  final void Function() onCloseHistoryDetail;
  final void Function() onSubmit;
  final EvaluationUrlHistoryItemPayload? selectedHistoryItem;
  final EvaluationResponsePayload? selectedHistoryDetail;
  final String? historyDetailError;
  final bool isLoadingHistoryDetail;

  @override
  Component build(BuildContext context) {
    return div([
      _pageSection(),
      if (selectedHistoryItem != null) _historyDetailModal(),
    ]);
  }

  Component _pageSection() {
    return section(classes: 'page', [
      ...pageHeader(
        'Evaluate',
        'Submit a URL or pasted job post for ad-hoc evaluation and compare the output across one persona or all personas.',
      ),
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
        onOpenHistoryDetail: onOpenHistoryDetail,
      ),
      if (session != null)
        _EvaluateResultsSection(
          session: session!,
          expandedPersonas: expandedResultPersonas,
          onToggleResultPanel: onToggleResultPanel,
        ),
    ]);
  }

  Component _historyDetailModal() {
    return _SavedEvaluationModal(
      item: selectedHistoryItem!,
      payload: selectedHistoryDetail,
      error: historyDetailError,
      isLoading: isLoadingHistoryDetail,
      onClose: onCloseHistoryDetail,
      onUseUrl: onUseUrl,
    );
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
        placeholder: 'Paste the full job description here...',
        onInput: onRawTextChanged,
        [.text(rawText)],
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
        _EvaluationPayloadView(
          payload: result,
        ),
      ],
    );
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
    required this.onOpenHistoryDetail,
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
  final void Function(EvaluationUrlHistoryItemPayload) onOpenHistoryDetail;

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
        _filters(sourceOptions),
        p([
          .text('Showing ${filteredItems.length} of ${items.length} URLs'),
        ]),
        if (error != null) p(classes: 'error', [.text(error!)]),
        _historyContent(filteredItems),
      ],
    );
  }

  Component _filters(List<String> sourceOptions) {
    return div(classes: 'controls form-grid', [
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
    ]);
  }

  Component _historyContent(List<EvaluationUrlHistoryItemPayload> filteredItems) {
    if (filteredItems.isEmpty && error == null) {
      return p([
        .text(items.isEmpty ? 'No reusable URLs were found yet.' : 'No URLs match the current filters.'),
      ]);
    }
    if (filteredItems.isEmpty) {
      return div([]);
    }
    return reportTable(
      columns: const [
        ReportTableColumn('Seen', width: ReportTableWidth.medium),
        ReportTableColumn('Company'),
        ReportTableColumn('Title', width: ReportTableWidth.xwide),
        ReportTableColumn('Source', width: ReportTableWidth.wide),
        ReportTableColumn('Link'),
        ReportTableColumn('Action', width: ReportTableWidth.compact),
      ],
      rows: [
        for (final item in filteredItems) _historyRow(item),
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
    final hostLabel = _hostLabel(item.url);
    return tr([
      td([.text(formatFriendlyDateTime(item.generatedAt))]),
      td([.text(item.companyName.isEmpty ? 'Unknown' : item.companyName)]),
      td([.text(item.title.isEmpty ? 'Untitled job' : item.title)]),
      td([
        div(classes: 'history-source-cell', [
          p([.text(_sourceSummary(item))]),
          if (item.savedEvaluationAvailable)
            p(classes: 'history-saved-note', [
              .text(
                'Saved ad-hoc result · ${formatFriendlyDateTime(item.savedEvaluationGeneratedAt)}'
                '${_savedEvaluationScope(item).isEmpty ? '' : ' · ${_savedEvaluationScope(item)}'}',
              ),
            ]),
        ]),
      ]),
      td([
        div(classes: 'history-link-cell', [
          a(
            href: item.url,
            target: Target.blank,
            attributes: {'rel': 'noreferrer noopener', 'title': item.url},
            [.text('Source')],
          ),
          if (hostLabel.isNotEmpty) p(classes: 'history-link-meta', [.text(hostLabel)]),
        ]),
      ]),
      td([
        div(classes: 'history-actions', [
          _ActionIconButton(
            label: 'Reuse URL in the form',
            icon: _ActionIcon.reuse,
            onClick: () => onUseUrl(item.url),
          ),
          if (item.savedEvaluationAvailable)
            _ActionIconButton(
              label: 'Open the latest saved evaluation',
              icon: _ActionIcon.preview,
              onClick: () => onOpenHistoryDetail(item),
            ),
        ]),
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

  String _savedEvaluationScope(EvaluationUrlHistoryItemPayload item) {
    final scope = [
      if (item.savedEvaluationPersona.isNotEmpty) item.savedEvaluationPersona,
      if (item.savedEvaluationCandidateProfile.isNotEmpty) item.savedEvaluationCandidateProfile,
    ].join(' / ');
    return scope;
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
      if (source.rawText.isNotEmpty) ...[
        h3([.text('Original Input')]),
        artifactViewer(
          title: 'Original Input',
          subtitle: source.url.isNotEmpty ? 'Captured from the submitted source' : 'Captured from pasted raw text',
          formatLabel: 'Text',
          content: source.rawText,
          preClasses: 'text-artifact',
        ),
      ],
    ]);
  }
}

class _SavedEvaluationModal extends StatelessComponent {
  const _SavedEvaluationModal({
    required this.item,
    required this.payload,
    required this.error,
    required this.isLoading,
    required this.onClose,
    required this.onUseUrl,
  });

  final EvaluationUrlHistoryItemPayload item;
  final EvaluationResponsePayload? payload;
  final String? error;
  final bool isLoading;
  final void Function() onClose;
  final void Function(String) onUseUrl;

  @override
  Component build(BuildContext context) {
    return div(classes: 'modal-backdrop', [
      div(classes: 'modal-surface', [
        div(classes: 'modal-header', [
          div(classes: 'modal-header-copy', [
            h2(classes: 'modal-title', [.text('Saved Evaluation')]),
            p(
              classes: 'modal-meta',
              [
                .text(
                  item.title.isEmpty
                      ? item.url
                      : '${item.companyName.isEmpty ? 'Unknown' : item.companyName} · ${item.title}',
                ),
              ],
            ),
          ]),
          div(classes: 'modal-actions', [
            button(
              classes: 'button-secondary',
              onClick: () {
                onUseUrl(item.url);
                onClose();
              },
              [.text('Use URL')],
            ),
            button(
              classes: 'button-secondary',
              onClick: onClose,
              [.text('Close')],
            ),
          ]),
        ]),
        div(classes: 'modal-body', [
          if (isLoading)
            p([.text('Loading the latest saved ad-hoc evaluation...')])
          else if (error != null)
            p(classes: 'error', [.text(error!)])
          else if (payload != null) ...[
            p(classes: 'collapsible-summary', [
              .text(
                'Latest saved ad-hoc result for this URL. This lets you inspect the artifact without re-running the engine.',
              ),
            ]),
            _EvaluationPayloadView(
              payload: payload!,
            ),
          ] else
            p([.text('No saved ad-hoc evaluation is available for this URL.')]),
        ]),
      ]),
    ]);
  }
}

class _EvaluationPayloadView extends StatelessComponent {
  const _EvaluationPayloadView({
    required this.payload,
  });

  final EvaluationResponsePayload payload;

  @override
  Component build(BuildContext context) {
    final negativeAspects = _negativeAspects(payload);
    return div(classes: 'context-shell', [
      p([
        .text('Generated at: ${formatFriendlyDateTime(payload.generatedAt)}'),
      ]),
      div(classes: 'summary-grid', [
        _metricCard('Verdict', payload.evaluation.verdict.isEmpty ? 'Unknown' : payload.evaluation.verdict),
        _metricCard('Score', '${payload.evaluation.score}/100'),
        _metricCard('Salary', payload.jobInput.salaryRange.isEmpty ? 'Unknown' : payload.jobInput.salaryRange),
        _metricCard('Remote', payload.jobInput.remotePolicy.isEmpty ? 'Unknown' : payload.jobInput.remotePolicy),
      ]),
      EvaluationHumanReadingSection(
        humanReading: payload.evaluation.humanReading,
        positiveSignals: payload.evaluation.positiveSignals,
        negativeSignals: negativeAspects,
      ),
      _DisclosureSection(
        title: 'Source details',
        description:
            'Inspect the submitted source URL and any captured raw input without expanding the full artifact by default.',
        child: _EvaluateSourceBlock(source: payload.source),
      ),
      _DisclosureSection(
        title: 'Normalized job snapshot',
        description: 'See the normalized job input that the engine actually evaluated.',
        child: artifactViewer(
          title: 'Job Snapshot',
          subtitle: '${payload.jobInput.companyName} · ${payload.jobInput.title}',
          formatLabel: 'Input',
          content: _jobSnapshot(payload),
          preClasses: 'text-artifact',
          showHeader: false,
        ),
      ),
      if (payload.normalizationWarnings.isNotEmpty)
        _DisclosureSection(
          title: 'Normalization notes',
          description: 'Notes recorded while shaping the source into a normalized evaluation input.',
          child: artifactViewer(
            title: 'Normalization Notes',
            subtitle: 'Applied while shaping the artifact into an evaluation input',
            formatLabel: 'Notes',
            content: payload.normalizationWarnings.map((item) => '- $item').join('\n'),
            preClasses: 'text-artifact',
            showHeader: false,
          ),
        ),
    ]);
  }

  Component _metricCard(String label, String value) {
    return div(classes: 'metric', [
      div(classes: 'metric-label', [.text(label)]),
      div(classes: 'metric-value', [.text(value)]),
    ]);
  }

  String _jobSnapshot(EvaluationResponsePayload payload) {
    return [
      'company_name: ${payload.jobInput.companyName}',
      'title: ${payload.jobInput.title}',
      'location: ${payload.jobInput.location}',
      'salary_range: ${payload.jobInput.salaryRange}',
      'remote_policy: ${payload.jobInput.remotePolicy}',
      'description:',
      for (final line in payload.jobInput.description.split(RegExp(r'\r?\n'))) '  $line',
    ].join('\n');
  }

  List<String> _negativeAspects(EvaluationResponsePayload payload) {
    final values = <String>[];
    final seen = <String>{};
    for (final item in [
      ...payload.evaluation.hardRejectReasons,
      ...payload.evaluation.riskSignals,
    ]) {
      if (seen.add(item)) {
        values.add(item);
      }
    }
    return values;
  }
}

class _DisclosureSection extends StatelessComponent {
  const _DisclosureSection({
    required this.title,
    required this.description,
    required this.child,
  });

  final String title;
  final String description;
  final Component child;

  @override
  Component build(BuildContext context) {
    return details(classes: 'disclosure', [
      summary(classes: 'disclosure-summary', [
        div(classes: 'disclosure-summary-row', [
          div(classes: 'disclosure-copy', [
            span(classes: 'disclosure-title', [.text(title)]),
            p(classes: 'disclosure-description', [.text(description)]),
          ]),
          div(classes: 'disclosure-cue', [
            span(classes: 'disclosure-cue-icon', [.text('▾')]),
          ]),
        ]),
      ]),
      div(classes: 'disclosure-body', [
        child,
      ]),
    ]);
  }
}

enum _ActionIcon { reuse, preview }

class _ActionIconButton extends StatelessComponent {
  const _ActionIconButton({
    required this.label,
    required this.icon,
    required this.onClick,
  });

  final String label;
  final _ActionIcon icon;
  final void Function() onClick;

  @override
  Component build(BuildContext context) {
    return button(
      classes: 'button-secondary icon-button',
      attributes: {'title': label, 'aria-label': label},
      onClick: onClick,
      [_ActionIconGlyph(icon: icon)],
    );
  }
}

class _ActionIconGlyph extends StatelessComponent {
  const _ActionIconGlyph({required this.icon});

  final _ActionIcon icon;

  @override
  Component build(BuildContext context) {
    return svg(
      viewBox: '0 0 20 20',
      width: 18.px,
      height: 18.px,
      attributes: const {
        'fill': 'none',
        'stroke': 'currentColor',
        'stroke-width': '1.7',
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'aria-hidden': 'true',
      },
      _paths(),
    );
  }

  List<Component> _paths() {
    return switch (icon) {
      _ActionIcon.reuse => [
        path(d: 'M10 3v8', []),
        path(d: 'M7 8l3 3 3-3', []),
        path(d: 'M4 15h12', []),
      ],
      _ActionIcon.preview => [
        path(d: 'M1.5 10s3-5 8.5-5 8.5 5 8.5 5-3 5-8.5 5-8.5-5-8.5-5Z', []),
        path(d: 'M10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z', []),
      ],
    };
  }
}
