package com.pmfb.gonogo.engine.config;

import java.util.List;

public record EngineConfig(
        List<CompanyConfig> companies,
        List<PersonaConfig> personas,
        List<BlacklistedCompanyConfig> blacklistedCompanies,
        List<CandidateProfileConfig> candidateProfiles,
        RuntimeSettingsConfig runtimeSettings,
        DecisionSignalsConfig decisionSignals
) {
    public EngineConfig {
        companies = List.copyOf(companies);
        personas = List.copyOf(personas);
        blacklistedCompanies = List.copyOf(blacklistedCompanies);
        candidateProfiles = List.copyOf(candidateProfiles);
        runtimeSettings = runtimeSettings == null ? RuntimeSettingsConfig.defaults() : runtimeSettings;
        decisionSignals = decisionSignals == null ? DecisionSignalsConfig.defaults() : decisionSignals;
    }

    public EngineConfig(
            List<CompanyConfig> companies,
            List<PersonaConfig> personas,
            List<BlacklistedCompanyConfig> blacklistedCompanies,
            List<CandidateProfileConfig> candidateProfiles,
            RuntimeSettingsConfig runtimeSettings
    ) {
        this(
                companies,
                personas,
                blacklistedCompanies,
                candidateProfiles,
                runtimeSettings,
                DecisionSignalsConfig.defaults()
        );
    }
}
