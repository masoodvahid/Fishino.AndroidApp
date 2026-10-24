package rdsco.fishino.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import rdsco.fishino.app.ui.common.GlassPanel

/** Real employer announcements, with a neutral glass surface and a flat illustration fallback. */
@Composable
fun NewsSlideshow(items: List<NewsItem>, onOpen: (Long) -> Unit, onOpenAll: () -> Unit) {
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

    HorizontalPager(
        state = pager,
        pageSpacing = 12.dp,
        key = { items[it].id },
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
    ) { page ->
        val item = items[page]
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOpenAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(
                                item.typeLabel.ifBlank { stringResource(R.string.home_announcement) },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val description = item.summary.ifBlank { item.dateLabel.orEmpty() }.persianDigits()
                        if (description.isNotBlank()) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val illustration: @Composable () -> Unit = {
                        Image(
                            painterResource(R.drawable.ic_flat_announcement),
                            contentDescription = null,
                            modifier = Modifier.size(88.dp),
                        )
                    }
                    if (item.hasImage) {
                        AuthedImage(
                            path = NewsApi.imagePath(item.id),
                            contentDescription = null,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)),
                            placeholder = illustration,
                        )
                    } else {
                        illustration()
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onOpen(item.id) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.home_read_announcement), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(items.size) { index ->
                            Box(
                                Modifier.size(7.dp).background(
                                    if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
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
