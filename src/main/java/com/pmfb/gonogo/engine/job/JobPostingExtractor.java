package com.pmfb.gonogo.engine.job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class JobPostingExtractor {
    private static final Set<String> JOB_KEYWORDS = Set.of(
            "engineer",
            "developer",
            "backend",
            "front",
            "frontend",
            "fullstack",
            "full stack",
            "mobile",
            "ios",
            "android",
            "platform",
            "devops",
            "sre",
            "qa",
            "quality",
            "security",
            "data scientist",
            "machine learning",
            "ml",
            "ai",
            "software"
    );
    private static final Set<String> NOISE_KEYWORDS = Set.of(
            "cookie",
            "privacy",
            "terms",
            "newsletter",
            "sign in",
            "log in",
            "contact us",
            "about us"
    );

    public List<JobPostingCandidate> extract(String html, String baseUrl, int maxItems) {
        Document doc = Jsoup.parse(html, baseUrl);
        doc.select("script,style,noscript,svg,footer,header,nav").remove();

        LinkedHashMap<String, JobPostingCandidate> candidates = new LinkedHashMap<>();

        collectFromAnchors(doc, candidates, maxItems);
        if (candidates.size() < maxItems) {
            collectFromContainers(doc, candidates, maxItems);
        }
        return List.copyOf(candidates.values());
    }

    private void collectFromAnchors(Document doc, Map<String, JobPostingCandidate> out, int maxItems) {
        Elements anchors = doc.select("a[href]");
        for (Element anchor : anchors) {
            if (out.size() >= maxItems) {
                return;
            }
            String title = normalizeWhitespace(anchor.text());
            if (!looksLikeJobTitle(title)) {
                continue;
            }

            String url = normalizeWhitespace(anchor.absUrl("href"));
            if (url.isBlank()) {
                url = normalizeWhitespace(anchor.attr("href"));
            }
            String snippet = extractSnippet(anchor, title);
            addCandidate(out, title, url, snippet);
        }
    }

    private void collectFromContainers(Document doc, Map<String, JobPostingCandidate> out, int maxItems) {
        Elements containers = doc.select("article,li,div");
        for (Element container : containers) {
            if (out.size() >= maxItems) {
                return;
            }
            String text = normalizeWhitespace(container.text());
            if (!looksLikeJobTitle(text)) {
                continue;
            }

            Element firstLink = container.selectFirst("a[href]");
            String url = firstLink != null ? normalizeWhitespace(firstLink.absUrl("href")) : "";
            String title = normalizeWhitespace(findBestTitle(container));
            if (!looksLikeJobTitle(title)) {
                title = shorten(text, 120);
            }
            String snippet = shorten(text, 420);
            addCandidate(out, title, url, snippet);
        }
    }

    private void addCandidate(Map<String, JobPostingCandidate> out, String title, String url, String snippet) {
        String key = (title + "||" + url).toLowerCase(Locale.ROOT);
        if (out.containsKey(key)) {
            return;
        }
        out.put(key, new JobPostingCandidate(title, url, snippet));
    }

    private String findBestTitle(Element container) {
        Element heading = container.selectFirst("h1,h2,h3,h4,h5,strong,b");
        if (heading != null) {
            String text = normalizeWhitespace(heading.text());
            if (!text.isBlank()) {
                return text;
            }
        }
        Element firstLink = container.selectFirst("a[href]");
        if (firstLink != null) {
            String text = normalizeWhitespace(firstLink.text());
            if (!text.isBlank()) {
                return text;
            }
        }
        return normalizeWhitespace(container.ownText());
    }

    private String extractSnippet(Element anchor, String title) {
        Element current = anchor;
        for (int i = 0; i < 4 && current != null; i++) {
            String text = normalizeWhitespace(current.text());
            if (text.length() >= 40) {
                return shorten(text, 420);
            }
            current = current.parent();
        }
        return title;
    }

    private boolean looksLikeJobTitle(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.length() < 6 || normalized.length() > 140) {
            return false;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        for (String noise : NOISE_KEYWORDS) {
            if (lowered.contains(noise)) {
                return false;
            }
        }
        for (String keyword : JOB_KEYWORDS) {
            if (lowered.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String shorten(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3).trim() + "...";
    }

    private String normalizeWhitespace(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim();
    }
}
