package org.sjf4j.testbench.schema.official;

/**
 * Standalone Application entrypoint for official compiled JSON Schema cases.
 * Arguments: [dialect [file.json [groupIndex [testIndex]]]].
 * The dialect defaults to all; use draft7, draft2019-09, or draft2020-12 to narrow.
 * Run OfficialCompiledTest as a JUnit test to see individual dynamic cases in the IDE.
 */
public final class OfficialCompiledMain {

    private OfficialCompiledMain() {
    }

    public static void main(String[] args) throws Exception {
//        OfficialCompiledTest.runStandalone(args);
        OfficialCompiledTest.runStandalone(new String[]{"draft2019-09", "ref.json", "3", "0"});
    }
}
