package com.pmfb.gonogo.engine.job;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class JobPostingExtractor {
    private static final int MIN_TITLE_LENGTH = 6;
    private static final int MAX_TITLE_LENGTH = 140;
    private static final Pattern NEWS_DATE_PREFIX_PATTERN = Pattern.compile(
            "^(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\\s+\\d{1,2},\\s+\\d{4}.*",
            Pattern.CASE_INSENSITIVE
    );

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
            "software",
            "manager",
            "architect",
            "staff engineer",
            "principal engineer"
    );
    private static final Set<String> JOB_CONTEXT_KEYWORDS = Set.of(
            "responsibilities",
            "requirements",
            "qualifications",
            "what you'll do",
            "role overview",
            "hiring",
            "apply"
    );
    private static final Set<String> NON_JOB_TEXT_KEYWORDS = Set.of(
            "cookie",
            "privacy",
            "terms",
            "newsletter",
            "sign in",
            "log in",
            "contact us",
            "about us",
            "sustainability",
            "our mission",
            "our values",
            "company profile",
            "investor relations",
            "newsroom",
            "in the spotlight",
            "workplace",
            "benefits",
            "faq",
            "サステナビリティ",
            "会社概要",
            "ニュース",
            "福利厚生"
    );
    private static final Set<String> JOB_URL_HINTS = Set.of(
            "/job",
            "/jobs",
            "/career",
            "/careers",
            "/recruit",
            "/positions",
            "/vacanc",
            "/openings",
            "workdayjobs",
            "greenhouse.io",
            "lever.co",
            "/apply"
    );
    private static final Set<String> NON_JOB_URL_HINTS = Set.of(
            "/sustainability",
            "/about",
            "/company",
            "/mission",
            "/vision",
            "/values",
            "/culture",
            "/workplace",
            "/benefits",
            "/faq",
            "/news",
            "/blog",
            "/press",
            "/investor",
            "/ir",
            "/privacy",
            "/terms",
            "/contact",
            "/our-latest",
            "/stories"
    );
    private static final Set<String> JOB_BOARD_LINK_TEXT_HINTS = Set.of(
            "jobs",
            "careers",
            "open positions",
            "positions",
            "vacancies",
            "recruit",
            "hiring",
            "採用",
            "求人",
            "募集"
    );
    private static final Set<String> URL_SCHEME_REJECT_PREFIXES = Set.of(
            "mailto:",
            "tel:",
            "javascript:"
    );

    public List<JobPostingCandidate> extract(String html, String baseUrl, int maxItems) {
        Document doc = sanitizedDocument(html, baseUrl);
        LinkedHashMap<String, JobPostingCandidate> candidates = new LinkedHashMap<>();

        collectFromAnchors(doc, baseUrl, candidates, maxItems);
        if (candidates.size() < maxItems) {
            collectFromContainers(doc, baseUrl, candidates, maxItems);
        }
        return List.copyOf(candidates.values());
    }

    public List<String> discoverJobBoardUrls(String html, String baseUrl, int maxItems) {
        if (maxItems < 1) {
            return List.of();
        }
        Document doc = sanitizedDocument(html, baseUrl);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        String normalizedBaseUrl = normalizeWhitespace(baseUrl);

        Elements anchors = doc.select("a[href]");
        for (Element anchor : anchors) {
            if (urls.size() >= maxItems) {
                break;
            }
            String text = normalizeWhitespace(anchor.text());
            String url = resolveUrl(anchor);
            if (url.isBlank()) {
                continue;
            }
            if (normalizeWhitespace(url).equalsIgnoreCase(normalizedBaseUrl)) {
                continue;
            }
            if (!looksLikeJobBoardLink(text, url)) {
                continue;
            }
            if (looksLikeNoiseUrl(url)) {
                continue;
            }
            urls.add(url);
        }

        return List.copyOf(urls);
    }

    private Document sanitizedDocument(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        doc.select("script,style,noscript,svg,footer,header,nav").remove();
        return doc;
    }

    private void collectFromAnchors(
            Document doc,
            String baseUrl,
            Map<String, JobPostingCandidate> out,
            int maxItems
    ) {
        Elements anchors = doc.select("a[href]");
        for (Element anchor : anchors) {
            if (out.size() >= maxItems) {
                return;
            }
            String title = normalizeWhitespace(anchor.text());
            String url = resolveUrl(anchor);
            String snippet = extractSnippet(anchor, title);
            addCandidate(out, title, url, snippet, baseUrl);
        }
    }

    private void collectFromContainers(
            Document doc,
            String baseUrl,
            Map<String, JobPostingCandidate> out,
            int maxItems
    ) {
        Elements containers = doc.select("article,li,div,section");
        for (Element container : containers) {
            if (out.size() >= maxItems) {
                return;
            }
            String text = normalizeWhitespace(container.text());
            if (text.length() < MIN_TITLE_LENGTH || text.length() > 500) {
                continue;
            }

            Element firstLink = container.selectFirst("a[href]");
            String url = firstLink != null ? resolveUrl(firstLink) : "";
            String title = normalizeWhitespace(findBestTitle(container));
            if (!looksLikeJobTitle(title)) {
                title = shorten(text, MAX_TITLE_LENGTH);
            }
            String snippet = shorten(text, 420);
            addCandidate(out, title, url, snippet, baseUrl);
        }
    }

    private void addCandidate(
            Map<String, JobPostingCandidate> out,
            String title,
            String url,
            String snippet,
            String baseUrl
    ) {
        String normalizedTitle = normalizeWhitespace(title);
        String normalizedUrl = normalizeWhitespace(url);
        String normalizedSnippet = normalizeWhitespace(snippet);
        boolean hasDirectUrl = !normalizedUrl.isBlank();
        String effectiveUrl = normalizedUrl.isBlank() ? normalizeWhitespace(baseUrl) : normalizedUrl;
        if (!isLikelyJobCandidate(normalizedTitle, effectiveUrl, normalizedSnippet, hasDirectUrl)) {
            return;
        }

        String key = (normalizedTitle + "||" + effectiveUrl).toLowerCase(Locale.ROOT);
        if (out.containsKey(key)) {
            return;
        }
        out.put(key, new JobPostingCandidate(normalizedTitle, effectiveUrl, normalizedSnippet));
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

    private boolean isLikelyJobCandidate(String title, String url, String snippet, boolean hasDirectUrl) {
        if (!looksLikeJobTitle(title)) {
            return false;
        }
        if (looksLikeNoiseUrl(url)) {
            return false;
        }
        String joinedText = normalizeWhitespace(title + " " + snippet).toLowerCase(Locale.ROOT);
        if (containsAny(joinedText, NON_JOB_TEXT_KEYWORDS)) {
            return false;
        }
        if (hasDirectUrl && hasJobUrlHint(url)) {
            return true;
        }
        return containsAny(joinedText, JOB_CONTEXT_KEYWORDS) && !url.isBlank();
    }

    private boolean looksLikeJobTitle(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.length() < MIN_TITLE_LENGTH || normalized.length() > MAX_TITLE_LENGTH) {
            return false;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (NEWS_DATE_PREFIX_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (containsAny(lowered, NON_JOB_TEXT_KEYWORDS)) {
            return false;
        }
        return containsAny(lowered, JOB_KEYWORDS);
    }

    private boolean looksLikeJobBoardLink(String text, String url) {
        String loweredText = normalizeWhitespace(text).toLowerCase(Locale.ROOT);
        if (containsAny(loweredText, JOB_BOARD_LINK_TEXT_HINTS)) {
            return true;
        }
        return hasJobUrlHint(url);
    }

    private boolean hasJobUrlHint(String url) {
        String lowered = normalizeWhitespace(url).toLowerCase(Locale.ROOT);
        for (String hint : JOB_URL_HINTS) {
            if (lowered.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeNoiseUrl(String url) {
        String lowered = normalizeWhitespace(url).toLowerCase(Locale.ROOT);
        if (lowered.isBlank()) {
            return true;
        }
        for (String prefix : URL_SCHEME_REJECT_PREFIXES) {
            if (lowered.startsWith(prefix)) {
                return true;
            }
        }
        for (String hint : NON_JOB_URL_HINTS) {
            if (lowered.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (containsKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String text, String keyword) {
        if (text.isBlank() || keyword.isBlank()) {
            return false;
        }
        if (shouldUseLiteralContains(keyword)) {
            return text.contains(keyword);
        }
        String pattern = "\\b" + Pattern.quote(keyword) + "\\b";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private boolean shouldUseLiteralContains(String keyword) {
        if (keyword.contains(" ") || keyword.contains("-") || keyword.contains("/")) {
            return true;
        }
        return keyword.chars().anyMatch(c -> c > 0x7f);
    }

    private String resolveUrl(Element anchor) {
        String url = normalizeWhitespace(anchor.absUrl("href"));
        if (url.isBlank()) {
            url = normalizeWhitespace(anchor.attr("href"));
        }
        return url;
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
