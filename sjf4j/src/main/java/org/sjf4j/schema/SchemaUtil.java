package org.sjf4j.schema;

import org.sjf4j.exception.SchemaException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * String length helpers for JSON Schema length keywords.
 */
public final class SchemaUtil {

    /// Length of Unicode grapheme clusters

    /**
     * Returns the length of the given string.
     */
    public static int stringIcuLength(String s) {
        if (s == null || s.isEmpty()) return 0;
        return s.codePointCount(0, s.length());
    }

    /// Regex Pattern

    private static final Pattern UNICODE_PROPERTY_PATTERN = Pattern.compile("\\\\([pP])\\{([^}]+)}");

    public static Pattern compileRegexPattern(String pattern, String keyword) {
        Objects.requireNonNull(pattern, "pattern");
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ignore) {
            String normalized = normalizeUnicodeProperties(pattern);
            try {
                return Pattern.compile(normalized);
            } catch (PatternSyntaxException e) {
                throw new SchemaException("Invalid regex for keyword '" + keyword + "': " + pattern, e);
            }
        }
    }

    public static String normalizeUnicodeProperties(String pattern) {
        Matcher matcher = UNICODE_PROPERTY_PATTERN.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String pOrP = matcher.group(1);
            String prop = matcher.group(2);
            String normalized = normalizeUnicodePropertyName(prop);
            String replacement = "\\" + pOrP + "{" + normalized + "}";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String normalizeUnicodePropertyName(String prop) {
        if (prop == null || prop.isEmpty()) return prop;
        if (prop.length() <= 3) return prop;
        switch (prop) {
            case "Letter": return "L";
            case "Lowercase_Letter": return "Ll";
            case "Uppercase_Letter": return "Lu";
            case "Titlecase_Letter": return "Lt";
            case "Modifier_Letter": return "Lm";
            case "Other_Letter": return "Lo";
            case "Number": return "N";
            case "Decimal_Number": return "Nd";
            case "Letter_Number": return "Nl";
            case "Other_Number": return "No";
            case "Mark": return "M";
            case "Nonspacing_Mark": return "Mn";
            case "Spacing_Mark": return "Mc";
            case "Enclosing_Mark": return "Me";
            case "Separator": return "Z";
            case "Space_Separator": return "Zs";
            case "Line_Separator": return "Zl";
            case "Paragraph_Separator": return "Zp";
            case "Punctuation": return "P";
            case "Connector_Punctuation": return "Pc";
            case "Dash_Punctuation": return "Pd";
            case "Open_Punctuation": return "Ps";
            case "Close_Punctuation": return "Pe";
            case "Initial_Punctuation": return "Pi";
            case "Final_Punctuation": return "Pf";
            case "Other_Punctuation": return "Po";
            case "Symbol": return "S";
            case "Math_Symbol": return "Sm";
            case "Currency_Symbol": return "Sc";
            case "Modifier_Symbol": return "Sk";
            case "Other_Symbol": return "So";
            case "Other": return "C";
            case "Control": return "Cc";
            case "Format": return "Cf";
            case "Private_Use": return "Co";
            case "Surrogate": return "Cs";
            case "Unassigned": return "Cn";
            default:
                return prop;
        }
    }

    public static URI resolveUri(URI base, URI ref) {
        if (ref == null) return base;
        if (base == null || ref.isAbsolute()) return ref;
        if (base.isOpaque()) {
            String r = ref.toString();
            // "" resolves to base
            if (r.isEmpty()) return base;
            if (r.startsWith("#")) return URI.create(stripFragment(base.toString()) + r);
            throw new SchemaException("Cannot resolve relative URI against opaque base URI: base=" + base + ", ref=" + ref);
        }

        return base.resolve(ref);
    }

    public static String stripFragment(String s) {
        int i = s.indexOf('#');
        return i >= 0 ? s.substring(0, i) : s;
    }

    public static String normalizeUriKey(String uri) {
        return normalizeUriKey(URI.create(uri));
    }

    /**
     * Normalizes registry keys for schema resources.
     * <p>
     * Fragments are stripped because fragment lookup is handled inside a
     * compiled resource. File URIs are rebuilt component-wise so equivalent
     * local resource URIs map to one key consistently.
     */
    public static String normalizeUriKey(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (uri.getFragment() != null) {
            uri = URI.create(stripFragment(uri.toString()));
        }
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            return uri.toString();
        }
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
        } catch (URISyntaxException e) {
            throw new SchemaException("Failed to normalize uri key: " + uri, e);
        }
    }


}
