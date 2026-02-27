// FILE 1: Root build.gradle.kts
// Declares plugins for all subprojects (apply false = available but not applied here)

plugins {
    kotlin("jvm") version "1.9.20" apply false
    kotlin("plugin.serialization") version "1.9.20" apply false
    id("io.ktor.plugin") version "2.3.4" apply false
}

group = "com.menu"
version = "1.0.0"

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx/maven")
    }
}
