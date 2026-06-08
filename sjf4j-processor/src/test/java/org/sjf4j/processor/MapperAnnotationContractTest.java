package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapperAnnotationContractTest {

    @Test
    public void rejectsInvalidMappingAnnotationContracts() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-contract-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadContracts.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "class Source { public String name; public String other; public Child child; }\n" +
                        "class Child { public String name; } class ChildDto { public String name; public ChildDto() {} }\n" +
                        "class Target { public String name; public List<String> list; public Map<String,String> map; public ChildDto child; public Target() {} }\n" +
                        "@CompiledMapper interface BadContracts {\n" +
                        "  @Mapping(target=\"name\", source=\"other\", ignore=true) Target ignoreSource(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"n -> n\", ignore=true) Target ignoreCompute(Source s);\n" +
                        "  @Mapping(target=\"child\", source=\"child\", ignore=true) Target ignoreNested(Source s);\n" +
                        "  @Mapping(target=\"list\", array=ArrayPolicy.ADD, ignore=true) void ignoreArray(Target t, Source s);\n" +
                        "  @Mapping(target=\"map\", object=ObjectPolicy.CLEAR_PUT, ignore=true) void ignoreObject(Target t, Source s);\n" +
                        "  @Mapping(target=\"name\", sources={\"name\"}) Target sourcesWithoutCompute(Source s);\n" +
                        "  @Mapping(target=\"list\", array=ArrayPolicy.ADD) Target createArrayPolicy(Source s);\n" +
                        "  @Mapping(target=\"map\", object=ObjectPolicy.CLEAR_PUT) Target createObjectPolicy(Source s);\n" +
                        "  @Mapping(target=\"name\", sources={\"name\"}, compute=\"n -> { return n; }\") Target computeBlock(Source s);\n" +
                        "  @Mapping(target=\"name\", sources={\"name\"}, compute=\"n -> return n\") Target computeReturn(Source s);\n" +
                        "  @Mapping(target=\"name\", sources={\"name\"}, compute=\"n -> n;\") Target computeSemicolon(Source s);\n" +
                        "  @MapperOptions(using={\"bad ref\"}) Target badUsing(Source s);\n" +
                        "  @Mapping(target=\"$.map.name\", source=\"name\") @Mapping(target=\"$.map.name\", source=\"other\") Target duplicatePath(Source s);\n" +
                        "  default ChildDto toDto(Child c) { return new ChildDto(); }\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadContracts.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@Mapping.ignore cannot be combined with source, sources, compute, array, or object"), messages);
        assertTrue(messages.contains("@Mapping.sources may be used only with @Mapping.compute"), messages);
        assertTrue(messages.contains("@Mapping.array and @Mapping.object are supported only on void update mapper methods"), messages);
        assertTrue(messages.contains("@Mapping.compute supports only expression bodies"), messages);
        assertTrue(messages.contains("@MapperOptions.using expects 'method', 'this::method', 'ImportedMapper::method', or 'pkg.ImportedMapper::method'"), messages);
        assertTrue(messages.contains("Duplicate target path '$.map.name'"), messages);
    }

    private static void write(Path path, String text) throws Exception {
        File file = path.toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }
}
