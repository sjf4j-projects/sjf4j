package org.sjf4j.schema;

import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathSyntax;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared schema helpers for string lengths, regex compatibility, and URI
 * resolution/normalization.
 */
public final class SchemaUtil {

    /**
     * Stable message codes used by schema compilation and validation output.
     * <p>
     * Codes stay intentionally coarse-grained so callers can categorize errors
     * without coupling to every individual keyword message variant.
     */
    public static final class Code {
        public static final String SCHEMA_INVALID = "schema.invalid";
        public static final String SCHEMA_RESOLVE = "schema.resolve";
        public static final String SCHEMA_CONFLICT = "schema.conflict";
        public static final String SCHEMA_URI = "schema.uri";
        public static final String SCHEMA_LOAD = "schema.load";

        public static final String VALIDATE_FALSE = "validate.false";
        public static final String VALIDATE_TYPE = "validate.type";
        public static final String VALIDATE_VALUE = "validate.value";
        public static final String VALIDATE_RANGE = "validate.range";
        public static final String VALIDATE_PATTERN = "validate.pattern";
        public static final String VALIDATE_FORMAT = "validate.format";
        public static final String VALIDATE_REQUIRED = "validate.required";
        public static final String VALIDATE_OBJECT = "validate.object";
        public static final String VALIDATE_ARRAY = "validate.array";
        public static final String VALIDATE_COMBINATOR = "validate.combinator";
        public static final String VALIDATE_GENERIC = "validate.generic";

        private Code() {}

    }

    static String validationCode(String keyword) {
        if (keyword == null) return Code.VALIDATE_GENERIC;
        switch (keyword) {
            case "false":
                return Code.VALIDATE_FALSE;
            case "type":
                return Code.VALIDATE_TYPE;
            case "const":
            case "enum":
                return Code.VALIDATE_VALUE;
            case "minimum":
            case "maximum":
            case "exclusiveMinimum":
            case "exclusiveMaximum":
            case "multipleOf":
            case "minLength":
            case "maxLength":
            case "minProperties":
            case "maxProperties":
            case "minItems":
            case "maxItems":
            case "minContains":
            case "maxContains":
                return Code.VALIDATE_RANGE;
            case "pattern":
                return Code.VALIDATE_PATTERN;
            case "format":
                return Code.VALIDATE_FORMAT;
            case "required":
            case "dependentRequired":
                return Code.VALIDATE_REQUIRED;
            case "propertyNames":
            case "additionalProperties":
            case "dependentSchemas":
            case "unevaluatedProperties":
                return Code.VALIDATE_OBJECT;
            case "uniqueItems":
            case "contains":
            case "unevaluatedItems":
                return Code.VALIDATE_ARRAY;
            case "allOf":
            case "anyOf":
            case "oneOf":
            case "not":
                return Code.VALIDATE_COMBINATOR;
            default:
                return Code.VALIDATE_GENERIC;
        }
    }

    static String formatValidationLine(ValidationMessage.Severity severity, String summary,
                                       PathSegment instancePs, PathSegment keywordPs, URI schemaUri, String keyword) {
        StringBuilder sb = new StringBuilder();
        sb.append(severity).append(' ').append(validationCode(keyword)).append(": ").append(summary);
        String keywordValue = displayPath(keywordPs);
        String keywordName = (keyword != null && (keywordValue == null || "/".equals(keywordValue))) ? keyword : null;
        _appendMeta(sb, _meta("instance", displayPath(instancePs)),
                _meta("keyword", keywordValue),
                _meta("name", keywordName),
                _meta("schema", displaySchemaUri(schemaUri)));
        return sb.toString();
    }

    static String formatSchemaLine(String code, String summary, PathSegment keywordPs, URI schemaUri) {
        return formatSchemaLine(code, summary, displayPath(keywordPs), displaySchemaUri(schemaUri));
    }

    static String formatSchemaLine(String code, String summary, String keywordPath, String schemaUri) {
        StringBuilder sb = new StringBuilder();
        sb.append("SCHEMA ").append(code).append(": ").append(summary);
        _appendMeta(sb, _meta("keyword", keywordPath), _meta("schema", schemaUri));
        return sb.toString();
    }

    private static String _meta(String key, String value) {
        if (value == null || value.isEmpty()) return null;
        return key + '=' + value;
    }

    private static void _appendMeta(StringBuilder sb, String... entries) {
        int count = 0;
        for (String entry : entries) {
            if (entry == null) continue;
            if (count++ == 0) sb.append(" (");
            else sb.append(", ");
            sb.append(entry);
        }
        if (count > 0) sb.append(')');
    }

    public static String displayPath(PathSegment ps) {
        if (ps == null) return null;
        String path = PathSyntax.rootedPointerExpr(ps);
        return path == null || path.isEmpty() ? "/" : path;
    }

    public static String displaySchemaUri(URI schemaUri) {
        if (schemaUri == null) return "<inline>";
        if ("sjf4j".equalsIgnoreCase(schemaUri.getScheme())) return "<inline>";
        return schemaUri.toString();
    }

    // Length of Unicode code points

    /**
     * Returns string length measured in Unicode code points.
     * <p>
     * This matches JSON Schema's character-counting intent more closely than a
     * raw UTF-16 {@link String#length()} count, but it is not a grapheme-cluster
     * implementation.
     */
    public static int stringIcuLength(String s) {
        if (s == null || s.isEmpty()) return 0;
        return s.codePointCount(0, s.length());
    }

    // Regex helpers

