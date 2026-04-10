package org.sjf4j.path;

import org.sjf4j.JsonArray;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeRegistry;
import org.sjf4j.node.Numbers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parser/formatter helpers for JSONPath and JSON Pointer.
 *
 * <p>This class centralizes all string parsing and formatting logic so
 * {@link JsonPath} can focus on execution semantics.
 */
public final class Paths {

    /**
     * Converts a segment chain to rooted debug inspect text.
     */
    public static String rootedInspect(PathSegment lastSegment) {
        if (lastSegment == null) return "";
        return inspect(linearize(lastSegment));
    }

    /**
     * Converts a segment chain to a rooted JSONPath expression.
     */
    public static String rootedPathExpr(PathSegment lastSegment) {
        if (lastSegment == null) return "";
        return toPathExpr(linearize(lastSegment));
    }

    /**
     * Converts a segment chain to a rooted JSON Pointer expression.
     */
    public static String rootedPointerExpr(PathSegment lastSegment) {
        if (lastSegment == null) return "";
        return toPointerExpr(linearize(lastSegment));
    }

    /// Inspect
    /**
     * Linearizes a segment chain into an ordered array.
     */
    public static PathSegment[] linearize(PathSegment lastSegment) {
        Objects.requireNonNull(lastSegment, "lastSegment");
        int size = 0;
        for (PathSegment p = lastSegment; p != null; p = p.parent()) size++;
        PathSegment[] segments = new PathSegment[size];
        int idx = size - 1;
        for (PathSegment p = lastSegment; p != null; p = p.parent()) {
            segments[idx--] = p;
        }
        return segments;
    }

