package com.mio.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

internal fun HomeView.drawFolderIcon(
    c: Canvas,
    slot: String,
    left: Float,
    top: Float,
    size: Float
) {
    val folder = store.getFolderFromSlot(slot) ?: return
    val rect = RectF(left, top, left + size, top + size)
    val radius = size * 0.25f

    folderIconBgPaint.shader = LinearGradient(
        left,
        top,
        left + size,
        top + size,
        intArrayOf(
            0xDDF4FFFF.toInt(),
            0xCDE5FFFF.toInt(),
            0xBBD5F5FF.toInt()
        ),
        null,
        Shader.TileMode.CLAMP
    )

    c.drawPath(
        squirclePath(left, top, size),
        folderIconBgPaint
    )

    folderIconBgPaint.shader = null

    c.drawPath(
        squirclePath(left, top, size),
        folderIconBorderPaint
    )

    val padding = size * 0.13f
    val gap = size * 0.055f
    val mini = (size - padding * 2f - gap * 2f) / 3f

    for (i in 0 until min(9, folder.apps.size)) {
        val app = apps[folder.apps[i]] ?: continue
        val col = i % 3
        val row = i / 3
        val x = left + padding + col * (mini + gap)
        val y = top + padding + row * (mini + gap)

        c.drawBitmap(
            app.icon,
            null,
            RectF(x, y, x + mini, y + mini),
            folderIconPaint
        )
    }

    val total = if (NotifService.dnd) 0 else {
        folder.apps.sumOf { pkg ->
            (NotifService.counts[pkg] ?: 0).coerceAtLeast(0)
        }
    }

    if (total > 0) {
        drawBadgeValue(c, total, left, top, size)
    }
}

// Geometría del panel abierto

internal fun HomeView.folderCount(): Int =
    openFolderId?.let { store.folders[it]?.apps?.size } ?: 0

// El panel siempre es de 3x3, como en iOS 26
internal fun HomeView.folderRows(): Int = 3

internal fun HomeView.folderIconSize(): Float = folderPanel.width() * 0.195f

internal fun HomeView.folderCellH(): Float = folderPanel.width() * 0.3215f

internal fun HomeView.folderGridTop(): Float =
    folderPanel.top + folderPanel.width() * 0.085f

internal fun HomeView.folderGridBottom(): Float =
    folderGridTop() + folderRows() * folderCellH()

internal fun HomeView.folderPanelGeometry() {
    val panelW = min(width * 0.84f, dp(440f))
    val panelH = panelW * 1.07f

    val left = (width - panelW) / 2f
    val top = height * 0.54f - panelH / 2f

    folderPanel.set(
        left,
        top,
        left + panelW,
        top + panelH
    )
}

// Dibujo de carpeta abierta

