package com.example.fieldtrack.data.db.entity

import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyStyle

/**
 * How this business's money is written, in the way the person on this phone reads it.
 *
 * A business with no row to read, such as a document made before its details loaded,
 * falls back to the style on screen.
 */
fun OrgEntity?.moneyStyle(): MoneyStyle =
    if (this == null) Money.style
    else MoneyStyle.of(currencyCode, moneyStepMinor, Money.grouping)
