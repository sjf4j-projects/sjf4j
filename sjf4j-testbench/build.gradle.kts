import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.testing.Test
import java.io.BufferedInputStream
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("java-library")
    id("jacoco")
    id("me.champeau.jmh") version "0.7.2"
    id("org.graalvm.buildtools.native") version "1.1.12"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    testCompileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}


dependencies {
    // Shared testbench models use Lombok, while ModelState retains JMH's @State.
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    compileOnly("org.openjdk.jmh:jmh-core:1.37")

    implementation(project(":sjf4j-core"))
    implementation(project(":sjf4j-asm"))
    implementation(project(":sjf4j-schema"))

    implementation(project(":sjf4j-backend-jackson3"))
    implementation(project(":sjf4j-backend-jackson2"))
    implementation(project(":sjf4j-backend-gson"))
    implementation(project(":sjf4j-backend-fastjson2"))
    implementation(project(":sjf4j-backend-jsonp"))
    implementation(project(":sjf4j-backend-snake"))

    implementation("tools.jackson.core:jackson-databind:3.2.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.66")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    implementation("org.eclipse.parsson:parsson:1.1.7")
    implementation("org.yaml:snakeyaml:2.5")

    // test
    testAnnotationProcessor(project(":sjf4j-processor"))
    testImplementation(project(":sjf4j-processor"))
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.34")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.3")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.springframework:spring-jdbc:6.2.19")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // jmh
    jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
    jmhCompileOnly("org.projectlombok:lombok:1.18.38")
    jmhAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    // Benchmark-only comparisons; neither library is part of a production artifact.
    jmhImplementation("com.h2database:h2:2.3.232")
    jmhImplementation("org.springframework:spring-jdbc:6.2.19")
    jmhImplementation("org.mybatis:mybatis:3.5.19")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    jmhAnnotationProcessor(project(":sjf4j-processor"))

    jmhImplementation("org.mapstruct:mapstruct:1.6.3")
    jmhAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    jmhImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    jmhImplementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.22.3")
    jmhImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.3")
    jmhImplementation("tools.jackson.core:jackson-databind:3.2.3")
    jmhImplementation("tools.jackson.module:jackson-module-blackbird:3.2.3")
    jmhImplementation("com.google.code.gson:gson:2.13.1")
    jmhImplementation("com.alibaba.fastjson2:fastjson2:2.0.66")
    jmhImplementation("jakarta.json:jakarta.json-api:2.1.3")
    jmhImplementation("org.eclipse.parsson:parsson:1.1.7")
    jmhImplementation("com.jayway.jsonpath:json-path:3.0.0")

}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

// Annotation processing reads schemas from the same classpath as the compiled tests.
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(tasks.named("processTestResources"))
    classpath += files(layout.buildDirectory.dir("resources/test"))
}

// Recompile only SJF4J-generated test sources in strict mode. Consumer projects
// may compile with -Xlint:cast -Werror, where generated redundant casts are fatal.
val lintGeneratedCasts by tasks.registering(JavaCompile::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Rejects redundant casts and other warnings in generated SJF4J test sources."
    dependsOn(tasks.named("compileTestJava"))
    source(fileTree(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/test")) {
        include("**/*_Impl.java")
    })
    // Compile-only annotations are referenced in generated test signatures too.
    classpath = sourceSets.test.get().compileClasspath +
            sourceSets.test.get().runtimeClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/generated-cast-lint"))
    options.compilerArgs.addAll(listOf("-proc:none", "-Xlint:cast", "-Werror"))
}
tasks.named("check") {
    dependsOn(lintGeneratedCasts)
}

// CompiledValidator's annotation processor reads Creek schemas from jmh resources.
tasks.named<JavaCompile>("compileJmhJava") {
    dependsOn(tasks.named("processJmhResources"))
    classpath += files(layout.buildDirectory.dir("resources/jmh"))
}

