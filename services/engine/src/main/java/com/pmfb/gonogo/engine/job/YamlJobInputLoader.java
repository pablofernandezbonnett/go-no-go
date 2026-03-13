package com.pmfb.gonogo.engine.job;

import com.pmfb.gonogo.engine.exception.JobInputLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public final class YamlJobInputLoader {
    private final Path jobFile;

    public YamlJobInputLoader(Path jobFile) {
        this.jobFile = jobFile;
    }

    public JobInput load() {
        List<String> errors = new ArrayList<>();
        Map<String, Object> root = loadRoot(errors);
        if (!errors.isEmpty()) {
            throw new JobInputLoadException(errors);
        }

        String companyName = readRequiredString(root, JobInputFieldKeys.COMPANY_NAME, errors);
        String title = readRequiredString(root, JobInputFieldKeys.TITLE, errors);
        String location = readRequiredString(root, JobInputFieldKeys.LOCATION, errors);
        String salaryRange = readRequiredString(root, JobInputFieldKeys.SALARY_RANGE, errors);
        String remotePolicy = readRequiredString(root, JobInputFieldKeys.REMOTE_POLICY, errors);
        String description = readRequiredString(root, JobInputFieldKeys.DESCRIPTION, errors);

        if (!errors.isEmpty()) {
            throw new JobInputLoadException(errors);
        }
        return new JobInput(companyName, title, location, salaryRange, remotePolicy, description);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRoot(List<String> errors) {
        if (!Files.exists(jobFile)) {
            errors.add("Job file does not exist: " + jobFile);
            return Map.of();
        }
        if (!Files.isRegularFile(jobFile)) {
            errors.add("Job file path is not a regular file: " + jobFile);
            return Map.of();
        }

        try (InputStream inputStream = Files.newInputStream(jobFile)) {
            Object loaded = new Yaml().load(inputStream);
            if (!(loaded instanceof Map<?, ?> map)) {
                errors.add("Job YAML root must be a mapping object: " + jobFile);
                return Map.of();
            }
            Object jobSection = map.get(JobInputFieldKeys.JOB);
            if (jobSection instanceof Map<?, ?> jobMap) {
                return (Map<String, Object>) jobMap;
            }
            return (Map<String, Object>) map;
        } catch (IOException e) {
            errors.add("Unable to read job file " + jobFile + ": " + e.getMessage());
            return Map.of();
        } catch (YAMLException e) {
            errors.add("Invalid YAML in job file " + jobFile + ": " + e.getMessage());
            return Map.of();
        }
    }

    private String readRequiredString(Map<String, Object> map, String key, List<String> errors) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            errors.add("Missing string field '" + key + "' in " + jobFile);
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            errors.add("Field '" + key + "' cannot be blank in " + jobFile);
        }
        return trimmed;
    }
}
