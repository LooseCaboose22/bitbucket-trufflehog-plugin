package com.acme.bitbucket.trufflehog.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class SeverityTest {

    @Test
    public void fromVerified_trueMeansCritical() {
        assertEquals(Severity.CRITICAL, Severity.fromVerified(true));
    }

    @Test
    public void fromVerified_falseMeansMedium() {
        assertEquals(Severity.MEDIUM, Severity.fromVerified(false));
    }

    @Test
    public void fromString_casInsensitive() {
        assertEquals(Severity.HIGH,     Severity.fromString("high"));
        assertEquals(Severity.HIGH,     Severity.fromString("HIGH"));
        assertEquals(Severity.CRITICAL, Severity.fromString("Critical"));
        assertEquals(Severity.LOW,      Severity.fromString("  low  "));
        assertEquals(Severity.INFO,     Severity.fromString("INFO"));
    }

    @Test
    public void fromString_nullReturnsMedium() {
        assertEquals(Severity.MEDIUM, Severity.fromString(null));
    }

    @Test
    public void fromString_unknownReturnsMedium() {
        assertEquals(Severity.MEDIUM, Severity.fromString("BANANA"));
        assertEquals(Severity.MEDIUM, Severity.fromString(""));
    }

    @Test
    public void enumValuesComplete() {
        Severity[] values = Severity.values();
        assertEquals(5, values.length);
    }
}
