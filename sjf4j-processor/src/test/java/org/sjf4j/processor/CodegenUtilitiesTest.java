package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.annotation.AnnotationValues;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.MethodSignature;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodegenUtilitiesTest {

    @Test
    void formatsResolvedSignaturesAndKeepsAnnotationDefaultsExplicit() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Child.java",
                "package fixture; "
                        + "enum Mode { FIRST, SECOND } "
                        + "@interface Options { boolean flag() default true; Mode mode() default Mode.FIRST; } "
                        + "@Options(flag=false, mode=Mode.SECOND) interface Parent<T> { "
                        + "T convert(T value) throws java.io.IOException; } "
                        + "@Options public interface Child extends Parent<String> {}"), Probe.class);
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
            TypeElement child = processingEnv.getElementUtils().getTypeElement("fixture.Child");
            if (child == null) return false;
            TypeElement parent = processingEnv.getElementUtils().getTypeElement("fixture.Parent");
            CompilationContext context = new ProcessorContext(processingEnv).forInterface(child, false);

            ExecutableElement method = null;
            for (Element member : parent.getEnclosedElements()) {
                if (member.getKind() == ElementKind.METHOD) method = (ExecutableElement) member;
            }
            assertNotNull(method);
            ExecutableType resolved = (ExecutableType) context.typeUtils.asMemberOf(
                    (DeclaredType) child.asType(), method);
            assertEquals("public java.lang.String convert(java.lang.String value) throws java.io.IOException",
                    MethodSignature.override(method, resolved, true));
            assertEquals("public java.lang.String convert(java.lang.String value)",
                    MethodSignature.override(method, resolved, false));
            assertEquals("public T convert(T value)", MethodSignature.override(method, false));

            AnnotationMirror inheritedDefault = AnnotationValues.find(child, "fixture.Options");
            AnnotationMirror explicit = AnnotationValues.find(parent, "fixture.Options");
            assertNull(AnnotationValues.explicit(inheritedDefault, "flag"));
            assertEquals(Boolean.TRUE, AnnotationValues.withDefaults(context.elements, inheritedDefault, "flag").getValue());
            assertEquals(Boolean.FALSE, AnnotationValues.explicit(explicit, "flag").getValue());
            assertEquals("FIRST", AnnotationValues.text(
                    AnnotationValues.withDefaults(context.elements, inheritedDefault, "mode"), null));
            assertEquals("SECOND", AnnotationValues.text(AnnotationValues.explicit(explicit, "mode"), null));
            assertEquals("fallback", AnnotationValues.text(null, "fallback"));

            TypeMirror primitive = context.typeUtils.getPrimitiveType(TypeKind.INT);
            assertEquals("int", JavaExpressions.concreteType(context.types, primitive));
            assertEquals("java.lang.Integer", JavaExpressions.boxedType(context.types, primitive));
            assertEquals("java.lang.Integer.class",
                    JavaExpressions.boxedClassLiteral(context.types, context.typeUtils, primitive));
            assertEquals("(java.lang.String) value", JavaExpressions.cast("java.lang.String", "value"));
            assertEquals("new java.util.ArrayList<>()", JavaExpressions.newInstance("java.util.ArrayList", true));
            assertEquals("new org.sjf4j.JsonObject()", JavaExpressions.newInstance("org.sjf4j.JsonObject", false));
            return false;
        }
    }
}
