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
    FormatValidator DATE = value -> {
        return _isValidDate(value);
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

    FormatValidator JSON_POINTER = FormatValidator::_validateJsonPointer;

    FormatValidator REGEX = FormatValidator::_validateRegex;

    // ─────────────────────────────────────────────────────────────────
    // Pattern-based: Pattern constant + lambda
    // ─────────────────────────────────────────────────────────────────

    FormatValidator EMAIL = FormatValidator::_validateEmail;

    FormatValidator IPV4 = FormatValidator::_validateIpv4;

    FormatValidator UUID = value ->
            _validateUuid(value);

    FormatValidator DURATION = FormatValidator::_validateDuration;

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
        if (value == null || value.isEmpty()) return false;
        char first = value.charAt(0);
        if (first < '0' || first > '9') {
            if (Character.isDigit(first)) return _validateRelativeJsonPointerSlow(value);
            return false;
        }
        int len = value.length();
        int idx = 1;
        int prefixValue = first - '0';
        while (idx < len) {
            char ch = value.charAt(idx);
            if (ch >= '0' && ch <= '9') {
                if (first == '0') return false;
                if (prefixValue > 214748364 || (prefixValue == 214748364 && ch > '7')) return false;
                prefixValue = prefixValue * 10 + (ch - '0');
                idx++;
                continue;
            }
            if (Character.isDigit(ch)) return _validateRelativeJsonPointerSlow(value);
            break;
        }
        if (idx == len) return true;
        if (value.charAt(idx) == '#') return idx == len - 1;
        return _validateJsonPointer(value, idx);
    }

    static boolean _validateRelativeJsonPointerSlow(String value) {
        try {
            int idx = 0;
            while (idx < value.length() && Character.isDigit(value.charAt(idx))) idx++;
            if (idx == 0) return false;
            String prefix = value.substring(0, idx);
            if (prefix.length() > 1 && prefix.charAt(0) == '0') return false;
            Integer.parseInt(prefix);
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
        if (local.charAt(0) == '.' || local.charAt(local.length() - 1) == '.') return false;
        boolean segment = false;
        for (int i = 0; i < local.length(); i++) {
            char ch = local.charAt(i);
            if (ch == '.') {
                if (!segment) return false;
                segment = false;
                continue;
            }
            if (!_isEmailAtext(ch)) return false;
            segment = true;
        }
        return segment;
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
        if (value == null || value.length() < 9) return false;
        if (value.charAt(2) != ':' || value.charAt(5) != ':') return false;
        int hour = _parseInt(value, 0, 2);
        int minute = _parseInt(value, 3, 5);
        int second = _parseInt(value, 6, 8);
        if (hour < 0 || minute < 0 || second < 0) return false;
        int offsetStart = 8;
        if (offsetStart < value.length() && value.charAt(offsetStart) == '.') {
            offsetStart++;
            int digitsStart = offsetStart;
            while (offsetStart < value.length()) {
                char ch = value.charAt(offsetStart);
                if (ch < '0' || ch > '9') break;
                offsetStart++;
            }
            if (offsetStart == digitsStart) return false;
        }
        if (offsetStart >= value.length()) return false;
        String offset = value.substring(offsetStart);
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
        if (value == null || value.length() < 20) return false;
        if (value.charAt(10) != 'T' && value.charAt(10) != 't') return false;
        return _isValidDate(value.substring(0, 10)) && _validateTime(value.substring(11));
    }

    static boolean _validateUuid(String value) {
        if (value == null || value.length() != 36) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (ch != '-') return false;
                continue;
            }
            if (!_isHexDigit(ch)) return false;
        }
        return true;
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

    static boolean _validateIpv4(String value) {
        if (value == null) return false;
        int len = value.length();
        int start = 0;
        for (int octet = 0; octet < 4; octet++) {
            if (start >= len) return false;
            int end = start;
            int number = 0;
            while (end < len) {
                char ch = value.charAt(end);
                if (ch < '0' || ch > '9') break;
                if (end - start == 3) return false;
                number = number * 10 + (ch - '0');
                end++;
            }
            if (end == start || number > 255) return false;
            if (end - start > 1 && value.charAt(start) == '0') return false;
            if (octet == 3) return end == len;
            if (end >= len || value.charAt(end) != '.') return false;
            start = end + 1;
        }
        return false;
    }

    static boolean _validateDuration(String value) {
        if (value == null || value.length() <= 1 || !_isAsciiDigitsOnly(value)) return false;
        int len = value.length();
        if (!_matchesIgnoreCase(value.charAt(0), 'P')) return false;
        int idx = 1;
        if (idx == len) return false;

        int numberEnd = _scanAsciiDigits(value, idx, len);
        if (numberEnd > idx) {
            if (numberEnd < len && _matchesIgnoreCase(value.charAt(numberEnd), 'W')) return numberEnd + 1 == len;
        }

        boolean hasComponent = false;
        idx = _parseDurationDate(value, idx, len);
        if (idx < 0) return false;
        if (idx > 1) hasComponent = true;
        if (idx < len && _matchesIgnoreCase(value.charAt(idx), 'T')) {
            idx = _parseDurationTime(value, idx + 1, len);
            if (idx < 0) return false;
            hasComponent = true;
        }
        return hasComponent && idx == len;
    }

    static int _parseDurationDate(String value, int start, int len) {
        int numberEnd = _scanAsciiDigits(value, start, len);
        if (numberEnd == start) return start;
        if (numberEnd >= len) return -1;
        char unit = value.charAt(numberEnd);
        if (_matchesIgnoreCase(unit, 'Y')) {
            int idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'M')) return -1;
            idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'D')) return -1;
            return numberEnd + 1;
        }
        if (_matchesIgnoreCase(unit, 'M')) {
            int idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'D')) return -1;
            return numberEnd + 1;
        }
        if (_matchesIgnoreCase(unit, 'D')) return numberEnd + 1;
        return -1;
    }

    static int _parseDurationTime(String value, int start, int len) {
        int numberEnd = _scanAsciiDigits(value, start, len);
        if (numberEnd == start || numberEnd >= len) return -1;
        char unit = value.charAt(numberEnd);
        if (_matchesIgnoreCase(unit, 'H')) {
            int idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'M')) return -1;
            idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'S')) return -1;
            return numberEnd + 1;
        }
        if (_matchesIgnoreCase(unit, 'M')) {
            int idx = numberEnd + 1;
            numberEnd = _scanAsciiDigits(value, idx, len);
            if (numberEnd == idx) return idx;
            if (numberEnd >= len || !_matchesIgnoreCase(value.charAt(numberEnd), 'S')) return -1;
            return numberEnd + 1;
        }
        if (_matchesIgnoreCase(unit, 'S')) return numberEnd + 1;
        return -1;
    }

    static boolean _validateJsonPointer(String value) {
        return _validateJsonPointer(value, 0);
    }

    static boolean _validateJsonPointer(String value, int start) {
        if (value == null || start < 0 || start > value.length()) return false;
        if (start == value.length()) return true;
        if (value.charAt(start) != '/') return false;
        for (int i = start + 1; i < value.length(); i++) {
            if (value.charAt(i) != '~') continue;
            if (i + 1 >= value.length()) return false;
            char escaped = value.charAt(i + 1);
            if (escaped != '0' && escaped != '1') return false;
            i++;
        }
        return true;
    }

    static boolean _isValidOffset(String offset) {
        if (offset.length() == 1) return offset.charAt(0) == 'Z' || offset.charAt(0) == 'z';
        if (offset.length() != 6 || (offset.charAt(0) != '+' && offset.charAt(0) != '-') || offset.charAt(3) != ':') return false;
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

    static boolean _isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    static int _scanAsciiDigits(String value, int start, int end) {
        int idx = start;
        while (idx < end) {
            char ch = value.charAt(idx);
            if (ch < '0' || ch > '9') break;
            idx++;
        }
        return idx;
    }

    static boolean _matchesIgnoreCase(char actual, char expectedUpper) {
        return actual == expectedUpper || actual == expectedUpper + ('a' - 'A');
    }

    static boolean _isEmailAtext(char ch) {
        return (ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '!'
                || ch == '#'
                || ch == '$'
                || ch == '%'
                || ch == '&'
                || ch == '\''
                || ch == '*'
                || ch == '+'
                || ch == '/'
                || ch == '='
                || ch == '?'
                || ch == '^'
                || ch == '_'
                || ch == '`'
                || ch == '{'
                || ch == '|'
                || ch == '}'
                || ch == '~'
                || ch == '-';
    }

}