    /**
     * Builds a debug-friendly representation of the path segments.
     */
    public static String inspect(PathSegment[] segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < segments.length; i++) {
            PathSegment ps = segments[i];
            sb.append("/");
            if (ps instanceof PathSegment.Name) {
                Class<?> clazz = ps.clazz();
                String name = ((PathSegment.Name) ps).name;
                if (clazz == null || Map.class.isAssignableFrom(clazz)) sb.append("{").append(name);
                else if (clazz == JsonObject.class) sb.append("J{").append(name);
                else {
                    sb.append("@").append(clazz.getSimpleName()).append("{");
                    if (NodeRegistry.registerPojoOrElseThrow(clazz).fields.containsKey(name)) sb.append("*");
                    sb.append(name);
                }
            } else if (ps instanceof PathSegment.Index) {
                Class<?> clazz = ps.clazz();
                int idx = ((PathSegment.Index) ps).index;
                if (clazz == null || List.class.isAssignableFrom(clazz)) sb.append("[").append(idx);
                else if (clazz == JsonArray.class) sb.append("J[").append(idx);
                else if (clazz.isArray()) sb.append("A[").append(idx);
                else if (Set.class.isAssignableFrom(clazz)) sb.append("S[").append(idx);
                else sb.append("@").append(clazz.getSimpleName()).append("[").append(idx);
            } else {
                sb.append("!").append(ps);
            }
        }
        return sb.toString();
    }

    /// JSON Pointer

    /**
     * Parses a JSON Pointer expression into path segments.
     * <p>
     * Supports RFC 6901 escaping ({@code ~0} for {@code ~}, {@code ~1} for
     * {@code /}) and RFC 6902 append token ({@code -}) when used in patch paths.
     */
    public static PathSegment[] parsePointer(String expr) {
        Objects.requireNonNull(expr, "expr");
        if (!expr.isEmpty() && !expr.startsWith("/"))
            throw new JsonException("Invalid JSON Pointer expression '" + expr + "': must start with '/'");

        Deque<PathSegment> segments = new ArrayDeque<>();
        segments.addLast(PathSegment.Root.INSTANCE);
        if (expr.isEmpty()) return segments.toArray(new PathSegment[0]);

        int len = expr.length();
        int start = 1; // skip leading '/'
        while (start <= len) {
            int end = expr.indexOf('/', start);
            if (end == -1) end = len;
            String seg = expr.substring(start, end);
            String name;

            // decode ~0/~1 and reject invalid escapes
            if (seg.indexOf('~') >= 0) {
                StringBuilder sb = new StringBuilder(seg.length());
                for (int i = 0, len2 = seg.length(); i < len2; i++) {
                    char c = seg.charAt(i);
                    if (c == '~') {
                        if (i + 1 >= seg.length()) {
                            throw new JsonException("Invalid JSON Pointer expression '" + expr + 
                                    "': invalid escape '~' in token '" + seg + "'");
                        }
                        char next = seg.charAt(i + 1);
                        if (next == '0') { sb.append('~'); i++; continue; }
                        if (next == '1') { sb.append('/'); i++; continue; }
                        throw new JsonException("Invalid JSON Pointer expression '" + expr + 
                                "': invalid escape '~" + next + "' in token '" + seg + "'");
                    }
                    sb.append(c);
                }
                name = sb.toString();
            } else {
                name = seg;
            }

            // detect numeric index
            boolean isNumber = !name.isEmpty();
            for (int i = 0, len2 = name.length(); i < len2; i++) {
                char c = name.charAt(i);
                if (c < '0' || c > '9') {
                    isNumber = false;
                    break;
                }
            }

            if (isNumber) {
                try {
                    segments.addLast(new PathSegment.Index(segments.peekLast(), null, Integer.parseInt(name), seg));
                } catch (NumberFormatException e) {
                    segments.addLast(new PathSegment.Name(segments.peekLast(), null, name));
                }
            } else if (name.equals("-")) {
                segments.addLast(new PathSegment.Append(segments.peekLast(), null));
            } else {
                segments.addLast(new PathSegment.Name(segments.peekLast(), null, name));
            }

            start = end + 1;
        }

        return segments.toArray(new PathSegment[0]);
    }

    /**
     * Formats segments as a JSON Pointer expression.
     * <p>
     * Name tokens are escaped using RFC 6901 rules. Append token ({@code -}) is
     * only valid as the last token.
     */
    public static String toPointerExpr(PathSegment[] segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder sb = new StringBuilder();

        for (int i = 0, len = segments.length; i < len; i++) {
            PathSegment token = segments[i];

            if (token instanceof PathSegment.Root) {
                if (i != 0) throw new JsonException("Root token must be the first token in JSON Pointer");
                // Root token: no output
            } else if (token instanceof PathSegment.Index) {
                sb.append('/');
                PathSegment.Index index = (PathSegment.Index) token;
                if (index.pointerToken != null) {
                    sb.append(index.pointerToken);
                } else {
                    sb.append(index.index);
                }
            } else if (token instanceof PathSegment.Append) {
                sb.append("/-");
                if (i != segments.length - 1)
                    throw new JsonException("Append token '-' can only appear at the end of a JSON Patch path");
            } else if (token instanceof PathSegment.Name) {
                sb.append('/');
                String name = ((PathSegment.Name) token).name;
                // ~0/~1
                for (int j = 0, len2 = name.length(); j < len2; j++) {
                    char c = name.charAt(j);
                    if (c == '~') {
                        sb.append("~0");
                    } else if (c == '/') {
                        sb.append("~1");
                    } else {
                        sb.append(c);
                    }
                }
            } else {
                throw new JsonException("Unsupported PathToken type: " + token.getClass().getName());
            }
        }

        return sb.toString();
    }


    ///  JSON Path

    /**
     * Formats segments as a JSONPath expression.
     */
    public static String toPathExpr(PathSegment[] segments) {
        Objects.requireNonNull(segments, "segments");
        StringBuilder sb = new StringBuilder();
        PathSegment lastPt = null;
        for (PathSegment pt : segments) {
            if (lastPt instanceof PathSegment.Descendant && pt instanceof PathSegment.Name) {
                PathSegment.Name name = (PathSegment.Name) pt;
                if (name.needQuoted()) {
                    sb.append("[").append(name.toQuoted()).append("]");
                } else {
                    sb.append(name.name);
                }
            } else {
                sb.append(pt);
            }
            lastPt = pt;
        }
        return sb.toString();
    }

    /**
     * Parses a JSONPath expression into path segments.
     * <p>
     * Supports root/current selectors, dot and bracket notation, wildcard,
     * descendant, union, slice, filter, and terminal function calls.
     */
    public static PathSegment[] parsePath(String expr) {
        if (expr == null || expr.isEmpty()) throw new JsonException("expr is empty");
        Deque<PathSegment> segments = new ArrayDeque<>();
        int i = 0;

        if (expr.charAt(i) == '$') {
            segments.addLast(PathSegment.Root.INSTANCE);
            i++;
        } else if (expr.charAt(i) == '@') {
            segments.addLast(PathSegment.Current.INSTANCE);
            i++;
        } else {
            // throw new JsonException("Must start with '$' or '@' in path '" + expr + "'");
            // Can start with empty
            segments.addLast(PathSegment.Root.INSTANCE);
        }

        while (i < expr.length()) {
            char c = expr.charAt(i);

            // Descendant ..
            if (c == '.' && i + 1 < expr.length() && expr.charAt(i + 1) == '.') {
                segments.addLast(new PathSegment.Descendant(segments.peekLast(), null));
                if (i + 2 == expr.length()) {
                    throw new JsonException("Descendant '..' cannot appear at the end.");
                } else if (expr.charAt(i + 2) == '[') {
                    i += 2;
                } else {
                    i += 1;
                }
                c = expr.charAt(i);
            }

            if (c == '[') {
                int next = _tryParseSimpleBracketToken(expr, i, segments);
                if (next >= 0) {
                    i = next;
                    continue;
                }

                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '[' in path '" + expr + "' at position " + i);

                // Scan and check
                int start = i;
                boolean hasComma = false;
                int depth = 0;
                while (i < expr.length()) {
                    char ch = expr.charAt(i);
                    if (ch == '\'' || ch == '"') {
                        // Skip quoted content
                        i++; // skip opening quote
                        while (i < expr.length() && expr.charAt(i) != ch) {
                            if (expr.charAt(i) == '\\') i++; // skip escape char
                            i++;
                        }
                        if (i >= expr.length()) {
                            throw new JsonException("Unclosed quote in path '" + expr + "' at position " + start);
                        }
                        i++; // skip closing quote
                    } else if (ch == ',') {
                        hasComma = true;
                        i++;
                    } else if (ch == '[') {
                        depth++;
                        i++;
                    } else if (ch == ']') {
                        if (depth == 0) {
                            break;
                        } else {
                            depth--;
                            i++;
                        }
                    } else {
                        i++;
                    }
                }

                if (i >= expr.length())
                    throw new JsonException("Missing closing ']' in path '" + expr + "' at position " + start);

                int contentStart = _skipWhitespace(expr, start);
                int contentEnd = _trimTrailingWhitespace(expr, contentStart, i);
                i++; // skip ]

                // Single element: could be [*], [0], [-1], ['name'], or [start:end:step]
                // Dispatch based on content type
                if (contentStart >= contentEnd) {
                    throw new JsonException("Empty content [] in path '" + expr + "' at position " + i);
                } else if (expr.charAt(contentStart) == '?') {
                    // Filter
                    int filterStart = _skipWhitespace(expr, contentStart + 1);
                    FilterExpr filterExpr = _parseFilterRange(expr, filterStart, contentEnd);
                    segments.addLast(new PathSegment.Filter(segments.peekLast(), null, filterExpr));
                } else if (hasComma) {
                    // Union: can include indices, names, and slices like [1, 'name', 2:5]
                    PathSegment[] unionTokens = _parseUnionTokens(expr, contentStart, contentEnd);
                    segments.addLast(new PathSegment.Union(segments.peekLast(), null, unionTokens));
                } else {
                    if (contentEnd == contentStart + 1 && expr.charAt(contentStart) == '*') {
                        // [*]
                        segments.addLast(new PathSegment.Wildcard(segments.peekLast(), null));
                    } else if (contentEnd == contentStart + 1 && expr.charAt(contentStart) == '+') {
                        // [+]
                        segments.addLast(new PathSegment.Append(segments.peekLast(), null));
                    } else if (expr.charAt(contentStart) == '\'' || expr.charAt(contentStart) == '"') {
                        // Single quoted name ['name'] or ["name"]
                        String name = _parseQuotedContent(expr, contentStart, contentEnd, null, "name");
                        segments.addLast(new PathSegment.Name(segments.peekLast(), null, name));
                    } else if (_containsChar(expr, contentStart, contentEnd, ':')) {
                        // Slice [start:end:step]
                        segments.addLast(_parseSlice(segments.peekLast(), expr, contentStart, contentEnd,
                                " in path '" + expr + "'"));
                    } else {
                        try {
                            // Try to parse as numeric index
                            int idx = _parseInt(expr, contentStart, contentEnd);
                            segments.addLast(new PathSegment.Index(segments.peekLast(), null, idx));
                        } catch (NumberFormatException e) {
                            String content = expr.substring(contentStart, contentEnd);
                            throw new JsonException("Invalid name or index '" + content + "' in path '" + expr + "'");
                        }
                    }
                }

            }
            else if (c == '.' || i == 0) {
                if (c == '.') i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '.' in path '" + expr + "' at position " + i);

                // Wildcard
                if (expr.charAt(i) == '*') {
                    segments.addLast(new PathSegment.Wildcard(segments.peekLast(), null));
                    i++;
                    continue;
                }

                int start = i;
                while (i < expr.length() && _isNextTokenChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new JsonException("Empty field name after '.' in path '" + expr + "' at position " + i);

                // Function
                if (i < expr.length() && expr.charAt(i) == '(') {
                    int end = _findMatchingParen(expr, i);
                    if (end < 0) {
                        throw new JsonException("Unclosed '(' in function at position " + i + " in path '" + expr + "'");
                    }
                    String funcName = expr.substring(start, i);
                    String args = expr.substring(i + 1, end); // inside (...)
                    segments.addLast(new PathSegment.Function(segments.peekLast(), null, funcName, _parseFunctionArgs(args)));
                    i = end + 1;
                    continue;
                }

                // Name
                String name = expr.substring(start, i);
                segments.addLast(new PathSegment.Name(segments.peekLast(), null, name));
            }
            else {
                throw new JsonException("Unexpected char '" + c + "' in path '" + expr + "' at position " + i);
            }
        }

        return segments.toArray(new PathSegment[0]);
    }

    private static int _tryParseSimpleBracketToken(String expr, int bracketIndex, Deque<PathSegment> segments) {
        int len = expr.length();
        int i = _skipWhitespace(expr, bracketIndex + 1);
        if (i >= len) return -1;

        char c = expr.charAt(i);

        if (c == '*') {
            int tokenEnd = _skipWhitespace(expr, i + 1);
            if (tokenEnd < len && expr.charAt(tokenEnd) == ']') {
                segments.addLast(new PathSegment.Wildcard(segments.peekLast(), null));
                return tokenEnd + 1;
            }
            return -1;
        }

        if (Character.isDigit(c)) {
            int start = i;
            do {
                i++;
            } while (i < len && Character.isDigit(expr.charAt(i)));

            int tokenEnd = _skipWhitespace(expr, i);
            if (tokenEnd < len && expr.charAt(tokenEnd) == ']') {
                try {
                    segments.addLast(new PathSegment.Index(segments.peekLast(), null,
                            Integer.parseInt(expr.substring(start, i))));
                    return tokenEnd + 1;
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }


    /// private

    /**
     * Returns true if the char can continue a dot-name token.
     */
    private static boolean _isNextTokenChar(char c) {
//        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c != '.' && c != '[' && c != '(';
    }

    private static boolean _isSimpleNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean _isSimpleNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean _isFilterPathTerminator(char c) {
        return c == ')' || c == ']' || c == ',' || c == '!'
                || c == '=' || c == '<' || c == '>'
                || c == '&' || c == '|' || Character.isWhitespace(c);
    }

    private static int _skipWhitespace(String expr, int i) {
        while (i < expr.length() && Character.isWhitespace(expr.charAt(i))) i++;
        return i;
    }

    private static int _trimTrailingWhitespace(String expr, int start, int end) {
        while (end > start && Character.isWhitespace(expr.charAt(end - 1))) end--;
        return end;
    }

    private static boolean _containsChar(String expr, int start, int end, char target) {
        for (int i = start; i < end; i++) {
            if (expr.charAt(i) == target) return true;
        }
        return false;
    }


    /**
     * Parses a slice part value or returns null when blank.
     */
    private static Integer _parseSlicePart(String part) {
        if (part == null || part.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid slice part '" + part + "'");
        }
    }

    private static Integer _parseSlicePart(String content, int start, int end) {
        start = _skipWhitespace(content, start);
        end = _trimTrailingWhitespace(content, start, end);
        if (start >= end) return null;
        try {
            return _parseInt(content, start, end);
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid slice part '" + content.substring(start, end) + "'");
        }
    }


    /**
     * Parses union elements into segment tokens.
     */
    private static PathSegment[] _parseUnionTokens(String content, int start, int end) {
        List<PathSegment> segments = new ArrayList<>();
        int i = start;
        while (i < end) {
            // Skip spaces and commas
            while (i < end && (content.charAt(i) == ' ' || content.charAt(i) == ',')) i++;
            if (i >= end) break;

            char firstChar = content.charAt(i);

            if (firstChar == '\'' || firstChar == '"') {
                // Quoted name
                int[] quotedEnd = new int[1];
                String name = _parseQuotedContent(content, i, end, quotedEnd, "union name");
                segments.add(new PathSegment.Name(null, null, name));
                i = quotedEnd[0];
                // Find next comma or end
                while (i < end && content.charAt(i) != ',') i++;
            } else if (Character.isDigit(firstChar) || firstChar == '-') {
                // Could be numeric index or slice
                int tokenStart = i;
                boolean hasColon = false;
                while (i < end && content.charAt(i) != ',') {
                    if (content.charAt(i) == ':') {
                        hasColon = true;
                    }
                    i++;
                }

                int tokenContentStart = _skipWhitespace(content, tokenStart);
                int tokenContentEnd = _trimTrailingWhitespace(content, tokenContentStart, i);
                String part = content.substring(tokenContentStart, tokenContentEnd);
                if (hasColon) {
                    // Slice
                    segments.add(_parseSlice(null, content, tokenContentStart, tokenContentEnd, " in union"));
                } else {
                    // Numeric index
                    try {
                        int idx = _parseInt(content, tokenContentStart, tokenContentEnd);
                        segments.add(new PathSegment.Index(null, null, idx));
                    } catch (NumberFormatException e) {
                        throw new JsonException("Invalid index '" + part + "' in union");
                    }
                }
            } else {
                throw new JsonException("Invalid first char '" + firstChar + "' at position " + i +
                        " in content '" + content + "'");
            }
        }
        return segments.toArray(new PathSegment[0]);
    }

    private static PathSegment.Slice _parseSlice(PathSegment parent, String content,
                                                int start, int end, String errorContext) {
        int firstColon = -1;
        int secondColon = -1;
        for (int i = start; i < end; i++) {
            if (content.charAt(i) == ':') {
                if (firstColon < 0) firstColon = i;
                else if (secondColon < 0) secondColon = i;
                else throw new JsonException("Invalid slice syntax '" + content.substring(start, end) + "'" + errorContext);
            }
        }

        Integer startIdx = _parseSlicePart(content, start, firstColon < 0 ? end : firstColon);
        Integer endIdx = firstColon < 0 ? null : _parseSlicePart(content, firstColon + 1, secondColon < 0 ? end : secondColon);
        Integer step = secondColon < 0 ? null : _parseSlicePart(content, secondColon + 1, end);
        if (step != null && step == 0) {
            throw new JsonException("Slice step cannot be 0" + errorContext);
        }
        return new PathSegment.Slice(parent, null, startIdx, endIdx, step);
    }

    private static String _parseQuotedContent(String content, int start, int limit, int[] end, String errorContext) {
        char quote = content.charAt(start);
        StringBuilder sb = new StringBuilder(Math.max(0, limit - start - 2));
        int i = start + 1;

        while (i < limit) {
            char ch = content.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= limit) {
                    throw new JsonException("Invalid escape at end of " + errorContext);
                }
                char next = content.charAt(i + 1);
                if (next == quote || next == '\\') {
                    sb.append(next);
                } else {
                    sb.append('\\').append(next);
                }
                i += 2;
            } else if (ch == quote) {
                if (end != null) end[0] = i + 1;
                return sb.toString();
            } else {
                sb.append(ch);
                i++;
            }
        }

        throw new JsonException("Missing closing quote in " + errorContext);
    }

    private static int _parseInt(String content, int start, int end) {
        if (start >= end) throw new NumberFormatException("empty");
        boolean negative = content.charAt(start) == '-';
        int i = negative ? start + 1 : start;
        if (i >= end) throw new NumberFormatException("empty");

        int value = 0;
        while (i < end) {
            char ch = content.charAt(i++);
            if (!Character.isDigit(ch)) throw new NumberFormatException("bad digit");
            value = Math.multiplyExact(value, 10);
            value = Math.addExact(value, ch - '0');
        }
        return negative ? -value : value;
    }

    /**
     * Finds the matching closing parenthesis for an expression starting at `start`,
     * automatically skipping parentheses that appear inside quoted strings.
     *
     * Supports both single-quoted and double-quoted strings.
     */
    static int _findMatchingParen(String s, int start) {
        if (start < 0 || start >= s.length() || s.charAt(start) != '(') {
            throw new JsonException("Invalid expression: start is not at '(' position");
        }

        boolean inString = false;
        char stringQuote = 0; // ' or "
        boolean escape = false;
        int depth = 0;

        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);

            // Handle escaping inside strings: \" or \'
            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            // Entering / exiting a string literal
            if (inString) {
                if (c == stringQuote) {
                    inString = false; // end string
                }
                continue; // ignore all content inside strings
            } else {
                if (c == '"' || c == '\'') {
                    inString = true;
                    stringQuote = c;
                    continue;
                }
            }

            // Handle parentheses when NOT inside strings
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        throw new JsonException("Invalid expression: no matching ')' found for '(' at position " + start);
    }

    /**
     * Parses function arguments at the last token.
     */
    static List<String> _parseFunctionArgs(String s) {
        List<String> args = new ArrayList<>();
        if (s == null || s.isEmpty()) return args;

        boolean inString = false;
        char stringQuote = 0;
        boolean escape = false;
        int depth = 0;
        int start = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (inString) {
                if (c == stringQuote) inString = false;
                continue;
            } else if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                continue;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }

        if (start < s.length()) {
            args.add(s.substring(start).trim());
        }

        return args;
    }


    /// Path Filter

    /**
     * Parses a filter expression into an AST.
     * <p>
     * Supported operators include logical ({@code &&}, {@code ||}, {@code !}),
     * comparison ({@code == != < <= > >= =~}), literals, path references,
     * regex literals, and function calls.
     */
    public static FilterExpr parseFilter(String s) {
        return _parseFilterRange(s, 0, s.length());
    }

    private static FilterExpr _parseFilterRange(String s, int start, int endExclusive) {
        int[] pos = {0};
        pos[0] = start;
        _skipWs(s, pos);
        FilterExpr expr = _parseOr(s, pos);
        _skipWs(s, pos);
        if (pos[0] != endExclusive) {
            throw new JsonException("Trailing characters at pos " + pos[0]);
        }
        return expr;
    }

    /**
     * Parses an OR expression.
     */
    private static FilterExpr _parseOr(String s, int[] pos) {
        FilterExpr left = _parseAnd(s, pos);
        while (true) {
            _skipWs(s, pos);
            if (_match(s, pos, "||")) {
                FilterExpr right = _parseAnd(s, pos);
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.OR);
            } else break;
        }
        return left;
    }

    /**
     * Parses an AND expression.
     */
    private static FilterExpr _parseAnd(String s, int[] pos) {
        FilterExpr left = _parseCompare(s, pos);
        while (true) {
            _skipWs(s, pos);
            if (_match(s, pos, "&&")) {
                FilterExpr right = _parseCompare(s, pos);
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.AND);
            } else break;
        }
        return left;
    }

    /**
     * Parses a comparison expression.
     */
    private static FilterExpr _parseCompare(String s, int[] pos) {
        FilterExpr left = _parseUnary(s, pos);
        _skipWs(s, pos);

        FilterExpr.Op op = null;

        if (_match(s, pos, "==")) op = FilterExpr.Op.EQ;
        else if (_match(s, pos, "=~")) op = FilterExpr.Op.MATCH;
        else if (_match(s, pos, "!=")) op = FilterExpr.Op.NE;
        else if (_match(s, pos, ">=")) op = FilterExpr.Op.GE;
        else if (_match(s, pos, "<=")) op = FilterExpr.Op.LE;
        else if (_match(s, pos, ">"))  op = FilterExpr.Op.GT;
        else if (_match(s, pos, "<"))  op = FilterExpr.Op.LT;

        if (op != null) {
            FilterExpr right = _parseUnary(s, pos);
            return new FilterExpr.BinaryExpr(left, right, op);
        }

        return left;
    }

    /**
     * Parses a unary expression.
     */
    private static FilterExpr _parseUnary(String s, int[] pos) {
        _skipWs(s, pos);
        if (_match(s, pos, "!")) {
            FilterExpr child = _parseUnary(s, pos);
            return new FilterExpr.UnaryExpr(false, child);
        }
        return _parsePrimary(s, pos);
    }

    /**
     * Parses a primary expression.
     */
    private static FilterExpr _parsePrimary(String s, int[] pos) {
        _skipWs(s, pos);
        char c = _peekLast(s, pos);

        // (expr)
        if (c == '(') {
            pos[0]++;
            FilterExpr expr = _parseOr(s, pos);
            _skipWs(s, pos);
            if (_peekLast(s, pos) != ')') {
                throw new JsonException("Missing ')'");
            }
            pos[0]++;
            return expr;
        }

        // String literal
        if (c == '\'' || c == '"') {
            return new FilterExpr.LiteralExpr(_parseString(s, pos));
        }

        // Number literal
        if (Character.isDigit(c) || c == '-') {
            return new FilterExpr.LiteralExpr(_parseNumber(s, pos));
        }

        // Path: @.a.b or $.x.y
        if (c == '@' || c == '$') {
            FilterExpr.PathExpr fast = _tryParseSimpleFilterPathExpr(s, pos);
            if (fast != null) return fast;
            String path = _parsePath(s, pos);
            return new FilterExpr.PathExpr(path);
        }

        // Regex: /^a/i
        if (c == '/') {
            return _parseRegex(s, pos);
        }

        if (_matchKeyword(s, pos, "null")) {
            return new FilterExpr.LiteralExpr(null);
        }
        if (_matchKeyword(s, pos, "true")) {
            return new FilterExpr.LiteralExpr(true);
        }
        if (_matchKeyword(s, pos, "false")) {
            return new FilterExpr.LiteralExpr(false);
        }

        // Function: search(@.b, 'a')
        if (_isNamePart(c)) {
            return _parseFunction(s, pos);
        }

        throw new JsonException("Unexpected char '" + c + "' at pos " + pos[0]);
    }

    /**
     * Parses a function call expression.
     */
    private static FilterExpr _parseFunction(String s, int[] pos) {
        // function name
        int start = pos[0];
        while (pos[0] < s.length() && _isNamePart(s.charAt(pos[0]))) { pos[0]++; }
        String name = s.substring(start, pos[0]);
        _skipWs(s, pos);

        if (pos[0] >= s.length() || s.charAt(pos[0]) != '(') {
            throw new JsonException("Expected '(' after function name: " + name);
        }
        pos[0]++; // '('

        List<FilterExpr> args = new ArrayList<>();
        _skipWs(s, pos);

        // empty arg list
        if (pos[0] < s.length() && s.charAt(pos[0]) == ')') {
            pos[0]++;
            return new FilterExpr.FunctionExpr(name, args);
        }

        // arguments
        while (true) {
            FilterExpr arg = _parseOr(s, pos);
            args.add(arg);
            _skipWs(s, pos);

            if (pos[0] >= s.length()) {
                throw new JsonException("Unterminated function call: " + name);
            }

            char c = s.charAt(pos[0]);
            if (c == ',') {
                pos[0]++;
                _skipWs(s, pos);
                continue;
            }
            if (c == ')') {
                pos[0]++;
                break;
            }
            throw new JsonException("Expected ',' or ')' in function call: " + name);
        }

        return new FilterExpr.FunctionExpr(name, args);
    }

    /**
     * Returns true if the char is valid in function names.
     */
    private static boolean _isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Advances the cursor over whitespace.
     */
    private static void _skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    /**
     * Matches and consumes the given operator token.
     */
    private static boolean _match(String s, int[] pos, String op) {
        if (s.startsWith(op, pos[0])) {
            pos[0] += op.length();
            return true;
        }
        return false;
    }

    private static boolean _matchKeyword(String s, int[] pos, String keyword) {
        int start = pos[0];
        if (!s.startsWith(keyword, start)) {
            return false;
        }
        int end = start + keyword.length();
        if (end < s.length() && _isNamePart(s.charAt(end))) {
            return false;
        }
        pos[0] = end;
        return true;
    }

    /**
     * Returns the current character or '\0' when out of range.
     */
    private static char _peekLast(String s, int[] pos) {
        return pos[0] < s.length() ? s.charAt(pos[0]) : '\0';
    }

    /**
     * Parses a quoted string literal.
     */
    private static String _parseString(String s, int[] pos) {
        char quote = s.charAt(pos[0]++);
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);

            if (c == quote) {
                return sb.toString();
            }

            if (c != '\\') {
                sb.append(c);
                continue;
            }

            if (pos[0] >= s.length()) {
                throw new JsonException("Unterminated escape sequence in string literal");
            }

            char next = s.charAt(pos[0]++);
            switch (next) {
                case '\\':
                case '\'':
                case '"':
                    sb.append(next);
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (pos[0] + 4 > s.length()) {
                        throw new JsonException("Invalid unicode escape in string literal");
                    }
                    String hex = s.substring(pos[0], pos[0] + 4);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new JsonException("Invalid unicode escape in string literal", e);
                    }
                    pos[0] += 4;
                    break;
                default:
                    throw new JsonException("Invalid escape '\\" + next + "' in string literal");
            }
        }

        // reached end without closing quote
        throw new JsonException("Unterminated string literal");
    }

    /**
     * Parses a numeric literal.
     */
    private static Number _parseNumber(String s, int[] pos) {
        return Numbers.parseSimpleDoubleLiteral(s, pos);
    }

    private static FilterExpr.PathExpr _tryParseSimpleFilterPathExpr(String s, int[] pos) {
        int start = pos[0];
        int i = start;
        if (i >= s.length()) return null;

        PathSegment current;
        char head = s.charAt(i++);
        if (head == '$') {
            current = PathSegment.Root.INSTANCE;
        } else if (head == '@') {
            current = PathSegment.Current.INSTANCE;
        } else {
            return null;
        }

        ArrayList<PathSegment> segments = new ArrayList<>(4);
        segments.add(current);

        while (i < s.length()) {
            char c = s.charAt(i);
            if (_isFilterPathTerminator(c)) {
                pos[0] = i;
                return new FilterExpr.PathExpr(new JsonPath(s.substring(start, i),
                        segments.toArray(new PathSegment[0])));
            }
            if (c != '.') {
                return null;
            }
            if (i + 1 >= s.length() || s.charAt(i + 1) == '.' || !_isSimpleNameStart(s.charAt(i + 1))) {
                return null;
            }

            int nameStart = i + 1;
            i += 2;
            while (i < s.length() && _isSimpleNamePart(s.charAt(i))) i++;
            current = new PathSegment.Name(current, null, s.substring(nameStart, i));
            segments.add(current);
        }

        pos[0] = i;
        return new FilterExpr.PathExpr(new JsonPath(s.substring(start, i),
                segments.toArray(new PathSegment[0])));
    }

    /**
     * Parses a path literal within a filter expression.
     */
    private static String _parsePath(String s, int[] pos) {
        int start = pos[0];
        boolean inStr = false, escape = false, inBracket = false, inParen = false;
        char quote = 0;

        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);

            if (escape) { escape = false; pos[0]++; continue; }
            if (c == '\\') { escape = true; pos[0]++; continue; }

            if (inStr) {
                if (c == quote) inStr = false;
                pos[0]++; continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true; quote = c;
                pos[0]++; continue;
            }

            if (inBracket) {
                if (c == ']') inBracket = false;
                pos[0]++; continue;
            }
            if (c == '[') {
                inBracket = true;
                pos[0]++; continue;
            }

            if (inParen) {
                if (c == ')') inParen = false;
                pos[0]++; continue;
            }
            if (c == '(') {
                inParen = true;
                pos[0]++; continue;
            }

            // allowed path chars
            if (Character.isAlphabetic(c) || Character.isDigit(c) ||
                    c == '@' || c == '$' || c == '.' || c == '_' || c == '*') {
                pos[0]++; continue;
            }

            break;
        }
        return s.substring(start, pos[0]);
    }

    /**
     * Parses a regex literal with optional flags.
     */
    @SuppressWarnings("MagicConstant")
    private static FilterExpr.RegexExpr _parseRegex(String s, int[] pos) {
        int start = pos[0];
        if (s.charAt(pos[0]++) != '/') throw new JsonException("Regex must start with '/' at pos " + pos[0]);

        boolean escape = false;
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (escape) {
                escape = false;
                pos[0]++;
                continue;
            }
            if (c == '\\') {
                escape = true;
                pos[0]++;
                continue;
            }
            if (c == '/') {
                // end of regex
                String regex = s.substring(start + 1, pos[0]);
                pos[0]++; // skip closing '/'

                // parse optional flags
                int flagsStart = pos[0];
                while (pos[0] < s.length()) {
                    char f = s.charAt(pos[0]);
                    if (f == 'i' || f == 'm' || f == 's' || f == 'u' || f == 'g') {
                        pos[0]++;
                    } else {
                        break;
                    }
                }
                String flags = s.substring(flagsStart, pos[0]);
                String source = s.substring(start, pos[0]);
                Pattern pattern = Pattern.compile(regex, _toFlags(flags));
                return new FilterExpr.RegexExpr(source, pattern);
            }
            pos[0]++;
        }

        throw new JsonException("Unterminated regex starting at pos " + start);
    }

    /**
     * Converts regex flag letters to Pattern flags.
     */
    private static int _toFlags(String flags) {
        int f = 0;
        for (char c : flags.toCharArray()) {
            switch (c) {
                case 'i': f |= Pattern.CASE_INSENSITIVE; break;
                case 'm': f |= Pattern.MULTILINE; break;
                case 's': f |= Pattern.DOTALL; break;
                case 'u': f |= Pattern.UNICODE_CASE; break;
                case 'g':
                    // Not support global
                    break;
                default:
                    throw new JsonException("Invalid regex flag '" + c + "' in JSONPath filter");
            }
        }
        return f;
    }



}
