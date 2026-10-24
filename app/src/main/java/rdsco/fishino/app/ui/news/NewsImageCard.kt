package rdsco.fishino.app.ui.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.NewsApi
import rdsco.fishino.app.panel.NewsItem
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.home.persianDigits

/**
 * A news item as a picture: the image fills the card, a short dark
 * gradient at the bottom carries the title. The whole card opens the item.
 */
@Composable
fun NewsImageCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = item.title, onClick = onClick),
    ) {
        val fallback: @Composable () -> Unit = { NoImage() }
        if (item.hasImage) {
            AuthedImage(
                path = NewsApi.imagePath(item.id),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = fallback,
            )
        } else {
            fallback()
        }

        // Only the lower part darkens, enough for white text on any picture.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.55f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))),
        )

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            val meta = listOfNotNull(item.typeLabel.takeIf { it.isNotBlank() }, item.dateLabel).joinToString(" · ").persianDigits()
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f), maxLines = 1)
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        overlay()
    }
}

/** Without a picture: a calm tinted surface with the announcement illustration. */
@Composable
private fun NoImage() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painterResource(R.drawable.ic_flat_announcement),
            contentDescription = null,
            modifier = Modifier.padding(top = 18.dp).size(88.dp),
        )
    }
}
