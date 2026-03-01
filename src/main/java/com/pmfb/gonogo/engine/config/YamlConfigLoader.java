package com.pmfb.gonogo.engine.config;

import com.pmfb.gonogo.engine.decision.RankingStrategy;
import com.pmfb.gonogo.engine.decision.SignalRegistry;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public final class YamlConfigLoader {
    private final Path configDirectory;

    public YamlConfigLoader(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    public EngineConfig load() {
        List<String> errors = new ArrayList<>();
        ensureConfigDirectory(errors);

        List<CompanyConfig> companies = loadCompanies(errors);
        List<PersonaConfig> personas = loadPersonas(errors);
        List<BlacklistedCompanyConfig> blacklistedCompanies = loadBlacklistedCompanies(errors);

        if (!errors.isEmpty()) {
            throw new ConfigLoadException(errors);
        }
        return new EngineConfig(companies, personas, blacklistedCompanies);
    }

    private void ensureConfigDirectory(List<String> errors) {
        if (!Files.exists(configDirectory)) {
            errors.add("Config directory does not exist: " + configDirectory);
            return;
        }
        if (!Files.isDirectory(configDirectory)) {
            errors.add("Config path is not a directory: " + configDirectory);
        }
    }

    private List<CompanyConfig> loadCompanies(List<String> errors) {
        String fileName = "companies.yaml";
        Map<String, Object> root = loadRoot(configDirectory.resolve(fileName), errors);
        if (root == null) {
            return List.of();
        }
        List<Map<String, Object>> items = readListOfMaps(root, "companies", fileName, errors);
        List<CompanyConfig> companies = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String context = fileName + " companies[" + i + "]";
            Map<String, Object> item = items.get(i);
            String id = readRequiredString(item, "id", context, errors);
            String name = readRequiredString(item, "name", context, errors);
            String careerUrl = readRequiredString(item, "career_url", context, errors);
            String corporateUrl = readOptionalString(item, "corporate_url", context, errors);
            String typeHint = readRequiredString(item, "type_hint", context, errors);
            String region = readRequiredString(item, "region", context, errors);
            String notes = readOptionalString(item, "notes", context, errors);
            List<String> profileTags = readOptionalStringList(item, "profile_tags", context, errors);
            List<String> riskTags = readOptionalStringList(item, "risk_tags", context, errors);
            companies.add(new CompanyConfig(
                    id,
                    name,
                    careerUrl,
                    corporateUrl,
                    typeHint,
                    region,
                    notes,
                    profileTags,
                    riskTags
            ));
        }
        return companies;
    }

    private List<PersonaConfig> loadPersonas(List<String> errors) {
        String fileName = "personas.yaml";
        Map<String, Object> root = loadRoot(configDirectory.resolve(fileName), errors);
        if (root == null) {
            return List.of();
        }
        List<Map<String, Object>> items = readListOfMaps(root, "personas", fileName, errors);
        List<PersonaConfig> personas = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String context = fileName + " personas[" + i + "]";
            Map<String, Object> item = items.get(i);
            String id = readRequiredString(item, "id", context, errors);
            String description = readRequiredString(item, "description", context, errors);
            List<String> priorities = readStringList(item, "priorities", context, errors);
            List<String> hardNo = readStringList(item, "hard_no", context, errors);
            List<String> acceptableIf = readStringList(item, "acceptable_if", context, errors);
            Map<String, Integer> signalWeights = readOptionalStringIntMap(item, "signal_weights", context, errors);
            RankingStrategy rankingStrategy = readOptionalRankingStrategy(item, "ranking_strategy", context, errors);
            personas.add(new PersonaConfig(id, description, priorities, hardNo, acceptableIf, signalWeights, rankingStrategy));
        }
        return personas;
    }

    private List<BlacklistedCompanyConfig> loadBlacklistedCompanies(List<String> errors) {
        String fileName = "blacklist.yaml";
        Map<String, Object> root = loadRoot(configDirectory.resolve(fileName), errors);
        if (root == null) {
            return List.of();
        }
        List<Map<String, Object>> items =
                readListOfMaps(root, "blacklisted_companies", fileName, errors);
        List<BlacklistedCompanyConfig> companies = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String context = fileName + " blacklisted_companies[" + i + "]";
            Map<String, Object> item = items.get(i);
            String name = readRequiredString(item, "name", context, errors);
            String reason = readRequiredString(item, "reason", context, errors);
            companies.add(new BlacklistedCompanyConfig(name, reason));
        }
        return companies;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRoot(Path path, List<String> errors) {
        if (!Files.exists(path)) {
            errors.add("Missing config file: " + path);
            return null;
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(inputStream);
            if (loaded == null) {
                errors.add("Empty YAML file: " + path);
                return null;
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                errors.add("YAML root must be a mapping object: " + path);
                return null;
            }
            return (Map<String, Object>) map;
        } catch (IOException e) {
            errors.add("Unable to read file " + path + ": " + e.getMessage());
            return null;
        } catch (YAMLException e) {
            errors.add("Invalid YAML in " + path + ": " + e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> readListOfMaps(
            Map<String, Object> root,
            String key,
            String fileName,
            List<String> errors
    ) {
        Object value = root.get(key);
        if (!(value instanceof List<?> list)) {
            errors.add(fileName + " must contain a list for key '" + key + "'");
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> mapItem)) {
                errors.add(fileName + " key '" + key + "' item[" + i + "] must be an object");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedItem = (Map<String, Object>) mapItem;
            result.add(typedItem);
        }
        return result;
    }

    private String readRequiredString(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            errors.add(context + " missing string field '" + key + "'");
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            errors.add(context + " field '" + key + "' cannot be blank");
        }
        return trimmed;
    }

    private String readOptionalString(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            errors.add(context + " field '" + key + "' must be a string");
            return "";
        }
        return text.trim();
    }

    private List<String> readStringList(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            errors.add(context + " missing list field '" + key + "'");
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String text)) {
                errors.add(context + " field '" + key + "' item[" + i + "] must be a string");
                continue;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                errors.add(context + " field '" + key + "' item[" + i + "] cannot be blank");
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private List<String> readOptionalStringList(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            errors.add(context + " field '" + key + "' must be a list");
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof String text)) {
                errors.add(context + " field '" + key + "' item[" + i + "] must be a string");
                continue;
            }
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                errors.add(context + " field '" + key + "' item[" + i + "] cannot be blank");
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readOptionalStringIntMap(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            errors.add(context + " field '" + key + "' must be a map");
            return Map.of();
        }
        Map<String, Object> typedMap = (Map<String, Object>) rawMap;
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : typedMap.entrySet()) {
            String signal = entry.getKey();
            Object rawVal = entry.getValue();
            if (!(rawVal instanceof Number number)) {
                errors.add(context + " field '" + key + "." + signal + "' must be an integer");
                continue;
            }
            int intVal = number.intValue();
            if (intVal < 0) {
                errors.add(context + " field '" + key + "." + signal + "' must be non-negative");
                continue;
            }
            result.put(signal, intVal);
        }
        Map<String, Integer> immutable = Map.copyOf(result);
        SignalRegistry.validateWeightKeys(immutable, context + " signal_weights", errors);
        return immutable;
    }

    private RankingStrategy readOptionalRankingStrategy(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (value == null) {
            return RankingStrategy.BY_SCORE;
        }
        if (!(value instanceof String text)) {
            errors.add(context + " field '" + key + "' must be a string");
            return RankingStrategy.BY_SCORE;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return RankingStrategy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            errors.add(context + " field '" + key + "' has unknown value '" + text.trim() + "' — defaulting to BY_SCORE");
            return RankingStrategy.BY_SCORE;
        }
    }
}
