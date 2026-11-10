package com.example.fieldtrack.feature.help

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/** The questions people ask most, each with its answer. Words only, so they can be translated whole. */
private val QUESTIONS = listOf(
    R.string.help_q_record to R.string.help_a_record,
    R.string.help_q_driver to R.string.help_a_driver,
    R.string.help_q_offline to R.string.help_a_offline,
    R.string.help_q_part to R.string.help_a_part,
    R.string.help_q_share to R.string.help_a_share,
    R.string.help_q_phone to R.string.help_a_phone,
    R.string.help_q_money to R.string.help_a_money,
    R.string.help_q_export to R.string.help_a_export,
)

/**
 * Help inside the app: answers to what people ask, a way to see the tour again, a way to
 * try the app on made-up work, and a way to reach us. The last is shown only when there is
 * an address to reach.
 */
@Composable
fun HelpScreen(
    hasSupport: Boolean,
    practiceExists: Boolean,
    onTour: () -> Unit,
    onPractice: () -> Unit,
    onRate: () -> Unit,
    onContact: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.help_title),
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
            FtCard(padding = 0.dp) {
                Column {
                    FtListRow(
                        title = stringResource(R.string.help_tour),
                        subtitle = stringResource(R.string.help_tour_note),
                        leading = { FtIconTile(FieldTrackIcons.Play, size = 40.dp) },
                        showChevron = true,
                        onClick = onTour,
                    )
                    FtRowDivider()
                    FtListRow(
                        title = stringResource(if (practiceExists) R.string.help_practice_back else R.string.help_practice),
                        subtitle = stringResource(R.string.help_practice_note),
                        leading = { FtIconTile(FieldTrackIcons.Tractor, size = 40.dp) },
                        showChevron = true,
                        onClick = onPractice,
                    )
                    FtRowDivider()
                    FtListRow(
                        title = stringResource(R.string.help_rate),
                        subtitle = stringResource(R.string.help_rate_note),
                        leading = { FtIconTile(FieldTrackIcons.Check, size = 40.dp) },
                        showChevron = true,
                        onClick = onRate,
                    )
                    if (hasSupport) {
                        FtRowDivider()
                        FtListRow(
                            title = stringResource(R.string.help_contact),
                            subtitle = stringResource(R.string.help_contact_note),
                            leading = { FtIconTile(FieldTrackIcons.Call, size = 40.dp) },
                            showChevron = true,
                            onClick = onContact,
                        )
                    }
                }
            }
            FtSectionLabel(stringResource(R.string.help_questions), Modifier.padding(top = 8.dp))
            QUESTIONS.forEachIndexed { index, (question, answer) ->
                var open by rememberSaveable(index) { mutableStateOf(false) }
                val q = stringResource(question)
                FtCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { open = !open }
                            .semantics { stateDescription = if (open) "expanded" else "collapsed" },
                    ) {
                        Text(text = q, style = FieldTrackTheme.type.rowTitle, color = colors.ink)
                        if (open) {
                            Text(
                                text = stringResource(answer),
                                style = FieldTrackTheme.type.body,
                                color = colors.inkSecondary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

/** An email to us, with what we would otherwise have to ask: which version, which phone. */
fun contactSupport(context: Context, to: String, subject: String, intro: String) {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    val body = "$intro\n\n—\nFieldTrack $version, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}\n"
    val send = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(send) }
}
