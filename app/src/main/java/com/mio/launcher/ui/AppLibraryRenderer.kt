
package com.mio.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object AppLibraryRenderer {

    private fun dp(value: Float, density: Float) = value * density

    private fun paint(color: Int, size: Float, density: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = dp(size, density)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

    fun draw(canvas: Canvas, width: Float, height: Float, density: Float) {
        val c = canvas
        val white = Color.WHITE
        val muted = 0xD9FFFFFF.toInt()

        val titlePaint = paint(white, 34f, density).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val sectionPaint = paint(white, 19f, density).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val labelPaint = paint(white, 12f, density)
        val smallPaint = paint(muted, 10f, density)
        val searchPaint = paint(0xEFFFFFFF.toInt(), 15f, density)

        val side = dp(20f, density)
        val gap = dp(12f, density)
        val cardW = (width - side * 2f - gap) / 2f
        val top = dp(58f, density)

        // Título
        c.drawText("Biblioteca de apps", side, top, titlePaint)

        // Buscador
        val searchTop = top + dp(22f, density)
        val searchRect = RectF(
            side, searchTop,
            width - side, searchTop + dp(48f, density)
        )
        val searchBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
        }
        c.drawRoundRect(searchRect, dp(16f, density), dp(16f, density), searchBg)

        val searchIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            style = Paint.Style.STROKE
            strokeWidth = dp(1.8f, density)
        }
        val sx = side + dp(20f, density)
        val sy = searchRect.centerY()
        c.drawCircle(sx, sy - dp(1f, density), dp(6f, density), searchIcon)
        c.drawLine(
            sx + dp(4.5f, density), sy + dp(4f, density),
            sx + dp(9f, density), sy + dp(8.5f, density),
            searchIcon
        )
        c.drawText("Buscar", sx + dp(19f, density), sy + dp(5f, density), searchPaint)

        // Sugerencias
        val suggestedY = searchRect.bottom + dp(31f, density)
        c.drawText("Sugerencias", side, suggestedY, sectionPaint)

        val suggestedTop = suggestedY + dp(13f, density)
        val suggestedRect = RectF(
            side, suggestedTop,
            width - side, suggestedTop + dp(112f, density)
        )
        drawGlassCard(c, suggestedRect, density)

        val suggestions = listOf(
            "Fotos", "Cámara", "Ajustes", "Chrome"
        )
        val symbols = listOf("✿", "◉", "⚙", "◎")
        val iconColors = intArrayOf(
            0xFFFFC45C.toInt(),
            0xFF75B9F2.toInt(),
            0xFF9CA8B5.toInt(),
            0xFF75C98A.toInt()
        )

        val itemW = suggestedRect.width() / 4f
        for (i in suggestions.indices) {
            val cx = suggestedRect.left + itemW * (i + 0.5f)
            val cy = suggestedRect.top + dp(37f, density)
            drawAppIcon(c, cx, cy, dp(27f, density), iconColors[i], symbols[i], density)
            labelPaint.textAlign = Paint.Align.CENTER
            c.drawText(
                suggestions[i],
                cx,
                cy + dp(49f, density),
                labelPaint
            )
        }

        // Categorías
        val categoriesY = suggestedRect.bottom + dp(34f, density)
        c.drawText("Categorías", side, categoriesY, sectionPaint)

        val cardTop = categoriesY + dp(14f, density)
        val cardH = dp(132f, density)
        val rowGap = dp(12f, density)

        val categories = listOf(
            Category("Productividad", "Documentos y herramientas", intArrayOf(
                0xFF76A9F7.toInt(), 0xFF8D9CF5.toInt(), 0xFF7BC9B0.toInt()
            ), listOf("▤", "✓", "⌘")),
            Category("Redes sociales", "Comunicación y comunidad", intArrayOf(
                0xFFEF8DB2.toInt(), 0xFF8C9FF0.toInt(), 0xFFF0A16D.toInt()
            ), listOf("♡", "◎", "➤")),
            Category("Entretenimiento", "Música, vídeo y juegos", intArrayOf(
                0xFF9C8CEB.toInt(), 0xFFE18DA9.toInt(), 0xFF75B9E8.toInt()
            ), listOf("♫", "▶", "★")),
            Category("Utilidades", "Apps para el día a día", intArrayOf(
                0xFF8BB8A0.toInt(), 0xFF8EA8C9.toInt(), 0xFFE0B878.toInt()
            ), listOf("⌁", "◷", "⌕"))
        )

        categories.forEachIndexed { index, category ->
            val col = index % 2
            val row = index / 2
            val left = side + col * (cardW + gap)
            val y = cardTop + row * (cardH + rowGap)
            val rect = RectF(left, y, left + cardW, y + cardH)

            drawGlassCard(c, rect, density)

            val inset = dp(13f, density)
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            labelPaint.textSize = dp(13f, density)
            c.drawText(category.title, left + inset, y + dp(25f, density), labelPaint)

            smallPaint.textAlign = Paint.Align.LEFT
            smallPaint.textSize = dp(9.5f, density)
            c.drawText(category.subtitle, left + inset, y + dp(43f, density), smallPaint)

            val iconY = y + dp(88f, density)
            category.symbols.forEachIndexed { i, symbol ->
                val cx = left + inset + dp(22f, density) +
                    i * dp(43f, density)
                drawAppIcon(
                    c, cx, iconY, dp(17f, density),
                    category.colors[i], symbol, density
                )
            }
        }
    }

    private data class Category(
        val title: String,
        val subtitle: String,
        val colors: IntArray,
        val symbols: List<String>
    )

    private fun drawGlassCard(c: Canvas, rect: RectF, density: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38FFFFFF
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x42FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = dp(0.8f, density)
        }
        val radius = dp(20f, density)
        c.drawRoundRect(rect, radius, radius, bg)
        c.drawRoundRect(rect, radius, radius, border)
    }

    private fun drawAppIcon(
        c: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        symbol: String,
        density: Float
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        c.drawRoundRect(
            RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            radius * 0.32f, radius * 0.32f, bg
        )

        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = radius * 1.05f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val baseline = cy - (glyph.ascent() + glyph.descent()) / 2f
        c.drawText(symbol, cx, baseline, glyph)
    }
}
