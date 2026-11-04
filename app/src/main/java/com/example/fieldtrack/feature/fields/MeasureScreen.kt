package com.example.fieldtrack.feature.fields

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.measure.Shape
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtFitText
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.layout.LocalWidthClass
import com.example.fieldtrack.designsystem.layout.WidthClass
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.appViewModel
import com.example.fieldtrack.feature.calculator.exactQuantity
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.unitInPhrase
import java.util.Locale

/** What the screen can ask for, kept together so the screen stays one argument wide. */
class MeasureActions(
    val onBack: () -> Unit,
    /** Asks for the permission, or — once it has been refused for good — opens the app's settings. */
    val onAllow: () -> Unit,
    val onOpenLocation: () -> Unit,
    val onStart: () -> Unit,
    val onFinish: () -> Unit,
    val onKeepWalking: () -> Unit,
    val onStartOver: () -> Unit,
    val onUnit: (MeasureUnit) -> Unit,
    val onPerAcre: (String) -> Unit,
    val onUse: () -> Unit,
)

/**
 * The measuring screen with everything it needs wired to the phone: the GPS while it
 * is on show, the location permission, the screen kept awake while walking, and a
 * question before a walk is thrown away.
 */
@Composable
fun MeasureDestination(unit: MeasureUnit, onBack: () -> Unit, onUse: (Double, MeasureUnit) -> Unit) {
    val context = LocalContext.current
    val vm: MeasureViewModel = appViewModel(key = "measure-${unit.name}") { MeasureViewModel(it, unit) }
    val state by vm.state.collectAsStateWithLifecycle()
    var asked by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    // Both together: from Android 12 the phone offers precise or approximate, and
    // asks for precise on its own is not shown that choice.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        vm.refresh()
        vm.listen(gpsFixes(context))
    }

    // Readings only while this screen is in front: nothing keeps the GPS running
    // behind a locked phone, and a walk that was cut off says so.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { vm.refresh(); vm.listen(gpsFixes(context)) }
                Lifecycle.Event.ON_STOP -> vm.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.stop() }
    }

    val view = LocalView.current
    DisposableEffect(state.walking) {
        view.keepScreenOn = state.walking
        onDispose { view.keepScreenOn = false }
    }

    val inProgress = state.points.isNotEmpty()
    BackHandler(enabled = inProgress) { confirmLeave = true }

    fun open(intent: Intent) { runCatching { context.startActivity(intent) } }
    val permanentlyRefused = asked && (context as? Activity)?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.ACCESS_FINE_LOCATION)
    } == true

    MeasureScreen(
        state = state,
        actions = MeasureActions(
            onBack = { if (inProgress) confirmLeave = true else onBack() },
            onAllow = {
                if (permanentlyRefused) {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else {
                    ask.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                }
            },
            onOpenLocation = { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            onStart = vm::start,
            onFinish = vm::finish,
            onKeepWalking = vm::keepWalking,
            onStartOver = vm::startOver,
            onUnit = vm::setUnit,
            onPerAcre = vm::setPerAcre,
            onUse = { vm.use()?.let { (area, chosen) -> onUse(area, chosen) } },
        ),
        permanentlyRefused = permanentlyRefused,
    )

    if (confirmLeave) {
        val colors = FieldTrackTheme.colors
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.measure_discard_title), style = FieldTrackTheme.type.sectionTitle, color = colors.ink) },
            text = { Text(stringResource(R.string.measure_discard_note), style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.startOver(); onBack() }) {
                    Text(stringResource(R.string.measure_discard_yes), style = FieldTrackTheme.type.label, color = colors.due)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.measure_keep_going), style = FieldTrackTheme.type.label, color = colors.ink)
                }
            },
        )
    }
}

/**
 * Measure a plot by walking round it: the path as it is walked, then the size.
 *
 * On a phone the path is above and the questions below, with the one thing to do
 * next pinned at the foot; where there is room they sit side by side.
 */
