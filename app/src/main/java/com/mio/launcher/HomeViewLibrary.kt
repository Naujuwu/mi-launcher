package com.mio.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max

// Tipos de toque dentro de la biblioteca
internal const val LIB_APP = 0
internal const val LIB_GROUP = 1
internal const val LIB_BACK = 2
internal const val LIB_SEARCH = 3

// Clave especial: lista con todas las apps
internal const val LIB_ALL = "@all"

private val LIB_ORDER = listOf(
    "Social",
    "Productividad",
    "Creatividad",
    "Entretenimiento",
    "Juegos",
    "Información y lectura",
    "Viajes",
    "Utilidades",
    "Otros"
)

private val SOCIAL_WORDS = listOf(
    "whatsapp", "telegram", "facebook", "instagram", "tiktok",
    "snapchat", "twitter", "discord", "messenger", "signal",
    "reddit", "linkedin", "pinterest", "threads", "bluesky"
)

private val ENTERTAINMENT_WORDS = listOf(
    "youtube", "spotify", "netflix", "music", "video", "disney",
    "hbo", "twitch", "podcast", "radio", "deezer", "soundcloud"
)

private val CREATIVE_WORDS = listOf(
    "camera", "gallery", "photo", "snapseed", "editor", "canva",
    "picsart", "capcut", "sketch", "draw", "paint"
)

private val PRODUCTIVITY_WORDS = listOf(
    "docs", "drive", "gmail", "mail", "calendar", "office", "word",
    "excel", "powerpoint", "notes", "keep", "onenote", "notion",
    "todo", "outlook", "teams", "zoom", "sheets", "slides", "pdf"
)

private val READING_WORDS = listOf(
    "news", "reader", "books", "kindle", "wikipedia", "weather",
    "pocket", "medium"
)

private val TRAVEL_WORDS = listOf(
    "maps", "uber", "didi", "cabify", "airbnb", "booking",
    "travel", "flight", "waze", "taxi", "transit"
)

private val SYSTEM_PREFIXES = listOf(
    "com.android.", "com.google.android.", "com.samsung.", "com.sec."
)

internal class LibGroup(
    val name: String,
    val pkgs: List<String>
)

internal class LibHit(
    val rect: RectF,
    val kind: Int,
    val key: String?
)

internal class LibraryState(density: Float) {
    var groups: List<LibGroup> = emptyList()

    // null = vista general, LIB_ALL = todas las apps, otro = nombre de grupo
    var open: String? = null

    var scroll = 0f
    var savedScroll = 0f
    var maxScroll = 0f
    var lastY = 0f

    var velocity: VelocityTracker? = null
    var fling: ValueAnimator? = null

    val hits = ArrayList<LibHit>()
    val catCache = HashMap<String, String>()
    val tmp = Rect()

    val dim = Paint().apply {
        color = 0x38000000
    }

    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
    }

    val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = density
    }

    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
    }

    val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        textSize = 17f * density
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f * density
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setShadowLayer(2f * density, 0f, 0.5f * density, 0x73000000)
    }
}

// ───────────── Estado y datos ─────────────

internal fun HomeView.libOnPage(): Boolean =
    currentPage == store.pages.size

internal fun HomeView.libProgress(): Float {
    if (width <= 0) return 0f

    val pos = currentPage - pageOffset / width

    return (pos - (store.pages.size - 1)).coerceIn(0f, 1f)
}

internal fun HomeView.libReset() {
    lib.fling?.cancel()
    lib.open = null
    lib.scroll = 0f
    lib.savedScroll = 0f
}

internal fun HomeView.libOpenGroup(key: String) {
    lib.fling?.cancel()
    lib.savedScroll = lib.scroll
    lib.open = key
    lib.scroll = 0f
    invalidate()
}

internal fun HomeView.libCloseGroup() {
    lib.fling?.cancel()
    lib.open = null
    lib.scroll = lib.savedScroll
    invalidate()
}

