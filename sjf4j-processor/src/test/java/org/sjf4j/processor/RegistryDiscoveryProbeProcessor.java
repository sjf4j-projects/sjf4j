package org.sjf4j.processor;

import org.sjf4j.processor.discovery.CompiledMetadata;
import org.sjf4j.processor.discovery.ValueCodecMetadata;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/** Test-only annotation processor that queries discovered metadata without generating code. */
public final class RegistryDiscoveryProbeProcessor extends AbstractProcessor {
    private ProcessorContext context;
    private boolean finished;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        context = new ProcessorContext(environment);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("org.sjf4j.annotation.binding.CompiledBinder");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (finished || round.processingOver()) return false;
        finished = true;
        context.discovery.collect(round);
        TypeElement owner = context.elements.getTypeElement("fixture.Binder");
        CompiledMetadata metadata = context.metadata(owner);
        for (String name : new String[]{"fixture.Money", "java.util.UUID", "java.time.Instant"}) {
            TypeElement value = context.elements.getTypeElement(name);
            if (value == null) continue;
            for (String format : new String[]{"", "iso", "epochMillis", "compact", "alpha", "beta"}) {
                ValueCodecMetadata info = metadata.valueCodec(value.asType(), format);
                if (info != null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "DISCOVER " + name + "/" + format + " -> "
                                    + info.rawType + " from " + info.declaration.getEnclosingElement());
                }
            }
        }
        return false;
    }
}
