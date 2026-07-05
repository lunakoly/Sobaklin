package org.turbobrains.sobaklin.cli

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class FileSystemManagement(outputPaths: List<File>) {
    val temporaryDirectory: File = createTempDirectory().toFile()
    private val allowedPaths = listOf(temporaryDirectory) + outputPaths

    fun allowsWritingTo(file: File): Boolean {
        val normalizedPath = file.normalize().absolutePath
        return allowedPaths.any { normalizedPath.startsWith(it.absolutePath) }
    }

    inline fun requireWriteAccess(file: File, block: (String) -> Unit) {
        if (!allowsWritingTo(file)) {
            block("Error: `${file.absolutePath}` lies outside the allowed temporary directory.")
        }
    }

    inline fun requireWriteAccess(path: String, block: (String) -> Unit) = requireWriteAccess(File(path), block)

    @Tool
    @LLMDescription("Returns the absolute path to a fresh temporary directory.")
    fun prepareTemporaryDirectory(): String = temporaryDirectory.absolutePath

    @Tool
    @LLMDescription("Lists the names of all the files and directories which are immediate children of the target one.")
    fun listDirectory(path: String): String = File(path).list()?.joinToString("\n") {
        when {
            File(path).resolve(it).isDirectory -> "$it (directory)"
            else -> it
        }
    } ?: "Failure: no such directory or another kind of error."

    @Tool
    @LLMDescription("Creates a directory along with all the needed parents if it doesn't already exist.")
    fun createDirectory(path: String): String {
        requireWriteAccess(path) { return it }
        File(path).mkdirs()
        return "Created: $path."
    }

    @Tool
    @LLMDescription("Returns the contents of the file as if they were text.")
    fun readFileAsText(path: String): String = File(path).readText()

    @Tool
    @LLMDescription("Creates or overrides a text file with the following contents.")
    fun createTextFile(contents: String, path: String): String {
        requireWriteAccess(path) { return it }
        File(path).also { it.parentFile?.mkdirs() }.writeText(contents)
        return "Written: $path."
    }

    @Tool
    @LLMDescription("Moves the file or the directory from `oldPath` to `newPath`.")
    fun move(oldPath: String, newPath: String): String {
        requireWriteAccess(oldPath) { return it }
        requireWriteAccess(newPath) { return it }
        Files.move(Paths.get(oldPath), Paths.get(newPath), StandardCopyOption.REPLACE_EXISTING)
        return "Moved: `$oldPath` to `$newPath`."
    }

    @Tool
    @LLMDescription("Returns `FILE` if `path` denotes a file, `DIRECTORY` if a directory, and `NOT FOUND` if it doesn't exist.")
    fun checkPath(path: String): String {
        val file = File(path)
        return when {
            !file.exists() -> "NOT FOUND"
            file.isFile -> "FILE"
            else -> "DIRECTORY"
        }
    }

    val mainClassInManifestPattern = """Main-Class: (.*)\n""".toRegex()

    @Tool
    @LLMDescription($$"Calls `jar cfm $resultingJarPath $manifestPath $sourceDirectoryPath`.")
    fun createJar(
        sourceDirectoryPath: String,
        resultingJarPath: String,
        manifestPath: String,
    ): String {
        if (!File(manifestPath).exists()) {
            return "Error: the manifest file doesn't exist. You must generate it manually."
        }
        requireWriteAccess(resultingJarPath) { return it }

        val mainClass = mainClassInManifestPattern.find(File(manifestPath).readText())?.groupValues?.get(1)
        val mainClassFile = mainClass?.let {
            File(sourceDirectoryPath).resolve(mainClass.replace(".", "/") + ".class")
        }

        if (mainClassFile != null && !mainClassFile.exists()) {
            return "Error: `Main-Class:` specifies `$mainClass` but there's no `${mainClassFile.absolutePath}`."
        }

        if (File(resultingJarPath).exists()) {
            File(resultingJarPath).deleteRecursively()
        }

        fun String.normalized(): String = File(this).normalize().absolutePath

        val process = ProcessBuilder("jar", "cfm", resultingJarPath.normalized(), manifestPath.normalized(), ".")
            .directory(File(sourceDirectoryPath))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        process.waitFor(3, TimeUnit.MINUTES)

        return when {
            process.exitValue() == 0 -> "Finished: $resultingJarPath. Output: \n" + process.inputStream.bufferedReader().readText()
            else -> "Error: exit code ${process.exitValue()}, ${process.errorStream.bufferedReader().readText()}"
        }
    }
}
