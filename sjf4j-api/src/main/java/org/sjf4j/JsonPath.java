package org.sjf4j;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

public class JsonPath {

    @Getter
    private final List<PathToken> tokens;

    public JsonPath() {
        this.tokens = new ArrayList<>();
    }

    public void push(@NonNull PathToken token) {
        tokens.add(token);
    }

    public static JsonPath parse(String expr) {
        if (expr == null || expr.isEmpty()) {
            throw new JsonException("JsonPath cannot be empty");
        }

        JsonPath path = new JsonPath();
        int i = 0;

        if (expr.charAt(i) == '$') {
            path.push(new PathToken.Root());
            i++;
        } else {
            throw new JsonException("Must start with '$' in path '" + expr + "'");
        }

        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '.') {
                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '.' in path '" + expr + "' at pos " + i);

                // handle wildcard
                if (expr.charAt(i) == '*') {
                    path.push(new PathToken.Wildcard());
                    i++;
                    continue;
                }

                // read normal field name
                int start = i;
                while (i < expr.length() && isNameChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new JsonException("Empty field name after '.' in path '" + expr + "' at pos " + i);
                String name = expr.substring(start, i);
                path.push(new PathToken.Field(name));
            }
            else if (c == '[') {
                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '[' in path '" + expr + "' at pos " + i);

                char quote = expr.charAt(i);
                if (quote == '\'' || quote == '"') {
                    // --- ['key'] or ["key"]
                    i++;
                    StringBuilder sb = new StringBuilder();
                    while (i < expr.length()) {
                        char ch = expr.charAt(i);
                        if (ch == '\\') {
                            // escape
                            if (i + 1 >= expr.length())
                                throw new JsonException("Invalid escape at end of path '" + expr + "'");
                            char next = expr.charAt(i + 1);
                            if (next == quote || next == '\\')
                                sb.append(next);
                            else
                                sb.append('\\').append(next);
                            i += 2;
                        } else if (ch == quote) {
                            i++;
                            break;
                        } else {
                            sb.append(ch);
                            i++;
                        }
                    }

                    if (i >= expr.length() || expr.charAt(i) != ']')
                        throw new JsonException("Missing closing ']' after quoted field in path '" + expr + "' at pos " + i);
                    i++; // skip ]
                    path.push(new PathToken.Field(sb.toString()));
                }
                else if (expr.charAt(i) == '*') {
                    // [*]
                    i++;
                    if (i >= expr.length() || expr.charAt(i) != ']')
                        throw new JsonException("Missing closing ']' after '*' in path '" + expr + "' at pos " + i);
                    i++;
                    path.push(new PathToken.Wildcard(true));
                }
                else if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '-') {
                    // [0]
                    int start = i++;
                    while (i < expr.length() && Character.isDigit(expr.charAt(i))) i++;
                    if (i >= expr.length() || expr.charAt(i) != ']')
                        throw new JsonException("Missing closing ']' after index in path '" + expr + "' at pos " + i);
                    int idx = Integer.parseInt(expr.substring(start, i));
                    i++; // skip ]
                    path.push(new PathToken.Index(idx));
                }
                else {
                    throw new JsonException("Unexpected char after '[' in path '" + expr + "' at pos " + i);
                }
            }
            else {
                throw new JsonException("Unexpected char '" + c + "' in path '" + expr + "' at pos " + i);
            }
        }

        return path;
    }

    private static boolean isNameChar(char c) {
//        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c != '.' && c != '[';
    }


    /// Object

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (PathToken t : tokens) sb.append(t);
        return sb.toString();
    }
}
