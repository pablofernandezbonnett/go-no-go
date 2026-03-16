package com.pmfb.gonogo.engine.config;

public record EducationItem(
        String degree,
        String institution,
        String note
) {
    public EducationItem {
        degree = degree == null ? "" : degree.trim();
        institution = institution == null ? "" : institution.trim();
        note = note == null ? "" : note.trim();
    }

    public boolean isBlank() {
        return degree.isBlank() && institution.isBlank() && note.isBlank();
    }
}
