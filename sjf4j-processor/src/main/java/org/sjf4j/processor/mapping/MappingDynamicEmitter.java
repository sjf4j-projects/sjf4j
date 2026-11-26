package org.sjf4j.processor.mapping;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;

import java.util.Map;

/** Emits dynamic source enumeration and destination writes. */
final class MappingDynamicEmitter {

    private MappingDynamicEmitter() {
    }

    static void emitDynamicSources(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            String target) {

        for (MappingCompiler.DynamicSource dynamic :
                compiled.dynamicSources()) {

            String source =
                    dynamic.source()
                            .getSimpleName()
                            .toString();

            boolean guard = !MappingEmissionSupport.rootKnownNonNull(compiled.plan());
            if (guard) out.beginBlock("if (" + source + " != null)");

            switch (dynamic.kind()) {
                case OBJECT_MAP:
                    emitMapDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target);
                    break;

                case OBJECT_JSON_OBJECT:
                    emitJsonObjectDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target,
                            false);
                    break;

                case OBJECT_JOJO:
                    emitJsonObjectDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target,
                            true);
                    break;

                case COMPILE_TIME_UNKNOWN:
                    emitRuntimeDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target);
                    break;

                default:
                    throw new IllegalStateException(
                            "unsupported JOJO dynamic source " +
                                    dynamic.kind());
            }

            if (guard) out.endBlock();
        }
    }

    private static void emitMapDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target) {

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<?, ?> " +
                        entry +
                        " : " +
                        source +
                        ".entrySet()) {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                "(String) " + entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");
    }

    private static void emitJsonObjectDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target,
            boolean dynamicOnly) {

        if (dynamicOnly) {
            out.beginBlock(
                    "if (" +
                            "org.sjf4j.InternalAccess.dynamicMembers(" +
                            source +
                            ") != null)");
        }

        String entry =
                names.newName(
                        "entry");

        String entries =
                dynamicOnly
                        ? "org.sjf4j.InternalAccess.dynamicMembers(" +
                        source +
                        ").entrySet()"
                        : source + ".entrySet()";

        out.line(
                "for (java.util.Map.Entry<String, Object> " +
                        entry +
                        " : " +
                        entries +
                        ") {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");

        if (dynamicOnly) {
            out.endBlock();
        }
    }

    private static void emitRuntimeDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target) {

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<String, Object> " +
                        entry +
                        " : org.sjf4j.node.NodeWalker.entrySetInObject(" +
                        source +
                        ")) {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");
    }

    private static void emitDynamicPut(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String target,
            String keyExpression,
            String valueExpression) {

        String key =
                names.newName(
                        "key");

        out.line(
                "String " +
                        key +
                        " = " +
                        keyExpression +
                        ";");

        if (!dynamic.excludedNames()
                .isEmpty()) {

            StringBuilder condition =
                    new StringBuilder();

            for (String excluded :
                    dynamic.excludedNames()) {

                if (condition.length() != 0) {
                    condition.append(" || ");
                }

                condition.append(
                                JavaWriter.stringLiteral(
                                        excluded))
                        .append(".equals(")
                        .append(key)
                        .append(')');
            }

            out.line(
                    "if (" +
                            condition +
                            ") continue;");
        }

        out.line(
                target +
                        ".put(" +
                        key +
                        ", " +
                        valueExpression +
                        ");");
    }

}
