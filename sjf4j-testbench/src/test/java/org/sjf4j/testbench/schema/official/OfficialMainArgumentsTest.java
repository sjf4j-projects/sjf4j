package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaDialect;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OfficialMainArgumentsTest {

    @Test
    void compiledTestHasSingleZeroArgumentConstructorForJunit() {
        Constructor<?>[] constructors = OfficialCompiledTest.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
    }

    @Test
    void runtimeMainUsesTheSamePositionalDialectChoices() {
        assertEquals(OfficialAnnotationTest.standaloneDialects(null),
                OfficialRuntimeTest.standaloneDialects(null));
        for (String dialect : List.of("all", "draft7", "draft2019-09", "draft2020-12")) {
            assertEquals(OfficialAnnotationTest.standaloneDialects(dialect),
                    OfficialRuntimeTest.standaloneDialects(dialect), dialect);
        }
        assertThrows(IllegalArgumentException.class,
                () -> OfficialRuntimeTest.standaloneDialects("not-a-draft"));
    }

    @Test
    void annotationMainParsesDialectRatherThanSuiteRoot() {
        assertEquals(List.of(SchemaDialect.DRAFT_07, SchemaDialect.DRAFT_2019_09,
                        SchemaDialect.DRAFT_2020_12),
                OfficialAnnotationTest.standaloneDialects(null));
        assertEquals(List.of(SchemaDialect.DRAFT_07),
                OfficialAnnotationTest.standaloneDialects("draft7"));
        assertEquals(List.of(SchemaDialect.DRAFT_2019_09),
                OfficialAnnotationTest.standaloneDialects("draft2019-09"));
        assertEquals(List.of(SchemaDialect.DRAFT_2020_12),
                OfficialAnnotationTest.standaloneDialects("draft2020-12"));
        assertEquals(List.of(SchemaDialect.DRAFT_07, SchemaDialect.DRAFT_2019_09,
                        SchemaDialect.DRAFT_2020_12),
                OfficialAnnotationTest.standaloneDialects("all"));
        assertThrows(IllegalArgumentException.class,
                () -> OfficialAnnotationTest.standaloneDialects("unknown"));
    }


}
