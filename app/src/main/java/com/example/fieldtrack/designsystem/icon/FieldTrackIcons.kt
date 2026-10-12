package com.example.fieldtrack.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The FieldTrack icon set.
 *
 * Drawn here rather than pulled from a library for two reasons. The machines have no
 * equivalent anywhere — no icon font ships a rotavator, a straw baler or a laser
 * leveller — and owning the whole set means one stroke weight and one corner treatment
 * across the app instead of a visible seam where a borrowed icon sits next to ours.
 *
 * All are stroke icons on a square viewport, 2px at nominal size. Tint them through
 * `Icon(tint = ...)`; they carry no colour of their own.
 *
 * Machine icons are paired with a photo of the real machine wherever layout allows —
 * a glyph tells a non-reading driver the category, the photo tells them *which one*.
 */
object FieldTrackIcons {

    // ---------------------------------------------------------------- machines

    val Tractor: ImageVector by machine("Tractor") {
        circle(22f, 21f, 8f)
        circle(7f, 25f, 4f)
        moveTo(3f, 21f); verticalLineTo(15f); horizontalLineTo(9f)
        verticalLineTo(8f); horizontalLineTo(16f); verticalLineTo(15f)
        moveTo(16f, 15f); horizontalLineTo(23f)
    }

    val Harvester: ImageVector by machine("Harvester") {
        moveTo(4f, 22f); verticalLineTo(12f); horizontalLineTo(17f); verticalLineTo(22f); close()
        circle(25f, 17f, 5f)
        moveTo(25f, 12f); verticalLineTo(22f)
        moveTo(20f, 17f); horizontalLineTo(30f)
        circle(8f, 26f, 3f)
        circle(16f, 26f, 2f)
    }

    val Rotavator: ImageVector by machine("Rotavator") {
        moveTo(3f, 11f); horizontalLineTo(29f)
        moveTo(16f, 11f); verticalLineTo(5f)
        moveTo(8f, 11f); verticalLineTo(17f); arcToRelative(2.5f, 2.5f, 0f, false, false, 5f, 0f)
        moveTo(19f, 11f); verticalLineTo(17f); arcToRelative(2.5f, 2.5f, 0f, false, false, 5f, 0f)
    }

    val Cultivator: ImageVector by machine("Cultivator") {
        moveTo(4f, 12f); horizontalLineTo(16f); lineTo(22f, 20f); horizontalLineTo(10f); close()
        moveTo(4f, 12f); verticalLineTo(7f)
        moveTo(16f, 12f); lineTo(21f, 6f)
        circle(12f, 25f, 3f)
        circle(23f, 25f, 3f)
    }

    val Trolley: ImageVector by machine("Trolley") {
        moveTo(6f, 8f); horizontalLineTo(27f); verticalLineTo(19f); horizontalLineTo(6f); close()
        moveTo(6f, 13f); horizontalLineTo(27f)
        moveTo(6f, 12f); horizontalLineTo(2f)
        circle(12f, 25f, 3f)
        circle(22f, 25f, 3f)
    }

    val Thresher: ImageVector by machine("Thresher") {
        roundRect(4f, 10f, 17f, 13f, 2f)
        circle(12f, 16f, 3.5f)
        moveTo(21f, 14f); lineTo(28f, 9f)
        moveTo(21f, 20f); horizontalLineTo(28f)
    }

    val Sprayer: ImageVector by machine("Sprayer") {
        roundRect(11f, 5f, 10f, 11f, 2f)
        moveTo(3f, 20f); horizontalLineTo(29f)
        moveTo(16f, 16f); verticalLineTo(20f)
        moveTo(8f, 20f); verticalLineTo(25f)
        moveTo(16f, 20f); verticalLineTo(25f)
        moveTo(24f, 20f); verticalLineTo(25f)
    }

