package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationOptions;
import org.sjf4j.schema.ValidationResult;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfficialMainArgumentsTest {

    @Test
    void compiledTestHasSingleZeroArgumentConstructorForJunit() {
        Constructor<?>[] constructors = OfficialCompiledTest.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
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

    @Test
    void annotationCompatibilityUsesSelectedDialect() {
        assertTrue(OfficialAnnotationTest.compatibleWith(null, SchemaDialect.DRAFT_07));
        assertTrue(OfficialAnnotationTest.compatibleWith("7", SchemaDialect.DRAFT_07));
        assertFalse(OfficialAnnotationTest.compatibleWith("2019", SchemaDialect.DRAFT_07));
        assertTrue(OfficialAnnotationTest.compatibleWith("6,<=2019", SchemaDialect.DRAFT_07));
        assertFalse(OfficialAnnotationTest.compatibleWith("6,<=2019", SchemaDialect.DRAFT_2020_12));
        assertTrue(OfficialAnnotationTest.compatibleWith("2019", SchemaDialect.DRAFT_2019_09));
        assertTrue(OfficialAnnotationTest.compatibleWith("<=2019", SchemaDialect.DRAFT_2019_09));
        assertFalse(OfficialAnnotationTest.compatibleWith("<=2019", SchemaDialect.DRAFT_2020_12));
        assertTrue(OfficialAnnotationTest.compatibleWith("=2019", SchemaDialect.DRAFT_2019_09));
        assertFalse(OfficialAnnotationTest.compatibleWith("=2019", SchemaDialect.DRAFT_2020_12));
        assertFalse(OfficialAnnotationTest.compatibleWith("9999", SchemaDialect.DRAFT_2020_12));
        assertThrows(IllegalArgumentException.class,
                () -> OfficialAnnotationTest.compatibleWith("bad", SchemaDialect.DRAFT_2020_12));
    }
    @Test
    void metadataAnnotationsAreCollectedAcrossAllSupportedDrafts() {
        ValidationOptions options = ValidationOptions.builder()
                .collectAnnotations(true).build();
        for (SchemaDialect dialect : SchemaDialect.values()) {
            ValidationResult result = JsonSchema.fromJson("{\"title\":\"Example\"}")
                    .createPlan(new SchemaRegistry(dialect)).validate(42, options);
            assertTrue(result.isValid(), dialect.toString());
            assertEquals(1, result.getAnnotations().size(), dialect.toString());
            assertEquals("title", result.getAnnotations().get(0).getKeyword(), dialect.toString());
            assertEquals("Example", result.getAnnotations().get(0).getAnnotation(), dialect.toString());
        }
    }

}
