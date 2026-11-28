package org.sjf4j.path;

import org.sjf4j.exception.NodeException;
import org.sjf4j.regex.RegexEngine;
import org.sjf4j.util.Asserts;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser/formatter helpers for JSONPath and JSON Pointer.
 *
 * <p>This class provides path parsing and formatting entry points so
 * {@link JsonPath} can focus on execution semantics.
 */
public final class PathSyntax {
    private static final long MAX_IJSON_INTEGER = 9007199254740991L;
    private static final long MAX_IJSON_INTEGER_DIV_10 = MAX_IJSON_INTEGER / 10;
    private static final int MAX_IJSON_INTEGER_MOD_10 = (int) (MAX_IJSON_INTEGER % 10);

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

    /*
     * --------------------------------------------------------------
     * Inspect
     * --------------------------------------------------------------
     */
    /**
     * Linearizes a segment chain into an ordered array.
     */
    public static PathSegment[] linearize(PathSegment lastSegment) {
        Asserts.notNull(lastSegment, "lastSegment");
        int size = 0;
        for (PathSegment p = lastSegment; p != null; p = p.parent()) size++;
        PathSegment[] segments = new PathSegment[size];
        int idx = size - 1;
        for (PathSegment p = lastSegment; p != null; p = p.parent()) {
            segments[idx--] = p;
        }
        return segments;
    }

    /*
     * --------------------------------------------------------------
     * JSON Pointer
     * --------------------------------------------------------------
     */

    /**
     * Parses a JSON Pointer expression into path segments.
     * <p>
     * Supports RFC 6901 escaping ({@code ~0} for {@code ~}, {@code ~1} for
     * {@code /}) and RFC 6902 append token ({@code -}) when used in patch paths.
     */
    public static PathSegment[] parsePointer(String expr) {
        Asserts.notNull(expr, "expr");
        if (!expr.isEmpty() && !expr.startsWith("/"))
            throw new NodeException("invalid JSON Pointer expression '" + expr + "': must start with '/'");

        PathSegment lastSegment = PathSegment.Root.INSTANCE;
        if (expr.isEmpty()) return linearize(lastSegment);

        int len = expr.length();
        int start = 1; // skip leading '/'
        while (start <= len) {
            int end = expr.indexOf('/', start);
            if (end == -1) end = len;
            if (end == start + 1 && expr.charAt(start) == '-') {
                // Only a terminal "-" can denote append. In the middle of a
                // pointer it is an ordinary object member name.
                lastSegment = end == len
                        ? new PathSegment.Append(lastSegment, "-")
                        : new PathSegment.Name(lastSegment, "-");
            } else {
                String seg = expr.substring(start, end);
                String name;

                // Decode ~0/~1 and reject invalid escapes.
                if (seg.indexOf('~') >= 0) {
                    StringBuilder sb = new StringBuilder(seg.length());
                    for (int i = 0, len2 = seg.length(); i < len2; i++) {
                        char c = seg.charAt(i);
                        if (c == '~') {
                            if (i + 1 >= seg.length()) {
                                throw new NodeException("invalid JSON Pointer expression '" + expr +
                                        "': invalid escape '~' in token '" + seg + "'");
                            }
                            char next = seg.charAt(i + 1);
                            if (next == '0') { sb.append('~'); i++; continue; }
                            if (next == '1') { sb.append('/'); i++; continue; }
                            throw new NodeException("invalid JSON Pointer expression '" + expr +
                                    "': invalid escape '~" + next + "' in token '" + seg + "'");
                        }
                        sb.append(c);
                    }
                    name = sb.toString();
                } else {
                    name = seg;
                }

                int index = _parsePointerIndex(name);
                if (index >= 0) {
                    lastSegment = new PathSegment.Index(lastSegment, index, seg);
                } else {
                    lastSegment = new PathSegment.Name(lastSegment, name);
                }
            }

            start = end + 1;
        }

        return linearize(lastSegment);
    }

