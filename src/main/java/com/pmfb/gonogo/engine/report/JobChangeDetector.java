package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.job.JobInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

public final class JobChangeDetector {
    private static final String ROOT_FIELD_VERSION = "version";
    private static final String ROOT_FIELD_GENERATED_AT = "generated_at";
    private static final String ROOT_FIELD_JOBS = "jobs";

    private static final String ENTRY_FIELD_SOURCE_FILE = "source_file";
    private static final String ENTRY_FIELD_COMPANY = "company";
    private static final String ENTRY_FIELD_TITLE = "title";
    private static final String ENTRY_FIELD_LOCATION = "location";
    private static final String ENTRY_FIELD_FINGERPRINT = "fingerprint";

    private static final String BASE_KEY_SOURCE_URL_PREFIX = "source_url:";
    private static final String BASE_KEY_ROLE_PREFIX = "role:";
    private static final String KEY_DUPLICATE_SUFFIX_SEPARATOR = "#";
    private static final String HASH_ALGORITHM_SHA256 = "SHA-256";
    private static final String DESCRIPTION_FETCHED_AT_PREFIX = "fetched at:";

    private static final Pattern SOURCE_URL_PATTERN = Pattern.compile(
            "(?im)^\\s*source\\s+url\\s*:\\s*(\\S+)\\s*$"
    );
    private static final String STATUS_NEW = ChangeStatuses.NEW;
    private static final String STATUS_UPDATED = ChangeStatuses.UPDATED;
    private static final String STATUS_UNCHANGED = ChangeStatuses.UNCHANGED;

    public ClassificationResult classifyAndPersist(List<CandidateJob> candidates, Path stateFile) throws IOException {
        LoadedState loadedState = loadState(stateFile);
        Map<String, SnapshotEntry> previous = loadedState.entries();
        Map<String, SnapshotEntry> current = new LinkedHashMap<>();

        Map<String, Integer> keyCounters = new HashMap<>();
        List<ClassifiedJob> jobs = new ArrayList<>();
        List<RemovedJobItem> removedItems = new ArrayList<>();

        int newCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (CandidateJob candidate : candidates) {
            String baseKey = buildBaseKey(candidate.job());
            String jobKey = uniqueKey(baseKey, keyCounters);
            String fingerprint = buildFingerprint(candidate.job());

            String status = STATUS_NEW;
            SnapshotEntry previousEntry = previous.get(jobKey);
            if (previousEntry != null) {
                status = fingerprint.equals(previousEntry.fingerprint())
                        ? STATUS_UNCHANGED
                        : STATUS_UPDATED;
            }

            if (STATUS_NEW.equals(status)) {
                newCount++;
            } else if (STATUS_UPDATED.equals(status)) {
                updatedCount++;
            } else {
                unchangedCount++;
            }

            jobs.add(new ClassifiedJob(
                    candidate.sourceFile(),
                    candidate.job(),
                    jobKey,
                    fingerprint,
                    status
            ));
            current.put(jobKey, SnapshotEntry.fromCandidate(jobKey, candidate.sourceFile(), candidate.job(), fingerprint));
        }

        for (Map.Entry<String, SnapshotEntry> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                SnapshotEntry previousEntry = entry.getValue();
                removedItems.add(new RemovedJobItem(
                        previousEntry.jobKey(),
                        previousEntry.sourceFile(),
                        previousEntry.company(),
                        previousEntry.title(),
                        previousEntry.location()
                ));
            }
        }

        removedItems.sort(Comparator.comparing(RemovedJobItem::jobKey));
        saveState(stateFile, current);

