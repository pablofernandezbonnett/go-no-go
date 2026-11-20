package com.pmfb.gonogo.engine.decision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SalaryRangeParser {
    private static final Pattern MILLION_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(m|million)\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,3}(?:[,.]\\d{3})+|\\d{6,9})");
    private static final Pattern ORDERED_TOKEN_PATTERN =
            Pattern.compile("(?i)(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(m|million|k)?");

    private SalaryRangeParser() {
    }

    static List<Integer> orderedValues(String salaryRange) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = ORDERED_TOKEN_PATTERN.matcher(salaryRange);
        while (matcher.find()) {
            String numberRaw = matcher.group(1);
            String suffixRaw = matcher.group(2);
            if (numberRaw == null || numberRaw.isBlank()) {
                continue;
            }

            String normalized = numberRaw.replace(",", "");
            double baseValue;
            try {
                baseValue = Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                continue;
            }

            int value = (int) Math.round(baseValue);
            String suffix = suffixRaw == null ? "" : suffixRaw.trim().toLowerCase(Locale.ROOT);
            if ("m".equals(suffix) || "million".equals(suffix)) {
                value = (int) Math.round(baseValue * 1_000_000d);
            } else if ("k".equals(suffix)) {
                value = (int) Math.round(baseValue * 1_000d);
            }
            values.add(value);
        }
        return values;
    }

    static int benchmarkYen(String salaryRange, boolean conservativeRange) {
        List<Integer> orderedValues = orderedValues(salaryRange);
        if (orderedValues.size() >= 2) {
            int lowest = orderedValues.get(0);
            int highest = orderedValues.get(orderedValues.size() - 1);
            if (lowest <= highest) {
                int ratioDivisor = conservativeRange ? 4 : 2;
                return lowest + ((highest - lowest) / ratioDivisor);
            }
        }
        return upperBoundYen(salaryRange);
    }

    static int upperBoundYen(String salaryRange) {
        if (salaryRange.isBlank()) {
            return 0;
        }

        int maxMillionBasedYen = 0;
        Matcher millionMatcher = MILLION_PATTERN.matcher(salaryRange);
        while (millionMatcher.find()) {
            double value = Double.parseDouble(millionMatcher.group(1));
            int yen = (int) Math.round(value * 1_000_000d);
            if (yen > maxMillionBasedYen) {
                maxMillionBasedYen = yen;
            }
        }
        if (maxMillionBasedYen > 0) {
            return maxMillionBasedYen;
        }

        int maxNumber = 0;
        Matcher numberMatcher = NUMBER_PATTERN.matcher(salaryRange);
        while (numberMatcher.find()) {
            String raw = numberMatcher.group(1).replace(",", "").replace(".", "");
            int value = Integer.parseInt(raw);
            if (value > maxNumber) {
                maxNumber = value;
            }
        }
        return maxNumber;
    }
}
