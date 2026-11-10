package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.measure.Fix
import com.example.fieldtrack.data.measure.Land
import com.example.fieldtrack.data.measure.Point
import com.example.fieldtrack.data.measure.Shape
import com.example.fieldtrack.data.measure.Walking
import com.example.fieldtrack.data.measure.metresBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Measuring a plot from a walk round it.
 *
 * Plots are laid out in metres from a latitude and longitude so that the answers can
 * be checked against a shape whose area is known without a phone: a 100 m by 40 m
 * rectangle is 4,000 square metres wherever on earth it is walked.
 */
class WalkTest {

    private val lat0 = 30.9
    private val lon0 = 75.85
    private val metreLat = 1.0 / 111_194.9266          // degrees of latitude in a metre
    private val metreLon = metreLat / cos(Math.toRadians(lat0))

    /** A point [east] and [north] metres from the start. */
    private fun at(east: Double, north: Double, t: Long = 0) =
        Point(lat0 + north * metreLat, lon0 + east * metreLon, t)

    private fun walk(vararg metres: Pair<Int, Int>) =
        Shape(metres.mapIndexed { i, (e, n) -> at(e.toDouble(), n.toDouble(), i * 5_000L) })

    private val rectangle = walk(0 to 0, 100 to 0, 100 to 40, 0 to 40)

    // ------------------------------------------------------------------- the shape

    @Test
    fun `a rectangle is its length times its breadth`() {
        assertEquals(4_000.0, rectangle.areaSquareMetres, 4_000 * 0.001)
        assertEquals(280.0, rectangle.perimeterMetres, 0.5)
    }

    @Test
    fun `it does not matter which way round, or where, it was walked`() {
        val backwards = walk(0 to 40, 100 to 40, 100 to 0, 0 to 0)
        assertEquals(rectangle.areaSquareMetres, backwards.areaSquareMetres, 0.5)
        // The same 100 x 40 m at another latitude, where a degree of longitude is a different width.
        val farLat = -20.0
        val farLon = metreLat / cos(Math.toRadians(farLat))
        val far = Shape(listOf(0 to 0, 100 to 0, 100 to 40, 0 to 40).map { (e, n) ->
            Point(farLat + n * metreLat, 3.0 + e * farLon, 0)
        })
        assertEquals("another place on earth, the same plot", 4_000.0, far.areaSquareMetres, 4_000 * 0.002)
    }

    @Test
    fun `a triangle is half its box`() {
        assertEquals(2_000.0, walk(0 to 0, 100 to 0, 0 to 40).areaSquareMetres, 2.0)
    }

    @Test
    fun `an L-shaped plot`() {
        // 100 x 100 with a 50 x 50 corner cut out: 7,500.
        assertEquals(7_500.0, walk(0 to 0, 100 to 0, 100 to 50, 50 to 50, 50 to 100, 0 to 100).areaSquareMetres, 8.0)
    }

    @Test
    fun `an acre is an acre`() {
        // 63.6 m a side is an acre to a fraction of a percent.
        val side = Math.sqrt(Land.ACRE_SQUARE_METRES)
        val plot = Shape(listOf(at(0.0, 0.0), at(side, 0.0), at(side, side), at(0.0, side)))
        assertEquals(1.0, Land.inUnit(plot.areaSquareMetres, 1.0), 0.002)
        assertEquals(8.0, Land.inUnit(plot.areaSquareMetres, 8.0), 0.02)
    }

    @Test
    fun `how far from the start it ended`() {
        assertEquals(40.0, rectangle.closingGapMetres, 0.2)
        val closed = Shape(rectangle.points + at(0.0, 1.0))
        assertEquals(1.0, closed.closingGapMetres, 0.1)
    }

    @Test
    fun `a path that crosses itself is caught`() {
        assertFalse(rectangle.crossesItself)
        assertTrue("a figure of eight", walk(0 to 0, 100 to 40, 100 to 0, 0 to 40).crossesItself)
        assertFalse("an L", walk(0 to 0, 100 to 0, 100 to 50, 50 to 50, 50 to 100, 0 to 100).crossesItself)
        assertTrue("a loop whose tail goes back over the start", walk(0 to 0, 100 to 0, 100 to 100, 0 to 100, 0 to -20, 50 to 50).crossesItself)
    }

