
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
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}


include("sjf4j")
include("sjf4j-schema")
include("sjf4j-jdk17-test")
include("sjf4j-bytecode")