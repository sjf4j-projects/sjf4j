package org.sjf4j.testbench.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.meta.RecordInfo;
import org.sjf4j.meta.ReflectUtil;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Java record metadata coverage, kept in the JDK 17 testbench. */
class ReflectUtilRecordTest {

    @Test
    void analyzeRecord_readsComponents_andFindsCanonicalCtor() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        RecordInfo ri = ReflectUtil.analyzeRecord(Person.class, lookup);
        assertNotNull(ri);

        assertEquals(2, ri.compCount);
        assertArrayEquals(new String[]{"name", "age"}, ri.compNames);

        // compClasses
        assertEquals(String.class, ri.compClasses[0]);
        assertEquals(int.class, ri.compClasses[1]);

        assertNotNull(ri.compCtor);
        assertNotNull(ri.compCtorHandle);
    }

    public record Person(String name, int age) {}

}
