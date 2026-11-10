package org.sjf4j.processor;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Compilation-error cases selected from the kinds of processor diagnostics
 * tested by MapStruct, expressed using SJF4J's actual annotation contracts.
 */
public class CompiledMapperDiagnosticsTest {

    @TestFactory
    Stream<DynamicTest> invalidMapperDeclarations() {
        return Stream.of(
                new InvalidCase("generic mapper interface",
                        "@CompiledMapper public interface Mapper<T> { T map(T source); }",
                        "@CompiledMapper interface cannot declare type parameters"),
                new InvalidCase("generic mapper method",
                        "@CompiledMapper public interface Mapper { <T> T map(T source); }",
                        "mapper method cannot declare type parameters"),
                new InvalidCase("create method without source",
                        "@CompiledMapper public interface Mapper { String map(); }",
                        "mapper method must declare at least one source parameter"),
                new InvalidCase("update method without source",
                        "@CompiledMapper public interface Mapper { void update(String target); }",
                        "update mapper method must declare target parameter"),
                new InvalidCase("primitive update target",
                        "@CompiledMapper public interface Mapper { void update(int target, String source); }",
                        "update mapper target cannot be primitive"),
                new InvalidCase("duplicate target mapping",
                        fixtures()
                        + "@Mapping(target = \"value\", source = \"value\")"
                        + "@Mapping(target = \"value\", ignore = true)"
                        + "Target map(Source source); }",
                        "duplicate mapping target 'value'"),
                new InvalidCase("source and sources are mutually exclusive",
                        fixtures()
                        + "@Mapping(target = \"value\", source = \"value\", "
                        + "sources = {\"value\"}, compute = \"(v) -> v\")"
                        + "Target map(Source source); }",
                        "cannot declare both source and sources"),
                new InvalidCase("sources require compute",
                        fixtures()
                        + "@Mapping(target = \"value\", sources = {\"value\"})"
                        + "Target map(Source source); }",
                        "sources may be used only with compute"),
                new InvalidCase("ignored mapping cannot compute",
                        fixtures()
                        + "@Mapping(target = \"value\", ignore = true, "
                        + "compute = \"(v) -> v\")"
                        + "Target map(Source source); }",
                        "cannot declare sources, compute or nestedMapper"),
                new InvalidCase("ignored target path is forbidden",
                        fixtures()
                        + "@Mapping(target = \"$.value\", ignore = true)"
                        + "Target map(Source source); }",
                        "does not support target paths"),
                new InvalidCase("ensure target must be a path",
                        fixtures()
                        + "@EnsureMapping(target = \"value\", source = \"value\")"
                        + "Target map(Source source); }",
                        "target must be a JSONPath or JSON Pointer"),
                new InvalidCase("ensure path cannot use array index",
                        fixtures()
                        + "@EnsureMapping(target = \"$.values[0]\", source = \"value\")"
                        + "Target map(Source source); }",
                        "target path supports only name segments"),
                new InvalidCase("array update policy on create mapper",
                        fixtures()
                        + "@Mapping(target = \"value\", source = \"value\", array = ArrayPolicy.ADD)"
                        + "Target map(Source source); }",
                        "@Mapping.array is supported only on update mapper methods"),
                new InvalidCase("object update policy on create mapper",
                        fixtures()
                        + "@Mapping(target = \"value\", source = \"value\", "
                        + "object = ObjectPolicy.PUT_IF_ABSENT)"
                        + "Target map(Source source); }",
                        "@Mapping.object is supported only on update mapper methods")
        ).map(testCase -> dynamicTest(testCase.name(), () -> {
            String source = "package fixture; import org.sjf4j.annotation.mapping.*; "
                    + testCase.mapperSource();
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    Map.of("fixture/Mapper.java", source), CodegenProcessor.class);
            assertFalse(result.success, result.diagnostics());
            assertTrue(result.diagnostics().contains(testCase.diagnosticFragment()),
                    result.diagnostics());
        }));
    }

    private static String fixtures() {
        return "@CompiledMapper public interface Mapper { "
                + "class Source { public String value; } "
                + "class Target { public String value; } ";
    }

    private record InvalidCase(String name, String mapperSource, String diagnosticFragment) {}
}
