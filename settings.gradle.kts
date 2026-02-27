// FILE 2: Root settings.gradle.kts
// Defines the project name and includes the :server submodule

rootProject.name = "restaurant-menu-scanner-backend"

include(":server")
project(":server").projectDir = file("server")
