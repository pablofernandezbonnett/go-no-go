package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.decision.RankingStrategy;
import com.pmfb.gonogo.engine.exception.WeeklyDigestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public final class WeeklyDigestGenerator {
    private final OpportunityRanker ranker = new OpportunityRanker();
    private static final String FIELD_PERSONA = "persona";
    private static final String FIELD_CANDIDATE_PROFILE = "candidate_profile";
    private static final String FIELD_GENERATED_AT = "generated_at";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_SOURCE_FILE = "source_file";
    private static final String FIELD_COMPANY = "company";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_VERDICT = "verdict";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_RAW_SCORE = "raw_score";
    private static final String FIELD_RAW_SCORE_RANGE = "raw_score_range";
    private static final String FIELD_LANGUAGE_FRICTION_INDEX = "language_friction_index";
    private static final String FIELD_COMPANY_REPUTATION_INDEX = "company_reputation_index";
    private static final String FIELD_MIN = "min";
    private static final String FIELD_MAX = "max";
    private static final String FIELD_HARD_REJECT_REASONS = "hard_reject_reasons";
    private static final String FIELD_POSITIVE_SIGNALS = "positive_signals";
    private static final String FIELD_RISK_SIGNALS = "risk_signals";
    private static final String FIELD_REASONING = "reasoning";

    private static final String VERDICT_GO = "GO";
    private static final String VERDICT_GO_WITH_CAUTION = "GO_WITH_CAUTION";
    private static final String VERDICT_NO_GO = "NO_GO";

    private static final String UNKNOWN_PERSONA = "unknown_persona";
    private static final String UNKNOWN_VALUE = "unknown";
    private static final String UNKNOWN_COMPANY = "Unknown Company";
    private static final String UNKNOWN_TITLE = "Unknown Title";

    private static final int DEFAULT_ZERO = 0;
    private static final int DEFAULT_INDEX_NEUTRAL = 50;

    public WeeklyDigestData fromBatchReport(String sourcePath, BatchEvaluationReport report) {
        List<WeeklyDigestItem> items = report.items().stream()
                .map(item -> new WeeklyDigestItem(
                        item.sourceFile(),
                        item.job().companyName(),
                        item.job().title(),
                        normalizeVerdict(item.evaluation().verdict().name()),
                        item.evaluation().score(),
                        item.evaluation().rawScore(),
                        item.evaluation().rawScoreMin(),
                        item.evaluation().rawScoreMax(),
                        item.evaluation().languageFrictionIndex(),
                        item.evaluation().companyReputationIndex(),
                        item.evaluation().hardRejectReasons(),
                        item.evaluation().positiveSignals(),
                        item.evaluation().riskSignals(),
                        item.evaluation().reasoning()
                ))
                .toList();

        return new WeeklyDigestData(
                sourcePath,
                report.generatedAt(),
                Instant.now().toString(),
                report.personaId(),
                report.candidateProfileId(),
                items,
                report.errors()
        );
    }

    public WeeklyDigestData fromBatchReportJson(String sourcePath, String jsonContent) {
        List<String> errors = new ArrayList<>();
        Object loaded;
        try {
            loaded = new Yaml().load(jsonContent);
        } catch (YAMLException e) {
            throw new WeeklyDigestException(List.of("Invalid JSON content: " + e.getMessage()));
        }

        if (!(loaded instanceof Map<?, ?> root)) {
            throw new WeeklyDigestException(List.of("Batch report root must be an object."));
        }

        String persona = asString(root.get(FIELD_PERSONA), UNKNOWN_PERSONA);
        String candidateProfile = asString(root.get(FIELD_CANDIDATE_PROFILE), "none");
        String sourceGeneratedAt = asString(root.get(FIELD_GENERATED_AT), UNKNOWN_VALUE);

        List<WeeklyDigestItem> items = parseItems(root.get(FIELD_ITEMS), errors);
        List<BatchEvaluationError> batchErrors = parseErrors(root.get(FIELD_ERRORS), errors);

        if (!errors.isEmpty()) {
            throw new WeeklyDigestException(errors);
        }

        return new WeeklyDigestData(
                sourcePath,
                sourceGeneratedAt,
                Instant.now().toString(),
                persona,
                candidateProfile,
                items,
                batchErrors
        );
    }

    public String toMarkdown(WeeklyDigestData data, int topPerSection) {
        return toMarkdown(data, topPerSection, RankingStrategy.BY_SCORE);
    }

    public String toMarkdown(WeeklyDigestData data, int topPerSection, RankingStrategy strategy) {
        List<WeeklyDigestItem> goItems = filterByVerdict(data.items(), VERDICT_GO, strategy);
        List<WeeklyDigestItem> cautionItems = filterByVerdict(data.items(), VERDICT_GO_WITH_CAUTION, strategy);
        List<WeeklyDigestItem> noGoItems = filterByVerdict(data.items(), VERDICT_NO_GO, strategy);

        StringBuilder sb = new StringBuilder();
        sb.append("# Weekly Digest\n\n");
        sb.append("- generated_at: ").append(data.generatedAt()).append("\n");
        sb.append("- source_report: ").append(data.sourceReport()).append("\n");
        sb.append("- source_generated_at: ").append(data.sourceGeneratedAt()).append("\n");
        sb.append("- persona: ").append(data.persona()).append("\n");
        sb.append("- candidate_profile: ").append(data.candidateProfileId()).append("\n\n");

        sb.append("## Summary\n\n");
        sb.append("- evaluated: ").append(data.items().size()).append("\n");
        sb.append("- parsing_errors: ").append(data.errors().size()).append("\n");
        sb.append("- go: ").append(goItems.size()).append("\n");
        sb.append("- go_with_caution: ").append(cautionItems.size()).append("\n");
        sb.append("- no_go: ").append(noGoItems.size()).append("\n\n");

        sb.append("## Top GO\n\n");
        appendItemsSection(sb, goItems, topPerSection);

        sb.append("## GO With Caution\n\n");
        appendItemsSection(sb, cautionItems, topPerSection);

        sb.append("## NO_GO Highlights\n\n");
        appendItemsSection(sb, noGoItems, topPerSection);

        sb.append("## Aggregated Signals\n\n");
        appendAggregatedSignalSection(sb, FIELD_RISK_SIGNALS, aggregateSignals(data.items(), true));
        appendAggregatedSignalSection(sb, FIELD_HARD_REJECT_REASONS, aggregateHardRejectReasons(data.items()));

        sb.append("## Parsing Errors\n\n");
        if (data.errors().isEmpty()) {
            sb.append("No parsing errors.\n");
        } else {
            for (BatchEvaluationError error : data.errors()) {
                sb.append("### ").append(error.sourceFile()).append("\n\n");
                for (String message : error.errors()) {
                    sb.append("- ").append(message).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private void appendItemsSection(StringBuilder sb, List<WeeklyDigestItem> items, int limit) {
        if (items.isEmpty()) {
            sb.append("No entries.\n\n");
            return;
        }
        int max = Math.max(1, limit);
        for (int i = 0; i < Math.min(items.size(), max); i++) {
            WeeklyDigestItem item = items.get(i);
            sb.append("### ")
                    .append(i + 1)
                    .append(". ")
                    .append(item.company())
                    .append(" - ")
                    .append(item.title())
                    .append("\n\n");
            sb.append("- source_file: ").append(item.sourceFile()).append("\n");
            sb.append("- score: ").append(item.score()).append("/100").append("\n");
            sb.append("- raw_score: ")
                    .append(item.rawScore())
                    .append(" (range ")
                    .append(item.rawScoreMin())
                    .append("..")
                    .append(item.rawScoreMax())
                    .append(")\n");
            sb.append("- language_friction_index: ")
                    .append(item.languageFrictionIndex())
                    .append("/100\n");
            sb.append("- company_reputation_index: ")
                    .append(item.companyReputationIndex())
                    .append("/100\n");
            sb.append("- positive_signals: ").append(formatInlineList(item.positiveSignals())).append("\n");
            sb.append("- risk_signals: ").append(formatInlineList(item.riskSignals())).append("\n");
            sb.append("- hard_reject_reasons: ").append(formatInlineList(item.hardRejectReasons())).append("\n");
            sb.append("- why: ").append(firstReasoning(item.reasoning())).append("\n\n");
        }
    }

    private void appendAggregatedSignalSection(
            StringBuilder sb,
            String title,
            List<Map.Entry<String, Integer>> entries
    ) {
        sb.append("### ").append(title).append("\n\n");
        if (entries.isEmpty()) {
            sb.append("No entries.\n\n");
            return;
        }
        for (Map.Entry<String, Integer> entry : entries) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
    }

    private List<WeeklyDigestItem> filterByVerdict(
            List<WeeklyDigestItem> items,
            String verdict,
            RankingStrategy strategy
    ) {
        return items.stream()
                .filter(item -> verdict.equals(item.verdict()))
                .sorted(ranker.comparatorFor(strategy))
                .toList();
    }

    private List<Map.Entry<String, Integer>> aggregateSignals(
            List<WeeklyDigestItem> items,
            boolean risk
    ) {
        Map<String, Integer> counts = new HashMap<>();
        for (WeeklyDigestItem item : items) {
            List<String> signals = risk ? item.riskSignals() : item.positiveSignals();
            for (String signal : signals) {
                counts.merge(signal, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    private List<Map.Entry<String, Integer>> aggregateHardRejectReasons(List<WeeklyDigestItem> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (WeeklyDigestItem item : items) {
            for (String reason : item.hardRejectReasons()) {
                counts.merge(reason, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    private List<WeeklyDigestItem> parseItems(Object value, List<String> errors) {
        if (!(value instanceof List<?> rawItems)) {
            errors.add("Field 'items' must be a list.");
            return List.of();
        }

        List<WeeklyDigestItem> items = new ArrayList<>();
        for (int i = 0; i < rawItems.size(); i++) {
            Object entry = rawItems.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                errors.add("items[" + i + "] must be an object.");
                continue;
            }

            String sourceFile = asString(map.get(FIELD_SOURCE_FILE), UNKNOWN_VALUE);
            String company = asString(map.get(FIELD_COMPANY), UNKNOWN_COMPANY);
            String title = asString(map.get(FIELD_TITLE), UNKNOWN_TITLE);
            String verdict = normalizeVerdict(asString(map.get(FIELD_VERDICT), VERDICT_NO_GO));
            int score = asInt(map.get(FIELD_SCORE), DEFAULT_ZERO);
            int rawScore = asInt(map.get(FIELD_RAW_SCORE), DEFAULT_ZERO);
            int languageFrictionIndex = asInt(map.get(FIELD_LANGUAGE_FRICTION_INDEX), DEFAULT_ZERO);
            int companyReputationIndex = asInt(
                    map.get(FIELD_COMPANY_REPUTATION_INDEX),
                    DEFAULT_INDEX_NEUTRAL
            );

            int rawMin = DEFAULT_ZERO;
            int rawMax = DEFAULT_ZERO;
            Object rangeObj = map.get(FIELD_RAW_SCORE_RANGE);
            if (rangeObj instanceof Map<?, ?> range) {
                rawMin = asInt(range.get(FIELD_MIN), DEFAULT_ZERO);
                rawMax = asInt(range.get(FIELD_MAX), DEFAULT_ZERO);
            }

            List<String> hardRejectReasons = asStringList(map.get(FIELD_HARD_REJECT_REASONS));
            List<String> positiveSignals = asStringList(map.get(FIELD_POSITIVE_SIGNALS));
            List<String> riskSignals = asStringList(map.get(FIELD_RISK_SIGNALS));
            List<String> reasoning = asStringList(map.get(FIELD_REASONING));

            items.add(new WeeklyDigestItem(
                    sourceFile,
                    company,
                    title,
                    verdict,
                    score,
                    rawScore,
                    rawMin,
                    rawMax,
                    languageFrictionIndex,
                    companyReputationIndex,
                    hardRejectReasons,
                    positiveSignals,
                    riskSignals,
                    reasoning
            ));
        }
        return items;
    }

    private List<BatchEvaluationError> parseErrors(Object value, List<String> errors) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawErrors)) {
            errors.add("Field 'errors' must be a list.");
            return List.of();
        }

        List<BatchEvaluationError> parsed = new ArrayList<>();
        for (int i = 0; i < rawErrors.size(); i++) {
            Object entry = rawErrors.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                errors.add("errors[" + i + "] must be an object.");
                continue;
            }
            String sourceFile = asString(map.get(FIELD_SOURCE_FILE), UNKNOWN_VALUE);
            List<String> details = asStringList(map.get(FIELD_ERRORS));
            parsed.add(new BatchEvaluationError(sourceFile, details));
        }
        return parsed;
    }

    private String normalizeVerdict(String verdict) {
        String normalized = verdict.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case VERDICT_GO, VERDICT_GO_WITH_CAUTION, VERDICT_NO_GO -> normalized;
            default -> VERDICT_NO_GO;
        };
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = String.valueOf(item).trim();
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private String formatInlineList(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }
        return String.join(", ", values);
    }

    private String firstReasoning(List<String> reasoning) {
        if (reasoning.isEmpty()) {
            return "No reasoning available.";
        }
        return reasoning.get(0);
    }
}
