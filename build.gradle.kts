plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2024.2.4")
    type.set("IC") // or "IU" if we later switch to Ultimate

    // Pull the official Dart plugin into the sandbox so Dart PSI is available at runtime.
    plugins.set(listOf("Dart"))
}

tasks {
    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
    }
    runIde {
        jvmArgs("-Xmx2g")
    }
    buildSearchableOptions {
        enabled = false
    }
}