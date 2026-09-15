plugins {
    id("java-library")
    id("com.vanniktech.maven.publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

dependencies {
    api(project(":sjf4j-core"))
    api(project(":sjf4j-schema"))
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), name, version.toString())

    pom {
        name.set("SJF4J")
        description.set("Simple JSON Facade for Java")
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
