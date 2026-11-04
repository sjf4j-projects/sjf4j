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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Official JSON-Schema-Test-Suite annotation conformance.
 *
 * <p>The automated test runs the metadata vocabulary only (first runtime phase).
 * The main method can run a selected annotation suite file, group, and test
 * for debugging. Group and test selectors match description substrings, or
 * zero-based indices when prefixed with '#'.
 */
public final class OfficialAnnotationTest {
    private static final String TEST_ROOT_PROPERTY = "sjf4j.schema.officialTestRoot";
    private static final String SUITE_DIRECTORY = "official-json-schema-tests/suite";
    private static final String DEFAULT_FILE = "meta-data.json";

    private static final ValidationOptions OPTIONS = ValidationOptions.builder()
            .collectAnnotations(true).build();

    /**
     * Usage: OfficialAnnotationTest [suite-root] [file] [group-filter] [test-filter].
     *
     * <p>With no arguments, uses the cached suite and runs meta-data.json.
     * Examples: meta-data.json '#2' '#0', or applicators.json anyOf.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException("Usage: OfficialAnnotationTest "
                    + "[<JSON-Schema-Test-Suite root>] [<file>] [<group-filter>] [<test-filter>]");
        }
        Path root = locateTestRoot(args.length > 0 ? args[0] : null);
        String file = args.length > 1 ? args[1] : DEFAULT_FILE;
        String groupFilter = args.length > 2 ? args[2] : null;
        String testFilter = args.length > 3 ? args[3] : null;
        runFile(root, file, groupFilter, testFilter);
    }

    @Test
    public void metadataVocabulary() throws Exception {
        runFile(locateTestRoot(null), DEFAULT_FILE, null, null);
    }

    private static Path locateTestRoot(String argumentRoot) {
        String property = System.getProperty(TEST_ROOT_PROPERTY);
        if (property != null && !property.isEmpty()) {
            return Paths.get(property);
        }
        if (argumentRoot != null && !argumentRoot.isEmpty()) {
            return Paths.get(argumentRoot);
        }
        Path moduleRoot = Paths.get("build", SUITE_DIRECTORY);
        if (Files.isDirectory(moduleRoot)) return moduleRoot;
        Path repositoryRoot = Paths.get("sjf4j-testbench", "build", SUITE_DIRECTORY);
        if (Files.isDirectory(repositoryRoot)) return repositoryRoot;
        throw new IllegalStateException("Official JSON Schema test suite not found. Run "
                + "./gradlew :sjf4j-testbench:downloadOfficialSchemaTests, or supply -D"
                + TEST_ROOT_PROPERTY + "=<suite-root> or a <suite-root> main argument.");
    }

    private static void runFile(Path root, String file, String groupFilter,
                                String testFilter) throws Exception {
        Path directory = root.resolve("annotations").resolve("tests");
        Path path = directory.resolve(file).normalize();
        if (!path.startsWith(directory.normalize()) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Annotation suite file not found: " + path);
        }

        JsonObject suite = Sjf4j.global().fromJson(Files.readAllBytes(path), JsonObject.class);
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12);
        JsonArray groups = suite.getJsonArray("suite");
        int total = 0;
        int passed = 0;
        int failed = 0;
        int errors = 0;

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            JsonObject group = groups.getJsonObject(groupIndex);
            String groupDescription = group.getString("description", "");
            if (!selected(groupFilter, groupIndex, groupDescription)) continue;

            JsonArray tests = group.getJsonArray("tests");
            SchemaPlan plan = null;
            Exception compileError = null;
            for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
                JsonObject test = tests.getJsonObject(testIndex);
                String testDescription = test.getString("description", "");
                if (!selected(testFilter, testIndex, testDescription)) continue;

                total++;
                String label = file + " group#" + groupIndex + " (" + groupDescription + ") test#"
                        + testIndex + " (" + testDescription + ")";
                try {
                    if (plan == null && compileError == null) {
                        try {
                            plan = JsonSchema.fromNode(group.getNode("schema")).createPlan(registry);
                        } catch (Exception e) {
                            compileError = e;
                        }
                    }
                    if (compileError != null) throw compileError;

                    ValidationResult result = plan.validate(test.getNode("instance"), OPTIONS);
                    checkAnnotations(result, test, label);
                    passed++;
                    System.out.println("PASS " + label);
                } catch (AssertionError e) {
                    failed++;
                    System.out.println("FAIL " + label + ": " + e.getMessage());
                } catch (Exception e) {
                    errors++;
                    System.out.println("ERROR " + label + ": " + e);
                    e.printStackTrace(System.out);
                }
            }
        }

        System.out.println("================================");
        System.out.println(" JSON Schema Annotation Summary");
        System.out.println("================================");
        System.out.println("File  : " + path);
        System.out.println("Total : " + total);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Error : " + errors);
        System.out.println("================================");

        if (total == 0) {
            throw new IllegalArgumentException("No annotation tests matched the specified selectors");
        }
        if (failed != 0 || errors != 0) {
            throw new AssertionError("Official annotation tests failed: " + failed
                    + " failures, " + errors + " errors in " + file);
        }
    }

    private static boolean selected(String filter, int index, String description) {
        if (filter == null || filter.isEmpty()) return true;
        if (filter.startsWith("#")) {
            return filter.substring(1).equals(Integer.toString(index));
        }
        return description.contains(filter);
    }

    private static void checkAnnotations(ValidationResult result, JsonObject test, String label) {
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

                String pointer = "#" + message.getKeywordPs().rootedPointerExpr();
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
                if (!actual.containsKey(location)
                        || !Nodes.equals(expected.getNode(location), actual.get(location))) {
                    throw new AssertionError(label + ": " + keyword + " at " + location
                            + " expected " + expected.getNode(location) + ", actual " + actual);
                }
            }
        }
    }
}