internal fun HomeView.drawOpenFolder(c: Canvas) {
    val id = openFolderId ?: return
    val folder = store.folders[id] ?: return

    folderPanelGeometry()

    c.drawRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        folderOverlayPaint
    )

    folderTitlePaint.textSize = width * 0.083f
    folderLabelPaint.textSize = folderPanel.width() * 0.037f

    folderPanelPaint.shader = LinearGradient(
        folderPanel.left,
        folderPanel.top,
        folderPanel.right,
        folderPanel.bottom,
        intArrayOf(
            0x5CFFFFFF,
            0x45FFFFFF,
            0x52FFFFFF
        ),
        null,
        Shader.TileMode.CLAMP
    )

    c.drawPath(
        squirclePath(
            folderPanel.left,
            folderPanel.top,
            folderPanel.width(),
            9.0,
            1.07f
        ),
        folderPanelPaint
    )

    folderPanelPaint.shader = null

    c.drawPath(
        squirclePath(
            folderPanel.left,
            folderPanel.top,
            folderPanel.width(),
            9.0,
            1.07f
        ),
        folderPanelBorderPaint
    )

    val pageCount = max(1, (folder.apps.size + 8) / 9)

    if (renameEdit == null) {
        c.drawText(
            folder.name,
            width / 2f,
            folderPanel.top - folderPanel.width() * 0.20f,
            folderTitlePaint
        )
    }

    val gridTop = folderGridTop()

    val cellW = folderPanel.width() * 0.30f
    val cellH = folderCellH()

    val icon = folderIconSize()

    c.save()
    c.clipPath(
        squirclePath(
            folderPanel.left,
            folderPanel.top,
            folderPanel.width(),
            9.0,
            1.07f
        )
    )

    for (
        page in max(0, folderPage - 1)..
            min(pageCount - 1, folderPage + 1)
    ) {
        val translation =
            (page - folderPage) * folderPanel.width() +
                folderPageOffset

        c.save()
        c.translate(translation, 0f)

        for (i in 0 until 9) {
            val appIndex = page * 9 + i

            if (appIndex >= folder.apps.size) break

            val pkg = folder.apps[appIndex]
            val app = apps[pkg] ?: continue

            if (pkg == folderDragPkg) continue

            val col = i % 3
            val row = i / 3

            val cx = folderPanel.left +
                folderPanel.width() * 0.20f +
                col * cellW

            val left = cx - icon / 2f
            val top = gridTop + row * cellH

            c.drawBitmap(
                app.icon,
                null,
                RectF(
                    left,
                    top,
                    left + icon,
                    top + icon
                ),
                iconPaint
            )

            drawBadge(c, pkg, left, top, icon)

            c.drawText(
                fitFolderLabel(
                    app.label,
                    cellW - dp(6f)
                ),
                cx,
                top + icon + folderPanel.width() * 0.052f,
                folderLabelPaint
            )
        }

        c.restore()
    }

    c.restore()

    if (pageCount > 1) {
        val dotGap = dp(12f)

        val startX = folderPanel.centerX() -
            (pageCount - 1) * dotGap / 2f

        for (i in 0 until pageCount) {
            folderDotPaint.color =
                if (i == folderPage) {
                    Color.WHITE
                } else {
                    0x66FFFFFF
                }

            c.drawCircle(
                startX + i * dotGap,
                folderPanel.bottom - folderPanel.width() * 0.045f,
                dp(3.5f),
                folderDotPaint
            )
        }
    }

    val dragged = folderDragPkg

    if (dragged != null) {
        val app = apps[dragged]

        if (app != null) {
            val size = folderIconSize()

            c.save()
            c.scale(1.15f, 1.15f, dragX, dragY)

            c.drawBitmap(
                app.icon,
                null,
                RectF(
                    dragX - size / 2f,
                    dragY - size / 2f,
                    dragX + size / 2f,
                    dragY + size / 2f
                ),
                iconPaint
            )

            c.restore()
        }
    }
}

internal fun HomeView.fitFolderLabel(
    label: String,
    maxWidth: Float
): String {
    if (folderLabelPaint.measureText(label) <= maxWidth) {
        return label
    }

    var result = label

    while (
        result.isNotEmpty() &&
        folderLabelPaint.measureText(result + "…") > maxWidth
    ) {
        result = result.dropLast(1)
    }

    return result + "…"
}

internal fun HomeView.folderCellAt(x: Float, y: Float): Int {
    val id = openFolderId ?: return -1
    val folder = store.folders[id] ?: return -1

    folderPanelGeometry()

    if (!folderPanel.contains(x, y)) return -1

    val gridTop = folderGridTop()
    val gridBottom = folderGridBottom()

    if (y < gridTop || y >= gridBottom) return -1

    val pitch = folderPanel.width() * 0.30f

    val col = Math.floor(
        ((x - folderPanel.left -
            folderPanel.width() * 0.20f + pitch / 2f) / pitch).toDouble()
    ).toInt()

    val row = ((y - gridTop) /
        folderCellH()).toInt()

    if (col !in 0..2 || row !in 0 until folderRows()) return -1

    val index = folderPage * 9 + row * 3 + col

    return if (index in folder.apps.indices) index else -1
}

internal fun HomeView.folderTitleArea(): RectF = RectF(
    folderPanel.left,
    folderPanel.top - folderPanel.width() * 0.40f,
    folderPanel.right,
    folderPanel.top - dp(4f)
)
