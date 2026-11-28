package org.sjf4j.path;

import org.sjf4j.exception.NodeException;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.regex.RegexEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Recursive-descent parser for JSONPath filter expressions. */
final class FilterParser {
    private final String source;
    private final RegexEngine engine;
    private int pos;

    FilterParser(String source, int pos, RegexEngine engine) {
        this.source = source;
        this.pos = pos;
        this.engine = engine;
    }

    FilterExpr parse(int endExclusive) {
        skipWs();
        FilterExpr expr = parseOr();
        skipWs();
        if (pos != endExclusive) {
            throw new NodeException("trailing characters at position " + pos);
        }
        return expr;
    }

    /**
     * Parses an OR expression.
     */
    private FilterExpr parseOr() {
        FilterExpr left = parseAnd();
        while (true) {
            skipWs();
            if (match("||")) {
                FilterExpr right = parseAnd();
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.OR);
            } else break;
        }
        return left;
    }

    /**
     * Parses an AND expression.
     */
    private FilterExpr parseAnd() {
        FilterExpr left = parseCompare();
        while (true) {
            skipWs();
            if (match("&&")) {
                FilterExpr right = parseCompare();
                left = new FilterExpr.BinaryExpr(left, right, FilterExpr.Op.AND);
            } else break;
        }
        return left;
    }

    /**
     * Parses a comparison expression.
     */
    private FilterExpr parseCompare() {
        FilterExpr left = parseUnary();
        skipWs();

        FilterExpr.Op op = null;
        char c = peekLast();
        if (c == '=') {
            if (match("==")) op = FilterExpr.Op.EQ;
            else if (match("=~")) op = FilterExpr.Op.MATCH;
        } else if (c == '!') {
            if (match("!=")) op = FilterExpr.Op.NE;
        } else if (c == '>') {
            if (match(">=")) op = FilterExpr.Op.GE;
            else if (match(">")) op = FilterExpr.Op.GT;
        } else if (c == '<') {
            if (match("<=")) op = FilterExpr.Op.LE;
            else if (match("<")) op = FilterExpr.Op.LT;
        } else if (c == 'i') {
            if (matchKeyword("in")) op = FilterExpr.Op.IN;
        } else if (c == 'n') {
            if (matchKeyword("nin")) op = FilterExpr.Op.NIN;
        }

        if (op != null) {
            FilterExpr right = parseUnary();
            return new FilterExpr.BinaryExpr(left, right, op);
        }

        return left;
    }

    /**
     * Parses a unary expression.
     */
    private FilterExpr parseUnary() {
        skipWs();
        if (match("!")) {
            FilterExpr child = parseUnary();
            return new FilterExpr.UnaryExpr(false, child);
        }
        return parsePrimary();
    }

    /**
     * Parses a primary expression.
     */
    private FilterExpr parsePrimary() {
        skipWs();
        char c = peekLast();

        // (expr)
        if (c == '(') {
            pos++;
            FilterExpr expr = parseOr();
            skipWs();
            if (peekLast() != ')') {
                throw new NodeException("missing closing ')' at position " + pos);
            }
            pos++;
            return expr;
        }

        // String literal
        if (c == '\'' || c == '"') {
            return new FilterExpr.LiteralExpr(parseString());
        }

        // Array literal
        if (c == '[') {
            return parseArrayLiteral();
        }

        // Number literal
        if (Character.isDigit(c) || c == '-') {
            return new FilterExpr.LiteralExpr(parseNumber());
        }

        // Path: @.a.b or $.x.y
        if (c == '@' || c == '$') {
            FilterExpr.PathExpr fast = tryParseSimpleFilterPathExpr();
            if (fast != null) return fast;
            String path = parsePath();
            return new FilterExpr.PathExpr(JsonPath.parse(path, engine));
        }

        // Regex: /^a/i
        if (c == '/') {
            return parseRegex();
        }

        if (matchKeyword("null")) {
            return new FilterExpr.LiteralExpr(null);
        }
        if (matchKeyword("true")) {
            return new FilterExpr.LiteralExpr(true);
        }
        if (matchKeyword("false")) {
            return new FilterExpr.LiteralExpr(false);
        }

        // Function: search(@.b, 'a')
        if (isNamePart(c)) {
            return parseFunction();
        }

        throw new NodeException("unexpected character '" + c + "' at position " + pos);
    }

    private FilterExpr.ArrayExpr parseArrayLiteral() {
        pos++;
        skipWs();

        List<FilterExpr> elements = new ArrayList<>();
        if (pos < source.length() && source.charAt(pos) == ']') {
            pos++;
            return new FilterExpr.ArrayExpr(elements);
        }

        while (true) {
            elements.add(parseOr());
            skipWs();

            if (pos >= source.length()) {
                throw new NodeException("missing closing ']' at position " + pos);
            }

            char c = source.charAt(pos);
            if (c == ',') {
                pos++;
                skipWs();
                continue;
            }
            if (c == ']') {
                pos++;
                return new FilterExpr.ArrayExpr(elements);
            }
            throw new NodeException("expected ',' or ']' in array literal at position " + pos);
        }
    }

    /**
     * Parses a function call expression.
     */
    private FilterExpr parseFunction() {
        // function name
        int start = pos;
        while (pos < source.length() && isNamePart(source.charAt(pos))) { pos++; }
        String name = source.substring(start, pos);
        skipWs();

        if (pos >= source.length() || source.charAt(pos) != '(') {
            throw new NodeException("expected '(' after function name '" + name + "'");
        }
        pos++; // '('

        List<FilterExpr> args = new ArrayList<>();
        skipWs();

        // empty arg list
        if (pos < source.length() && source.charAt(pos) == ')') {
            pos++;
            return new FilterExpr.FunctionExpr(name, args);
        }

        // arguments
        while (true) {
            FilterExpr arg = parseOr();
            args.add(arg);
            skipWs();

            if (pos >= source.length()) {
                throw new NodeException("unterminated function call '" + name + "'");
            }

            char c = source.charAt(pos);
            if (c == ',') {
                pos++;
                skipWs();
                continue;
            }
            if (c == ')') {
                pos++;
                break;
            }
            throw new NodeException("expected ',' or ')' in function call '" + name + "'");
        }

        if (args.size() == 2 && FunctionRegistry.isRegexFunction(name)) {
            return new RegexFunctionExpr(name, args, engine);
        }
        return new FilterExpr.FunctionExpr(name, args);
    }

    private static final class RegexFunctionExpr extends FilterExpr.FunctionExpr {
        private final FunctionRegistry.RegexCall call;

        RegexFunctionExpr(String name, List<FilterExpr> args, RegexEngine engine) {
            super(name, args);
            FilterExpr pattern = args.get(1);
            Object literal = pattern instanceof FilterExpr.LiteralExpr
                    ? ((FilterExpr.LiteralExpr) pattern).value() : null;
            this.call = new FunctionRegistry.RegexCall(name, engine, literal);
        }

        @Override
        public Object eval(Object rootNode, Object currentNode, NodeWalker.Access access) {
            Object target = args.get(0).eval(rootNode, currentNode, access);
            Object pattern = args.get(1).eval(rootNode, currentNode, access);
            return call.invoke(target, pattern);
        }
    }

    /**
     * Returns true if the char is valid in function names.
     */
    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Advances the cursor over whitespace.
     */
    private void skipWs() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }

    /**
     * Matches and consumes the given operator token.
     */
    private boolean match(String op) {
        if (source.startsWith(op, pos)) {
            pos += op.length();
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        int start = pos;
        if (!source.startsWith(keyword, start)) {
            return false;
        }
        int end = start + keyword.length();
        if (end < source.length() && isNamePart(source.charAt(end))) {
            return false;
        }
        pos = end;
        return true;
    }

    /**
     * Returns the current character or '\0' when out of range.
     */
    private char peekLast() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    /**
     * Parses a quoted string literal.
     */
    private String parseString() {
        char quote = source.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = source.charAt(pos++);

            if (c == quote) {
                return sb.toString();
            }

            if (c != '\\') {
                sb.append(c);
                continue;
            }

            if (pos >= source.length()) {
                throw new NodeException("unterminated escape sequence in string literal");
            }

            char next = source.charAt(pos++);
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
                    if (pos + 4 > source.length()) {
                        throw new NodeException("invalid unicode escape in string literal");
                    }
                    String hex = source.substring(pos, pos + 4);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new NodeException("invalid unicode escape in string literal", e);
                    }
                    pos += 4;
                    break;
                default:
                    throw new NodeException("invalid escape '\\" + next + "' in string literal");
            }
        }

        // reached end without closing quote
        throw new NodeException("unterminated string literal");
    }

    /**
     * Parses a numeric literal.
     */
    private Number parseNumber() {
        int start = pos;
        boolean negative = false;
        if (pos < source.length() && source.charAt(pos) == '-') {
            negative = true;
            pos++;
        }

        double value = 0;
        boolean hasDigit = false;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (!Character.isDigit(c)) break;
            hasDigit = true;
            value = value * 10 + (c - '0');
            pos++;
        }

        if (pos < source.length() && source.charAt(pos) == '.') {
            pos++;
            double scale = 0.1d;
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (!Character.isDigit(c)) break;
                hasDigit = true;
                value += (c - '0') * scale;
                scale *= 0.1d;
                pos++;
            }
        }

        if (!hasDigit) {
            throw new NumberFormatException("Invalid number literal at pos " + start);
        }

        return negative ? -value : value;
    }

    private FilterExpr.PathExpr tryParseSimpleFilterPathExpr() {
        int start = pos;
        int i = start;
        if (i >= source.length()) return null;

        PathSegment current;
        char head = source.charAt(i++);
        if (head == '$') {
            current = PathSegment.Root.INSTANCE;
        } else if (head == '@') {
            current = PathSegment.Current.INSTANCE;
        } else {
            return null;
        }

        ArrayList<PathSegment> segments = new ArrayList<>(4);
        segments.add(current);

        while (i < source.length()) {
            char c = source.charAt(i);
            if (isPathTerminator(c)) {
                pos = i;
                return new FilterExpr.PathExpr(new JsonPath(source.substring(start, i),
                        segments.toArray(new PathSegment[0])));
            }
            if (c != '.') {
                return null;
            }
            if (i + 1 >= source.length() || source.charAt(i + 1) == '.' || !PathSyntax._isSimpleNameStart(source.charAt(i + 1))) {
                return null;
            }

            int nameStart = i + 1;
            i += 2;
            while (i < source.length() && PathSyntax._isSimpleNamePart(source.charAt(i))) i++;
            current = new PathSegment.Name(current, source.substring(nameStart, i));
            segments.add(current);
        }

        pos = i;
        return new FilterExpr.PathExpr(new JsonPath(source.substring(start, i),
                segments.toArray(new PathSegment[0])));
    }

    /**
     * Parses a path literal within a filter expression.
     */
    private String parsePath() {
        int start = pos;
        boolean inStr = false, escape = false, inBracket = false, inParen = false;
        char quote = 0;

        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (escape) { escape = false; pos++; continue; }
            if (c == '\\') { escape = true; pos++; continue; }

            if (inStr) {
                if (c == quote) inStr = false;
                pos++; continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true; quote = c;
                pos++; continue;
            }

            if (inBracket) {
                if (c == ']') inBracket = false;
                pos++; continue;
            }
            if (c == '[') {
                inBracket = true;
                pos++; continue;
            }

            if (inParen) {
                if (c == ')') inParen = false;
                pos++; continue;
            }
            if (c == '(') {
                inParen = true;
                pos++; continue;
            }

            // allowed path chars
            if (Character.isAlphabetic(c) || Character.isDigit(c) ||
                    c == '@' || c == '$' || c == '.' || c == '_' || c == '*') {
                pos++; continue;
            }

            break;
        }
        return source.substring(start, pos);
    }

    /**
     * Parses a regex literal with optional flags.
     */
    @SuppressWarnings("MagicConstant")
    private FilterExpr.RegexExpr parseRegex() {
        int start = pos;
        if (source.charAt(pos) != '/') throw new NodeException("regex must start with '/' at position " + pos);
        pos++;

        boolean escape = false;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (escape) {
                escape = false;
                pos++;
                continue;
            }
            if (c == '\\') {
                escape = true;
                pos++;
                continue;
            }
            if (c == '/') {
                // end of regex
                String regex = source.substring(start + 1, pos);
                pos++; // skip closing '/'

                // parse optional flags
                int flagsStart = pos;
                while (pos < source.length()) {
                    char f = source.charAt(pos);
                    if (f == 'i' || f == 'm' || f == 's' || f == 'u' || f == 'g') {
                        pos++;
                    } else {
                        break;
                    }
                }
                String flags = source.substring(flagsStart, pos);
                String literal = source.substring(start, pos);
                return new FilterExpr.RegexExpr(literal, regex, toFlags(flags), engine);
            }
            pos++;
        }

        throw new NodeException("unterminated regex starting at position " + start);
    }

    /**
     * Converts regex flag letters to Pattern flags.
     */
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
                    throw new NodeException("invalid regex flag '" + c + "' in JSONPath filter");
            }
        }
        return f;
    }

    private static boolean isPathTerminator(char c) {
        return c == ')' || c == ']' || c == ',' || c == '!'
                || c == '=' || c == '<' || c == '>'
                || c == '&' || c == '|' || Character.isWhitespace(c);
    }
}
