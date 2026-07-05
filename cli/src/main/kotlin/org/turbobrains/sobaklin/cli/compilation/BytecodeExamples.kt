package org.turbobrains.sobaklin.cli.compilation

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool

open class CodeExample(val kotlinSourceCode: String) {
    override fun toString(): String = "Example:\n```kotlin\n$kotlinSourceCode\n```"
}

const val FILE_START_MARKER = "// MARKER:FILE_START\n"
const val FILE_END_MARKER = "// MARKER:FILE_END"

class BytecodeExample(
    kotlinSourceCode: String,
    val expectedOutput: String,
    val bytecodeGenerator: String,
) : CodeExample(kotlinSourceCode) {
    override fun toString(): String = super.toString() + """
        ```java
        $FILE_START_MARKER
        """.trimIndent() + bytecodeGenerator + """
        $FILE_END_MARKER
        ```
        Expected output: """.trimIndent() + expectedOutput
}

object BytecodeExamples {
    val HELLO_WORLD = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            fun main() {
                println("Hello from a sample Kotlin program!")
            }
        """.trimIndent(),
        expectedOutput = "Hello from a sample Kotlin program!",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;
            import java.lang.reflect.Method;
            
            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "MainKt", null, "java/lang/Object", null);
            
                    MethodVisitor mv;
            
                    // visit constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
            
                    // visit main method
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitLdcInsn("Hello from a sample Kotlin program!");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(2, 1);
                    mv.visitEnd();
            
                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val PRIMITIVE_ARRAYS = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            fun main() {
                val numbers = intArrayOf(1, 2, 3)
                
                for (number in numbers) {
                    print(number * number)
                }
            }
        """.trimIndent(),
        expectedOutput = "149",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;
            
            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "MainKt", null, "java/lang/Object", null);
            
                    MethodVisitor mv;
            
                    // visit constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
            
                    // visit main method
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    
                    // Create an array of integers
                    mv.visitIntInsn(BIPUSH, 3); // Array length
                    mv.visitIntInsn(NEWARRAY, T_INT);
                    mv.visitVarInsn(ASTORE, 1);
            
                    // Populate the array with values
                    int arrayIndex = 0;
                    for (int i : new int[]{1, 2, 3}) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitIntInsn(BIPUSH, arrayIndex++);
                        mv.visitIntInsn(BIPUSH, i);
                        mv.visitInsn(IASTORE);
                    }
                    
                    // int index = 0
                    mv.visitInsn(ICONST_0);
                    mv.visitVarInsn(ISTORE, 2); // local 2 = index
            
                    // int len = numbers.length
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitInsn(ARRAYLENGTH);
                    mv.visitVarInsn(ISTORE, 3); // local 3 = len
            
                    Label loopStart = new Label();
                    Label loopEnd = new Label();
            
                    mv.visitLabel(loopStart);
            
                    // if (index >= len) goto loopEnd
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitVarInsn(ILOAD, 3);
                    mv.visitJumpInsn(IF_ICMPGE, loopEnd);
            
                    // number = numbers[index].intValue()
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitInsn(IALOAD);
                    mv.visitVarInsn(ISTORE, 4); // local 4 = number
            
                    // print(number * number)
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitInsn(IMUL);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
            
                    // index++
                    mv.visitIincInsn(2, 1);
                    mv.visitJumpInsn(GOTO, loopStart);
            
                    mv.visitLabel(loopEnd);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(5, 5);
                    mv.visitEnd();
            
                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val BOXED_ARRAYS = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            fun main() {
                val numbers = arrayOf(1, 2, 3)
                
                for (number in numbers) {
                    print(number * number)
                }
            }
        """.trimIndent(),
        expectedOutput = "149",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;
            
            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "MainKt", null, "java/lang/Object", null);
            
                    MethodVisitor mv;
            
                    // visit constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
            
                    // visit main method
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    
                    // numbers = new Integer[]{1, 2, 3}
                    mv.visitInsn(ICONST_3);
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/Integer");
            
                    // Populate the array with values
                    int arrayIndex = 0;
                    for (int i : new int[]{1, 2, 3}) {
                        mv.visitInsn(DUP);
                        mv.visitIntInsn(BIPUSH, arrayIndex++);
                        mv.visitIntInsn(BIPUSH, i);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        mv.visitInsn(AASTORE);
                    }
                    mv.visitVarInsn(ASTORE, 1);
                    
                    // int index = 0
                    mv.visitInsn(ICONST_0);
                    mv.visitVarInsn(ISTORE, 2); // local 2 = index
            
                    // int len = numbers.length
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitInsn(ARRAYLENGTH);
                    mv.visitVarInsn(ISTORE, 3); // local 3 = len
            
                    Label loopStart = new Label();
                    Label loopEnd = new Label();
            
                    mv.visitLabel(loopStart);
            
                    // if (index >= len) goto loopEnd
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitVarInsn(ILOAD, 3);
                    mv.visitJumpInsn(IF_ICMPGE, loopEnd);
            
                    // number = numbers[index].intValue()
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitInsn(AALOAD);
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    mv.visitVarInsn(ISTORE, 4); // local 4 = number
            
                    // print(number * number)
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitVarInsn(ILOAD, 4);
                    mv.visitInsn(IMUL);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
            
                    // index++
                    mv.visitIincInsn(2, 1);
                    mv.visitJumpInsn(GOTO, loopStart);
            
                    mv.visitLabel(loopEnd);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(5, 5);
                    mv.visitEnd();
            
                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val LISTS = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            fun main() {
                val numbers = listOf(1, 2, 3)
                
                for (number in numbers) {
                    print(number * number)
                }
            }
        """.trimIndent(),
        expectedOutput = "149",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;
            
            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "MainKt", null, "java/lang/Object", null);
            
                    MethodVisitor mv;
            
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
            
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
            
                    // Create Integer[] {1, 2, 3}
                    mv.visitInsn(ICONST_3);
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/Integer");
                    int[] values = {1, 2, 3};
                    for (int i = 0; i < values.length; i++) {
                        mv.visitInsn(DUP);
                        mv.visitIntInsn(BIPUSH, i);
                        mv.visitIntInsn(BIPUSH, values[i]);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        mv.visitInsn(AASTORE);
                    }
            
                    // numbers = Arrays.asList(array)
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
                    mv.visitVarInsn(ASTORE, 1); // local 1 = numbers
            
                    // iterator = numbers.iterator()
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
                    mv.visitVarInsn(ASTORE, 2); // local 2 = iterator
            
                    Label loopStart = new Label();
                    Label loopEnd = new Label();
            
                    mv.visitLabel(loopStart);
            
                    // if (!iterator.hasNext()) goto loopEnd
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
                    mv.visitJumpInsn(IFEQ, loopEnd);
            
                    // number = ((Integer) iterator.next()).intValue()
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
                    mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    mv.visitVarInsn(ISTORE, 3); // local 3 = number
            
                    // print(number * number)
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitVarInsn(ILOAD, 3);
                    mv.visitVarInsn(ILOAD, 3);
                    mv.visitInsn(IMUL);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
            
                    mv.visitJumpInsn(GOTO, loopStart);
            
                    mv.visitLabel(loopEnd);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(5, 4);
                    mv.visitEnd();
            
                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    @Tool
    @LLMDescription("Shows an example for a Hello World")
    fun getHelloWorldExampleTool() = HELLO_WORLD.toString()

    @Tool
    @LLMDescription("Shows an example for a primitive arrays")
    fun getPrimitiveArraysExampleTool() = PRIMITIVE_ARRAYS.toString()

    @Tool
    @LLMDescription("Shows an example for boxed arrays")
    fun getBoxedArraysExampleTool() = BOXED_ARRAYS.toString()

    @Tool
    @LLMDescription("Shows an example for lists")
    fun getListsExampleTool() = LISTS.toString()

    @Suppress("unused")
    val toolRegistry = ToolRegistry {
        tool(::getHelloWorldExampleTool)
        tool(::getPrimitiveArraysExampleTool)
        tool(::getBoxedArraysExampleTool)
        tool(::getListsExampleTool)
    }
}