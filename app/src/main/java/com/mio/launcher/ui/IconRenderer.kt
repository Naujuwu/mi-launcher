package com.mio.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.sign

object IconRenderer {

    /** Forma "squircle" de iOS (superelipse). Con n = 5 coincide con la silueta de iOS. */
    fun buildSquircle(size: Float, n: Double = 5.0): Path {
        val path = Path()
        val half = size / 2f
        val steps = 360
        for (i in 0 until steps) {
            val t = 2.0 * Math.PI * i / steps
            val ct = Math.cos(t)
            val st = Math.sin(t)
            val x = sign(ct) * absPow(ct, 2.0 / n)
            val y = sign(st) * absPow(st, 2.0 / n)
            val px = (half + x * half).toFloat()
            val py = (half + y * half).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun absPow(v: Double, exp: Double): Double =
        Math.pow(Math.abs(v), exp)

    /**
     * Devuelve el color de fondo que hace falta detrás del icono,
     * o null si ya llena la forma.
     */
    fun backingColor(px: IntArray): Int? {
        val counts = IntArray(4096)
        val sumR = LongArray(4096)
        val sumG = LongArray(4096)
        val sumB = LongArray(4096)
        var opaque = 0
        var lumSum = 0.0
        for (c in px) {
            if ((c ushr 24) < 200) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            counts[k]++
            sumR[k] += r.toLong()
            sumG[k] += g.toLong()
            sumB[k] += b.toLong()
            lumSum += 0.299 * r + 0.587 * g + 0.114 * b
            opaque++
        }
        if (opaque == 0) return 0xFFF2F2F7.toInt()
        val frac = opaque.toFloat() / px.size
        if (frac >= 0.97f) return null                  // ya llena toda la forma
        if (frac >= 0.60f) {
            // tiene fondo propio: extenderlo con su color dominante
            var best = 0
            for (k in counts.indices) if (counts[k] > counts[best]) best = k
            val n = counts[best].toLong()
            return Color.rgb(
                (sumR[best] / n).toInt(),
                (sumG[best] / n).toInt(),
                (sumB[best] / n).toInt()
            )
        }
        // solo un símbolo sobre transparente: fondo con buen contraste
        val avgLum = lumSum / opaque
        return if (avgLum > 150.0) 0xFF1C1C1E.toInt() else 0xFFF2F2F7.toInt()
    }

    fun roundedIcon(d: Drawable, iconPx: Int, squircle: Path): Bitmap {
        val s = iconPx
        val raw = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val rc = Canvas(raw)
        if (d is AdaptiveIconDrawable) {
            // las capas miden 108dp y solo se ven los 72dp centrales: ampliar para llenar
            val pad = (s * 0.25f).toInt()
            d.background?.let {
                it.setBounds(-pad, -pad, s + pad, s + pad)
                it.draw(rc)
            }
            d.foreground?.let {
                it.setBounds(-pad, -pad, s + pad, s + pad)
                it.draw(rc)
            }
        } else {
            d.setBounds(0, 0, s, s)
            d.draw(rc)
        }

        val px = IntArray(s * s)
        raw.getPixels(px, 0, s, 0, 0, s, s)
        val back = backingColor(px)

        val composed = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cc = Canvas(composed)
        if (back != null) cc.drawColor(back)
        cc.drawBitmap(raw, 0f, 0f, null)
        raw.recycle()

        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(composed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        Canvas(out).drawPath(squircle, p)
        composed.recycle()
        return out
    }
}
