package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.DataInputStream
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The plugin jar runs in the CLI, in the IDE, and in `kotlin-compiler-embeddable`, which relocates
 * `com.intellij` to `org.jetbrains.kotlin.com.intellij`. A class constant, a field or method
 * reference, or a declared descriptor with either name links in one of them only. The JVM resolves
 * them on the first execution, so a test that doesn't reach the code doesn't find the failure.
 * A generic signature is safe, because the JVM doesn't resolve it.
 */
class IntellijReferencesTest {
    @Test
    fun `plugin classes have no linkable intellij reference`() {
        val references = pluginClasses().flatMap { (className, bytes) ->
            ClassConstants(bytes).intellijReferences().map { "$className: $it" }
        }
        assertTrue(
            references.isEmpty(),
            "${references.size} references to com.intellij link in the CLI or in " +
                "kotlin-compiler-embeddable only:\n" + references.joinToString("\n"),
        )
    }

    /** The class files of the plugin package, as the compiled output that the test classpath loads. */
    private fun pluginClasses(): List<Pair<String, ByteArray>> {
        val packagePath = WatchdogDiagnostics::class.java.packageName
            .substringBeforeLast(".fir")
            .replace('.', '/')
        val location = File(WatchdogDiagnostics::class.java.protectionDomain.codeSource.location.toURI())
        if (location.isDirectory) {
            return location.resolve(packagePath).walk()
                .filter { it.isFile && it.extension == "class" }
                .map { it.relativeTo(location).invariantSeparatorsPath.removeSuffix(".class") to it.readBytes() }
                .toList()
        }
        return JarFile(location).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.startsWith("$packagePath/") && it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class") to jar.getInputStream(it).readBytes() }
                .toList()
        }
    }
}

/** The constant pool entries and the declared descriptors of one class file (JVMS 4.1, 4.4). */
private class ClassConstants(bytes: ByteArray) {
    private val utf8 = HashMap<Int, String>()
    private val classNames = ArrayList<Int>()
    private val memberDescriptors = ArrayList<Int>()
    private val methodTypes = ArrayList<Int>()
    private val declaredDescriptors = ArrayList<Int>()

    init {
        val input = DataInputStream(bytes.inputStream())
        check(input.readInt() == 0xCAFEBABE.toInt()) { "Not a class file" }
        input.skipNBytes(4) // minor_version, major_version
        val constantCount = input.readUnsignedShort()
        var index = 1
        while (index < constantCount) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8[index] = input.readUTF()
                3, 4 -> input.skipNBytes(4) // Integer, Float
                5, 6 -> { // Long, Double take two slots
                    input.skipNBytes(8)
                    index++
                }
                7 -> classNames += input.readUnsignedShort()
                8, 19, 20 -> input.skipNBytes(2) // String, Module, Package
                9, 10, 11, 17, 18 -> input.skipNBytes(4) // Fieldref, Methodref, InterfaceMethodref, Dynamic, InvokeDynamic
                12 -> { // NameAndType
                    input.skipNBytes(2)
                    memberDescriptors += input.readUnsignedShort()
                }
                15 -> input.skipNBytes(3) // MethodHandle
                16 -> methodTypes += input.readUnsignedShort()
                else -> error("Unknown constant pool tag $tag")
            }
            index++
        }
        input.skipNBytes(6) // access_flags, this_class, super_class
        input.skipNBytes(2L * input.readUnsignedShort()) // interfaces
        repeat(2) { // fields, then methods
            repeat(input.readUnsignedShort()) {
                input.skipNBytes(4) // access_flags, name_index
                declaredDescriptors += input.readUnsignedShort()
                repeat(input.readUnsignedShort()) {
                    input.skipNBytes(2) // attribute_name_index
                    input.skipNBytes(input.readInt().toLong() and 0xFFFFFFFFL)
                }
            }
        }
    }

    /** Each reference as `<kind> <name or descriptor>`. Both spellings of the package match. */
    fun intellijReferences(): List<String> = listOf(
        "class" to classNames,
        "member descriptor" to memberDescriptors,
        "method type" to methodTypes,
        "declared descriptor" to declaredDescriptors,
    ).flatMap { (kind, indices) ->
        indices.mapNotNull { utf8[it] }.filter { "com/intellij/" in it }.distinct().map { "$kind $it" }
    }
}
