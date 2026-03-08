package com.pmfb.gonogo.engine.job;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RobotsTxtRules {
    private static final String USER_AGENT_KEY = "user-agent";
    private static final String ALLOW_KEY = "allow";
    private static final String DISALLOW_KEY = "disallow";
    private static final String AGENT_WILDCARD = "*";
    private static final String ROOT_PATH = "/";
    private static final String COMMENT_PREFIX = "#";

    private final Map<String, Rules> rulesByAgent;

    private RobotsTxtRules(Map<String, Rules> rulesByAgent) {
        this.rulesByAgent = rulesByAgent;
    }

    static RobotsTxtRules parse(String body) {
        if (body == null || body.isBlank()) {
            return new RobotsTxtRules(Map.of());
        }

        Map<String, MutableRules> mutable = new HashMap<>();
        List<String> currentAgents = new ArrayList<>();
        String[] lines = body.split("\\R");
        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.isBlank()) {
                currentAgents = new ArrayList<>();
                continue;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separatorIndex + 1).trim();

            if (USER_AGENT_KEY.equals(key)) {
                if (value.isBlank()) {
                    continue;
                }
                currentAgents.add(normalizeAgent(value));
                continue;
            }
            if (!(ALLOW_KEY.equals(key) || DISALLOW_KEY.equals(key))) {
                continue;
            }
            if (currentAgents.isEmpty()) {
                continue;
            }
            for (String agent : currentAgents) {
                MutableRules rules = mutable.computeIfAbsent(agent, ignored -> new MutableRules());
                if (ALLOW_KEY.equals(key)) {
                    rules.allow.add(normalizeRulePath(value));
                } else {
                    rules.disallow.add(normalizeRulePath(value));
                }
            }
        }

        Map<String, Rules> immutable = new HashMap<>();
        for (Map.Entry<String, MutableRules> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), new Rules(List.copyOf(entry.getValue().allow), List.copyOf(entry.getValue().disallow)));
        }
        return new RobotsTxtRules(Map.copyOf(immutable));
    }

    boolean isAllowed(String targetUrl, String userAgent) {
        String path = extractPath(targetUrl);
        String agent = normalizeAgent(userAgent);
        Rules rules = pickRules(agent);
        if (rules == null) {
            return true;
        }

        int allowLength = longestPrefixMatch(path, rules.allow());
        int disallowLength = longestPrefixMatch(path, rules.disallow());
        if (allowLength < 0 && disallowLength < 0) {
            return true;
        }
        if (allowLength >= disallowLength) {
            return true;
        }
        return false;
    }

    private Rules pickRules(String userAgent) {
        String normalized = normalizeAgent(userAgent);
        Rules exact = rulesByAgent.get(normalized);
        if (exact != null) {
            return exact;
        }
        return rulesByAgent.get(AGENT_WILDCARD);
    }

    private int longestPrefixMatch(String path, List<String> rules) {
        int longest = -1;
        for (String rule : rules) {
            if (rule.isBlank()) {
                continue;
            }
            if (ROOT_PATH.equals(rule)) {
                longest = Math.max(longest, 1);
                continue;
            }
            if (path.startsWith(rule)) {
                longest = Math.max(longest, rule.length());
            }
        }
        return longest;
    }

    private String extractPath(String targetUrl) {
        try {
            URI uri = new URI(targetUrl);
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                return ROOT_PATH;
            }
            return path;
        } catch (URISyntaxException e) {
            return ROOT_PATH;
        }
    }

    private static String normalizeAgent(String value) {
        if (value == null || value.isBlank()) {
            return AGENT_WILDCARD;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 0) {
            normalized = normalized.substring(0, slashIndex);
        }
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            normalized = normalized.substring(0, spaceIndex);
        }
        return normalized.isBlank() ? AGENT_WILDCARD : normalized;
    }

    private static String normalizeRulePath(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (!normalized.startsWith(ROOT_PATH)) {
            return ROOT_PATH + normalized;
        }
        return normalized;
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        int index = line.indexOf(COMMENT_PREFIX);
        if (index < 0) {
            return line;
        }
        return line.substring(0, index);
    }

    private record Rules(
            List<String> allow,
            List<String> disallow
    ) {
    }

    private static final class MutableRules {
        private final List<String> allow = new ArrayList<>();
        private final List<String> disallow = new ArrayList<>();
    }
}
