plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
    id("com.vanniktech.maven.publish") version "0.35.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
//    withJavadocJar()
}

configurations {
    testCompileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    compileOnly("com.google.code.gson:gson:2.13.1")
    compileOnly("com.alibaba.fastjson2:fastjson2:2.0.59")
    compileOnly("org.yaml:snakeyaml:2.5")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.19")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    testImplementation("org.yaml:snakeyaml:2.5")

    // JMH
    jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    jmhImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    jmhImplementation("com.google.code.gson:gson:2.13.1")
    jmhImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
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

//publishing {
//    publications {
//        create<MavenPublication>("mavenJava") {
//            from(components["java"])
//            artifactId = "sjf4j"
//            pom {
//                name.set("SJF4J")
//                description.set("Simple JSON Facade for Java")
//                url.set("https://sjf4j.org")
//                licenses {
//                    license { name.set("MIT License"); url.set("https://opensource.org/license/mit") }
//                }
//                developers {
//                    developer { id.set("hannyu"); name.set("Yu Han"); email.set("hannyu@gmail.com") }
//                }
//                scm {
//                    connection.set("scm:git:https://github.com/sjf4j-projects/sjf4j.git")
//                    developerConnection.set("scm:git:ssh://github.com/sjf4j-projects/sjf4j.git")
//                    url.set("https://github.com/sjf4j-projects/sjf4j")
//                }
//            }
//        }
//    }
//    repositories {
//        maven {
//            name = "SonatypeOSSRH"
//            url = if (version.toString().uppercase().endsWith("SNAPSHOT"))
//                uri("https://central.sonatype.com/repository/maven-snapshots/")
//            else
//                uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
//
////            name = "SonatypeCentral"
////            url = uri("https://central.sonatype.com/api/v1/publisher")
//
//            credentials {
//                username = System.getenv("OSSRH_TOKEN_ID")
//                password = System.getenv("OSSRH_TOKEN_SECRET")
//            }
//        }
//    }
//}
//
//signing {
//    useInMemoryPgpKeys(
//        System.getenv("GPG_PRIVATE_KEY"),
//        System.getenv("GPG_PASSPHRASE")
//    )
//    sign(publishing.publications["mavenJava"])
//}