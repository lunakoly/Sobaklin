package org.turbobrains.sobaklin.cli

import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail

fun runAndGetOutput(jarPath: String): String {
    val running = ProcessBuilder("java", "-jar", jarPath)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    running.waitFor(10, TimeUnit.MINUTES)

    if (running.exitValue() != 0) {
        fail(running.errorStream.bufferedReader().readText().trim())
    }

    return running.inputStream.bufferedReader().readText().trim()
}

fun packageAndRunMainKt(fsManagement: FileSystemManagement): String {
    val manifestPath = fsManagement.temporaryDirectory.resolve("META-INF").resolve("MANIFEST.MF")
    val jarPath = fsManagement.temporaryDirectory.resolve("main.jar")

    assertTrue {
        fsManagement.createTextFile(
            """
                    Manifest-Version: 1.0
                    Main-Class: MainKt
                """.trimIndent() + "\n",
            path = manifestPath.absolutePath,
        ).startsWith("Written:")
    }
    assertTrue {
        addKotlinRuntimeTo(fsManagement.temporaryDirectory.absolutePath).startsWith("Finished:")
    }
    assertTrue {
        fsManagement.createJar(
            fsManagement.temporaryDirectory.absolutePath,
            jarPath.absolutePath,
            manifestPath.absolutePath,
        ).startsWith("Finished:")
    }
    return runAndGetOutput(jarPath.absolutePath)
}
