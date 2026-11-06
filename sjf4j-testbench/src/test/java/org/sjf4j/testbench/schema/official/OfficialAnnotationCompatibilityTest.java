package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaDialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dialect compatibility rules for the official annotation suite. */
final class OfficialAnnotationCompatibilityTest {

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

}
