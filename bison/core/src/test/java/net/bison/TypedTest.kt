package net.bison

import net.bison.domain.Typed
import org.junit.Assert.assertTrue
import org.junit.Test

/** The comparison the set asks for: whitespace normalised, capitals kept, otherwise exact */
class TypedTest {
    @Test
    fun `the layout of an answer does not decide it`() {
        assertTrue(Typed.same("d = [3;6;2];", "d  =  [3;6;2];"))
        assertTrue(Typed.same("  d = [3;6;2];  ", "d = [3;6;2];"))
        // typed across two lines, which is what a long answer on a phone looks like
        assertTrue(Typed.same("X = [zeros(3,8);\n diag([5 5 5 5 5]), zeros(5,3)];", "X = [zeros(3,8); diag([5 5 5 5 5]), zeros(5,3)];"))
    }

    @Test
    fun `capitals are part of the answer`() {
        // zeros is a function and Zeros is an undefined name
        assertTrue(!Typed.same("Zeros(8,1)", "zeros(8,1)"))
        assertTrue(!Typed.same("A(2,3)", "a(2,3)"))
    }

    @Test
    fun `everything else has to match exactly`() {
        // the spacing inside is layout, the semicolon is not
        assertTrue(!Typed.same("d = [3;6;2]", "d = [3;6;2];"))
        assertTrue(!Typed.same("d=[3;6;2];", "d = [3;6;2];"))
    }

    @Test
    fun `any of the answers the card accepts will do`() {
        val accepted = listOf("R(12,4) = 7;", "R(12, 4) = 7;")

        assertTrue(Typed.matches("R(12,4)   = 7;", accepted))
        assertTrue(Typed.matches("R(12, 4) = 7;", accepted))
        assertTrue(!Typed.matches("R(4,12) = 7;", accepted))
    }
}
