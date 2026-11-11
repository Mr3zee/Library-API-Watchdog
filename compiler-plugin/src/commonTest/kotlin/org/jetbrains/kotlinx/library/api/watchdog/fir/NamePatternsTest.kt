package org.jetbrains.kotlinx.library.api.watchdog.fir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamePatternsTest {
    @Test
    fun `a name without a wildcard matches the whole name only`() {
        val patterns = NamePatterns(setOf("com.example.Foo"))

        assertTrue(patterns.matches("com.example.Foo"))
        for (name in listOf("com.example.Foo.Nested", "com.example.FooBar", "com.example", "xcom.example.Foo", "com-example.Foo")) {
            assertFalse(patterns.matches(name), name)
        }
    }

    @Test
    fun `two stars match any characters`() {
        val patterns = NamePatterns(setOf("com.example.**"))

        for (name in listOf("com.example.Foo", "com.example.deep.Foo.Nested", "com.example.")) {
            assertTrue(patterns.matches(name), name)
        }
        for (name in listOf("com.example", "com.examples.Foo", "org.com.example.Foo")) {
            assertFalse(patterns.matches(name), name)
        }
        assertTrue(NamePatterns(setOf("**")).matches("com.example.Foo"))
        assertTrue(NamePatterns(setOf("**.Foo")).matches("com.example.Foo"))
        assertFalse(NamePatterns(setOf("**.Foo")).matches("Foo"))
        assertTrue(NamePatterns(setOf("**Foo")).matches("Foo"))
    }

    @Test
    fun `one star matches any characters except a dot`() {
        val patterns = NamePatterns(setOf("com.example.*Impl"))

        for (name in listOf("com.example.Impl", "com.example.DefaultImpl")) {
            assertTrue(patterns.matches(name), name)
        }
        for (name in listOf("com.example.Outer.NestedImpl", "com.example.ImplX", "com.exampleImpl")) {
            assertFalse(patterns.matches(name), name)
        }
        assertTrue(NamePatterns(setOf("com.*.Foo")).matches("com.example.Foo"))
        assertFalse(NamePatterns(setOf("com.*.Foo")).matches("com.example.deep.Foo"))
    }

    @Test
    fun `three stars match as two stars and one star`() {
        val patterns = NamePatterns(setOf("com.***"))

        assertTrue(patterns.matches("com.example.Foo"))
        assertFalse(patterns.matches("com"))
    }

    @Test
    fun `a question mark matches one character`() {
        val patterns = NamePatterns(setOf("com.example.Shape?"))

        assertTrue(patterns.matches("com.example.Shape1"))
        assertTrue(patterns.matches("com.example.Shape."))
        for (name in listOf("com.example.Shape", "com.example.Shape12")) {
            assertFalse(patterns.matches(name), name)
        }
    }

    @Test
    fun `other characters are literal`() {
        for (name in listOf("com.example.Foo\$Bar", "com.example.(Foo)", "com.example.[a-z]+", "com.example.a|b", "com.example.\\E", "com.example.^x$")) {
            val wildcard = NamePatterns(setOf("$name*"))
            assertTrue(wildcard.matches(name), name)
            assertTrue(wildcard.matches(name + "Suffix"), name)
            assertFalse(wildcard.matches("com.example.b"), name)
        }
        assertFalse(NamePatterns(setOf("com.example.Foo*")).matches("comXexampleXFoo"))
    }

    @Test
    fun `the first match is a plain name, then a pattern in the given order`() {
        val patterns = NamePatterns(linkedSetOf("com.**", "com.example.*", "com.example.Foo"))

        assertEquals("com.example.Foo", patterns.firstMatch("com.example.Foo"))
        assertEquals("com.**", patterns.firstMatch("com.example.Bar"))
        assertNull(patterns.firstMatch("org.example.Foo"))
    }

    @Test
    fun `an empty set matches no name`() {
        val patterns = NamePatterns(emptySet())

        assertTrue(patterns.isEmpty)
        assertFalse(patterns.matches(""))
        assertFalse(NamePatterns(setOf("com.Foo")).isEmpty)
        assertFalse(NamePatterns(setOf("com.*")).isEmpty)
    }
}
