package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaDialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfficialAnnotationCompatibilityTest {

    @Test
    void draft2020() {
        SchemaDialect dialect = SchemaDialect.DRAFT_2020_12;
        assertTrue(OfficialAnnotationCompatibility.supports(null, dialect));
        assertTrue(OfficialAnnotationCompatibility.supports("7", dialect));
        assertTrue(OfficialAnnotationCompatibility.supports("2019", dialect));
        assertTrue(OfficialAnnotationCompatibility.supports("2020", dialect));
        assertTrue(OfficialAnnotationCompatibility.supports("=2020", dialect));
        assertFalse(OfficialAnnotationCompatibility.supports("<=2019", dialect));
        assertFalse(OfficialAnnotationCompatibility.supports("=2019", dialect));
        assertFalse(OfficialAnnotationCompatibility.supports("9999", dialect));
    }

    @Test
    void olderDrafts() {
        assertTrue(OfficialAnnotationCompatibility.supports("6,<=2019", SchemaDialect.DRAFT_07));
        assertTrue(OfficialAnnotationCompatibility.supports("6,<=2019", SchemaDialect.DRAFT_2019_09));
        assertFalse(OfficialAnnotationCompatibility.supports("6,<=2019", SchemaDialect.DRAFT_2020_12));
        assertFalse(OfficialAnnotationCompatibility.supports("2019", SchemaDialect.DRAFT_07));
        assertTrue(OfficialAnnotationCompatibility.supports("<=2019", SchemaDialect.DRAFT_07));
        assertTrue(OfficialAnnotationCompatibility.supports("=2019", SchemaDialect.DRAFT_2019_09));
        assertFalse(OfficialAnnotationCompatibility.supports("=2020", SchemaDialect.DRAFT_2019_09));
    }

    @Test
    void combinedConstraintsAreIntersected() {
        assertTrue(OfficialAnnotationCompatibility.supports("2019,<=2020", SchemaDialect.DRAFT_2020_12));
        assertFalse(OfficialAnnotationCompatibility.supports("=2020,<=2019", SchemaDialect.DRAFT_2020_12));
        assertFalse(OfficialAnnotationCompatibility.supports("=2019,2020", SchemaDialect.DRAFT_2020_12));
        assertFalse(OfficialAnnotationCompatibility.supports("9999", SchemaDialect.DRAFT_2020_12));
    }

    @Test
    void malformedCompatibilityIsRejected() {
        SchemaDialect dialect = SchemaDialect.DRAFT_2020_12;
        for (String invalid : new String[]{"", "2021", ">=2020", "2020,", "2020, 2019", "5", "2020-12"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> OfficialAnnotationCompatibility.supports(invalid, dialect), invalid);
        }
    }
}
