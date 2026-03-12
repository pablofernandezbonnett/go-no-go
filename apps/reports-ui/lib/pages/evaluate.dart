import 'package:jaspr/dom.dart';
import 'package:jaspr/jaspr.dart';

import '../constants/evaluation_contract.dart';
import '../models/evaluation_payload.dart';
import '../services/evaluation_api.dart';
import 'reports_view_helpers.dart';

@client
class EvaluatePage extends StatefulComponent {
  const EvaluatePage({super.key});

  @override
  State<EvaluatePage> createState() => _EvaluatePageState();
}

class _EvaluatePageState extends State<EvaluatePage> {
  static const _client = EvaluationApiClient();

  EvaluationOptionsPayload? _options;
  EvaluationResponsePayload? _result;
  String? _loadError;
  String? _submitError;
  bool _isLoading = true;
  bool _isSubmitting = false;

  String _inputMode = inputModeRawText;
  String _selectedPersonaId = '';
  String _selectedCandidateProfileId = candidateProfileAuto;
  String _jobUrl = '';
  String _rawText = '';
  String _companyName = '';
  String _title = '';
  String _timeoutSeconds = '20';

  @override
  void initState() {
    super.initState();
    if (kIsWeb) {
      _loadOptions();
    } else {
      _isLoading = false;
    }
  }

  Future<void> _loadOptions() async {
    setState(() {
      _isLoading = true;
      _loadError = null;
    });
    try {
      final options = await _client.fetchOptions();
      setState(() {
        _options = options;
        _selectedPersonaId = options.personas.isEmpty ? '' : options.personas.first.id;
        _isLoading = false;
      });
    } catch (error) {
      setState(() {
        _isLoading = false;
        _loadError = error.toString();
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
        'companyName': _companyName,
        'title': _title,
        'timeoutSeconds': _timeoutSeconds,
      });
      setState(() {
        _result = result;
        _isSubmitting = false;
      });
    } catch (error) {
      setState(() {
        _isSubmitting = false;
        _submitError = error.toString();
      });
    }
  }