    @Test
    fun `a line or a few steps is not a plot`() {
        assertFalse(walk(0 to 0, 50 to 0).enough)
        assertFalse(walk(0 to 0, 50 to 0, 100 to 0).enough)
        assertFalse("three steps round a tree", walk(0 to 0, 3 to 0, 3 to 3).enough)
        assertTrue(rectangle.enough)
    }

    @Test
    fun `a single point does not crash it`() {
        val one = Shape(listOf(at(0.0, 0.0)))
        assertEquals(0.0, one.areaSquareMetres, 0.0)
        assertFalse(one.enough)
        assertFalse(one.crossesItself)
    }

    // -------------------------------------------------------------- what is kept

    private fun fix(east: Double, north: Double, accuracy: Float = 5f, t: Long = 0) =
        Fix(lat0 + north * metreLat, lon0 + east * metreLon, accuracy, t)

    @Test
    fun `the first good reading starts the walk`() {
        assertTrue(Walking.accepts(null, fix(0.0, 0.0)))
    }

    @Test
    fun `a poor reading is left out`() {
        assertFalse(Walking.accepts(null, fix(0.0, 0.0, accuracy = 25f)))
        assertTrue(Walking.accepts(null, fix(0.0, 0.0, accuracy = 20f)))
    }

    @Test
    fun `standing still adds nothing, a few steps do`() {
        val last = at(0.0, 0.0, 0)
        assertFalse("jitter", Walking.accepts(last, fix(2.0, 1.0, t = 1_000)))
        assertTrue("a few paces", Walking.accepts(last, fix(3.0, 3.0, t = 3_000)))
    }

    @Test
    fun `a jump no walker or tractor could make is left out`() {
        val last = at(0.0, 0.0, 0)
        assertFalse("200 m in two seconds", Walking.accepts(last, fix(200.0, 0.0, t = 2_000)))
        assertTrue("the same distance, given the time it takes", Walking.accepts(last, fix(200.0, 0.0, t = 20_000)))
        assertFalse("not later than the last", Walking.accepts(last, fix(10.0, 0.0, t = 0)))
    }

    // ---------------------------------------------------------------- the units

    @Test
    fun `an acre, a kanal and a guntha are the same everywhere`() {
        assertEquals(1.0, Land.fixedPerAcre(MeasureUnit.Acre)!!, 0.0)
        assertEquals(8.0, Land.fixedPerAcre(MeasureUnit.Kanal)!!, 0.0)
        assertEquals(40.0, Land.fixedPerAcre(MeasureUnit.Guntha)!!, 0.0)
    }

    @Test
    fun `a bigha and a katha are not, so nothing is supplied for them`() {
        assertNull(Land.fixedPerAcre(MeasureUnit.Bigha))
        assertNull(Land.fixedPerAcre(MeasureUnit.Katha))
    }

    @Test
    fun `a number of bigha to an acre that could be real`() {
        assertTrue(Land.plausiblePerAcre(1.6))
        assertTrue(Land.plausiblePerAcre(5.0))
        assertFalse(Land.plausiblePerAcre(0.0))
        assertFalse(Land.plausiblePerAcre(-2.0))
        assertFalse(Land.plausiblePerAcre(5_000.0))
    }

    @Test
    fun `a business's own bigha turns square metres into bigha`() {
        // Where 1.6 bigha make an acre, an acre's worth of ground is 1.6 bigha.
        assertEquals(1.6, Land.inUnit(Land.ACRE_SQUARE_METRES, 1.6), 1e-9)
        assertEquals(0.8, Land.inUnit(Land.ACRE_SQUARE_METRES / 2, 1.6), 1e-9)
    }

    @Test
    fun `distance on the ground`() {
        assertEquals(100.0, metresBetween(lat0, lon0, lat0 + 100 * metreLat, lon0), 0.1)
        assertEquals(100.0, metresBetween(lat0, lon0, lat0, lon0 + 100 * metreLon), 0.2)
        assertEquals(0.0, metresBetween(lat0, lon0, lat0, lon0), 0.0)
    }
}
