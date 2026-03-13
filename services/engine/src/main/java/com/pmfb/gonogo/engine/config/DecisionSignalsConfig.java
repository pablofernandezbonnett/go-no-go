package com.pmfb.gonogo.engine.config;

import java.util.List;

public record DecisionSignalsConfig(
        LanguageConfig language,
        WorkLifeBalanceConfig workLifeBalance,
        MobilityConfig mobility
) {
    public DecisionSignalsConfig {
        language = language == null ? LanguageConfig.defaults() : language;
        workLifeBalance = workLifeBalance == null ? WorkLifeBalanceConfig.defaults() : workLifeBalance;
        mobility = mobility == null ? MobilityConfig.defaults() : mobility;
    }

    public static DecisionSignalsConfig defaults() {
        return new DecisionSignalsConfig(
                LanguageConfig.defaults(),
                WorkLifeBalanceConfig.defaults(),
                MobilityConfig.defaults()
        );
    }

    public record LanguageConfig(
            List<String> requiredKeywords,
            List<String> frictionSoftKeywords,
            List<String> mediumHighFrictionKeywords,
            List<String> highFrictionKeywords,
            List<String> optionalOrExemptKeywords,
            List<String> englishFriendlyKeywords,
            List<String> englishSupportEnvironmentKeywords,
            int englishSupportMaxIndex
    ) {
        public LanguageConfig {
            requiredKeywords = List.copyOf(requiredKeywords);
            frictionSoftKeywords = List.copyOf(frictionSoftKeywords);
            mediumHighFrictionKeywords = List.copyOf(mediumHighFrictionKeywords);
            highFrictionKeywords = List.copyOf(highFrictionKeywords);
            optionalOrExemptKeywords = List.copyOf(optionalOrExemptKeywords);
            englishFriendlyKeywords = List.copyOf(englishFriendlyKeywords);
            englishSupportEnvironmentKeywords = List.copyOf(englishSupportEnvironmentKeywords);
        }

        public static LanguageConfig defaults() {
            return new LanguageConfig(
                    List.of(
                            "japanese required",
                            "japanese is required",
                            "japanese language required",
                            "must speak japanese",
                            "japanese is mandatory",
                            "mandatory japanese",
                            "japanese proficiency required",
                            "native japanese",
                            "native-level japanese",
                            "native level japanese",
                            "fluent japanese",
                            "advanced japanese",
                            "professional japanese",
                            "business-level japanese",
                            "business level japanese",
                            "jlpt n1",
                            "jlpt n2",
                            "n1 level japanese",
                            "n2 level japanese",
                            "日本語必須",
                            "ビジネスレベルの日本語",
                            "日本語ネイティブ",
                            "日本語能力試験n1",
                            "日本語能力試験n2"
                    ),
                    List.of(
                            "business japanese",
                            "japanese fluency",
                            "japanese proficiency",
                            "japanese communication",
                            "japanese skill"
                    ),
                    List.of(
                            "business-level japanese",
                            "business level japanese",
                            "jlpt n2",
                            "n2 level japanese",
                            "n2 level or above",
                            "n2 or above",
                            "ビジネスレベルの日本語",
                            "日本語能力試験n2",
                            "日本語能力試験ｎ2",
                            "日本語能力試験Ｎ２"
                    ),
                    List.of(
                            "native japanese",
                            "native-level japanese",
                            "native level japanese",
                            "jlpt n1",
                            "n1 level japanese",
                            "日本語ネイティブ",
                            "日本語能力試験n1",
                            "日本語能力試験ｎ1",
                            "日本語能力試験Ｎ１"
                    ),
                    List.of(
                            "no japanese required",
                            "japanese not required",
                            "japanese is a plus",
                            "japanese preferred",
                            "japanese optional",
                            "japanese nice to have",
                            "basic japanese welcome",
                            "conversational japanese preferred",
                            "english only",
                            "日本語不問",
                            "日本語歓迎",
                            "日本語は必須ではありません"
                    ),
                    List.of(
                            "english-first",
                            "english first",
                            "international team",
                            "no japanese required",
                            "japanese not required",
                            "english only",
                            "english speaking environment",
                            "english communication",
                            "global team"
                    ),
                    List.of(
                            "english is used on a daily basis",
                            "english used on a daily basis",
                            "daily english usage",
                            "english usage on a daily basis",
                            "fluency in english is not required",
                            "current fluency in english is not required",
                            "current fluency is not a requirement",
                            "english fluency is not required",
                            "english is not required",
                            "comfortable with english",
                            "support is provided"
                    ),
                    20
            );
        }
    }

    public record WorkLifeBalanceConfig(
            List<String> overtimeRiskKeywords,
            List<String> holidayPolicyRiskKeywords
    ) {
        public WorkLifeBalanceConfig {
            overtimeRiskKeywords = List.copyOf(overtimeRiskKeywords);
            holidayPolicyRiskKeywords = List.copyOf(holidayPolicyRiskKeywords);
        }

        public static WorkLifeBalanceConfig defaults() {
            return new WorkLifeBalanceConfig(
                    List.of(
                            "fixed overtime",
                            "deemed overtime",
                            "overtime included",
                            "overtime work may occur",
                            "overtime required",
                            "significant overtime",
                            "fast-paced",
                            "high-pressure",
                            "tight deadlines",
                            "deadlines are tight",
                            "occasional weekend work"
                    ),
                    List.of(
                            "national holidays correspond to working days",
                            "national holidays are working days",
                            "national holidays are workdays",
                            "public holidays are working days",
                            "public holidays are workdays",
                            "holidays correspond to working days"
                    )
            );
        }
    }

    public record MobilityConfig(
            List<String> locationMobilityRiskKeywords
    ) {
        public MobilityConfig {
            locationMobilityRiskKeywords = List.copyOf(locationMobilityRiskKeywords);
        }

        public static MobilityConfig defaults() {
            return new MobilityConfig(
                    List.of(
                            "location may be changed",
                            "including overseas",
                            "transfer may be required",
                            "relocation may be required",
                            "as determined by the company"
                    )
            );
        }
    }
}
