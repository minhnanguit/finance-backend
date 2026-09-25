import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    `jvm-test-suite`
    id("org.springframework.boot") version "4.1.1"
    id("org.openapi.generator") version "7.14.0"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "com.mosaicglobal.finance"
version = "0.1.0-SNAPSHOT"
description = "Finance backend – modular monolith"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"
val archunitVersion = "1.5.0"
val springModulithVersion = "2.1.1"
val springdocVersion = "3.1.1"

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.modulith:spring-modulith-bom:$springModulithVersion"))

    // Web / API
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("jakarta.annotation:jakarta.annotation-api")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Cache / idempotency
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // In-memory cache for the subject -> user id mapping on the authentication hot path
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Messaging
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("org.springframework.modulith:spring-modulith-events-api")
    implementation("org.springframework.modulith:spring-modulith-events-amqp")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    // Mapping
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // Unit + architecture tests (no Spring context, no Docker)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------------------------
// Contract-first: api/openapi.yaml -> server interfaces + DTOs. Controllers implement them.
// ---------------------------------------------------------------------------------------------
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(layout.projectDirectory.file("api/openapi.yaml").asFile.path)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.mosaicglobal.finance.api.v1")
    modelPackage.set("com.mosaicglobal.finance.api.v1.model")
    globalProperties.set(mapOf("apis" to "", "models" to ""))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "performBeanValidation" to "false",
            "openApiNullable" to "false",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "useResponseEntity" to "true",
            "serializableModel" to "false",
            "hideGenerationTimestamp" to "true",
            "dateLibrary" to "java8",
            "requestMappingMode" to "api_interface",
            "containerDefaultToNull" to "false",
        ),
    )
}

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// Ship the contract with the app so Swagger UI can render it (springdoc.swagger-ui.url=/openapi.yaml).
tasks.processResources {
    from(layout.projectDirectory.file("api/openapi.yaml")) {
        into("static")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-Amapstruct.defaultComponentModel=spring",
            "-Amapstruct.unmappedTargetPolicy=ERROR",
            "-Amapstruct.defaultInjectionStrategy=constructor",
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Test suites: `test` = unit + architecture (fast, no infra); `integrationTest` = Spring + Testcontainers.
// ---------------------------------------------------------------------------------------------
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                implementation(platform("org.springframework.modulith:spring-modulith-bom:$springModulithVersion"))
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.springframework.security:spring-security-test")
                implementation("org.springframework.modulith:spring-modulith-starter-test")
                implementation("org.testcontainers:testcontainers-postgresql")
                implementation("org.testcontainers:testcontainers-rabbitmq")
                implementation("org.awaitility:awaitility")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.test)
                }
            }
        }
    }
}

// The integration suite sees everything the application sees (RestClient, JdbcClient, Jackson...).
configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.implementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
}

tasks.check {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        formatAnnotations()
    }
}