tasks.test {
    useJUnitPlatform()
    exclude("**/OfficialRuntimeTest.class", "**/OfficialCompiledTest.class",
            "**/OfficialAnnotationTest.class")
    jvmArgs(
        "-Xshare:off"
    )
}

/////////////////////
/// Official JSON Schema Test Suite

val officialSchemaSuiteDir = layout.buildDirectory.dir("official-json-schema-tests/suite")

fun githubStream(url: String) = URI(url).toURL().openConnection().apply {
    setRequestProperty("User-Agent", "sjf4j-gradle")
    // GitHub Actions authenticates API requests to avoid anonymous 60/hr rate
    // limits; never forward the token to third-party or archive hosts.
    if (url.startsWith("https://api.github.com/")) {
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
            setRequestProperty("Authorization", "Bearer $it")
        }
    }
    connectTimeout = 30_000
    readTimeout = 120_000
}.getInputStream()

fun jsonSchemaTestSuiteSha(ref: String): String {
    if (Regex("[0-9a-fA-F]{40}").matches(ref)) return ref.lowercase()
    val api = "https://api.github.com/repos/json-schema-org/JSON-Schema-Test-Suite/commits/$ref"
    val json = githubStream(api).bufferedReader(Charsets.UTF_8).use { it.readText() }
    return Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"")
        .find(json)
        ?.groupValues
        ?.get(1)
        ?: throw GradleException("Failed to resolve JSON-Schema-Test-Suite revision: $ref")
}

// Explicit download/refresh tasks check the upstream revision on every invocation,
// but skip the archive download when the complete local cache is already current.
// Ordinary builds never depend on these network tasks.
fun updateOfficialSuite(ref: String) {
    val sha = jsonSchemaTestSuiteSha(ref)
    val outDir = officialSchemaSuiteDir.get().asFile
    val revisionFile = outDir.resolve("OFFICIAL_TEST_SUITE_REV")
    val validCache = revisionFile.isFile
        && revisionFile.readText().lineSequence().any { it.trim() == "revision: $sha" }
        && outDir.resolve("tests/draft2020-12").isDirectory
        && outDir.resolve("remotes").isDirectory
        && outDir.resolve("annotations/tests").isDirectory
    if (validCache) {
        logger.lifecycle("Official JSON Schema suite already at $sha; reusing local cache")
        return
    }

    val zipUrl = "https://codeload.github.com/json-schema-org/JSON-Schema-Test-Suite/zip/$sha"
    val staging = outDir.parentFile.resolve("suite-downloading")
    delete(staging)
    staging.mkdirs()
    val outRoot = staging.canonicalFile.toPath()
    var copied = 0
    try {
        ZipInputStream(BufferedInputStream(githubStream(zipUrl))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val rootEnd = name.indexOf('/')
                if (rootEnd < 0) continue
                val relative = name.substring(rootEnd + 1)
                if (!relative.startsWith("tests/") && !relative.startsWith("remotes/")
                    && !relative.startsWith("annotations/")) continue

                val target = staging.resolve(relative)
                val targetPath = target.canonicalFile.toPath()
                if (!targetPath.startsWith(outRoot)) {
                    throw GradleException("Unsafe path in JSON-Schema-Test-Suite zip: $name")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    copied++
                }
                zip.closeEntry()
            }
        }
        require(staging.resolve("tests/draft2020-12").isDirectory
                && staging.resolve("remotes").isDirectory
                && staging.resolve("annotations/tests").isDirectory) {
            "Downloaded suite $sha is missing required tests, remotes, or annotations"
        }
        staging.resolve("OFFICIAL_TEST_SUITE_REV").writeText(
            "repo: https://github.com/json-schema-org/JSON-Schema-Test-Suite\n" +
                "ref: $ref\n" +
                "revision: $sha\n"
        )
        // A failed network download leaves the previous complete suite untouched.
        delete(outDir)
        if (!staging.renameTo(outDir)) {
            staging.copyRecursively(outDir, overwrite = true)
            delete(staging)
        }
    } catch (e: Exception) {
        delete(staging)
        throw e
    }
    logger.lifecycle("Downloaded JSON-Schema-Test-Suite $sha ($copied files) to $outDir")
}

