package rdsco.fishino.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.NewsApi
import rdsco.fishino.app.panel.NewsItem
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.ContentMaxWidth

/** The three latest announcements, sliding by themselves every few seconds. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsSlideshow(items: List<NewsItem>, onOpen: (Long) -> Unit, onOpenAll: () -> Unit) {
    val pager = rememberPagerState { items.size }

    LaunchedEffect(items.size) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_MS)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }

    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        ) { page ->
            Slide(items[page]) { onOpen(items[page].id) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(items.size) { index ->
                    Box(
                        Modifier
                            .size(if (index == pager.currentPage) 9.dp else 7.dp)
                            .background(
                                if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                    )
                }
            }
            TextButton(onClick = onOpenAll) { Text(stringResource(R.string.home_all_news)) }
        }
    }
}

@Composable
private fun Slide(item: NewsItem, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.75f))))
            .clickable(onClick = onClick),
    ) {
        if (item.hasImage) {
            AuthedImage(
                path = NewsApi.imagePath(item.id),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = {},
            )
        }
        // Keeps the title readable on any picture.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))

        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(
                listOfNotNull(item.typeLabel.takeIf { it.isNotBlank() }, item.dateLabel).joinToString(" · ").persianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SLIDE_MS = 5_000L
