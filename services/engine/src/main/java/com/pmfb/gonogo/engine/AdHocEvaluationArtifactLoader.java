package com.pmfb.gonogo.engine;

import com.pmfb.gonogo.engine.exception.AdHocEvaluationArtifactLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

final class AdHocEvaluationArtifactLoader {
    AdHocEvaluationArtifact load(Path artifactFile) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> root = loadRoot(artifactFile, errors);
        if (!errors.isEmpty()) {
            throw new AdHocEvaluationArtifactLoadException(errors);
        }

        String personaId = readRequiredString(root, EvaluateInputFieldKeys.PERSONA, artifactFile, errors);
        String candidateProfileId = readOptionalString(root, EvaluateInputFieldKeys.CANDIDATE_PROFILE);
        Map<String, Object> source = readRequiredMap(root, EvaluateInputFieldKeys.SOURCE, artifactFile, errors);
        String sourceKind = readRequiredString(source, EvaluateInputFieldKeys.KIND, artifactFile, errors);
        String sourceUrl = readOptionalString(source, EvaluateInputFieldKeys.URL);
        String sourceRawText = readOptionalString(source, EvaluateInputFieldKeys.RAW_TEXT);

        if (sourceUrl.isBlank() && sourceRawText.isBlank()) {
            errors.add(
                    "Ad-hoc artifact must contain either source.url or source.raw_text in " + artifactFile
            );
        }
        if (!errors.isEmpty()) {
            throw new AdHocEvaluationArtifactLoadException(errors);
        }

        return new AdHocEvaluationArtifact(
                personaId,
                candidateProfileId,
                new AdHocEvaluationArtifact.Source(sourceKind, sourceUrl, sourceRawText)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRoot(Path artifactFile, List<String> errors) {
        if (!Files.exists(artifactFile)) {
            errors.add("Ad-hoc artifact does not exist: " + artifactFile);
            return Map.of();
        }
        if (!Files.isRegularFile(artifactFile)) {
            errors.add("Ad-hoc artifact path is not a regular file: " + artifactFile);
            return Map.of();
        }

        try (InputStream inputStream = Files.newInputStream(artifactFile)) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> map)) {
                errors.add("Ad-hoc artifact YAML root must be a mapping object: " + artifactFile);
                return Map.of();
            }
            return (Map<String, Object>) map;
        } catch (IOException e) {
            errors.add("Unable to read ad-hoc artifact " + artifactFile + ": " + e.getMessage());
            return Map.of();
        } catch (YAMLException e) {
            errors.add("Invalid YAML in ad-hoc artifact " + artifactFile + ": " + e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequiredMap(
            Map<String, Object> map,
            String key,
            Path artifactFile,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> nested)) {
            errors.add("Missing mapping field '" + key + "' in " + artifactFile);
            return Map.of();
        }
        return (Map<String, Object>) nested;
    }

    private String readRequiredString(
            Map<String, Object> map,
            String key,
            Path artifactFile,
            List<String> errors
    ) {
        String value = readOptionalString(map, key);
        if (value.isBlank()) {
            errors.add("Missing string field '" + key + "' in " + artifactFile);
        }
        return value;
    }

    private String readOptionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            return "";
        }
        return text.trim();
    }
}
