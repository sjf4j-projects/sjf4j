package org.sjf4j.schema;

import org.sjf4j.path.PathSyntax;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Format validators for JSON Schema {@code format} keyword.
 * <p>
 * Validators are stateless and shared as singleton instances. The
 * implementations are intentionally pragmatic and lightweight rather than full
 * RFC-complete parsers for every standard format.
 */
public interface FormatValidator {

    /**
     * Returns true when the value satisfies the format.
     */
    boolean validate(String value);

    /**
     * Returns validator implementation for a standard format name.
     * <p>
     * Unknown formats return {@link #NOOP}, matching JSON Schema's
     * annotation-only behavior for unsupported format names.
     */
    static FormatValidator of(String format) {
        switch (format) {
            case "email": return EMAIL;
            case "idn-email": return IDN_EMAIL;
            case "date": return DATE;
            case "date-time": return DATETIME;
            case "time": return TIME;
            case "duration": return DURATION;
            case "hostname": return HOSTNAME;
            case "idn-hostname": return IDN_HOSTNAME;
            case "ipv4": return IPV4;
            case "ipv6": return IPV6;
            case "uuid": return UUID;
            case "uri": return URI;
            case "uri-reference": return URI_REFERENCE;
            case "uri-template": return URI_TEMPLATE;
            case "iri": return IRI;
            case "iri-reference": return IRI_REFERENCE;
            case "json-pointer": return JSON_POINTER;
            case "relative-json-pointer": return RELATIVE_JSON_POINTER;
            case "regex": return REGEX;
            default: return NOOP;
        }
    }

    /** Unknown formats are treated as annotations and never fail validation. */
    FormatValidator NOOP = value -> true;

    // ─────────────────────────────────────────────────────────────────
    // Pattern constants (shared across validators)
    // ─────────────────────────────────────────────────────────────────
    Pattern _EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    Pattern _IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$");
    Pattern _UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    Pattern _DURATION_PATTERN = Pattern.compile(
            "^P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?$");
    Pattern _HOSTNAME_LABEL = Pattern.compile("^[a-zA-Z0-9-]+$");

    // ─────────────────────────────────────────────────────────────────
    // Simple: try-parse + catch → lambda field
    // ─────────────────────────────────────────────────────────────────

    FormatValidator DATE = value -> {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    };

    FormatValidator DATETIME = value -> {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    };

