plugins {
    id("java-library")
    id("jacoco")
    id("me.champeau.jmh") version "0.7.2"
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


dependencies {
    api(project.findProject(":sjf4j-core")?.let { project(":sjf4j-core") } ?: "org.sjf4j:sjf4j-core:$version")
    compileOnly("com.alibaba.fastjson2:fastjson2:2.0.59")

    testImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // JMH
    jmhImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    jmhImplementation("org.openjdk.jmh:jmh-core:1.36")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.36")
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
}


/////////////////////
/// Publish
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), name, version.toString())

    pom {
        name.set("Fastjson2 Backend for SJF4J")
        description.set("Fastjson2 Backend for SJF4J")
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
