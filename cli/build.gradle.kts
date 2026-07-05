import java.util.Properties

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.ext)
    implementation(libs.asm)
    implementation(libs.asm.util)
    implementation(libs.asm.tree)
    implementation(libs.asm.analysis)
    implementation(libs.prompt.executor.openai.client)
    implementation(libs.agents.features.memory)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)
    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "org.turbobrains.sobaklin.cli.MainKt"

    tasks.run.configure {
        workingDir = findProperty("workingDir")?.toString()?.let(::file) ?: projectDir
    }
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")

    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val kotlinRuntimePath: String? = localProperties.getProperty("sobaklin.kotlin.runtime")

tasks.withType<Test>().configureEach {
    if (kotlinRuntimePath != null) {
        systemProperty("sobaklin.kotlin.runtime", kotlinRuntimePath)
    }

    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "info")
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Main-Class"] = application.mainClass
    }

    // Grabs all runtime dependencies and unpacks them into the final JAR
    val dependencies = configurations.runtimeClasspath.get().map { zipTree(it) }
    from(dependencies)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("dist") {
    group = "distribution"
    description = "Build `sobaklinc` distribution."

    val outputDir = layout.buildDirectory.dir("dist")
    into(outputDir)

    if (kotlinRuntimePath == null) {
        error("Kotlin runtime path is not set but is necessary to build the final distribution.")
    }
    from(file(kotlinRuntimePath)) {
        into("kotlin")
    }

    from(tasks.named("jar")) {
        into(".")
    }

    from(resources.text.fromString(
        $$"""
        #!/bin/bash

        script_full_path=$(dirname "$0")

        # logging="-Dorg.slf4j.simpleLogger.defaultLogLevel=info"

        java $logging -Dsobaklin.kotlin.runtime="$script_full_path/kotlin" -jar "$script_full_path/cli.jar" "$@"
        """.trimIndent()
    )) {
        rename { "sobaklinc" }

        // 4. Force Unix line endings (\n) and make the script executable
        filter { line -> line.replace("\r\n", "\n") }

        filePermissions {
            user {
                read = true
                write = true
                execute = true
            }
            group {
                read = true
                execute = true
            }
            other {
                read = true
                execute = true
            }
        }
    }
}
