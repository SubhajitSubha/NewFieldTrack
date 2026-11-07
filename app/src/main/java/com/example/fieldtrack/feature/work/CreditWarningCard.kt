package com.example.fieldtrack.feature.work

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.billing.CreditWarning
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * "Ramesh already owes ₹40,000, more than the ₹30,000 you set for him."
 *
 * Said when work is about to be booked or started for somebody in arrears. It informs and
 * never blocks: the owner knows the farmer and the harvest, and what to do next is theirs.
 */
@Composable
fun CreditWarningCard(warning: CreditWarning, name: String) {
    val colors = FieldTrackTheme.colors
    FtCard(background = colors.moneyWash, borderColor = colors.money) {
        Text(
            text = when (warning) {
                is CreditWarning.OverLimit ->
                    stringResource(R.string.credit_over_limit, name, money(warning.owedMinor), money(warning.limitMinor))
                is CreditWarning.Late ->
                    pluralStringResource(R.plurals.credit_late, warning.days, name, money(warning.owedMinor), warning.days)
            },
            style = FieldTrackTheme.type.bodySmall,
            color = colors.moneyInk,
        )
    }
}
