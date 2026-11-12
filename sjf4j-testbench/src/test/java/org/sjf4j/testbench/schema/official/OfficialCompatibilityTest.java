package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaDialect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfficialCompatibilityTest {
    @Test
    void matchesBoundedAndDisjointDialectRanges() {
        SchemaDialect d7 = SchemaDialect.DRAFT_07;
        SchemaDialect d19 = SchemaDialect.DRAFT_2019_09;
        SchemaDialect d20 = SchemaDialect.DRAFT_2020_12;

        assertTrue(OfficialCompatibility.matches(null, d7));
        assertTrue(OfficialCompatibility.matches("3", d7));
        assertTrue(OfficialCompatibility.matches("2019", d19));
        assertTrue(OfficialCompatibility.matches("2019", d20));
        assertFalse(OfficialCompatibility.matches("2019", d7));
        assertTrue(OfficialCompatibility.matches("=2020", d20));
        assertFalse(OfficialCompatibility.matches("=2020", d19));
        assertTrue(OfficialCompatibility.matches("6,<=2019", d7));
        assertTrue(OfficialCompatibility.matches("6,<=2019", d19));
        assertFalse(OfficialCompatibility.matches("6,<=2019", d20));
        assertTrue(OfficialCompatibility.matches("=3,7,<=2020", d7));
        assertTrue(OfficialCompatibility.matches("=3,7,<=2020", d20));
        assertTrue(OfficialCompatibility.matches("<=3,7", d7));
        assertFalse(OfficialCompatibility.matches("9999", d20));
        assertFalse(OfficialCompatibility.matches("=9999", d20));
        assertThrows(IllegalArgumentException.class,
                () -> OfficialCompatibility.matches("n/a", d20));
        assertThrows(IllegalArgumentException.class,
                () -> OfficialCompatibility.matches("", d20));
    }
}
