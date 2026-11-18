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
    api("org.springframework.boot:spring-boot-starter:3.4.13")

    testImplementation(project(":sjf4j-schema"))
    testAnnotationProcessor(project(":sjf4j-processor"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.13")
    testImplementation("org.springframework.boot:spring-boot-starter-web:3.4.13")
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
        name.set("SJF4J Spring Boot Starter")
        description.set("Spring Boot auto-configuration for SJF4J compiled interfaces")
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
