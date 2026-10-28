package rdsco.fishino.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rdsco.fishino.app.panel.NewsItem
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.news.NewsImageCard

/**
 * The latest announcements as picture cards that turn by themselves; a
 * tap on a card opens it. The full list is in the menu.
 */
@Composable
fun NewsSlideshow(items: List<NewsItem>, onOpen: (Long) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    LaunchedEffect(items.map { it.id }) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_MS)
            if (!pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            key = { items[it].id },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            NewsImageCard(item, onClick = { onOpen(item.id) }) {
                if (items.size > 1) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = .35f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(items.size) { index ->
                            Box(
                                Modifier.size(6.dp).background(
                                    if (index == pager.currentPage) Color.White else Color.White.copy(alpha = .45f),
                                    CircleShape,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val SLIDE_MS = 6_000L