val downloadOfficialSchemaTests by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks the requested upstream revision, downloading only when the cached SHA differs."
    outputs.dir(officialSchemaSuiteDir)
    // Always resolve the remote revision on explicit invocation.
    outputs.upToDateWhen { false }
    doLast {
        updateOfficialSuite(providers.gradleProperty("jsonSchemaSuiteRef").orElse("main").get())
    }
}

val cachedOfficialSuiteExists = {
    officialSchemaSuiteDir.get().asFile.resolve("tests/draft2020-12").isDirectory &&
        officialSchemaSuiteDir.get().asFile.resolve("OFFICIAL_TEST_SUITE_REV").isFile
}

val officialRuntimeTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs OfficialRuntimeTest against the locally cached JSON Schema suite."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/OfficialRuntimeTest.class")
    systemProperty("sjf4j.schema.officialTestRoot", officialSchemaSuiteDir.get().asFile.absolutePath)
    jvmArgs("-Xshare:off")
    inputs.dir(officialSchemaSuiteDir)
    outputs.upToDateWhen { false }
    onlyIf("local official JSON Schema suite has been downloaded") { cachedOfficialSuiteExists() }
    shouldRunAfter(tasks.test)
}

val cachedOfficialAnnotationsExist = {
    cachedOfficialSuiteExists() &&
        officialSchemaSuiteDir.get().asFile.resolve("annotations/tests/meta-data.json").isFile
}

val officialAnnotationTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the complete official Annotation Suite across all three drafts."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/OfficialAnnotationTest.class")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("sjf4j.schema.officialTestRoot", officialSchemaSuiteDir.get().asFile.absolutePath)
    jvmArgs("-Xshare:off")
    inputs.dir(officialSchemaSuiteDir)
    outputs.upToDateWhen { false }
    onlyIf("official annotation suite is cached") { cachedOfficialAnnotationsExist() }
    shouldRunAfter(tasks.test)
}
officialAnnotationTest.configure { mustRunAfter(downloadOfficialSchemaTests) }

// When a publication also schedules a download, run it before checking the suite.
// mustRunAfter alone never invokes a network task during an ordinary build.
officialRuntimeTest.configure { mustRunAfter(downloadOfficialSchemaTests) }


/**
 * Generate one independent annotated interface per official schema group.
 * Sources are compiled by OfficialCompiledTest with javac in isolated batches:
 * an unsupported schema must not prevent other official cases from running.
 */
