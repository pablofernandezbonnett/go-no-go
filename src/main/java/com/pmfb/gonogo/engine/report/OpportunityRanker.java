package com.pmfb.gonogo.engine.report;

import com.pmfb.gonogo.engine.decision.RankingStrategy;
import java.util.Comparator;

public final class OpportunityRanker {

    public Comparator<WeeklyDigestItem> comparatorFor(RankingStrategy strategy) {
        return switch (strategy) {
            case BY_SCORE -> (a, b) -> Integer.compare(b.score(), a.score());
            case BY_LANGUAGE_EASE -> (a, b) -> {
                int c = Integer.compare(a.languageFrictionIndex(), b.languageFrictionIndex());
                return c != 0 ? c : Integer.compare(b.score(), a.score());
            };
            case BY_REPUTATION -> (a, b) -> {
                int c = Integer.compare(b.companyReputationIndex(), a.companyReputationIndex());
                return c != 0 ? c : Integer.compare(b.score(), a.score());
            };
            case BY_COMPOSITE -> (a, b) -> {
                int ca = 2 * a.score() + (100 - a.languageFrictionIndex()) + a.companyReputationIndex();
                int cb = 2 * b.score() + (100 - b.languageFrictionIndex()) + b.companyReputationIndex();
                return Integer.compare(cb, ca);
            };
        };
    }
}
