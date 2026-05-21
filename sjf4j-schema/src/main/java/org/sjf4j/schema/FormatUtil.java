package org.sjf4j.schema;

import com.ibm.icu.text.IDNA;
import java.net.IDN;
import java.net.URI;

final class FormatUtil {

    private static final IcuIdnaBridge ICU = IcuIdnaBridge.create();

    private FormatUtil() {
    }

    static boolean isIcu4jAvailable() {
        return ICU != null;
    }

    static boolean validateIri(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            if (hasUnbracketedIpv6Authority(value)) return false;
            URI uri = new URI(convertIriToAscii(value));
            return uri.isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    static boolean validateIriReference(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            if (hasUnbracketedIpv6Authority(value)) return false;
            String ascii = hasIriScheme(value) ? convertIriToAscii(value) : value;
            new URI(ascii);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean validateUri(String value) {
        if (value == null || value.isEmpty() || !isAsciiOnly(value)) return false;
        try {
            if (hasUnbracketedIpv6Authority(value)) return false;
            URI uri = new URI(value);
            return uri.isAbsolute() && hasValidUriPort(uri.getRawAuthority());
        } catch (Exception e) {
            return false;
        }
    }

    static boolean validateUriReference(String value) {
        if (value == null || value.isEmpty() || !isAsciiOnly(value)) return false;
        try {
            if (hasUnbracketedIpv6Authority(value)) return false;
            URI uri = new URI(value);
            return hasValidUriPort(uri.getRawAuthority());
        } catch (Exception e) {
            return false;
        }
    }

    static boolean validateHostname(String value, boolean allowUnicode) {
        if (value == null || value.isEmpty()) return false;
        String normalized = allowUnicode ? normalizeIdnHostnameSeparators(value) : value;
        if (normalized.isEmpty() || normalized.length() > 253 || normalized.startsWith(".") || normalized.endsWith(".")) return false;
        if (isAsciiOnly(normalized)) return validateAsciiHostname(normalized);
        String[] parts = normalized.split("\\.", -1);
        int asciiLength = 0;
        for (int i = 0; i < parts.length; i++) {
            String asciiLabel = validatedHostnameAsciiLabel(parts[i], allowUnicode);
            if (asciiLabel == null || asciiLabel.isEmpty() || asciiLabel.length() > 63) return false;
            asciiLength += asciiLabel.length();
            if (i > 0) asciiLength++;
        }
        return asciiLength <= 253;
    }

    static String convertIriToAscii(String iri) {
        int schemeEnd = iri.indexOf(':') + 1;
        String rest = iri.substring(schemeEnd);
        if (rest.startsWith("//")) {
            int pathStart = authorityEnd(rest, 2);
            String authority = pathStart >= 0 ? rest.substring(2, pathStart) : rest.substring(2);
            String pathAndQuery = pathStart >= 0 ? rest.substring(pathStart) : "";
            return iri.substring(0, schemeEnd) + "//" + authorityToAscii(authority) + pathAndQuery;
        }
        return iri;
    }

    static String normalizeIdnHostnameSeparators(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            char normalized = (ch == '\u3002' || ch == '\uFF0E' || ch == '\uFF61') ? '.' : ch;
            if (sb != null) sb.append(normalized);
            else if (normalized != ch) {
                sb = new StringBuilder(value.length());
                sb.append(value, 0, i).append(normalized);
            }
        }
        return sb == null ? value : sb.toString();
    }

    static boolean hasUnbracketedIpv6Authority(String value) {
        int start;
        if (value.startsWith("//")) start = 2;
        else {
            int scheme = value.indexOf("://");
            if (scheme < 0) return false;
            start = scheme + 3;
        }
        int end = authorityEnd(value, start);
        String authority = end >= 0 ? value.substring(start, end) : value.substring(start);
        int at = authority.indexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        if (hostPort.isEmpty() || hostPort.charAt(0) == '[') return false;
        return hostPort.indexOf(':') != hostPort.lastIndexOf(':');
    }

    static boolean hasIriScheme(String value) {
        if (value.isEmpty()) return false;
        char first = value.charAt(0);
        if ((first < 'A' || first > 'Z') && (first < 'a' || first > 'z')) return false;
        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ':') return true;
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '+' || ch == '-' || ch == '.') continue;
            return false;
        }
        return false;
    }

    private static int authorityEnd(String value, int offset) {
        for (int i = offset; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') return i;
        }
        return -1;
    }

    private static String authorityToAscii(String authority) {
        int at = authority.indexOf('@');
        String userInfo = at >= 0 ? authority.substring(0, at + 1) : "";
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        if (hostPort.isEmpty()) return authority;
        if (hostPort.charAt(0) == '[') return authority;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && hostPort.indexOf(':') == colon) {
            return userInfo + toAsciiName(hostPort.substring(0, colon)) + hostPort.substring(colon);
        }
        return userInfo + toAsciiName(hostPort);
    }

    private static String validatedHostnameAsciiLabel(String label, boolean allowUnicode) {
        if (label.isEmpty()) return null;
        if (label.startsWith("-") || label.endsWith("-")) return null;
        if (isAsciiOnly(label)) return validatedAsciiHostnameLabel(label, 0, label.length());
        if (!allowUnicode) return null;
        if (hasInvalidZeroWidthJoiner(label)) return null;
        if (ICU == null && !validateUnicodeLabelFallback(label)) return null;
        try {
            String ascii = toAsciiLabel(label);
            return validatedAsciiHostnameLabel(ascii, 0, ascii.length());
        } catch (Exception e) {
            return null;
        }
    }

    private static String validatedAsciiHostnameLabel(String label, int start, int end) {
        if (!isValidAsciiHostnameLabel(label, start, end)) return null;
        return start == 0 && end == label.length() ? label : label.substring(start, end);
    }

    private static boolean isValidAsciiHostnameLabel(String label, int start, int end) {
        if (start >= end || label.charAt(start) == '-' || label.charAt(end - 1) == '-') return false;
        for (int i = start; i < end; i++) {
            char ch = label.charAt(i);
            if (!((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-')) return false;
        }
        if (ICU != null && isAceLabel(label, start, end)) {
            String aceLabel = start == 0 && end == label.length() ? label : label.substring(start, end);
            String unicode = ICU.toUnicodeLabel(aceLabel);
            if (unicode == null || unicode.equals(aceLabel) || !validateUlabel(unicode)) return false;
        }
        return true;
    }

    private static boolean validateUnicodeLabelFallback(String label) {
        return !label.isEmpty()
                && !label.startsWith("-")
                && !label.endsWith("-")
                && !hasDoubleHyphenInThirdAndFourth(label)
                && !startsWithMark(label);
    }

    private static String toAsciiName(String value) {
        if (ICU != null) return ICU.toAsciiName(value);
        return IDN.toASCII(value);
    }

    private static String toAsciiLabel(String value) {
        if (ICU != null) return ICU.toAsciiLabel(value);
        return IDN.toASCII(value);
    }

    private static boolean isAsciiOnly(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) return false;
        }
        return true;
    }

    private static boolean hasValidUriPort(String authority) {
        if (authority == null || authority.isEmpty()) return true;
        int at = authority.indexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        if (hostPort.isEmpty()) return true;
        if (hostPort.charAt(0) == '[') {
            int end = hostPort.indexOf(']');
            if (end < 0) return false;
            if (end == hostPort.length() - 1) return true;
            if (hostPort.charAt(end + 1) != ':') return false;
            return isDigits(hostPort, end + 2);
        }
        int colon = hostPort.lastIndexOf(':');
        if (colon < 0) return true;
        if (hostPort.indexOf(':') != colon) return false;
        if (colon == 0 || colon == hostPort.length() - 1) return false;
        return isDigits(hostPort, colon + 1);
    }

    private static boolean isDigits(String value, int offset) {
        if (offset >= value.length()) return false;
        for (int i = offset; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static boolean validateAsciiHostname(String value) {
        int labelStart = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i < value.length() && value.charAt(i) != '.') continue;
            if (i == labelStart || i - labelStart > 63) return false;
            if (!isValidAsciiHostnameLabel(value, labelStart, i)) return false;
            labelStart = i + 1;
        }
        return true;
    }

    private static boolean isAceLabel(String label, int start, int end) {
        return end - start > 4 && label.regionMatches(true, start, "xn--", 0, 4);
    }

    private static boolean hasDoubleHyphenInThirdAndFourth(String label) {
        return label.length() >= 4 && label.charAt(2) == '-' && label.charAt(3) == '-';
    }

    private static boolean startsWithMark(String label) {
        int type = Character.getType(label.codePointAt(0));
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean hasInvalidZeroWidthJoiner(String label) {
        int[] cps = label.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            if (cps[i] == 0x200D && (i == 0 || !isVirama(cps[i - 1]))) return true;
        }
        return false;
    }

    private static boolean validateUlabel(String label) {
        if (label.isEmpty() || label.startsWith("-") || label.endsWith("-")) return false;
        if (hasDoubleHyphenInThirdAndFourth(label)) return false;
        if (startsWithMark(label)) return false;
        boolean hasKatakanaMiddleDot = false;
        boolean hasKanaOrHan = false;
        boolean hasArabicIndic = false;
        boolean hasExtendedArabicIndic = false;
        int[] cps = label.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            if (cp == 0x302E || cp == 0x302F || cp == 0x0640 || cp == 0x07FA) return false;
            if (cp == 0x00B7) {
                if (i == 0 || i == cps.length - 1 || cps[i - 1] != 'l' || cps[i + 1] != 'l') return false;
            } else if (cp == 0x0375) {
                if (i == cps.length - 1 || !isGreek(cps[i + 1])) return false;
            } else if (cp == 0x05F3 || cp == 0x05F4) {
                if (i == 0 || !isHebrew(cps[i - 1])) return false;
            } else if (cp == 0x30FB) {
                hasKatakanaMiddleDot = true;
            } else if (cp == 0x200D) {
                if (i == 0 || !isVirama(cps[i - 1])) return false;
            }
            if (isHiragana(cp) || (cp != 0x30FB && isKatakana(cp)) || isHan(cp)) hasKanaOrHan = true;
            if (cp >= 0x0660 && cp <= 0x0669) hasArabicIndic = true;
            if (cp >= 0x06F0 && cp <= 0x06F9) hasExtendedArabicIndic = true;
        }
        if (hasKatakanaMiddleDot && !hasKanaOrHan) return false;
        return !hasArabicIndic || !hasExtendedArabicIndic;
    }

    private static boolean isGreek(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.GREEK || block == Character.UnicodeBlock.GREEK_EXTENDED;
    }

    private static boolean isHebrew(int cp) {
        return Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.HEBREW;
    }

    private static boolean isHiragana(int cp) {
        return Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.HIRAGANA;
    }

    private static boolean isKatakana(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.KATAKANA || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS;
    }

    private static boolean isHan(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN;
    }

    private static boolean isVirama(int cp) {
        String name = Character.getName(cp);
        return name != null && name.contains("VIRAMA");
    }

    private static final class IcuIdnaBridge {
        private final IDNA uts46 = IDNA.getUTS46Instance(0);

        private IcuIdnaBridge() {
        }

        static IcuIdnaBridge create() {
            ClassLoader loader = FormatUtil.class.getClassLoader();
            try {
                if (loader != null) loader.loadClass("com.ibm.icu.text.IDNA");
                else Class.forName("com.ibm.icu.text.IDNA");
                return new IcuIdnaBridge();
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }

        String toAsciiName(String value) {
            return toAscii(value, false);
        }

        String toAsciiLabel(String value) {
            return toAscii(value, true);
        }

        String toUnicodeLabel(String value) {
            try {
                IDNA.Info info = new IDNA.Info();
                StringBuilder out = new StringBuilder(value.length() + 16);
                uts46.labelToUnicode(value, out, info);
                return info.hasErrors() ? null : out.toString();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private String toAscii(String value, boolean label) {
            try {
                IDNA.Info info = new IDNA.Info();
                StringBuilder out = new StringBuilder(value.length() + 16);
                if (label) uts46.labelToASCII(value, out, info);
                else uts46.nameToASCII(value, out, info);
                return info.hasErrors() ? null : out.toString();
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
