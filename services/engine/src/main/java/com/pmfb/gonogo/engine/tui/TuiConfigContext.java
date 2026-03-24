package com.pmfb.gonogo.engine.tui;

import com.pmfb.gonogo.engine.config.CandidateProfileConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.ConfigValidator;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.EvaluationRuntimeConfig;
import com.pmfb.gonogo.engine.config.FetchWebRuntimeConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.YamlConfigLoader;
import com.pmfb.gonogo.engine.exception.ConfigLoadException;
import java.nio.file.Path;
import java.util.List;

public record TuiConfigContext(
        Path configDir,
        List<CompanyConfig> companies,
        List<PersonaConfig> personas,
        List<CandidateProfileConfig> candidateProfiles,
        FetchWebRuntimeConfig fetchWebDefaults,
        EvaluationRuntimeConfig evaluationDefaults
) {
    private static final String DEFAULT_PERSONA_ID = "product_expat_engineer";

    public TuiConfigContext {
        companies = List.copyOf(companies);
        personas = List.copyOf(personas);
        candidateProfiles = List.copyOf(candidateProfiles);
    }

    public static TuiConfigContext load(Path configDir) {
        EngineConfig config = new YamlConfigLoader(configDir).load();
        List<String> validationErrors = new ConfigValidator().validate(config);
        if (!validationErrors.isEmpty()) {
            throw new ConfigLoadException(validationErrors);
        }
        return new TuiConfigContext(
                configDir.normalize(),
                config.companies(),
                config.personas(),
                config.candidateProfiles(),
                config.runtimeSettings().fetchWeb(),
                config.runtimeSettings().evaluation()
        );
    }

    public List<String> companyIds() {
        return companies.stream()
                .map(CompanyConfig::id)
                .toList();
    }

    public List<String> personaIds() {
        return personas.stream()
                .map(PersonaConfig::id)
                .toList();
    }

    public String defaultPersonaId() {
        return personas.stream()
                .map(PersonaConfig::id)
                .filter(DEFAULT_PERSONA_ID::equals)
                .findFirst()
                .orElseGet(() -> personas.isEmpty() ? DEFAULT_PERSONA_ID : personas.getFirst().id());
    }
}
