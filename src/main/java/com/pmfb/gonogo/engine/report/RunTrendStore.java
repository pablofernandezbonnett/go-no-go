package com.pmfb.gonogo.engine.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class RunTrendStore {
    private static final String ROOT_FIELD_VERSION = "version";
    private static final String ROOT_FIELD_GENERATED_AT = "generated_at";
    private static final String ROOT_FIELD_SNAPSHOTS = "snapshots";

    private static final String FIELD_GENERATED_AT = "generated_at";
    private static final String FIELD_PERSONA = "persona";
    private static final String FIELD_EVALUATED = "evaluated";
    private static final String FIELD_GO = "go";
    private static final String FIELD_GO_WITH_CAUTION = "go_with_caution";
    private static final String FIELD_NO_GO = "no_go";
    private static final String FIELD_NEW = "new";
    private static final String FIELD_UPDATED = "updated";
    private static final String FIELD_UNCHANGED = "unchanged";
    private static final String FIELD_REMOVED = "removed";
    private static final String FIELD_AVERAGE_SCORE = "average_score";
    private static final String FIELD_AVERAGE_LANGUAGE_FRICTION_INDEX = "average_language_friction_index";
    private static final String FIELD_AVERAGE_COMPANY_REPUTATION_INDEX = "average_company_reputation_index";
    private static final String FIELD_COMPANIES = "companies";
    private static final String FIELD_COMPANY = "company";
    private static final String FIELD_JOBS = "jobs";

    private static final int DEFAULT_ZERO_INT = 0;
    private static final double DEFAULT_ZERO_DOUBLE = 0.0;

    public RunTrendSnapshot loadLatest(Path file) {
        List<RunTrendSnapshot> snapshots = loadAll(file);
        if (snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(snapshots.size() - 1);
    }

    public void append(Path file, RunTrendSnapshot snapshot, int maxRuns) throws IOException {
        List<RunTrendSnapshot> snapshots = new ArrayList<>(loadAll(file));
        snapshots.add(snapshot);
        if (maxRuns > 0 && snapshots.size() > maxRuns) {
            snapshots = new ArrayList<>(snapshots.subList(snapshots.size() - maxRuns, snapshots.size()));
        }
        saveAll(file, snapshots);
    }

    private List<RunTrendSnapshot> loadAll(Path file) {
        if (file == null || !Files.exists(file)) {
            return List.of();
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Object loaded = new Yaml().load(content);
            if (!(loaded instanceof Map<?, ?> root)) {
                return List.of();
            }
            Object snapshotsObj = root.get(ROOT_FIELD_SNAPSHOTS);
            if (!(snapshotsObj instanceof List<?> entries)) {
                return List.of();
            }

            List<RunTrendSnapshot> snapshots = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }

                List<RunTrendCompanyStats> companies = new ArrayList<>();
                Object companiesObj = map.get(FIELD_COMPANIES);
                if (companiesObj instanceof List<?> rawCompanies) {
                    for (Object rawCompany : rawCompanies) {
                        if (!(rawCompany instanceof Map<?, ?> companyMap)) {
                            continue;
                        }
                        companies.add(new RunTrendCompanyStats(
                                asString(companyMap.get(FIELD_COMPANY)),
                                asInt(companyMap.get(FIELD_JOBS), DEFAULT_ZERO_INT),
                                asDouble(companyMap.get(FIELD_AVERAGE_SCORE), DEFAULT_ZERO_DOUBLE),
                                asDouble(
                                        companyMap.get(FIELD_AVERAGE_LANGUAGE_FRICTION_INDEX),
                                        DEFAULT_ZERO_DOUBLE
                                ),
                                asDouble(
                                        companyMap.get(FIELD_AVERAGE_COMPANY_REPUTATION_INDEX),
                                        DEFAULT_ZERO_DOUBLE
                                ),
                                asInt(companyMap.get(FIELD_GO), DEFAULT_ZERO_INT),
                                asInt(companyMap.get(FIELD_GO_WITH_CAUTION), DEFAULT_ZERO_INT),
                                asInt(companyMap.get(FIELD_NO_GO), DEFAULT_ZERO_INT)
                        ));
                    }
                }

                snapshots.add(new RunTrendSnapshot(
                        asString(map.get(FIELD_GENERATED_AT)),
                        asString(map.get(FIELD_PERSONA)),
                        asInt(map.get(FIELD_EVALUATED), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_GO), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_GO_WITH_CAUTION), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_NO_GO), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_NEW), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_UPDATED), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_UNCHANGED), DEFAULT_ZERO_INT),
                        asInt(map.get(FIELD_REMOVED), DEFAULT_ZERO_INT),
                        asDouble(map.get(FIELD_AVERAGE_SCORE), DEFAULT_ZERO_DOUBLE),
                        asDouble(map.get(FIELD_AVERAGE_LANGUAGE_FRICTION_INDEX), DEFAULT_ZERO_DOUBLE),
                        asDouble(map.get(FIELD_AVERAGE_COMPANY_REPUTATION_INDEX), DEFAULT_ZERO_DOUBLE),
                        companies
                ));
            }
            return List.copyOf(snapshots);
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    private void saveAll(Path file, List<RunTrendSnapshot> snapshots) throws IOException {
        if (file == null) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put(ROOT_FIELD_VERSION, 1);
        root.put(ROOT_FIELD_GENERATED_AT, Instant.now().toString());

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RunTrendSnapshot snapshot : snapshots) {
            serialized.add(toMap(snapshot));
        }
        root.put(ROOT_FIELD_SNAPSHOTS, serialized);

        String yaml = new Yaml().dump(root);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    private Map<String, Object> toMap(RunTrendSnapshot snapshot) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(FIELD_GENERATED_AT, snapshot.generatedAt());
        out.put(FIELD_PERSONA, snapshot.persona());
        out.put(FIELD_EVALUATED, snapshot.evaluated());
        out.put(FIELD_GO, snapshot.goCount());
        out.put(FIELD_GO_WITH_CAUTION, snapshot.goWithCautionCount());
        out.put(FIELD_NO_GO, snapshot.noGoCount());
        out.put(FIELD_NEW, snapshot.newCount());
        out.put(FIELD_UPDATED, snapshot.updatedCount());
        out.put(FIELD_UNCHANGED, snapshot.unchangedCount());
        out.put(FIELD_REMOVED, snapshot.removedCount());
        out.put(FIELD_AVERAGE_SCORE, snapshot.averageScore());
        out.put(FIELD_AVERAGE_LANGUAGE_FRICTION_INDEX, snapshot.averageLanguageFrictionIndex());
        out.put(FIELD_AVERAGE_COMPANY_REPUTATION_INDEX, snapshot.averageCompanyReputationIndex());

        List<Map<String, Object>> companies = new ArrayList<>();
        for (RunTrendCompanyStats company : snapshot.companies()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(FIELD_COMPANY, company.company());
            row.put(FIELD_JOBS, company.jobs());
            row.put(FIELD_AVERAGE_SCORE, company.averageScore());
            row.put(FIELD_AVERAGE_LANGUAGE_FRICTION_INDEX, company.averageLanguageFrictionIndex());
            row.put(FIELD_AVERAGE_COMPANY_REPUTATION_INDEX, company.averageCompanyReputationIndex());
            row.put(FIELD_GO, company.goCount());
            row.put(FIELD_GO_WITH_CAUTION, company.goWithCautionCount());
            row.put(FIELD_NO_GO, company.noGoCount());
            companies.add(row);
        }
        out.put(FIELD_COMPANIES, companies);
        return out;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