    private static final Pattern UNICODE_PROPERTY_PATTERN = Pattern.compile("\\\\([pP])\\{([^}]+)}");
    private static final String ECMA_WS_CHARS =
            "\\u0009-\\u000D" +
                    "\\u0020" +
                    "\\u00A0" +
                    "\\u1680" +
                    "\\u2000-\\u200A" +
                    "\\u2028\\u2029" +
                    "\\u202F" +
                    "\\u205F" +
                    "\\u3000" +
                    "\\uFEFF";
    private static final String ECMA_WS_CLASS = "[" + ECMA_WS_CHARS + "]";
    private static final String ECMA_NON_WS_CLASS = "[^" + ECMA_WS_CHARS + "]";


    /**
     * Compiles a regex for schema keywords such as {@code pattern}.
     * <p>
     * The helper first tries Java's regex engine directly, then retries after a
     * small normalization pass for verbose Unicode property names often used in
     * JSON Schema test suites.
     */
    public static Pattern compileRegexPattern(String pattern, String keyword) {
        Objects.requireNonNull(pattern, "pattern");
        String normalized = normalizeEcma262Regex(pattern);
        normalized = normalizeUnicodeProperties(normalized);
        try {
            return Pattern.compile(normalized);
        } catch (PatternSyntaxException e) {
            throw new SchemaException(formatSchemaLine(Code.SCHEMA_INVALID,
                    "invalid regex for keyword '" + keyword + "': " + pattern,
                    (String) null, (String) null), e);
        }
    }

    static String normalizeEcma262Regex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length() + 16);
        boolean inClass = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                // \c[a-z] -> \c[A-Z]
                if (next == 'c' && i + 2 < pattern.length()) {
                    char ctrl = pattern.charAt(i + 2);
                    if (ctrl >= 'a' && ctrl <= 'z') {
                        sb.append("\\c").append((char) (ctrl - ('a' - 'A')));
                        i += 2;
                        continue;
                    }
                }
                // \s
                if (next == 's') {
                    sb.append(inClass ? ECMA_WS_CHARS : ECMA_WS_CLASS);
                    i++;
                    continue;
                }
                // \S
                if (next == 'S' && !inClass) {
                    sb.append(ECMA_NON_WS_CLASS);
                    i++;
                    continue;
                }
                sb.append(ch).append(next);
                i++;
                continue;
            }
            if (ch == '[') inClass = true;
            else if (ch == ']' && inClass) inClass = false;
            sb.append(ch);
        }
        return sb.toString();
    }


    /**
     * Normalizes long-form Unicode property names to shorter Java-friendly
     * aliases when possible.
     */
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

    /**
     * Normalizes one Unicode property name used inside a regex escape.
     */
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
            case "digit": return "Nd";
            default:
                return prop;
        }
    }

    /**
     * Resolves a reference URI against a base schema resource URI.
     * <p>
     * Opaque bases are supported only for empty or fragment-only references,
     * which is enough for SJF4J's synthetic root URIs.
     */
    public static URI resolveUri(URI base, URI ref) {
        if (ref == null) return base;
        if (base == null || ref.isAbsolute()) return ref;
        if (base.isOpaque()) {
            String r = ref.toString();
            // "" resolves to base
            if (r.isEmpty()) return base;
            if (r.startsWith("#")) return URI.create(stripFragment(base.toString()) + r);
            throw new SchemaException(formatSchemaLine(Code.SCHEMA_RESOLVE,
                    "cannot resolve relative uri against opaque base uri: base='" + base + "', ref='" + ref + "'",
                    null, displaySchemaUri(base)));
        }

        return base.resolve(ref);
    }

    /**
     * Removes URI fragment text without further normalization.
     */
    public static String stripFragment(String s) {
        if (s == null) return s;
        int i = s.indexOf('#');
        return i >= 0 ? s.substring(0, i) : s;
    }

    /**
     * Normalizes a schema-resource registry key from a URI string.
     */
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
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
            } catch (URISyntaxException e) {
                throw new SchemaException(formatSchemaLine(Code.SCHEMA_URI,
                        "failed to normalize uri key '" + uri + "'", null, displaySchemaUri(uri)), e);
            }
        }
        return uri.toString();
    }


    /// load

    /**
     * Loads a schema from local URI.
     * <p>
     * Supported schemes: {@code file}, {@code classpath}. Returns {@code null}
     * when the target does not exist. The returned schema keeps the given URI as
     * its root retrieval URI.
     */
    public static ObjectSchema loadSchemaFromLocalUri(URI uri) {
        Objects.requireNonNull(uri, "uri");
        ObjectSchema schema;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            schema = _loadSchemaFromFile(uri.getPath());
        } else if ("classpath".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = uri.getSchemeSpecificPart();
            }
            schema = _loadSchemaFromResource(path);
        } else {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "unsupported local schema uri", null, uri.toString()));
        }
        if (schema == null) return null;
        schema.setRetrievalUri(uri);
        return schema;
    }

    /**
     * Loads a schema from a file path. Returns {@code null} when the file does
     * not exist.
     */
    private static ObjectSchema _loadSchemaFromFile(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (NoSuchFileException e) {
            return null;
        } catch (Exception e) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "failed to load schema from file", null, path), e);
        }
    }

    /**
     * Loads a schema from a classpath resource path. Returns {@code null} when
     * the resource does not exist.
     */
    private static ObjectSchema _loadSchemaFromResource(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        try (InputStream in = SchemaRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return Sjf4j.global().fromJson(in, ObjectSchema.class);
        } catch (Exception e) {
            throw new SchemaException(SchemaUtil.formatSchemaLine(SchemaUtil.Code.SCHEMA_LOAD,
                    "failed to load schema from resource", null, path), e);
        }
    }

}
