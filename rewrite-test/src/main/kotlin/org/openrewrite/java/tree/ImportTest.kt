/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

interface ImportTest {

    @Test
    fun matchImport(jp: JavaParser) {
        val a = jp.parse("""
            import java.util.List;
            public class A {}
        """)[0]

        assertTrue(a.imports.first().isFromType("java.util.List"))
    }

    @Test
    fun matchStarImport(jp: JavaParser) {
        val a = jp.parse("""
            import java.util.*;
            public class A {}
        """)[0]

        assertTrue(a.imports.first().isFromType("java.util.List"))
    }

    @Test
    fun hasStarImportOnInnerClass(jp: JavaParser) {
        val a = """
            package a;
            public class A {
               public static class B { }
            }
        """

        val c = """
            import a.*;
            public class C {
                A.B b = new A.B();
            }
        """

        val cu = jp.parse(c, a)[0]
        val import = cu.imports.first()
        assertTrue(import.isFromType("a.A.B"))
        assertTrue(import.isFromType("a.A"))
    }
    
    @Test
    fun format(jp: JavaParser) {
        val a = jp.parse("""
            import java.util.List;
            import static java.util.Collections.*;
            public class A {}
        """)[0]
        
        assertEquals("import java.util.List", a.imports[0].printTrimmed())
        assertEquals("import static java.util.Collections.*", a.imports[1].printTrimmed())
    }

    @Test
    fun compare(jp: JavaParser) {
        val a = jp.parse("""
            import b.B;
            import c.c.C;
        """.trimIndent())[0]

        val (b, c) = a.imports

        assertTrue(b < c)
        assertTrue(c > b)
    }

    @Test
    fun compareSamePackageDifferentNameLengths(jp: JavaParser) {
        val a = jp.parse("""
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
        """.trimIndent())[0]

        val (b, c) = a.imports

        assertTrue(b < c)
        assertTrue(c > b)
    }
}
