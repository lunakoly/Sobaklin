package org.turbobrains.sobaklin.cli

import org.turbobrains.sobaklin.cli.compilation.BytecodeExample
import org.turbobrains.sobaklin.cli.compilation.CodeExample

object TestCodeExamples {
    val TYPE_MISMATCH = CodeExample("""
        // class name: MainKt
        fun main() {
            val i: Int = "Hello World!" // A classical TYPE_MISMATCH in Kotlin
        }
    """.trimIndent())

    val PROHIBITED_HELLO_WORLD = CodeExample("""
        // class name: MainKt
        fun main() {
            // Because `-XXLanguage:+ProhibitHelloWorld` is enabled by default
            // starting Kotlin 3.0, this line will no longer compile:
            println("Hello World!")
        }
    """.trimIndent())

    val BRAINROT_COMPILER_PLUGIN = CodeExample("""
        // class name: MainKt

        // With Kotlin Brainrot compiler plugin, using brainrot
        // references in identifiers becomes no longer possible.
        // For example:

        fun tunTunTunSagur() = 10 // Reports BRAINROT_DECLARATION now

        // Note how the compiler also flags forming brainrot expressions
        // before they are actually instantiated:

        fun seven() = 7
        fun main() = println(60 + seven()) // This addition would result in "six-seven"
    """.trimIndent())

    val GRANULAR_FEATURE_ENABLING = CodeExample("""
        // class name: MainKt

        // -XXLanguage:+ProhibitLocalVariables
        fun foo() {
            val a = 10
        }
        
        // -XXLanguage:-ProhibitLocalVariables
        fun bar() {
            val b = 10
        }
    """.trimIndent())
}

object TestBytecodeExamples {
    val COLLECTION_LITERALS = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            fun main() {
                // Since the "collection literals" feature is enabled, this is valid now
                val numbers = [1, 2, 3]
    
                for (number in numbers) {
                    println(number + 100)
                }
            }
        """.trimIndent(),
        expectedOutput = "101\n102\n103",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;

            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    MethodVisitor mv;

                    cw.visit(V1_6, ACC_PUBLIC | ACC_SUPER, "MainKt", null, "java/lang/Object", null);

                    // Constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    // main method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    
                    // val numbers = [1, 2, 3]
                    mv.visitInsn(ICONST_3);
                    mv.visitIntInsn(NEWARRAY, T_INT);
                    mv.visitVarInsn(ASTORE, 0); // Store array in local variable 0
                    
                    // Populate array elements
                    mv.visitVarInsn(ALOAD, 0); // Load array reference
                    mv.visitInsn(ICONST_0); // Index 0
                    mv.visitInsn(ICONST_1); // Value 1
                    mv.visitInsn(IASTORE); // Store at index 0
                    
                    mv.visitVarInsn(ALOAD, 0); // Load array reference
                    mv.visitInsn(ICONST_1); // Index 1
                    mv.visitInsn(ICONST_2); // Value 2
                    mv.visitInsn(IASTORE); // Store at index 1
                    
                    mv.visitVarInsn(ALOAD, 0); // Load array reference
                    mv.visitInsn(ICONST_2); // Index 2
                    mv.visitInsn(ICONST_3); // Value 3
                    mv.visitInsn(IASTORE); // Store at index 2

                    // for (number in numbers)
                    mv.visitVarInsn(ALOAD, 0); // Load array reference
                    mv.visitInsn(ARRAYLENGTH); // Get array length
                    mv.visitVarInsn(ISTORE, 2); // Store length in local variable 2
                    
                    mv.visitInsn(ICONST_0); // Initialize index = 0
                    mv.visitVarInsn(ISTORE, 1); // Store index in local variable 1

                    Label loopStart = new Label();
                    Label loopEnd = new Label();

                    mv.visitLabel(loopStart);
                    
                    // Loop condition check: index < length
                    mv.visitVarInsn(ILOAD, 1); // Load index
                    mv.visitVarInsn(ILOAD, 2); // Load length
                    mv.visitJumpInsn(IF_ICMPGE, loopEnd);

                    // number = numbers[index]
                    mv.visitVarInsn(ALOAD, 0); // Load array reference
                    mv.visitVarInsn(ILOAD, 1); // Load index
                    mv.visitInsn(IALOAD); // Load element at index
                    mv.visitVarInsn(ISTORE, 3); // Store in local variable 3 (number)

                    // println(number + 100)
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitVarInsn(ILOAD, 3); // Load number
                    mv.visitIntInsn(BIPUSH, 100); // Load 100
                    mv.visitInsn(IADD); // Add number + 100
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);

                    // Increment index
                    mv.visitIincInsn(1, 1); // index++
                    mv.visitJumpInsn(GOTO, loopStart);
                    
                    mv.visitLabel(loopEnd);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 4);
                    mv.visitEnd();

                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val TYPE_CLASSES = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt

            // Note the "typeclasses" feature is enabled, so this now works:
            typeclass CanBeComparedTo<T> {
                fun isSameAs(other: T): Boolean
            }
            
            impl CanBeComparedTo<String> for Int {
                fun isSameAs(other: String): Boolean = this.toString() == other
            }
    
            fun main() {
                val a = 10
                val b = "10"
                println(a.isSameAs(b)) // Knows which function to call at compile time
            }
        """.trimIndent(),
        expectedOutput = "true",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;

            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    MethodVisitor mv;

