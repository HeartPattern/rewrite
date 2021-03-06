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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.firstMethodStatement

interface ReturnTest {
    
    @Test
    fun returnValue(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                public String test() {
                    return "";
                }
            }
        """)[0]
        
        val rtn = a.firstMethodStatement() as J.Return
        assertTrue(rtn.expr is J.Literal)
    }

    @Test
    fun returnVoid(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                public void test() {
                    return;
                }
            }
        """)[0]

        val rtn = a.firstMethodStatement() as J.Return
        assertNull(rtn.expr)
    }
    
    @Test
    fun format(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                public int test() {
                    return 0;
                }
            }
        """)[0]

        val rtn = a.firstMethodStatement() as J.Return
        assertEquals("return 0", rtn.printTrimmed())
    }
}
