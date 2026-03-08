class BatchItemPayload {
  const BatchItemPayload({
    required this.jobId,
    required this.sourceFile,
    required this.company,
    required this.title,
    required this.location,
    required this.salaryRange,
    required this.remotePolicy,
    required this.verdict,
    required this.score,
    required this.languageFrictionIndex,
    required this.companyReputationIndex,
    required this.changeStatus,
    required this.rawScore,
    required this.rawScoreMin,
    required this.rawScoreMax,
    required this.hardRejectReasons,
    required this.positiveSignals,
    required this.riskSignals,
    required this.reasoning,
  });

  final String jobId;
  final String sourceFile;
  final String company;
  final String title;
  final String location;
  final String salaryRange;
  final String remotePolicy;
  final String verdict;
  final int? score;
  final int? languageFrictionIndex;
  final int? companyReputationIndex;
  final String changeStatus;
  final int? rawScore;
  final int? rawScoreMin;
  final int? rawScoreMax;
  final List<String> hardRejectReasons;
  final List<String> positiveSignals;
  final List<String> riskSignals;
  final List<String> reasoning;

  factory BatchItemPayload.fromJson(Map<String, dynamic> json) {
    final jobKey = _asString(json['job_key']);
    final sourceFile = _asString(json['source_file']);
    final fallbackId = sourceFile.isNotEmpty ? sourceFile : '${_asString(json['company'])}:${_asString(json['title'])}';
    final rawScoreRange = _asMap(json['raw_score_range']);

    return BatchItemPayload(
      jobId: jobKey.isNotEmpty ? jobKey : fallbackId,
      sourceFile: sourceFile,
      company: _asString(json['company']),
      title: _asString(json['title']),
      location: _asString(json['location']),
      salaryRange: _asString(json['salary_range']),
      remotePolicy: _asString(json['remote_policy']),
      verdict: _asString(json['verdict']),
      score: _asInt(json['score']),
      languageFrictionIndex: _asInt(json['language_friction_index']),
      companyReputationIndex: _asInt(json['company_reputation_index']),
      changeStatus: _asString(json['change_status']),
      rawScore: _asInt(json['raw_score']),
      rawScoreMin: _asInt(rawScoreRange['min']),
      rawScoreMax: _asInt(rawScoreRange['max']),
      hardRejectReasons: _asStringList(json['hard_reject_reasons']),
      positiveSignals: _asStringList(json['positive_signals']),
      riskSignals: _asStringList(json['risk_signals']),
      reasoning: _asStringList(json['reasoning']),
    );
  }
}

Map<String, dynamic> _asMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    final mapped = <String, dynamic>{};
    for (final entry in value.entries) {
      mapped[entry.key.toString()] = entry.value;
    }
    return mapped;
  }
  return const <String, dynamic>{};
}

String _asString(Object? value) {
  if (value == null) {
    return '';
  }
  return value.toString();
}

List<String> _asStringList(Object? value) {
  if (value is! List) {
    return const <String>[];
  }
  return value.map((entry) => entry.toString()).toList();
}

int? _asInt(Object? value) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value);
  }
  return null;
}
