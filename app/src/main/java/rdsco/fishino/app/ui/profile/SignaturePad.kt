package rdsco.fishino.app.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/** Strokes drawn with a finger, in the pad's own pixels. */
class SignatureStrokes {
    val strokes: SnapshotStateList<List<Offset>> = mutableStateListOf()
    internal var current: SnapshotStateList<Offset> = mutableStateListOf()
    internal var size: IntSize = IntSize.Zero
    internal var strokePx: Float = 8f

    val isEmpty: Boolean get() = strokes.none { it.size > 1 } && current.size < 2

    fun clear() {
        strokes.clear()
        current = mutableStateListOf()
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    /**
     * The drawing as a transparent PNG, scaled to [width] pixels wide, as the
     * web's signature pad produces it.
     */
    fun toPng(width: Int = 1000): ByteArray? {
        if (size.width <= 0 || size.height <= 0 || isEmpty) return null
        val scale = width.toFloat() / size.width
        val bitmap = Bitmap.createBitmap(width, (size.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = strokePx * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.filter { it.size > 1 }.forEach { points ->
            val path = android.graphics.Path()
            path.moveTo(points[0].x * scale, points[0].y * scale)
            points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
            canvas.drawPath(path, paint)
        }

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }
}

@Composable
fun rememberSignatureStrokes(): SignatureStrokes = remember { SignatureStrokes() }

/** A white pad to draw the signature on. */
@Composable
fun SignaturePad(strokes: SignatureStrokes, enabled: Boolean, modifier: Modifier = Modifier) {
    val ink = Color(0xFF111111)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.2f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .onSizeChanged { strokes.size = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        strokes.current = mutableStateListOf(start)
                        strokes.strokes.add(strokes.current)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        strokes.current.add(change.position)
                    },
                )
            },
    ) {
        strokes.strokePx = 3.2.dp.toPx()
        strokes.strokes.forEach { points ->
            if (points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, ink, style = Stroke(width = strokes.strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
