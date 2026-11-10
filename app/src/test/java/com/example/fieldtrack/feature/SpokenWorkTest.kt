package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.feature.work.SpokenDay
import com.example.fieldtrack.feature.work.SpokenWorkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Rotavator, Ramesh, 3 acre": what is picked up, and what is left alone. */
class SpokenWorkTest {

    private fun customer(id: String, name: String) = CustomerEntity(id = id, orgId = "o", name = name, createdAt = 1, updatedAt = 1)
    private val ramesh = customer("1", "Ramesh Singh")
    private val rameshK = customer("2", "Ramesh Kumar")
    private val harjit = customer("3", "Harjit Kaur")

    private val work = workTypeOptions.associateBy { it.key.lowercase() } + mapOf("plough" to workTypeOptions.first { it.key == "Ploughing" })
    private val units = mapOf("acre" to MeasureUnit.Acre, "acres" to MeasureUnit.Acre, "bigha" to MeasureUnit.Bigha, "hour" to MeasureUnit.Hour, "hours" to MeasureUnit.Hour)

    private fun parse(text: String, vararg all: CustomerEntity = arrayOf(ramesh, rameshK, harjit)) =
        SpokenWorkParser.parse(text, all.toList(), work, units)

    @Test fun `the sentence in the plan is understood whole`() {
        val said = parse("rotavator Harjit 3 acre")
        assertEquals("Rotavator", said.workType?.key)
        assertEquals(harjit, said.customer)
        assertEquals(3.0, said.quantity!!, 0.0)
        assertEquals(MeasureUnit.Acre, said.unit)
    }

    @Test fun `numbers said as words, and with a decimal point, are numbers`() {
        assertEquals(2.0, parse("harjit two bigha").quantity!!, 0.0)
        assertEquals(3.5, parse("harjit 3.5 acre").quantity!!, 0.0)
        assertEquals(3.5, parse("harjit 3,5 acre").quantity!!, 0.0)
        assertEquals(0.5, parse("harjit half hour").quantity!!, 0.0)
    }

    @Test fun `two farmers with the same first name are not guessed between, and the surname decides`() {
        assertNull(parse("ramesh 3 acre").customer)
        assertEquals(ramesh, parse("ramesh singh 3 acre").customer)
        assertEquals(rameshK, parse("kumar 3 acre").customer)
    }

    @Test fun `what is not said is left blank, never invented`() {
        val said = parse("hello there")
        assertFalse(said.understoodAnything)
        assertNull(parse("harjit").quantity)
        assertNull(parse("harjit").unit)
        assertNull(parse("3 acre").customer)
    }

    @Test fun `yesterday and today are heard`() {
        assertEquals(SpokenDay.Yesterday, parse("harjit 3 acre yesterday").day)
        assertEquals(SpokenDay.Today, parse("today harjit 3 acre").day)
        assertNull(parse("harjit 3 acre").day)
    }

    @Test fun `a word of the work or the unit is never taken for a farmer's name`() {
        val acre = customer("9", "Acre Farms")
        val said = SpokenWorkParser.parse("rotavator 3 acre", listOf(acre), work, units)
        assertNull(said.customer)
        assertTrue(said.unit == MeasureUnit.Acre)
    }
}
