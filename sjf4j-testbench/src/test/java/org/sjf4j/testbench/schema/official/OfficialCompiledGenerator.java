package org.sjf4j.testbench.schema.official;

import org.sjf4j.schema.SchemaDialect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates independent validator interfaces and exact schema resources from the
 * unified official Validation Suite, before programmatic javac runs in the tests.
 */
public final class OfficialCompiledGenerator {
    private static final String PACKAGE_NAME = "org.sjf4j.testbench.schema.official.generated";

    private OfficialCompiledGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected suite root and generated output directory");
        }
        generate(Path.of(args[0]), Path.of(args[1]));
    }

    static void generate(Path suiteRoot, Path outputRoot) throws IOException {
        Path input = suiteRoot.resolve("validation/tests");
        List<Path> files;
        try (Stream<Path> entries = Files.list(input)) {
            files = new ArrayList<>(entries
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList());
        }
        require(!files.isEmpty(), "No official Validation files at " + input);
        Path vocabulary = input.resolve(OfficialTestPolicy.FORMAT_VOCABULARY);
        require(Files.isRegularFile(vocabulary), "Missing supported vocabulary fixture: " + vocabulary);
        files.add(vocabulary);
        generateDraft(input, outputRoot, files, "draft7", SchemaDialect.DRAFT_07,
                "http://json-schema.org/draft-07/schema#");
        generateDraft(input, outputRoot, files, "draft2019-09", SchemaDialect.DRAFT_2019_09,
                "https://json-schema.org/draft/2019-09/schema");
        generateDraft(input, outputRoot, files, "draft2020-12", SchemaDialect.DRAFT_2020_12,
                "https://json-schema.org/draft/2020-12/schema");
    }

    private static void generateDraft(Path input, Path outputRoot, List<Path> files,
                                      String draft, SchemaDialect dialect, String dialectUri) throws IOException {
        Path output = outputRoot.resolve(draft);
        Path javaDir = output.resolve("java").resolve(PACKAGE_NAME.replace('.', '/'));
        Path resourceDir = output.resolve("resources/official").resolve(draft);
        Files.createDirectories(javaDir);
        Files.createDirectories(resourceDir);

        StringBuilder manifest = new StringBuilder("file\tgroup\tclass\ttests\n");
        int fileCount = 0, groupCount = 0, testCount = 0;
        for (Path file : files) {
            String relativeFile = input.relativize(file).toString().replace('\\', '/');
            String stem = relativeFile.substring(0, relativeFile.length() - ".json".length());
            String classStem = classStem(stem);
            Object source = OfficialFixtures.readNode(file);
            require(source instanceof Map<?, ?>, "Invalid official Validation file: " + file);
            Map<?, ?> suite = (Map<?, ?>) source;
            require(suite.get("tests") instanceof List<?>, "Missing official tests array: " + file);
            List<?> groups = (List<?>) suite.get("tests");
            int selectedInFile = 0;
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                String label = file + " #" + groupIndex;
                Object raw = groups.get(groupIndex);
                require(raw instanceof Map<?, ?>, "Invalid official group: " + label);
                Map<?, ?> group = (Map<?, ?>) raw;
                if (!OfficialCompatibility.matches((String) group.get("compatibility"), dialect)) continue;
                Object tags = group.get("tags");
                if (tags instanceof List<?> list) {
                    require(list.stream().allMatch(tag -> "format-assertion".equals(tag)
                            || "format-annotation".equals(tag)), "Unknown official tags: " + label);
                }
                require(group.containsKey("schema"), "Missing schema: " + label);
                require(group.get("tests") instanceof List<?>, "Missing tests: " + label);
                List<?> tests = (List<?>) group.get("tests");
                require(!tests.isEmpty(), "Empty official tests: " + label);

                String index = String.format(Locale.ROOT, "%03d", groupIndex);
                String className = "Official" + classStem + "G" + index;
                String schemaPath = stem + "/" + index + ".json";
                OfficialFixtures.writeNode(resourceDir.resolve(schemaPath),
                        withDialect(group.get("schema"), dialect, dialectUri));

                String externalAttribute = "";
                if (group.get("externalSchemas") instanceof Map<?, ?> external) {
                    Map<String, Object> documents = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : external.entrySet()) {
                        require(entry.getKey() instanceof String, "Non-string external URI: " + label);
                        documents.put((String) entry.getKey(), withDialect(entry.getValue(), dialect, dialectUri));
                    }
                    String externalPath = "external/" + schemaPath;
                    OfficialFixtures.writeNode(resourceDir.resolve(externalPath), documents);
                    externalAttribute = ", externalSchemasRef = \"classpath:/official/" + draft
                            + "/" + externalPath + "\"";
                }
                for (boolean fallback : new boolean[]{false, true}) {
                    String variant = fallback ? className + "Fallback" : className;
                    String javaSource = """
                            package %s;

                            import org.sjf4j.annotation.schema.CompiledValidator;
                            import org.sjf4j.annotation.schema.ValidJsonSchema;

                            @CompiledValidator(fallback = %s)
                            public interface %s {
                                @ValidJsonSchema(ref = "classpath:/official/%s/%s"%s)
                                boolean valid(Object value, boolean strictFormat);
                            }
                            """.formatted(PACKAGE_NAME, fallback, variant, draft, schemaPath, externalAttribute);
                    Files.writeString(javaDir.resolve(variant + ".java"), javaSource);
                }
                manifest.append(relativeFile).append('\t').append(groupIndex)
                        .append('\t').append(className).append('\t').append(tests.size()).append('\n');
                selectedInFile++;
                groupCount++;
                testCount += tests.size();
            }
            if (selectedInFile > 0) fileCount++;
        }
        require(groupCount > 0, "No compatible official cases for " + draft);
        Files.writeString(output.resolve("index.tsv"), manifest);
        Files.writeString(output.resolve("counts.properties"),
                "files=" + fileCount + "\ngroups=" + groupCount + "\ntests=" + testCount + "\n");
        System.out.printf("Generated %s: %d files, %d groups, %d assertions%n",
                draft, fileCount, groupCount, testCount);
    }

    private static String classStem(String stem) {
        StringBuilder result = new StringBuilder();
        for (String segment : stem.split("[^a-zA-Z0-9]+")) {
            if (!segment.isEmpty()) {
                result.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
            }
        }
        return result.toString();
    }

    private static Object withDialect(Object schema, SchemaDialect dialect, String dialectUri) {
        if (dialect == SchemaDialect.DRAFT_2020_12
                || !(schema instanceof Map<?, ?> map) || map.containsKey("$schema")) {
            return schema;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$schema", dialectUri);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            require(entry.getKey() instanceof String, "Non-string schema key");
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
