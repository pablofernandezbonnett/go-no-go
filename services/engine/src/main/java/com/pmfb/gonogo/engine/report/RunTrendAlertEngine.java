package com.pmfb.gonogo.engine.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunTrendAlertEngine {
    private static final String SEVERITY_CRITICAL = "CRITICAL";
    private static final String SEVERITY_WARNING = "WARNING";
    private static final String SEVERITY_INFO = "INFO";

    private static final String CODE_NO_GO_JUMP = "NO_GO_JUMP";
    private static final String CODE_GO_DROP = "GO_DROP";
    private static final String CODE_AVG_SCORE_DROP = "AVG_SCORE_DROP";
    private static final String CODE_LANGUAGE_FRICTION_SPIKE = "LANGUAGE_FRICTION_SPIKE";
    private static final String CODE_REPUTATION_DROP = "REPUTATION_DROP";
    private static final String CODE_UPDATED_SURGE = "UPDATED_SURGE";
    private static final String CODE_COMPANY_SCORE_DROP = "COMPANY_SCORE_DROP";
    private static final String CODE_COMPANY_NO_GO_SPIKE = "COMPANY_NO_GO_SPIKE";
    private static final String MARKDOWN_NONE_LINE = "- " + TrendAlertSinkFactory.SINK_NONE + "\n";

    private static final int NO_GO_JUMP_CRITICAL = 3;
    private static final int GO_DROP_WARNING = -3;
    private static final double AVG_SCORE_DROP_WARNING = -8.0;
    private static final double LANGUAGE_FRICTION_SPIKE_WARNING = 12.0;
    private static final double REPUTATION_DROP_WARNING = -10.0;
    private static final int UPDATED_SURGE_MIN = 10;
    private static final int UPDATED_SURGE_MULTIPLIER = 2;

    private static final int COMPANY_MIN_JOBS_FOR_ALERT = 2;
    private static final double COMPANY_SCORE_DROP_WARNING = -12.0;
    private static final double COMPANY_NO_GO_RATE_SPIKE = 0.4;
    private static final int COMPANY_NO_GO_MIN_FOR_SPIKE = 2;

    public List<TrendAlert> evaluate(RunTrendSnapshot previous, RunTrendSnapshot current) {
        if (previous == null || current == null) {
            return List.of();
        }

        List<TrendAlert> alerts = new ArrayList<>();

        int noGoDelta = current.noGoCount() - previous.noGoCount();
        if (noGoDelta >= NO_GO_JUMP_CRITICAL) {
            alerts.add(new TrendAlert(
                    SEVERITY_CRITICAL,
                    CODE_NO_GO_JUMP,
                    "NO_GO count increased by " + signed(noGoDelta) + " between runs."
            ));
        }

        int goDelta = current.goCount() - previous.goCount();
        if (goDelta <= GO_DROP_WARNING) {
            alerts.add(new TrendAlert(
                    SEVERITY_WARNING,
                    CODE_GO_DROP,
                    "GO count decreased by " + signed(goDelta) + " between runs."
            ));
        }

        double avgScoreDelta = current.averageScore() - previous.averageScore();
        if (avgScoreDelta <= AVG_SCORE_DROP_WARNING) {
            alerts.add(new TrendAlert(
                    SEVERITY_WARNING,
                    CODE_AVG_SCORE_DROP,
                    "Average score dropped by " + signedDecimal(avgScoreDelta) + "."
            ));
        }

        double languageDelta = current.averageLanguageFrictionIndex() - previous.averageLanguageFrictionIndex();
        if (languageDelta >= LANGUAGE_FRICTION_SPIKE_WARNING) {
            alerts.add(new TrendAlert(
                    SEVERITY_WARNING,
                    CODE_LANGUAGE_FRICTION_SPIKE,
                    "Average language friction increased by " + signedDecimal(languageDelta) + "."
            ));
        }

        double reputationDelta = current.averageCompanyReputationIndex() - previous.averageCompanyReputationIndex();
        if (reputationDelta <= REPUTATION_DROP_WARNING) {
            alerts.add(new TrendAlert(
                    SEVERITY_WARNING,
                    CODE_REPUTATION_DROP,
                    "Average company reputation index dropped by " + signedDecimal(reputationDelta) + "."
            ));
        }

        if (isUpdatedSurge(previous.updatedCount(), current.updatedCount())) {
            alerts.add(new TrendAlert(
                    SEVERITY_INFO,
                    CODE_UPDATED_SURGE,
                    "Updated jobs surged from " + previous.updatedCount() + " to " + current.updatedCount() + "."
            ));
        }

        alerts.addAll(companyLevelAlerts(previous, current));
        alerts.sort(
                Comparator.comparingInt((TrendAlert alert) -> severityRank(alert.severity()))
                        .thenComparing(TrendAlert::code)
        );
        return List.copyOf(alerts);
    }

    public String toMarkdown(List<TrendAlert> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Trend Alerts\n\n");
        if (alerts == null || alerts.isEmpty()) {
            sb.append(MARKDOWN_NONE_LINE);
            return sb.toString();
        }
        for (TrendAlert alert : alerts) {
            sb.append("- [")
                    .append(alert.severity())
                    .append("] ")
                    .append(alert.code())
                    .append(": ")
                    .append(alert.message())
                    .append("\n");
        }
        return sb.toString();
    }

    private boolean isUpdatedSurge(int previousUpdated, int currentUpdated) {
        if (currentUpdated < UPDATED_SURGE_MIN) {
            return false;
        }
        if (previousUpdated <= 0) {
            return true;
        }
        return currentUpdated >= previousUpdated * UPDATED_SURGE_MULTIPLIER;
    }

    private List<TrendAlert> companyLevelAlerts(RunTrendSnapshot previous, RunTrendSnapshot current) {
        Map<String, RunTrendCompanyStats> previousByCompany = byCompany(previous.companies());
        List<TrendAlert> alerts = new ArrayList<>();

        for (RunTrendCompanyStats currentCompany : current.companies()) {
            RunTrendCompanyStats previousCompany = previousByCompany.get(normalize(currentCompany.company()));
            if (previousCompany == null) {
                continue;
            }
            if (currentCompany.jobs() < COMPANY_MIN_JOBS_FOR_ALERT || previousCompany.jobs() < COMPANY_MIN_JOBS_FOR_ALERT) {
                continue;
            }

            double scoreDelta = currentCompany.averageScore() - previousCompany.averageScore();
            if (scoreDelta <= COMPANY_SCORE_DROP_WARNING) {
                alerts.add(new TrendAlert(
                        SEVERITY_WARNING,
                        CODE_COMPANY_SCORE_DROP,
                        currentCompany.company() + " average score dropped by " + signedDecimal(scoreDelta) + "."
                ));
            }

            double previousNoGoRate = noGoRate(previousCompany);
            double currentNoGoRate = noGoRate(currentCompany);
            double noGoRateDelta = currentNoGoRate - previousNoGoRate;
            if (noGoRateDelta >= COMPANY_NO_GO_RATE_SPIKE && currentCompany.noGoCount() >= COMPANY_NO_GO_MIN_FOR_SPIKE) {
                alerts.add(new TrendAlert(
                        SEVERITY_WARNING,
                        CODE_COMPANY_NO_GO_SPIKE,
                        currentCompany.company()
                                + " NO_GO rate increased by "
                                + signedDecimal(noGoRateDelta * 100.0)
                                + " points."
                ));
            }
        }

        return alerts;
    }

    private Map<String, RunTrendCompanyStats> byCompany(List<RunTrendCompanyStats> companies) {
        Map<String, RunTrendCompanyStats> byCompany = new HashMap<>();
        for (RunTrendCompanyStats company : companies) {
            byCompany.put(normalize(company.company()), company);
        }
        return byCompany;
    }

    private double noGoRate(RunTrendCompanyStats company) {
        if (company.jobs() <= 0) {
            return 0.0;
        }
        return company.noGoCount() / (double) company.jobs();
    }

    private String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private String signedDecimal(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case SEVERITY_CRITICAL -> 0;
            case SEVERITY_WARNING -> 1;
            case SEVERITY_INFO -> 2;
            default -> 3;
        };
    }
}
