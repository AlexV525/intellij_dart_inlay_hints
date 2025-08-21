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
    type.set("IC") // Community Edition

    // Note: IDEA 2025.2 is not yet available. Using latest stable version.
    // Dart plugin dependency temporarily commented out due to compatibility issues
    // plugins.set(listOf("6351"))  // Dart plugin ID from JetBrains Marketplace
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