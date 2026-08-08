package org.sjf4j.path;

import org.junit.jupiter.api.function.Executable;
import org.sjf4j.Sjf4j;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class JsonPathComparisonSupport {
    private JsonPathComparisonSupport() {}

    static void consensus(String[][] cases) {
        List<Executable> assertions = new ArrayList<>(cases.length);
        for (String[] c : cases) assertions.add(() -> {
            try {
                assertEquals(json(c[3]), JsonPath.parse(c[1]).eval(json(c[2])), c[0]);
            } catch (RuntimeException e) {
                fail(c[0], e);
            }
        });
        assertAll(assertions);
    }

    static void noConsensus(String[][] cases) {
        for (String[] c : cases) {
            try {
                JsonPath.parse(c[1]).eval(json(c[2]));
            } catch (RuntimeException ignored) {
                // Unsupported syntax has no normative result.
            }
        }
    }

    static void unsupported(String[][] cases) {
        List<Executable> assertions = new ArrayList<>(cases.length);
        for (String[] c : cases) {
            assertions.add(() -> assertThrows(RuntimeException.class,
                    () -> JsonPath.parse(c[1]).eval(json(c[2])), c[0]));
        }
        assertAll(assertions);
    }

    private static Object json(String value) {
        return Sjf4j.global().fromJson(value);
    }
}
