package org.sjf4j.processor;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * MapStruct selection/constructor/factory/erroneous-inspired negative cases.
 * Failures are deliberately observable as javac diagnostics, not hidden by
 * disabling tests or implementing fallback behavior.
 */
public class CompiledMapperBatch2DiagnosticsTest {

    @TestFactory
    Stream<DynamicTest> selectionAndCreationDiagnostics() {
        return Stream.of(
                invalid("ambiguous local converter candidates",
                        "@CompiledMapper public interface Mapper {"
                        + "class Leaf { public String name; } class Value { public String name; } "
                        + "record From(Leaf leaf) {} record To(Value leaf) {} "
                        + "To map(From source); "
                        + "default Value one(Leaf x) { return new Value(); } "
                        + "default Value two(Leaf x) { return new Value(); } }",
                        "ambiguous local mapper methods"),
                invalid("ambiguous imported converter candidates",
                        "@CompiledMapper interface First { default Mapper.Value one(Mapper.Leaf x) { return new Mapper.Value(); } } "
                        + "@CompiledMapper interface Second { default Mapper.Value two(Mapper.Leaf x) { return new Mapper.Value(); } } "
                        + "@CompiledMapper(importing={First.class,Second.class}) public interface Mapper { "
                        + "class Leaf { public String name; } class Value { public String name; } "
                        + "record From(Leaf leaf) {} record To(Value leaf) {} "
                        + "To map(From source); }",
                        "ambiguous imported mapper methods"),
                invalid("explicit nested mapper is incompatible",
                        "@CompiledMapper public interface Mapper { "
                        + "class Leaf { public String name; } class Value { public String name; } "
                        + "record From(Leaf leaf) {} class To { public Value leaf; } "
                        + "@MappingIfParentPresent(target=\"$.leaf\",source=\"leaf\",nestedMapper=\"this::wrong\") "
                        + "void update(To target, From source); "
                        + "default Value wrong(Integer value) { return new Value(); } }",
                        "nestedMapper 'this::wrong' cannot map"),
                invalid("unresolved preferred method reference",
                        "@CompiledMapper public interface Mapper { "
                        + "record Source(String name) {} record Target(String name) {} "
                        + "@MappingOptions(using={\"this::missing\"}) Target map(Source source); }",
                        "using reference 'this::missing' cannot be resolved"),
                invalid("duplicate MappingCreator rules",
                        "@CompiledMapper @MappingCreator(targetType=Mapper.Target.class,implementation=Mapper.Target.class) "
                        + "@MappingCreator(targetType=Mapper.Target.class,implementation=Mapper.Target.class) "
                        + "public interface Mapper { "
                        + "class Target { public String name; } "
                        + "record Source(String name) {} Target map(Source source); }",
                        "duplicate @MappingCreator rules"),
                invalid("ambiguous MappingCreator supertypes",
                        "@CompiledMapper @MappingCreator(targetType=Mapper.Left.class,implementation=Mapper.Target.class) "
                        + "@MappingCreator(targetType=Mapper.Right.class,implementation=Mapper.Target.class) "
                        + "public interface Mapper { "
                        + "interface Left {} interface Right {} "
                        + "class Target implements Left,Right { public String name; } "
                        + "record Source(String name) {} Target map(Source source); }",
                        "ambiguous @MappingCreator rules"),
                invalid("missing MappingCreator factory method",
                        "@CompiledMapper public interface Mapper { "
                        + "class Target { public String name; } record Source(String name) {} "
                        + "@MappingCreator(targetType=Target.class,creator=\"this::missing\") "
                        + "Target map(Source source); }",
                        "factory method 'missing' was not found"),
                invalid("MappingCreator implementation incompatible with target",
                        "@CompiledMapper public interface Mapper { "
                        + "interface View { String getName(); } "
                        + "class Wrong { public String name; } record Source(String name) {} "
                        + "@MappingCreator(targetType=View.class,implementation=Wrong.class) "
                        + "View map(Source source); }",
                        "is not assignable to targetType"),
                invalid("multiple constructors with no no-args",
                        "@CompiledMapper public interface Mapper { "
                        + "record Source(String name) {} "
                        + "class Target { public Target(String name) {} public Target(Integer number) {} } "
                        + "Target map(Source source); }",
                        "has multiple public constructors and no public no-args constructor"),
                invalid("target without public constructor",
                        "@CompiledMapper public interface Mapper { "
                        + "record Source(String name) {} "
                        + "class Target { private Target() {} } "
                        + "Target map(Source source); }",
                        "has no public constructor"),
                invalid("raw List target is unsupported",
                        "@CompiledMapper public interface Mapper { "
                        + "java.util.List map(java.util.List<String> source); }",
                        "target container element type is not writable"),
                invalid("raw Set target is unsupported",
                        "@CompiledMapper public interface Mapper { "
                        + "java.util.Set map(java.util.Set<String> source); }",
                        "target container element type is not writable"),
                invalid("raw Map target is unsupported",
                        "@CompiledMapper public interface Mapper { "
                        + "java.util.Map map(java.util.Map<String,Integer> source); }",
                        "target Map key/value type is not writable"),
                invalid("incompatible scalar leaf",
                        "@CompiledMapper public interface Mapper { "
                        + "record Source(String value) {} record Target(Boolean value) {} "
                        + "Target map(Source source); }",
                        "cannot map value"),
                invalid("map key conversion requires a declared contract",
                        "@CompiledMapper public interface Mapper { "
                        + "java.util.Map<Integer,String> map(java.util.Map<String,String> source); }",
                        "cannot map value")
        ).map(test -> dynamicTest(test.name, () -> {
            String source = "package fixture; import java.util.*; "
                    + "import org.sjf4j.annotation.mapping.*; " + test.source;
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    Map.of("fixture/Mapper.java", source), CodegenProcessor.class);
            assertFalse(result.success, "Expected compilation failure: " + test.name
                    + "\n" + result.diagnostics());
            assertTrue(result.diagnostics().contains(test.diagnosticFragment),
                    "Unexpected diagnostics for " + test.name + ":\n" + result.diagnostics());
        }));
    }

    private static InvalidCase invalid(String name, String source, String diagnosticFragment) {
        return new InvalidCase(name, source, diagnosticFragment);
    }

    private static final class InvalidCase {
        final String name;
        final String source;
        final String diagnosticFragment;

        InvalidCase(String name, String source, String diagnosticFragment) {
            this.name = name;
            this.source = source;
            this.diagnosticFragment = diagnosticFragment;
        }
    }
}
