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
                tokens.add(new PathToken.Field(name));
            }
        }
        return tokens;
    }

}
