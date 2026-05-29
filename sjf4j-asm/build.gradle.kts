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

repositories {
    maven {
        name = "Central Portal Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
    mavenCentral()
}

configurations {
    testCompileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation(project.findProject(":sjf4j")?.let { project(":sjf4j") } ?: "org.sjf4j:sjf4j:$version")

    // ASM — bytecode generation
    implementation("org.ow2.asm:asm:9.7.1")

    // test
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
        description.set("SJF4J bytecode acceleration module — ASM-based compiled-path compiler for SJF4J")
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
