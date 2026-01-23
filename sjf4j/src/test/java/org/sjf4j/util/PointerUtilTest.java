package org.sjf4j.util;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PointerUtilTest {


    @Test
    void testRootOnly() {
        List<PathToken> tokens = PointerUtil.compile("");
        assertEquals(1, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));

        tokens = PointerUtil.compile("/");
        assertEquals(1, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));
    }

    @Test
    void testSimplePath() {
        List<PathToken> tokens = PointerUtil.compile("/users/name");
        assertEquals(3, tokens.size());
        assertInstanceOf(PathToken.Root.class, tokens.get(0));
        assertInstanceOf(PathToken.Name.class, tokens.get(1));
        assertEquals("users", ((PathToken.Name) tokens.get(1)).name);
        assertInstanceOf(PathToken.Name.class, tokens.get(2));
        assertEquals("name", ((PathToken.Name) tokens.get(2)).name);
    }

    @Test
    void testNumericIndex() {
        List<PathToken> tokens = PointerUtil.compile("/users/0/posts/12");
        assertEquals(5, tokens.size());
        assertInstanceOf(PathToken.Index.class, tokens.get(2));
        assertEquals(0, ((PathToken.Index) tokens.get(2)).index);
        assertInstanceOf(PathToken.Index.class, tokens.get(4));
        assertEquals(12, ((PathToken.Index) tokens.get(4)).index);
    }

    @Test
    void testAppendToken() {
        List<PathToken> tokens = PointerUtil.compile("/users/-/name");
        assertEquals(4, tokens.size());
        assertInstanceOf(PathToken.Append.class, tokens.get(2));
    }

    @Test
    void testEscapeSequences() {
        // "~0" -> "~", "~1" -> "/"
        List<PathToken> tokens = PointerUtil.compile("/a~1b/c~0d/~1~0");
        assertEquals(4, tokens.size());
        assertEquals("a/b", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("c~d", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("/~", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testConsecutiveSlashes() {
        List<PathToken> tokens = PointerUtil.compile("/a//b///c");
        assertEquals(7, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(3)).name);
        assertEquals("c", ((PathToken.Name) tokens.get(6)).name);
    }

    @Test
    void testNumericEdgeCases() {
        // leading zeros
        List<PathToken> tokens = PointerUtil.compile("/01/002");
        assertEquals(3, tokens.size());
        assertEquals(1, ((PathToken.Index) tokens.get(1)).index);
        assertEquals(2, ((PathToken.Index) tokens.get(2)).index);

        // large number
        tokens = PointerUtil.compile("/1234567890");
        assertEquals(2, tokens.size());
        assertEquals(1234567890, ((PathToken.Index) tokens.get(1)).index);
    }

    @Test
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PointerUtil.compile(null));
        assertThrows(IllegalArgumentException.class, () -> PointerUtil.compile("users")); // missing '/'
    }

    @Test
    void testEmptySegments() {
        List<PathToken> tokens = PointerUtil.compile("/a//b");
        assertEquals(4, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testEmptyEnds() {
        List<PathToken> tokens = PointerUtil.compile("/a/b/");
        assertEquals(4, tokens.size());
        assertEquals("a", ((PathToken.Name) tokens.get(1)).name);
        assertEquals("b", ((PathToken.Name) tokens.get(2)).name);
        assertEquals("", ((PathToken.Name) tokens.get(3)).name);
    }

    @Test
    void testOnlyAppend() {
        List<PathToken> tokens = PointerUtil.compile("/-");
        assertEquals(2, tokens.size());
        assertInstanceOf(PathToken.Append.class, tokens.get(1));
    }


}