  @override
  Component build(BuildContext context) {
    if (!kIsWeb) return pageLoading('Evaluate', 'Loading evaluation tools on the client...');
    if (_isLoading) return pageLoading('Evaluate', 'Loading evaluation options...');
    if (_loadError != null) return pageError('Evaluate', _loadError!, _loadOptions);
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
      companyName: _companyName,
      title: _title,
      timeoutSeconds: _timeoutSeconds,
      submitError: _submitError,
      result: _result,
      isSubmitting: _isSubmitting,
      onInputModeChanged: (value) => setState(() => _inputMode = value),
      onPersonaChanged: (value) => setState(() => _selectedPersonaId = value),
      onCandidateProfileChanged: (value) => setState(() => _selectedCandidateProfileId = value),
      onJobUrlChanged: (value) => setState(() => _jobUrl = value),
      onRawTextChanged: (value) => setState(() => _rawText = value),
      onCompanyNameChanged: (value) => setState(() => _companyName = value),
      onTitleChanged: (value) => setState(() => _title = value),
      onTimeoutChanged: (value) => setState(() => _timeoutSeconds = value),
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
    required this.companyName,
    required this.title,
    required this.timeoutSeconds,
    required this.submitError,
    required this.result,
    required this.isSubmitting,
    required this.onInputModeChanged,
    required this.onPersonaChanged,
    required this.onCandidateProfileChanged,
    required this.onJobUrlChanged,
    required this.onRawTextChanged,
    required this.onCompanyNameChanged,
    required this.onTitleChanged,
    required this.onTimeoutChanged,
    required this.onSubmit,
  });

  final EvaluationOptionsPayload options;
  final String inputMode;
  final String selectedPersonaId;
  final String selectedCandidateProfileId;
  final String jobUrl;
  final String rawText;
  final String companyName;
  final String title;
  final String timeoutSeconds;
  final String? submitError;
  final EvaluationResponsePayload? result;
  final bool isSubmitting;
  final void Function(String) onInputModeChanged;
  final void Function(String) onPersonaChanged;
  final void Function(String) onCandidateProfileChanged;
  final void Function(String) onJobUrlChanged;
  final void Function(String) onRawTextChanged;
  final void Function(String) onCompanyNameChanged;
  final void Function(String) onTitleChanged;
  final void Function(String) onTimeoutChanged;
  final void Function() onSubmit;

  @override
  Component build(BuildContext context) {
    return section(classes: 'page', [
      h1([.text('Evaluate')]),
      card([
        p([.text('Paste a job post or submit a URL for ad-hoc evaluation against the engine runtime.')]),
        _EvaluateForm(
          options: options,
          inputMode: inputMode,
          selectedPersonaId: selectedPersonaId,
          selectedCandidateProfileId: selectedCandidateProfileId,
          jobUrl: jobUrl,
          rawText: rawText,
          companyName: companyName,
          title: title,
          timeoutSeconds: timeoutSeconds,
          isSubmitting: isSubmitting,
          onInputModeChanged: onInputModeChanged,
          onPersonaChanged: onPersonaChanged,
          onCandidateProfileChanged: onCandidateProfileChanged,
          onJobUrlChanged: onJobUrlChanged,
          onRawTextChanged: onRawTextChanged,
          onCompanyNameChanged: onCompanyNameChanged,
          onTitleChanged: onTitleChanged,
          onTimeoutChanged: onTimeoutChanged,
          onSubmit: onSubmit,
        ),
        if (submitError != null) p(classes: 'error', [.text(submitError!)]),
      ]),
      if (result != null) _EvaluateResultCard(result: result!),
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
    required this.companyName,
    required this.title,
    required this.timeoutSeconds,
    required this.isSubmitting,
    required this.onInputModeChanged,
    required this.onPersonaChanged,
    required this.onCandidateProfileChanged,
    required this.onJobUrlChanged,
    required this.onRawTextChanged,
    required this.onCompanyNameChanged,
    required this.onTitleChanged,
    required this.onTimeoutChanged,
    required this.onSubmit,
  });

  final EvaluationOptionsPayload options;
  final String inputMode;
  final String selectedPersonaId;
  final String selectedCandidateProfileId;
  final String jobUrl;
  final String rawText;
  final String companyName;
  final String title;
  final String timeoutSeconds;
  final bool isSubmitting;
  final void Function(String) onInputModeChanged;
  final void Function(String) onPersonaChanged;
  final void Function(String) onCandidateProfileChanged;
  final void Function(String) onJobUrlChanged;
  final void Function(String) onRawTextChanged;
  final void Function(String) onCompanyNameChanged;
  final void Function(String) onTitleChanged;
  final void Function(String) onTimeoutChanged;
  final void Function() onSubmit;

  @override
  Component build(BuildContext context) {
    return div(classes: 'controls form-grid', [
      ..._selectorFields(),
      ..._overrideFields(),
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
        options: [for (final persona in options.personas) (persona.id, persona.label)],
        onChanged: onPersonaChanged,
      ),
      _selectField(
        labelText: 'Candidate profile',
        value: selectedCandidateProfileId,
        options: [
          const (candidateProfileAuto, 'Auto'),
          const (candidateProfileNone, 'None'),
          for (final profile in options.candidateProfiles) (profile.id, profile.label),
        ],
        onChanged: onCandidateProfileChanged,
      ),
    ];
  }

  List<Component> _overrideFields() {
    return [
      _textField(
        labelText: 'Company override',
        value: companyName,
        placeholder: 'Optional company name override',
        onChanged: onCompanyNameChanged,
      ),
      _textField(
        labelText: 'Title override',
        value: title,
        placeholder: 'Optional title override',
        onChanged: onTitleChanged,
      ),
      _textField(
        labelText: 'Timeout seconds',
        value: timeoutSeconds,
        placeholder: '20',
        onChanged: onTimeoutChanged,
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

class _EvaluateResultCard extends StatelessComponent {
  const _EvaluateResultCard({required this.result});

  final EvaluationResponsePayload result;

  @override
  Component build(BuildContext context) {
    return card([
      h2([.text('Result')]),
      p([
        .text('Artifact: '),
        code([.text(result.analysisFile.isEmpty ? 'not written' : result.analysisFile)]),
      ]),
      p([.text('Generated at: ${result.generatedAt}')]),
      _EvaluateSourceBlock(source: result.source),
      pre([.text(_consoleOutput(result))]),
    ]);
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
      if (payload.source.file.isNotEmpty) 'source_file: ${payload.source.file}',
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
