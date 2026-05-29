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
include("sjf4j-asm")
include("sjf4j-schema")
include("sjf4j-jdk17-test")
include("sjf4j-processor")