package org.sjf4j.path;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathStackTest {

    @Test
    public void pushAndMaterialize() {
        PathStack stack = new PathStack();
        stack.pushName(Map.class, "user");
        stack.pushIndex(java.util.List.class, 2);
        stack.pushName(Map.class, "name");

        PathSegment ps = stack.toPathSegment();
        assertEquals("$.user[2].name", Paths.rootedPathExpr(ps));
        assertEquals("/user/2/name", Paths.rootedPointerExpr(ps));
    }

    @Test
    public void popAndClear() {
        PathStack stack = new PathStack();
        stack.pushName(Map.class, "a");
        stack.pushIndex(java.util.List.class, 0);
        assertEquals(2, stack.size());

        stack.pop();
        assertEquals(1, stack.size());
        assertEquals("$.a", Paths.rootedPathExpr(stack.toPathSegment()));

        stack.clear();
        assertTrue(stack.isEmpty());
        assertEquals("$", Paths.rootedPathExpr(stack.toPathSegment()));
    }

    @Test
    public void growCapacity() {
        PathStack stack = new PathStack(2);
        for (int i = 0; i < 32; i++) {
            stack.pushIndex(java.util.List.class, i);
        }

        assertFalse(stack.isEmpty());
        assertEquals(32, stack.size());
        assertEquals("$[0][1][2][3][4][5][6][7][8][9][10][11][12][13][14][15][16][17][18][19][20][21][22][23][24][25][26][27][28][29][30][31]",
                Paths.rootedPathExpr(stack.toPathSegment()));
    }

    @Test
    public void popEmptyThrows() {
        PathStack stack = new PathStack();
        assertThrows(IllegalStateException.class, stack::pop);
    }
}
