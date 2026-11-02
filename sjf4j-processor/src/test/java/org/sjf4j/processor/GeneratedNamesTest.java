package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.GeneratedNames;
import org.sjf4j.processor.code.NameAllocator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedNamesTest {

    @Test
    void reservesInheritedMembersAndSeparatesLocalScopes() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Parent.java",
                "package fixture;\n"
                        + "public interface Parent {\n"
                        + "  String USER_NAMES = \"names\";\n"
                        + "  default void _readUser() {}\n"
                        + "}\n");
        sources.put("fixture/Sample.java",
                "package fixture;\n"
                        + "public interface Sample extends Parent {\n"
                        + "  void validate(String result, int index);\n"
                        + "}\n");

        CaptureProcessor.captured = null;
        NavigatorTestCompiler.Result compilation = NavigatorTestCompiler.compile(
                sources, CaptureProcessor.class);
        assertTrue(compilation.success, compilation.diagnostics());

        Map<String, String> names = CaptureProcessor.captured;
        assertNotNull(names);
        assertEquals("_readUser2", names.get("helper"));
        assertEquals("_readUser3", names.get("helper2"));
        assertEquals("_readFallback", names.get("prefixedHelper"));
        assertEquals("USER_NAMES2", names.get("field"));
        assertEquals("USER_NAMES3", names.get("field2"));
        assertEquals("context2", names.get("context"));
        assertEquals("binder2", names.get("binder"));
        assertEquals("validate2", names.get("declaredMethod"));
        assertEquals("Sample_Impl2", names.get("nestedType"));
        assertEquals("result2", names.get("parameter"));
        assertEquals("index2", names.get("index"));
        assertEquals("result3", names.get("repeatLocal"));
        assertEquals("result2", names.get("freshScope"));
        assertEquals("parser2", names.get("helperLocal"));
    }

    @SupportedAnnotationTypes("*")
    public static final class CaptureProcessor extends AbstractProcessor {
        static volatile Map<String, String> captured;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (round.processingOver()) return false;

            TypeElement target = processingEnv.getElementUtils().getTypeElement("fixture.Sample");
            if (target == null) return false;

            GeneratedClass generated = GeneratedClass.forInterface(
                    new ProcessorContext(processingEnv), target);
            GeneratedNames allocated = generated.names();
            Map<String, String> names = new LinkedHashMap<>();

            names.put("helper", allocated.helperMethod("readUser"));
            names.put("helper2", allocated.helperMethod("readUser"));
            names.put("prefixedHelper", allocated.helperMethod("_readFallback"));
            names.put("field", allocated.field("USER_NAMES"));
            names.put("field2", allocated.field("USER_NAMES"));
            names.put("context", allocated.field("context"));
            names.put("binder", allocated.field("binder"));
            names.put("declaredMethod", allocated.field("validate"));
            names.put("nestedType", allocated.type("Sample_Impl"));

            for (Element member : target.getEnclosedElements()) {
                if (member.getKind() != ElementKind.METHOD
                        || !member.getSimpleName().contentEquals("validate")) continue;
                NameAllocator locals = allocated.locals((ExecutableElement) member);
                names.put("parameter", locals.newName("result"));
                names.put("index", locals.newName("index"));
                names.put("repeatLocal", locals.newName("result"));
                names.put("freshScope", allocated.locals((ExecutableElement) member)
                        .newName("result"));
                break;
            }
            names.put("helperLocal", allocated.helperLocals("parser", "value")
                    .newName("parser"));
            captured = names;
            return false;
        }
    }
}
