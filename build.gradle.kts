import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    id("com.vanniktech.maven.publish") version "0.35.0" apply false
}

// Publication guard belongs to the root build: it applies when publishing any
// module, including single-project publishToMavenCentral calls. The testbench
// task checks upstream main and runs mandatory official runtime + compiled tests.
subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(":sjf4j-testbench:officialPublishGate")
        }
        tasks.withType<PublishToMavenLocal>().configureEach {
            dependsOn(":sjf4j-testbench:officialPublishGate")
        }
        tasks.matching {
            it.name == "publish" || it.name == "publishToMavenLocal" ||
                it.name == "publishToMavenCentral" ||
                it.name.startsWith("publishAllPublicationsTo")
        }.configureEach {
            dependsOn(":sjf4j-testbench:officialPublishGate")
        }
    }
}
