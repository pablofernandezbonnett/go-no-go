package com.pmfb.gonogo.engine.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CandidateProfileTaxonomy {
    private static final Map<String, List<String>> SKILL_ALIASES = Map.ofEntries(
            Map.entry("java", List.of("java")),
            Map.entry("spring", List.of("spring", "spring framework")),
            Map.entry("spring_boot", List.of("spring boot", "springboot")),
            Map.entry("aws", List.of("aws", "amazon web services")),
            Map.entry("gcp", List.of("gcp", "google cloud", "google cloud platform")),
            Map.entry("azure", List.of("azure", "microsoft azure")),
            Map.entry("python", List.of("python")),
            Map.entry("go", List.of("go", "golang")),
            Map.entry("typescript", List.of("typescript", "ts")),
            Map.entry("javascript", List.of("javascript", "js")),
            Map.entry("react", List.of("react", "react.js", "reactjs")),
            Map.entry("node", List.of("node", "node.js", "nodejs")),
            Map.entry("kotlin", List.of("kotlin")),
            Map.entry("flutter", List.of("flutter")),
            Map.entry("dart", List.of("dart")),
            Map.entry("php", List.of("php")),
            Map.entry("sql", List.of("sql", "postgresql", "mysql", "oracle", "database")),
            Map.entry("mongodb", List.of("mongodb", "mongo db", "mongo")),
            Map.entry("redis", List.of("redis")),
            Map.entry("kafka", List.of("kafka", "apache kafka")),
            Map.entry("docker", List.of("docker", "container", "containers", "containerized")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s")),
            Map.entry("terraform", List.of("terraform")),
            Map.entry("microservices", List.of("microservice", "microservices")),
            Map.entry("rest_api", List.of("rest", "rest api", "restful", "api design")),
            Map.entry("jpa", List.of("jpa", "hibernate")),
            Map.entry("sap_hybris", List.of("hybris", "sap hybris", "sap commerce", "sap commerce cloud")),
            Map.entry("stripe", List.of("stripe")),
            Map.entry("shopify", List.of("shopify"))
    );
    private static final Map<String, List<String>> DOMAIN_ALIASES = Map.ofEntries(
            Map.entry("ecommerce_platforms", List.of("ecommerce", "e-commerce", "commerce", "checkout", "cart", "retail")),
            Map.entry("commerce_performance", List.of("performance", "scalability", "reliability", "high availability", "load")),
            Map.entry("payment_integrations", List.of("payment", "payments", "stripe")),
            Map.entry("omnichannel_retail", List.of("omnichannel", "inventory", "order management", "oms", "retail")),
            Map.entry("mobile_cross_platform", List.of("flutter", "dart", "mobile")),
            Map.entry("enterprise_java", List.of("java", "spring", "jpa", "hibernate")),
            Map.entry("distributed_teams", List.of("international", "global team", "multicultural", "distributed")),
            Map.entry("distributed_product_systems", List.of("distributed systems", "distributed system", "webhooks", "apis", "async integrations")),
            Map.entry("event_driven_architecture", List.of("event-driven", "event driven", "kafka", "messaging", "pub/sub")),
            Map.entry("system_design", List.of("system design", "architecture", "scalability", "reliability")),
            Map.entry("frontend_fullstack", List.of("react", "typescript", "frontend", "full stack", "full-stack")),
            Map.entry("cloud_basics", List.of("aws", "gcp", "azure", "cloud")),
            Map.entry("data_pipelines", List.of("data pipeline", "streaming", "cdc", "kafka streams", "etl")),
            Map.entry("infrastructure_as_code", List.of("terraform", "cdk", "cloudformation", "infrastructure as code", "iac")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s"))
    );

    private CandidateProfileTaxonomy() {
    }

    public static Map<String, List<String>> skillAliasesById() {
        return SKILL_ALIASES;
    }

    public static List<String> skillAliases(String skillId) {
        return SKILL_ALIASES.getOrDefault(normalizeId(skillId), List.of(normalizeId(skillId)));
    }

    public static List<String> domainAliases(String domainId) {
        return DOMAIN_ALIASES.get(normalizeId(domainId));
    }

    public static Set<String> canonicalizeSkillIds(List<String> rawSkills) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        if (rawSkills == null) {
            return Set.of();
        }
        for (String rawSkill : rawSkills) {
            String normalizedSkill = normalizeId(rawSkill);
            for (Map.Entry<String, List<String>> entry : SKILL_ALIASES.entrySet()) {
                if (matchesAnyAlias(normalizedSkill, entry.getValue())) {
                    canonical.add(entry.getKey());
                }
            }
        }
        return Set.copyOf(canonical);
    }

    public static Set<String> normalizeDomainIds(List<ProfileDomain> domains) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (domains == null) {
            return Set.of();
        }
        for (ProfileDomain domain : domains) {
            String id = normalizeId(domain.id());
            if (!id.isBlank()) {
                normalized.add(id);
            }
        }
        return Set.copyOf(normalized);
    }

    static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesAnyAlias(String text, List<String> aliases) {
        for (String alias : aliases) {
            if (text.contains(alias)) {
                return true;
            }
        }
        return false;
    }
}
