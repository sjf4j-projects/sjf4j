package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.BindingFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered backend group for one format.
 *
 * <p>The processor currently owns these groups directly. Keeping the group
 * boundary explicit allows a future provider SPI to contribute equivalent
 * {@link BackendSpec}s without changing binder compilation.</p>
 */
final class BackendGroup {

    private final BindingFormat format;
    private final List<BackendSpec> backends;

    BackendGroup(
            BindingFormat format,
            List<BackendSpec> backends) {

        this.format = Objects.requireNonNull(format, "format");
        this.backends = Collections.unmodifiableList(
                new ArrayList<BackendSpec>(backends));
    }

    BindingFormat format() {
        return format;
    }

    List<BackendSpec> backends() {
        return backends;
    }

    BackendSpec find(BindingBackend backend) {
        for (BackendSpec spec : backends) {
            if (spec.backend() == backend) {
                return spec;
            }
        }
        return null;
    }
}
