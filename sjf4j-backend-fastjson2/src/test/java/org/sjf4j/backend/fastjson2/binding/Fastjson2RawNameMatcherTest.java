package org.sjf4j.backend.fastjson2.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.NameMatcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Both the expected-name and arbitrary-order raw match must preserve fallback semantics. */
class Fastjson2RawNameMatcherTest {

    private static final String[] NAMES = {
            "createdAt", "createdBy", "createdOn", "id",
            "café", "longPropertyNameTooLong", "q"
    };
    private static final String[][] ALIASES = {
            {"oldCreatedAt"}, {}, {}, {}, {}, {}, {}
    };
    private static final String DOCUMENT =
            "{\"createdBy\":1,\"createdOn\":2,\"createdAt\":3,\"id\":4,"
                    + "\"café\":5,\"oldCreatedAt\":6,\"created\\u0041t\":7,"
                    + "\"unknown\":0,\"longPropertyNameTooLong\":9,\"q\":10,"
                    + "\"createdBy\" :11}";

    @Test
    void matchesAllRawPrefixesWithSharedPrefixesAndFallbacks() throws Exception {
        readAndCheck(false);
        readAndCheck(true);
    }

    private void readAndCheck(boolean utf8) throws Exception {
        Fastjson2Binder binder = new Fastjson2Binder();
        Fastjson2NameMatcher matcher = new Fastjson2NameMatcher(NAMES, ALIASES);
        {
            Fastjson2Parser parser = utf8
                ? binder.createParser(DOCUMENT.getBytes(StandardCharsets.UTF_8))
                : binder.createParser(DOCUMENT);
            try {
                parser.start();
                parser.readObjectStart();
                List<Integer> indexes = new ArrayList<>();
                List<Integer> values = new ArrayList<>();
                int expected = 0;
                int index;
                while ((index = parser.nextNameMatch(matcher, expected)) != NameMatcher.OBJECT_END) {
                    indexes.add(index);
                    if (index == NameMatcher.UNKNOWN) {
                        parser.skipNode();
                    } else {
                        values.add(parser.readIntValue());
                        expected = index + 1;
                    }
                }
                parser.end();
                assertEquals(Arrays.asList(1, 2, 0, 3, 4, 0, 0,
                        NameMatcher.UNKNOWN, 5, 6, 1), indexes);
                assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 9, 10, 11), values);

            } finally {
                parser.close();
            }
        }
    }
}
