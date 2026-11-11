package org.sjf4j.testbench.nativeimage;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Fast JVM correctness checks for the same twelve native-image entry points.
 * Each backend/implementation is an independent test, so one incompatibility
 * cannot prevent the others from being checked.
 */
class NativeCompatibilityProbeTest {

    @TempDir
    Path fixtures;

    @FunctionalInterface
    interface Probe {
        void run(String[] args) throws Exception;
    }

    private static DynamicTest check(String name, Probe probe, String[] args) {
        return DynamicTest.dynamicTest(name, () -> probe.run(args));
    }

    @TestFactory
    Stream<DynamicTest> backendsAndImplementationsAgreeOnFabienFixtures() throws Exception {
        NativeCompatibilityFixtures.main(new String[]{fixtures.toString()});
        String[] args = {fixtures.toString()};
        return Stream.of(
                check("jackson2/native", NativeBindingCompatibility.Jackson2Native::main, args),
                check("jackson2/runtime", NativeBindingCompatibility.Jackson2Runtime::main, args),
                check("jackson2/compiled", NativeBindingCompatibility.Jackson2Compiled::main, args),
                check("jackson3/native", NativeBindingCompatibility.Jackson3Native::main, args),
                check("jackson3/runtime", NativeBindingCompatibility.Jackson3Runtime::main, args),
                check("jackson3/compiled", NativeBindingCompatibility.Jackson3Compiled::main, args),
                check("fastjson2/native", NativeBindingCompatibility.Fastjson2Native::main, args),
                check("fastjson2/runtime", NativeBindingCompatibility.Fastjson2Runtime::main, args),
                check("fastjson2/compiled", NativeBindingCompatibility.Fastjson2Compiled::main, args),
                check("gson/native", NativeBindingCompatibility.GsonNative::main, args),
                check("gson/runtime", NativeBindingCompatibility.GsonRuntime::main, args),
                check("gson/compiled", NativeBindingCompatibility.GsonCompiled::main, args)
        );
    }
}
