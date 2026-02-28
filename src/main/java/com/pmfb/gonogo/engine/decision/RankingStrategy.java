package com.pmfb.gonogo.engine.decision;

public enum RankingStrategy {
    BY_SCORE,         // normalized score DESC (current default)
    BY_LANGUAGE_EASE, // languageFrictionIndex ASC, score DESC as tiebreaker
    BY_REPUTATION,    // companyReputationIndex DESC, score DESC as tiebreaker
    BY_COMPOSITE      // weighted: 2*score + (100-friction) + reputation DESC
}
