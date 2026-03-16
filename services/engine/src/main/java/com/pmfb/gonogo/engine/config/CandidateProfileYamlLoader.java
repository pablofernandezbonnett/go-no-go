package com.pmfb.gonogo.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

final class CandidateProfileYamlLoader {
    private static final String DIRECTORY_NAME = "candidate-profiles";

    private final Path configDirectory;

    CandidateProfileYamlLoader(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    List<CandidateProfileConfig> load(List<String> errors) {
        Path directory = configDirectory.resolve(DIRECTORY_NAME);
        if (!Files.exists(directory)) {
            return List.of();
        }
        if (!Files.isDirectory(directory)) {
            errors.add("Candidate profiles path is not a directory: " + directory);
            return List.of();
        }

        List<Path> files = collectYamlFiles(directory, errors);
        List<CandidateProfileConfig> profiles = new ArrayList<>();
        for (Path file : files) {
            Map<String, Object> root = loadRoot(file, errors);
            if (root == null) {
                continue;
            }
            profiles.add(parseProfile(file, root, errors));
        }
        return profiles;
    }

    private List<Path> collectYamlFiles(Path directory, List<String> errors) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(this::isYamlFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            errors.add("Unable to list candidate profile files in " + directory + ": " + e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRoot(Path path, List<String> errors) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(inputStream);
            if (loaded == null) {
                errors.add("Empty candidate profile YAML file: " + path);
                return null;
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                errors.add("Candidate profile YAML root must be a mapping object: " + path);
                return null;
            }
            return (Map<String, Object>) map;
        } catch (IOException e) {
            errors.add("Unable to read candidate profile file " + path + ": " + e.getMessage());
            return null;
        } catch (YAMLException e) {
            errors.add("Invalid YAML in candidate profile file " + path + ": " + e.getMessage());
            return null;
        }
    }

    private CandidateProfileConfig parseProfile(
            Path file,
            Map<String, Object> root,
            List<String> errors
    ) {
        String context = "candidate-profiles/" + file.getFileName();
        String id = fileBaseName(file);

        Map<String, Object> candidate = readRequiredMap(root, "candidate", context, errors);
        Map<String, Object> stack = readRequiredMap(root, "stack", context, errors);
        Map<String, Object> production = readRequiredMap(stack, "production_proven", context + ".stack", errors);
        Map<String, Object> domainExpertise = readRequiredMap(root, "domain_expertise", context, errors);

        String name = readRequiredString(candidate, "name", context + ".candidate", errors);
        String title = readRequiredString(candidate, "title", context + ".candidate", errors);
        String location = readOptionalString(candidate, "location", context + ".candidate", errors);
        int totalExperienceYears = readRequiredInt(
                candidate,
                "total_experience_years",
                context + ".candidate",
                errors
        );

        List<String> productionSkills = flattenNestedStringLists(production, context + ".stack.production_proven", errors);
        List<String> learningSkills = readOptionalStringList(stack, "actively_learning", context + ".stack", errors);
        List<String> gapSkills = readOptionalStringList(stack, "gaps_honest", context + ".stack", errors);
        List<ProfileDomain> strongDomains = readDomainList(
                domainExpertise,
                "strong",
                ProfileDomainStrength.STRONG,
                context + ".domain_expertise",
                errors
        );
        List<ProfileDomain> moderateDomains = readDomainList(
                domainExpertise,
                "moderate",
                ProfileDomainStrength.MODERATE,
                context + ".domain_expertise",
                errors
        );
        List<ProfileDomain> limitedDomains = readDomainList(
                domainExpertise,
                "limited",
                ProfileDomainStrength.LIMITED,
                context + ".domain_expertise",
                errors
        );
        List<EducationItem> education = readEducationItems(root, "education", context, errors);
        List<String> targetRoleHints = flattenNestedStringLists(
                readOptionalMap(root, "target_roles", context, errors),
                context + ".target_roles",
                errors
        );
        List<String> differentiators = readOptionalStringList(root, "differentiators", context, errors);

        return new CandidateProfileConfig(
                id,
                name,
                title,
                location,
                totalExperienceYears,
                productionSkills,
                learningSkills,
                gapSkills,
                strongDomains,
                moderateDomains,
                limitedDomains,
                education,
                targetRoleHints,
                differentiators,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRequiredMap(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> rawMap)) {
            errors.add(context + " missing map field '" + key + "'");
            return Map.of();
        }
        return (Map<String, Object>) rawMap;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> readOptionalMap(
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
        return (Map<String, Object>) rawMap;
    }

    private int readRequiredInt(
            Map<String, Object> map,
            String key,
            String context,
            List<String> errors
    ) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            errors.add(context + " missing integer field '" + key + "'");
            return 0;
        }
        return number.intValue();
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
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<String> flattenNestedStringLists(
            Map<String, Object> map,
            String context,
            List<String> errors
    ) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object rawValue = entry.getValue();
            String fieldContext = context + "." + entry.getKey();
            if (rawValue instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (!(item instanceof String text)) {
                        errors.add(fieldContext + " item[" + i + "] must be a string");
                        continue;
                    }
                    String trimmed = text.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(trimmed);
                    }
                }
                continue;
            }
            if (rawValue instanceof Map<?, ?> nestedMap) {
                values.addAll(flattenNestedStringLists((Map<String, Object>) nestedMap, fieldContext, errors));
                continue;
            }
            errors.add(fieldContext + " must be a list or nested map");
        }
        return List.copyOf(values);
    }

    private List<ProfileDomain> readDomainList(
            Map<String, Object> map,
            String key,
            ProfileDomainStrength strength,
            String context,
            List<String> errors
    ) {
        List<String> domainIds = readOptionalStringList(map, key, context, errors);
        List<ProfileDomain> domains = new ArrayList<>();
        for (String domainId : domainIds) {
            domains.add(ProfileDomain.of(domainId, strength));
        }
        return List.copyOf(domains);
    }

    @SuppressWarnings("unchecked")
    private List<EducationItem> readEducationItems(
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

        List<EducationItem> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> rawItem)) {
                errors.add(context + " field '" + key + "' item[" + i + "] must be a map");
                continue;
            }
            Map<String, Object> itemMap = (Map<String, Object>) rawItem;
            EducationItem educationItem = new EducationItem(
                    readMapString(itemMap, "degree"),
                    readMapString(itemMap, "institution"),
                    readMapString(itemMap, "note")
            );
            if (!educationItem.isBlank()) {
                items.add(educationItem);
            }
        }
        return List.copyOf(items);
    }

    private String readMapString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String text) {
            return text.trim();
        }
        return "";
    }

    private boolean isYamlFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".example.yaml") || name.endsWith(".example.yml")) {
            return false;
        }
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private String fileBaseName(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }
}
