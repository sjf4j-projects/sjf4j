plugins {
    id("java-library")
    id("jacoco")
    id("com.vanniktech.maven.publish")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
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
    compileOnly("tools.jackson.core:jackson-databind:3.2.0")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    compileOnly("com.google.code.gson:gson:2.13.1")
    compileOnly("com.alibaba.fastjson2:fastjson2:2.0.59")
    compileOnly("jakarta.json:jakarta.json-api:2.1.3")
    compileOnly("org.eclipse.parsson:parsson:1.1.7")
    compileOnly("org.yaml:snakeyaml:2.5")
    compileOnly("com.ibm.icu:icu4j:77.1")

    // test
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    testImplementation("jakarta.json:jakarta.json-api:2.1.3")
    testImplementation("org.eclipse.parsson:parsson:1.1.7")
    testImplementation("org.yaml:snakeyaml:2.5")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.34")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
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
        name.set("SJF4J Core")
        description.set("Core JSON facade and structural processing APIs for Java")
        inceptionYear.set("2025")
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



/////////////////////
/// Incubator
val incubator by sourceSets.creating {
    java.srcDir("src/incubator/java")
    resources.srcDir("src/incubator/resources")

    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
val incubatorTest by sourceSets.creating {
    java.srcDir("src/incubatorTest/java")
    resources.srcDir("src/incubatorTest/resources")

    compileClasspath += sourceSets.main.get().output + incubator.output
    runtimeClasspath += output + compileClasspath
}
configurations.named(incubator.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
configurations.named(incubator.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}
configurations.named(incubatorTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(incubatorTest.compileOnlyConfigurationName) {
    extendsFrom(configurations.testCompileOnly.get())
}
configurations.named(incubatorTest.annotationProcessorConfigurationName) {
    extendsFrom(configurations.testAnnotationProcessor.get())
}
configurations.named(incubatorTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("incubatorTest") {
    description = "Runs incubator tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = incubatorTest.output.classesDirs
    classpath = incubatorTest.runtimeClasspath
    useJUnitPlatform()
    jvmArgs("-Xshare:off")
}
tasks.named("check") { dependsOn("incubatorTest") }
