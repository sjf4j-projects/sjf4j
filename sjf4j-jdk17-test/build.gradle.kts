import java.math.BigDecimal

plugins {
    id("java-library")
    id("jacoco")
    id("me.champeau.jmh") version "0.7.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    implementation(project(":sjf4j"))
    implementation(project(":sjf4j-bytecode"))
    implementation(project(":sjf4j-schema"))

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("tools.jackson.core:jackson-databind:3.1.1")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    implementation("org.yaml:snakeyaml:2.5")

    // test
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.25")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // jmh
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

val coverageProjects = listOf(":sjf4j", ":sjf4j-bytecode", ":sjf4j-schema")
val coverageExecFiles = files(
    coverageProjects.map { project(it).layout.buildDirectory.file("jacoco/test.exec") } +
    layout.buildDirectory.file("jacoco/test.exec")
)

tasks.jacocoTestReport {
    dependsOn(coverageProjects.map { "$it:test" } + tasks.test)
    executionData(coverageExecFiles)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(coverageProjects.map { "$it:test" } + tasks.test)
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
