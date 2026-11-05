package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for filtering official compiled JSON Schema cases.
 * Run OfficialCompiledTest as a JUnit test to see individual dynamic cases in the IDE.
 */
public final class OfficialCompiledMain {

    private OfficialCompiledMain() {
    }

    public static void main(String[] args) throws Exception {
        OfficialCompiledTest.runStandalone(args);
    }
}
