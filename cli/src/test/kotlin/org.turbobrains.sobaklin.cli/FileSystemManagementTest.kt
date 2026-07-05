package org.turbobrains.sobaklin.cli

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSystemManagementTest {
    private fun testDirectoryBoundary(parent: File, fsManagement: FileSystemManagement) {
        val a = parent.resolve("a/a.txt").absolutePath
        assertEquals("Written: $a.", fsManagement.createTextFile("A", a))

        val b = parent.resolve("../b.txt").absolutePath
        assertEquals(
            "Error: `$b` lies outside the allowed temporary directory.",
            fsManagement.createTextFile("B", b),
        )

        val c = parent.resolve("c/c").absolutePath
        assertEquals("Created: $c.", fsManagement.createDirectory(c))

        val d = parent.resolve("../d").absolutePath
        assertEquals(
            "Error: `$d` lies outside the allowed temporary directory.",
            fsManagement.createDirectory(d)
        )
    }

    @Test
    fun testOnlyTemporaryDirectoryAllowed() {
        val fsManagement = FileSystemManagement(emptyList())
        testDirectoryBoundary(fsManagement.temporaryDirectory, fsManagement)
    }

    @Test
    fun testAlternativeDirectoryAllowed() {
        val alternativeDirectory = createTempDirectory().toFile()
        val fsManagement = FileSystemManagement(listOf(alternativeDirectory))

        testDirectoryBoundary(alternativeDirectory, fsManagement)
        testDirectoryBoundary(fsManagement.temporaryDirectory, fsManagement)
    }
}