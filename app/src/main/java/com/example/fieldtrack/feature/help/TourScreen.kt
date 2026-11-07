package com.example.fieldtrack.feature.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.launch

private val PAGES = listOf(
    Triple(FieldTrackIcons.Clock, R.string.tour_1_title, R.string.tour_1_body),
    Triple(FieldTrackIcons.Calendar, R.string.tour_2_title, R.string.tour_2_body),
    Triple(FieldTrackIcons.Rupee, R.string.tour_3_title, R.string.tour_3_body),
    Triple(FieldTrackIcons.Offline, R.string.tour_4_title, R.string.tour_4_body),
)

/**
 * Four short pages on what the app does, shown once after setting up and again from Help.
 * It can be skipped at any point and it never gets in the way of the real screens.
 */
@Composable
fun TourScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    Column(modifier.fillMaxSize().background(colors.paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) FtButton(text = stringResource(R.string.tour_skip), onClick = onDone, tone = FtButtonTone.Quiet, height = 44.dp)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val (icon, title, body) = PAGES[page]
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FtIconTile(icon, size = 72.dp)
                Text(stringResource(title), style = FieldTrackTheme.type.screenTitle, color = colors.ink, textAlign = TextAlign.Center)
                Text(stringResource(body), style = FieldTrackTheme.type.body, color = colors.inkSecondary, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { index ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) colors.primary else colors.line)
                )
            }
        }
        FtButton(
            text = stringResource(if (last) R.string.tour_done else R.string.tour_next),
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(dimens.screenPadding),
        )
    }
}
