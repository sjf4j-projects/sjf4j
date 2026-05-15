plugins {
    id("java")
}


dependencies {
    implementation(project(":sjf4j"))

    // ByteBuddy — high-level bytecode generation
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")

    // ASM — low-level bytecode generation
    implementation("org.ow2.asm:asm:9.7.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}