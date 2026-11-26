package org.sjf4j.processor.mapping;

import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.type.TypeSystem;

import java.util.List;

/** Emits compiled compute expressions and binds their source inputs. */
final class MappingComputeEmitter {

    private final TypeSystem types;
    private final MappingReadEmitter sourceReads;

    MappingComputeEmitter(TypeSystem types, MappingReadEmitter sourceReads) {
        this.types = types;
        this.sourceReads = sourceReads;
    }

    String emitCompute(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            MappingCompiler.Value value) {

        List<MappingCompiler.Read> inputs =
                value.inputs();

        String compute =
                value.compute()
                        .trim();

        String targetType =
                JavaExpressions.boxedType(types,
                        value.type());

        if (compute.startsWith("this::")) {
            String method =
                    compute.substring(6)
                            .trim();

            String[] expressions =
                    emitComputeInputs(
                            out,
                            names,
                            inputs,
                            plan);

            String result =
                    names.newName("computed");

            StringBuilder call =
                    new StringBuilder();

            call.append(method)
                    .append('(');

            for (int i = 0;
                 i < expressions.length;
                 i++) {

                if (i != 0) {
                    call.append(", ");
                }

                call.append(
                        expressions[i]);
            }

            call.append(')');

            out.line(
                    targetType +
                            " " +
                            result +
                            " = " +
                            call +
                            ";");

            return result;
        }

        int arrow =
                compute.indexOf("->");

        if (arrow < 0) {
            throw new IllegalStateException(
                    "invalid compute expression: " +
                            compute);
        }

        String parameters =
                compute.substring(
                                0,
                                arrow)
                        .trim();

        String expression =
                compute.substring(
                                arrow + 2)
                        .trim();

        if (parameters.startsWith("(") &&
                parameters.endsWith(")")) {

            parameters =
                    parameters.substring(
                            1,
                            parameters.length() - 1);
        }

        String[] parameterNames =
                parameters.isEmpty()
                        ? new String[0]
                        : parameters.split("\\s*,\\s*");

        if (parameterNames.length !=
                inputs.size()) {

            throw new IllegalStateException(
                    "compute parameter count does not match sources");
        }

        for (String parameterName :
                parameterNames) {

            names.reserve(
                    parameterName.trim());
        }

        String result =
                names.newName("computed");

        // The computation always assigns a value before leaving this scope;
        // initializing the result to null adds no semantics.
        out.line(targetType + " " + result + ";");
        out.line("{");
        out.indent();

        for (int i = 0; i < parameterNames.length; i++) {
            String parameter = parameterNames[i].trim();
            MappingCompiler.Read input = inputs.get(i);
            if (input.rootValue() || input.steps().isEmpty()) {
                out.line(JavaExpressions.boxedType(types, input.type()) + " " + parameter + " = " +
                        input.root().getSimpleName() + ";");
            } else {
                // Bind the final read directly to the named compute parameter.
                // This removes redundant sourceValue -> parameter aliases while
                // keeping multi-step null guards and the expression scope.
                sourceReads.emitRead(out, names, input, MappingEmissionSupport.rootKnownNonNull(plan), false, parameter);
            }
        }

        out.line(
                result +
                        " = " +
                        expression +
                        ";");

        out.dedent();
        out.line("}");

        return result;
    }

    private String[] emitComputeInputs(
            JavaWriter out,
            NameAllocator names,
            List<MappingCompiler.Read> inputs,
            MappingPlan plan) {

        String[] expressions =
                new String[inputs.size()];

        for (int i = 0;
             i < inputs.size();
             i++) {

            expressions[i] =
                    sourceReads.emitRead(
                            out,
                            names,
                            inputs.get(i),
                            MappingEmissionSupport.rootKnownNonNull(plan));
        }

        return expressions;
    }

}
