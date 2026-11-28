package com.mio.launcher

import android.graphics.Canvas
import kotlin.math.max

internal fun HomeView.drawBadge(
    c: Canvas,
    pkg: String,
    iconLeft: Float,
    iconTop: Float,
    size: Float = iconSize
) {
    if (NotifService.dnd) return

    val count = NotifService.counts[pkg] ?: return
    if (count <= 0) return

    drawBadgeValue(c, count, iconLeft, iconTop, size)
}

internal fun HomeView.drawBadgeValue(
    c: Canvas,
    count: Int,
    iconLeft: Float,
    iconTop: Float,
    size: Float
) {
    val label = if (count > 999) "999+" else count.toString()
    val h = size * 0.40f

    val oldTextSize = badgeText.textSize
    badgeText.textSize = size * 0.248f

    val tw = badgeText.measureText(label)
    val w = max(h, tw + h * 0.62f)

    val cx = iconLeft + size - size * 0.02f
    val cy = iconTop + size * 0.06f

    badgeRect.set(
        cx - w / 2f,
        cy - h / 2f,
        cx + w / 2f,
        cy + h / 2f
    )

    c.drawRoundRect(
        badgeRect,
        h / 2f,
        h / 2f,
        badgePaint
    )

    val fm = badgeText.fontMetrics
    val baseline = badgeRect.centerY() -
        (fm.ascent + fm.descent) / 2f

    c.drawText(
        label,
        badgeRect.centerX(),
        baseline,
        badgeText
    )

    badgeText.textSize = oldTextSize
}
