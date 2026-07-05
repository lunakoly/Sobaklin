# Kotlin K3 Compiler (aka Sobaklin)

An AI-powered drop-in replacement for `kotlinc`, the compiler for Kotlin programming language.

Compiles Kotlin programs by looking at the source code and hallucinating JVM bytecode. 
Supports all possible language features, current and future ones.
Great for prototyping novel KEEPs, superb for providing the so much necessary coffee breaks.

Because the compiler supports all possible language features, Kotlin language has
finally reached the ideal state, so it no longer needs a language version or
even updates.

## Usage

Designed to accept the same CLI arguments as the OG `kotlinc`, so a simple "Hello World!"
may be compiled and run with:

```bash
$ sobaklinc Main.kt -include_runtime -d App.jar
$ java -jar App.jar
```

> Seriously, there's a separate agent who's only job is to determine
> what `kotlinc` would do given the same arguments.

Keep in mind that the AI nature of the compiler makes its result quite probabilistic,
but it did compile on my machine a few times!

## My Favorite Examples

### Collection Literals

```kotlin
// class name: MainKt
fun main() {
    // Since the "collection literals" feature is enabled, this is valid now
    val numbers = [1, 2, 3]

    for (number in numbers) {
        println(number + 100)
    }
}
```
Prints:
```
101
102
103
```

### Type Classes

```kotlin
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
```
Prints:
```
true
```

### -XXLanguage:ProhibitHelloWorld

```kotlin
// class name: MainKt
fun main() {
    // Because `-XXLanguage:+ProhibitHelloWorld` is enabled by default
    // starting Kotlin 3.0, this line will no longer compile:
    println("Hello World!")
}
```
Prints:
```
MainKt: [PROHIBIT_HELLO_WORLD] Hello World! is prohibited by default in Kotlin 3.0 due to -XXLanguage:+ProhibitHelloWorld flag.
```

They say, in Kotlin 3.0, writing inefficient code would be downright impossible!

### Brainrot Compiler Plugin

```kotlin
// class name: MainKt

// With Kotlin Brainrot compiler plugin, using brainrot
// references in identifiers becomes no longer possible.
// For example:

fun tunTunTunSagur() = 10 // Reports BRAINROT_DECLARATION now

// Note how the compiler also flags forming brainrot expressions
// before they are actually instantiated:

fun seven() = 7
fun main() = println(60 + seven()) // This addition would result in "six-seven"
```
Prints:
```
MainKt: [BRAINROT_DECLARATION] The declaration 'tunTunTunSagur' is not allowed as it violates brainrot rules.
MainKt: [BRAINROT_EXPRESSION] Expression '60 + seven()' results in a brainrot expression 'six-seven'.
```

### Granular Feature Enabling

Unlike the old compiler, `sobaklinc` support enabling language features granularly:

```kotlin
// class name: MainKt

// -XXLanguage:+ProhibitLocalVariables
fun foo() {
    val a = 10
}

// -XXLanguage:-ProhibitLocalVariables
fun bar() {
    val b = 10
}
```
Prints:
```
MainKt: [PROHIBITED_LOCAL_VARIABLE] Local variable 'a' is prohibited in this context due to the -XXLanguage:+ProhibitLocalVariables directive.
```

### Inline Prompts

```kotlin
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
```
Prints:
```
[41, -20, 10]
```

### -XXLanguage:+OverloadResolutionByUseSiteContext

```kotlin
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
```
Prints:
```
AB
```

> Note: this one is the only source my `qwen3-coder:30b` failed to produce entirely
> correctly on its own... its result would print `BA` instead of `AB`. But a smarter
> model would likely do just fine, and maybe I can tweak the prompt somehow.

## Models

I've had successful results with `qwen2.5:7b`, `qwen2.5-coder:7b` and `qwen3-coder:30b`,
with the first two being somewhat consistently successful with basic "Hello World!" and
the latter successfully compiling all the other examples
(except for `OVERLOAD_RESOLUTION_BY_USE_SITE_CONTEXT`),
but maybe some other models could handle this task better.

Using Claude Code + Sonnet 4.6 as the underlying LLM works like a charm, obviously.

## Known Limitations

Currently, can only hallucinate JVM bytecode, sorry Wasm & Native folks.
But the approach is transferable!

Currently, the agents implicitly assume a single Kotlin file corresponds to a single
output `.class` file, which means nested/inner classes will likely not work
(or maybe the AI will hallucinate something to support them, who knows...).

For the same reason, calls like `numbers.map { it * it }` won't work either since
they require spawning additional classes.

## How It Works

So, there are several individual agents working together as a team to deliver
the best result possible.

1. One agent determines the output paths given the CLI arguments
   (to make sure the other ones aren't writing bullshit where they shouldn't be).
2. Another one is a `kotlinc` expert who explains what outputs people typically expect 
   to see given the provided arguments.
3. The third one determines if any error diagnostics should be reported.
   1. Determining if the Brainrot plugin is real is pretty hard, you know...
4. The fourth one performs the Kotlin source to bytecode conversion.
   1. It generates a class named `BytecodeGenerator` in Java which builds the
      resulting class file using the ASM library.
   2. The generator is then compiled using `ToolProvider.getSystemJavaCompiler()`
      (so, `sobaklinc` needs to be run with a JDK, not JRE), loaded & resolved
      right away, and verified with `CheckClassAdapter`.
   3. If something's obviously wrong, the agent is told to redo the work.
   4. Usually, this agent gives consistent working results for "Hello World!"s,
      but complex things will likely take it some trial and error.
5. And there's also the main one orchestrating all that.
   1. This one's a bit dense as it may struggle to put the files
      into right places, supply proper names and write the manifest,
      but it sometimes does the job!

## How to Build

To successfully compile `sobaklinc`, one needs a working
Kotlin runtime, which doesn't come bundled with this repo.

The main executable, `cli.jar` can be compiled without it,
but it expects the system property `sobaklin.kotlin.runtime` to be
set to the path to Kotlin runtime whenever someone passes `-include_runtime`.

You can compile a fresh JAR using the real `kotlinc`,
extract the `kotlin` folder from there and assign its path
to `sobaklin.kotlin.runtime` Gradle property in `local.properties`.
It will then become possible to run the tests and even assemble
the distribution via `./gradlew :cli:dist`!

