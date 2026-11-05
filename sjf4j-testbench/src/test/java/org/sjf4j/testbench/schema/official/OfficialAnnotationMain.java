package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for filtering official annotation tests.
 * Arguments: [dialect [file.json [group-filter [test-filter]]]].
 * Dialect defaults to draft2020-12; available values include draft7,
 * draft2019-09, draft2020-12, and all.
 * Run OfficialAnnotationTest as a JUnit test for the CI-gated annotation suite.
 */
public final class OfficialAnnotationMain {

    private OfficialAnnotationMain() {
    }

    public static void main(String[] args) throws Exception {
        OfficialAnnotationTest.runStandalone(args);
    }
}
