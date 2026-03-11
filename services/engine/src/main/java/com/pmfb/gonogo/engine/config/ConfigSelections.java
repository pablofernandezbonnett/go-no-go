package com.pmfb.gonogo.engine.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ConfigSelections {
    public static final String CANDIDATE_PROFILE_NONE = "none";

    private ConfigSelections() {
    }

    public static Optional<PersonaConfig> findPersona(List<PersonaConfig> personas, String id) {
        String normalized = normalize(id);
        return personas.stream()
                .filter(persona -> normalize(persona.id()).equals(normalized))
                .findFirst();
    }

    public static CandidateProfileResolution resolveCandidateProfile(
            List<CandidateProfileConfig> profiles,
            String requestedId
    ) {
        String normalizedRequested = normalize(requestedId);
        if (normalizedRequested.isBlank()) {
            if (profiles.size() == 1) {
                return new CandidateProfileResolution(Optional.of(profiles.get(0)), true, "");
            }
            return new CandidateProfileResolution(Optional.empty(), false, "");
        }
        if (CANDIDATE_PROFILE_NONE.equals(normalizedRequested)) {
            return new CandidateProfileResolution(Optional.empty(), false, "");
        }

        return profiles.stream()
                .filter(profile -> normalize(profile.id()).equals(normalizedRequested))
                .findFirst()
                .map(profile -> new CandidateProfileResolution(Optional.of(profile), false, ""))
                .orElseGet(() -> new CandidateProfileResolution(
                        Optional.empty(),
                        false,
                        "Unknown candidate profile id '" + requestedId + "'."
                ));
    }

    public static boolean isCandidateProfileNone(String value) {
        return CANDIDATE_PROFILE_NONE.equals(normalize(value));
    }

    public static String candidateProfileIdOrNone(CandidateProfileConfig profile) {
        return profile == null ? CANDIDATE_PROFILE_NONE : profile.id();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CandidateProfileResolution(
            Optional<CandidateProfileConfig> profile,
            boolean autoSelected,
            String errorMessage
    ) {
    }
}
