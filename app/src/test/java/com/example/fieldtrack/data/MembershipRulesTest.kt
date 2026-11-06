package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.entity.MembershipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The server's rules name every switch, because they cannot say "all of them".
 *
 * A switch added to [MembershipEntity] and left out of `firestore.rules` is one a
 * driver can give themselves through the API: the app never would, so nothing else
 * notices. This fails the build instead.
 */
class MembershipRulesTest {

    private val rules: String = listOf(File("../firestore.rules"), File("firestore.rules"))
        .first { it.exists() }.readText()

    private val switches: List<String> = MembershipEntity::class.java.declaredFields
        .filter { it.name.startsWith("can") && it.type == Boolean::class.javaPrimitiveType }
        .map { it.name }

    /** The text of one rule function, from its name to the closing brace at its indent. */
    private fun body(name: String): String {
        val start = rules.indexOf("function $name(")
        assertTrue("$name is in the rules", start >= 0)
        val end = rules.indexOf("\n        }\n", start)
        return rules.substring(start, end)
    }

    @Test
    fun `there are switches to guard`() {
        assertTrue("found $switches", switches.size >= 8)
    }

    @Test
    fun `a person cannot change any switch on their own row`() {
        val guard = body("keepsOwnRank")
        assertEquals(
            "switches keepsOwnRank does not name",
            emptyList<String>(), switches.filter { "data.$it ==" !in guard && "get('$it'" !in guard },
        )
    }

    @Test
    fun `a person cannot arrive on a code holding switches it does not carry`() {
        val terms = body("joinsOnInvitedTerms") + body("joinsWithoutOtherSwitches")
        assertEquals(
            "switches a joiner could choose for themselves",
            emptyList<String>(), switches.filter { it !in terms },
        )
    }
}
