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
    // External JSON Schema baseline using Jackson 2 (not a production dependency).
    jmhImplementation("com.networknt:json-schema-validator:2.0.5")

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
        && outDir.resolve("validation/tests").isDirectory
        && outDir.resolve("validation/validation-test-schema.json").isFile
        && outDir.resolve("annotations/tests/meta-data.json").isFile
        && revisionFile.readText().lineSequence().any { it.trim() == "layout: unified-validation-v1" }
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
                if (!relative.startsWith("validation/") && !relative.startsWith("annotations/")) continue

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
        require(staging.resolve("validation/tests").isDirectory
                && staging.resolve("validation/tests").listFiles()?.any { it.isFile && it.extension == "json" } == true
                && staging.resolve("validation/validation-test-schema.json").isFile
                && staging.resolve("annotations/tests/meta-data.json").isFile) {
            "Downloaded suite $sha is missing required validation or annotation fixtures"
        }
        staging.resolve("OFFICIAL_TEST_SUITE_REV").writeText(
            "repo: https://github.com/json-schema-org/JSON-Schema-Test-Suite\n" +
                "ref: $ref\n" +
                "revision: $sha\n" +
                "layout: unified-validation-v1\n"
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
    officialSchemaSuiteDir.get().asFile.resolve("validation/tests").isDirectory &&
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
    include("**/OfficialAnnotationTest.class",
        "**/OfficialAnnotationCompatibilityTest.class",
        "**/OfficialAnnotationSuiteCoverageTest.class")
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
 * Official unified Validation Suite compatibility. Tests use ordered draft
 * intervals; this matches the upstream validation/README.md algorithm.
 */
fun officialValidationCompatible(rule: String?, selectedDraft: Int): Boolean {
    if (rule == null) return true
    require(Regex("^(<=|=)?[0-9]+(,(<=|=)?[0-9]+)*$").matches(rule)) {
        "Invalid official compatibility: $rule"
    }
    var eligible = false
    for (constraint in rule.split(",")) {
        val upper = constraint.startsWith("<=")
        val exact = constraint.startsWith("=")
        val version = constraint.substring(if (upper) 2 else if (exact) 1 else 0).toInt()
        eligible = when {
            upper -> selectedDraft <= version
            exact -> selectedDraft == version
            else -> selectedDraft >= version
        }
        if (selectedDraft <= version) break
    }
    return eligible
}

/**
 * Generate one independent @CompiledValidator per compatible official schema
 * group. External schemas are copied as distinct classpath resources and passed
 * to programmatic javac as retrieval URI -> source URI bindings.
 */
val officialCompiledDrafts = listOf("draft7", "draft2019-09", "draft2020-12")
val officialCompiledGeneratedDir = layout.buildDirectory.dir("generated/officialCompiled")
val generateOfficialCompiledCases by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates compiled validator fixtures for the unified official Validation Suite."
    onlyIf("unified official suite exists locally") { cachedOfficialSuiteExists() }
    inputs.dir(officialSchemaSuiteDir)
    outputs.dir(officialCompiledGeneratedDir)

    doLast {
        val outputRoot = officialCompiledGeneratedDir.get().asFile
        delete(outputRoot)
        val input = officialSchemaSuiteDir.get().asFile.resolve("validation/tests")
        val files = requireNotNull(input.listFiles())
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.name }
        require(files.isNotEmpty()) { "No official Validation files at $input" }

        for (draft in officialCompiledDrafts) {
            val dialectVersion = when (draft) {
                "draft7" -> 7
                "draft2019-09" -> 2019
                else -> 2020
            }
            val dialectUri = when (draft) {
                "draft7" -> "http://json-schema.org/draft-07/schema#"
                "draft2019-09" -> "https://json-schema.org/draft/2019-09/schema"
                else -> "https://json-schema.org/draft/2020-12/schema"
            }
            val output = outputRoot.resolve(draft)
            val packageName = "org.sjf4j.testbench.schema.official.generated"
            val javaDir = output.resolve("java/" + packageName.replace('.', '/'))
            val resourceDir = output.resolve("resources/official/$draft")
            javaDir.mkdirs()
            resourceDir.mkdirs()

            val manifest = StringBuilder("file\tgroup\tclass\ttests\n")
            var fileCount = 0
            var groupCount = 0
            var testCount = 0
            for (file in files) {
                val suite = JsonSlurper().parse(file) as? Map<*, *>
                    ?: throw GradleException("Invalid official Validation file: $file")
                val groups = suite["tests"] as? List<*>
                    ?: throw GradleException("Missing official tests array: $file")
                val stem = file.nameWithoutExtension
                val classStem = stem.split(Regex("[^a-zA-Z0-9]+"))
                    .filter { it.isNotEmpty() }
                    .joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                var selectedInFile = 0
                for ((groupIndex, raw) in groups.withIndex()) {
                    val item = raw as? Map<*, *>
                        ?: throw GradleException("Invalid official group: $file #$groupIndex")
                    val compatible = officialValidationCompatible(
                        item["compatibility"] as? String, dialectVersion)
                    if (!compatible) continue
                    val tags = item["tags"] as? List<*>
                    if (tags != null) {
                        require(tags.all { it == "format-assertion" || it == "format-annotation" }) {
                            "Unknown official tags: $file #$groupIndex"
                        }
                        if (tags.contains("format-assertion")) continue
                    }
                    require(item.containsKey("schema")) { "Missing schema: $file #$groupIndex" }
                    val tests = item["tests"] as? List<*>
                        ?: throw GradleException("Missing tests: $file #$groupIndex")
                    require(tests.isNotEmpty()) { "Empty official tests: $file #$groupIndex" }
                    val index = groupIndex.toString().padStart(3, '0')
                    val className = "Official" + classStem + "G" + index
                    val schemaPath = "$stem/$index.json"
                    val schemaFile = resourceDir.resolve(schemaPath)
                    schemaFile.parentFile.mkdirs()
                    val originalSchema = item["schema"]
                    val compiledSchema = if (draft != "draft2020-12"
                        && originalSchema is Map<*, *>
                        && !originalSchema.containsKey("$" + "schema")) {
                        linkedMapOf<String, Any?>("$" + "schema" to dialectUri).apply {
                            for ((key, value) in originalSchema) {
                                require(key is String) { "Non-string schema key: $file #$groupIndex" }
                                put(key, value)
                            }
                        }
                    } else originalSchema
                    schemaFile.writeText(JsonOutput.toJson(compiledSchema), Charsets.UTF_8)

                    var externalAttribute = ""
                    val external = item["externalSchemas"] as? Map<*, *>
                    if (external != null) {
                        val documents = linkedMapOf<String, Any?>()
                        for ((retrievalUri, rawSchema) in external) {
                            require(retrievalUri is String) {
                                "Non-string external URI: $file #$groupIndex"
                            }
                            val mappedSchema = if (draft != "draft2020-12"
                                && rawSchema is Map<*, *> && !rawSchema.containsKey("$" + "schema")) {
                                linkedMapOf<String, Any?>("$" + "schema" to dialectUri).apply {
                                    for ((key, value) in rawSchema) {
                                        require(key is String) { "Non-string external schema key" }
                                        put(key, value)
                                    }
                                }
                            } else rawSchema
                            documents[retrievalUri] = mappedSchema
                        }
                        val externalPath = "external/$stem/$index.json"
                        val externalFile = resourceDir.resolve(externalPath)
                        externalFile.parentFile.mkdirs()
                        externalFile.writeText(JsonOutput.toJson(documents), Charsets.UTF_8)
                        externalAttribute = ", externalSchemasRef = \"classpath:/official/$draft/$externalPath\""
                    }
                    for (allowFallback in listOf(false, true)) {
                        val variant = if (allowFallback) className + "Fallback" else className
                        val source = listOf(
                            "package $packageName;",
                            "",
                            "import org.sjf4j.annotation.schema.CompiledValidator;",
                            "import org.sjf4j.annotation.schema.ValidJsonSchema;",
                            "",
                            "@CompiledValidator(fallback = $allowFallback)",
                            "public interface $variant {",
                            "    @ValidJsonSchema(ref = \"classpath:/official/$draft/$schemaPath\"$externalAttribute)",
                            "    boolean valid(Object value);",
                            "}"
                        ).joinToString("\n", postfix = "\n")
                        javaDir.resolve("$variant.java").writeText(source, Charsets.UTF_8)
                    }
                    manifest.append(file.name).append('\t').append(groupIndex)
                        .append('\t').append(className).append('\t').append(tests.size).append('\n')
                    selectedInFile++
                    groupCount++
                    testCount += tests.size
                }
                if (selectedInFile > 0) fileCount++
            }
            require(groupCount > 0) { "No compatible official cases for $draft" }
            output.resolve("index.tsv").writeText(manifest.toString(), Charsets.UTF_8)
            output.resolve("counts.properties").writeText(
                "files=$fileCount\ngroups=$groupCount\ntests=$testCount\n", Charsets.UTF_8
            )
            logger.lifecycle("Generated $draft: $fileCount files, $groupCount groups, $testCount assertions")
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
    onlyIf("unified official suite exists locally") { cachedOfficialSuiteExists() }
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

// Opt-in Binding Native Image training: visit every supported backend/implementation
// and both model shapes so the tracing agent records the required reachability metadata.
val bindingNativeImage = providers.gradleProperty("bindingNativeImage")
    .map { it.toBoolean() }.orElse(false)

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

        if (bindingNativeImage.get()) {
            // On the JVM Fastjson2 normally uses ASM, while Native Image uses
            // reflection. Train the tracing agent on the reflective path so
            // the native direct-library baseline gets its required metadata.
            args("-Dfastjson2.creator=reflect")
        }

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

        if (bindingNativeImage.get()) {
            args(
                "-p", "backend=jackson2,jackson3,fastjson2,gson",
                "-p", "implementation=native,runtime,compiled",
                "-p", "model=users,clients",
                "-p", "shape=pojo,map",
                // A 1KB Clients fixture may have no Partner entries, so the
                // tracing agent never sees Fastjson2's reflective 'since' field.
                "-p", "sizeKb=100",
                "-p", "seed=12345"
            )
        }
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

            if (bindingNativeImage.get()) {
                // Diagnostic only: Fastjson2 upstream issue #2834 suggests
                // build-time initialization of the package to cover objects
                // retained by its eager default providers. Keep its own
                // native-image.properties active; validate with JMH Smoke.
                buildArgs.add("--initialize-at-build-time=com.alibaba.fastjson2")
            }

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
    // Tracing-agent output is a real Native Image compilation input.
    inputs.dir(jmhAgentOutput)
    inputs.property("bindingNativeImage", bindingNativeImage)

    classpathJar.set(
        jmhJar.flatMap { it.archiveFile }
    )
}