val officialCompiledDrafts = listOf("draft7", "draft2019-09", "draft2020-12")
val officialCompiledGeneratedDir = layout.buildDirectory.dir("generated/officialCompiled")
val generateOfficialCompiledCases by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates official compiled bindings for all supported JSON Schema drafts."
    onlyIf("official suite exists locally") { cachedOfficialSuiteExists() }
    inputs.dir(officialSchemaSuiteDir)
    outputs.dir(officialCompiledGeneratedDir)

    doLast {
        val outputRoot = officialCompiledGeneratedDir.get().asFile
        delete(outputRoot)
        for (draft in officialCompiledDrafts) {
            val dialectUri = when (draft) {
                "draft7" -> "http://json-schema.org/draft-07/schema#"
                "draft2019-09" -> "https://json-schema.org/draft/2019-09/schema"
                else -> "https://json-schema.org/draft/2020-12/schema"
            }
            val input = officialSchemaSuiteDir.get().asFile.resolve("tests/$draft")
            val output = outputRoot.resolve(draft)

            val packageName = "org.sjf4j.testbench.schema.official.generated"
            val javaDir = output.resolve("java/" + packageName.replace('.', '/'))
            val resourceDir = output.resolve("resources/official/$draft")
            // Official remotes may also omit $schema. The runtime suite assigns
            // the selected draft via SchemaRegistry; javac defaults to 2020-12.
            // Copy only for legacy drafts, retaining the relative URI layout.
            val upstreamRemotes = officialSchemaSuiteDir.get().asFile.resolve("remotes")
            val registryRemotes = if (draft == "draft2020-12") upstreamRemotes
                                  else output.resolve("remotes")
            if (draft != "draft2020-12") {
                upstreamRemotes.walkTopDown().filter { it.isFile && it.extension == "json" }
                    .forEach { file ->
                        val relative = upstreamRemotes.toPath().relativize(file.toPath())
                        val target = registryRemotes.toPath().resolve(relative).toFile()
                        target.parentFile.mkdirs()
                        val original = JsonSlurper().parse(file)
                        val schema = if (original is Map<*, *> && !original.containsKey("\$schema")) {
                            linkedMapOf<String, Any?>("\$schema" to dialectUri).apply {
                                for ((key, value) in original) {
                                    require(key is String) { "Expected string remote schema key: $key" }
                                    put(key, value)
                                }
                            }
                        } else original
                        target.writeText(JsonOutput.toJson(schema), Charsets.UTF_8)
                    }
            }
            val registryDir = JsonOutput.toJson(registryRemotes.toURI().toString())
            val registryBaseUri = JsonOutput.toJson("http://localhost:1234/")
            javaDir.mkdirs()
            resourceDir.mkdirs()

            val files = requireNotNull(input.listFiles())
                .filter { it.isFile && it.extension == "json" }
                .sortedBy { it.name }
            require(files.isNotEmpty()) { "No official $draft JSON files at $input" }

            val manifest = StringBuilder("file\tgroup\tclass\ttests\n")
            var groupCount = 0
            var testCount = 0
            for (file in files) {
                val groups = JsonSlurper().parse(file) as? List<*>
                    ?: throw GradleException("Invalid official test file: $file")
                val stem = file.nameWithoutExtension
                val classStem = stem.split(Regex("[^a-zA-Z0-9]+"))
                    .filter { it.isNotEmpty() }
                    .joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                for ((groupIndex, raw) in groups.withIndex()) {
                    val group = raw as? Map<*, *>
                        ?: throw GradleException("Invalid official group: $file #$groupIndex")
                    require(group.containsKey("schema")) { "Missing schema in $file #$groupIndex" }
                    val tests = group["tests"] as? List<*>
                        ?: throw GradleException("Missing tests in $file #$groupIndex")
                    val index = groupIndex.toString().padStart(3, '0')
                    val className = "Official" + classStem + "G" + index
                    val schemaPath = stem + "/" + index + ".json"
                    val schemaFile = resourceDir.resolve(schemaPath)
                    schemaFile.parentFile.mkdirs()
                    val originalSchema = group["schema"]
                    // Official test directories imply a dialect when $schema is omitted.
                    // Make that implicit selection explicit in the generated fixture.
                    val compiledSchema = if (draft != "draft2020-12"
                            && originalSchema is Map<*, *>
                            && !originalSchema.containsKey("\$schema")) {
                        linkedMapOf<String, Any?>("\$schema" to dialectUri).apply {
                            for ((key, value) in originalSchema) {
                                require(key is String) { "Expected string schema key: $key" }
                                put(key, value)
                            }
                        }
                    } else originalSchema
                    schemaFile.writeText(JsonOutput.toJson(compiledSchema), Charsets.UTF_8)

                    // Generate both variants without selecting fallback here. The test
                    // class decides which groups may use the fallback variant.
                    for (allowFallback in listOf(false, true)) {
                        val variant = if (allowFallback) className + "Fallback" else className
                        val source = listOf(
                            "package $packageName;",
                            "",
                            "import org.sjf4j.annotation.schema.CompiledValidator;",
                            "import org.sjf4j.annotation.schema.ValidJsonSchema;",
                            "",
                            "@CompiledValidator(fallback = $allowFallback, registryDir = $registryDir, registryBaseUri = $registryBaseUri)",
                            "public interface $variant {",
                            "    @ValidJsonSchema(ref = \"classpath:/official/$draft/$schemaPath\")",
                            "    boolean valid(Object value);",
                            "}"
                        ).joinToString("\n", postfix = "\n")
                        javaDir.resolve("$variant.java").writeText(source, Charsets.UTF_8)
                    }
                    manifest.append(file.name).append('\t').append(groupIndex)
                        .append('\t').append(className).append('\t').append(tests.size).append('\n')
                    groupCount++
                    testCount += tests.size
                }
            }
            output.resolve("index.tsv").writeText(manifest.toString(), Charsets.UTF_8)
            output.resolve("counts.properties").writeText(
                "files=" + files.size + "\ngroups=" + groupCount + "\ntests=" + testCount + "\n",
                Charsets.UTF_8
            )
            logger.lifecycle(
                "Generated official $draft bindings: " +
                    files.size + " files, " + groupCount + " schema groups, " + testCount +
                    " assertions"
            )
        }
    }
}

