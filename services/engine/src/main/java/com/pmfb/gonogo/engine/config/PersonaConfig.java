package com.pmfb.gonogo.engine.config;

import com.pmfb.gonogo.engine.decision.RankingStrategy;
import java.util.List;
import java.util.Map;

public record PersonaConfig(
        String id,
        String description,
        List<String> priorities,
        List<String> hardNo,
        List<String> acceptableIf,
        Map<String, Integer> signalWeights,
        RankingStrategy rankingStrategy,
        int minimumSalaryYen
) {
    public PersonaConfig {
        priorities = List.copyOf(priorities);
        hardNo = List.copyOf(hardNo);
        acceptableIf = List.copyOf(acceptableIf);
        signalWeights = Map.copyOf(signalWeights);
    }

    public PersonaConfig(
            String id,
            String description,
            List<String> priorities,
            List<String> hardNo,
            List<String> acceptableIf
    ) {
        this(id, description, priorities, hardNo, acceptableIf, Map.of(), RankingStrategy.BY_SCORE, 0);
    }

    public PersonaConfig(
            String id,
            String description,
            List<String> priorities,
            List<String> hardNo,
            List<String> acceptableIf,
            Map<String, Integer> signalWeights,
            RankingStrategy rankingStrategy
    ) {
        this(id, description, priorities, hardNo, acceptableIf, signalWeights, rankingStrategy, 0);
    }
}
