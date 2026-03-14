package com.pmfb.gonogo.engine.decision;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public catalog of all 58 signals recognized by DecisionEngineV1.
 *
 * <p>Provides metadata (name, type, priority group, default weight) for each signal,
 * enabling YAML config validation and API exposure via GET /api/signals.
 *
 * <p>{@code defaultWeight} reflects the priority-matched default weight, i.e. the weight
 * applied when the signal's {@code priorityGroup} is in the persona's priorities list.
 */
public final class SignalRegistry {
    private static final String JSON_FIELD_NAME = "name";
    private static final String JSON_FIELD_TYPE = "type";
    private static final String JSON_FIELD_PRIORITY_GROUP = "priority_group";
    private static final String JSON_FIELD_DEFAULT_WEIGHT = "default_weight";

    public enum SignalType { POSITIVE, RISK }

    public record SignalDescriptor(
            String name,
            SignalType type,
            String priorityGroup,
            int defaultWeight
    ) {
        public Map<String, Object> toJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(JSON_FIELD_NAME, name);
            map.put(JSON_FIELD_TYPE, type.name().toLowerCase(Locale.ROOT));
            map.put(JSON_FIELD_PRIORITY_GROUP, priorityGroup);
            map.put(JSON_FIELD_DEFAULT_WEIGHT, defaultWeight);
            return map;
        }
    }

    private static final List<SignalDescriptor> ALL = List.of(
            // ── Positive signals (24) ──────────────────────────────────────────────
            new SignalDescriptor(SignalIds.SALARY_TRANSPARENCY,               SignalType.POSITIVE, PriorityGroupIds.SALARY,              2),
            new SignalDescriptor(SignalIds.HYBRID_WORK,                       SignalType.POSITIVE, PriorityGroupIds.HYBRID_WORK,         2),
            new SignalDescriptor(SignalIds.REMOTE_FRIENDLY,                   SignalType.POSITIVE, PriorityGroupIds.HYBRID_WORK,         2),
            new SignalDescriptor(SignalIds.ENGLISH_ENVIRONMENT,               SignalType.POSITIVE, PriorityGroupIds.ENGLISH_ENVIRONMENT, 2),
            new SignalDescriptor(SignalIds.PRODUCT_COMPANY,                   SignalType.POSITIVE, PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.ENGINEERING_CULTURE,               SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.ENGINEERING_ENVIRONMENT,           SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.INHOUSE_PRODUCT_ENGINEERING,       SignalType.POSITIVE, PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.GLOBAL_TEAM_COLLABORATION,         SignalType.POSITIVE, PriorityGroupIds.ENGLISH_ENVIRONMENT, 2),
            new SignalDescriptor(SignalIds.ENGLISH_SUPPORT_ENVIRONMENT,       SignalType.POSITIVE, PriorityGroupIds.ENGLISH_ENVIRONMENT, 2),
            new SignalDescriptor(SignalIds.VISA_SPONSORSHIP_SUPPORT,          SignalType.POSITIVE, PriorityGroupIds.ENGLISH_ENVIRONMENT, 2),
            new SignalDescriptor(SignalIds.WORK_LIFE_BALANCE,                 SignalType.POSITIVE, PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.STABILITY,                         SignalType.POSITIVE, PriorityGroupIds.STABILITY,           2),
            new SignalDescriptor(SignalIds.COMPANY_REPUTATION_POSITIVE,       SignalType.POSITIVE, PriorityGroupIds.STABILITY,           2),
            new SignalDescriptor(SignalIds.COMPANY_REPUTATION_POSITIVE_STRONG,SignalType.POSITIVE, PriorityGroupIds.STABILITY,           2),
            new SignalDescriptor(SignalIds.CANDIDATE_STACK_FIT,               SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.CANDIDATE_DOMAIN_FIT,              SignalType.POSITIVE, PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.CANDIDATE_SENIORITY_FIT,           SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.PRODUCT_PM_COLLABORATION,          SignalType.POSITIVE, PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.ENGINEERING_MATURITY,              SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.CASUAL_INTERVIEW,                  SignalType.POSITIVE, PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.ASYNC_COMMUNICATION,               SignalType.POSITIVE, PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.REAL_FLEXTIME,                     SignalType.POSITIVE, PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.LOW_OVERTIME_DISCLOSED,            SignalType.POSITIVE, PriorityGroupIds.WORK_LIFE_BALANCE,   2),

            // ── Risk signals (34) ─────────────────────────────────────────────────
            new SignalDescriptor(SignalIds.SALARY_LOW_CONFIDENCE,             SignalType.RISK,     PriorityGroupIds.SALARY,              2),
            new SignalDescriptor(SignalIds.SALARY_BELOW_PERSONA_FLOOR,        SignalType.RISK,     PriorityGroupIds.SALARY,              3),
            new SignalDescriptor(SignalIds.ONSITE_BIAS,                       SignalType.RISK,     PriorityGroupIds.HYBRID_WORK,         2),
            new SignalDescriptor(SignalIds.LANGUAGE_FRICTION,                 SignalType.RISK,     PriorityGroupIds.ENGLISH_ENVIRONMENT, 3),
            new SignalDescriptor(SignalIds.LANGUAGE_FRICTION_CRITICAL,        SignalType.RISK,     PriorityGroupIds.ENGLISH_ENVIRONMENT, 7),
            new SignalDescriptor(SignalIds.CONSULTING_RISK,                   SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     3),
            new SignalDescriptor(SignalIds.OVERTIME_RISK,                     SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   3),
            new SignalDescriptor(SignalIds.ENGINEERING_ENVIRONMENT_RISK,      SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.STARTUP_RISK,                      SignalType.RISK,     PriorityGroupIds.STABILITY,           3),
            new SignalDescriptor(SignalIds.ROLE_MISMATCH_MANAGER_VS_IC_TITLE, SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 5),
            new SignalDescriptor(SignalIds.ROLE_IDENTITY_MISMATCH,            SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 6),
            new SignalDescriptor(SignalIds.INTERMEDIARY_CONTRACT_RISK,        SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     5),
            new SignalDescriptor(SignalIds.ANONYMOUS_EMPLOYER_RISK,           SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.GENERIC_MARKETING_POST_RISK,       SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     2),
            new SignalDescriptor(SignalIds.VAGUE_CONDITIONS_RISK,             SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.INCLUSION_CONTRADICTION,           SignalType.RISK,     PriorityGroupIds.ENGLISH_ENVIRONMENT, 5),
            new SignalDescriptor(SignalIds.PRE_IPO_RISK,                      SignalType.RISK,     PriorityGroupIds.STABILITY,           4),
            new SignalDescriptor(SignalIds.MANAGER_SCOPE_SALARY_MISALIGNED,   SignalType.RISK,     PriorityGroupIds.SALARY,              5),
            new SignalDescriptor(SignalIds.WORKLOAD_POLICY_RISK,              SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   6),
            new SignalDescriptor(SignalIds.HOLIDAY_POLICY_RISK,               SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   5),
            new SignalDescriptor(SignalIds.LOCATION_MOBILITY_RISK,            SignalType.RISK,     PriorityGroupIds.HYBRID_WORK,         4),
            new SignalDescriptor(SignalIds.SALARY_RANGE_ANOMALY,              SignalType.RISK,     PriorityGroupIds.SALARY,              6),
            new SignalDescriptor(SignalIds.DEBT_FIRST_CULTURE_RISK,           SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 6),
            new SignalDescriptor(SignalIds.HYPERGROWTH_EXECUTION_RISK,        SignalType.RISK,     PriorityGroupIds.STABILITY,           5),
            new SignalDescriptor(SignalIds.COMPANY_REPUTATION_RISK,           SignalType.RISK,     PriorityGroupIds.STABILITY,           3),
            new SignalDescriptor(SignalIds.COMPANY_REPUTATION_RISK_HIGH,      SignalType.RISK,     PriorityGroupIds.STABILITY,           3),
            new SignalDescriptor(SignalIds.CANDIDATE_STACK_GAP,               SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 3),
            new SignalDescriptor(SignalIds.CANDIDATE_DOMAIN_GAP,              SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     3),
            new SignalDescriptor(SignalIds.CANDIDATE_SENIORITY_MISMATCH,      SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 4),
            new SignalDescriptor(SignalIds.ALGORITHMIC_INTERVIEW_RISK,        SignalType.RISK,     PriorityGroupIds.ENGINEERING_CULTURE, 2),
            new SignalDescriptor(SignalIds.PRESSURE_CULTURE_RISK,             SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.FAKE_FLEXTIME_RISK,                SignalType.RISK,     PriorityGroupIds.WORK_LIFE_BALANCE,   2),
            new SignalDescriptor(SignalIds.TRADITIONAL_CORPORATE_PROCESS_RISK,SignalType.RISK,     PriorityGroupIds.ENGLISH_ENVIRONMENT, 2),
            new SignalDescriptor(SignalIds.CUSTOMER_SITE_RISK,                SignalType.RISK,     PriorityGroupIds.PRODUCT_COMPANY,     2)
    );

    private static final Map<String, SignalDescriptor> BY_NAME;
    static {
        Map<String, SignalDescriptor> index = new HashMap<>(ALL.size() * 2);
        for (SignalDescriptor d : ALL) {
            index.put(d.name(), d);
        }
        BY_NAME = Map.copyOf(index);
    }

    private SignalRegistry() {}

    /** Returns an unmodifiable list of all signal descriptors. */
    public static List<SignalDescriptor> all() {
        return ALL;
    }

    /** O(1) lookup: returns true if {@code name} is a recognized signal name. */
    public static boolean isKnown(String name) {
        return BY_NAME.containsKey(name);
    }

    /**
     * Validates every key in {@code weights} against the known signal catalog.
     * Appends an error message for each unrecognized signal name to {@code errors}.
     *
     * @param weights  the signal_weights map to validate
     * @param context  description prefix for error messages (e.g. "personas.yaml personas[0] signal_weights")
     * @param errors   mutable list to which error strings are appended
     */
    public static void validateWeightKeys(
            Map<String, Integer> weights,
            String context,
            List<String> errors
    ) {
        for (String key : weights.keySet()) {
            if (!isKnown(key)) {
                errors.add(context + " contains unknown signal '" + key + "'");
            }
        }
    }

    /** Returns a list of JSON-serializable maps, one per signal descriptor. */
    public static List<Map<String, Object>> toJsonList() {
        return ALL.stream().map(SignalDescriptor::toJson).toList();
    }
}
