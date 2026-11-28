package com.mio.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.os.Build
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
import android.widget.EditText
import android.widget.FrameLayout

internal const val MODE_NONE = 0
internal const val MODE_PRESS = 1
internal const val MODE_SWIPE = 2
internal const val MODE_DRAG = 3
internal const val MODE_FOLDER = 4
internal const val MODE_LIBSCROLL = 5

class HomeView(internal val act: Activity) : View(act) {
    internal val cols = 4

    internal val rows = 6

    internal val perPage = cols * rows

    internal val store = LayoutStore(act, cols, rows)

    internal val ui = Handler(Looper.getMainLooper())

    internal val apps = LinkedHashMap<String, AppItem>()

    internal var loaded = false

    internal fun dp(v: Float) = v * resources.displayMetrics.density

    internal val iconPx = (resources.displayMetrics.widthPixels * 0.16f).toInt()

    internal val iconSize = iconPx.toFloat()

    internal val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    internal val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = iconSize * 0.19f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.01f
        setShadowLayer(dp(2f), 0f, dp(0.5f), 0x73000000)
    }

    internal val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x55FFFFFF
    }

    internal val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val searchText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(12.5f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    internal val magPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }

    internal val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
    }

    internal val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF383C.toInt()
    }

    internal val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = iconSize * 0.40f * 0.62f
    }

    internal val badgeRect = RectF()

    // Geometría principal

    internal var topInset = dp(32f)

    internal var bottomInset = dp(16f)

    internal var gridTop = 0f

    internal var gridLeft = 0f

    internal var cellW = 0f

    internal var cellH = 0f

    internal var dockLeft = 0f

    internal var dockPitch = 0f

    internal var pillTop = 0f

    internal var pillH = 0f

    internal val dockRect = RectF()

    internal val pillRect = RectF()

    internal var dockShader: Shader? = null

    // Carpetas

    internal var openFolderId: String? = null

    internal var folderPage = 0

    internal val folderPanel = RectF()

    internal val folderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(30f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setShadowLayer(dp(8f), 0f, dp(3f), 0x66000000)
    }

    internal val folderLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11.5f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        letterSpacing = 0.01f
        setShadowLayer(dp(2f), 0f, dp(0.5f), 0x73000000)
    }

    internal val folderPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val folderPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    internal val folderIconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val folderIconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    internal val folderOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
    }

    internal val folderDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal val folderIconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    internal val folderHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF
    }

    internal var renameBox: FrameLayout? = null

    internal var renameEdit: EditText? = null

    internal var renamingFolderId: String? = null

    internal var folderDragPkg: String? = null

    internal var dragFromFolderId: String? = null

    internal val folderLongPress = Runnable {
        val id = openFolderId ?: return@Runnable
        val folder = store.folders[id] ?: return@Runnable

        if (mode != MODE_FOLDER || folderPageOffset != 0f) return@Runnable

        val idx = folderCellAt(folderDownX, folderDownY)
        val pkg = folder.apps.getOrNull(idx) ?: return@Runnable

        folderDragPkg = pkg
        dragX = folderDownX
        dragY = folderDownY

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    internal var folderDownX = 0f

    internal var folderDownY = 0f

    internal var folderPageOffset = 0f

    internal var folderPageAnimator: ValueAnimator? = null

    // Puntos de página

    internal var dotsMix = 0f

    internal var dotsTarget = 0f

    internal var dotsAnimator: ValueAnimator? = null

    internal val hideDots = Runnable {
        animateDots(0f)
    }

    // Estado

    internal var currentPage = 0

    internal var pageOffset = 0f

    internal var pageAnimator: ValueAnimator? = null

    internal val touchSlop =
        ViewConfiguration.get(act).scaledTouchSlop.toFloat()

    internal var mode = MODE_NONE

    internal var downX = 0f

    internal var downY = 0f

    internal var lastX = 0f

    internal var lastY = 0f

    internal var pressInDock = false

    internal var pressIndex = -1

    internal var dragPkg: String? = null

    internal var dragX = 0f

    internal var dragY = 0f

    internal var srcInDock = false

    internal var srcPage = 0

    internal var srcIndex = -1

    internal var edgeDir = 0

    // Biblioteca de apps (última página, a la derecha)
    internal val lib = LibraryState(resources.displayMetrics.density)

    // Carpeta al mantener el icono encima: hace falta quedarse ~0.5 s
    internal var hoverKey = -1
    internal var folderReady = false

    internal val folderDwell = Runnable {
        folderReady = true
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    internal val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
    }

    internal val targetRect = RectF()

    internal val squircle: Path by lazy {
        IconRenderer.buildSquircle(iconSize, 5.0)
    }

    internal val longPress = Runnable {
        if (mode != MODE_PRESS) return@Runnable

        val pkg = pkgAt(
            pressInDock,
            pressIndex,
            currentPage
        ) ?: return@Runnable

        startDrag(pkg)
    }

    internal val edgeRunnable = Runnable {
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

    // API pública

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

        val wasOnLibrary = currentPage >= store.pages.size

        currentPage = store.trimEmptyPages(currentPage)

        if (wasOnLibrary) currentPage = store.pages.size

        libBuild()

        if (openFolderId != null &&
            store.folders[openFolderId] == null
        ) {
            openFolderId = null
        }

        computeGeometry()
    }

    // Geometría

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry()
    }

    internal fun computeGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) return

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

    internal fun gridCellAt(x: Float, y: Float): Int {
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

    internal fun dockCellAt(x: Float, y: Float): Int {
        if (x < dockRect.left || x > dockRect.right) return -1

        if (y < dockRect.top - dp(10f) ||
            y > dockRect.bottom + dp(10f)
        ) return -1

        return ((x - dockLeft) / dockPitch)
            .toInt()
            .coerceIn(0, 3)
    }

    internal fun pkgAt(
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

    // Dibujo principal

    internal var bgNode: RenderNode? = null

    override fun onDraw(canvas: Canvas) {
        val folderOpen = openFolderId != null

        if (folderOpen &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated
        ) {
            val node = bgNode ?: RenderNode("homeBlur").also { bgNode = it }
            node.setPosition(0, 0, width, height)
            node.setRenderEffect(
                RenderEffect.createBlurEffect(
                    dp(26f), dp(26f), Shader.TileMode.CLAMP
                )
            )
            val rc = node.beginRecording()
            drawHome(rc)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            drawHome(canvas)
        }

        if (folderOpen) {
            drawOpenFolder(canvas)
        }
    }

    internal val sqTmp = Path()

    internal var sqBase: Path? = null

    internal var sqBaseSize = -1f

    internal var sqBaseN = -1.0

    internal val sqMatrix = android.graphics.Matrix()

    internal fun activateSlot(slot: String) {
        if (store.isFolderSlot(slot)) {
            openFolder(slot)
        } else {
            launch(slot)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (renameEdit != null) {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                commitRename()
            }

            return true
        }

        if (openFolderId != null) {
            return handleFolderTouch(e)
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageAnimator?.cancel()

                downX = e.x
                downY = e.y

                lastX = e.x
                lastY = e.y

                mode = MODE_PRESS

                val onLib = libOnPage()
                val d = if (onLib) -1 else dockCellAt(e.x, e.y)

                if (onLib) libTouchDown(e)

                if (d >= 0) {
                    pressInDock = true
                    pressIndex = d
                } else {
                    pressInDock = false
                    pressIndex = if (onLib) -1 else gridCellAt(e.x, e.y)
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

                if (libOnPage()) libTrack(e)

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
                            } else if (libOnPage()) {
                                MODE_LIBSCROLL
                            } else {
                                MODE_NONE
                            }

                            if (mode == MODE_SWIPE) {
                                showDots()
                            }

                            if (mode == MODE_LIBSCROLL) {
                                lib.lastY = e.y
                            }
                        }
                    }

                    MODE_SWIPE -> {
                        pageOffset = clampOffset(e.x - downX)
                        invalidate()
                    }

                    MODE_LIBSCROLL -> libScrollTo(e.y)

                    MODE_DRAG -> {
                        dragX = e.x
                        dragY = e.y

                        updateHover()
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
                    MODE_LIBSCROLL -> libEndScroll()

                    MODE_PRESS -> if (libOnPage()) {
                        libTap(e.x, e.y)
                    } else {
                        val slot = pkgAt(
                            pressInDock,
                            pressIndex,
                            currentPage
                        )

                        if (slot != null) {
                            activateSlot(slot)
                        }
                    }

                    MODE_SWIPE -> {
                        val threshold = width / 5f
                        var target = currentPage

                        if (
                            pageOffset < -threshold &&
                            currentPage < store.pages.size
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

                libReleaseTracker()

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
                dragFromFolderId = null
                mode = MODE_NONE

                invalidate()
                return true
            }
        }

        return true
    }

    internal fun clampOffset(dx: Float): Float {
        if (dx > 0 && currentPage == 0) {
            return dx * 0.3f
        }

        if (
            dx < 0 &&
            currentPage == store.pages.size
        ) {
            // Nada más allá de la biblioteca
            return 0f
        }

        return dx
    }

    // Arrastrar y soltar
}
