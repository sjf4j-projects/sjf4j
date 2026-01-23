package org.sjf4j.schema;


import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;

import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public final class OfficialTestRunner {

    private static SchemaStore _store;

    public static void main(String[] args) throws Exception {
        _store = loadRemotesToStore(locatePath("json-schema/remotes"));
        Path root = locatePath("json-schema/tests/draft2020-12");

        runTestDir(root);
//        runTestFile(root.resolve("ref.json"), "remote ref, containing refs itself", "");
//        runTestFile(root.resolve("vocabulary.json"), "", "");
    }

    private static Path locatePath(String dir) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = cl.getResource(dir);
        if (url == null) {
            throw new IllegalStateException("Not found " + dir + " in test resources");
        }
        return Paths.get(url.toURI());
    }

    private static void runTestDir(Path dir) throws Exception {
        TestSuiteReport suite = new TestSuiteReport();
        Files.walk(dir, 1)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> runTestFile(p, suite, true, null, null));
        printSuiteReport(suite);
    }

    private static void runTestFile(Path file, String groupFilter, String testFilter) throws Exception {
        TestSuiteReport suite = new TestSuiteReport();
        runTestFile(file, suite, true, groupFilter, testFilter);
    }

    private static void runTestFile(Path file,
                                    TestSuiteReport suite,
                                    boolean showDetails,
                                    String groupFilter,
                                    String testFilter) {
        String fileName = file.getFileName().toString();
        TestFileReport report = suite.file(fileName);
        String groupDesc;

        try {
            JsonArray cases = Sjf4j.fromJson(Files.readAllBytes(file), JsonArray.class);

            for (int i = 0; i < cases.size(); i++) {
                JsonObject caseObj = cases.getJsonObject(i);
                JsonSchema schema = JsonSchema.fromNode(caseObj.getNode("schema"));
                groupDesc = caseObj.getString("description", "no group desc");
                if (groupFilter != null && !groupFilter.isEmpty() && !groupDesc.contains(groupFilter))
                    continue;

                try {
                    schema.compile(_store);
                } catch (Exception e) {
                    int size = caseObj.getJsonArray("tests").size();
                    report.total += size;
                    report.error += size;
                    suite.total += size;
                    suite.error += size;
                    report.addFailure(String.format("%s \n     -> Compile error", groupDesc), e);
                    continue;
                }

                JsonArray tests = caseObj.getJsonArray("tests");
                for (int j = 0; j < tests.size(); j++) {
                    JsonObject test = tests.getJsonObject(j);
                    report.total++;
                    suite.total++;
                    String testDesc = test.getString("description", "no test desc");
                    if (testFilter != null && !testFilter.isEmpty() && !testDesc.contains(testFilter))
                        continue;

                    try {
                        Object data = test.get("data");
                        boolean expected = test.getBoolean("valid");

                        ValidationResult result = schema.validate(data);
                        boolean actual = result.isValid();

                        if (actual == expected) {
                            report.passed++;
                            suite.passed++;
                        } else {
                            report.failed++;
                            suite.failed++;
                            report.addFailure(String.format("%s \n     -> %s", groupDesc, testDesc),
                                    "expected=" + expected + ", actual=" + actual + ", " + result);
                        }

                    } catch (Exception e) {
                        report.error++;
                        suite.error++;
                        report.addFailure(String.format("%s \n     -> %s", groupDesc, testDesc), e);
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {
            report.error++;
            suite.error++;
            report.addFailure("file parse error", e);
        }

        String line;
        if (report.failed == 0 && report.error == 0) {
            line = String.format("✓ %-35s [ %4d / %4d ]", report.fileName, report.passed, report.total);
        } else {
            line = String.format("✗ %-35s [ %4d / %4d ]  Failed: %d  Error: %d",
                    report.fileName, report.passed, report.total, report.failed, report.error);
        }
        System.out.println(line);

        if (showDetails && !report.failures.isEmpty()) {
            System.out.println("  Failures / Errors:");
            for (String msg : report.failures) {
                System.out.println("   - " + msg);
            }
            System.out.println();
        }
    }


    private static final URI TEST_BASE_URI = URI.create("http://localhost:1234/");

    private static SchemaStore loadRemotesToStore(Path remotesDir) throws Exception {
        SchemaStore store = new SchemaStore();
        Files.walk(remotesDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        ObjectSchema schema = Sjf4j.fromJson(Files.readAllBytes(p), ObjectSchema.class);
                        URI uri = resolveSchemaUri(schema, remotesDir, p);
//                        System.out.println("uri: " + uri);
                        store.register(uri, schema);
                        String id = schema.getString("$id", null);
                        if (id != null) {
                            store.register(URI.create(id), schema);
                        }

                    } catch (Exception e) {
                        System.err.println("Error loading remotes: " + e.getMessage());
                    }
                });
        return store;
    }

    private static URI resolveSchemaUri(ObjectSchema schema, Path remotesDir, Path file) {
        Path relative = remotesDir.relativize(file);
        String path = relative.toString().replace('\\', '/');
        return TEST_BASE_URI.resolve(path);
    }


    static class TestSuiteReport {
        int total, passed, failed, error;
        Map<String, TestFileReport> fileMap = new LinkedHashMap<>();

        TestFileReport file(String name) {
            return fileMap.computeIfAbsent(name, TestFileReport::new);
        }

        Collection<TestFileReport> files() {
            return fileMap.values();
        }
    }

    static class TestFileReport {
        final String fileName;
        int total, passed, failed, error;
        List<String> failures = new ArrayList<>();

        TestFileReport(String name) {
            this.fileName = name;
        }

        void addFailure(String desc, Object e) {
            if (e instanceof Exception) {
                failures.add(desc + " : " + ((Exception) e).toString());
            } else {
                failures.add(desc + " : " + e);
            }
        }
    }

    private static void printSuiteReport(TestSuiteReport s) {
        System.out.println("================================");
        System.out.println(" JSON Schema Test Summary");
        System.out.println("================================");
        System.out.println("Total : " + s.total);
        System.out.println("Passed: " + s.passed);
        System.out.println("Failed: " + s.failed);
        System.out.println("Error : " + s.error);
        System.out.println("================================\n");
    }
    private static void printFileBreakdown(TestSuiteReport suite) {
        System.out.println("\n--------------------------------");
        System.out.println("File Summary");
        System.out.println("--------------------------------");

        for (TestFileReport f : suite.files()) {
            String line;
            if (f.failed == 0 && f.error == 0) {
                line = String.format("✓ %-35s [ %4d / %4d ]", f.fileName, f.passed, f.total);
            } else {
                line = String.format("✗ %-35s [ %4d / %4d ]  Failed: %d  Error: %d",
                        f.fileName, f.passed, f.total, f.failed, f.error);
            }
            System.out.println(line);
        }
    }



}
