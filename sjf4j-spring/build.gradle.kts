plugins {
    id("java-library")
    id("jacoco")
    id("com.vanniktech.maven.publish")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

dependencies {
    api(project(":sjf4j-core"))
    api("org.springframework:spring-context:6.2.15")

    testImplementation(project(":sjf4j-schema"))
    // Test-only Spring MVC integration example; no new runtime dependencies.
    testImplementation("org.springframework:spring-webmvc:6.2.15")
    testImplementation("org.springframework:spring-test:6.2.15")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    testImplementation(project(":sjf4j-backend-snake"))
    testImplementation("org.yaml:snakeyaml:2.7")
    testAnnotationProcessor(project(":sjf4j-processor"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), name, version.toString())

    pom {
        name.set("SJF4J Spring")
        description.set("Spring Framework Bean registration for SJF4J compiled interfaces")
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

tasks.matching { it.name == "generateMetadataFileForMavenPublication" }
    .configureEach {
        dependsOn(tasks.matching { it.name == "plainJavadocJar" })
    }
