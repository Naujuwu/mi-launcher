package com.mio.launcher

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max

internal fun HomeView.drawPill(c: Canvas) {
    val n = store.pages.size + 1
    val label = "Search"

    val tw = searchText.measureText(label)

    val iconW = dp(11f)
    val gap = dp(5f)
    val padH = dp(24f)

    val searchW = iconW + gap + tw + padH

    val dotGap = dp(10f)
    val dr = dp(3f)

    val dotsW = max(
        searchW,
        (n - 1) * dotGap + 2f * dr + padH
    )

    val pw = searchW + (dotsW - searchW) * dotsMix

    val cx = width / 2f
    val cy = pillTop + pillH / 2f

    pillRect.set(
        cx - pw / 2f,
        pillTop,
        cx + pw / 2f,
        pillTop + pillH
    )

    glassPaint.shader = null
    glassPaint.color = 0x33FFFFFF

    c.drawRoundRect(
        pillRect,
        pillH / 2f,
        pillH / 2f,
        glassPaint
    )

    val half = pillBorder.strokeWidth / 2f
    pillRect.inset(half, half)

    c.drawRoundRect(
        pillRect,
        pillRect.height() / 2f,
        pillRect.height() / 2f,
        pillBorder
    )

    val sa = ((1f - dotsMix) * 255f).toInt()

    if (sa > 0) {
        searchText.alpha = sa
        magPaint.alpha = sa

        val r = dp(4.4f)

        val startX = cx - (iconW + gap + tw) / 2f

        // Lupa centrada en vertical (círculo + mango)
        val mx = startX + r
        val my = cy - r * 0.25f

        c.drawCircle(mx, my, r, magPaint)

        c.drawLine(
            mx + r * 0.72f,
            my + r * 0.72f,
            mx + r * 1.5f,
            my + r * 1.5f,
            magPaint
        )

        // Texto centrado por la altura de sus mayúsculas
        searchText.getTextBounds("S", 0, 1, lib.tmp)

        c.drawText(
            label,
            startX + iconW + gap,
            cy + lib.tmp.height() / 2f,
            searchText
        )
    }

    if (dotsMix > 0.01f && n > 1) {
        val x0 = cx - ((n - 1) * dotGap) / 2f

        val active = Math.round(
            currentPage - pageOffset / width
        )

        for (i in 0 until n) {
            val baseA = if (i == active) 255 else 110

            dotPaint.color = Color.argb(
                (baseA * dotsMix).toInt(),
                255,
                255,
                255
            )

            c.drawCircle(
                x0 + i * dotGap,
                cy,
                dr,
                dotPaint
            )
        }
    }
}

internal fun HomeView.drawDock(c: Canvas) {
    val rad = dp(38f)

    glassPaint.color = Color.WHITE
    glassPaint.shader = dockShader

    c.drawRoundRect(
        dockRect,
        rad,
        rad,
        glassPaint
    )

    glassPaint.shader = null

    c.drawRoundRect(
        dockRect,
        rad,
        rad,
        borderPaint
    )

    for (i in 0 until 4) {
        val slot = store.dock[i] ?: continue

        if (
            dragPkg != null &&
            srcInDock &&
            srcIndex == i
        ) continue

        val cx = dockLeft + (i + 0.5f) * dockPitch

        drawSlot(
            c,
            slot,
            cx - iconSize / 2f,
            dockRect.centerY() - iconSize / 2f,
            iconSize,
            false
        )
    }
}

// Notificaciones
