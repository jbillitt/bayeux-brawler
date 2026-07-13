package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameFlavourTest {

    /** The bug: the given name was re-parsed out of the display string and lost. */
    @Test
    fun `renaming for hair keeps the man's given name and title`() {
        val state = BattleSimState(honorific = "Syr", givenName = "Roger", byname = "the Grey")
        assertEquals("Syr Roger the Grey", state.playerName)

        // A shave changes the byname only — his name and title are his to keep.
        val shaved = state.copy(byname = "the Shorn")
        assertEquals("Syr Roger the Shorn", shaved.playerName)
        assertTrue("the given name was lost", shaved.playerName.contains("Roger"))
    }

    /** A blank honorific must not leave a leading space. */
    @Test
    fun `a man with no title is still named`() {
        val plain = BattleSimState(honorific = "", givenName = "Wadard", byname = "the Bald")
        assertEquals("Wadard the Bald", plain.playerName)
        assertFalse("leading space from a blank title", plain.playerName.startsWith(" "))
    }

    @Test
    fun `battle names vary in pattern, not just vocabulary`() {
        val names = (1..40).map { FlavourText.battleName(1066L, it) }
        names.forEach { println(it) }

        assertTrue("battle names repeat", names.toSet().size >= 35)
        // The old generator could only ever produce "The X Y at Z".
        val oldShape = names.count { it.startsWith("The ") && it.contains(" at ") }
        assertTrue("every name still follows the one old template", oldShape < names.size)
    }

    @Test
    fun `battle names are not written by an illiterate monk`() {
        (1..400).map { FlavourText.battleName(1066L, it) }.forEach { name ->
            assertTrue("sentence starts lowercase: '$name'", name.first().isUpperCase())
            assertFalse("'a' before a vowel: '$name'", Regex("\\bA [AEIOU]").containsMatchIn(name))
            assertFalse("'an' before a consonant: '$name'", Regex("\\bAn [BCDFGHJKLMNPQRSTVWXYZ]").containsMatchIn(name))
        }
    }
}
