package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonException;
import org.sjf4j.PathToken;

import java.util.ArrayList;
import java.util.List;

public class JsonPathUtil {


    public static List<PathToken> compile(@NonNull String expr) {
        if (expr.isEmpty()) {
            throw new JsonException("JsonPath cannot be empty");
        }

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
            if (c == '.') {
                i++;
                if (i >= expr.length())
                    throw new JsonException("Unexpected EOF after '.' in path '" + expr + "' at pos " + i);

                // handle wildcard
                if (expr.charAt(i) == '*') {
                    tokens.add(new PathToken.Wildcard());
                    i++;
                    continue;
                }

                // read normal field name
                int start = i;
                while (i < expr.length() && isNameChar(expr.charAt(i))) i++;
                if (start == i)
                    throw new JsonException("Empty field name after '.' in path '" + expr + "' at pos " + i);
                String name = expr.substring(start, i);
                tokens.add(new PathToken.Name(name));
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
                    tokens.add(new PathToken.Name(sb.toString()));
                }
                else if (expr.charAt(i) == '*') {
                    // [*]
                    i++;
                    if (i >= expr.length() || expr.charAt(i) != ']')
                        throw new JsonException("Missing closing ']' after '*' in path '" + expr + "' at pos " + i);
                    i++;
                    tokens.add(new PathToken.Wildcard(true));
                }
                else if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '-') {
                    // [0]
                    int start = i++;
                    while (i < expr.length() && Character.isDigit(expr.charAt(i))) i++;
                    if (i >= expr.length() || expr.charAt(i) != ']')
                        throw new JsonException("Missing closing ']' after index in path '" + expr + "' at pos " + i);
                    int idx = Integer.parseInt(expr.substring(start, i));
                    i++; // skip ]
                    tokens.add(new PathToken.Index(idx));
                }
                else {
                    throw new JsonException("Unexpected char after '[' in path '" + expr + "' at pos " + i);
                }
            }
            else {
                throw new JsonException("Unexpected char '" + c + "' in path '" + expr + "' at pos " + i);
            }
        }

        return tokens;
    }


    public static String genExpr(List<PathToken> tokens) {
        StringBuilder sb = new StringBuilder();
        for (PathToken t : tokens) sb.append(t);
        return sb.toString();
    }

    /// private

    private static boolean isNameChar(char c) {
//        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c != '.' && c != '[';
    }

}
