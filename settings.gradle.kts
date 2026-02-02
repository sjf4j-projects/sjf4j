
rootProject.name = "sjf4j"

include("sjf4j")
include("sjf4j-jdk17-test")



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
