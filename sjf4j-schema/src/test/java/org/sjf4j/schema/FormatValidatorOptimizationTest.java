package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void validatesRfc3986UriAuthorityAndReferences() {
        // RFC 3986 allows an empty relative reference and empty authority.
        assertTrue(FormatUtil.validateUriReference(""));
        assertTrue(FormatUtil.validateUriReference("//"));
        assertTrue(FormatUtil.validateUriReference("//?query"));
        assertTrue(FormatUtil.validateUriReference("///path"));
        assertTrue(FormatUtil.validateUriReference("#fragment"));
        assertTrue(FormatUtil.validateUriReference("relative/path"));
        assertTrue(FormatUtil.validateUriReference("http://"));
        assertTrue(FormatUtil.validateUriReference("http://example.com:"));
        assertTrue(FormatUtil.validateUriReference("//user%40name@host:443/path"));
        assertTrue(FormatUtil.validateUriReference("//[::ffff:192.168.0.1]:8080/path"));

        assertFalse(FormatUtil.validateUriReference("//a@b@example.com/"));
        assertFalse(FormatUtil.validateUriReference("http://host:abc/"));
        assertFalse(FormatUtil.validateUriReference("//host:abc/path"));
        assertFalse(FormatUtil.validateUriReference("http://[::ffff:192.168.0.01]/path"));
        assertFalse(FormatUtil.validateUriReference("//[::ffff:01.2.3.4]/path"));
        assertFalse(FormatUtil.validateUriReference("http://::1/path"));
        assertFalse(FormatUtil.validateUriReference("http://a@b@host/path"));
        assertFalse(FormatUtil.validateUriReference("http://example.com/%GG"));

        // Absolute URI rules differ from URI-reference rules.
        assertTrue(FormatUtil.validateUri("mailto:someone@example.com"));
        assertTrue(FormatUtil.validateUri("http://[::ffff:192.168.0.1]"));
        assertTrue(FormatUtil.validateUri("http://user%40name@host:"));
        assertFalse(FormatUtil.validateUri(""));
        assertFalse(FormatUtil.validateUri("//host"));
        assertFalse(FormatUtil.validateUri("http://a@b@host"));
        assertFalse(FormatUtil.validateUri("http://[::ffff:192.168.0.01]"));

        // Typed URI validation must agree with string validation.
        assertTrue(FormatValidator.validateUri(URI.create(""), false));
        assertFalse(FormatValidator.validateUri(URI.create(""), true));
        assertTrue(FormatValidator.validateUri(URI.create("http://[::ffff:192.168.0.1]"), true));
        assertFalse(FormatValidator.validateUri(URI.create("http://[::ffff:192.168.0.01]"), true));
    }

    @Test
    void iriReusesAuthorityGrammarWithoutRestrictingUnicodeComponents() {
        // Every ASCII URI is also an IRI, including non-DNS reg-names.
        for (String value : new String[]{
                "", "//", "relative/path", "//host:443/path",
                "http://host:bad/path", "http://a@b@host/path",
                "http://[::ffff:192.168.0.01]/path", "mailto:user@example.org",
                "http://reg!name/path", "http://[::1]/"}) {
            assertEquals(FormatUtil.validateUriReference(value),
                    FormatUtil.validateIriReference(value), value);
            assertEquals(FormatUtil.validateUri(value),
                    FormatUtil.validateIri(value), value);
        }

        // Unicode is permitted in IRI paths and registered names.
        assertTrue(FormatUtil.validateIri("https://bücher.example/café?city=Zürich#über"));
        assertTrue(FormatUtil.validateIriReference("//bücher.example/café"));
        assertTrue(FormatUtil.validateIriReference("café/über"));
        assertTrue(FormatUtil.validateIriReference("//user%40name@bücher.example:443/über"));
        assertTrue(FormatUtil.validateIriReference("//bücher.example:/über"));
        assertTrue(FormatUtil.validateIri("https://[::ffff:192.168.0.1]/路径"));
        assertTrue(FormatUtil.validateIriReference("//[::ffff:192.168.0.1]/路径"));
        assertFalse(FormatUtil.validateUri("https://bücher.example/café"));

        // Authority syntax must not become less strict for internationalized
        // input than for ASCII URI references.
        assertFalse(FormatUtil.validateIri("https://bücher.example:abc/über"));
        assertFalse(FormatUtil.validateIriReference("//bücher.example:abc/über"));
        assertFalse(FormatUtil.validateIri("https://u@v@bücher.example/über"));
        assertFalse(FormatUtil.validateIriReference("//u@v@bücher.example/über"));
        assertFalse(FormatUtil.validateIri("https://[::ffff:192.168.0.01]/über"));
        assertFalse(FormatUtil.validateIriReference("//[::ffff:01.2.3.4]/über"));
        assertFalse(FormatUtil.validateIri("https://bücher.example/%ZZ"));
        assertFalse(FormatUtil.validateIriReference("//bücher.example/%ZZ"));
        assertFalse(FormatUtil.validateIriReference("http://bücher.example:abc/über"));
    }

    @Test
    void iriAuthorityNormalizationDoesNotInspectRelativePathQueryOrFragment() {
        // In a relative reference, a colon outside the first path segment
        // and any subsequent slashes are path content, not an authority.
        for (String reference : new String[]{
                "relative/path://a..é/",
                "relative/path://a..é/?q=ü",
                "relative?next://a..é/path",
                "relative#next://a..é/path",
                "scheme:relative/path://a..é/"
        }) {
            assertTrue(FormatUtil.validateIriReference(reference), reference);
            assertTrue(FormatUtil.convertIriToAscii(reference).equals(reference), reference);
        }
        assertTrue(FormatUtil.validateIri("scheme:relative/path://a..é/"));

        // A genuine authority at the beginning or after scheme:// must
        // still undergo IDNA validation and normalization.
        assertTrue(FormatUtil.validateIriReference("//bücher.example/path://a..é/"));
        assertTrue(FormatUtil.validateIri("scheme://bücher.example/path://a..é/"));
        assertFalse(FormatUtil.validateIriReference("//a..é/"));
        assertFalse(FormatUtil.validateIri("scheme://a..é/"));
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
        assertFalse(FormatValidator._validateRelativeJsonPointer("١/foo"));
        assertFalse(FormatValidator._validateRelativeJsonPointer(""));
        assertFalse(FormatValidator._validateRelativeJsonPointer("01#"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("0##"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("1a"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("1/~2"));
        assertTrue(FormatValidator._validateRelativeJsonPointer("2147483648#"));
        assertTrue(FormatValidator._validateRelativeJsonPointer("999999999999999999999999999999999999999999999999#"));
        assertFalse(FormatValidator._validateRelativeJsonPointer("01/foo"));
    }

    @Test
    void validatesUriTemplateLiteralsByCodePoint() {
        assertFalse(FormatValidator._validateUriTemplate("a|b"));
        assertTrue(FormatValidator._validateUriTemplate("a😀b"));
        assertTrue(FormatValidator._validateUriTemplate("a\ue000b"));
        assertTrue(FormatValidator._validateUriTemplate("a\udb80\udc00b"));
        assertTrue(FormatValidator._validateUriTemplate("a\udbc0\udc00b"));
        assertFalse(FormatValidator._validateUriTemplate("a\u0085b"));
        assertFalse(FormatValidator._validateUriTemplate("a\ud800b"));
    }

    @Test
    void acceptsUriTemplateExtensionOperators() {
        assertTrue(FormatValidator._validateUriTemplate("{=var}"));
        assertTrue(FormatValidator._validateUriTemplate("{,var}"));
        assertTrue(FormatValidator._validateUriTemplate("{!var}"));
        assertTrue(FormatValidator._validateUriTemplate("{@var}"));
        assertTrue(FormatValidator._validateUriTemplate("{|var}"));
        assertTrue(FormatValidator._validateUriTemplate("{=a,b}"));
        assertFalse(FormatValidator._validateUriTemplate("{,}"));
    }
}