    val Transplanter: ImageVector by machine("Transplanter") {
        moveTo(5f, 12f); horizontalLineTo(20f); lineTo(24f, 19f); horizontalLineTo(9f); close()
        moveTo(20f, 12f); lineTo(26f, 6f)
        moveTo(9f, 12f); verticalLineTo(8f); horizontalLineTo(17f); verticalLineTo(12f)
        circle(11f, 25f, 3f)
        circle(23f, 25f, 3f)
    }

    val PowerTiller: ImageVector by machine("PowerTiller") {
        circle(12f, 21f, 7f)
        moveTo(12f, 14f); verticalLineTo(28f)
        moveTo(5f, 21f); horizontalLineTo(19f)
        moveTo(19f, 17f); lineTo(27f, 9f)
        moveTo(24f, 8f); horizontalLineTo(29f); verticalLineTo(13f)
    }

    val Baler: ImageVector by machine("Baler") {
        roundRect(4f, 9f, 14f, 13f, 2f)
        circle(25f, 18f, 6f)
        circle(25f, 18f, 2f)
        circle(9f, 26f, 2.5f)
    }

    val Leveller: ImageVector by machine("Leveller") {
        moveTo(3f, 24f); horizontalLineTo(29f)
        moveTo(8f, 24f); lineTo(12f, 17f); horizontalLineTo(25f); lineTo(22f, 24f)
        moveTo(21f, 17f); verticalLineTo(5f)
        moveTo(17f, 5f); horizontalLineTo(25f)
    }

    /** Every machine glyph, in the order the picker shows them. */
    val allMachines: List<ImageVector>
        get() = listOf(
            Tractor, Harvester, Rotavator, Cultivator, Trolley, Thresher,
            Sprayer, Transplanter, PowerTiller, Baler, Leveller,
        )

    // ------------------------------------------------------------------ chrome

