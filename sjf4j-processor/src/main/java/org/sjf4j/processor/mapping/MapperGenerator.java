package org.sjf4j.processor.mapping;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;


/**
 * Generates implementations for {@code @CompiledMapper} interfaces.
 *
 * <p>Generation is deliberately split into analysis, semantic compilation,
 * conversion preparation and source emission. No semantic resolution is
 * performed while the generated source is being written.</p>
 */
public final class MapperGenerator {

    private final ProcessorContext context;
    private final MapperMethodGenerator methodGenerator;


    public MapperGenerator(ProcessorContext context) {
        this.context = context;
        this.methodGenerator =
                new MapperMethodGenerator(context);
    }


    /**
     * Generates one compiled mapper implementation.
     */
    public void generate(TypeElement type) {
        if (!type.getTypeParameters()
                .isEmpty()) {

            context.error(
                    type,
                    "@CompiledMapper interface cannot declare type parameters");

            return;
        }

        GeneratedClass generated =
                GeneratedClass.forInterface(
                        context,
                        type);

        /*
         * --------------------------------------------------------------
         * 1. Analyze methods
         * --------------------------------------------------------------
         */
        List<MappingPlan> plans =
                analyze(
                        type,
                        generated);

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 2. Compile mapping semantics
         * --------------------------------------------------------------
         */
        MappingCompiler compiler =
                new MappingCompiler(
                        context,
                        type,
                        plans);

        if (!compiler.validate(
                generated)) {

            return;
        }

        List<MappingCompiler.CompiledMethod> methods =
                new ArrayList<MappingCompiler.CompiledMethod>(
                        plans.size());

        for (MappingPlan plan :
                plans) {

            MappingCompiler.CompiledMethod method =
                    compiler.compile(
                            plan,
                            generated);

            if (method != null) {
                methods.add(method);
            }
        }

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 3. Compile recursive conversions
         * --------------------------------------------------------------
         */
        ConversionEmitter conversionEmitter =
                new ConversionEmitter(
                        context,
                        type,
                        plans);

        if (!conversionEmitter.prepare(
                methods,
                generated)) {

            return;
        }

        if (!generated.isValid()) {
            return;
        }

        /*
         * --------------------------------------------------------------
         * 4. Emit methods
         * --------------------------------------------------------------
         */
        MappingEmitter emitter =
                new MappingEmitter(
                        context,
                        conversionEmitter);

        for (MappingCompiler.CompiledMethod method :
                methods) {

            emitter.emit(
                    method,
                    generated);
        }

        /*
         * --------------------------------------------------------------
         * 5. Write source
         * --------------------------------------------------------------
         */
        generated.write();
    }


    /*
     * --------------------------------------------------------------
     * Analyze
     * --------------------------------------------------------------
     */

    private List<MappingPlan> analyze(
            TypeElement type,
            GeneratedClass generated) {

        List<ResolvedMethod> methods =
                context.methods.abstractMethods(type);

        List<MappingPlan> plans =
                new ArrayList<MappingPlan>(
                        methods.size());

        for (ResolvedMethod resolved : methods) {
            if (!isResolvedSignature(resolved)) {
                ExecutableElement method = resolved.declaration();

                context.error(
                        method,
                        "Mapper method contains unresolved type variables after interface specialization: " +
                                resolved.type());

                generated.invalidate();
                continue;
            }

            MappingPlan plan =
                    methodGenerator.analyze(
                            resolved,
                            generated);

            if (plan != null) {
                plans.add(plan);
            }
        }

        return plans;
    }


    private boolean isResolvedSignature(
            ResolvedMethod method) {

        ExecutableType type =
                method.type();

        if (!context.types.isFullyResolved(
                type.getReturnType())) {
            return false;
        }

        for (TypeMirror parameter :
                type.getParameterTypes()) {

            if (!context.types.isFullyResolved(
                    parameter)) {
                return false;
            }
        }

        for (TypeMirror thrown :
                type.getThrownTypes()) {

            if (!context.types.isFullyResolved(
                    thrown)) {
                return false;
            }
        }

        return true;
    }


}
