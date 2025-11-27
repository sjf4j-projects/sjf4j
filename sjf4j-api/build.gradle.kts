plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8 // 限制语法
    targetCompatibility = JavaVersion.VERSION_1_8 // 限制字节码
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    compileOnly("com.google.code.gson:gson:2.13.1")
    compileOnly("com.alibaba.fastjson2:fastjson2:2.0.59")
    compileOnly("org.yaml:snakeyaml:2.5")

    compileOnly("org.slf4j:slf4j-api:2.0.17")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    testImplementation("ch.qos.logback:logback-classic:1.5.18")
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

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off"
    )
}


// JMH
jmh {
//    warmupIterations.set(3)
//    iterations.set(5)
//    fork.set(1)
//    timeOnIteration.set("500ms")
//    threads.set(1)
//    jvmArgs.set(listOf(
//        "-XX:+UnlockDiagnosticVMOptions",
//        "-XX:+PrintInlining"
//    ))
}


