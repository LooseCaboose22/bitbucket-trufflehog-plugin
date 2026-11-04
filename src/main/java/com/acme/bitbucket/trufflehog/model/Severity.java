package com.acme.bitbucket.trufflehog.model;

public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;

    public static Severity fromVerified(boolean verified) {
        return verified ? CRITICAL : MEDIUM;
    }

    public static Severity fromString(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