private fun HomeView.libPrefs() =
    act.getSharedPreferences("library", Context.MODE_PRIVATE)

private fun HomeView.libRecents(): List<String> =
    (libPrefs().getString("recent", "") ?: "")
        .split(",")
        .filter { it.isNotEmpty() }

internal fun HomeView.libPushRecent(pkg: String) {
    val list = libRecents().toMutableList()

    list.remove(pkg)
    list.add(0, pkg)

    libPrefs()
        .edit()
        .putString("recent", list.take(8).joinToString(","))
        .apply()

    libBuild()
}

private fun libHas(p: String, words: List<String>): Boolean =
    words.any { p.contains(it) }

@Suppress("DEPRECATION")
private fun HomeView.libCategoryOf(pkg: String): String {
    lib.catCache[pkg]?.let { return it }

    val info = try {
        act.packageManager.getApplicationInfo(pkg, 0)
    } catch (e: Exception) {
        null
    }

    val cat = info?.category ?: ApplicationInfo.CATEGORY_UNDEFINED

    val isGame = info != null &&
        (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    val p = pkg.lowercase()

    val result = when {
        isGame || cat == ApplicationInfo.CATEGORY_GAME -> "Juegos"
        cat == ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        cat == ApplicationInfo.CATEGORY_AUDIO ||
            cat == ApplicationInfo.CATEGORY_VIDEO -> "Entretenimiento"
        cat == ApplicationInfo.CATEGORY_IMAGE -> "Creatividad"
        cat == ApplicationInfo.CATEGORY_NEWS -> "Información y lectura"
        cat == ApplicationInfo.CATEGORY_MAPS -> "Viajes"
        cat == ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productividad"
        libHas(p, SOCIAL_WORDS) -> "Social"
        libHas(p, ENTERTAINMENT_WORDS) -> "Entretenimiento"
        libHas(p, CREATIVE_WORDS) -> "Creatividad"
        libHas(p, PRODUCTIVITY_WORDS) -> "Productividad"
        libHas(p, READING_WORDS) -> "Información y lectura"
        libHas(p, TRAVEL_WORDS) -> "Viajes"
        SYSTEM_PREFIXES.any { p.startsWith(it) } -> "Utilidades"
        else -> "Otros"
    }

    lib.catCache[pkg] = result

    return result
}

internal fun HomeView.libBuild() {
    val buckets = LinkedHashMap<String, MutableList<String>>()

    for (name in LIB_ORDER) {
        buckets[name] = mutableListOf()
    }

    for (pkg in apps.keys) {
        buckets.getValue(libCategoryOf(pkg)).add(pkg)
    }

    val out = ArrayList<LibGroup>()

    val recents = libRecents()
        .filter { apps.containsKey(it) }
        .take(4)

    if (recents.isNotEmpty()) {
        out.add(LibGroup("Recientes", recents))
    }

    for ((name, list) in buckets) {
        if (list.isNotEmpty()) {
            out.add(LibGroup(name, list))
        }
    }

    lib.groups = out
    invalidate()
}

// ───────────── Toques ─────────────

internal fun HomeView.libTouchDown(e: MotionEvent) {
    lib.fling?.cancel()
    lib.velocity?.recycle()

    lib.velocity = VelocityTracker.obtain().also {
        it.addMovement(e)
    }
}

internal fun HomeView.libTrack(e: MotionEvent) {
    lib.velocity?.addMovement(e)
}

internal fun HomeView.libScrollTo(y: Float) {
    val dy = y - lib.lastY
    lib.lastY = y

    lib.scroll = (lib.scroll - dy).coerceIn(0f, lib.maxScroll)

    invalidate()
}

internal fun HomeView.libEndScroll() {
    val vt = lib.velocity ?: return

    vt.computeCurrentVelocity(1000)
    val vy = vt.yVelocity

    vt.recycle()
    lib.velocity = null

    if (abs(vy) < dp(200f)) return

    val start = lib.scroll
    val target = (start - vy * 0.35f).coerceIn(0f, lib.maxScroll)

    if (target == start) return

    val a = ValueAnimator.ofFloat(start, target)
    a.duration = 450
    a.interpolator = DecelerateInterpolator()

    a.addUpdateListener {
        lib.scroll = it.animatedValue as Float
        invalidate()
    }

    lib.fling = a
    a.start()
}

internal fun HomeView.libReleaseTracker() {
    lib.velocity?.recycle()
    lib.velocity = null
}

internal fun HomeView.libTap(x: Float, y: Float) {
    val cy = y + lib.scroll

    for (i in lib.hits.indices.reversed()) {
        val h = lib.hits[i]

        if (!h.rect.contains(x, cy)) continue

        when (h.kind) {
            LIB_APP -> h.key?.let { launch(it) }
            LIB_GROUP -> h.key?.let { libOpenGroup(it) }
            LIB_BACK -> libCloseGroup()
            LIB_SEARCH -> libOpenGroup(LIB_ALL)
        }

        return
    }
}

// ───────────── Dibujo ─────────────

// Línea base para que el texto quede centrado en vertical sobre centerY
private fun HomeView.libBaseline(p: Paint, centerY: Float): Float {
    p.getTextBounds("B", 0, 1, lib.tmp)

    return centerY + lib.tmp.height() / 2f
}

internal fun HomeView.drawLibrary(c: Canvas, offX: Float) {
    val w = width.toFloat()

    c.drawRect(offX, 0f, offX + w, height.toFloat(), lib.dim)

    lib.hits.clear()

    val areaTop = topInset + dp(8f)
    val areaBottom = height.toFloat()

    c.save()
    c.clipRect(offX, topInset, offX + w, areaBottom)
    c.translate(offX, -lib.scroll)

    val bottom = if (lib.open == null) {
        drawLibraryOverview(c, areaTop)
    } else {
        drawLibraryGroup(c, areaTop)
    }

    c.restore()

    lib.maxScroll = max(
        0f,
        bottom - areaBottom + bottomInset + dp(24f)
    )

    if (lib.scroll > lib.maxScroll) {
        lib.scroll = lib.maxScroll
    }
}

private fun HomeView.drawLibraryOverview(
    c: Canvas,
    areaTop: Float
): Float {
    val w = width.toFloat()
    val side = dp(20f)
    val gap = dp(14f)
    val cardW = (w - side * 2f - gap) / 2f
    val rowH = cardW + dp(34f)

    // Buscador
    val field = RectF(side, areaTop, w - side, areaTop + dp(54f))

    c.drawRoundRect(field, dp(27f), dp(27f), lib.card)
    c.drawRoundRect(field, dp(27f), dp(27f), lib.cardBorder)

    val rowY = field.centerY()
    val mr = dp(7.5f)
    val mx = field.left + dp(26f)

    // La lupa (círculo + mango) queda centrada en vertical
    val my = rowY - mr * 0.25f

    c.drawCircle(mx, my, mr, lib.glyph)

    c.drawLine(
        mx + mr * 0.72f,
        my + mr * 0.72f,
        mx + mr * 1.5f,
        my + mr * 1.5f,
        lib.glyph
    )

    c.drawText(
        "Biblioteca de apps",
        field.left + dp(54f),
        libBaseline(lib.hint, rowY),
        lib.hint
    )

    lib.hits.add(LibHit(RectF(field), LIB_SEARCH, null))

    val y = field.bottom + dp(30f)

    // Tarjetas de categorías
    val groups = lib.groups

    val pad = cardW * 0.09f
    val inner = cardW * 0.065f
    val icon = (cardW - pad * 2f - inner) / 2f

    for (i in groups.indices) {
        val g = groups[i]

        val left = side + (i % 2) * (cardW + gap)
        val top = y + (i / 2) * rowH

        val card = RectF(left, top, left + cardW, top + cardW)

        c.drawRoundRect(card, dp(24f), dp(24f), lib.card)
        c.drawRoundRect(card, dp(24f), dp(24f), lib.cardBorder)

        lib.hits.add(LibHit(RectF(card), LIB_GROUP, g.name))

        for (slot in 0 until 4) {
            val l = card.left + pad + (slot % 2) * (icon + inner)
            val t = card.top + pad + (slot / 2) * (icon + inner)

            if (slot == 3 && g.pkgs.size > 4) {
                drawLibraryCluster(c, g, l, t, icon)
                continue
            }

            val pkg = g.pkgs.getOrNull(slot) ?: continue
            val app = apps[pkg] ?: continue

            val r = RectF(l, t, l + icon, t + icon)

            c.drawBitmap(app.icon, null, r, iconPaint)

            lib.hits.add(LibHit(r, LIB_APP, pkg))
        }

        c.drawText(
            fit(g.name, cardW - dp(8f)),
            card.centerX(),
            card.bottom + dp(18f),
            labelPaint
        )
    }

    val rows = (groups.size + 1) / 2

    return y + rows * rowH
}

private fun HomeView.drawLibraryCluster(
    c: Canvas,
    g: LibGroup,
    left: Float,
    top: Float,
    icon: Float
) {
    val mini = icon * 0.46f
    val mg = icon * 0.08f

    for (k in 0 until 4) {
        val pkg = g.pkgs.getOrNull(3 + k) ?: break
        val app = apps[pkg] ?: continue

        val ml = left + (k % 2) * (mini + mg)
        val mt = top + (k / 2) * (mini + mg)

        c.drawBitmap(
            app.icon,
            null,
            RectF(ml, mt, ml + mini, mt + mini),
            iconPaint
        )
    }

    lib.hits.add(
        LibHit(
            RectF(left, top, left + icon, top + icon),
            LIB_GROUP,
            g.name
        )
    )
}

private fun HomeView.drawLibraryGroup(
    c: Canvas,
    areaTop: Float
): Float {
    val w = width.toFloat()
    val side = dp(20f)
    val key = lib.open ?: return areaTop

    val pkgs: List<String>
    val title: String

    if (key == LIB_ALL) {
        pkgs = apps.keys.toList()
        title = "Todas las apps"
    } else {
        val g = lib.groups.firstOrNull { it.name == key }

        if (g == null) {
            libReset()
            return areaTop
        }

        pkgs = g.pkgs
        title = g.name
    }

    // Botón atrás
    val cy = areaTop + dp(27f)
    val cx = side + dp(6f)

    c.drawLine(cx + dp(6f), cy - dp(9f), cx, cy, lib.glyph)
    c.drawLine(cx, cy, cx + dp(6f), cy + dp(9f), lib.glyph)

    c.drawText(
        title,
        side + dp(26f),
        libBaseline(lib.title, cy),
        lib.title
    )

    lib.hits.add(
        LibHit(
            RectF(0f, areaTop - dp(4f), w * 0.7f, areaTop + dp(54f)),
            LIB_BACK,
            null
        )
    )

    // Cuadrícula de apps
    val startY = areaTop + dp(70f)

    for (i in pkgs.indices) {
        val col = i % cols
        val row = i / cols

        val cellLeft = gridLeft + col * cellW
        val cellTop = startY + row * cellH

        val iconTop = cellTop +
            (cellH - iconSize - dp(18f)) / 2f

        drawSlot(
            c,
            pkgs[i],
            cellLeft + (cellW - iconSize) / 2f,
            iconTop,
            iconSize,
            true
        )

        lib.hits.add(
            LibHit(
                RectF(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH),
                LIB_APP,
                pkgs[i]
            )
        )
    }

    val rows = (pkgs.size + cols - 1) / cols

    return startY + rows * cellH
}
