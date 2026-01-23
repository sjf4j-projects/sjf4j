package org.sjf4j.util;

import org.sjf4j.JsonException;
import org.sjf4j.path.PathToken;

import java.util.ArrayList;
import java.util.List;

public class PointerUtil {


//    public static List<PathToken> compile2(String expr) {
//        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
//        if (!expr.isEmpty() && !expr.startsWith("/"))
//            throw new IllegalArgumentException("Invalid JSON Pointer expression '" + expr + "': must start with '$'");
//        List<PathToken> tokens = new ArrayList<>();
//        tokens.add(PathToken.Root.INSTANCE);
//        if (expr.isEmpty() || expr.equals("/")) return tokens;
//        for (String seg : expr.split("/")) {
//            if (seg.isEmpty()) continue;
//            String name = seg.replace("~1", "/").replace("~0", "~");
//            if (name.matches("\\d+")) {
//                tokens.add(new PathToken.Index(Integer.parseInt(name)));
//            } else if (name.equals("-")) {
//                tokens.add(PathToken.Append.INSTANCE);
//            } else {
//                tokens.add(new PathToken.Name(name));
//            }
//        }
//        return tokens;
//    }


    public static List<PathToken> compile3(String expr) {
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

            if (end > start) { // non-empty segment
                String seg = expr.substring(start, end);
                StringBuilder sb = null;

                // decode ~0/~1
                boolean needDecode = seg.indexOf('~') >= 0;
                String name;
                if (needDecode) {
                    sb = new StringBuilder(seg.length());
                    for (int i = 0; i < seg.length(); i++) {
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
                for (int i = 0; i < name.length() && isNumber; i++) {
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
            }

            start = end + 1;
        }

        return tokens;
    }

    public static List<PathToken> compile(String expr) {
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
                for (int i = 0; i < seg.length(); i++) {
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
            for (int i = 0; i < name.length() && isNumber; i++) {
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



//    public static String genExpr2(List<PathToken> tokens) {
//        if (tokens == null) throw new IllegalArgumentException("Tokens must not be null");
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i != tokens.size(); i++) {
//            PathToken token = tokens.get(i);
//            if (token instanceof PathToken.Root) {
//                if (i != 0) throw new JsonException("Root token must be the first token in JSON Pointer");
//            } else if (token instanceof PathToken.Index) {
//                sb.append("/");
//                sb.append(((PathToken.Index) token).index);
//            } else if (token instanceof PathToken.Name) {
//                sb.append("/");
//                String name = ((PathToken.Name) token).name
//                        .replace("~", "~0")
//                        .replace("/", "~1");
//                sb.append(name);
//            } else if (token instanceof PathToken.Append) {
//                sb.append("/-");
//                if (i != tokens.size() - 1)
//                    throw new JsonException("Append token '-' can only appear at the end of a JSON Patch path");
//            } else {
//                throw new JsonException("Unsupported PathToken type: " + token.getClass().getName());
//            }
//        }
//        return sb.toString();
//    }

    public static String genExpr(List<PathToken> tokens) {
        if (tokens == null) throw new IllegalArgumentException("Tokens must not be null");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tokens.size(); i++) {
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
                // 单次遍历转义 ~0/~1
                for (int j = 0; j < name.length(); j++) {
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


}
