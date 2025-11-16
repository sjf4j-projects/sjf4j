package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.PathToken;

import java.util.ArrayList;
import java.util.List;

public class JsonPointerUtil {


    public static List<PathToken> compile(@NonNull String expr) {
        List<PathToken> tokens = new ArrayList<>();
        tokens.add(new PathToken.Root());
        if (expr.isEmpty() || expr.equals("/")) return tokens;
        for (String seg : expr.split("/")) {
            if (seg.isEmpty()) continue;
            String name = seg.replace("~1", "/").replace("~0", "~");
            if (name.matches("\\d+")) {
                tokens.add(new PathToken.Index(Integer.parseInt(name)));
            } else {
                tokens.add(new PathToken.Name(name));
            }
        }
        return tokens;
    }


    public static String genExpr(@NonNull List<PathToken> tokens) {
        StringBuilder sb = new StringBuilder();
        for (PathToken token : tokens) {
            if (token instanceof PathToken.Root) {
                sb.append("/");
            } else if (token instanceof PathToken.Index) {
                sb.append(((PathToken.Index) token).index);
                sb.append("/");
            } else if (token instanceof PathToken.Name) {
                String name = ((PathToken.Name) token).name
                        .replace("~", "~0")
                        .replace("/", "~1");
                sb.append(name);
                sb.append("/");
            }
        }
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }


}
