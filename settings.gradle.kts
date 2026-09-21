import org.gradle.kotlin.dsl.maven

rootProject.name = "sjf4j"


/// Proxy: +aliyun
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}


include("sjf4j")
include("sjf4j-core")
include("sjf4j-asm")
include("sjf4j-schema")
include("sjf4j-testbench")
include("sjf4j-processor")
include("sjf4j-processor2")

include("sjf4j-backend-gson")
include("sjf4j-backend-jackson2")
include("sjf4j-backend-fastjson2")
include("sjf4j-backend-snake")