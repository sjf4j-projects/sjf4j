package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationOptions;
import org.sjf4j.schema.ValidationResult;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Official JSON Schema annotation conformance suite.
 *
 * <p>JUnit runs every file and reports all results. Until the remaining annotation
 * vocabularies are implemented, the CI gate enforces the metadata and applicator
 * suites and reports the other files as pending (without concealing failures).
 * The main method is strict for any selected file or the entire suite.
 */
public final class OfficialAnnotationTest {
    private static final String TEST_ROOT_PROPERTY = "sjf4j.schema.officialTestRoot";
    private static final String SUITE_DIRECTORY = "official-json-schema-tests/suite";
    private static final ValidationOptions OPTIONS = ValidationOptions.builder()
            .collectAnnotations(true).build();

    /**
     * Run all files, or select one file/group/test:
     * OfficialAnnotationTest [suite-root] [file.json] [group-filter] [test-filter].
     *
     * <p>Group/test selectors match description substrings or zero-based indices
     * prefixed with '#', e.g. applicators.json '#5' '#0'.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException("Usage: OfficialAnnotationTest "
                    + "[<JSON-Schema-Test-Suite root>] [<file.json>] [<group-filter>] [<test-filter>]");
        }
        Path root = locateTestRoot(args.length > 0 ? args[0] : null);
        String file = args.length > 1 ? args[1] : null;
        String groupFilter = args.length > 2 ? args[2] : null;
        String testFilter = args.length > 3 ? args[3] : null;
        Report report = run(root, file, groupFilter, testFilter);
        if (report.total == 0) throw new IllegalArgumentException("No annotation tests matched");
        if (report.failed != 0 || report.errors != 0) {
            throw new AssertionError("Annotation suite: " + report.failed
                    + " failed, " + report.errors + " errors");
        }
    }

    /**
     * Execute the complete official suite. Only implemented vocabularies are
     * release-gated until the remaining files are explicitly supported.
     */
    @Test
    public void officialAnnotations() throws Exception {
        Path root = locateTestRoot(null);
        Report all = run(root, null, null, null);
        if (all.total == 0) throw new AssertionError("No official annotation tests found");

        // Reuse the single full-suite run rather than re-evaluating files for CI.
        assertPassing(all, "meta-data.json", 7);
        assertPassing(all, "applicators.json", 15);
        assertPassing(all, "unevaluated.json", 20);
        assertPassing(all, "content.json", 7);
        assertPassing(all, "format.json", 1);
        assertPassing(all, "unknown.json", 1);

        // Compatibility "9999" requires a future dialect and cannot be gated
        // as Draft 2020-12 conformance. Report these in the full-suite run.
        Report core2020 = runFile(root.resolve("annotations/tests/core.json"), null, null, true);
        if (core2020.total != 4 || core2020.failed != 0 || core2020.errors != 0) {
            throw new AssertionError("Official Draft 2020-12 core annotations: "
                    + core2020.passed + "/" + core2020.total + ", failed "
                    + core2020.failed + ", errors " + core2020.errors
                    + ", details: " + core2020.failures);
        }
    }

    private static Path locateTestRoot(String argumentRoot) {
        String property = System.getProperty(TEST_ROOT_PROPERTY);
        if (property != null && !property.isEmpty()) return Paths.get(property);
        if (argumentRoot != null && !argumentRoot.isEmpty()) return Paths.get(argumentRoot);

        Path moduleRoot = Paths.get("build", SUITE_DIRECTORY);
        if (Files.isDirectory(moduleRoot)) return moduleRoot;
        Path repositoryRoot = Paths.get("sjf4j-testbench", "build", SUITE_DIRECTORY);
        if (Files.isDirectory(repositoryRoot)) return repositoryRoot;
        throw new IllegalStateException("Official JSON Schema test suite not found. Run "
                + "./gradlew :sjf4j-testbench:downloadOfficialSchemaTests, or supply -D"
                + TEST_ROOT_PROPERTY + "=<suite-root> or a <suite-root> main argument.");
    }

