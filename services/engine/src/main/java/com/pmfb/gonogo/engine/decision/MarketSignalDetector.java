package com.pmfb.gonogo.engine.decision;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarketSignalDetector {
    private static final int ALGORITHMIC_INTERVIEW_HIT_MIN = 2;
    private static final int ENGINEERING_MATURITY_HIT_MIN = 2;
    private static final int PRESSURE_CULTURE_HIT_MIN = 2;
    private static final int LOW_OVERTIME_HOURS_MAX = 15;

    private static final List<String> ALGORITHMIC_INTERVIEW_KEYWORDS = List.of(
            "coding test",
            "online assessment",
            "hackerrank",
            "leetcode",
            "algorithmic thinking",
            "computer science fundamentals",
            "data structures and algorithms",
            "data structures",
            "problem-solving ability",
            "strong problem-solving ability"
    );
    private static final List<String> PM_COLLABORATION_KEYWORDS = List.of(
            "product manager",
            "product managers",
            "with pm",
            "with pms",
            "collaborate closely with product managers"
    );
    private static final List<String> PRODUCT_DELIVERY_KEYWORDS = List.of(
            "customer-facing product",
            "customer facing product",
            "product reliability",
            "scalable backend systems",
            "scalable backend system",
            "build scalable backend systems",
            "product development",
            "customer-facing",
            "customer facing"
    );
    private static final List<String> ENGINEERING_MATURITY_KEYWORDS = List.of(
            "engineering blog",
            "tech blog",
            "developer productivity",
            "platform engineering",
            "internal developer tools",
            "developer experience",
            "devex"
    );
    private static final List<String> CASUAL_INTERVIEW_KEYWORDS = List.of(
            "casual interview",
            "casual talk",
            "casual chat",
            "informal chat"
    );
    private static final List<String> ASYNC_COMMUNICATION_KEYWORDS = List.of(
            "asynchronous communication",
            "async communication",
            "written-first communication",
            "written first communication"
    );
    private static final List<String> REAL_FLEXTIME_KEYWORDS = List.of(
            "flextime",
            "flex time",
            "full flextime",
            "core time",
            "no core hours",
            "core hours"
    );
    private static final List<String> GENERIC_FLEXTIME_KEYWORDS = List.of(
            "flexible working hours",
            "flexible hours",
            "flexible schedule"
    );
    private static final Pattern CORE_TIME_WINDOW_PATTERN = Pattern.compile(
            "(?i)core\\s*time\\s*[:：]?\\s*\\d{1,2}:\\d{2}\\s*[-–—~〜～]\\s*\\d{1,2}:\\d{2}"
    );
    private static final Pattern AVERAGE_OVERTIME_HOURS_PATTERN = Pattern.compile(
            "(?i)average\\s+overtime\\s*[:：]?\\s*(\\d{1,3})(?:\\.\\d+)?\\s*(?:h|hr|hrs|hours?)\\s*(?:/|per)?\\s*month"
    );
    private static final List<String> PRESSURE_CULTURE_KEYWORDS = List.of(
            "fast-paced environment",
            "fast paced environment",
            "dynamic environment",
            "handle multiple tasks",
            "work under pressure",
            "self-motivated",
            "self motivated"
    );
    private static final List<String> PRESSURE_CULTURE_STRONG_KEYWORDS = List.of(
            "self-motivated person who can work under pressure",
            "self motivated person who can work under pressure"
    );
    private static final List<String> TOEIC_KEYWORDS = List.of(
            "toeic",
            "toeic score"
    );
    private static final List<String> TRADITIONAL_CORPORATE_PROCESS_KEYWORDS = List.of(
            "business level japanese required",
            "business-level japanese required",
            "business japanese required",
            "native japanese required",
            "native-level japanese required",
            "professional japanese required"
    );
    private static final List<String> CUSTOMER_SITE_KEYWORDS = List.of(
            "onsite at customer",
            "on-site at customer",
            "onsite at client",
            "on-site at client",
            "customer site",
            "client site",
            "client projects",
            "client project",
            "assigned to client site",
            "work at client offices",
            "onsite at customer offices"
    );

    SignalMatches detect(String combinedText) {
        String normalizedText = normalize(combinedText);
        boolean realFlextimePositive = hasRealFlextimePositive(normalizedText);

        return new SignalMatches(
                hasAlgorithmicInterviewRisk(normalizedText),
                hasProductPmCollaborationPositive(normalizedText),
                hasEngineeringMaturityPositive(normalizedText),
                hasCasualInterviewPositive(normalizedText),
                hasAsyncCommunicationPositive(normalizedText),
                realFlextimePositive,
                hasLowOvertimeDisclosedPositive(normalizedText),
                hasPressureCultureRisk(normalizedText),
                hasFakeFlextimeRisk(normalizedText, realFlextimePositive),
                hasTraditionalCorporateProcessRisk(normalizedText),
                hasCustomerSiteRisk(normalizedText)
        );
    }

    private boolean hasAlgorithmicInterviewRisk(String combinedText) {
        return countKeywordMatches(combinedText, ALGORITHMIC_INTERVIEW_KEYWORDS) >= ALGORITHMIC_INTERVIEW_HIT_MIN;
    }

    private boolean hasProductPmCollaborationPositive(String combinedText) {
        return containsAny(combinedText, PM_COLLABORATION_KEYWORDS)
                && containsAny(combinedText, PRODUCT_DELIVERY_KEYWORDS);
    }

    private boolean hasEngineeringMaturityPositive(String combinedText) {
        return countKeywordMatches(combinedText, ENGINEERING_MATURITY_KEYWORDS) >= ENGINEERING_MATURITY_HIT_MIN;
    }

    private boolean hasCasualInterviewPositive(String combinedText) {
        return containsAny(combinedText, CASUAL_INTERVIEW_KEYWORDS);
    }

    private boolean hasAsyncCommunicationPositive(String combinedText) {
        return containsAny(combinedText, ASYNC_COMMUNICATION_KEYWORDS);
    }

    private boolean hasRealFlextimePositive(String combinedText) {
        return containsAny(combinedText, REAL_FLEXTIME_KEYWORDS)
                || CORE_TIME_WINDOW_PATTERN.matcher(combinedText).find();
    }

    private boolean hasLowOvertimeDisclosedPositive(String combinedText) {
        Matcher matcher = AVERAGE_OVERTIME_HOURS_PATTERN.matcher(combinedText);
        if (!matcher.find()) {
            return false;
        }
        int hours = Integer.parseInt(matcher.group(1));
        return hours <= LOW_OVERTIME_HOURS_MAX;
    }

    private boolean hasPressureCultureRisk(String combinedText) {
        return containsAny(combinedText, PRESSURE_CULTURE_STRONG_KEYWORDS)
                || countKeywordMatches(combinedText, PRESSURE_CULTURE_KEYWORDS) >= PRESSURE_CULTURE_HIT_MIN;
    }

    private boolean hasFakeFlextimeRisk(String combinedText, boolean realFlextimePositive) {
        return !realFlextimePositive && containsAny(combinedText, GENERIC_FLEXTIME_KEYWORDS);
    }

    private boolean hasTraditionalCorporateProcessRisk(String combinedText) {
        if (containsAny(combinedText, TOEIC_KEYWORDS)) {
            return true;
        }
        return containsAny(combinedText, TRADITIONAL_CORPORATE_PROCESS_KEYWORDS);
    }

    private boolean hasCustomerSiteRisk(String combinedText) {
        return containsAny(combinedText, CUSTOMER_SITE_KEYWORDS);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private int countKeywordMatches(String text, List<String> keywords) {
        int matches = 0;
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private boolean containsKeyword(String text, String keyword) {
        String normalizedKeyword = normalize(keyword);
        return !text.isBlank()
                && !normalizedKeyword.isBlank()
                && text.contains(normalizedKeyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record SignalMatches(
            boolean algorithmicInterviewRisk,
            boolean productPmCollaborationPositive,
            boolean engineeringMaturityPositive,
            boolean casualInterviewPositive,
            boolean asyncCommunicationPositive,
            boolean realFlextimePositive,
            boolean lowOvertimeDisclosedPositive,
            boolean pressureCultureRisk,
            boolean fakeFlextimeRisk,
            boolean traditionalCorporateProcessRisk,
            boolean customerSiteRisk
    ) {
    }
}
