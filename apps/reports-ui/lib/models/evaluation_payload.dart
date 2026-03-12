class EvaluationOptionItemPayload {
  const EvaluationOptionItemPayload({
    required this.id,
    required this.label,
    required this.description,
  });

  final String id;
  final String label;
  final String description;

  factory EvaluationOptionItemPayload.fromJson(Map<String, dynamic> json) {
    return EvaluationOptionItemPayload(
      id: json['id']?.toString() ?? '',
      label: json['label']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
    );
  }
}

class EvaluationOptionsPayload {
  const EvaluationOptionsPayload({
    required this.personas,
    required this.candidateProfiles,
  });

  final List<EvaluationOptionItemPayload> personas;
  final List<EvaluationOptionItemPayload> candidateProfiles;

  factory EvaluationOptionsPayload.fromJson(Map<String, dynamic> json) {
    final personasRaw = json['personas'];
    final candidateProfilesRaw = json['candidateProfiles'];
    return EvaluationOptionsPayload(
      personas: personasRaw is List
          ? personasRaw.whereType<Map<String, dynamic>>().map(EvaluationOptionItemPayload.fromJson).toList()
          : const [],
      candidateProfiles: candidateProfilesRaw is List
          ? candidateProfilesRaw.whereType<Map<String, dynamic>>().map(EvaluationOptionItemPayload.fromJson).toList()
          : const [],
    );
  }
}

class EvaluationSourcePayload {
  const EvaluationSourcePayload({
    required this.kind,
    required this.url,
    required this.file,
    required this.rawText,
  });

  final String kind;
  final String url;
  final String file;
  final String rawText;

  factory EvaluationSourcePayload.fromJson(Map<String, dynamic> json) {
    return EvaluationSourcePayload(
      kind: json['kind']?.toString() ?? '',
      url: json['url']?.toString() ?? '',
      file: json['file']?.toString() ?? '',
      rawText: json['raw_text']?.toString() ?? '',
    );
  }
}

class EvaluationJobInputPayload {
  const EvaluationJobInputPayload({
    required this.companyName,
    required this.title,
    required this.location,
    required this.salaryRange,
    required this.remotePolicy,
    required this.description,
  });

  final String companyName;
  final String title;
  final String location;
  final String salaryRange;
  final String remotePolicy;
  final String description;

  factory EvaluationJobInputPayload.fromJson(Map<String, dynamic> json) {
    return EvaluationJobInputPayload(
      companyName: json['company_name']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      location: json['location']?.toString() ?? '',
      salaryRange: json['salary_range']?.toString() ?? '',
      remotePolicy: json['remote_policy']?.toString() ?? '',
      description: json['description']?.toString() ?? '',
    );
  }
}

class EvaluationResultPayload {
  const EvaluationResultPayload({
    required this.verdict,
    required this.score,
    required this.rawScore,
    required this.rawScoreMin,
    required this.rawScoreMax,
    required this.languageFrictionIndex,
    required this.companyReputationIndex,
    required this.hardRejectReasons,
    required this.positiveSignals,
    required this.riskSignals,
    required this.reasoning,
  });

  final String verdict;
  final int score;
  final int rawScore;
  final int rawScoreMin;
  final int rawScoreMax;
  final int languageFrictionIndex;
  final int companyReputationIndex;
  final List<String> hardRejectReasons;
  final List<String> positiveSignals;
  final List<String> riskSignals;
  final List<String> reasoning;

  factory EvaluationResultPayload.fromJson(Map<String, dynamic> json) {
    List<String> readList(String key) {
      final raw = json[key];
      if (raw is! List) {
        return const [];
      }
      return raw.map((item) => item.toString()).toList();
    }

    int readInt(String key) {
      final raw = json[key];
      if (raw is num) {
        return raw.toInt();
      }
      return int.tryParse(raw?.toString() ?? '') ?? 0;
    }

    return EvaluationResultPayload(
      verdict: json['verdict']?.toString() ?? '',
      score: readInt('score'),
      rawScore: readInt('raw_score'),
      rawScoreMin: readInt('raw_score_min'),
      rawScoreMax: readInt('raw_score_max'),
      languageFrictionIndex: readInt('language_friction_index'),
      companyReputationIndex: readInt('company_reputation_index'),
      hardRejectReasons: readList('hard_reject_reasons'),
      positiveSignals: readList('positive_signals'),
      riskSignals: readList('risk_signals'),
      reasoning: readList('reasoning'),
    );
  }
}

class EvaluationResponsePayload {
  const EvaluationResponsePayload({
    required this.generatedAt,
    required this.persona,
    required this.candidateProfile,
    required this.source,
    required this.jobInput,
    required this.evaluation,
    required this.normalizationWarnings,
    required this.analysisFile,
  });

  final String generatedAt;
  final String persona;
  final String candidateProfile;
  final EvaluationSourcePayload source;
  final EvaluationJobInputPayload jobInput;
  final EvaluationResultPayload evaluation;
  final List<String> normalizationWarnings;
  final String analysisFile;

  factory EvaluationResponsePayload.fromJson(Map<String, dynamic> json) {
    final sourceRaw = json['source'];
    final jobInputRaw = json['job_input'];
    final evaluationRaw = json['evaluation'];
    final warningsRaw = json['normalization_warnings'];
    return EvaluationResponsePayload(
      generatedAt: json['generated_at']?.toString() ?? '',
      persona: json['persona']?.toString() ?? '',
      candidateProfile: json['candidate_profile']?.toString() ?? '',
      source: sourceRaw is Map<String, dynamic>
          ? EvaluationSourcePayload.fromJson(sourceRaw)
          : const EvaluationSourcePayload(kind: '', url: '', file: '', rawText: ''),
      jobInput: jobInputRaw is Map<String, dynamic>
          ? EvaluationJobInputPayload.fromJson(jobInputRaw)
          : const EvaluationJobInputPayload(
              companyName: '',
              title: '',
              location: '',
              salaryRange: '',
              remotePolicy: '',
              description: '',
            ),
      evaluation: evaluationRaw is Map<String, dynamic>
          ? EvaluationResultPayload.fromJson(evaluationRaw)
          : const EvaluationResultPayload(
              verdict: '',
              score: 0,
              rawScore: 0,
              rawScoreMin: 0,
              rawScoreMax: 0,
              languageFrictionIndex: 0,
              companyReputationIndex: 0,
              hardRejectReasons: [],
              positiveSignals: [],
              riskSignals: [],
              reasoning: [],
            ),
      normalizationWarnings: warningsRaw is List ? warningsRaw.map((item) => item.toString()).toList() : const [],
      analysisFile: json['analysis_file']?.toString() ?? '',
    );
  }
}