// Generated bindings must match the latest downloaded suite when publishing.
generateOfficialCompiledCases.configure { mustRunAfter(downloadOfficialSchemaTests) }

val officialCompiledTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs all required compiled JSON Schema cases across all three drafts."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/OfficialCompiledTest.class")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Runtime SchemaPlan fallback must resolve the same generated classpath resources as javac.
    classpath = classpath + files(officialCompiledDrafts.map { draft ->
        officialCompiledGeneratedDir.get().dir("$draft/resources").asFile
    })
    systemProperty("sjf4j.schema.officialTestRoot", officialSchemaSuiteDir.get().asFile.absolutePath)
    systemProperty("sjf4j.schema.officialGeneratedRoot", officialCompiledGeneratedDir.get().asFile.absolutePath)
    systemProperty("sjf4j.schema.officialCompilerClasspath", sourceSets.test.get().runtimeClasspath.asPath)
    jvmArgs("-Xshare:off")
    inputs.dir(officialSchemaSuiteDir)
    outputs.upToDateWhen { false }
    dependsOn(generateOfficialCompiledCases)
    onlyIf("official suite exists locally") { cachedOfficialSuiteExists() }
    shouldRunAfter(tasks.test)
}

// Normal build/check: use the suite already cached on disk, never access GitHub.
// First checkout with no cached suite simply SKIPs these tasks. The release gate
// below always refreshes, so no publication can silently omit the official suite.
tasks.named("check") {
    dependsOn(officialRuntimeTest, officialCompiledTest, officialAnnotationTest)
}

// Release gate covers runtime and the full mandatory compiled suites of all three drafts.
// Publication invokes the downloader; normal build/check never invokes it.
val officialPublishGate by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Refreshes upstream main and runs required official runtime, compiled and annotation suites."
    dependsOn(downloadOfficialSchemaTests, officialRuntimeTest, officialCompiledTest, officialAnnotationTest)
    doLast {
        check(cachedOfficialSuiteExists()) { "Official suite missing before publish" }
        // A pinned local SHA may be used for debugging, never for a publication.
        check(providers.gradleProperty("jsonSchemaSuiteRef").orElse("main").get() == "main") {
            "Publication requires latest upstream main; remove -PjsonSchemaSuiteRef"
        }
    }
}
officialCompiledTest.configure { mustRunAfter(downloadOfficialSchemaTests) }

tasks.named("build") {
    dependsOn(tasks.named("jmhJar"))
}

//////////////////
// jacoco
val coverageProjects = listOf(":sjf4j-core", ":sjf4j-schema")
evaluationDependsOn(":sjf4j-schema")
val coverageExecFiles = files(
    coverageProjects.map { project(it).layout.buildDirectory.file("jacoco/test.exec") } +
    layout.buildDirectory.file("jacoco/officialRuntimeTest.exec") +
    layout.buildDirectory.file("jacoco/officialAnnotationTest.exec") +
    layout.buildDirectory.file("jacoco/officialCompiledTest.exec") +
    layout.buildDirectory.file("jacoco/test.exec")
)

