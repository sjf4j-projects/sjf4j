package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for official runtime JSON Schema cases.
 * Arguments: [dialect [file.json [groupIndex [testIndex]]]].
 * The dialect defaults to all; use draft7, draft2019-09, or draft2020-12 to narrow.
 * Run OfficialRuntimeTest as a JUnit test to execute all mandatory cases.
 */
public final class OfficialRuntimeMain {

    private OfficialRuntimeMain() {
    }

    public static void main(String[] args) throws Exception {
        OfficialRuntimeTest.runStandalone(args);
    }
}
