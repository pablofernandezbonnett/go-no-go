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
        int minimumSalaryYen,
        PersonaIndex index
) {
    public PersonaConfig {
        id = id == null ? "" : id.trim();
        description = description == null ? "" : description.trim();
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
        hardNo = hardNo == null ? List.of() : List.copyOf(hardNo);
        acceptableIf = acceptableIf == null ? List.of() : List.copyOf(acceptableIf);
        signalWeights = signalWeights == null ? Map.of() : Map.copyOf(signalWeights);
        rankingStrategy = rankingStrategy == null ? RankingStrategy.BY_SCORE : rankingStrategy;
        index = index == null
                ? PersonaIndex.from(priorities, hardNo, acceptableIf, signalWeights)
                : index;
    }

    public PersonaConfig(
            String id,
            String description,
            List<String> priorities,
            List<String> hardNo,
            List<String> acceptableIf
    ) {
        this(
                id,
                description,
                priorities,
                hardNo,
                acceptableIf,
                Map.of(),
                RankingStrategy.BY_SCORE,
                0,
                null
        );
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
        this(
                id,
                description,
                priorities,
                hardNo,
                acceptableIf,
                signalWeights,
                rankingStrategy,
                0,
                null
        );
    }

    public PersonaConfig(
            String id,
            String description,
            List<String> priorities,
            List<String> hardNo,
            List<String> acceptableIf,
            Map<String, Integer> signalWeights,
            RankingStrategy rankingStrategy,
            int minimumSalaryYen
    ) {
        this(
                id,
                description,
                priorities,
                hardNo,
                acceptableIf,
                signalWeights,
                rankingStrategy,
                minimumSalaryYen,
                null
        );
    }
}
