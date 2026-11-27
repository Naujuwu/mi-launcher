package com.mio.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

private const val MODE_NONE = 0
private const val MODE_PRESS = 1
private const val MODE_SWIPE = 2
private const val MODE_DRAG = 3

class MainActivity : Activity() {

    private lateinit var home: HomeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        home = HomeView(this)
        setContentView(home)
        ViewCompat.setOnApplyWindowInsetsListener(home) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.setInsets(bars.top, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(home)
    }

    override fun onResume() {
        super.onResume()
        home.reloadApps()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        home.goToFirstPage()
    }

    @Deprecated("Launcher: el botón atrás solo vuelve a la primera página")
    override fun onBackPressed() {
        home.goToFirstPage()
    }
}

class HomeView(private val act: Activity) : View(act) {

    private class App(val label: String, val pkg: String, val icon: Bitmap) {
        var shown: String? = null
    }

    private val cols = 4
    private val rows = 6
    private val perPage = cols * rows

    private val prefs = act.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())

    private val apps = LinkedHashMap<String, App>()
    private val pages = mutableListOf<Array<String?>>()
    private val dock = arrayOfNulls<String>(4)
    private var loaded = false
    private var firstRun = false

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val iconPx = dp(58f).toInt()
    private val iconSize = iconPx.toFloat()

    // ---- pinceles ----
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(11.5f)
        setShadowLayer(dp(3f), 0f, dp(1f), 0xAA000000.toInt())
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x55FFFFFF
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val searchText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(15f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val magPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }

    // ---- geometría ----
    private var topInset = dp(32f)
    private var bottomInset = dp(16f)
    private var gridTop = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var dotsY = 0f
    private val dockRect = RectF()
    private val pillRect = RectF()
    private var dockShader: Shader? = null

    // ---- estado ----
    private var currentPage = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null

    private val touchSlop = ViewConfiguration.get(act).scaledTouchSlop.toFloat()
    private var mode = MODE_NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pressInDock = false
    private var pressIndex = -1

    private var dragPkg: String? = null
    private var dragX = 0f
    private var dragY = 0f
    private var srcInDock = false
    private var srcPage = 0
    private var srcIndex = -1
    private var edgeDir = 0

    private val longPress = Runnable {
        if (mode != MODE_PRESS) return@Runnable
        val pkg = pkgAt(pressInDock, pressIndex, currentPage) ?: return@Runnable
        startDrag(pkg)
    }

    private val edgeRunnable = Runnable {
        if (mode != MODE_DRAG) return@Runnable
        if (edgeDir > 0) {
            if (currentPage == pages.size - 1) pages.add(arrayOfNulls<String>(perPage))
            animateToPage(currentPage + 1)
        } else if (edgeDir < 0 && currentPage > 0) {
            animateToPage(currentPage - 1)
        }
        edgeDir = 0
    }

    // =====================  API pública  =====================

    fun setInsets(top: Int, bottom: Int) {
        topInset = top.toFloat()
        bottomInset = bottom.toFloat()
        computeGeometry()
    }

    fun goToFirstPage() {
        if (currentPage != 0 && mode == MODE_NONE) animateToPage(0)
    }

    fun reloadApps() {
        val pm = act.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val fresh = LinkedHashMap<String, App>()
        for (ri in found) {
            val pkg = ri.activityInfo.packageName
            if (fresh.containsKey(pkg)) continue
            fresh[pkg] = apps[pkg] ?: App(ri.loadLabel(pm).toString(), pkg, roundedIcon(ri.loadIcon(pm)))
        }
        apps.clear()
        apps.putAll(fresh)
        if (!loaded) {
            loadLayout()
            loaded = true
        }
        reconcile()
        save()
        computeGeometry()
    }

    // =====================  iconos y layout guardado  =====================

    private fun roundedIcon(d: Drawable): Bitmap {
        val raw = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, iconPx, iconPx)
        d.draw(Canvas(raw))
        val out = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val r = iconSize * 0.225f
        Canvas(out).drawRoundRect(RectF(0f, 0f, iconSize, iconSize), r, r, p)
        return out
    }

    private fun loadLayout() {
        pages.clear()
        val saved = prefs.getString("pages", null)
        if (saved != null) {
            for (pageStr in saved.split(";")) {
                val arr = arrayOfNulls<String>(perPage)
                val parts = pageStr.split(",")
                for (i in 0 until perPage) {
                    val s = parts.getOrNull(i)
                    arr[i] = if (s.isNullOrEmpty()) null else s
                }
                pages.add(arr)
            }
        }
        if (pages.isEmpty()) pages.add(arrayOfNulls<String>(perPage))
        dock.fill(null)
        val d = prefs.getString("dock", null)
        if (d != null) {
            val parts = d.split(",")
            for (i in 0 until 4) {
                val s = parts.getOrNull(i)
                dock[i] = if (s.isNullOrEmpty()) null else s
            }
        }
        firstRun = (saved == null)
    }

    private fun reconcile() {
        val placed = HashSet<String>()
        for (page in pages) {
            for (i in page.indices) {
                val p = page[i]
                if (p != null && (!apps.containsKey(p) || !placed.add(p))) page[i] = null
            }
        }
        for (i in dock.indices) {
            val p = dock[i]
            if (p != null && (!apps.containsKey(p) || !placed.add(p))) dock[i] = null
        }

        if (firstRun) {
            val wanted = listOf(
                listOf("phone", "teléfono", "telefono"),
                listOf("messages", "mensajes", "messaging"),
                listOf("browser", "internet", "navegador", "chrome"),
                listOf("camera", "cámara", "camara")
            )
            var di = 0
            for (names in wanted) {
                val match = apps.values.firstOrNull { it.label.lowercase() in names && !placed.contains(it.pkg) }
                if (match != null && di < 4) {
                    dock[di] = match.pkg
                    di++
                    placed.add(match.pkg)
                }
            }
            for (i in 0 until 4) {
                if (dock[i] == null) {
                    val nx = apps.values.firstOrNull { !placed.contains(it.pkg) } ?: break
                    dock[i] = nx.pkg
                    placed.add(nx.pkg)
                }
            }
            firstRun = false
        }

        for (app in apps.values) {
            if (placed.contains(app.pkg)) continue
            var done = false
            for (page in pages) {
                val idx = page.indexOfFirst { it == null }
                if (idx >= 0) {
                    page[idx] = app.pkg
                    done = true
                    break
                }
            }
            if (!done) {
                val np = arrayOfNulls<String>(perPage)
                np[0] = app.pkg
                pages.add(np)
            }
            placed.add(app.pkg)
        }
        trimEmptyPages()
    }

    private fun trimEmptyPages() {
        while (pages.size > 1 && pages[pages.size - 1].all { it == null }) {
            pages.removeAt(pages.size - 1)
        }
        if (currentPage > pages.size - 1) currentPage = pages.size - 1
    }

    private fun save() {
        val p = pages.joinToString(";") { page -> page.joinToString(",") { it ?: "" } }
        val d = dock.joinToString(",") { it ?: "" }
        prefs.edit().putString("pages", p).putString("dock", d).apply()
    }

    // =====================  geometría  =====================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val dockH = dp(92f)
        val dockSide = dp(14f)
        val dockBottom = h - bottomInset - dp(10f)
        dockRect.set(dockSide, dockBottom - dockH, w - dockSide, dockBottom)
        dockShader = LinearGradient(
            0f, dockRect.top, 0f, dockRect.bottom,
            0x66FFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP
        )

        val pillW = dp(112f)
        val pillH = dp(36f)
        val pillBottom = dockRect.top - dp(14f)
        pillRect.set((w - pillW) / 2f, pillBottom - pillH, (w + pillW) / 2f, pillBottom)

        dotsY = pillRect.top - dp(16f)
        gridTop = topInset + dp(10f)
        val gridBottom = dotsY - dp(14f)
        cellW = w / cols
        cellH = (gridBottom - gridTop) / rows

        for (a in apps.values) a.shown = null
        invalidate()
    }

    private fun gridCellAt(x: Float, y: Float): Int {
        if (cellW <= 0f || cellH <= 0f) return -1
        if (y < gridTop || y >= gridTop + cellH * rows) return -1
        val col = (x / cellW).toInt()
        val row = ((y - gridTop) / cellH).toInt()
        if (col < 0 || col >= cols || row < 0 || row >= rows) return -1
        return row * cols + col
    }

    private fun dockCellAt(x: Float, y: Float): Int {
        if (x < dockRect.left || x > dockRect.right) return -1
        if (y < dockRect.top - dp(10f) || y > dockRect.bottom + dp(10f)) return -1
        val i = ((x - dockRect.left) / (dockRect.width() / 4f)).toInt()
        return i.coerceIn(0, 3)
    }

    private fun pkgAt(inDock: Boolean, idx: Int, page: Int): String? {
        if (idx < 0) return null
        return if (inDock) dock[idx] else pages.getOrNull(page)?.get(idx)
    }

    // =====================  dibujo  =====================

    override fun onDraw(canvas: Canvas) {
        for (p in (currentPage - 1)..(currentPage + 1)) {
            if (p < 0 || p >= pages.size) continue
            val offX = (p - currentPage) * width + pageOffset
            if (offX <= -width || offX >= width) continue
            drawPage(canvas, p, offX)
        }
        drawDots(canvas)
        drawPill(canvas)
        drawDock(canvas)
        drawDragged(canvas)
    }

    private fun fit(label: String, maxW: Float): String {
        if (labelPaint.measureText(label) <= maxW) return label
        var s = label
        while (s.isNotEmpty() && labelPaint.measureText(s + "…") > maxW) s = s.dropLast(1)
        return s + "…"
    }

    private fun drawPage(c: Canvas, p: Int, offX: Float) {
        val cells = pages[p]
        for (i in 0 until perPage) {
            val pkg = cells[i] ?: continue
            if (dragPkg != null && !srcInDock && srcPage == p && srcIndex == i) continue
            val app = apps[pkg] ?: continue
            val col = i % cols
            val row = i / cols
            val cx = offX + (col + 0.5f) * cellW
            val top = gridTop + row * cellH + (cellH - iconSize - dp(18f)) / 2f
            c.drawBitmap(app.icon, cx - iconSize / 2f, top, iconPaint)
            val shown = app.shown ?: fit(app.label, cellW - dp(8f)).also { app.shown = it }
            c.drawText(shown, cx, top + iconSize + dp(14f), labelPaint)
        }
    }

    private fun drawDots(c: Canvas) {
        val n = pages.size
        if (n <= 1) return
        val r = dp(3.2f)
        val gap = dp(12f)
        val total = (n - 1) * gap
        val x0 = (width - total) / 2f
        val active = Math.round(currentPage - pageOffset / width)
        for (i in 0 until n) {
            dotPaint.color = if (i == active) 0xFFFFFFFF.toInt() else 0x66FFFFFF
            c.drawCircle(x0 + i * gap, dotsY, r, dotPaint)
        }
    }

    private fun drawPill(c: Canvas) {
        glassPaint.shader = null
        glassPaint.color = 0x44FFFFFF
        val rad = pillRect.height() / 2f
        c.drawRoundRect(pillRect, rad, rad, glassPaint)

        val label = "Search"
        val tw = searchText.measureText(label)
        val iconW = dp(16f)
        val gap = dp(6f)
        val startX = pillRect.centerX() - (iconW + gap + tw) / 2f
        val cy = pillRect.centerY()
        val r = dp(5f)
        val mx = startX + r
        val my = cy - dp(1f)
        c.drawCircle(mx, my, r, magPaint)
        c.drawLine(mx + r * 0.72f, my + r * 0.72f, startX + iconW, cy + dp(5f), magPaint)
        c.drawText(label, startX + iconW + gap, cy + dp(5f), searchText)
    }

    private fun drawDock(c: Canvas) {
        val rad = dp(34f)
        glassPaint.color = Color.WHITE
        glassPaint.shader = dockShader
        c.drawRoundRect(dockRect, rad, rad, glassPaint)
        glassPaint.shader = null
        c.drawRoundRect(dockRect, rad, rad, borderPaint)

        val cw = dockRect.width() / 4f
        for (i in 0 until 4) {
            val pkg = dock[i] ?: continue
            if (dragPkg != null && srcInDock && srcIndex == i) continue
            val app = apps[pkg] ?: continue
            val cx = dockRect.left + (i + 0.5f) * cw
            c.drawBitmap(app.icon, cx - iconSize / 2f, dockRect.centerY() - iconSize / 2f, iconPaint)
        }
    }

    private fun drawDragged(c: Canvas) {
        val pkg = dragPkg ?: return
        val app = apps[pkg] ?: return
        c.save()
        c.scale(1.15f, 1.15f, dragX, dragY)
        iconPaint.alpha = 225
        c.drawBitmap(app.icon, dragX - iconSize / 2f, dragY - iconSize / 2f - dp(8f), iconPaint)
        iconPaint.alpha = 255
        c.restore()
    }

    // =====================  táctil  =====================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageAnimator?.cancel()
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                mode = MODE_PRESS
                val d = dockCellAt(e.x, e.y)
                if (d >= 0) {
                    pressInDock = true
                    pressIndex = d
                } else {
                    pressInDock = false
                    pressIndex = gridCellAt(e.x, e.y)
                }
                ui.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = e.x
                lastY = e.y
                when (mode) {
                    MODE_PRESS -> {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            ui.removeCallbacks(longPress)
                            mode = if (abs(dx) > abs(dy)) MODE_SWIPE else MODE_NONE
                        }
                    }
                    MODE_SWIPE -> {
                        pageOffset = clampOffset(e.x - downX)
                        invalidate()
                    }
                    MODE_DRAG -> {
                        dragX = e.x
                        dragY = e.y
                        checkEdge()
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                ui.removeCallbacks(longPress)
                ui.removeCallbacks(edgeRunnable)
                when (mode) {
                    MODE_PRESS -> {
                        val pkg = pkgAt(pressInDock, pressIndex, currentPage)
                        if (pkg != null) launch(pkg)
                    }
                    MODE_SWIPE -> {
                        val threshold = width / 5f
                        var target = currentPage
                        if (pageOffset < -threshold && currentPage < pages.size - 1) target++
                        else if (pageOffset > threshold && currentPage > 0) target--
                        animateToPage(target)
                    }
                    MODE_DRAG -> dropDragged()
                }
                mode = MODE_NONE
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(longPress)
                ui.removeCallbacks(edgeRunnable)
                if (mode == MODE_SWIPE) animateToPage(currentPage)
                dragPkg = null
                mode = MODE_NONE
                invalidate()
                return true
            }
        }
        return true
    }

    private fun clampOffset(dx: Float): Float {
        if (dx > 0 && currentPage == 0) return dx * 0.3f
        if (dx < 0 && currentPage == pages.size - 1) return dx * 0.3f
        return dx
    }

    private fun startDrag(pkg: String) {
        mode = MODE_DRAG
        dragPkg = pkg
        srcInDock = pressInDock
        srcPage = currentPage
        srcIndex = pressIndex
        dragX = lastX
        dragY = lastY
        edgeDir = 0
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun checkEdge() {
        val dir = when {
            dragX < dp(30f) -> -1
            dragX > width - dp(30f) -> 1
            else -> 0
        }
        if (dir != edgeDir) {
            ui.removeCallbacks(edgeRunnable)
            edgeDir = dir
            if (dir != 0) ui.postDelayed(edgeRunnable, 600)
        }
    }

    private fun dropDragged() {
        val pkg = dragPkg
        if (pkg != null) {
            val dIdx = dockCellAt(dragX, dragY)
            if (dIdx >= 0) {
                place(pkg, true, 0, dIdx)
            } else {
                val gIdx = gridCellAt(dragX, dragY)
                if (gIdx >= 0) place(pkg, false, currentPage, gIdx)
            }
        }
        dragPkg = null
        edgeDir = 0
        trimEmptyPages()
        save()
        invalidate()
    }

    private fun place(pkg: String, toDock: Boolean, toPage: Int, toIdx: Int) {
        val target = if (toDock) dock else pages[toPage]
        val src = if (srcInDock) dock else pages[srcPage]
        val displaced = target[toIdx]
        target[toIdx] = pkg
        src[srcIndex] = displaced
    }

    private fun launch(pkg: String) {
        act.packageManager.getLaunchIntentForPackage(pkg)?.let { act.startActivity(it) }
    }

    private fun animateToPage(target: Int) {
        pageAnimator?.cancel()
        val from = pageOffset
        val to = when {
            target > currentPage -> -width.toFloat()
            target < currentPage -> width.toFloat()
            else -> 0f
        }
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = 220
        a.addUpdateListener {
            pageOffset = it.animatedValue as Float
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentPage = target
                pageOffset = 0f
                invalidate()
            }
        })
        pageAnimator = a
        a.start()
    }
}
