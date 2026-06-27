import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.Test
import java.io.BufferedInputStream
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("java-library")
    id("jacoco")
    id("me.champeau.jmh") version "0.7.2"
    id("com.vanniktech.maven.publish") version "0.35.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

configurations {
    testCompileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    api(project.findProject(":sjf4j")?.let { project(":sjf4j") } ?: "org.sjf4j:sjf4j:$version")
    compileOnly("com.ibm.icu:icu4j:77.1")

    // test
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    testImplementation("org.yaml:snakeyaml:2.5")
    testImplementation("com.ibm.icu:icu4j:77.1")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.25")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // jmh
    jmhImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

tasks.test {
    useJUnitPlatform()
    exclude("**/OfficialTest.class")
    jvmArgs(
        "-Xshare:off"
    )
}

/////////////////////
/// Official JSON Schema Test Suite

val officialSchemaSuiteDir = layout.buildDirectory.dir("official-json-schema-tests/suite")

fun githubStream(url: String) = URI(url).toURL().openConnection().apply {
    setRequestProperty("User-Agent", "sjf4j-gradle")
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

val downloadOfficialSchemaTests by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Downloads the latest official JSON Schema test suite into build/."
    outputs.dir(officialSchemaSuiteDir)
    outputs.upToDateWhen { false }

    doLast {
        val ref = providers.gradleProperty("jsonSchemaSuiteRef").orElse("main").get()
        val sha = jsonSchemaTestSuiteSha(ref)
        val outDir = officialSchemaSuiteDir.get().asFile
        delete(outDir)
        outDir.mkdirs()

        val outRoot = outDir.canonicalFile.toPath()
        val zipUrl = "https://codeload.github.com/json-schema-org/JSON-Schema-Test-Suite/zip/$sha"
        var copied = 0

        ZipInputStream(BufferedInputStream(githubStream(zipUrl))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val rootEnd = name.indexOf('/')
                if (rootEnd < 0) continue

                val relative = name.substring(rootEnd + 1)
                if (!relative.startsWith("tests/") && !relative.startsWith("remotes/")) continue

                val target = outDir.resolve(relative)
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

        outDir.resolve("OFFICIAL_TEST_SUITE_REV").writeText(
            "repo: https://github.com/json-schema-org/JSON-Schema-Test-Suite\n" +
                    "ref: $ref\n" +
                    "revision: $sha\n"
        )
        logger.lifecycle("Downloaded JSON-Schema-Test-Suite $sha ($copied files) to $outDir")
    }
}

val officialLatestTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs OfficialTest against the latest JSON-Schema-Test-Suite from GitHub."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/OfficialTest.class")
    systemProperty("sjf4j.schema.officialTestRoot", officialSchemaSuiteDir.get().asFile.absolutePath)
    jvmArgs("-Xshare:off")
    inputs.dir(officialSchemaSuiteDir)
    outputs.upToDateWhen { false }
    dependsOn(downloadOfficialSchemaTests)
    shouldRunAfter(tasks.test)
}

tasks.withType<Javadoc> {
    options.locale = "en_US"
    (options as? StandardJavadocDocletOptions)?.apply {
        addStringOption("Xdoclint:none", "-quiet")
    }
}


/////////////////////
/// Publish
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), name, version.toString())

    pom {
        name.set("SJF4J")
        description.set("SJF4J JSON Schema module — schema parsing, evaluation, format validation")
        inceptionYear.set("2026")
        url.set("https://sjf4j.org")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
            }
        }
        developers {
            developer {
                id.set("hannyu")
                name.set("Yu Han")
                url.set("https://github.com/hannyu/")
            }
        }
        scm {
            url.set("https://github.com/sjf4j-projects/sjf4j/")
            connection.set("scm:git:git://github.com/sjf4j-projects/sjf4j.git")
            developerConnection.set("scm:git:ssh://git@github.com/sjf4j-projects/sjf4j.git")
        }
    }
}

// Gradle 9 task validation: ensure metadata generation sees javadoc artifact producer.
tasks.matching { it.name == "generateMetadataFileForMavenPublication" }
    .configureEach {
        dependsOn(tasks.matching { it.name == "plainJavadocJar" })
    }

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(officialLatestTest)
}

tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn(officialLatestTest)
}

tasks.matching { it.name == "publish" || it.name == "publishToMavenLocal" }
    .configureEach {
        dependsOn(officialLatestTest)
    }
