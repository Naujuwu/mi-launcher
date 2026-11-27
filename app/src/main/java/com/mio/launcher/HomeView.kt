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

    private var dotsMix = 0f
    private var dotsTarget = 0f
    private var dotsAnimator: ValueAnimator? = null
    private val hideDots = Runnable { animateDots(0f) }

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
            if (currentPage == store.pages.size - 1) {
                store.pages.add(arrayOfNulls(perPage))
            }
            animateToPage(currentPage + 1)
        } else if (edgeDir < 0 && currentPage > 0) {
            animateToPage(currentPage - 1)
        }
        edgeDir = 0
    }

    private val squircle: Path by lazy { IconRenderer.buildSquircle(iconSize, 5.0) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotifService.onChanged = { invalidate() }
    }

    override fun onDetachedFromWindow() {
        NotifService.onChanged = null
        super.onDetachedFromWindow()
    }

    // =====================  API pública  =====================

    fun setInsets(top: Int, bottom: Int) {
        topInset = max(top.toFloat(), dp(24f))
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
        val fresh = LinkedHashMap<String, AppItem>()
        for (ri in found) {
            val pkg = ri.activityInfo.packageName
            if (fresh.containsKey(pkg)) continue
            fresh[pkg] = apps[pkg] ?: AppItem(
                ri.loadLabel(pm).toString(),
                pkg,
                IconRenderer.roundedIcon(ri.loadIcon(pm), iconPx, squircle)
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

    // =====================  geometría  =====================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    private fun computeGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val dockH = iconSize + dp(34f)
        val dockSide = w * 0.032f
        val dockBottom = h - bottomInset - dp(10f)
        dockRect.set(dockSide, dockBottom - dockH, w - dockSide, dockBottom)
        dockShader = LinearGradient(
            0f, dockRect.top, 0f, dockRect.bottom,
            0x66FFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP
        )
        dockPitch = w * 0.2205f
        dockLeft = w / 2f - 2f * dockPitch

        pillH = dp(26f)
        pillTop = dockRect.top - dp(16f) - pillH
        pillBorder.shader = LinearGradient(
            0f, pillTop, 0f, pillTop + pillH,
            0x80FFFFFF.toInt(), 0x1AFFFFFF, Shader.TileMode.CLAMP
        )

        cellW = w * 0.231f
        gridLeft = (w - cellW * cols) / 2f
        val availTop = topInset + dp(8f)
        val availBottom = pillTop - dp(12f)
        val avail = availBottom - availTop
        cellH = minOf(avail / rows, w * 0.252f)
        gridTop = availTop + (avail - cellH * rows) / 2f

        for (a in apps.values) a.shown = null
        invalidate()
    }

    private fun gridCellAt(x: Float, y: Float): Int {
        if (y < gridTop || y > gridTop + cellH * rows) return -1
        val col = ((x - gridLeft) / cellW).toInt()
        val row = ((y - gridTop) / cellH).toInt()
        if (col !in 0 until cols || row !in 0 until rows) return -1
        return row * cols + col
    }

    private fun dockCellAt(x: Float, y: Float): Int {
        if (y < dockRect.top || y > dockRect.bottom) return -1
        for (i in
