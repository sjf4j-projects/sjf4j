package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.GeneratedNames;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Reachability must retain transitive and member-group references, not dead helpers. */
class GeneratedHelperReachabilityTest {

    @Test
    void retainsOnlyReachableNamedHelpersAndAllExplicitMemberGroups() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Collections.singletonMap("fixture/Sample.java",
                        "package fixture; public interface Sample { int value(); }"),
                ReachabilityProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Sample_Reachability.java");
        assertTrue(generated.contains("int _entry()"), generated);
        assertTrue(generated.contains("int _leaf()"), generated);
        assertTrue(generated.contains("int _groupLeaf()"), generated);
        assertTrue(generated.contains("int GROUP"), generated);
        assertFalse(generated.contains("int _deadA()"), generated);
        assertFalse(generated.contains("int _deadB()"), generated);
        assertFalse(generated.contains("int _literalOnly()"), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> implementation = loader.loadClass("fixture.Sample_Reachability");
            Object instance = implementation.getConstructor().newInstance();
            assertEquals(42, implementation.getMethod("value").invoke(instance));
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class ReachabilityProcessor extends AbstractProcessor {
        private boolean written;

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (written || round.processingOver()) return false;
            TypeElement target = processingEnv.getElementUtils().getTypeElement("fixture.Sample");
            if (target == null) return false;
            written = true;
            GeneratedClass generated = GeneratedClass.forInterface(
                    new ProcessorContext(processingEnv).forInterface(target, false),
                    target, "_Reachability");
            GeneratedNames names = generated.names();
            String entry = names.helperMethod("entry");
            String leaf = names.helperMethod("leaf");
            String groupLeaf = names.helperMethod("groupLeaf");
            String deadA = names.helperMethod("deadA");
            String deadB = names.helperMethod("deadB");
            String literalOnly = names.helperMethod("literalOnly");
            generated.addMethod(out -> out.line("public int value() { return "
                    + entry + "() + GROUP; }"));
            generated.addHelper(entry, out -> out.line("private static int " + entry
                    + "() { return " + leaf + "(); }"));
            generated.addHelper(leaf, out -> out.line("private static int " + leaf
                    + "() { return 40; }"));
            generated.addHelper(deadA, out -> out.line("private static int " + deadA
                    + "() { return " + deadB + "(); }"));
            generated.addHelper(deadB, out -> out.line("private static int " + deadB
                    + "() { return " + deadA + "(); }"));
            generated.addHelper(literalOnly, out -> out.line("private static int "
                    + literalOnly + "() { return 0; }"));
            generated.addMemberGroup(out -> {
                out.line("private static final int GROUP = " + groupLeaf + "();");
                out.line("private static final String TEXT = "
                        + org.sjf4j.processor.code.JavaWriter.stringLiteral(literalOnly) + ";");
            });
            generated.addHelper(groupLeaf, out -> out.line("private static int "
                    + groupLeaf + "() { return 2; }"));
            generated.write();
            return false;
        }
    }
}
