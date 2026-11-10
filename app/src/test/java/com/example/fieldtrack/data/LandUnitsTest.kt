package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.measure.Land
import com.example.fieldtrack.data.region.OwnLandUnit
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.feature.unitName
import com.example.fieldtrack.feature.unitsFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Land as the world measures it: every unit offered, the fixed ones the right size, and a business's
 * own unit named by it and called that everywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LandUnitsTest {

    private lateinit var h: TestHarness
    private lateinit var store: SessionStore
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().clear().commit()
        h = TestHarness()
        store = SessionStore(context, h.db, h.clock, h.ids)
        OwnLandUnit.name = null
    }

    @After fun tearDown() {
        h.close()
        OwnLandUnit.name = null
        com.example.fieldtrack.designsystem.format.Money.style = com.example.fieldtrack.designsystem.format.MoneyStyle()
    }

    @Test
    fun `the query that sums land knows every land unit`() {
        val inSql = Regex("'(\\w+)'").findAll(MeasureUnit.LAND_SQL).map { it.groupValues[1] }.toSet()
        assertEquals(MeasureUnit.LAND.map { it.name }.toSet(), inSql)
        assertEquals(MeasureUnit.entries.filter { it.isLand }.toSet(), MeasureUnit.LAND.toSet())
    }

    @Test
    fun `units with one size everywhere convert, and the ones that differ by place are asked`() {
        fun acres(unit: MeasureUnit, n: Double) = n / Land.fixedPerAcre(unit)!!
        assertEquals("a hundred decimals to the acre", 1.0, acres(MeasureUnit.Decimal, 100.0), 1e-9)
        assertEquals("twenty marla to the kanal", acres(MeasureUnit.Kanal, 1.0), acres(MeasureUnit.Marla, 20.0), 1e-9)
        assertEquals("a dunam is a thousand square metres", 1_000.0, acres(MeasureUnit.Dunam, 1.0) * Land.ACRE_SQUARE_METRES, 1e-6)
        assertEquals("a feddan is 4,200", 4_200.0, acres(MeasureUnit.Feddan, 1.0) * Land.ACRE_SQUARE_METRES, 1e-6)
        assertEquals("a rai is 1,600", 1_600.0, acres(MeasureUnit.Rai, 1.0) * Land.ACRE_SQUARE_METRES, 1e-6)
        for (asked in listOf(MeasureUnit.Bigha, MeasureUnit.Manzana, MeasureUnit.Alqueire, MeasureUnit.OwnLand)) {
            assertNull("$asked differs by place", Land.fixedPerAcre(asked))
        }
    }

    @Test
    fun `a country's everyday unit is the first guess`() {
        assertEquals(MeasureUnit.Feddan, Region.landUnitOf("EG"))
        assertEquals(MeasureUnit.Dunam, Region.landUnitOf("JO"))
        assertEquals(MeasureUnit.Rai, Region.landUnitOf("TH"))
        assertEquals(MeasureUnit.Acre, Region.landUnitOf("BD"))
        assertEquals(MeasureUnit.Hectare, Region.landUnitOf("KE"))
    }

    @Test
    fun `the business's own unit is offered only once it has one`() {
        assertFalse(MeasureUnit.OwnLand in unitsFor(BillingMode.PerAcre))
        OwnLandUnit.name = "Kattha"
        assertTrue(MeasureUnit.OwnLand in unitsFor(BillingMode.PerAcre))
        assertEquals("Kattha", unitName(context, MeasureUnit.OwnLand))
        assertEquals("ours stay ours", context.getString(com.example.fieldtrack.R.string.unit_acre), unitName(context, MeasureUnit.Acre))
    }

    @Test
    fun `a business names its own unit, keeps the name, and every screen says it`() = runTest {
        store.completeOnboarding("Somchai", "Somchai Farm", MeasureUnit.OwnLand, true, "THB", 1, ownLandUnit = "  Ngan  ")
        store.activate()
        assertEquals("Ngan", OwnLandUnit.name)
        assertEquals("Ngan", h.db.orgDao().observeOrg(store.current()!!.orgId).first()!!.ownLandUnit)

        store.setLandUnit(MeasureUnit.Rai)
        assertEquals("choosing another unit keeps the name for later", "Ngan", OwnLandUnit.name)
        store.setLandUnit(MeasureUnit.OwnLand, "Wa")
        assertEquals("Wa", store.current()!!.ownLandUnit)
        assertEquals("Wa", unitName(context, MeasureUnit.OwnLand))
    }
}