    /**
     * Formats segments as a JSON Pointer expression.
     * <p>
     * Name tokens are escaped using RFC 6901 rules. Append token ({@code -}) is
     * only valid as the last token.
     */
    public static String toPointerExpr(PathSegment[] segments) {
        Asserts.notNull(segments, "segments");
        StringBuilder sb = new StringBuilder();

        for (int i = 0, len = segments.length; i < len; i++) {
            PathSegment token = segments[i];

            if (token instanceof PathSegment.Root) {
                if (i != 0) throw new NodeException("root token must be the first token in a JSON Pointer");
                // Root token: no output
            } else if (token instanceof PathSegment.Index) {
                sb.append('/');
                PathSegment.Index index = (PathSegment.Index) token;
                if (index.pointerRaw != null) {
                    sb.append(index.pointerRaw);
                } else {
                    sb.append(index.index);
                }
            } else if (token instanceof PathSegment.Append) {
                sb.append("/-");
                if (i != segments.length - 1)
                    throw new NodeException("append token '-' can only appear at the end of a JSON Patch path");
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
                throw new NodeException("unsupported path segment type '" + token.getClass().getName() + "'");
            }
        }
        return sb.toString();
    }


    /*
     * --------------------------------------------------------------
     * JSON Path
     * --------------------------------------------------------------
     */

    /**
     * Formats segments as a JSONPath expression.
     */
    public static String toPathExpr(PathSegment[] segments) {
        Asserts.notNull(segments, "segments");
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
        return parsePath(expr, RegexEngine.JDK_ENGINE);
    }

