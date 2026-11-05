package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for filtering official annotation tests.
 * Arguments: [dialect [file.json [groupIndex [testIndex]]]].
 * Dialect defaults to all; available values are all, draft7,
 * draft2019-09, and draft2020-12.
 * Run OfficialAnnotationTest as a JUnit test for the CI-gated annotation suite.
 */
public final class OfficialAnnotationMain {

    private OfficialAnnotationMain() {
    }

    public static void main(String[] args) throws Exception {
        OfficialAnnotationTest.runStandalone(args);
    }
}
