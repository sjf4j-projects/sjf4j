package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatValidatorOptimizationTest {

    @Test
    void validatesCommonFormatsWithDirectScanners() {
        assertTrue(FormatValidator._validateUuid("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse(FormatValidator._validateUuid("123e4567e89b-12d3-a456-426614174000"));
        assertFalse(FormatValidator._validateUuid("123e4567-e89b-12d3-a456-42661417400z"));

        assertTrue(FormatValidator._validateTime("23:59:60Z"));
        assertTrue(FormatValidator._validateTime("01:29:60+01:30"));
        assertTrue(FormatValidator._validateTime("12:34:56.789-02:30"));
        assertFalse(FormatValidator._validateTime("12:34:56"));
        assertFalse(FormatValidator._validateTime("12:34:56."));
        assertFalse(FormatValidator._validateTime("12:34:56.AZ"));

        assertTrue(FormatValidator._validateDateTime("1998-12-31T23:59:60Z"));
        assertTrue(FormatValidator._validateDateTime("1998-12-31t23:59:59.1+00:00"));
        assertFalse(FormatValidator._validateDateTime("1998-12-31X23:59:59Z"));
        assertFalse(FormatValidator._validateDateTime("1998-12-31T23:59:59"));
    }

    @Test
    void preservesEmailAndHostnameSemantics() {
        assertTrue(FormatValidator._validateEmailLocal("user.name+tag-1"));
        assertFalse(FormatValidator._validateEmailLocal(".user"));
        assertFalse(FormatValidator._validateEmailLocal("user..name"));
        assertFalse(FormatValidator._validateEmailLocal("user name"));
        assertTrue(FormatValidator._validateEmailLocal("\"joe bloggs\""));

        assertTrue(FormatUtil.validateHostname("example.com", false));
        assertTrue(FormatUtil.validateHostname("xn--11b2ezcw70k", false));
        assertFalse(FormatUtil.validateHostname("example..com", false));
        assertFalse(FormatUtil.validateHostname("-example.com", false));
    }

    @Test
    void validatesIpv4WithDirectOctetScanner() {
        assertTrue(FormatValidator._validateIpv4("0.0.0.0"));
        assertTrue(FormatValidator._validateIpv4("255.255.255.255"));
        assertFalse(FormatValidator._validateIpv4("256.0.0.1"));
        assertFalse(FormatValidator._validateIpv4("1.2.3"));
        assertFalse(FormatValidator._validateIpv4("1.2.3.4.5"));
        assertFalse(FormatValidator._validateIpv4("01.2.3.4"));
        assertFalse(FormatValidator._validateIpv4("1.02.3.4"));
    }

    @Test
    void preservesDurationGrammarWithDirectParser() {
        assertTrue(FormatValidator._validateDuration("P1W"));
        assertTrue(FormatValidator._validateDuration("p1y2m3dt4h5m6s"));
        assertTrue(FormatValidator._validateDuration("PT1H2M3S"));
        assertTrue(FormatValidator._validateDuration("P3DT4H"));
        assertFalse(FormatValidator._validateDuration("P"));
        assertFalse(FormatValidator._validateDuration("PT"));
        assertFalse(FormatValidator._validateDuration("P1YT"));
        assertFalse(FormatValidator._validateDuration("P1Y2D"));
        assertFalse(FormatValidator._validateDuration("PT1H2S"));
        assertFalse(FormatValidator._validateDuration("PT0.5S"));
        assertFalse(FormatValidator._validateDuration("P1WT1H"));
        assertFalse(FormatValidator._validateDuration("Ｐ1D"));
    }

    @Test
    void validatesJsonPointerSyntaxWithoutParsingSegments() {
        assertTrue(FormatValidator._validateJsonPointer(""));
        assertTrue(FormatValidator._validateJsonPointer("/a~1b/c~0d//-"));
        assertTrue(FormatValidator._validateJsonPointer("/01/002"));
        assertFalse(FormatValidator._validateJsonPointer("users"));
        assertFalse(FormatValidator._validateJsonPointer("/~"));
        assertFalse(FormatValidator._validateJsonPointer("/a~2b"));
        assertFalse(FormatValidator._validateJsonPointer("/a~xb"));
    }

    @Test
    void preservesRelativeJsonPointerBehavior() {
        assertTrue(FormatValidator._validateRelativeJsonPointer("0#"));
        assertTrue(FormatValidator._validateRelativeJsonPointer("1/foo"));
        assertTrue(FormatValidator._validateRelativeJsonPointer("12/a~1b/c~0d/0"));
        assertTrue(FormatValidator._validateRelativeJsonPointer("١/foo"));
        assertFalse(FormatValidator._validateRelativeJsonPointer(""));
        assertFalse(FormatValidator._validateRelativeJsonPointer("01#"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("0##"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("1a"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("1/~2"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("2147483648#"));
    }
}
