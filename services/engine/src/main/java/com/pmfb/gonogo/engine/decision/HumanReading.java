package com.pmfb.gonogo.engine.decision;

import java.util.List;

public record HumanReading(
        HumanReadingLevel accessFit,
        HumanReadingLevel executionFit,
        HumanReadingLevel domainFit,
        HumanReadingLevel opportunityQuality,
        HumanReadingLevel interviewRoi,
        String summary,
        List<String> whyStillInteresting,
        List<String> whyWasteOfTime
) {
    public HumanReading {
        accessFit = accessFit == null ? HumanReadingLevel.MIXED : accessFit;
        executionFit = executionFit == null ? HumanReadingLevel.MIXED : executionFit;
        domainFit = domainFit == null ? HumanReadingLevel.MIXED : domainFit;
        opportunityQuality = opportunityQuality == null ? HumanReadingLevel.MIXED : opportunityQuality;
        interviewRoi = interviewRoi == null ? HumanReadingLevel.MIXED : interviewRoi;
        summary = summary == null ? "" : summary.trim();
        whyStillInteresting = whyStillInteresting == null ? List.of() : List.copyOf(whyStillInteresting);
        whyWasteOfTime = whyWasteOfTime == null ? List.of() : List.copyOf(whyWasteOfTime);
    }

    public static HumanReading empty() {
        return new HumanReading(
                HumanReadingLevel.MIXED,
                HumanReadingLevel.MIXED,
                HumanReadingLevel.MIXED,
                HumanReadingLevel.MIXED,
                HumanReadingLevel.MIXED,
                "",
                List.of(),
                List.of()
        );
    }
}
