package com.pmfb.gonogo.engine.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record PersonaIndex(
        Set<String> normalizedPriorities,
        Set<String> normalizedHardNo,
        Set<String> normalizedAcceptableIf,
        Map<String, Integer> normalizedSignalWeights
) {
    public PersonaIndex {
        normalizedPriorities = normalizedPriorities == null ? Set.of() : Set.copyOf(normalizedPriorities);
        normalizedHardNo = normalizedHardNo == null ? Set.of() : Set.copyOf(normalizedHardNo);
        normalizedAcceptableIf = normalizedAcceptableIf == null ? Set.of() : Set.copyOf(normalizedAcceptableIf);
        normalizedSignalWeights = normalizedSignalWeights == null ? Map.of() : Map.copyOf(normalizedSignalWeights);
    }

    public static PersonaIndex from(
            List<String> priorities,
            List<String> hardNo,
            List<String> acceptableIf,
            Map<String, Integer> signalWeights
    ) {
        return new PersonaIndex(
                normalizeSet(priorities),
                normalizeSet(hardNo),
                normalizeSet(acceptableIf),
                normalizeWeightKeys(signalWeights)
        );
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String item = normalize(value);
            if (!item.isBlank()) {
                normalized.add(item);
            }
        }
        return Set.copyOf(normalized);
    }

    private static Map<String, Integer> normalizeWeightKeys(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                normalized.put(key, entry.getValue());
            }
        }
        return Map.copyOf(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
