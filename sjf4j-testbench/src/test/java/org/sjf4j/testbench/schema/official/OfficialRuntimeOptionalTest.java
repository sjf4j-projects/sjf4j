package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.SchemaDialect;

/**
 * Diagnostic-only optional JSON Schema validation.
 * All case results and per-draft totals are printed to the test log;
 * optional assertion mismatches and evaluation errors do not fail the build.
 */
public final class OfficialRuntimeOptionalTest {

    @Test
    void draft7() throws Exception {
        OfficialRuntimeTest.runOptional(SchemaDialect.DRAFT_07);
    }

    @Test
    void draft2019_09() throws Exception {
        OfficialRuntimeTest.runOptional(SchemaDialect.DRAFT_2019_09);
    }

    @Test
    void draft2020_12() throws Exception {
        OfficialRuntimeTest.runOptional(SchemaDialect.DRAFT_2020_12);
    }
}
