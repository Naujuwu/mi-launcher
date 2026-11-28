package com.mio.launcher

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.mio.launcher.ui.IconRenderer

internal fun HomeView.drawHome(canvas: Canvas) {
    val libIndex = store.pages.size

    for (p in (currentPage - 1)..(currentPage + 1)) {
        if (p < 0 || p > libIndex) continue

        val offX = (p - currentPage) * width + pageOffset

        if (offX <= -width || offX >= width) continue

        if (p == libIndex) {
            drawLibrary(canvas, offX)
        } else {
            drawPage(canvas, p, offX)
        }
    }

    drawPill(canvas)

    // El dock se desvanece al entrar en la biblioteca
    val dockAlpha = 1f - libProgress()

    if (dockAlpha > 0.99f) {
        drawDock(canvas)
    } else if (dockAlpha > 0.01f) {
        val sc = canvas.saveLayerAlpha(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            (255 * dockAlpha).toInt()
        )

        drawDock(canvas)
        canvas.restoreToCount(sc)
    }

    drawFolderTarget(canvas)
    drawDragged(canvas)
}

internal fun HomeView.fit(label: String, maxW: Float): String {
    if (labelPaint.measureText(label) <= maxW) {
        return label
    }

    var s = label

    while (
        s.isNotEmpty() &&
        labelPaint.measureText(s + "…") > maxW
    ) {
        s = s.dropLast(1)
    }

    return s + "…"
}

internal fun HomeView.drawPage(
    c: Canvas,
    p: Int,
    offX: Float
) {
    val cells = store.pages[p]

    for (i in 0 until perPage) {
        val slot = cells[i] ?: continue

        if (
            dragPkg != null &&
            !srcInDock &&
            srcPage == p &&
            srcIndex == i
        ) continue

        val col = i % cols
        val row = i / cols

        val cx = offX + gridLeft + (col + 0.5f) * cellW

        val top = gridTop +
            row * cellH +
            (cellH - iconSize - dp(18f)) / 2f

        drawSlot(
            c,
            slot,
            cx - iconSize / 2f,
            top,
            iconSize,
            true
        )
    }
}

internal fun HomeView.drawSlot(
    c: Canvas,
    slot: String,
    left: Float,
    top: Float,
    size: Float,
    showLabel: Boolean
) {
    if (store.isFolderSlot(slot)) {
        drawFolderIcon(c, slot, left, top, size)

        if (showLabel) {
            val folder = store.getFolderFromSlot(slot)
            val name = folder?.name ?: "Carpeta"

            c.drawText(
                fit(name, cellW - dp(6f)),
                left + size / 2f,
                top + size + dp(17f),
                labelPaint
            )
        }
        return
    }

    val app = apps[slot] ?: return

    c.drawBitmap(
        app.icon,
        null,
        RectF(left, top, left + size, top + size),
        iconPaint
    )

    if (size >= iconSize * 0.8f) {
        drawBadge(c, slot, left, top)
    }

    if (showLabel) {
        val shown = app.shown ?: fit(
            app.label,
            cellW - dp(6f)
        ).also {
            app.shown = it
        }

        c.drawText(
            shown,
            left + size / 2f,
            top + size + dp(17f),
            labelPaint
        )
    }
}

// Icono cerrado de carpeta: cuadrícula 3x3

// Squircle: n=5 para iconos, n=9 (esquinas más cerradas) para el panel
internal fun HomeView.squirclePath(
    left: Float,
    top: Float,
    size: Float,
    n: Double = 5.0,
    stretchY: Float = 1f
): Path {
    if (sqBase == null || sqBaseSize != size || sqBaseN != n) {
        sqBase = IconRenderer.buildSquircle(size, n)
        sqBaseSize = size
        sqBaseN = n
    }

    sqMatrix.setScale(1f, stretchY)
    sqMatrix.postTranslate(left, top)

    sqTmp.reset()
    sqTmp.addPath(sqBase!!, sqMatrix)

    return sqTmp
}

internal fun HomeView.drawDragged(c: Canvas) {
    val slot = dragPkg ?: return

    c.save()

    c.scale(
        1.15f,
        1.15f,
        dragX,
        dragY
    )

    if (store.isFolderSlot(slot)) {
        drawFolderIcon(
            c,
            slot,
            dragX - iconSize / 2f,
            dragY - iconSize / 2f - dp(8f),
            iconSize
        )
    } else {
        val app = apps[slot] ?: return

        iconPaint.alpha = 225

        c.drawBitmap(
            app.icon,
            dragX - iconSize / 2f,
            dragY - iconSize / 2f - dp(8f),
            iconPaint
        )

        iconPaint.alpha = 255
    }

    c.restore()
}

// Eventos táctiles
