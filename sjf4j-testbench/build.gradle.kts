import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

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
    implementation(project(":sjf4j"))
    implementation(project(":sjf4j-asm"))
    implementation(project(":sjf4j-schema"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("tools.jackson.core:jackson-databind:3.2.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    implementation("org.yaml:snakeyaml:2.5")

    // test
    testAnnotationProcessor(project(":sjf4j-processor"))
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.34")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
    jmhImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    jmhImplementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.22.1")
    jmhImplementation("tools.jackson.core:jackson-databind:3.2.0")
    jmhImplementation("tools.jackson.module:jackson-module-blackbird:3.2.0")
    jmhImplementation("com.google.code.gson:gson:2.13.1")
    jmhImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    jmhImplementation("jakarta.json:jakarta.json-api:2.1.3")
    jmhImplementation("org.eclipse.parsson:parsson:1.1.7")

}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off"
    )
}

val coverageProjects = listOf(":sjf4j", ":sjf4j-asm", ":sjf4j-schema")
evaluationDependsOn(":sjf4j-schema")
val schemaOfficialLatestTest = project(":sjf4j-schema").tasks.named("officialLatestTest")
val coverageExecFiles = files(
    coverageProjects.map { project(it).layout.buildDirectory.file("jacoco/test.exec") } +
    project(":sjf4j-schema").layout.buildDirectory.file("jacoco/officialLatestTest.exec") +
    layout.buildDirectory.file("jacoco/test.exec")
)

tasks.jacocoTestReport {
    dependsOn(coverageProjects.map { "$it:test" } + tasks.test + schemaOfficialLatestTest)
    executionData(coverageExecFiles)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(coverageProjects.map { "$it:test" } + tasks.test + schemaOfficialLatestTest)
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
/// Incubator
evaluationDependsOn(":sjf4j")
val sjf4jIncubator = project(":sjf4j")
    .extensions
    .getByType<SourceSetContainer>()
    .getByName("incubator")

evaluationDependsOn(":sjf4j-processor")
val processorIncubator = project(":sjf4j-processor")
    .extensions
    .getByType<SourceSetContainer>()
    .getByName("incubator")

val incubator by sourceSets.creating {
    java.srcDir("src/incubator/java")
    resources.srcDir("src/incubator/resources")

    compileClasspath += sourceSets.test.get().compileClasspath
    compileClasspath += sourceSets.test.get().output
    compileClasspath += sjf4jIncubator.output
    compileClasspath += processorIncubator.output

    runtimeClasspath += output + compileClasspath
}
configurations.named(incubator.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
configurations.named(incubator.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}
configurations.named(incubator.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    add(incubator.implementationConfigurationName, project(":sjf4j"))
    add(incubator.implementationConfigurationName, project(":sjf4j-schema"))
    add(incubator.implementationConfigurationName, platform("org.junit:junit-bom:5.10.0"))
    add(incubator.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
    add(incubator.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    add(incubator.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
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