@Composable
fun MeasureScreen(
    state: MeasureState,
    actions: MeasureActions,
    modifier: Modifier = Modifier,
    permanentlyRefused: Boolean = false,
) {
    val dimens = FieldTrackTheme.dimens
    val twoPane = LocalWidthClass.current == WidthClass.Expanded

    AdaptiveScreen(modifier) {
        FtTopBar(
            title = stringResource(R.string.measure_title),
            subtitle = stringResource(R.string.measure_subtitle),
            onBack = actions.onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        if (state.access != GpsAccess.Allowed) {
            Column(Modifier.padding(dimens.screenPadding)) { Setup(state.access, permanentlyRefused, actions) }
            return@AdaptiveScreen
        }

        if (twoPane) {
            Row(
                Modifier.weight(1f).padding(horizontal = dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PathCanvas(state, Modifier.weight(1f).fillMaxSize().padding(bottom = 12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Status(state, actions)
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PathCanvas(state, Modifier.fillMaxWidth().height(240.dp))
                Status(state, actions)
            }
        }
        Box(Modifier.padding(dimens.screenPadding)) { Doing(state, actions) }
    }
}

/** Why the GPS cannot be used yet, and the one thing that fixes it. */
@Composable
private fun Setup(access: GpsAccess, permanentlyRefused: Boolean, actions: MeasureActions) {
    val colors = FieldTrackTheme.colors
    val needsPermission = access != GpsAccess.SwitchedOff
    val approximate = access == GpsAccess.NotPrecise
    FtCard {
        Text(
            text = stringResource(
                when {
                    !needsPermission -> R.string.measure_off_title
                    approximate -> R.string.measure_precise_title
                    else -> R.string.measure_allow_title
                }
            ),
            style = FieldTrackTheme.type.sectionTitle,
            color = colors.ink,
        )
        Text(
            text = stringResource(
                when {
                    !needsPermission -> R.string.measure_off_note
                    approximate && permanentlyRefused -> R.string.measure_precise_denied_note
                    approximate -> R.string.measure_precise_note
                    permanentlyRefused -> R.string.measure_denied_note
                    else -> R.string.measure_allow_note
                }
            ),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        FtButton(
            text = stringResource(
                when {
                    !needsPermission -> R.string.measure_off_open
                    permanentlyRefused -> R.string.measure_settings
                    else -> R.string.measure_allow
                }
            ),
            onClick = if (needsPermission) actions.onAllow else actions.onOpenLocation,
        )
    }
}

/** What is happening, in words, and the result once there is one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Status(state: MeasureState, actions: MeasureActions) {
    val colors = FieldTrackTheme.colors

    if (state.finished) {
        Result(state, actions)
        return
    }

    val (text, warn) = when {
        state.walking && state.signal != Signal.Good -> stringResource(R.string.measure_signal_lost) to true
        state.walking -> stringResource(R.string.measure_walking) to false
        state.signal == Signal.Good -> stringResource(R.string.measure_ready, state.accuracyMetres ?: 0) to false
        state.signal == Signal.Weak -> stringResource(R.string.measure_weak, state.accuracyMetres ?: 0) to true
        else -> stringResource(R.string.measure_searching) to false
    }
    Text(text, style = FieldTrackTheme.type.body, color = if (warn) colors.dueInk else colors.ink)
    if (state.walking) {
        Text(
            text = stringResource(R.string.measure_walked, metres(state.walkedMetres)),
            style = FieldTrackTheme.type.sectionTitle,
            color = colors.ink,
        )
        if (state.interrupted) Notice(stringResource(R.string.measure_interrupted))
    }
    Text(stringResource(R.string.measure_accuracy_note), style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Result(state: MeasureState, actions: MeasureActions) {
    val colors = FieldTrackTheme.colors
    val shape = state.shape
    FtEmphasisCard {
        FtSectionLabel(stringResource(R.string.measure_result_label), color = colors.onDarkMuted)
        FtFitText(
            text = state.area?.let { stringResource(R.string.measure_area, exactQuantity(it), unitInPhrase(state.unit)) }
                ?: stringResource(R.string.price_result_blank),
            style = FieldTrackTheme.type.moneyHero,
            color = colors.onDark,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (shape != null) {
            Text(
                text = stringResource(R.string.measure_round, metres(shape.perimeterMetres)),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.onDarkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeasureUnit.entries.filter { it.isLand }.forEach { unit ->
            FtChip(
                label = stringResource(unit.labelRes),
                selected = unit == state.unit,
                onClick = { actions.onUnit(unit) },
            )
        }
    }

    // A bigha or a katha is not one size everywhere: the farmer says how many make an
    // acre where they live, and it is remembered rather than asked again.
    if (com.example.fieldtrack.data.measure.Land.fixedPerAcre(state.unit) == null) {
        FtTextField(
            label = stringResource(R.string.measure_per_acre_label, unitInPhrase(state.unit)),
            value = state.perAcreText,
            onValueChange = actions.onPerAcre,
            placeholder = stringResource(R.string.measure_per_acre_hint),
            helper = stringResource(R.string.measure_per_acre_note, unitInPhrase(state.unit)),
            keyboardType = KeyboardType.Decimal,
        )
    }

    when {
        shape == null || !shape.enough -> Notice(stringResource(R.string.measure_not_enough))
        else -> {
            if (shape.crossesItself) Notice(stringResource(R.string.measure_crosses))
            if (shape.closingGapMetres > maxOf(GAP_FLOOR_METRES, shape.perimeterMetres * GAP_SHARE)) {
                Notice(stringResource(R.string.measure_gap, metres(shape.closingGapMetres)))
            }
            if (state.interrupted) Notice(stringResource(R.string.measure_interrupted))
        }
    }
}

/** A warning that does not stop anything: the size is still given, with its doubt. */
@Composable
private fun Notice(text: String) {
    val colors = FieldTrackTheme.colors
    FtCard(background = colors.moneyWash, borderColor = colors.moneyWash, padding = 12.dp) {
        Text(text, style = FieldTrackTheme.type.bodySmall, color = colors.moneyInk)
    }
}

/** The thing to do next, pinned at the foot. */
@Composable
private fun Doing(state: MeasureState, actions: MeasureActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.finished -> {
                FtButton(stringResource(R.string.measure_use), actions.onUse, enabled = state.canUse)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FtButton(
                        stringResource(R.string.measure_keep), actions.onKeepWalking,
                        tone = FtButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f),
                    )
                    FtButton(
                        stringResource(R.string.measure_over), actions.onStartOver,
                        tone = FtButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f),
                    )
                }
            }
            state.walking -> {
                FtButton(stringResource(R.string.measure_finish), actions.onFinish)
                FtButton(stringResource(R.string.measure_over), actions.onStartOver, tone = FtButtonTone.Secondary, height = 48.dp)
            }
            else -> FtButton(stringResource(R.string.measure_start), actions.onStart, enabled = state.signal == Signal.Good)
        }
    }
}

