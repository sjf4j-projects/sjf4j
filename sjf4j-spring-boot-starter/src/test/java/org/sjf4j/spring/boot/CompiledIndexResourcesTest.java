package org.sjf4j.spring.boot;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledIndexResourcesTest {

    @Test
    void readsAndDeduplicatesIndexesFromMultipleJars() throws Exception {
        Path first = writeIndexJar("com.example.First\ncom.example.Shared\n");
        Path second = writeIndexJar("# comment\ncom.example.Second\ncom.example.Shared\n");

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{first.toUri().toURL(), second.toUri().toURL()}, null)) {
            assertEquals(Set.of("com.example.First", "com.example.Second", "com.example.Shared"),
                    CompiledBeanDefinitionRegistrar.indexedTypes(loader));
        }
    }

    private Path writeIndexJar(String index) throws Exception {
        Path jar = Files.createTempFile("sjf4j-index-", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/sjf4j/compiled.interfaces"));
            out.write(index.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}