    FormatValidator TIME = value -> {
        try {
            OffsetTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    };

    FormatValidator IRI = value -> {
        if (value == null || value.isEmpty()) return false;
        try {
            String ascii = IDN.toASCII(value);
            new URI(ascii);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    FormatValidator URI = value -> {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute();
        } catch (Exception e) {
            return false;
        }
    };

    FormatValidator URI_REFERENCE = value -> {
        try {
            new URI(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    FormatValidator IPV6 = value -> {
        if (value == null || value.isEmpty() || !value.contains(":")) return false;
        try {
            InetAddress.getByName(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    FormatValidator JSON_POINTER = value -> {
        try {
            if (value == null) return false;
            PathSyntax.parsePointer(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    };

    FormatValidator REGEX = value -> {
        try {
            Pattern.compile(value);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    };

    // ─────────────────────────────────────────────────────────────────
    // Pattern-based: Pattern constant + lambda
    // ─────────────────────────────────────────────────────────────────

    FormatValidator EMAIL = value ->
            value != null && _EMAIL_PATTERN.matcher(value).matches();

    FormatValidator IPV4 = value ->
            value != null && _IPV4_PATTERN.matcher(value).matches();

    FormatValidator UUID = value ->
            value != null && _UUID_PATTERN.matcher(value).matches();

    FormatValidator DURATION = value ->
            value != null && !value.isEmpty() && _DURATION_PATTERN.matcher(value).matches();

    FormatValidator HOSTNAME = value -> {
        if (value == null || value.isEmpty()) return false;
        if (value.length() > 253 || value.startsWith(".") || value.endsWith(".")) return false;
        String[] parts = value.split("\\.");
        for (String label : parts) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (!_HOSTNAME_LABEL.matcher(label).matches()) return false;
            if (label.startsWith("-") || label.endsWith("-")) return false;
        }
        return true;
    };

    // ─────────────────────────────────────────────────────────────────
    // Complex multi-line logic → private static method + method reference
    // ─────────────────────────────────────────────────────────────────

    FormatValidator IDN_EMAIL = FormatValidator::_validateIdnEmail;
    FormatValidator IDN_HOSTNAME = FormatValidator::_validateIdnHostname;
    FormatValidator URI_TEMPLATE = FormatValidator::_validateUriTemplate;
    FormatValidator IRI_REFERENCE = FormatValidator::_validateIriReference;
    FormatValidator RELATIVE_JSON_POINTER = FormatValidator::_validateRelativeJsonPointer;

    // idn-email
    static boolean _validateIdnEmail(String value) {
        if (value == null || value.isEmpty()) return false;
        int at = value.lastIndexOf('@');
        if (at <= 0 || at == value.length() - 1) return false;

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (!_EMAIL_LOCAL_PATTERN.matcher(local).matches()) return false;
        try {
            String ascii = IDN.toASCII(domain);
            return _validateIdnHostname(ascii);
        } catch (Exception e) {
            return false;
        }
    }

    Pattern _EMAIL_LOCAL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$");

    // idn-hostname
    static boolean _validateIdnHostname(String value) {
        if (value == null || value.isEmpty()) return false;
        String ascii;
        try {
            ascii = IDN.toASCII(value);
        } catch (Exception e) {
            return false;
        }
        String[] parts = ascii.split("\\.");
        for (String label : parts) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (!_HOSTNAME_LABEL.matcher(label).matches()) return false;
            if (label.startsWith("-") || label.endsWith("-")) return false;
        }
        return true;
    }

    // uri-template
    static boolean _validateUriTemplate(String value) {
        if (value == null) return false;
        boolean inExpression = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '{') {
                if (inExpression) return false;
                inExpression = true;
            } else if (ch == '}') {
                if (!inExpression) return false;
                inExpression = false;
            }
        }
        return !inExpression;
    }

    // iri-reference
    static boolean _validateIriReference(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            int schemeIdx = value.indexOf(':');
            String ascii;
            if (schemeIdx > 0) ascii = _convertIriToAscii(value);
            else ascii = value;
            new URI(ascii);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String _convertIriToAscii(String iri) {
        int schemeEnd = iri.indexOf(':') + 1;
        String rest = iri.substring(schemeEnd);
        if (rest.startsWith("//")) {
            int pathStart = rest.indexOf('/', 2);
            String authority = pathStart >= 0 ? rest.substring(2, pathStart) : rest.substring(2);
            String pathAndQuery = pathStart >= 0 ? rest.substring(pathStart) : "";
            String hostPort;
            int atIdx = authority.indexOf('@');
            if (atIdx >= 0) {
                String userinfo = authority.substring(0, atIdx + 1);
                String host = authority.substring(atIdx + 1);
                hostPort = userinfo + IDN.toASCII(host);
            } else {
                hostPort = IDN.toASCII(authority);
            }
            return iri.substring(0, schemeEnd) + "//" + hostPort + pathAndQuery;
        }
        return iri;
    }

    // relative-json-pointer
    static boolean _validateRelativeJsonPointer(String value) {
        try {
            if (value == null) return false;
            int idx = 0;
            while (idx < value.length() && Character.isDigit(value.charAt(idx))) idx++;
            if (idx == 0) return false;
            String prefix = value.substring(0, idx);
            if (prefix.length() > 1 && prefix.charAt(0) == '0') return false;
            if (Integer.parseInt(prefix) < 0) return false;
            if (idx == value.length()) return true;
            if (value.charAt(idx) == '#') return idx == value.length() - 1;
            PathSyntax.parsePointer(value.substring(idx));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
