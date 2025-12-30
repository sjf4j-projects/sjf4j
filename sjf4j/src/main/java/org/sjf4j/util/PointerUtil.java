package org.sjf4j.util;

import org.sjf4j.JsonException;
import org.sjf4j.path.PathToken;

import java.util.ArrayList;
import java.util.List;

public class PointerUtil {


    public static List<PathToken> compile(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        List<PathToken> tokens = new ArrayList<>();
        tokens.add(PathToken.Root.INSTANCE);
        if (expr.isEmpty() || expr.equals("/")) return tokens;
        for (String seg : expr.split("/")) {
            if (seg.isEmpty()) continue;
            String name = seg.replace("~1", "/").replace("~0", "~");
            if (name.matches("\\d+")) {
                tokens.add(new PathToken.Index(Integer.parseInt(name)));
            } else if (name.equals("-")) {
                tokens.add(PathToken.Append.INSTANCE);
            } else {
                tokens.add(new PathToken.Name(name));
            }
        }
        return tokens;
    }


    public static String genExpr(List<PathToken> tokens) {
        if (tokens == null) throw new IllegalArgumentException("Tokens must not be null");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i != tokens.size(); i++) {
            PathToken token = tokens.get(i);
            if (token instanceof PathToken.Root) {
                if (i != 0) throw new JsonException("Root token must be the first token in JSON Pointer");
            } else if (token instanceof PathToken.Index) {
                sb.append("/");
                sb.append(((PathToken.Index) token).index);
            } else if (token instanceof PathToken.Name) {
                sb.append("/");
                String name = ((PathToken.Name) token).name
                        .replace("~", "~0")
                        .replace("/", "~1");
                sb.append(name);
            } else if (token instanceof PathToken.Append) {
                sb.append("/-");
                if (i != tokens.size() - 1)
                    throw new JsonException("Append token '-' can only appear at the end of a JSON Patch path");
            } else {
                throw new JsonException("Unsupported PathToken type: " + token.getClass().getName());
            }
        }
        return sb.toString();
    }


}
