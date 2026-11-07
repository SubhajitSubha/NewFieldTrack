package com.example.fieldtrack.feature.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookingLinkState(
    val loaded: Boolean = false,
    val on: Boolean = false,
    val showRates: Boolean = false,
    val url: String? = null,
)

/** Where the business's public page lives: the project's own web address, and the secret part. */
fun bookingUrl(slug: String?): String? {
    val project = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()?.takeIf { it.isNotBlank() }
    return if (slug == null || project == null) null else "https://$project.web.app/b/$slug"
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookingLinkViewModel(private val app: FieldFlowApplication) : ViewModel() {
    val state: StateFlow<BookingLinkState> = app.sessionStore.session.flatMapLatest { s ->
        app.db.orgDao().observeOrg(s.orgId).map { org ->
            BookingLinkState(loaded = true, on = org?.bookingLink == true, showRates = org?.bookingShowRates == true, url = bookingUrl(org?.bookingSlug))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookingLinkState())

    init { viewModelScope.launch { app.sessionStore.load() } }

    /** Set when switching it on needs the paid plan, so the screen can say so and lead to it. */
    val needsPlan = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun set(on: Boolean, showRates: Boolean) = viewModelScope.launch {
        try {
            app.sessionStore.setBookingLink(on, showRates)
        } catch (limit: com.example.fieldtrack.data.plan.PlanLimit) {
            needsPlan.value = true
        } catch (other: Exception) {
            // Not allowed to change the business: nothing happens, as before.
        }
    }
}

@Composable
fun BookingLinkScreen(
    state: BookingLinkState,
    onChange: (on: Boolean, showRates: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val context = LocalContext.current
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.link_title),
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
            Text(stringResource(R.string.link_intro), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
            FtCard(padding = 0.dp) {
                Column {
                    SwitchRow(R.string.link_switch, R.string.link_switch_note, state.on) { onChange(it, state.showRates) }
                    if (state.on) {
                        com.example.fieldtrack.designsystem.component.FtRowDivider()
                        SwitchRow(R.string.link_rates, R.string.link_rates_note, state.showRates) { onChange(state.on, it) }
                    }
                }
            }
            if (state.on) {
                val url = state.url
                if (url != null) {
                    FtCard {
                        Text(url, style = FieldTrackTheme.type.body, color = colors.ink)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FtButton(
                            text = stringResource(R.string.link_copy),
                            onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                    ?.setPrimaryClip(ClipData.newPlainText("FieldTrack", url))
                            },
                            tone = FtButtonTone.Secondary, modifier = Modifier.weight(1f),
                        )
                        FtButton(
                            text = stringResource(R.string.link_share),
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.link_share_text, url))
                                }
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(stringResource(R.string.link_no_project), style = FieldTrackTheme.type.caption, color = colors.dueInk)
                }
                Text(stringResource(R.string.link_how), style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

@Composable
private fun SwitchRow(title: Int, note: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = FieldTrackTheme.type.rowTitle, color = colors.ink)
            Text(stringResource(note), style = FieldTrackTheme.type.caption, color = colors.inkTertiary, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.surface, uncheckedTrackColor = colors.line,
            ),
        )
    }
}
