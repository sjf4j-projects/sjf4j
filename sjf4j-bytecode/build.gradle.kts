plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}


java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

dependencies {
    implementation(project.findProject(":sjf4j")?.let { project(":sjf4j") } ?: "org.sjf4j:sjf4j:$version")

    // ASM — bytecode generation
    implementation("org.ow2.asm:asm:9.7.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // JMH
    jmhImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}