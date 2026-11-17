package com.naveen.pocosimple

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Logo B: a signal tower with waves on both sides. */
@Composable
fun SignalLogo(logoSize: Dp, color: Color) {
    Canvas(modifier = Modifier.size(logoSize)) {
        val s = size.minDimension
        val cx = size.width / 2f
        val top = size.height * 0.30f
        val bottom = size.height * 0.92f
        val stroke = s * 0.07f

        drawLine(
            color = color,
            start = Offset(cx, top),
            end = Offset(cx, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx - s * 0.12f, bottom),
            end = Offset(cx + s * 0.12f, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(color = color, radius = s * 0.08f, center = Offset(cx, top))

        for (i in 1..3) {
            val r = s * (0.16f + 0.12f * i)
            val alpha = 1f - (i - 1) * 0.25f
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = 150f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - r, top - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = -30f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - r, top - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round)
            )
        }
    }
}
