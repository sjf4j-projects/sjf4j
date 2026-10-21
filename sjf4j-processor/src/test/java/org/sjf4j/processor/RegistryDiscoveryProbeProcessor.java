package org.sjf4j.processor;

import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.processor.discovery.CompiledMetadata;
import org.sjf4j.processor.discovery.ValueCodecMetadata;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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
        return Set.of(CompiledBinder.class.getName());
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
        for (String name : new String[]{"fixture.Money", "fixture.Amount", UUID.class.getName(), Instant.class.getName()}) {
            TypeElement value = context.elements.getTypeElement(name);
            if (value == null) continue;
            ValueCodecMetadata defaultInfo = metadata.valueCodec(value.asType(), null);
            if (defaultInfo != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        "DEFAULT " + name + " -> " + defaultInfo.rawType);
            }
            for (String format : new String[]{"", "standard", "iso", "epochMillis", "compact", "alpha", "beta"}) {
                ValueCodecMetadata info = metadata.valueCodec(value.asType(), format);
                if (info != null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "DISCOVER " + name + "/" + format + " -> "
                                    + info.rawType + " from " + info.declaration.getEnclosingElement());
                }
            }
        }
        TypeElement otherOwner = context.elements.getTypeElement("fixture.OtherBinder");
        if (otherOwner != null) {
            CompiledMetadata other = context.metadata(otherOwner);
            TypeElement instant = context.elements.getTypeElement(Instant.class.getName());
            for (String format : new String[]{"alpha", "beta"}) {
                ValueCodecMetadata info = other.valueCodec(instant.asType(), format);
                if (info != null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "OTHER " + format + " -> " + info.rawType);
                }
            }
        }
        return false;
    }
}