/**
 * The walk drawn as it goes, north at the top, scaled to fill the box.
 *
 * Nothing underneath it — no map, no tiles — because the shape of the edge is all
 * that matters here, and a map would need somebody else's data.
 */
@Composable
private fun PathCanvas(state: MeasureState, modifier: Modifier) {
    val colors = FieldTrackTheme.colors
    val here = state.here
    val shape = (state.points + listOfNotNull(here)).takeIf { it.isNotEmpty() }?.let(::Shape)
    val description = stringResource(R.string.measure_path_cd, metres(state.walkedMetres))

    Canvas(
        modifier
            .clip(FieldTrackTheme.shapes.card)
            .background(colors.surface)
            .border(FieldTrackTheme.dimens.hairline, colors.line, FieldTrackTheme.shapes.card)
            .semantics { contentDescription = description },
    ) {
        shape ?: return@Canvas
        val flat = shape.flat
        val minX = flat.minOf { it.first }; val maxX = flat.maxOf { it.first }
        val minY = flat.minOf { it.second }; val maxY = flat.maxOf { it.second }
        val pad = 24.dp.toPx()
        // At least thirty metres across, so the first few steps do not blow up to fill the box.
        val scale = minOf((size.width - 2 * pad) / maxOf(maxX - minX, MIN_SPAN), (size.height - 2 * pad) / maxOf(maxY - minY, MIN_SPAN)).toFloat()
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        fun at(p: Pair<Double, Double>) = Offset(
            size.width / 2 + ((p.first - cx) * scale).toFloat(),
            size.height / 2 - ((p.second - cy) * scale).toFloat(),
        )

        val walked = flat.take(state.points.size).map(::at)
        if (walked.size >= 2) {
            val path = Path().apply { moveTo(walked[0].x, walked[0].y); walked.drop(1).forEach { lineTo(it.x, it.y) } }
            if (state.finished) {
                drawPath(Path().apply { addPath(path); close() }, colors.primaryWash)
                drawPath(Path().apply { addPath(path); close() }, colors.primary, style = Stroke(3.dp.toPx()))
            } else {
                drawPath(path, colors.primary, style = Stroke(3.dp.toPx()))
                // Where the walk would close, so the gap left is seen as it is made.
                if (walked.size >= 3) {
                    drawLine(
                        colors.inkTertiary, walked.last(), walked.first(), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                }
            }
        }
        walked.firstOrNull()?.let { drawCircle(colors.ink, 7.dp.toPx(), it) }
        if (!state.finished) drawCircle(colors.money, 6.dp.toPx(), at(flat.last()))
    }
}

private fun metres(value: Double) = String.format(Locale.US, "%,d", Math.round(value))

private const val MIN_SPAN = 30.0
private const val GAP_FLOOR_METRES = 15.0
private const val GAP_SHARE = 0.1
