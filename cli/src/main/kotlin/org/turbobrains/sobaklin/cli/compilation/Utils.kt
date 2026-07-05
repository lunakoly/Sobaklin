package org.turbobrains.sobaklin.cli.compilation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import org.turbobrains.sobaklin.cli.util.Outcome
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.net.URI
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

class InMemorySource(className: String, val source: String) : SimpleJavaFileObject(URI.create("string:///${className.replace('.', '/')}.java"), JavaFileObject.Kind.SOURCE) {
    override fun getCharContent(ignoreEncodingErrors: Boolean) = source
}

class InMemoryClass(originalName: String) : SimpleJavaFileObject(URI.create("mem:///${originalName.replace('.', '/')}.class"), JavaFileObject.Kind.CLASS) {
    private val out = ByteArrayOutputStream()

    override fun openOutputStream() = out

    fun bytes(): ByteArray = out.toByteArray()
}

class InMemoryFileManager(delegate: JavaFileManager) : ForwardingJavaFileManager<JavaFileManager>(delegate) {
    val classes = mutableMapOf<String, InMemoryClass>()

    override fun getJavaFileForOutput(
        location: JavaFileManager.Location,
        className: String,
        kind: JavaFileObject.Kind,
        sibling: FileObject?
    ): JavaFileObject = classes.getOrPut(className) { InMemoryClass(className) }
}

fun compileJava(source: String, className: String): Map<String, InMemoryClass> {
    val compiler = ToolProvider.getSystemJavaCompiler() ?: error("No compiler — must run on a JDK, not a JRE")
    val diagnostics = DiagnosticCollector<JavaFileObject>()
    val fileManager = InMemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null))

    // ASM must be on the classpath for the generated code to use it
    val options = listOf("-classpath", System.getProperty("java.class.path"))
    val task = compiler.getTask(
        null, fileManager, diagnostics, options, null,
        listOf(InMemorySource(className, source)),
    )

    if (!task.call()) {
        val errors = diagnostics.diagnostics
            .filter { it.kind == Diagnostic.Kind.ERROR }
            .joinToString("\n") { "${it.lineNumber}: ${it.getMessage(null)}" }
        error("Compilation failed:\n$errors")
    }

    return fileManager.classes
}

fun compileAndInvoke(source: String): ByteArray {
    val className = "BytecodeGenerator"
    val classes = compileJava(source, className)

    val loader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
        override fun findClass(name: String): Class<*> {
            val bytes = classes[name]?.bytes() ?: throw ClassNotFoundException(name)
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    val generatedResult = loader.loadClass(className).getMethod("generate").invoke(null)
    require(generatedResult is ByteArray) { "The result of the generate() method isn't a ByteArray" }
    return generatedResult
}

private val STACKTRACE_LINE_PATTERN = """^\s*at .*|Caused by:.*|\s*... \d+ more|""".toRegex()

fun verifyByTryingToLoad(generatedClass: ByteArray, expectedClassName: String) {
    val className = ClassReader(generatedClass).className.replace('/', '.')

    if (className != expectedClassName) {
        error("Error: class name should be $expectedClassName but was $className in the bytecode.")
    }

    object : ClassLoader(Thread.currentThread().contextClassLoader) {
        override fun findClass(name: String) = defineClass(name, generatedClass, 0, generatedClass.size)
        fun loadAndInit(name: String) = loadClass(name, true)
    }.loadAndInit(className)

    val errors = StringWriter()

    CheckClassAdapter.verify(
        ClassReader(generatedClass),
        Thread.currentThread().contextClassLoader,
        false,
        PrintWriter(errors),
    )

    if (errors.toString().isNotBlank()) {
        val cleanerOutput = errors.toString().lines()
            .filterNot { STACKTRACE_LINE_PATTERN.matches(it) }
            .joinToString("\n")

        error("Bytecode verification failed:\n$cleanerOutput")
    }
}

fun compileInvokeAndVerify(source: String, className: String): Outcome<ByteArray> {
    return try {
        val generatedClass = compileAndInvoke(source)
        verifyByTryingToLoad(generatedClass, className)
        Outcome.Success(generatedClass)
    } catch (e: LinkageError) {
        Outcome.Error(IllegalStateException("Bytecode verification failed: ${e.message}\nFix the BytecodeGenerator.", e))
    } catch (e: InvocationTargetException) {
        Outcome.Error(IllegalStateException("InvocationTargetException: ${e.targetException}\nFix the BytecodeGenerator.", e))
    } catch (e: Exception) {
        Outcome.Error(e)
    }
}
