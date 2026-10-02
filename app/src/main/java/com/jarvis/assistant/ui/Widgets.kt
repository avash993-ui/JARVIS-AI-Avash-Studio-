package com.jarvis.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Cyan line icon on a dark tile: the interface icon style from the approved design. */
@Composable
fun IconTile(icon: ImageVector, desc: String, onClick: () -> Unit, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(TileBg)
            .border(BorderStroke(1.dp, TileBorder), RoundedCornerShape(14.dp))
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Cyan, modifier = Modifier.size(size * 0.5f)) }
}

/** Level badge: rings count / tick / halo per level, exactly as designed (1..6). */
@Composable
fun LevelIcon(n: Int, color: Color, size: Dp) {
    val rings = intArrayOf(1, 2, 3, 3, 4, 5)[n - 1]
    Canvas(Modifier.size(size)) {
        val sc = this.size.minDimension / 100f
        drawRoundRect(TileBg, cornerRadius = CornerRadius(22f * sc), size = this.size)
        val c = Offset(50f * sc, 50f * sc)
        if (n == 6) drawCircle(color.copy(alpha = .35f), 46f * sc, c, style = Stroke(1.5f * sc))
        for (k in 0 until rings) {
            val r = 13f + k * (if (rings > 4) 7f else 8.5f)
            val last = k == rings - 1 && rings > 1
            val dash = when {
                last -> PathEffect.dashPathEffect(floatArrayOf(20f * sc, 5f * sc))
                n == 4 && k == 1 -> PathEffect.dashPathEffect(floatArrayOf(3f * sc, 6f * sc, 22f * sc, 6f * sc))
                else -> null
            }
            drawCircle(color, r * sc, c, style = Stroke((if (k == 0) 2.6f else 1.9f) * sc, pathEffect = dash))
        }
        drawCircle(Color(0xFFE0FFFF), 4.5f * sc, c)
    }
}
