 package com.example.fieldtrack.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtMetricChip
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtQuantityStepper
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStat
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.component.stripeColor
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * A living catalogue of the design system, for Android Studio previews.
 *
 * Not shipped to users — it exists so a change to a token can be seen across every
 * component at once, and so a new screen is assembled from things that already exist
 * rather than from fresh Boxes and hex codes.
 *
 * Copy here is literal English on purpose: this is a developer tool, not a screen, so
 * routing it through strings.xml would add translation noise for no benefit.
 */
@Preview(name = "Buttons", widthDp = 390, showBackground = true)
@Composable
private fun ButtonsPreview() = CatalogFrame {
    FtSectionLabel("Actions")
    FtButton("Start work", {}, tone = FtButtonTone.Primary, icon = FieldTrackIcons.Play)
    FtButton("Add customer", {}, tone = FtButtonTone.Secondary)
    FtButton("Send on WhatsApp", {}, tone = FtButtonTone.Send, icon = FieldTrackIcons.WhatsApp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FtButton("Pause", {}, Modifier.weight(1f), FtButtonTone.Caution, height = 52.dp)
        FtButton("Breakdown", {}, Modifier.weight(1f), FtButtonTone.Danger, height = 52.dp)
    }
    FtButton("Not now", {}, tone = FtButtonTone.Quiet, height = 50.dp)
}

@Preview(name = "Status", widthDp = 390, showBackground = true)
@Composable
private fun StatusPreview() = CatalogFrame {
    FtSectionLabel("Status — hue plus lightness")
    FtCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FtStatusPill(FtStatus.Running, "Running")
            FtStatusPill(FtStatus.Paused, "Paused")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FtStatusPill(FtStatus.Completed, "Completed")
            FtStatusPill(FtStatus.Due, "Payment due")
            FtStatusPill(FtStatus.Idle, "Idle")
        }
    }
}

@Preview(name = "Money", widthDp = 390, showBackground = true)
@Composable
private fun MoneyPreview() = CatalogFrame {
    FtSectionLabel("Money — Indian grouping, tabular figures")
    FtEmphasisCard {
        FtStat(
            label = "Owed to you",
            value = "",
            labelColor = FieldTrackTheme.colors.onDarkMuted,
        )
        FtMoney(184300, size = FtMoneySize.Hero, onDark = true)
    }
    FtCard {
        FtMoney(12400, size = FtMoneySize.Large, tone = FtMoneyTone.Due)
        FtMoney(5000, size = FtMoneySize.Medium, tone = FtMoneyTone.Received)
        FtMoney(3500, size = FtMoneySize.Row)
    }
}

@Preview(name = "Rows", widthDp = 390, showBackground = true)
@Composable
private fun RowsPreview() = CatalogFrame {
    FtSectionLabel("Customers")
    FtCard(padding = 0.dp) {
        FtListRow(
            title = "Ramesh Singh",
            subtitle = "Kheri village · 14 jobs",
            leading = {
                FtAvatar(
                    "RS",
                    background = FieldTrackTheme.colors.dueWash,
                    contentColor = FieldTrackTheme.colors.dueInk,
                )
            },
            trailing = { FtMoney(12400, size = FtMoneySize.Row, tone = FtMoneyTone.Due) },
            onClick = {},
        )
        FtRowDivider()
        FtListRow(
            title = "Gurpreet Kaur",
            subtitle = "Rampur · 6 jobs",
            leading = { FtAvatar("GK") },
            trailing = { FtStatusPill(FtStatus.Idle, "Settled") },
            onClick = {},
        )
    }

    FtSectionLabel("Live jobs")
    FtCard(padding = 0.dp) {
        FtListRow(
            title = "Jaswant Kumar",
            subtitle = "Harvester · for Ramesh Singh",
            statusStripe = FtStatus.Running.stripeColor(),
            leading = { FtIconTile(FieldTrackIcons.Harvester) },
            showChevron = true,
            onClick = {},
        )
    }
}

@Preview(name = "Inputs", widthDp = 390, showBackground = true)
@Composable
private fun InputsPreview() = CatalogFrame {
    FtTextField(
        label = "Your name",
        value = "Harpal Singh",
        onValueChange = {},
        focused = true,
        onVoiceInput = {},
        voiceContentDescription = "Speak your name instead of typing",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FtChip("Acre", selected = true, onClick = {})
        FtChip("Bigha", selected = false, onClick = {})
        FtChip("Kanal", selected = false, onClick = {})
    }
    FtChoiceCard(
        title = "I work my own machines",
        description = "You own a tractor or harvester and do the work yourself.",
        icon = FieldTrackIcons.Tractor,
        selected = true,
        onClick = {},
    )
    FtChoiceCard(
        title = "I run a business",
        description = "Several machines and drivers.",
        icon = FieldTrackIcons.Team,
        selected = false,
        onClick = {},
    )
}

@Preview(name = "Running work", widthDp = 390, heightDp = 420)
@Composable
private fun RunningWorkPreview() {
    FieldTrackTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(FieldTrackTheme.colors.primaryPressed)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FtStatusPill(FtStatus.Running, "Running", onDark = true)
            FtQuantityStepper(
                value = "3.5",
                unit = "acre",
                onDecrement = {},
                onIncrement = {},
                decrementContentDescription = "Take away half an acre",
                incrementContentDescription = "Add half an acre",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FtMetricChip("2:14:36", icon = FieldTrackIcons.Clock)
                FtMetricChip("48 km", icon = FieldTrackIcons.Field)
            }
        }
    }
}

@Preview(name = "Machine icons", widthDp = 390, showBackground = true)
@Composable
private fun MachineIconsPreview() = CatalogFrame {
    FtSectionLabel("Machines")
    FtCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FieldTrackIcons.allMachines.take(6).forEach {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = FieldTrackTheme.colors.ink,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FieldTrackIcons.allMachines.drop(6).forEach {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = FieldTrackTheme.colors.ink,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Preview(name = "Top bar", widthDp = 390, showBackground = true)
@Composable
private fun TopBarPreview() {
    FieldTrackTheme {
        Column(Modifier.background(FieldTrackTheme.colors.paper)) {
            FtTopBar(title = "Customers")
            FtTopBar(
                title = "Ramesh Singh",
                subtitle = "Kheri Kalan · 14 jobs",
                onBack = {},
                backContentDescription = "Go back",
            )
        }
    }
}

@Composable
private fun CatalogFrame(content: @Composable () -> Unit) {
    FieldTrackTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(FieldTrackTheme.colors.paper)
                .verticalScroll(rememberScrollState())
                .padding(FieldTrackTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            content()
        }
    }
}
