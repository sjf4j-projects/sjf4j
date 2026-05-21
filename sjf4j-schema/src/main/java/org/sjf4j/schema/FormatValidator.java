package org.sjf4j.schema;

import org.sjf4j.path.PathSyntax;

import java.net.IDN;
import java.net.InetAddress;
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
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~+-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~+-]+)*$");
    Pattern _IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    Pattern _UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    Pattern _DURATION_PATTERN = Pattern.compile(
            "^P(?:\\d+W|(?:\\d+Y(?:\\d+M(?:\\d+D)?|\\d+M|)|\\d+M(?:\\d+D)?|\\d+D)?(?:T(?:\\d+H(?:\\d+M(?:\\d+S)?|\\d+M|)|\\d+M(?:\\d+S)?|\\d+S))?)$",
            Pattern.CASE_INSENSITIVE);
    Pattern _TIME_PATTERN = Pattern.compile(
            "^(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?([zZ]|[+-]\\d{2}:\\d{2})$");
    Pattern _DATE_TIME_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[tT](\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[zZ]|[+-]\\d{2}:\\d{2}))$");

    // ─────────────────────────────────────────────────────────────────
    // Simple: try-parse + catch → lambda field
    // ─────────────────────────────────────────────────────────────────

    FormatValidator DATE = value -> {
        return _isAsciiDigitsOnly(value) && _isValidDate(value);
    };

    FormatValidator DATETIME = FormatValidator::_validateDateTime;

    FormatValidator TIME = FormatValidator::_validateTime;

    FormatValidator IRI = FormatUtil::validateIri;

    FormatValidator URI = FormatUtil::validateUri;

    FormatValidator URI_REFERENCE = FormatUtil::validateUriReference;

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

    FormatValidator REGEX = FormatValidator::_validateRegex;

    // ─────────────────────────────────────────────────────────────────
    // Pattern-based: Pattern constant + lambda
    // ─────────────────────────────────────────────────────────────────

    FormatValidator EMAIL = FormatValidator::_validateEmail;

    FormatValidator IPV4 = value ->
            value != null && _IPV4_PATTERN.matcher(value).matches();

    FormatValidator UUID = value ->
            value != null && _UUID_PATTERN.matcher(value).matches();

    FormatValidator DURATION = value ->
            value != null && _isAsciiDigitsOnly(value) && value.length() > 1 && _DURATION_PATTERN.matcher(value).matches();

    FormatValidator HOSTNAME = value -> FormatUtil.validateHostname(value, false);

    // ─────────────────────────────────────────────────────────────────
    // Complex multi-line logic → private static method + method reference
    // ─────────────────────────────────────────────────────────────────

    FormatValidator IDN_EMAIL = FormatValidator::_validateIdnEmail;
    FormatValidator IDN_HOSTNAME = value -> FormatUtil.validateHostname(value, true);
    FormatValidator URI_TEMPLATE = FormatValidator::_validateUriTemplate;
    FormatValidator IRI_REFERENCE = FormatValidator::_validateIriReference;
    FormatValidator RELATIVE_JSON_POINTER = FormatValidator::_validateRelativeJsonPointer;

    // idn-email
    static boolean _validateIdnEmail(String value) {
        if (value == null || value.isEmpty()) return false;
        int at = _findEmailAt(value);
        if (at <= 0 || at == value.length() - 1) return false;

        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (!_validateIdnEmailLocal(local)) return false;
        try {
            String ascii = IDN.toASCII(domain);
            return FormatUtil.validateHostname(ascii, false);
        } catch (Exception e) {
            return false;
        }
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
        return FormatUtil.validateIriReference(value);
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

    static boolean _validateEmail(String value) {
        if (value == null || value.isEmpty()) return false;
        int at = _findEmailAt(value);
        if (at <= 0 || at == value.length() - 1) return false;
        return _validateEmailLocal(value.substring(0, at)) && _validateEmailDomain(value.substring(at + 1), false);
    }

    static boolean _validateEmailLocal(String local) {
        if (local.isEmpty()) return false;
        if (local.charAt(0) == '"') {
            return _isQuotedEmailLocal(local);
        }
        return _EMAIL_PATTERN.matcher(local).matches();
    }

    static boolean _validateIdnEmailLocal(String local) {
        if (local.isEmpty()) return false;
        if (local.charAt(0) == '"') return _isQuotedEmailLocal(local);
        if (local.charAt(0) == '.' || local.charAt(local.length() - 1) == '.' || local.contains("..")) return false;
        for (int i = 0; i < local.length(); i++) {
            char ch = local.charAt(i);
            if (ch == '@' || Character.isWhitespace(ch) || Character.isISOControl(ch)) return false;
        }
        return true;
    }

    static boolean _validateEmailDomain(String domain, boolean allowUnicode) {
        if (domain.isEmpty()) return false;
        if (domain.charAt(0) == '[' && domain.charAt(domain.length() - 1) == ']') {
            String literal = domain.substring(1, domain.length() - 1);
            if (literal.regionMatches(true, 0, "IPv6:", 0, 5)) return IPV6.validate(literal.substring(5));
            return IPV4.validate(literal);
        }
        return FormatUtil.validateHostname(domain, allowUnicode);
    }

    static boolean _validateTime(String value) {
        if (!_isAsciiDigitsOnly(value)) return false;
        java.util.regex.Matcher m = _TIME_PATTERN.matcher(value);
        if (!m.matches()) return false;
        int hour = Integer.parseInt(m.group(1));
        int minute = Integer.parseInt(m.group(2));
        int second = Integer.parseInt(m.group(3));
        String offset = m.group(5);
        if (hour > 23 || minute > 59) return false;
        if (second == 60) return _isValidLeapSecond(hour, minute, offset);
        if (second > 59) return false;
        return _isValidOffset(offset);
    }

    static int _findEmailAt(String value) {
        boolean quoted = false;
        boolean escaped = false;
        int at = -1;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && quoted) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (ch == '@' && !quoted) {
                if (at >= 0) return -1;
                at = i;
            }
        }
        return quoted ? -1 : at;
    }

    static boolean _isQuotedEmailLocal(String local) {
        return local.length() >= 2 && local.charAt(local.length() - 1) == '"';
    }

    static boolean _validateDateTime(String value) {
        if (!_isAsciiDigitsOnly(value)) return false;
        java.util.regex.Matcher m = _DATE_TIME_PATTERN.matcher(value);
        if (!m.matches()) return false;
        return _isValidDate(m.group(1)) && _validateTime(m.group(2));
    }

    static boolean _validateRegex(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length() - 1; i++) {
            if (value.charAt(i) != '\\') continue;
            char next = value.charAt(++i);
            if ((next >= 'A' && next <= 'Z') || (next >= 'a' && next <= 'z')) {
                if ("bBdDsSwWfnrtvpPkKcux".indexOf(next) < 0) return false;
            }
        }
        try {
            Pattern.compile(value);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    static boolean _isAsciiDigitsOnly(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch > 127) return false;
        }
        return true;
    }

    static boolean _isValidDate(String value) {
        if (value == null || value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') return false;
        int year = _parseInt(value, 0, 4);
        int month = _parseInt(value, 5, 7);
        int day = _parseInt(value, 8, 10);
        if (year < 0 || month < 1 || month > 12 || day < 1) return false;
        int maxDay;
        switch (month) {
            case 2:
                maxDay = _isLeapYear(year) ? 29 : 28;
                break;
            case 4: case 6: case 9: case 11:
                maxDay = 30;
                break;
            default:
                maxDay = 31;
        }
        return day <= maxDay;
    }

    static boolean _isLeapYear(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    static boolean _isValidOffset(String offset) {
        if (offset.length() == 1) return true;
        int hour = _parseInt(offset, 1, 3);
        int minute = _parseInt(offset, 4, 6);
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    static boolean _isValidLeapSecond(int hour, int minute, String offset) {
        if (!_isValidOffset(offset)) return false;
        if (offset.length() == 1) return hour == 23 && minute == 59;
        int offsetHour = _parseInt(offset, 1, 3);
        int offsetMinute = _parseInt(offset, 4, 6);
        int total = offsetHour * 60 + offsetMinute;
        if (offset.charAt(0) == '+') total = -total;
        int utcMinutes = hour * 60 + minute + total;
        utcMinutes %= 1440;
        if (utcMinutes < 0) utcMinutes += 1440;
        return utcMinutes == 23 * 60 + 59;
    }

    static int _parseInt(String value, int start, int end) {
        int n = 0;
        for (int i = start; i < end; i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') return -1;
            n = n * 10 + (ch - '0');
        }
        return n;
    }

}
