package com.example.fieldtrack.feature.more

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.export.ExportPeriod
import com.example.fieldtrack.data.export.ExportScope
import com.example.fieldtrack.data.export.RecordsExport
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportState(
    val period: ExportPeriod = ExportPeriod.Everything,
    val busy: Boolean = false,
    val failed: Boolean = false,
    /** What goes in, so the person knows before they send it anywhere. */
    val money: Boolean = false,
    val peopleCost: Boolean = false,
)

class ExportViewModel(private val app: FieldFlowApplication) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _state.update {
                it.copy(money = Capability.SeeMoney in s.can, peopleCost = Capability.SeePeopleCost in s.can)
            }
        }
    }

    fun setPeriod(period: ExportPeriod) = _state.update { it.copy(period = period, failed = false) }

    /** Builds the file off the main thread and hands it to the share sheet. */
    fun export(context: Context) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val ok = runCatching {
                val s = app.sessionStore.load() ?: error("no session")
                val dir = File(app.cacheDir, "documents")
                val file = withContext(Dispatchers.IO) {
                    RecordsExport(app.db).build(
                        orgId = s.orgId,
                        orgName = s.businessName,
                        period = _state.value.period,
                        scope = ExportScope(money = _state.value.money, peopleCost = _state.value.peopleCost),
                        dir = dir,
                        now = System.currentTimeMillis(),
                    )
                }
                share(context, file)
            }.isSuccess
            _state.update { it.copy(busy = false, failed = !ok) }
        }
    }

    private fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Taking everything out: one file of plain tables a spreadsheet, an accountant or a bank
 * can open. Offered to everybody for what they are allowed to see, and saying plainly
 * what is in it before it goes anywhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    state: ExportState,
    onPeriod: (ExportPeriod) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.export_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.export_intro),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
            )
            FtSectionLabel(stringResource(R.string.export_period), Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ExportPeriod.Everything to R.string.export_all,
                    ExportPeriod.ThisYear to R.string.export_this_year,
                    ExportPeriod.LastYear to R.string.export_last_year,
                ).forEach { (period, label) ->
                    FtChip(label = stringResource(label), selected = state.period == period, onClick = { onPeriod(period) })
                }
            }
            Text(
                text = stringResource(R.string.export_period_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
            FtSectionLabel(stringResource(R.string.export_contents), Modifier.padding(top = 8.dp))
            FtCard {
                Text(
                    text = buildString {
                        append(stringResource(R.string.export_has_work))
                        if (state.money) append("\n").append(stringResource(R.string.export_has_money))
                        if (state.peopleCost) append("\n").append(stringResource(R.string.export_has_pay))
                    },
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.ink,
                )
            }
            if (!state.money) {
                Text(
                    text = stringResource(R.string.export_no_money),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }
            if (state.failed) {
                Text(
                    text = stringResource(R.string.export_failed),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        FtButton(
            text = stringResource(if (state.busy) R.string.export_busy else R.string.export_make),
            onClick = onExport,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(dimens.screenPadding),
        )
    }
}
