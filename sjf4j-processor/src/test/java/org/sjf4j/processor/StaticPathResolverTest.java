package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.JsonPath;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.ResolvedPath;
import org.sjf4j.processor.access.StaticPathResolver;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StaticPathResolverTest {

    @Test
    void resolvesGenericTypesAndStopsAtFirstRejectedSegment() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java",
                "package fixture; public class Model { "
                        + "public java.util.List<java.util.Map<String, String>> values; }"),
                Probe.class);
        assertTrue(result.success, result.diagnostics());
    }

    @SupportedAnnotationTypes("*")
    public static final class Probe extends AbstractProcessor {

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (round.processingOver()) return false;
            TypeElement owner = processingEnv.getElementUtils().getTypeElement("fixture.Model");
            if (owner == null) return false;

            CompilationContext context = new ProcessorContext(processingEnv).forInterface(owner, false);
            StaticPathResolver paths = context.paths;
            TypeMirror root = owner.asType();
            PathSegment[] segments = JsonPath.parse("$.values[0].name").segments();

            ResolvedPath read = paths.resolve(root, segments, (i, last, segment) -> true,
                    (i, last, step) -> step.access() == null ? null : step.access().readType());
            assertNotNull(read);
            assertEquals("fixture.Model", read.rootType().toString());
            assertEquals("java.lang.String", read.resultType().toString());
            assertEquals(3, read.steps().size());
            assertEquals(NodeAccess.Kind.PROPERTY, read.steps().get(0).access().kind());
            assertEquals(NodeAccess.Kind.LIST, read.steps().get(1).access().kind());
            assertEquals(NodeAccess.Kind.MAP, read.steps().get(2).access().kind());
            assertEquals("java.util.Map<java.lang.String,java.lang.String>",
                    read.steps().get(2).ownerType().toString());
            assertThrows(UnsupportedOperationException.class, () -> read.steps().clear());

            ResolvedPath write = paths.resolve(root, segments, (i, last, segment) -> true,
                    (i, last, step) -> last ? step.access().writeType() : step.access().readType());
            assertNotNull(write);
            assertEquals("java.lang.String", write.resultType().toString());

            ResolvedPath rejected = paths.resolve(root, segments, (i, last, segment) -> i == 1,
                    (i, last, step) -> {
                        assertEquals(1, i);
                        return step.access().readType();
                    });
            assertNull(rejected);

            ResolvedPath rootOnly = paths.resolve(root, JsonPath.parse("$").segments(),
                    (i, last, segment) -> { throw new AssertionError("root has no child"); },
                    (i, last, step) -> { throw new AssertionError("root has no access"); });
            assertNotNull(rootOnly);
            assertTrue(rootOnly.steps().isEmpty());
            assertEquals(root, rootOnly.resultType());
            return false;
        }
    }
}