                    cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "MainKt", null, "java/lang/Object", null);

                    // Constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    // main method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    
                    // val a = 10
                    mv.visitIntInsn(BIPUSH, 10);
                    mv.visitVarInsn(ISTORE, 1);
                    
                    // val b = "10"
                    mv.visitLdcInsn("10");
                    mv.visitVarInsn(ASTORE, 2);
                    
                    // Call a.isSameAs(b)
                    // Load 'a' (int 10)
                    mv.visitVarInsn(ILOAD, 1);
                    // Convert int to String using Integer.toString()
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false);
                    // Load 'b'
                    mv.visitVarInsn(ALOAD, 2);
                    // Call equals method
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    
                    // println(...)
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitInsn(DUP);
                    mv.visitVarInsn(ILOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
                    
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 3);
                    mv.visitEnd();

                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val OVERLOAD_RESOLUTION_BY_USE_SITE_CONTEXT = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            // -XXLanguage:+OverloadResolutionByUseSiteContext

            fun foo(a: Number) = "A"
            fun foo(b: Comparable<*>) = "B"

            // Normally, this would be OVERLOAD_RESOLUTION_AMBIGUITY,
            // but thanks to `OverloadResolutionByUseSiteContext`,
            // all calls in `legacyCode` resolve to `foo(Number)`,
            // while all calls in `newCode` resolve to `foo(Comparable<*>)`.
            fun legacyCode() = foo(1)
            fun newCode() = foo(1)
    
            fun main() = println(legacyCode() + newCode())
        """.trimIndent(),
        expectedOutput = "AB",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;
            
            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    MethodVisitor mv;
            
                    cw.visit(V1_6, ACC_PUBLIC | ACC_SUPER, "MainKt", null, "java/lang/Object", null);
            
                    // Main method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitCode();
                    
                    // Call newCode()
                    mv.visitMethodInsn(INVOKESTATIC, "MainKt", "newCode", "()Ljava/lang/String;", false);

                    // Call legacyCode()
                    mv.visitMethodInsn(INVOKESTATIC, "MainKt", "legacyCode", "()Ljava/lang/String;", false);
                    
                    // Concatenate strings
                    mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                    
                    // Print result
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
                    
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 1);
                    mv.visitEnd();
            
                    // legacyCode method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "legacyCode", "()Ljava/lang/String;", null, null);
                    mv.visitCode();
                    
                    // Call foo with integer literal 1
                    // Convert int to Integer for Number parameter
                    mv.visitInsn(ICONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKESTATIC, "MainKt", "foo", "(Ljava/lang/Number;)Ljava/lang/String;", false);
                    
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
            
                    // newCode method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "newCode", "()Ljava/lang/String;", null, null);
                    mv.visitCode();
                    
                    // Call foo with integer literal 1
                    // Convert int to Integer for Comparable parameter
                    mv.visitInsn(ICONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKESTATIC, "MainKt", "foo", "(Ljava/lang/Comparable;)Ljava/lang/String;", false);
                    
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
            
                    // foo(Number) method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "foo", "(Ljava/lang/Number;)Ljava/lang/String;", null, null);
                    mv.visitCode();
                    
                    // Return "A"
                    mv.visitLdcInsn("A");
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
            
                    // foo(Comparable<*>) method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "foo", "(Ljava/lang/Comparable;)Ljava/lang/String;", null, null);
                    mv.visitCode();
                    
                    // Return "B"
                    mv.visitLdcInsn("B");
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )

    val INLINE_PROMPTS = BytecodeExample(
        kotlinSourceCode = """
            // class name: MainKt
            // -XXLanguage:+InlinePrompts

            fun List<Int>.reverse(): List<Int> {
                // prompt: implement the reversing functionality
            }

            fun main() {
                val numbers = listOf(10, -20, 41)

                // Calls the auto-generated function:
                println(numbers.reverse())
            }
        """.trimIndent(),
        expectedOutput = "[41, -20, 10]",
        bytecodeGenerator = """
            import org.objectweb.asm.*;
            import static org.objectweb.asm.Opcodes.*;

            public class BytecodeGenerator {
                public static byte[] generate() {
                    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    MethodVisitor mv;

                    cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "MainKt", null, "java/lang/Object", null);

                    // Constructor
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    // reverse extension function
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "reverse", "(Ljava/util/List;)Ljava/util/List;", "<T:Ljava/lang/Object;>(Ljava/util/List<TT;>;)Ljava/util/List<TT;>;", null);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Collections", "reverse", "(Ljava/util/List;)V", false);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    // main method
                    mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                    mv.visitVarInsn(ASTORE, 1);

                    // Add elements to list
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, 10);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                    mv.visitInsn(POP);

                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, -20);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                    mv.visitInsn(POP);

                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, 41);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                    mv.visitInsn(POP);

                    // Call reverse function
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKESTATIC, "MainKt", "reverse", "(Ljava/util/List;)Ljava/util/List;", false);
                    mv.visitVarInsn(ASTORE, 2);

                    // Print result
                    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 3);
                    mv.visitEnd();

                    cw.visitEnd();
                    return cw.toByteArray();
                }
            }
        """.trimIndent()
    )
}
