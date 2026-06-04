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
    implementation(project.findProject(":sjf4j")?.let { project(":sjf4j") } ?: "org.sjf4j:sjf4j:$version")

    // test
    testImplementation(project.findProject(":sjf4j-schema")?.let { project(":sjf4j-schema") } ?: "org.sjf4j:sjf4j-schema:$version")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    testImplementation("org.yaml:snakeyaml:2.5")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.25")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // JMH
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
        name.set("SJF4J")
        description.set("SJF4J annotation processor module — compile-time generators for paths and mappers")
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


/////////////////////
/// Incubator
evaluationDependsOn(":sjf4j")
val sjf4jIncubator = project(":sjf4j")
    .extensions
    .getByType<SourceSetContainer>()
    .getByName("incubator")

val incubator by sourceSets.creating {
    java.srcDir("src/incubator/java")
    resources.srcDir("src/incubator/resources")

    compileClasspath += sourceSets.main.get().output
    compileClasspath += sjf4jIncubator.output
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
    add(incubator.implementationConfigurationName, project(":sjf4j-schema"))
    add(incubator.implementationConfigurationName, platform("org.junit:junit-bom:5.10.0"))
    add(incubator.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
    add(incubator.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
}
