

subprojects {
    // 所有模块（含父模块）的通用配置
    group = "org.sjf4j"
    version = "0.1.0"

    pluginManager.withPlugin("java") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }

}

