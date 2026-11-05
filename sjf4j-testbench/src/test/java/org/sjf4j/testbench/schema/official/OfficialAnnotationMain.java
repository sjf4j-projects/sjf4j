package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for filtering official annotation tests.
 * Run OfficialAnnotationTest as a JUnit test for the CI-gated annotation suite.
 */
public final class OfficialAnnotationMain {

    private OfficialAnnotationMain() {
    }

    public static void main(String[] args) throws Exception {
        OfficialAnnotationTest.runStandalone(args);
    }
}
