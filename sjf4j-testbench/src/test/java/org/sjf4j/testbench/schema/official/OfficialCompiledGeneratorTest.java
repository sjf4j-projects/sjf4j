package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class OfficialCompiledGeneratorTest {
    @TempDir
    Path directory;

    @Test
    void generatesExactSchemasAndExternalResourcesForAllDrafts() throws Exception {
        Path suite = directory.resolve("suite");
        Path input = Files.createDirectories(suite.resolve("validation/tests"));
        Files.writeString(input.resolve("numbers.json"), """
                {"tests":[
                  {"schema":{"maximum":1.00000000000000001,
                             "minimum":-9223372036854775808123456789},
                   "externalSchemas":{"https://example.test/numbers":{"multipleOf":0.00000000000000000001}},
                   "tests":[{"data":1,"valid":true}]},
                  {"compatibility":"=2020","schema":false,"tests":[{"data":null,"valid":false}]}
                ]}
                """);
        Path optional = Files.createDirectories(input.resolve("optional"));
        Files.writeString(optional.resolve("format-assertion.json"),
                """
                {"tests":[{"schema":true,"tests":[{"data":null,"valid":true}]}]}
                """);
        Files.writeString(optional.resolve("ignored.json"), "not JSON");

        Path output = directory.resolve("generated");
        OfficialCompiledGenerator.generate(suite, output);
        for (String draft : new String[]{"draft7", "draft2019-09", "draft2020-12"}) {
            Path generated = output.resolve(draft);
            Path resources = generated.resolve("resources/official").resolve(draft);
            Map<?, ?> schema = (Map<?, ?>) OfficialFixtures.readNode(resources.resolve("numbers/000.json"));
            assertEquals(new BigDecimal("1.00000000000000001"), schema.get("maximum"));
            assertEquals(new BigInteger("-9223372036854775808123456789"), schema.get("minimum"));
            Map<?, ?> external = (Map<?, ?>) OfficialFixtures.readNode(
                    resources.resolve("external/numbers/000.json"));
            Map<?, ?> externalSchema = (Map<?, ?>) external.get("https://example.test/numbers");
            assertEquals(new BigDecimal("0.00000000000000000001"), externalSchema.get("multipleOf"));
            boolean latest = "draft2020-12".equals(draft);
            assertEquals(!latest, schema.containsKey("$schema"));
            assertEquals(schema.get("$schema"), externalSchema.get("$schema"));
            assertEquals(latest, Files.exists(resources.resolve("numbers/001.json")));
            if (latest) assertEquals(Boolean.FALSE, OfficialFixtures.readNode(resources.resolve("numbers/001.json")));
            assertEquals(Boolean.TRUE, OfficialFixtures.readNode(resources.resolve("optional/format-assertion/000.json")));
            assertFalse(Files.exists(resources.resolve("optional/ignored")));
            assertEquals("files=2\ngroups=" + (latest ? 3 : 2) + "\ntests=" + (latest ? 3 : 2) + "\n",
                    Files.readString(generated.resolve("counts.properties")));
            String manifest = Files.readString(generated.resolve("index.tsv"));
            assertTrue(manifest.contains("numbers.json\t0\tOfficialNumbersG000\t1\n"));
            Path java = generated.resolve("java/org/sjf4j/testbench/schema/official/generated");
            String strict = Files.readString(java.resolve("OfficialNumbersG000.java"));
            String fallback = Files.readString(java.resolve("OfficialNumbersG000Fallback.java"));
            assertTrue(strict.contains("@CompiledValidator(fallback = false)"));
            assertTrue(fallback.contains("@CompiledValidator(fallback = true)"));
            assertTrue(strict.contains("externalSchemasRef = \"classpath:/official/" + draft
                    + "/external/numbers/000.json\""));
        }
    }
}
