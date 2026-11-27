package com.mio.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.mio.launcher.layout.LayoutStore
import com.mio.launcher.models.AppItem
import com.mio.launcher.ui.IconRenderer
import kotlin.math.abs
import kotlin.math.max

private const val MODE_NONE = 0
private const val MODE_PRESS = 1
private const val MODE_SWIPE = 2
private const val MODE_DRAG = 3

class HomeView(private val act: Activity) : View(act) {

    private val cols = 4
    private val rows = 6
    private val perPage = cols * rows

    private val store = LayoutStore(act, cols, rows)
    private val ui = Handler(Looper.getMainLooper())

    private val apps = LinkedHashMap<String, AppItem>()
    private var loaded = false

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val iconPx = (resources.displayMetrics.widthPixels * 0.16f).toInt()
    private val iconSize = iconPx.toFloat()

    // ---- pinceles ----

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(11.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        textSize = dp(12.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val magPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }

    private val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF383C.toInt()
    }

    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = iconSize * 0.40f * 0.62f
    }

    private val badgeRect = RectF()

    // ---- geometría ----

    private var topInset = dp(32f)
    private var bottomInset = dp(16f)

    private var gridTop = 0f
    private var gridLeft = 0f
    private var cellW = 0f
    private var cellH = 0f

    private var dockLeft = 0f
    private var dockPitch = 0f

    private var pillTop = 0f
    private var pillH = 0f

    private val dockRect = RectF()
    private val pillRect = RectF()

    private var dockShader: Shader? = null

    // ---- puntos de página ----

    private var dotsMix = 0f
    private var dotsTarget = 0f
    private var dotsAnimator: ValueAnimator? = null

    private val hideDots = Runnable {
        animateDots(0f)
    }

    // ---- estado ----

    private var currentPage = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null

    private val touchSlop =
        ViewConfiguration.get(act).scaledTouchSlop.toFloat()

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

    private val squircle: Path by lazy {
        IconRenderer.buildSquircle(iconSize, 5.0)
    }

    private val longPress = Runnable {
        if (mode != MODE_PRESS) return@Runnable

        val pkg = pkgAt(
            pressInDock,
            pressIndex,
            currentPage
        ) ?: return@Runnable

        startDrag(pkg)
    }

    private val edgeRunnable = Runnable {
        if (mode != MODE_DRAG) return@Runnable

        if (edgeDir > 0) {

            if (currentPage == store.pages.size - 1) {
                store.pages.add(arrayOfNulls(perPage))
            }

            animateToPage(currentPage + 1)

        } else if (edgeDir < 0 && currentPage > 0) {

            animateToPage(currentPage - 1)
        }

        edgeDir = 0
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotifService.onChanged = { invalidate() }
    }

    override fun onDetachedFromWindow() {
        NotifService.onChanged = null
        super.onDetachedFromWindow()
    }

    // ===================== API pública =====================

    fun setInsets(top: Int, bottom: Int) {
        topInset = max(top.toFloat(), dp(24f))
        bottomInset = bottom.toFloat()
        computeGeometry()
    }

    fun goToFirstPage() {
        if (currentPage != 0 && mode == MODE_NONE) {
            animateToPage(0)
        }
    }

    fun reloadApps() {

        val pm = act.packageManager

        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val found = pm.queryIntentActivities(intent, 0)
            .filter {
                it.activityInfo.packageName != act.packageName
            }
            .sortedBy {
                it.loadLabel(pm).toString().lowercase()
            }

        val fresh = LinkedHashMap<String, AppItem>()

        for (ri in found) {

            val pkg = ri.activityInfo.packageName

            if (fresh.containsKey(pkg)) continue

            fresh[pkg] = apps[pkg] ?: AppItem(
                ri.loadLabel(pm).toString(),
                pkg,
                IconRenderer.roundedIcon(
                    ri.loadIcon(pm),
                    iconPx,
                    squircle
                )
            )
        }

        apps.clear()
        apps.putAll(fresh)

        if (!loaded) {
            store.load()
            loaded = true
        }

        store.reconcile(apps)
        store.save()

        currentPage = store.trimEmptyPages(currentPage)

        computeGeometry()
    }

    // ===================== geometría =====================

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {

        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

        // Dock

        val dockH = iconSize + dp(34f)
        val dockSide = w * 0.032f
        val dockBottom = h - bottomInset - dp(10f)

        dockRect.set(
            dockSide,
            dockBottom - dockH,
            w - dockSide,
            dockBottom
        )

        dockShader = LinearGradient(
            0f,
            dockRect.top,
            0f,
            dockRect.bottom,
            0x66FFFFFF,
            0x40FFFFFF,
            Shader.TileMode.CLAMP
        )

        dockPitch = w * 0.2205f
        dockLeft = w / 2f - 2f * dockPitch

        // Burbuja de búsqueda

        pillH = dp(26f)
        pillTop = dockRect.top - dp(16f) - pillH

        pillBorder.shader = LinearGradient(
            0f,
            pillTop,
            0f,
            pillTop + pillH,
            0x80FFFFFF.toInt(),
            0x1AFFFFFF,
            Shader.TileMode.CLAMP
        )

        // Cuadrícula 4x6

        cellW = w * 0.231f
        gridLeft = (w - cellW * cols) / 2f

        val availTop = topInset + dp(8f)
        val availBottom = pillTop - dp(12f)
        val avail = availBottom - availTop

        cellH = minOf(
            avail / rows,
            w * 0.252f
        )

        gridTop = availTop + (avail - cellH * rows) / 2f

        for (a in apps.values) {
            a.shown = null
        }

        invalidate()
    }

    private fun gridCellAt(x: Float, y: Float): Int {

        if (cellW <= 0f || cellH <= 0f) return -1

        if (y < gridTop || y >= gridTop + cellH * rows) {
            return -1
        }

        if (x < gridLeft || x >= gridLeft + cellW * cols) {
            return -1
        }

        val col = ((x - gridLeft) / cellW).toInt()
        val row = ((y - gridTop) / cellH).toInt()

        if (col !in 0 until cols || row !in 0 until rows) {
            return -1
        }

        return row * cols + col
    }

    private fun dockCellAt(x: Float, y: Float): Int {

        if (x < dockRect.left || x > dockRect.right) return -1

        if (y < dockRect.top - dp(10f) ||
            y > dockRect.bottom + dp(10f)
        ) return -1

        return ((x - dockLeft) / dockPitch)
            .toInt()
            .coerceIn(0, 3)
    }

    private fun pkgAt(
        inDock: Boolean,
        idx: Int,
        page: Int
    ): String? {

        if (idx < 0) return null

        return if (inDock) {
            store.dock[idx]
        } else {
            store.pages.getOrNull(page)?.get(idx)
        }
    }

    // ===================== dibujo =====================

    override fun onDraw(canvas: Canvas) {

        for (p in (currentPage - 1)..(currentPage + 1)) {

            if (p < 0 || p >= store.pages.size) continue

            val offX = (p - currentPage) * width + pageOffset

            if (offX <= -width || offX >= width) continue

            drawPage(canvas, p, offX)
        }

        drawPill(canvas)
        drawDock(canvas)
        drawDragged(canvas)
    }

    private fun fit(label: String, maxW: Float): String {

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

    private fun drawPage(
        c: Canvas,
        p: Int,
        offX: Float
    ) {

        val cells = store.pages[p]

        for (i in 0 until perPage) {

            val pkg = cells[i] ?: continue

            if (
                dragPkg != null &&
                !srcInDock &&
                srcPage == p &&
                srcIndex == i
            ) continue

            val app = apps[pkg] ?: continue

            val col = i % cols
            val row = i / cols

            val cx = offX + gridLeft + (col + 0.5f) * cellW

            val top = gridTop +
                row * cellH +
                (cellH - iconSize - dp(18f)) / 2f

            c.drawBitmap(
                app.icon,
                cx - iconSize / 2f,
                top,
                iconPaint
            )

            drawBadge(
                c,
                pkg,
                cx - iconSize / 2f,
                top
            )

            val shown = app.shown ?: fit(
                app.label,
                iconSize
            ).also {
                app.shown = it
            }

            c.drawText(
                shown,
                cx,
                top + iconSize + dp(15f),
                labelPaint
            )
        }
    }

    private fun drawPill(c: Canvas) {

        val n = store.pages.size
        val label = "Search"

        val tw = searchText.measureText(label)

        val iconW = dp(12f)
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

        // Search

        val sa = ((1f - dotsMix) * 255f).toInt()

        if (sa > 0) {

            searchText.alpha = sa
            magPaint.alpha = sa

            val startX = cx - (iconW + gap + tw) / 2f

            val r = dp(3.8f)

            val mx = startX + r
            val my = cy - dp(0.8f)

            c.drawCircle(mx, my, r, magPaint)

            c.drawLine(
                mx + r * 0.72f,
                my + r * 0.72f,
                startX + iconW - dp(0.5f),
                cy + dp(4f),
                magPaint
            )

            val fm = searchText.fontMetrics

            val textY = cy -
                (fm.ascent + fm.descent) / 2f

            c.drawText(
                label,
                startX + iconW + gap,
                textY,
                searchText
            )
        }

        // Puntos de página

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

    private fun drawDock(c: Canvas) {

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

            val pkg = store.dock[i] ?: continue

            if (
                dragPkg != null &&
                srcInDock &&
                srcIndex == i
            ) continue

            val app = apps[pkg] ?: continue

            val cx = dockLeft + (i + 0.5f) * dockPitch

            c.drawBitmap(
                app.icon,
                cx - iconSize / 2f,
                dockRect.centerY() - iconSize / 2f,
                iconPaint
            )

            drawBadge(
                c,
                pkg,
                cx - iconSize / 2f,
                dockRect.centerY() - iconSize / 2f
            )
        }
    }

    // Globo rojo de notificaciones

    private fun drawBadge(
        c: Canvas,
        pkg: String,
        iconLeft: Float,
        iconTop: Float
    ) {

        if (NotifService.dnd) return

        val count = NotifService.counts[pkg] ?: return

        if (count <= 0) return

        val label = if (count > 999) "999+" else count.toString()

        val h = iconSize * 0.40f
        val tw = badgeText.measureText(label)

        val w = max(h, tw + h * 0.62f)

        val cx = iconLeft + iconSize - iconSize * 0.02f
        val cy = iconTop + iconSize * 0.06f

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
    }

    private fun drawDragged(c: Canvas) {

        val pkg = dragPkg ?: return
        val app = apps[pkg] ?: return

        c.save()

        c.scale(
            1.15f,
            1.15f,
            dragX,
            dragY
        )

        iconPaint.alpha = 225

        c.drawBitmap(
            app.icon,
            dragX - iconSize / 2f,
            dragY - iconSize / 2f - dp(8f),
            iconPaint
        )

        iconPaint.alpha = 255

        c.restore()
    }

    // ===================== táctil =====================

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

                ui.postDelayed(
                    longPress,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                lastX = e.x
                lastY = e.y

                when (mode) {

                    MODE_PRESS -> {

                        val dx = e.x - downX
                        val dy = e.y - downY

                        if (
                            abs(dx) > touchSlop ||
                            abs(dy) > touchSlop
                        ) {

                            ui.removeCallbacks(longPress)

                            mode = if (abs(dx) > abs(dy)) {
                                MODE_SWIPE
                            } else {
                                MODE_NONE
                            }

                            if (mode == MODE_SWIPE) {
                                showDots()
                            }
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

                        val pkg = pkgAt(
                            pressInDock,
                            pressIndex,
                            currentPage
                        )

                        if (pkg != null) {
                            launch(pkg)
                        }
                    }

                    MODE_SWIPE -> {

                        val threshold = width / 5f

                        var target = currentPage

                        if (
                            pageOffset < -threshold &&
                            currentPage < store.pages.size - 1
                        ) {
                            target++
                        } else if (
                            pageOffset > threshold &&
                            currentPage > 0
                        ) {
                            target--
                        }

                        animateToPage(target)
                    }

                    MODE_DRAG -> {
                        dropDragged()
                    }
                }

                mode = MODE_NONE

                return true
            }

            MotionEvent.ACTION_CANCEL -> {

                ui.removeCallbacks(longPress)
                ui.removeCallbacks(edgeRunnable)

                if (mode == MODE_SWIPE) {
                    animateToPage(currentPage)
                }

                dragPkg = null
                mode = MODE_NONE

                invalidate()

                return true
            }
        }

        return true
    }

    private fun clampOffset(dx: Float): Float {

        if (dx > 0 && currentPage == 0) {
            return dx * 0.3f
        }

        if (
            dx < 0 &&
            currentPage == store.pages.size - 1
        ) {
            return dx * 0.3f
        }

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

        performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS
        )

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

            if (dir != 0) {
                ui.postDelayed(edgeRunnable, 600)
            }
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

                if (gIdx >= 0) {
                    place(pkg, false, currentPage, gIdx)
                }
            }
        }

        dragPkg = null
        edgeDir = 0

        currentPage = store.trimEmptyPages(currentPage)

        store.save()

        invalidate()
    }

    private fun place(
        pkg: String,
        toDock: Boolean,
        toPage: Int,
        toIdx: Int
    ) {

        val target = if (toDock) {
            store.dock
        } else {
            store.pages[toPage]
        }

        val src = if (srcInDock) {
            store.dock
        } else {
            store.pages[srcPage]
        }

        val displaced = target[toIdx]

        target[toIdx] = pkg
        src[srcIndex] = displaced
    }

    // ===================== animaciones =====================

    private fun showDots() {

        if (store.pages.size <= 1) return

        ui.removeCallbacks(hideDots)

        animateDots(1f)
    }

    private fun scheduleHideDots() {

        ui.removeCallbacks(hideDots)

        ui.postDelayed(hideDots, 800)
    }

    private fun animateDots(target: Float) {

        if (dotsTarget == target) return

        dotsTarget = target

        dotsAnimator?.cancel()

        val a = ValueAnimator.ofFloat(
            dotsMix,
            target
        )

        a.duration = 180

        a.addUpdateListener {

            dotsMix = it.animatedValue as Float

            invalidate()
        }

        dotsAnimator = a

        a.start()
    }

    private fun launch(pkg: String) {

        act.packageManager
            .getLaunchIntentForPackage(pkg)
            ?.let {
                act.startActivity(it)
            }
    }

    private fun animateToPage(target: Int) {

        if (target !in store.pages.indices) return

        pageAnimator?.cancel()

        showDots()

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

                scheduleHideDots()

                invalidate()
            }
        })

        pageAnimator = a

        a.start()
    }
}
