package com.acme.bitbucket.trufflehog.model;

/**
 * Lifecycle status of a secret finding.
 * <p>
 * Stored as a string in Active Objects (see {@code AoSecretFinding.getStatus()})
 * to keep enum changes additive without schema migrations.
 */
public enum FindingStatus {
    OPEN,
    FALSE_POSITIVE,
    ACCEPTED_RISK,
    FIXED;

    public static FindingStatus fromString(String value) {
        if (value == null) {
            return OPEN;
        }
        try {
            return FindingStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
