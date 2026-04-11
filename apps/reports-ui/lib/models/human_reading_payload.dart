class HumanReadingPayload {
  const HumanReadingPayload({
    required this.accessFit,
    required this.executionFit,
    required this.domainFit,
    required this.opportunityQuality,
    required this.interviewRoi,
    required this.summary,
    required this.whyStillInteresting,
    required this.whyWasteOfTime,
  });

  final String accessFit;
  final String executionFit;
  final String domainFit;
  final String opportunityQuality;
  final String interviewRoi;
  final String summary;
  final List<String> whyStillInteresting;
  final List<String> whyWasteOfTime;

  static const empty = HumanReadingPayload(
    accessFit: '',
    executionFit: '',
    domainFit: '',
    opportunityQuality: '',
    interviewRoi: '',
    summary: '',
    whyStillInteresting: [],
    whyWasteOfTime: [],
  );

  bool get isEmpty =>
      accessFit.isEmpty &&
      executionFit.isEmpty &&
      domainFit.isEmpty &&
      opportunityQuality.isEmpty &&
      interviewRoi.isEmpty &&
      summary.isEmpty &&
      whyStillInteresting.isEmpty &&
      whyWasteOfTime.isEmpty;

  factory HumanReadingPayload.fromJson(Map<String, dynamic> json) {
    List<String> readList(String key) {
      final raw = json[key];
      if (raw is! List) {
        return const [];
      }
      return raw.map((item) => item.toString()).toList();
    }

    String readString(String key) => json[key]?.toString() ?? '';

    final summary = readString('summary');
    final batchSummary = readString('human_summary');
    return HumanReadingPayload(
      accessFit: readString('access_fit'),
      executionFit: readString('execution_fit'),
      domainFit: readString('domain_fit'),
      opportunityQuality: readString('opportunity_quality'),
      interviewRoi: readString('interview_roi'),
      summary: summary.isNotEmpty ? summary : batchSummary,
      whyStillInteresting: readList('why_still_interesting'),
      whyWasteOfTime: readList('why_waste_of_time'),
    );
  }
}
