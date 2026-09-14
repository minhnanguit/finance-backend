plugins {
    // Auto-provisions the JDK declared in the toolchain block (Java 21) when it is missing locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "finance-backend"