    val Home: ImageVector by chrome("Home") {
        moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); verticalLineTo(20f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, 1f); horizontalLineTo(15f); verticalLineTo(14f)
        horizontalLineTo(9f); verticalLineTo(21f); horizontalLineTo(4f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, -1f); close()
    }

    val Play: ImageVector by chrome("Play") {
        moveTo(7f, 4f); lineTo(19f, 12f); lineTo(7f, 20f); close()
    }

    val Pause: ImageVector by chrome("Pause") {
        moveTo(9f, 5f); verticalLineTo(19f)
        moveTo(15f, 5f); verticalLineTo(19f)
    }

    val Check: ImageVector by chrome("Check") {
        moveTo(4f, 12f); lineTo(10f, 18f); lineTo(20f, 6f)
    }

    val Add: ImageVector by chrome("Add") {
        moveTo(12f, 5f); verticalLineTo(19f)
        moveTo(5f, 12f); horizontalLineTo(19f)
    }

    val Minus: ImageVector by chrome("Minus") {
        moveTo(5f, 12f); horizontalLineTo(19f)
    }

    val Close: ImageVector by chrome("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Back: ImageVector by chrome("Back") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }

    val Forward: ImageVector by chrome("Forward") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    }

    val Customer: ImageVector by chrome("Customer") {
        circle(12f, 8f, 4f)
        moveTo(4f, 21f); curveToRelative(0f, -4f, 3.6f, -6f, 8f, -6f)
        reflectiveCurveToRelative(8f, 2f, 8f, 6f)
    }

    val Team: ImageVector by chrome("Team") {
        circle(9f, 8f, 3.5f)
        moveTo(2f, 20f); curveToRelative(0f, -3.5f, 3f, -5.5f, 7f, -5.5f)
        reflectiveCurveToRelative(7f, 2f, 7f, 5.5f)
        moveTo(18f, 14.5f); curveToRelative(2.4f, 0.6f, 4f, 2.3f, 4f, 5.5f)
    }

    val Rupee: ImageVector by chrome("Rupee") {
        moveTo(6f, 4f); horizontalLineTo(17f)
        moveTo(6f, 9f); horizontalLineTo(17f)
        moveTo(15f, 4f); curveToRelative(0f, 5f, -3.5f, 5f, -9f, 5f)
        moveTo(7f, 9f); lineTo(15f, 20f)
    }

    val Report: ImageVector by chrome("Report") {
        moveTo(4f, 20f); verticalLineTo(11f)
        moveTo(10f, 20f); verticalLineTo(5f)
        moveTo(16f, 20f); verticalLineTo(14f)
        moveTo(22f, 20f); verticalLineTo(8f)
    }

    val More: ImageVector by chrome("More") {
        circle(5f, 12f, 1.8f)
        circle(12f, 12f, 1.8f)
        circle(19f, 12f, 1.8f)
    }

    val Bell: ImageVector by chrome("Bell") {
        moveTo(18f, 8f); arcToRelative(6f, 6f, 0f, true, false, -12f, 0f)
        curveToRelative(0f, 7f, -3f, 8f, -3f, 8f); horizontalLineTo(21f)
        reflectiveCurveToRelative(-3f, -1f, -3f, -8f)
        moveTo(13.7f, 21f); arcToRelative(2f, 2f, 0f, false, true, -3.4f, 0f)
    }

    val Search: ImageVector by chrome("Search") {
        circle(11f, 11f, 7f)
        moveTo(16.5f, 16.5f); lineTo(21f, 21f)
    }

    val Clock: ImageVector by chrome("Clock") {
        circle(12f, 12f, 9f)
        moveTo(12f, 7f); verticalLineTo(12f); lineTo(15.5f, 14f)
    }

    val Field: ImageVector by chrome("Field") {
        moveTo(20f, 10.5f); curveToRelative(0f, 6f, -8f, 12f, -8f, 12f)
        reflectiveCurveToRelative(-8f, -6f, -8f, -12f)
        arcToRelative(8f, 8f, 0f, true, true, 16f, 0f); close()
        circle(12f, 10.5f, 3f)
    }

    val Mic: ImageVector by chrome("Mic") {
        roundRect(9f, 2.5f, 6f, 11f, 3f)
        moveTo(5f, 11f); arcToRelative(7f, 7f, 0f, false, false, 14f, 0f)
        moveTo(12f, 18f); verticalLineTo(21.5f)
    }

    val Camera: ImageVector by chrome("Camera") {
        roundRect(3f, 7f, 18f, 13f, 2f)
        moveTo(9f, 7f); lineTo(10.5f, 4f); horizontalLineTo(13.5f); lineTo(15f, 7f)
        circle(12f, 13.5f, 3.5f)
    }

    val WhatsApp: ImageVector by chrome("WhatsApp") {
        moveTo(3.5f, 20.5f); lineTo(4.9f, 15.9f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 3.2f, 3.2f); close()
        moveTo(9f, 9.5f); curveToRelative(0f, 3f, 2.5f, 5.5f, 5.5f, 5.5f)
    }

    val Fuel: ImageVector by chrome("Fuel") {
        moveTo(4f, 20f); verticalLineTo(9f); lineTo(8f, 5f); horizontalLineTo(13f)
        verticalLineTo(11f); horizontalLineTo(18f); verticalLineTo(20f); close()
        moveTo(8f, 14f); horizontalLineTo(14f)
    }

    val Service: ImageVector by chrome("Service") {
        moveTo(15.5f, 7f); arcToRelative(4.5f, 4.5f, 0f, false, true, -6f, 6f)
        lineTo(4f, 18.5f); lineTo(5.5f, 20f); lineTo(11f, 14.5f)
        arcToRelative(4.5f, 4.5f, 0f, false, false, 6f, -6f); close()
    }

    val Breakdown: ImageVector by chrome("Breakdown") {
        moveTo(12f, 3f); lineTo(1.5f, 21f); horizontalLineTo(22.5f); close()
        moveTo(12f, 10f); verticalLineTo(14f)
        moveTo(12f, 17.5f); verticalLineTo(18f)
    }

    val Lock: ImageVector by chrome("Lock") {
        roundRect(4f, 10f, 16f, 10f, 2f)
        moveTo(8f, 10f); verticalLineTo(7f); arcToRelative(4f, 4f, 0f, false, true, 8f, 0f); verticalLineTo(10f)
    }

    val Sync: ImageVector by chrome("Sync") {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 9f, 9f)
        moveTo(21f, 3f); verticalLineTo(9f); horizontalLineTo(15f)
    }

    val Offline: ImageVector by chrome("Offline") {
        moveTo(3f, 3f); lineTo(21f, 21f)
        moveTo(5f, 12.5f); arcToRelative(11f, 11f, 0f, false, true, 4f, -2.6f)
        moveTo(2f, 9f); arcToRelative(16f, 16f, 0f, false, true, 5f, -3.2f)
        moveTo(10f, 6.2f); arcToRelative(16f, 16f, 0f, false, true, 12f, 2.8f)
        moveTo(16f, 13f); arcToRelative(11f, 11f, 0f, false, true, 3f, 1.8f)
        moveTo(12f, 19f); verticalLineTo(19.5f)
    }

    val Calendar: ImageVector by chrome("Calendar") {
        roundRect(3.5f, 5f, 17f, 16f, 2f)
        moveTo(3.5f, 10f); horizontalLineTo(20.5f)
        moveTo(8f, 3f); verticalLineTo(7f)
        moveTo(16f, 3f); verticalLineTo(7f)
    }

    val Bill: ImageVector by chrome("Bill") {
        moveTo(6.5f, 3.5f); horizontalLineTo(17.5f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, 1f); verticalLineTo(19.5f)
        lineTo(12f, 16f); lineTo(5.5f, 19.5f); verticalLineTo(4.5f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, -1f); close()
    }

    val Edit: ImageVector by chrome("Edit") {
        moveTo(4f, 16.5f); verticalLineTo(20f); horizontalLineTo(7.5f)
        lineTo(18f, 9.5f); lineTo(14.5f, 6f); close()
        moveTo(13f, 7.5f); lineTo(16.5f, 11f)
    }

    val Call: ImageVector by chrome("Call") {
        moveTo(5f, 3f); horizontalLineTo(9f); lineTo(11f, 8f); lineTo(8.5f, 9.5f)
        arcToRelative(12f, 12f, 0f, false, false, 6f, 6f); lineTo(16f, 13f)
        lineTo(21f, 15f); verticalLineTo(19f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        arcTo(16.5f, 16.5f, 0f, false, true, 3f, 5f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); close()
    }

    val Swap: ImageVector by chrome("Swap") {
        moveTo(4f, 8f); horizontalLineTo(17f); lineTo(14f, 5f)
        moveTo(20f, 16f); horizontalLineTo(7f); lineTo(10f, 19f)
    }
}

