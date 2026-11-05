package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ObjectSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OfficialRuntimeTest {

    private static final String TEST_ROOT_PROPERTY = "sjf4j.schema.officialTestRoot";
    private static final String SUITE_DIRECTORY = "official-json-schema-tests/suite";


    /**
     * Program arguments: [dialect [file.json [groupIndex [testIndex]]]].
     * Gradle/JUnit always run all non-optional cases for every dialect.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "Usage: OfficialRuntimeTest [dialect [file.json [groupIndex [testIndex]]]]");
        }
        List<SchemaDialect> dialects = standaloneDialects(args.length == 0 ? null : args[0]);
        String file = args.length > 1 ? args[1] : null;
        Integer group = args.length > 2 ? Integer.parseInt(args[2]) : null;
        Integer test = args.length > 3 ? Integer.parseInt(args[3]) : null;
        if (file != null && (!file.endsWith(".json") || file.contains("/") || file.contains("\\\\"))) {
            throw new IllegalArgumentException("Expected a root official file.json: " + file);
        }
        if (group != null && (group < 0 || file == null)) {
            throw new IllegalArgumentException("Specify file.json and a nonnegative group index");
        }
        if (test != null && (test < 0 || group == null)) {
            throw new IllegalArgumentException("Specify group index and a nonnegative test index");
        }
        Path root = locateTestRoot();
        for (SchemaDialect dialect : dialects) {
            SchemaRegistry registry = new SchemaRegistry(dialect);
            loadRemotes(registry, locatePath(root, "remotes"));
            String dir = "tests/" + draftName(dialect);
            runTestDir(registry, locatePath(root, dir), false, true, file, group, test);
        }
    }

    static List<SchemaDialect> standaloneDialects(String dialect) {
        if (dialect == null || "all".equals(dialect)) {
            return List.of(SchemaDialect.DRAFT_07,
                    SchemaDialect.DRAFT_2019_09, SchemaDialect.DRAFT_2020_12);
        }
        switch (dialect) {
            case "draft7": return List.of(SchemaDialect.DRAFT_07);
            case "draft2019-09": return List.of(SchemaDialect.DRAFT_2019_09);
            case "draft2020-12": return List.of(SchemaDialect.DRAFT_2020_12);
            default: throw new IllegalArgumentException("Invalid official dialect: " + dialect);
        }
    }

    private static String draftName(SchemaDialect dialect) {
        switch (dialect) {
            case DRAFT_07: return "draft7";
            case DRAFT_2019_09: return "draft2019-09";
            case DRAFT_2020_12: return "draft2020-12";
            default: throw new IllegalArgumentException("Unsupported dialect: " + dialect);
        }
    }

    // Publication and everyday-build conformance: all required cases, no optional/ subtree.
    @Test
    public void testDraft7() throws Exception {
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_07);
        loadRemotes(registry, locatePath("remotes"));
        runTestDir(registry, locatePath("tests/draft7"), false, true);
    }

    @Test
    public void testDraft2019_09() throws Exception {
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2019_09);
        loadRemotes(registry, locatePath("remotes"));
        runTestDir(registry, locatePath("tests/draft2019-09"), false, true);
    }

    @Test
    public void testDraft2020_12() throws Exception {
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12);
        loadRemotes(registry, locatePath("remotes"));
        runTestDir(registry, locatePath("tests/draft2020-12"), false,true);
    }


    private static Path locatePath(String dir) {
        return locatePath(locateTestRoot(), dir);
    }

    private static Path locatePath(Path root, String dir) {
        Path path = root.resolve(dir);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Not found " + dir + " under " + root);
        }
        return path;
    }

    private static Path locateTestRoot() {
        String root = System.getProperty(TEST_ROOT_PROPERTY);
        if (root != null && !root.isEmpty()) {
            return Paths.get(root);
        }
        Path moduleRoot = Paths.get("build", SUITE_DIRECTORY);
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        Path repositoryRoot = Paths.get("sjf4j-testbench", "build", SUITE_DIRECTORY);
        if (Files.isDirectory(repositoryRoot)) {
            return repositoryRoot;
        }
        throw new IllegalStateException("Official JSON Schema test suite not found. Run ./gradlew :sjf4j-testbench:downloadOfficialSchemaTests, " +
                "or supply -D" + TEST_ROOT_PROPERTY + "=<suite-root>.");
    }

    private static void runTestDir(SchemaRegistry registry, Path dir, boolean strict, boolean canThrow) throws Exception {
        runTestDir(registry, dir, strict, canThrow, null, null, null);
    }

    private static void runTestDir(SchemaRegistry registry, Path dir, boolean strict, boolean canThrow,
                                   String fileName, Integer groupIndex, Integer testIndex) throws Exception {
        TestSuiteReport suite = new TestSuiteReport(registry.getDefaultDialect(), dir);
        if (fileName != null) {
            Path path = dir.resolve(fileName);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Official test file not found: " + path);
            }
            runTestFile(registry, path, strict, suite, true, groupIndex, testIndex);
        } else {
            try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
                paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(Files::isRegularFile)
                        .sorted()
                        .forEach(p -> runTestFile(registry, p, strict, suite, true, null, null));
            }
        }
        printSuiteReport(suite);
        if (suite.total == 0 || (canThrow && (suite.error > 0 || suite.failed > 0))) {
            throw new IllegalStateException("JSON Schema Official tests " +
                    (suite.total == 0 ? "matched no examples" : "failed") + " for " + suite.dialect
                    + " in " + suite.directory + ": " + suite.failed + " failed and "
                    + suite.error + " errors");
        }
    }


    private static void runTestFile(SchemaRegistry registry, Path file, boolean strict, TestSuiteReport suite,
                                    boolean showDetails, Integer groupFilter, Integer testFilter) {
        String fileName = file.getFileName().toString();
        TestFileReport report = suite.file(fileName);
        String groupDesc;

        try {
            JsonArray cases = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonArray.class);

            for (int i = 0; i < cases.size(); i++) {
                JsonObject caseObj = cases.getJsonObject(i);
                JsonSchema schema = JsonSchema.fromNode(caseObj.getNode("schema"));
                groupDesc = caseObj.getString("description", "no group desc");
                if (groupFilter != null && groupFilter != i) continue;

                JsonArray tests = caseObj.getJsonArray("tests");
                if (testFilter != null && testFilter >= tests.size()) {
                    throw new IllegalArgumentException(fileName + " group#" + i
                            + ": test index out of range: " + testFilter);
                }
                SchemaPlan plan;
                try {
                    plan = schema.createPlan(registry);
                } catch (Exception e) {
                    int size = testFilter == null ? tests.size() : 1;
                    report.total += size;
                    report.error += size;
                    suite.total += size;
                    suite.error += size;
                    report.addFailure(String.format("%s \n     -> Compile error", groupDesc), e);
                    continue;
                }

                for (int j = 0; j < tests.size(); j++) {
                    JsonObject test = tests.getJsonObject(j);
                    if (testFilter != null && testFilter != j) continue;
                    report.total++;
                    suite.total++;
                    String testDesc = test.getString("description", "no test desc");

                    try {
                        Object data = test.get("data");
                        boolean expected = test.getBoolean("valid");

                        ValidationResult result = plan.validate(data, true, strict);
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

    private static void loadRemotes(SchemaRegistry registry, Path remotesDir) throws Exception {
        Files.walk(remotesDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        ObjectSchema schema = Sjf4j.global().fromJson(Files.readAllBytes(p), ObjectSchema.class);
                        URI uri = resolveSchemaUri(schema, remotesDir, p);
//                        System.out.println("uri: " + uri);
                        registry.index(uri, schema);
                    } catch (Exception e) {
                        throw new AssertionError("Failed to load remote schema: " + p, e);
                    }
                });
    }

    private static URI resolveSchemaUri(ObjectSchema schema, Path remotesDir, Path file) {
        Path relative = remotesDir.relativize(file);
        String path = relative.toString().replace('\\', '/');
        return TEST_BASE_URI.resolve(path);
    }


    static class TestSuiteReport {
        final SchemaDialect dialect;
        final Path directory;
        int total, passed, failed, error;
        Map<String, TestFileReport> fileMap = new LinkedHashMap<>();

        TestSuiteReport(SchemaDialect dialect, Path directory) {
            this.dialect = dialect;
            this.directory = directory;
        }

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
        System.out.println(" JSON Schema Test Summary [" + s.dialect + "]");
        System.out.println("================================");
        System.out.println("Directory: " + s.directory);
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