    /**
     * Parses a JSONPath using the given engine for regex literals and match/search functions.
     */
    public static PathSegment[] parsePath(String expr, RegexEngine engine) {
        Asserts.notNull(engine, "engine");
        if (expr == null || expr.isEmpty()) throw new NodeException("expression must not be empty");
        PathSegment lastSegment;
        int i = 0;

        if (expr.charAt(i) == '$') {
            lastSegment = PathSegment.Root.INSTANCE;
            i++;
        } else if (expr.charAt(i) == '@') {
            lastSegment = PathSegment.Current.INSTANCE;
            i++;
        } else {
            // throw new NodeException("Must start with '$' or '@' in path '" + expr + "'");
            // Can start with empty
            lastSegment = PathSegment.Root.INSTANCE;
        }
        while (i < expr.length()) {
            char c = expr.charAt(i);

            // Descendant ..
            if (c == '.' && i + 1 < expr.length() && expr.charAt(i + 1) == '.') {
                lastSegment = new PathSegment.Descendant(lastSegment);
                if (i + 2 == expr.length()) {
                    throw new NodeException("descendant '..' cannot appear at the end");
                } else if (expr.charAt(i + 2) == '[') {
                    i += 2;
                } else {
                    i += 1;
                }
                c = expr.charAt(i);
            }

            if (c == '[') {
                // Keep the common, compact index and slice forms out of the bracket scanner.
                // This deliberately accepts only ASCII digits and no whitespace for slices.
                int len = expr.length();
                int tokenStart = i + 1;
                if (tokenStart < len) {
                    char first = expr.charAt(tokenStart);
                    int startEnd = -1;
                    if (first >= '0' && first <= '9') {
                        startEnd = tokenStart + 1;
                        while (startEnd < len && expr.charAt(startEnd) >= '0' && expr.charAt(startEnd) <= '9') startEnd++;
                    } else if (first == '-' || first == ':') {
                        startEnd = _scanSimpleSlicePart(expr, tokenStart, len);
                    }

                    if (startEnd >= 0 && startEnd < len && expr.charAt(startEnd) == ':') {
                        int endStart = startEnd + 1;
                        int endEnd = _scanSimpleSlicePart(expr, endStart, len);
                        int stepStart = -1;
                        int stepEnd = -1;
                        int tokenEnd = endEnd;
                        if (tokenEnd >= 0 && tokenEnd < len && expr.charAt(tokenEnd) == ':') {
                            stepStart = tokenEnd + 1;
                            stepEnd = _scanSimpleSlicePart(expr, stepStart, len);
                            tokenEnd = stepEnd;
                        }
                        if (tokenEnd >= 0 && tokenEnd < len && expr.charAt(tokenEnd) == ']') {
                            Long sliceStart = tokenStart == startEnd ? null : _parseSlicePart(expr, tokenStart, startEnd);
                            Long end = endStart == endEnd ? null : _parseSlicePart(expr, endStart, endEnd);
                            Long step = stepStart < 0 || stepStart == stepEnd ? null : _parseSlicePart(expr, stepStart, stepEnd);
                            if (step != null && step == 0)
                                throw new NodeException("slice step cannot be 0 in path '" + expr + "'");
                            lastSegment = new PathSegment.Slice(lastSegment, sliceStart, end, step);
                            i = tokenEnd + 1;
                            continue;
                        }
                    } else if (first >= '0' && first <= '9') {
                        int tokenEnd = _skipWhitespace(expr, startEnd);
                        if (tokenEnd < len && expr.charAt(tokenEnd) == ']') {
                            try {
                                int index = _parseInt(expr, tokenStart, startEnd);
                                lastSegment = new PathSegment.Index(lastSegment, index);
                                i = tokenEnd + 1;
                                continue;
                            } catch (NumberFormatException ignored) {
                                // Use the general parser to produce its normal error message.
                            }
                        }
                    }
                }

                int simpleStart = _skipWhitespace(expr, i + 1);
                if (simpleStart < len && expr.charAt(simpleStart) == '*') {
                    int tokenEnd = _skipWhitespace(expr, simpleStart + 1);
                    if (tokenEnd < len && expr.charAt(tokenEnd) == ']') {
                        lastSegment = new PathSegment.Wildcard(lastSegment);
                        i = tokenEnd + 1;
                        continue;
                    }
                }

                i++;
                if (i >= expr.length())
                    throw new NodeException("unexpected end of path after '[' in '" + expr + "' at position " + i);

                // Scan and check
                int start = i;
                boolean hasComma = false;
                int unionMemberCount = 1;
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
                            throw new NodeException("unclosed quote in path '" + expr + "' at position " + start);
                        }
                        i++; // skip closing quote
                    } else if (ch == ',') {
                        hasComma = true;
                        if (depth == 0) unionMemberCount++;
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
                    throw new NodeException("missing closing ']' in path '" + expr + "' at position " + start);

                int contentStart = _skipWhitespace(expr, start);
                int contentEnd = _trimTrailingWhitespace(expr, contentStart, i);
                i++; // skip ]

                // Single element: could be [*], [0], [-1], ['name'], or [start:end:step]
                // Dispatch based on content type
                if (contentStart >= contentEnd) {
                    throw new NodeException("empty bracket content in path '" + expr + "' at position " + i);
                } else if (expr.charAt(contentStart) == '?') {
                    // Filter
                    int filterStart = _skipWhitespace(expr, contentStart + 1);
                    FilterExpr filterExpr = new FilterParser(expr, filterStart, engine).parse(contentEnd);
                    lastSegment = new PathSegment.Filter(lastSegment, filterExpr);
                } else if (hasComma) {
                    // Union: can include indices, names, and slices like [1, 'name', 2:5]
                    PathSegment[] unionTokens = _parseUnionTokens(expr, contentStart, contentEnd, unionMemberCount);
                    lastSegment = new PathSegment.Union(lastSegment, unionTokens);
                } else {
                    if (contentEnd == contentStart + 1 && expr.charAt(contentStart) == '*') {
                        // [*]
                        lastSegment = new PathSegment.Wildcard(lastSegment);
                    } else if (contentEnd == contentStart + 1 && expr.charAt(contentStart) == '+') {
                        // [+]
                        lastSegment = new PathSegment.Append(lastSegment);
                    } else if (_isParamName(expr, contentStart, contentEnd)) {
                        lastSegment = new PathSegment.Param(lastSegment,
                                expr.substring(contentStart + 1, contentEnd - 1));
                    } else if (expr.charAt(contentStart) == '\'' || expr.charAt(contentStart) == '"') {
                        // Single quoted name ['name'] or ["name"]
                        int[] quotedEnd = new int[1];
                        String name = _parseQuotedContent(expr, contentStart, contentEnd, quotedEnd, "name");
                        int trailing = quotedEnd[0];
                        while (trailing < contentEnd && Character.isWhitespace(expr.charAt(trailing))) trailing++;
                        if (trailing != contentEnd) {
                            throw new NodeException("trailing characters after quoted name in path '" + expr + "'");
                        }
                        lastSegment = new PathSegment.Name(lastSegment, name);
                    } else if (_containsChar(expr, contentStart, contentEnd, ':')) {
                        // Slice [start:end:step]
                        lastSegment = _parseSlice(lastSegment, expr, contentStart, contentEnd,
                                " in path '" + expr + "'");
                    } else {
                        try {
                            // Try to parse as numeric index
                            int idx = _parseInt(expr, contentStart, contentEnd);
                            lastSegment = new PathSegment.Index(lastSegment, idx);
                        } catch (NumberFormatException e) {
                            String content = expr.substring(contentStart, contentEnd);
                            throw new NodeException("invalid name or index '" + content + "' in path '" + expr + "'");
                        }
                    }
                }

            }
            else if (c == '.' || i == 0) {
                if (c == '.') i++;
                if (i >= expr.length())
                    throw new NodeException("unexpected end of path after '.' in '" + expr + "' at position " + i);

                // Wildcard
                if (expr.charAt(i) == '*') {
                    lastSegment = new PathSegment.Wildcard(lastSegment);
                    i++;
                    continue;
                }

                int start = i;
                while (i < expr.length() && _isNextTokenChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new NodeException("empty field name after '.' in path '" + expr + "' at position " + i);

                // Function
                if (i < expr.length() && expr.charAt(i) == '(') {
                    int end = _findMatchingParen(expr, i);
                    if (end < 0) {
                        throw new NodeException("unclosed '(' in function at position " + i + " in path '" + expr + "'");
                    }
                    String funcName = expr.substring(start, i);
                    String args = expr.substring(i + 1, end); // inside (...)
                    lastSegment = PathSegment.Function.create(lastSegment, funcName, _parseFunctionArgs(args), engine);
                    i = end + 1;
                    continue;
                }

                // Name
                String name = expr.substring(start, i);
                lastSegment = new PathSegment.Name(lastSegment, name);
            }
            else {
                throw new NodeException("unexpected character '" + c + "' in path '" + expr + "' at position " + i);
            }
        }

        return linearize(lastSegment);
    }

