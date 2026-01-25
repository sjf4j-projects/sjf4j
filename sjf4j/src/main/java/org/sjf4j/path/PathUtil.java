package org.sjf4j.path;

import org.sjf4j.JsonException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PathUtil {

    /// JSON Pointer

    public static List<PathToken> tokenizePointer(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        if (!expr.isEmpty() && !expr.startsWith("/"))
            throw new IllegalArgumentException("Invalid JSON pointer '" + expr + "': must start with '/'");

        List<PathToken> tokens = new ArrayList<>();
        tokens.add(PathToken.Root.INSTANCE);
        if (expr.isEmpty() || expr.equals("/")) return tokens;

        int len = expr.length();
        int start = 1; // skip leading '/'
        while (start <= len) {
            int end = expr.indexOf('/', start);
            if (end == -1) end = len;

            String seg = expr.substring(start, end);
            String name;

            // decode ~0/~1
            if (seg.indexOf('~') >= 0) {
                StringBuilder sb = new StringBuilder(seg.length());
                for (int i = 0, len2 = seg.length(); i < len2; i++) {
                    char c = seg.charAt(i);
                    if (c == '~' && i + 1 < seg.length()) {
                        char next = seg.charAt(i + 1);
                        if (next == '0') { sb.append('~'); i++; continue; }
                        if (next == '1') { sb.append('/'); i++; continue; }
                    }
                    sb.append(c);
                }
                name = sb.toString();
            } else {
                name = seg;
            }

            // detect numeric index
            boolean isNumber = !name.isEmpty();
            for (int i = 0, len2 = name.length(); i < len2 && isNumber; i++) {
                char c = name.charAt(i);
                if (c < '0' || c > '9') isNumber = false;
            }

            if (isNumber) {
                tokens.add(new PathToken.Index(Integer.parseInt(name)));
            } else if (name.equals("-")) {
                tokens.add(PathToken.Append.INSTANCE);
            } else {
                tokens.add(new PathToken.Name(name));
            }

            start = end + 1;
        }

        return tokens;
    }

    public static String toPointerExpr(List<PathToken> tokens) {
        if (tokens == null) throw new IllegalArgumentException("Tokens must not be null");
        StringBuilder sb = new StringBuilder();

        for (int i = 0, len = tokens.size(); i < len; i++) {
            PathToken token = tokens.get(i);

            if (token instanceof PathToken.Root) {
                if (i != 0) throw new JsonException("Root token must be the first token in JSON Pointer");
                // Root token: no output
            } else if (token instanceof PathToken.Index) {
                sb.append('/');
                sb.append(((PathToken.Index) token).index);
            } else if (token instanceof PathToken.Append) {
                sb.append("/-");
                if (i != tokens.size() - 1)
                    throw new JsonException("Append token '-' can only appear at the end of a JSON Patch path");
            } else if (token instanceof PathToken.Name) {
                sb.append('/');
                String name = ((PathToken.Name) token).name;
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

    public static String toPathExpr(List<PathToken> tokens) {
        StringBuilder sb = new StringBuilder();
        PathToken lastPt = null;
        for (PathToken pt : tokens) {
            if (lastPt instanceof PathToken.Descendant && pt instanceof PathToken.Name) {
                PathToken.Name name = (PathToken.Name) pt;
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

    public static List<PathToken> tokenizePath(String expr) {
        if (expr == null || expr.isEmpty()) throw new JsonException("expr is empty");

        List<PathToken> tokens = new ArrayList<>();
        int i = 0;

        if (expr.charAt(i) == '$') {
            tokens.add(PathToken.Root.INSTANCE);
            i++;
        } else if (expr.charAt(i) == '@') {
            tokens.add(PathToken.Current.INSTANCE);
            i++;
        } else {
//            throw new JsonException("Must start with '$' or '@' in path '" + expr + "'");
            tokens.add(PathToken.Root.INSTANCE);
        }

        while (i < expr.length()) {
            char c = expr.charAt(i);

            // Descendant ..
            if (c == '.' && i + 1 < expr.length() && expr.charAt(i + 1) == '.') {
                tokens.add(PathToken.Descendant.INSTANCE);
                if (i + 2 == expr.length()) {
                    break;
                } else if (expr.charAt(i + 2) == '[') {
                    i += 2;
                } else {
                    i += 1;
                }
                c = expr.charAt(i);
            }

            if (c == '[') {
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

                // Extract content between [ and ]
                String bracketContent = expr.substring(start, i);
                i++; // skip ]

                // Single element: could be [*], [0], [-1], ['name'], or [start:end:step]
                String content = bracketContent.trim();
                // Dispatch based on content type
                if (content.startsWith("?")) {
                    // Filter
                    String filterStr = content.substring(1).trim();
                    FilterExpr filterExpr = parseFilter(filterStr);
                    tokens.add(new PathToken.Filter(filterExpr));
                } else if (hasComma) {
                    // Union: can include indices, names, and slices like [1, 'name', 2:5]
                    List<PathToken> unionTokens = parseUnionTokens(content);
                    tokens.add(new PathToken.Union(unionTokens));
                } else {
                    if (content.isEmpty()) {
                        throw new JsonException("Empty content [] in path '" + expr + "' at position " + i);
                    } else if (content.equals("*")) {
                        // [*]
                        tokens.add(PathToken.Wildcard.INSTANCE);
                    } else if (content.startsWith("'") || content.startsWith("\"")) {
                        // Single quoted name ['name'] or ["name"]
                        String name = parseSingleQuotedName(content);
                        tokens.add(new PathToken.Name(name));
                    } else if (content.contains(":")) {
                        // Slice [start:end:step]
                        String[] sliceParts = content.split(":", -1);
                        if (sliceParts.length > 3) {
                            throw new JsonException("Invalid slice syntax '" + content + "' in path '" + expr + "'");
                        }
                        Integer startIdx = parseSlicePart(sliceParts[0]);
                        Integer endIdx = sliceParts.length > 1 ? parseSlicePart(sliceParts[1]) : null;
                        Integer step = sliceParts.length > 2 ? parseSlicePart(sliceParts[2]) : null;
                        if (step != null && step == 0) {
                            throw new JsonException("Slice step cannot be 0 in path '" + expr + "'");
                        }
                        tokens.add(new PathToken.Slice(startIdx, endIdx, step));
                    } else {
                        try {
                            // Try to parse as numeric index
                            int idx = Integer.parseInt(content);
                            tokens.add(new PathToken.Index(idx));
                        } catch (NumberFormatException e) {
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
                    tokens.add(PathToken.Wildcard.INSTANCE);
                    i++;
                    continue;
                }

                int start = i;
                while (i < expr.length() && isNextTokenChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new JsonException("Empty field name after '.' in path '" + expr + "' at position " + i);

                // Function
                if (i < expr.length() && expr.charAt(i) == '(') {
                    int end = findMatchingParen(expr, i);
                    if (end < 0) {
                        throw new JsonException("Unclosed '(' in function at position " + i + " in path '" + expr + "'");
                    }
                    String funcName = expr.substring(start, i);
                    String args = expr.substring(i + 1, end); // inside (...)
                    tokens.add(new PathToken.Function(funcName, parseFunctionArgs(args)));
                    i = end + 1;
                    continue;
                }

                // Name
                String name = expr.substring(start, i);
                tokens.add(new PathToken.Name(name));
            }
            else {
                throw new JsonException("Unexpected char '" + c + "' in path '" + expr + "' at position " + i);
            }
        }

        return tokens;
    }


    /// private

    private static boolean isNextTokenChar(char c) {
//        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c != '.' && c != '[' && c != '(';
    }


    // Helper method: parse single quoted name
    private static String parseSingleQuotedName(String content) {
        if (content.length() < 2) {
            throw new JsonException("Invalid quoted name '" + content + "'");
        }
        char quote = content.charAt(0);
        if (quote != '\'' && quote != '"') {
            throw new JsonException("Expected quoted string, got '" + content + "'");
        }

        StringBuilder sb = new StringBuilder();
        int i = 1; // Skip opening quote
        while (i < content.length()) {
            char ch = content.charAt(i);
            if (ch == '\\') {
                // Escape character
                if (i + 1 >= content.length()) {
                    throw new JsonException("Invalid escape at end of quoted name");
                }
                char next = content.charAt(i + 1);
                if (next == quote || next == '\\') {
                    sb.append(next);
                } else {
                    sb.append('\\').append(next);
                }
                i += 2;
            } else if (ch == quote) {
                break;
            } else {
                sb.append(ch);
                i++;
            }
        }

        if (i >= content.length() || content.charAt(i) != quote) {
            throw new JsonException("Missing closing quote in name '" + content + "'");
        }

        return sb.toString();
    }


    // Helper method: parse slice part (can be null for default value)
    private static Integer parseSlicePart(String part) {
        if (part == null || part.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid slice part '" + part + "'");
        }
    }

    // Helper method: parse quoted name in Union
    private static String parseQuotedUnionName(String content, int start) {
        char quote = content.charAt(start);
        StringBuilder sb = new StringBuilder();
        int i = start + 1; // Skip opening quote

        while (i < content.length()) {
            char ch = content.charAt(i);
            if (ch == '\\') {
                // Escape character
                if (i + 1 >= content.length()) {
                    throw new JsonException("Invalid escape at end of quoted name in union");
                }
                char next = content.charAt(i + 1);
                if (next == quote || next == '\\') {
                    sb.append(next);
                } else {
                    sb.append('\\').append(next);
                }
                i += 2;
            } else if (ch == quote) {
                break;
            } else {
                sb.append(ch);
                i++;
            }
        }

        if (i >= content.length() || content.charAt(i) != quote) {
            throw new JsonException("Missing closing quote in union name");
        }

        return sb.toString();
    }

    // Helper method: parse multiple tokens in Union
    private static List<PathToken> parseUnionTokens(String content) {
        List<PathToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < content.length()) {
            // Skip spaces and commas
            while (i < content.length() && (content.charAt(i) == ' ' || content.charAt(i) == ',')) i++;
            if (i >= content.length()) break;

            char firstChar = content.charAt(i);

            if (firstChar == '\'' || firstChar == '"') {
                // Quoted name
                String name = parseQuotedUnionName(content, i);
                tokens.add(new PathToken.Name(name));
                i += name.length() + 2; // Skip quotes and content
                // Find next comma or end
                while (i < content.length() && content.charAt(i) != ',') i++;
            } else if (Character.isDigit(firstChar) || firstChar == '-') {
                // Could be numeric index or slice
                int start = i;
                boolean hasColon = false;
                while (i < content.length() && content.charAt(i) != ',') {
                    if (content.charAt(i) == ':') {
                        hasColon = true;
                    }
                    i++;
                }

                String part = content.substring(start, i).trim();
                if (hasColon) {
                    // Slice
                    String[] sliceParts = part.split(":", -1);
                    if (sliceParts.length > 3) {
                        throw new JsonException("Invalid slice syntax '" + part + "' in union");
                    }

                    Integer startIdx = parseSlicePart(sliceParts[0]);
                    Integer endIdx = sliceParts.length > 1 ? parseSlicePart(sliceParts[1]) : null;
                    Integer step = sliceParts.length > 2 ? parseSlicePart(sliceParts[2]) : null;

                    if (step != null && step == 0) {
                        throw new JsonException("Slice step cannot be 0 in union");
                    }

                    tokens.add(new PathToken.Slice(startIdx, endIdx, step));
                } else {
                    // Numeric index
                    try {
                        int idx = Integer.parseInt(part);
                        tokens.add(new PathToken.Index(idx));
                    } catch (NumberFormatException e) {
                        throw new JsonException("Invalid index '" + part + "' in union");
                    }
                }
            } else {
                throw new JsonException("Invalid first char '" + firstChar + "' at position " + i +
                        " in content '" + content + "'");
            }
        }
        return tokens;
    }

    /**
     * Finds the matching closing parenthesis for an expression starting at `start`,
     * automatically skipping parentheses that appear inside quoted strings.
     *
     * Supports both single-quoted and double-quoted strings.
     */
    static int findMatchingParen(String s, int start) {
        if (start < 0 || start >= s.length() || s.charAt(start) != '(') {
            throw new IllegalArgumentException("start is not position of '('");
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

        throw new IllegalArgumentException("No matching ')' found for '(' at position " + start);
    }

    // Function at last token, not in Filter
    static List<String> parseFunctionArgs(String s) {
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

    public static FilterExpr parseFilter(String s) {
        int[] pos = {0};
        skipWs(s, pos);
        FilterExpr expr = parseOr(s, pos);
        skipWs(s, pos);
        if (pos[0] != s.length()) {
            throw new JsonException("Trailing characters at pos " + pos[0]);
        }
        return expr;
    }

    // or := and ('||' and)*
    private static FilterExpr parseOr(String s, int[] pos) {
        FilterExpr left = parseAnd(s, pos);
        while (true) {
            skipWs(s, pos);
            if (match(s, pos, "||")) {
                FilterExpr right = parseAnd(s, pos);
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.OR);
            } else break;
        }
        return left;
    }

    // and := compare ('&&' compare)*
    private static FilterExpr parseAnd(String s, int[] pos) {
        FilterExpr left = parseCompare(s, pos);
        while (true) {
            skipWs(s, pos);
            if (match(s, pos, "&&")) {
                FilterExpr right = parseCompare(s, pos);
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.AND);
            } else break;
        }
        return left;
    }

    // compare := unary (op unary)?
    private static FilterExpr parseCompare(String s, int[] pos) {
        FilterExpr left = parseUnary(s, pos);
        skipWs(s, pos);

        FilterExpr.Op op = null;

        if (match(s, pos, "==")) op = FilterExpr.Op.EQ;
        else if (match(s, pos, "=~")) op = FilterExpr.Op.MATCH;
        else if (match(s, pos, "!=")) op = FilterExpr.Op.NE;
        else if (match(s, pos, ">=")) op = FilterExpr.Op.GE;
        else if (match(s, pos, "<=")) op = FilterExpr.Op.LE;
        else if (match(s, pos, ">"))  op = FilterExpr.Op.GT;
        else if (match(s, pos, "<"))  op = FilterExpr.Op.LT;

        if (op != null) {
            FilterExpr right = parseUnary(s, pos);
            return new FilterExpr.BinaryExpr(left, right, op);
        }

        return left;
    }

    // unary := '!' unary | primary
    private static FilterExpr parseUnary(String s, int[] pos) {
        skipWs(s, pos);
        if (match(s, pos, "!")) {
            FilterExpr child = parseUnary(s, pos);
            return new FilterExpr.UnaryExpr(false, child);
        }
        return parsePrimary(s, pos);
    }

    // primary := literal | path | '(' expr ')'
    private static FilterExpr parsePrimary(String s, int[] pos) {
        skipWs(s, pos);
        char c = peek(s, pos);

        // (expr)
        if (c == '(') {
            pos[0]++;
            FilterExpr expr = parseOr(s, pos);
            skipWs(s, pos);
            if (peek(s, pos) != ')') {
                throw new JsonException("Missing ')'");
            }
            pos[0]++;
            return expr;
        }

        // String literal
        if (c == '\'' || c == '"') {
            return new FilterExpr.LiteralExpr(parseString(s, pos));
        }

        // Number literal
        if (Character.isDigit(c) || c == '-') {
            return new FilterExpr.LiteralExpr(parseNumber(s, pos));
        }

        // Path: @.a.b or $.x.y
        if (c == '@' || c == '$') {
            String path = parsePath(s, pos);
            return new FilterExpr.PathExpr(path);
        }

        // Regex: /^a/i
        if (c == '/') {
            return parseRegex(s, pos);
        }

        // Function: search(@.b, 'a')
        if (isNamePart(c)) {
            return parseFunction(s, pos);
        }

        throw new JsonException("Unexpected char '" + c + "' at pos " + pos[0]);
    }

    // function := name '(' [ expr (',' expr)* ] ')'
    private static FilterExpr parseFunction(String s, int[] pos) {
        // function name
        int start = pos[0];
        while (pos[0] < s.length() && isNamePart(s.charAt(pos[0]))) { pos[0]++; }
        String name = s.substring(start, pos[0]);
        skipWs(s, pos);

        if (pos[0] >= s.length() || s.charAt(pos[0]) != '(') {
            throw new JsonException("Expected '(' after function name: " + name);
        }
        pos[0]++; // '('

        List<FilterExpr> args = new ArrayList<>();
        skipWs(s, pos);

        // empty arg list
        if (pos[0] < s.length() && s.charAt(pos[0]) == ')') {
            pos[0]++;
            return new FilterExpr.FunctionExpr(name, args);
        }

        // arguments
        while (true) {
            FilterExpr arg = parseOr(s, pos);
            args.add(arg);
            skipWs(s, pos);

            if (pos[0] >= s.length()) {
                throw new JsonException("Unterminated function call: " + name);
            }

            char c = s.charAt(pos[0]);
            if (c == ',') {
                pos[0]++;
                skipWs(s, pos);
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

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) {
            pos[0]++;
        }
    }

    private static boolean match(String s, int[] pos, String op) {
        if (s.startsWith(op, pos[0])) {
            pos[0] += op.length();
            return true;
        }
        return false;
    }

    private static char peek(String s, int[] pos) {
        return pos[0] < s.length() ? s.charAt(pos[0]) : '\0';
    }

    private static String parseString(String s, int[] pos) {
        char quote = s.charAt(pos[0]++);
        int start = pos[0];
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

            if (c == quote) {
                String v = s.substring(start, pos[0]);
                pos[0]++; // skip closing quote
                return v;
            }

            pos[0]++;
        }

        // reached end without closing quote
        throw new JsonException("Unterminated string literal starting at position " + start);
    }

    private static Number parseNumber(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length() &&
                (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.'
                        || s.charAt(pos[0]) == '-')) {
            pos[0]++;
        }
        return Double.valueOf(s.substring(start, pos[0]));
    }

    private static String parsePath(String s, int[] pos) {
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

    @SuppressWarnings("MagicConstant")
    private static FilterExpr.RegexExpr parseRegex(String s, int[] pos) {
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
                Pattern pattern = Pattern.compile(regex, toFlags(flags));
                return new FilterExpr.RegexExpr(source, pattern);
            }
            pos[0]++;
        }

        throw new JsonException("Unterminated regex starting at pos " + start);
    }

    private static int toFlags(String flags) {
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
                    throw new IllegalArgumentException("Unsupported regex flag: " + c);
            }
        }
        return f;
    }



}
