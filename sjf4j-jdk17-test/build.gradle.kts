plugins {
    java
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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    implementation("org.yaml:snakeyaml:2.5")
    implementation("com.ibm.icu:icu4j:77.1")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation("ch.qos.logback:logback-classic:1.5.25")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation("com.alibaba.fastjson2:fastjson2:2.0.59")
    testImplementation("org.yaml:snakeyaml:2.5")

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


/////////////////////