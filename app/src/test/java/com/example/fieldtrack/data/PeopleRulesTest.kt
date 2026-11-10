package com.example.fieldtrack.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the server enforces about people and money, read off the rules file the console is
 * given. A screen that hides a figure keeps it from nobody; only these rules can.
 */
class PeopleRulesTest {

    private val rules = java.io.File("../firestore.rules").readText()

    private fun block(name: String) = rules.substringAfter("match /$name/").substringBefore("\n      }")

    @Test fun `a person may read a payment made to them, and nobody else's`() {
        val payouts = block("payouts")
        assertTrue(payouts.contains("resource.data.toPersonId == request.auth.uid"))
        assertTrue(payouts.contains("may(orgId, 'canSeePeopleCost')"))
    }

    @Test fun `a hand-over is written by somebody who receives money, in their own name`() {
        val handovers = block("cash_handovers")
        assertTrue(handovers.contains("request.resource.data.byPersonId == request.auth.uid"))
        assertTrue(handovers.contains("allow delete: if false"))
    }

    @Test fun `particulars are the person's own and the team's managers', changed in the changer's name`() {
        val details = block("member_details")
        assertTrue(details.contains("memberId == orgId + '_' + request.auth.uid"))
        assertTrue(details.contains("request.resource.data.updatedByPersonId == request.auth.uid"))
    }

    @Test fun `pay terms are read by the money people and the person, and set by whoever pays`() {
        val terms = block("member_pay_terms")
        assertTrue(terms.contains("may(orgId, 'canSeePeopleCost')"))
        assertTrue(terms.contains("allow create, update: if isOwner(orgId) || may(orgId, 'canPayOut')"))
    }

    @Test fun `the blanket grant does not reach any of them`() {
        val read = rules.substringAfter("allow read: if isMember(orgId)\n                    && collection != 'memberships'").substringBefore(";")
        val write = rules.substringAfter("allow write: if isMember(orgId)").substringBefore(";")
        for (c in listOf("cash_handovers", "member_details", "member_pay_terms")) {
            assertTrue("read: $c", read.contains("collection != '$c'"))
            assertTrue("write: $c", write.contains("collection != '$c'"))
        }
    }
}