// Coverage tasks run after every official suite; verification follows report generation.
tasks.jacocoTestReport {
    dependsOn(coverageProjects.map { "$it:test" } +
            tasks.test + officialRuntimeTest + officialAnnotationTest + officialCompiledTest)
    executionData(coverageExecFiles)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData(coverageExecFiles)
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.75")
            }
        }
    }
}

// build -> check runs the official suites and enforces the CI coverage gate.
tasks.named("check") {
    dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

gradle.projectsEvaluated {
    val coverageMainSourceSets = coverageProjects.map {
        project(it).extensions.getByType<SourceSetContainer>().named("main")
    }
    tasks.jacocoTestReport {
        classDirectories.setFrom(
            coverageMainSourceSets.map { ss -> ss.map { it.output.classesDirs } }
        )
        sourceDirectories.setFrom(
            coverageMainSourceSets.map { ss -> ss.map { it.allJava.srcDirs } }
        )
    }
    tasks.jacocoTestCoverageVerification {
        classDirectories.setFrom(
            coverageMainSourceSets.map { ss -> ss.map { it.output.classesDirs } }
        )
        sourceDirectories.setFrom(
            coverageMainSourceSets.map { ss -> ss.map { it.allJava.srcDirs } }
        )
    }
}




/////////////////////
/// GraalVM

val graalVmHome = providers.environmentVariable("GRAALVM_HOME")

val jmhJar = tasks.named<Jar>("jmhJar")

val jmhAgentOutput =
    layout.buildDirectory.dir("native/agent-output/jmhAgent")

val nativeBenchmark = providers.gradleProperty("nativeBenchmark")
    .orElse(".*(HandWriteBenchmark|HandReadBenchmark).*")

val jmhAgent = tasks.register<Exec>("jmhAgent") {
    group = "benchmark"
    description = "Runs JMH with the GraalVM Native Image tracing agent"

    dependsOn(jmhJar)

    doFirst {
        val outputDir = jmhAgentOutput.get().asFile
        delete(outputDir)
        outputDir.mkdirs()

        val jar = jmhJar.get().archiveFile.get().asFile

        executable("${graalVmHome.get()}/bin/java")

        args(
            "-agentlib:native-image-agent=config-output-dir=${outputDir.absolutePath}",
            "-cp",
            jar.absolutePath,
            "org.openjdk.jmh.Main",

            nativeBenchmark.get(),

            "-f", "0",
            "-wi", "0",
            "-i", "1",
            "-r", "100ms"
        )
    }
}

graalvmNative {
    toolchainDetection.set(false)

    binaries {
        named("main") {
            imageName.set("sjf4j-benchmark-native")
            mainClass.set("org.openjdk.jmh.Main")
            sharedLibrary.set(false)

            buildArgs.add("-O2")

            buildArgs.add("--install-exit-handlers")

            buildArgs.add(
                "--initialize-at-build-time=" +
                        "org.openjdk.jmh.infra," +
                        "org.openjdk.jmh.util.Utils," +
                        "org.openjdk.jmh.runner.InfraControl," +
                        "org.openjdk.jmh.runner.InfraControlL0," +
                        "org.openjdk.jmh.runner.InfraControlL1," +
                        "org.openjdk.jmh.runner.InfraControlL2," +
                        "org.openjdk.jmh.runner.InfraControlL3," +
                        "org.openjdk.jmh.runner.InfraControlL4"
            )

            buildArgs.add(
                "-H:ConfigurationFileDirectories=" +
                        jmhAgentOutput.get().asFile.absolutePath
            )

            buildArgs.add(
                "-H:IncludeResourceBundles=" +
                        "joptsimple.ExceptionMessages," +
                        "joptsimple.HelpFormatterMessages"
            )
        }
    }
}

/*
 * nativeCompile normally sees the main source set.
 * Replace that classpath with the JMH fat jar.
 */
tasks.named<BuildNativeImageTask>("nativeCompile") {
    dependsOn(jmhAgent)

    classpathJar.set(
        jmhJar.flatMap { it.archiveFile }
    )
}
