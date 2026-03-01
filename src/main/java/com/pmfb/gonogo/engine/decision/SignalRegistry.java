package com.pmfb.gonogo.engine.decision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public catalog of all 35 signals recognized by DecisionEngineV1.
 *
 * <p>Provides metadata (name, type, priority group, default weight) for each signal,
 * enabling YAML config validation and API exposure via GET /api/signals.
 *
 * <p>{@code defaultWeight} reflects the priority-matched default weight, i.e. the weight
 * applied when the signal's {@code priorityGroup} is in the persona's priorities list.
 */
public final class SignalRegistry {

    public enum SignalType { POSITIVE, RISK }

    public record SignalDescriptor(
            String name,
            SignalType type,
            String priorityGroup,
            int defaultWeight
    ) {
        public Map<String, Object> toJson() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("type", type.name().toLowerCase());
            map.put("priority_group", priorityGroup);
            map.put("default_weight", defaultWeight);
            return map;
        }
    }

    private static final List<SignalDescriptor> ALL = List.of(
            // ── Positive signals (15) ──────────────────────────────────────────────
            new SignalDescriptor("salary_transparency",               SignalType.POSITIVE, "salary",               2),
            new SignalDescriptor("hybrid_work",                       SignalType.POSITIVE, "hybrid_work",          2),
            new SignalDescriptor("remote_friendly",                   SignalType.POSITIVE, "hybrid_work",          2),
            new SignalDescriptor("english_environment",               SignalType.POSITIVE, "english_environment",  2),
            new SignalDescriptor("product_company",                   SignalType.POSITIVE, "product_company",      2),
            new SignalDescriptor("engineering_culture",               SignalType.POSITIVE, "engineering_culture",  2),
            new SignalDescriptor("engineering_environment",           SignalType.POSITIVE, "engineering_culture",  2),
            new SignalDescriptor("inhouse_product_engineering",       SignalType.POSITIVE, "product_company",      2),
            new SignalDescriptor("global_team_collaboration",         SignalType.POSITIVE, "english_environment",  2),
            new SignalDescriptor("english_support_environment",       SignalType.POSITIVE, "english_environment",  2),
            new SignalDescriptor("visa_sponsorship_support",          SignalType.POSITIVE, "english_environment",  2),
            new SignalDescriptor("work_life_balance",                 SignalType.POSITIVE, "work_life_balance",    2),
            new SignalDescriptor("stability",                         SignalType.POSITIVE, "stability",            2),
            new SignalDescriptor("company_reputation_positive",       SignalType.POSITIVE, "stability",            2),
            new SignalDescriptor("company_reputation_positive_strong",SignalType.POSITIVE, "stability",            2),

            // ── Risk signals (20) ─────────────────────────────────────────────────
            new SignalDescriptor("salary_low_confidence",             SignalType.RISK,     "salary",               2),
            new SignalDescriptor("onsite_bias",                       SignalType.RISK,     "hybrid_work",          2),
            new SignalDescriptor("language_friction",                 SignalType.RISK,     "english_environment",  3),
            new SignalDescriptor("language_friction_critical",        SignalType.RISK,     "english_environment",  7),
            new SignalDescriptor("consulting_risk",                   SignalType.RISK,     "product_company",      3),
            new SignalDescriptor("overtime_risk",                     SignalType.RISK,     "work_life_balance",    3),
            new SignalDescriptor("engineering_environment_risk",      SignalType.RISK,     "work_life_balance",    2),
            new SignalDescriptor("startup_risk",                      SignalType.RISK,     "stability",            3),
            new SignalDescriptor("role_mismatch_manager_vs_ic_title", SignalType.RISK,     "engineering_culture",  5),
            new SignalDescriptor("inclusion_contradiction",           SignalType.RISK,     "english_environment",  5),
            new SignalDescriptor("pre_ipo_risk",                      SignalType.RISK,     "stability",            4),
            new SignalDescriptor("manager_scope_salary_misaligned",   SignalType.RISK,     "salary",               5),
            new SignalDescriptor("workload_policy_risk",              SignalType.RISK,     "work_life_balance",    6),
            new SignalDescriptor("holiday_policy_risk",               SignalType.RISK,     "work_life_balance",    5),
            new SignalDescriptor("location_mobility_risk",            SignalType.RISK,     "hybrid_work",          4),
            new SignalDescriptor("salary_range_anomaly",              SignalType.RISK,     "salary",               6),
            new SignalDescriptor("debt_first_culture_risk",           SignalType.RISK,     "engineering_culture",  6),
            new SignalDescriptor("hypergrowth_execution_risk",        SignalType.RISK,     "stability",            5),
            new SignalDescriptor("company_reputation_risk",           SignalType.RISK,     "stability",            3),
            new SignalDescriptor("company_reputation_risk_high",      SignalType.RISK,     "stability",            3)
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

    /** Returns an unmodifiable list of all 35 signal descriptors. */
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