        return new ClassificationResult(
                List.copyOf(jobs),
                List.copyOf(removedItems),
                newCount,
                updatedCount,
                unchangedCount,
                removedItems.size(),
                loadedState.warnings()
        );
    }

    public ChangeDetectionResult detectAndPersist(List<BatchEvaluationItem> inputItems, Path stateFile) throws IOException {
        List<CandidateJob> candidates = inputItems.stream()
                .map(item -> new CandidateJob(item.sourceFile(), item.job()))
                .toList();
        ClassificationResult classified = classifyAndPersist(candidates, stateFile);
        List<BatchEvaluationItem> items = new ArrayList<>();
        for (int i = 0; i < inputItems.size(); i++) {
            BatchEvaluationItem item = inputItems.get(i);
            ClassifiedJob classifiedJob = classified.jobs().get(i);
            items.add(new BatchEvaluationItem(
                    item.sourceFile(),
                    item.job(),
                    item.evaluation(),
                    classifiedJob.jobKey(),
                    classifiedJob.fingerprint(),
                    classifiedJob.changeStatus()
            ));
        }

        return new ChangeDetectionResult(
                List.copyOf(items),
                classified.removedItems(),
                classified.newCount(),
                classified.updatedCount(),
                classified.unchangedCount(),
                classified.removedCount(),
                classified.warnings()
        );
    }

    private LoadedState loadState(Path stateFile) {
        if (stateFile == null || !Files.exists(stateFile)) {
            return new LoadedState(Map.of(), List.of());
        }

        List<String> warnings = new ArrayList<>();
        try {
            String content = Files.readString(stateFile, StandardCharsets.UTF_8);
            Object loaded = new Yaml().load(content);
            if (!(loaded instanceof Map<?, ?> root)) {
                warnings.add("Change state file is invalid; starting from empty state.");
                return new LoadedState(Map.of(), List.copyOf(warnings));
            }

            Object jobsObject = root.get(ROOT_FIELD_JOBS);
            if (!(jobsObject instanceof Map<?, ?> rawJobs)) {
                return new LoadedState(Map.of(), List.copyOf(warnings));
            }

            Map<String, SnapshotEntry> entries = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawJobs.entrySet()) {
                String key = asString(entry.getKey());
                if (key.isBlank()) {
                    continue;
                }
                if (!(entry.getValue() instanceof Map<?, ?> rawEntry)) {
                    continue;
                }
                String sourceFile = asString(rawEntry.get(ENTRY_FIELD_SOURCE_FILE));
                String company = asString(rawEntry.get(ENTRY_FIELD_COMPANY));
                String title = asString(rawEntry.get(ENTRY_FIELD_TITLE));
                String location = asString(rawEntry.get(ENTRY_FIELD_LOCATION));
                String fingerprint = asString(rawEntry.get(ENTRY_FIELD_FINGERPRINT));
                entries.put(
                        key,
                        new SnapshotEntry(key, sourceFile, company, title, location, fingerprint)
                );
            }
            return new LoadedState(entries, List.copyOf(warnings));
        } catch (IOException | RuntimeException e) {
            warnings.add("Failed to read change state file; starting from empty state.");
            return new LoadedState(Map.of(), List.copyOf(warnings));
        }
    }

    private void saveState(Path stateFile, Map<String, SnapshotEntry> entries) throws IOException {
        if (stateFile == null) {
            return;
        }
        Path parent = stateFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put(ROOT_FIELD_VERSION, 1);
        root.put(ROOT_FIELD_GENERATED_AT, Instant.now().toString());

        Map<String, Object> jobs = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> jobs.put(entry.getKey(), toYamlMap(entry.getValue())));
        root.put(ROOT_FIELD_JOBS, jobs);

        String yaml = new Yaml().dump(root);
        Files.writeString(stateFile, yaml, StandardCharsets.UTF_8);
    }

    private Map<String, Object> toYamlMap(SnapshotEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(ENTRY_FIELD_SOURCE_FILE, entry.sourceFile());
        out.put(ENTRY_FIELD_COMPANY, entry.company());
        out.put(ENTRY_FIELD_TITLE, entry.title());
        out.put(ENTRY_FIELD_LOCATION, entry.location());
        out.put(ENTRY_FIELD_FINGERPRINT, entry.fingerprint());
        return out;
    }

    private String buildBaseKey(JobInput job) {
        String sourceUrl = extractSourceUrl(job.description());
        if (!sourceUrl.isBlank()) {
            return BASE_KEY_SOURCE_URL_PREFIX + normalize(sourceUrl);
        }
        return BASE_KEY_ROLE_PREFIX
                + normalize(job.companyName()) + "|"
                + normalize(job.title()) + "|"
                + normalize(job.location());
    }

    private String uniqueKey(String baseKey, Map<String, Integer> counters) {
        int index = counters.merge(baseKey, 1, Integer::sum);
        if (index == 1) {
            return baseKey;
        }
        return baseKey + KEY_DUPLICATE_SUFFIX_SEPARATOR + index;
    }

    private String extractSourceUrl(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        Matcher matcher = SOURCE_URL_PATTERN.matcher(description);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private String buildFingerprint(JobInput job) {
        String data = normalize(job.companyName()) + "\n"
                + normalize(job.title()) + "\n"
                + normalize(job.location()) + "\n"
                + normalize(job.salaryRange()) + "\n"
                + normalize(job.remotePolicy()) + "\n"
                + normalizeDescription(job.description());
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM_SHA256);
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM_SHA256 + " not available", e);
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = description.split("\\R");
        for (String line : lines) {
            String lowered = line.trim().toLowerCase(Locale.ROOT);
            if (lowered.startsWith(DESCRIPTION_FETCHED_AT_PREFIX)) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return normalize(sb.toString());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String asString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    public record ChangeDetectionResult(
            List<BatchEvaluationItem> items,
            List<RemovedJobItem> removedItems,
            int newCount,
            int updatedCount,
            int unchangedCount,
            int removedCount,
            List<String> warnings
    ) {
    }

    public record ClassificationResult(
            List<ClassifiedJob> jobs,
            List<RemovedJobItem> removedItems,
            int newCount,
            int updatedCount,
            int unchangedCount,
            int removedCount,
            List<String> warnings
    ) {
    }

    public record CandidateJob(
            String sourceFile,
            JobInput job
    ) {
    }

    public record ClassifiedJob(
            String sourceFile,
            JobInput job,
            String jobKey,
            String fingerprint,
            String changeStatus
    ) {
    }

    private record LoadedState(
            Map<String, SnapshotEntry> entries,
            List<String> warnings
    ) {
    }

    private record SnapshotEntry(
            String jobKey,
            String sourceFile,
            String company,
            String title,
            String location,
            String fingerprint
    ) {
        static SnapshotEntry fromCandidate(String jobKey, String sourceFile, JobInput job, String fingerprint) {
            return new SnapshotEntry(
                    jobKey,
                    sourceFile,
                    job.companyName(),
                    job.title(),
                    job.location(),
                    fingerprint
            );
        }
    }
}
