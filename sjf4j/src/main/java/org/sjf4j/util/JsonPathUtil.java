package org.sjf4j.util;

import org.sjf4j.JsonException;
import org.sjf4j.path.PathToken;

import java.util.ArrayList;
import java.util.List;

public class JsonPathUtil {

    public static List<PathToken> compile(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");

        List<PathToken> tokens = new ArrayList<>();
        int i = 0;

        if (expr.charAt(i) == '$') {
            tokens.add(new PathToken.Root());
            i++;
        } else {
            throw new JsonException("Must start with '$' in path '" + expr + "'");
        }

        while (i < expr.length()) {
            char c = expr.charAt(i);

            // Descendant ..
            if (c == '.' && i + 1 < expr.length() && expr.charAt(i + 1) == '.') {
                tokens.add(new PathToken.Descendant());
                if (i + 2 == expr.length()) {
                    break;
                } else if (expr.charAt(i + 2) == '[') {
                    i += 2;
                } else {
                    i += 1;
                }
                c = expr.charAt(i);
            }

            if (c == '.') {
                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '.' in path '" + expr + "' at position " + i);

                // Wildcard
                if (expr.charAt(i) == '*') {
                    tokens.add(new PathToken.Wildcard());
                    i++;
                    continue;
                }

                int start = i;
                while (i < expr.length() && isNextTokenChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new JsonException("Empty field name after '.' in path '" + expr + "' at position " + i);


                // Check if function call: NAME(...)
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

//                if (i > start + 2 && expr.charAt(i - 2) == '(' && expr.charAt(i - 1) == ')') {
//                    // Function
//                    if (i == expr.length()) {
//                        String name = expr.substring(start, i - 2);
//                        tokens.add(new PathToken.Function(name));
//                    } else {
//                        throw new JsonException("Function call must be at the end of path '" + expr + "'");
//                    }
//                } else {
//                    // Name
//                    String name = expr.substring(start, i);
//                    tokens.add(new PathToken.Name(name));
//                }

            }
            else if (c == '[') {
                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '[' in path '" + expr + "' at position " + i);

                // Scan and check
                int start = i;
                boolean hasComma = false;
                while (i < expr.length()) {
                    char ch = expr.charAt(i);
                    if (ch == '\'' || ch == '"') {
                        // Skip quoted content
                        i++; // skip opening quote
                        while (i < expr.length() && expr.charAt(i) != ch) {
                            if (expr.charAt(i) == '\\') {
                                i++; // skip escape char
                            }
                            i++;
                        }
                        if (i >= expr.length()) {
                            throw new JsonException("Unclosed quote in path '" + expr + "' at position " + start);
                        }
                        i++; // skip closing quote
                    } else if (ch == ',') {
                        hasComma = true;
                        i++;
                    } else if (ch == ']') {
                        break;
                    } else {
                        i++;
                    }
                }

                if (i >= expr.length())
                    throw new JsonException("Missing closing ']' in path '" + expr + "' at position " + start);

                // Extract content between [ and ]
                String bracketContent = expr.substring(start, i);
                i++; // skip ]

                // Dispatch based on content type
                if (hasComma) {
                    // Union: can include indices, names, and slices like [1, 'name', 2:5]
                    List<PathToken> unionTokens = parseUnionTokens(bracketContent);
                    tokens.add(new PathToken.Union(unionTokens));
                } else {
                    // Single element: could be [*], [0], [-1], ['name'], or [start:end:step]
                    String content = bracketContent.trim();

                    if (content.isEmpty()) {
                        throw new JsonException("Empty content [] in path '" + expr + "' at position " + i);
                    } else if (content.equals("*")) {
                        // [*]
                        tokens.add(new PathToken.Wildcard(true));
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
                    } else if (content.startsWith("?")) {
//                        throw new JsonException("Filter expression like '" + content + "' in path '" + expr +
//                                "' are not supported yet. You may use `JsonWalker` or `JsonStream` instead.");
                        String filterExpr = content.substring(2, content.length() - 1).trim();
                        tokens.add(new PathToken.Filter(filterExpr));
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
            else {
                throw new JsonException("Unexpected char '" + c + "' in path '" + expr + "' at position " + i);
            }
        }

        return tokens;
    }


    public static String genExpr(List<PathToken> tokens) {
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

}