    /** Returns the end of an empty or ASCII integer slice part, or -1 for a dangling sign. */
    private static int _scanSimpleSlicePart(String expr, int start, int len) {
        int i = start;
        if (i < len && expr.charAt(i) == '-') {
            i++;
            if (i >= len || expr.charAt(i) < '0' || expr.charAt(i) > '9') return -1;
        }
        while (i < len) {
            char c = expr.charAt(i);
            if (c < '0' || c > '9') break;
            i++;
        }
        return i;
    }

    /** Returns the index value, or -1 when the token is not a usable pointer index. */
    private static int _parsePointerIndex(String token) {
        int len = token.length();
        if (len == 0) return -1;
        char first = token.charAt(0);
        if (first == '0') return len == 1 ? 0 : -1;
        if (first < '1' || first > '9') return -1;

        int value = first - '0';
        for (int i = 1; i < len; i++) {
            char ch = token.charAt(i);
            if (ch < '0' || ch > '9') return -1;
            int digit = ch - '0';
            if (value > 214748364 || (value == 214748364 && digit > 7)) return -1;
            value = value * 10 + digit;
        }
        return value;
    }


    /*
     * --------------------------------------------------------------
     * Private Helpers
     * --------------------------------------------------------------
     */

    /**
     * Returns true if the char can continue a dot-name token.
     */
    private static boolean _isNextTokenChar(char c) {
//        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c != '.' && c != '[' && c != '(';
    }

