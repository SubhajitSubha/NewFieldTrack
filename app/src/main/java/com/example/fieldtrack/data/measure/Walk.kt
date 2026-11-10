package com.example.fieldtrack.data.measure

import com.example.fieldtrack.data.db.MeasureUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** One reading of where the phone is, and how far off it says it might be. */
data class Fix(val latitude: Double, val longitude: Double, val accuracyMetres: Float, val atMillis: Long)

/** A place on the walk. */
data class Point(val latitude: Double, val longitude: Double, val atMillis: Long)

/**
 * Which readings go on the walk.
 *
 * A phone's position wanders a few metres even standing still, and now and then
 * jumps. A reading that says it is poor, one that is barely a step from the last, and
 * one that would have needed a car are all left out; what remains is the path.
 */
object Walking {
    const val MAX_ACCURACY_METRES = 20f
    const val MIN_STEP_METRES = 4.0
    private const val MAX_SPEED_METRES_PER_SECOND = 25.0

    fun accepts(last: Point?, fix: Fix): Boolean {
        if (fix.accuracyMetres > MAX_ACCURACY_METRES) return false
        last ?: return true
        val step = metresBetween(last.latitude, last.longitude, fix.latitude, fix.longitude)
        if (step < MIN_STEP_METRES) return false
        val seconds = (fix.atMillis - last.atMillis) / 1000.0
        return seconds > 0 && step / seconds <= MAX_SPEED_METRES_PER_SECOND
    }
}

/**
 * The ground a walk went round.
 *
 * Laid flat around the walk's own first point — metres east and north of it — which
 * for a plot a few hundred metres across is exact to well under a hundredth of a
 * percent. The area is the plain polygon area of those points, closed back to the
 * first.
 */
class Shape(val points: List<Point>) {

    /** Metres east and north of the first point. */
    val flat: List<Pair<Double, Double>> = points.first().let { origin ->
        val scale = cos(Math.toRadians(origin.latitude))
        points.map {
            Math.toRadians(it.longitude - origin.longitude) * EARTH_RADIUS * scale to
                Math.toRadians(it.latitude - origin.latitude) * EARTH_RADIUS
        }
    }

    private val closed = flat + flat.first()

    val areaSquareMetres: Double = abs(
        closed.zipWithNext().sumOf { (a, b) -> a.first * b.second - b.first * a.second }
    ) / 2

    /** All the way round, the closing stretch back to the start included. */
    val perimeterMetres: Double = closed.zipWithNext().sumOf { (a, b) -> hypot(b.first - a.first, b.second - a.second) }

    /** How far the walk ended from where it began. */
    val closingGapMetres: Double = flat.last().let { hypot(it.first - flat.first().first, it.second - flat.first().second) }

    /** Whether the path crosses itself, which makes the area wrong however it is counted. */
    val crossesItself: Boolean by lazy {
        val last = closed.size - 2
        (0..last).any { i ->
            (i + 2..last).any { j ->
                !(i == 0 && j == last) && cross(closed[i], closed[i + 1], closed[j], closed[j + 1])
            }
        }
    }

    /** Enough to be a plot: a shape, not a line or a few steps. */
    val enough: Boolean get() = points.size >= 3 && areaSquareMetres >= MIN_AREA_SQUARE_METRES

    private fun cross(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>): Boolean {
        fun side(p: Pair<Double, Double>, q: Pair<Double, Double>, r: Pair<Double, Double>) =
            Math.signum((q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first))
        return side(a, b, c) * side(a, b, d) < 0 && side(c, d, a) * side(c, d, b) < 0
    }

    companion object {
        const val MIN_AREA_SQUARE_METRES = 20.0
    }
}

/**
 * Square metres in the units a plot is written in.
 *
 * An acre is an acre, a kanal is an eighth of one and a guntha a fortieth. A bigha
 * and a katha are not any one size — they differ from one district to the next —
 * so for those the farmer says how many make an acre where they live, and this
 * never supplies one.
 */
object Land {
    const val ACRE_SQUARE_METRES = 4046.8564224

    /** How many of [unit] make an acre, where that is the same everywhere; null where it is not. */
    fun fixedPerAcre(unit: MeasureUnit): Double? = when (unit) {
        MeasureUnit.Acre -> 1.0
        MeasureUnit.Kanal -> 8.0
        MeasureUnit.Guntha -> 40.0
        MeasureUnit.Hectare -> ACRE_SQUARE_METRES / 10_000.0
        MeasureUnit.SquareMetre -> ACRE_SQUARE_METRES
        // A hundredth of an acre, called a decimal in Bengal and a cent in the south.
        MeasureUnit.Decimal, MeasureUnit.Cent -> 100.0
        // Twenty to the kanal.
        MeasureUnit.Marla -> 160.0
        MeasureUnit.Are -> ACRE_SQUARE_METRES / 100.0
        MeasureUnit.Dunam -> ACRE_SQUARE_METRES / 1_000.0
        MeasureUnit.Feddan -> ACRE_SQUARE_METRES / 4_200.0
        MeasureUnit.Rai -> ACRE_SQUARE_METRES / 1_600.0
        MeasureUnit.Mu -> ACRE_SQUARE_METRES / (10_000.0 / 15.0)
        // Manzana, tarea, alqueire, bigha and the business's own unit differ by place: asked.
        else -> null
    }

    /** A number of a unit to an acre that could be a real one, rather than a slip of the finger. */
    fun plausiblePerAcre(value: Double) = value in 0.05..1000.0

    fun inUnit(squareMetres: Double, perAcre: Double) = squareMetres / ACRE_SQUARE_METRES * perAcre
}

private const val EARTH_RADIUS = 6_371_008.8

/** Metres between two places, on the ground. */
fun metresBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
    val y = Math.toRadians(lat2 - lat1)
    return hypot(x, y) * EARTH_RADIUS
}