    private static Report run(Path root, String file, String groupFilter,
                              String testFilter) throws Exception {
        Path directory = root.resolve("annotations").resolve("tests").normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Official annotation suite missing: " + directory);
        }

        Report total = new Report();
        if (file != null && !file.isEmpty()) {
            Path selected = directory.resolve(file).normalize();
            if (!selected.startsWith(directory) || !Files.isRegularFile(selected)
                    || !selected.getFileName().toString().endsWith(".json")) {
                throw new IllegalArgumentException("Annotation suite file not found: " + selected);
            }
            Report fileReport = runFile(selected, groupFilter, testFilter);
            total.add(fileReport);
            total.files.put(selected.getFileName().toString(), fileReport);
        } else {
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))::iterator) {
                    Report fileReport = runFile(path, groupFilter, testFilter);
                    total.add(fileReport);
                    total.files.put(path.getFileName().toString(), fileReport);
                }
            }
        }
        System.out.println("========================================");
        System.out.println("Official Annotation Suite Summary");
        System.out.println("Total: " + total.total + "  Passed: " + total.passed
                + "  Failed: " + total.failed + "  Error: " + total.errors);
        System.out.println("========================================");
        return total;
    }

    private static Report runFile(Path file, String groupFilter,
                                  String testFilter) throws Exception {
        return runFile(file, groupFilter, testFilter, false);
    }

    private static Report runFile(Path file, String groupFilter,
                                  String testFilter, boolean skipFuture) throws Exception {
        JsonObject suite = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonObject.class);
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12);
        JsonArray groups = suite.getJsonArray("suite");
        Report report = new Report();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            JsonObject group = groups.getJsonObject(groupIndex);
            String groupDescription = group.getString("description", "");
            if (!selected(groupFilter, groupIndex, groupDescription)) continue;
            if (skipFuture && "9999".equals(group.getString("compatibility"))) continue;

            JsonArray tests = group.getJsonArray("tests");
            SchemaPlan plan = null;
            Exception compileError = null;
            Map<URI, String> resourceLocations = new LinkedHashMap<>();
            indexResourceLocations(group.getNode("schema"), URI.create("sjf4j:/"),
                    "#", resourceLocations);
            for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
                JsonObject test = tests.getJsonObject(testIndex);
                String description = test.getString("description", "");
                if (!selected(testFilter, testIndex, description)) continue;

                report.total++;
                String label = file.getFileName() + " group#" + groupIndex
                        + " (" + groupDescription + ") test#" + testIndex
                        + (description.isEmpty() ? "" : " (" + description + ")");
                try {
                    if (plan == null && compileError == null) {
                        try {
                            plan = JsonSchema.fromNode(group.getNode("schema")).createPlan(registry);
                        } catch (Exception e) {
                            compileError = e;
                        }
                    }
                    if (compileError != null) throw compileError;
                    checkAnnotations(plan.validate(test.getNode("instance"), OPTIONS),
                            test, label, resourceLocations);
                    report.passed++;
                    System.out.println("PASS " + label);
                } catch (AssertionError e) {
                    report.failed++;
                    report.failures.add(label + ": " + e.getMessage());
                    System.out.println("FAIL " + label + ": " + e.getMessage());
                } catch (Exception e) {
                    report.errors++;
                    report.failures.add(label + ": " + e);
                    System.out.println("ERROR " + label + ": " + e);
//                    e.printStackTrace(System.out);
                }
            }
        }
        System.out.println(file.getFileName() + " [ " + report.passed + " / " + report.total
                + " ] Failed: " + report.failed + " Error: " + report.errors);
        return report;
    }

    private static boolean selected(String filter, int index, String description) {
        if (filter == null || filter.isEmpty()) return true;
        if (filter.startsWith("#")) return filter.substring(1).equals(Integer.toString(index));
        return description.contains(filter);
    }

    private static void checkAnnotations(ValidationResult result, JsonObject test, String label,
                                         Map<URI, String> resourceLocations) {
        if (!result.isValid()) {
            throw new AssertionError("validation failed: " + result.getErrors());
        }
        JsonArray assertions = test.getJsonArray("assertions");
        for (int i = 0; i < assertions.size(); i++) {
            JsonObject assertion = assertions.getJsonObject(i);
            String instanceLocation = assertion.getString("location", "");
            String keyword = assertion.getString("keyword");
            JsonObject expected = assertion.getJsonObject("expected");
            Map<String, Object> actual = new LinkedHashMap<>();

            for (ValidationResult.Message message : result.getAnnotations()) {
                if (!keyword.equals(message.getKeyword())) continue;
                if (!instanceLocation.equals(message.getInstancePs().rootedPointerExpr())) continue;

                String resourcePath = resourceLocations.getOrDefault(message.getSchemaUri(), "#");
                String pointer = resourcePath + message.getKeywordPs().rootedPointerExpr();
                String suffix = "/" + keyword.replace("~", "~0").replace("/", "~1");
                if (!pointer.endsWith(suffix)) {
                    throw new AssertionError("invalid keyword location: " + pointer);
                }
                String location = pointer.substring(0, pointer.length() - suffix.length());
                if (actual.containsKey(location)) {
                    throw new AssertionError("duplicate annotation at " + location);
                }
                actual.put(location, message.getValue());
            }

            if (expected.size() != actual.size()) {
                throw new AssertionError(label + ": " + keyword + " expected "
                        + expected.keySet() + ", actual " + actual);
            }
            for (String location : expected.keySet()) {
                // Official output locations may percent-encode JSON Pointer fragments
                // (e.g. %5E for '^'). PathSegment stores the decoded token.
                String decoded = URLDecoder.decode(location.replace("+", "%2B"),
                        StandardCharsets.UTF_8);
                if (!actual.containsKey(decoded)
                        || !Nodes.equals(expected.getNode(location), actual.get(decoded))) {
                    throw new AssertionError(label + ": " + keyword + " at " + location
                            + " expected " + expected.getNode(location) + ", actual " + actual);
                }
            }
        }
    }

    /**
     * Map absolute resource URIs to their original positions in the test schema.
     * Nested $id starts a new keyword-pointer scope, but the official suite's
     * expected locations are relative to the original test schema document.
     */
    private static void indexResourceLocations(Object node, URI inheritedUri, String path,
                                               Map<URI, String> result) {
        if (node instanceof JsonObject || node instanceof Map) {
            // Parsed test schema children may be ordinary Map/List nodes rather
            // than JsonObject/JsonArray. Index both representations.
            Object rawId = node instanceof JsonObject
                    ? ((JsonObject) node).getNode("$id")
                    : ((Map<?, ?>) node).get("$id");
            URI resourceUri = inheritedUri;
            if (rawId instanceof String) {
                resourceUri = inheritedUri.resolve((String) rawId);
                result.put(URI.create(resourceUri.toString().split("#", 2)[0]), path);
            }
            URI childResourceUri = resourceUri;
            Nodes.forEachObject(node, (key, value) -> indexResourceLocations(value,
                    childResourceUri,
                    path + "/" + key.replace("~", "~0").replace("/", "~1"), result));
        } else if (node instanceof JsonArray || node instanceof java.util.List) {
            Nodes.forEachArray(node, (index, value) -> indexResourceLocations(value,
                    inheritedUri, path + "/" + index, result));
        }
    }

    private static void assertPassing(Report all, String filename, int expectedCount) {
        Report file = all.files.get(filename);
        if (file == null || file.total != expectedCount) {
            throw new AssertionError("Official annotation suite changed: " + filename
                    + " expected " + expectedCount + " tests, got "
                    + (file == null ? "missing" : file.total));
        }
        if (file.failed != 0 || file.errors != 0) {
            throw new AssertionError("Official annotations failed in " + filename
                    + ": " + file.failed + " failures, " + file.errors + " errors");
        }
    }

    private static final class Report {
        final Map<String, Report> files = new LinkedHashMap<>();
        int total;
        int passed;
        int failed;
        int errors;
        final java.util.List<String> failures = new java.util.ArrayList<>();

        void add(Report other) {
            total += other.total;
            passed += other.passed;
            failed += other.failed;
            errors += other.errors;
            failures.addAll(other.failures);
        }
    }
}