    static boolean _isSimpleNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    static boolean _isSimpleNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
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

    private static boolean _isParamName(String expr, int start, int end) {
        if (end <= start + 2 || expr.charAt(start) != '{' || expr.charAt(end - 1) != '}') return false;
        if (!_isSimpleNameStart(expr.charAt(start + 1))) return false;
        for (int i = start + 2; i < end - 1; i++) {
            if (!_isSimpleNamePart(expr.charAt(i))) return false;
        }
        return true;
    }


    /**
     * Parses a slice part value or returns null when blank.
     */
    private static Long _parseSlicePart(String content, int start, int end) {
        start = _skipWhitespace(content, start);
        end = _trimTrailingWhitespace(content, start, end);
        if (start >= end) return null;
        try {
            return _parseSliceLong(content, start, end);
        } catch (NumberFormatException e) {
            throw new NodeException("invalid slice part '" + content.substring(start, end) + "'");
        }
    }


    /**
     * Parses union elements into segment tokens.
     */
    private static PathSegment[] _parseUnionTokens(String content, int start, int end, int memberCount) {
        PathSegment[] segments = new PathSegment[memberCount];
        int segmentCount = 0;
        int i = start;
        while (i < end) {
            i = _skipWhitespace(content, i);
            if (i >= end) break;

            char firstChar = content.charAt(i);

            if (firstChar == '\'' || firstChar == '"') {
                // Quoted name
                int[] quotedEnd = new int[1];
                String name = _parseQuotedContent(content, i, end, quotedEnd, "union name");
                segments[segmentCount++] = new PathSegment.Name(null, name);
                i = _skipWhitespace(content, quotedEnd[0]);
                if (i < end && content.charAt(i) != ',') {
                    throw new NodeException("trailing characters after quoted union name in content '" + content + "'");
                }
            } else if ((firstChar >= '0' && firstChar <= '9') || firstChar == '-' || firstChar == ':') {
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
                if (hasColon) {
                    // Slice
                    segments[segmentCount++] = _parseSlice(null, content, tokenContentStart, tokenContentEnd, " in union");
                } else {
                    // Numeric index
                    try {
                        int idx = _parseInt(content, tokenContentStart, tokenContentEnd);
                        segments[segmentCount++] = new PathSegment.Index(null, idx);
                    } catch (NumberFormatException e) {
                        throw new NodeException("invalid index '" + content.substring(tokenContentStart, tokenContentEnd) + "' in union");
                    }
                }
            } else {
                throw new NodeException("invalid first character '" + firstChar + "' at position " + i +
                        " in content '" + content + "'");
            }

            i = _skipWhitespace(content, i);
            if (i >= end) break;
            if (content.charAt(i) != ',') {
                throw new NodeException("expected ',' after union member in content '" + content + "'");
            }
            i = _skipWhitespace(content, i + 1);
            if (i >= end) {
                throw new NodeException("missing union member after ',' in content '" + content + "'");
            }
        }
        if (segmentCount != memberCount) {
            throw new NodeException("invalid union member count in content '" + content + "'");
        }
        return segments;
    }

