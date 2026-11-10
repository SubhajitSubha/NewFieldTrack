package com.example.fieldtrack.feature.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What has been noted at one end of a job. */
data class ProofEnd(
    val meter: Double? = null,
    val photoId: String? = null,
    val located: Boolean = false,
)

/** Everything noted on a job to prove it was done, at both ends. */
data class JobProof(val start: ProofEnd = ProofEnd(), val end: ProofEnd = ProofEnd())

/** One note of proof, to be written on the job. Anything null is left as it was. */
data class ProofNote(
    val atFinish: Boolean,
    val meter: Double? = null,
    val photoId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** The photo of a meter, from this phone if it is here and from the server if not. */
@Composable
fun PhotoImage(orgId: String, photoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as FieldFlowApplication
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, orgId, photoId) {
        value = withContext(Dispatchers.IO) {
            app.photoStore.open(orgId, photoId)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = stringResource(R.string.proof_photo_cd),
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Text(stringResource(R.string.proof_photo_loading), style = FieldTrackTheme.type.caption, color = FieldTrackTheme.colors.inkTertiary, modifier = modifier)
    }
}

/** The phone's last known position, from whatever it has, or null with no permission or no fix. */
fun lastKnownPlace(context: Context): Location? {
    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

/**
 * The meter, a photo of it, and where the phone was, for one end of a job. Each is its own
 * optional step and each is saved the moment it is done, so none is lost if the sheet is closed.
 */
@Composable
private fun ProofEndCard(
    title: String,
    orgId: String,
    done: ProofEnd,
    onNote: (meter: Double?, photoId: String?, latitude: Double?, longitude: Double?) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as FieldFlowApplication
    var meterText by rememberSaveable(title, done.meter) { mutableStateOf(done.meter?.let { quantityText(it) }.orEmpty()) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var placeMissing by rememberSaveable { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = capturePath?.let(::File)
        if (taken && file != null) app.photoStore.ingest(orgId, file)?.let { onNote(null, it, null, null) }
        capturePath = null
    }
    val here = {
        val fix = lastKnownPlace(context)
        if (fix != null) { placeMissing = false; onNote(null, null, fix.latitude, fix.longitude) } else placeMissing = true
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { here() }

    FtCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = FieldTrackTheme.type.rowTitle, color = FieldTrackTheme.colors.ink)
            FtTextField(
                value = meterText,
                onValueChange = { meterText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                label = stringResource(R.string.proof_meter),
                helper = stringResource(R.string.proof_meter_note),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            )
            val typed = meterText.toDoubleOrNull()
            if (typed != null && typed != done.meter) {
                FtButton(
                    text = stringResource(R.string.proof_save_meter),
                    onClick = { onNote(typed, null, null, null) },
                    tone = FtButtonTone.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            done.photoId?.let { PhotoImage(orgId, it) }
            FtButton(
                text = stringResource(if (done.photoId == null) R.string.proof_take_photo else R.string.proof_retake_photo),
                onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File.createTempFile("meter", ".jpg", dir)
                    capturePath = file.path
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                },
                tone = FtButtonTone.Secondary,
                icon = FieldTrackIcons.Camera,
                modifier = Modifier.fillMaxWidth(),
            )
            FtButton(
                text = stringResource(if (done.located) R.string.proof_place_done else R.string.proof_mark_place),
                onClick = {
                    val have = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                    if (have) here() else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                tone = FtButtonTone.Secondary,
                icon = FieldTrackIcons.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            if (placeMissing) {
                Text(stringResource(R.string.proof_place_missing), style = FieldTrackTheme.type.caption, color = FieldTrackTheme.colors.dueInk)
            }
        }
    }
}

private fun quantityText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** The proof sheet on a running job: the start, and the finish. */
@Composable
fun ProofPanel(orgId: String, proof: JobProof, onNote: (ProofNote) -> Unit) {
    SheetTitle(stringResource(R.string.proof_title))
    Text(stringResource(R.string.proof_intro), style = FieldTrackTheme.type.bodySmall, color = FieldTrackTheme.colors.inkSecondary)
    ProofEndCard(stringResource(R.string.proof_at_start), orgId, proof.start) { m, p, la, ln -> onNote(ProofNote(false, m, p, la, ln)) }
    ProofEndCard(stringResource(R.string.proof_at_finish), orgId, proof.end) { m, p, la, ln -> onNote(ProofNote(true, m, p, la, ln)) }
}
