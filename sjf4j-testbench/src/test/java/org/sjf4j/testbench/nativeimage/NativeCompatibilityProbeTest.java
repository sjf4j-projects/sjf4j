package org.sjf4j.testbench.nativeimage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Fast JVM correctness check for the same entry points used by native-image.
 * The separate Gradle nativeCompatibility task tests AOT reachability.
 */
class NativeCompatibilityProbeTest {

    @TempDir
    Path fixtures;

    @Test
    void allBackendsAndImplementationsAgreeOnFabienFixtures() throws Exception {
        NativeCompatibilityFixtures.main(new String[]{fixtures.toString()});
        String[] args = {fixtures.toString()};

        NativeBindingCompatibility.Jackson2Native.main(args);
        NativeBindingCompatibility.Jackson2Runtime.main(args);
        NativeBindingCompatibility.Jackson2Compiled.main(args);

        NativeBindingCompatibility.Jackson3Native.main(args);
        NativeBindingCompatibility.Jackson3Runtime.main(args);
        NativeBindingCompatibility.Jackson3Compiled.main(args);

        NativeBindingCompatibility.Fastjson2Native.main(args);
        NativeBindingCompatibility.Fastjson2Runtime.main(args);
        NativeBindingCompatibility.Fastjson2Compiled.main(args);

        NativeBindingCompatibility.GsonNative.main(args);
        NativeBindingCompatibility.GsonRuntime.main(args);
        NativeBindingCompatibility.GsonCompiled.main(args);
    }
}