// -------------------------------------------------------------------- builders

private const val MACHINE_VIEWPORT = 32f
private const val CHROME_VIEWPORT = 24f

private fun machine(name: String, block: PathBuilder.() -> Unit) =
    strokeIcon(name, MACHINE_VIEWPORT, 34f, block)

private fun chrome(name: String, block: PathBuilder.() -> Unit) =
    strokeIcon(name, CHROME_VIEWPORT, 24f, block)

/**
 * Lazily builds a stroke-only [ImageVector]. Lazy because an icon object holds every
 * glyph in the app and most screens touch a handful — there is no reason to allocate
 * paths for a baler on a screen that shows a ledger.
 */
private fun strokeIcon(
    name: String,
    viewport: Float,
    sizeDp: Float,
    block: PathBuilder.() -> Unit,
) = lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = name,
        defaultWidth = sizeDp.dp,
        defaultHeight = sizeDp.dp,
        viewportWidth = viewport,
        viewportHeight = viewport,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()
}

/** A full circle as two arcs — [PathBuilder] has no primitive for one. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y); arcToRelative(r, r, 0f, false, true, r, r)
    lineTo(x + w, y + h - r); arcToRelative(r, r, 0f, false, true, -r, r)
    lineTo(x + r, y + h); arcToRelative(r, r, 0f, false, true, -r, -r)
    lineTo(x, y + r); arcToRelative(r, r, 0f, false, true, r, -r)
    close()
}
