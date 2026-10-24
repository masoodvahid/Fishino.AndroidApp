package rdsco.fishino.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rdsco.fishino.app.ui.theme.LocalDarkTheme

/**
 * The employer's logo at [height]. Logos are drawn for a white page, so in
 * dark mode they sit on a light tile. Nothing is drawn without a logo.
 */
@Composable
fun EmployerLogoImage(image: ImageBitmap?, contentDescription: String?, height: Dp, modifier: Modifier = Modifier, maxWidth: Dp = height * 3) {
    if (image == null) return
    val dark = LocalDarkTheme.current
    val tile = if (dark) {
        Modifier.clip(RoundedCornerShape(height / 5)).background(Color.White.copy(alpha = .94f)).padding(height / 12)
    } else {
        Modifier
    }

    Image(
        bitmap = image,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.then(tile).height(height).widthIn(max = maxWidth),
    )
}

/** The confirm screen's large mark: the logo, or the name's first letter when there is none. */
@Composable
fun EmployerLogoMark(name: String, image: ImageBitmap?, modifier: Modifier = Modifier) {
    if (image != null) {
        EmployerLogoImage(image, contentDescription = name, height = 110.dp, modifier = modifier.heightIn(max = 130.dp), maxWidth = 260.dp)
        return
    }

    Box(
        modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.toString().orEmpty(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