    private static PathSegment.Slice _parseSlice(PathSegment parent, String content,
                                                 int start, int end, String errorContext) {
        int firstColon = -1;
        int secondColon = -1;
        for (int i = start; i < end; i++) {
            if (content.charAt(i) == ':') {
                if (firstColon < 0) firstColon = i;
                else if (secondColon < 0) secondColon = i;
                else throw new NodeException("invalid slice syntax '" + content.substring(start, end) + "'" + errorContext);
            }
        }

        Long startIdx = _parseSlicePart(content, start, firstColon < 0 ? end : firstColon);
        Long endIdx = firstColon < 0 ? null : _parseSlicePart(content, firstColon + 1, secondColon < 0 ? end : secondColon);
        Long step = secondColon < 0 ? null : _parseSlicePart(content, secondColon + 1, end);
        if (step != null && step == 0) {
            throw new NodeException("slice step cannot be 0" + errorContext);
        }
        return new PathSegment.Slice(parent, startIdx, endIdx, step);
    }

    private static String _parseQuotedContent(String content, int start, int limit, int[] end, String errorContext) {
        char quote = content.charAt(start);
        int i = start + 1;
        int copyStart = i;
        StringBuilder sb = null;

        while (i < limit) {
            char ch = content.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= limit) {
                    throw new NodeException("invalid escape sequence at the end of " + errorContext);
                }
                char next = content.charAt(i + 1);
                if (next == quote || next == '\\') {
                    if (sb == null) sb = new StringBuilder(Math.max(0, limit - start - 2));
                    sb.append(content, copyStart, i);
                    sb.append(next);
                    copyStart = i + 2;
                } else {
                }
                i += 2;
            } else if (ch == quote) {
                if (end != null) end[0] = i + 1;
                if (sb == null) return content.substring(start + 1, i);
                return sb.append(content, copyStart, i).toString();
            } else {
                i++;
            }
        }

        throw new NodeException("missing closing quote in " + errorContext);
    }

    private static int _parseInt(String content, int start, int end) {
        if (start >= end) throw new NumberFormatException("empty");
        boolean negative = content.charAt(start) == '-';
        int i = negative ? start + 1 : start;
        if (i >= end) throw new NumberFormatException("empty");

        int limit = negative ? Integer.MIN_VALUE : -Integer.MAX_VALUE;
        int multMin = limit / 10;
        int value = 0;
        while (i < end) {
            char ch = content.charAt(i++);
            if (ch < '0' || ch > '9') throw new NumberFormatException("bad digit");
            int digit = ch - '0';
            if (value < multMin) throw new NumberFormatException("out of range");
            value *= 10;
            if (value < limit + digit) throw new NumberFormatException("out of range");
            value -= digit;
        }
        return negative ? value : -value;
    }

    private static long _parseSliceLong(String content, int start, int end) {
        boolean negative = content.charAt(start) == '-';
        int i = negative ? start + 1 : start;
        if (i >= end) throw new NumberFormatException("empty");

        long value = 0;
        while (i < end) {
            char ch = content.charAt(i++);
            if (ch < '0' || ch > '9') throw new NumberFormatException("bad digit");
            int digit = ch - '0';
            if (value > MAX_IJSON_INTEGER_DIV_10 ||
                    (value == MAX_IJSON_INTEGER_DIV_10 && digit > MAX_IJSON_INTEGER_MOD_10)) {
                throw new NumberFormatException("out of range");
            }
            value = value * 10 + digit;
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
            throw new NodeException("invalid expression: start position does not point to '('");
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

        throw new NodeException("invalid expression: no matching ')' for '(' at position " + start);
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


    /*
     * --------------------------------------------------------------
     * Path Filter
     * --------------------------------------------------------------
     */

    /**
     * Parses a filter expression into an AST.
     * <p>
     * Supported operators include logical ({@code &&}, {@code ||}, {@code !}),
     * comparison ({@code == != < <= > >= =~ in nin}), literals, array literals,
     * path references, regex literals, and function calls.
     */
    public static FilterExpr parseFilter(String s) {
        return parseFilter(s, RegexEngine.JDK_ENGINE);
    }

    /**
     * Parses a filter using the given engine for regex literals and match/search functions.
     */
    public static FilterExpr parseFilter(String s, RegexEngine engine) {
        Asserts.notNull(engine, "engine");
        return new FilterParser(s, 0, engine).parse(s.length());
    }

}
