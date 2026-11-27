
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
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.os.Build
import android.view.WindowManager
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.mio.launcher.layout.LayoutStore
import com.mio.launcher.models.AppFolder
import com.mio.launcher.models.AppItem
import com.mio.launcher.ui.IconRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText

private const val MODE_NONE = 0
private const val MODE_PRESS = 1
private const val MODE_SWIPE = 2
private const val MODE_DRAG = 3
private const val MODE_FOLDER = 4

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

    // Geometría principal

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

    // Carpetas

    private var openFolderId: String? = null
    private var folderPage = 0

    private val folderPanel = RectF()

    private val folderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(30f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setShadowLayer(dp(8f), 0f, dp(3f), 0x66000000)
    }

    private val folderLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11.5f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setShadowLayer(dp(3f), 0f, dp(1f), 0x55000000)
    }

    private val folderPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val folderPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val folderIconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val folderIconBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val folderOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
    }

    private val folderDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val folderIconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val folderHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x14FFFFFF
    }

    private var folderDragPkg: String? = null
    private var dragFromFolderId: String? = null

    private val folderLongPress = Runnable {
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

    private var folderDownX = 0f
    private var folderDownY = 0f
    private var folderPageOffset = 0f
    private var folderPageAnimator: ValueAnimator? = null

    // Puntos de página

    private var dotsMix = 0f
    private var dotsTarget = 0f
    private var dotsAnimator: ValueAnimator? = null

    private val hideDots = Runnable {
        animateDots(0f)
    }

    // Estado

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

        currentPage = store.trimEmptyPages(currentPage)

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

    private fun computeGeometry() {
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

    // Dibujo principal

    private var bgNode: RenderNode? = null

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

    private fun drawHome(canvas: Canvas) {
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

    private fun drawSlot(
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
                    fit(name, size),
                    left + size / 2f,
                    top + size + dp(15f),
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
                size
            ).also {
                app.shown = it
            }

            c.drawText(
                shown,
                left + size / 2f,
                top + size + dp(15f),
                labelPaint
            )
        }
    }

    // Icono cerrado de carpeta: cuadrícula 3x3

    private val sqTmp = Path()
    private var sqBase: Path? = null
    private var sqBaseSize = -1f

    // Misma forma (squircle) que los iconos de las apps
    private fun squirclePath(left: Float, top: Float, size: Float): Path {
        if (sqBase == null || sqBaseSize != size) {
            sqBase = IconRenderer.buildSquircle(size, 5.0)
            sqBaseSize = size
        }

        sqTmp.reset()
        sqTmp.addPath(sqBase!!, left, top)

        return sqTmp
    }

    private fun drawFolderIcon(
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

    private fun folderCount(): Int =
        openFolderId?.let { store.folders[it]?.apps?.size } ?: 0

    // El panel siempre es de 3x3, como en iOS 26
    private fun folderRows(): Int = 3

    private fun folderIconSize(): Float = folderPanel.width() * 0.20f

    private fun folderCellH(): Float = folderPanel.width() * 0.285f

    private fun folderGridTop(): Float =
        folderPanel.top + folderPanel.width() * 0.09f

    private fun folderGridBottom(): Float =
        folderGridTop() + folderRows() * folderCellH()

    private fun folderPanelGeometry() {
        val panelW = min(width * 0.76f, dp(400f))
        val panelH = panelW

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

    private fun drawOpenFolder(c: Canvas) {
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
        folderLabelPaint.textSize = folderPanel.width() * 0.040f

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
                folderPanel.width()
            ),
            folderPanelPaint
        )

        folderPanelPaint.shader = null

        c.drawPath(
            squirclePath(
                folderPanel.left,
                folderPanel.top,
                folderPanel.width()
            ),
            folderPanelBorderPaint
        )

        val pageCount = max(1, (folder.apps.size + 8) / 9)

        c.drawText(
            folder.name,
            width / 2f,
            folderPanel.top - folderPanel.width() * 0.20f,
            folderTitlePaint
        )

        val gridTop = folderGridTop()

        val cellW = folderPanel.width() / 3f
        val cellH = folderCellH()

        val icon = folderIconSize()

        c.save()
        c.clipRect(folderPanel)

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

                val cx = folderPanel.left + (col + 0.5f) * cellW

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
                        cellW - dp(8f)
                    ),
                    cx,
                    top + icon + folderPanel.width() * 0.055f,
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

    private fun fitFolderLabel(
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

    private fun folderCellAt(x: Float, y: Float): Int {
        val id = openFolderId ?: return -1
        val folder = store.folders[id] ?: return -1

        folderPanelGeometry()

        if (!folderPanel.contains(x, y)) return -1

        val gridTop = folderGridTop()
        val gridBottom = folderGridBottom()

        if (y < gridTop || y >= gridBottom) return -1

        val col = ((x - folderPanel.left) /
            (folderPanel.width() / 3f)).toInt()

        val row = ((y - gridTop) /
            folderCellH()).toInt()

        if (col !in 0..2 || row !in 0 until folderRows()) return -1

        val index = folderPage * 9 + row * 3 + col

        return if (index in folder.apps.indices) index else -1
    }

    private fun openFolder(slot: String) {
        val id = store.folderIdFromSlot(slot) ?: return

        if (store.folders[id] == null) return

        folderPageAnimator?.cancel()

        openFolderId = id
        folderPage = 0
        folderPageOffset = 0f
        mode = MODE_NONE

        setWindowBlur(true)
        invalidate()
    }

    private fun closeFolder() {
        folderPageAnimator?.cancel()

        openFolderId = null
        folderPage = 0
        folderPageOffset = 0f
        mode = MODE_NONE

        setWindowBlur(false)
        invalidate()
    }

    // Desenfoca el fondo de pantalla detrás de la ventana (Android 12+)
    private fun setWindowBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        try {
            val w = act.window
            val lp = w.attributes

            if (on) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = dp(60f).toInt()
            } else {
                lp.flags = lp.flags and
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                lp.blurBehindRadius = 0
            }

            w.attributes = lp
        } catch (_: Throwable) {
        }
    }

    private fun showRenameFolderDialog(folderId: String) {
        val folder = store.folders[folderId] ?: return

        val input = EditText(act).apply {
            setText(folder.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }

        AlertDialog.Builder(act)
            .setTitle("Nombre de la carpeta")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()

                if (name.isNotEmpty()) {
                    store.renameFolder(folderId, name)
                    invalidate()
                }
            }
            .show()
    }

    // Animación de páginas de carpeta

    private fun animateFolderToPage(target: Int) {
        val id = openFolderId ?: return
        val folder = store.folders[id] ?: return

        val pages = max(1, (folder.apps.size + 8) / 9)

        if (target !in 0 until pages) {
            folderPageOffset = 0f
            invalidate()
            return
        }

        folderPageAnimator?.cancel()
        folderPanelGeometry()

        val from = folderPageOffset

        val to = when {
            target > folderPage -> -folderPanel.width()
            target < folderPage -> folderPanel.width()
            else -> 0f
        }

        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 260L
            interpolator = DecelerateInterpolator(1.5f)

            addUpdateListener {
                folderPageOffset = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (openFolderId != id) return

                    folderPage = target
                    folderPageOffset = 0f
                    invalidate()
                }
            })
        }

        folderPageAnimator = animator
        animator.start()
    }

    private fun activateSlot(slot: String) {
        if (store.isFolderSlot(slot)) {
            openFolder(slot)
        } else {
            launch(slot)
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
            val textY = cy - (fm.ascent + fm.descent) / 2f

            c.drawText(
                label,
                startX + iconW + gap,
                textY,
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

    private fun drawBadge(
        c: Canvas,
        pkg: String,
        iconLeft: Float,
        iconTop: Float,
        size: Float = iconSize
    ) {
        if (NotifService.dnd) return

        val count = NotifService.counts[pkg] ?: return
        if (count <= 0) return

        drawBadgeValue(c, count, iconLeft, iconTop, size)
    }

    private fun drawBadgeValue(
        c: Canvas,
        count: Int,
        iconLeft: Float,
        iconTop: Float,
        size: Float
    ) {
        val label = if (count > 999) "999+" else count.toString()
        val h = size * 0.40f

        val oldTextSize = badgeText.textSize
        badgeText.textSize = size * 0.248f

        val tw = badgeText.measureText(label)
        val w = max(h, tw + h * 0.62f)

        val cx = iconLeft + size - size * 0.02f
        val cy = iconTop + size * 0.06f

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

        badgeText.textSize = oldTextSize
    }

    private fun drawDragged(c: Canvas) {
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
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
                dragFromFolderId = null
                mode = MODE_NONE

                invalidate()
                return true
            }
        }

        return true
    }

    private fun handleFolderTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                folderPageAnimator?.cancel()
                folderDownX = e.x
                folderDownY = e.y
                folderPageOffset = 0f
                folderDragPkg = null
                mode = MODE_FOLDER

                ui.removeCallbacks(folderLongPress)
                ui.postDelayed(
                    folderLongPress,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - folderDownX
                val dy = e.y - folderDownY

                if (folderDragPkg != null) {
                    dragX = e.x
                    dragY = e.y

                    if (!folderPanel.contains(e.x, e.y)) {
                        leaveFolderWithDrag()
                    } else {
                        invalidate()
                    }

                    return true
                }

                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    ui.removeCallbacks(folderLongPress)
                }

                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    val folder = openFolderId?.let { store.folders[it] }

                    if (folder != null) {
                        val pages = (folder.apps.size + 8) / 9

                        folderPageOffset = when {
                            dx < 0 && folderPage >= pages - 1 -> dx * 0.25f
                            dx > 0 && folderPage <= 0 -> dx * 0.25f
                            else -> dx
                        }

                        invalidate()
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                ui.removeCallbacks(folderLongPress)

                val dx = e.x - folderDownX
                val dy = e.y - folderDownY

                val id = openFolderId
                val folder = id?.let { store.folders[it] }

                val draggedPkg = folderDragPkg

                if (draggedPkg != null) {
                    folderDragPkg = null

                    if (folder != null) {
                        val to = folderCellAt(e.x, e.y)
                        val from = folder.apps.indexOf(draggedPkg)

                        if (to >= 0 && from >= 0 && to != from) {
                            folder.apps.removeAt(from)
                            folder.apps.add(to, draggedPkg)
                            store.save()
                        }
                    }

                    mode = MODE_NONE
                    invalidate()
                    return true
                }

                if (folder == null) {
                    closeFolder()
                    return true
                }

                folderPanelGeometry()

                if (abs(dx) > touchSlop * 2f && abs(dx) > abs(dy)) {
                    val pages = (folder.apps.size + 8) / 9

                    val target = when {
                        dx < -width / 5f && folderPage < pages - 1 ->
                            folderPage + 1

                        dx > width / 5f && folderPage > 0 ->
                            folderPage - 1

                        else -> folderPage
                    }

                    animateFolderToPage(target)
                    return true
                }

                val titleArea = RectF(
                    folderPanel.left,
                    folderPanel.top - folderPanel.width() * 0.40f,
                    folderPanel.right,
                    folderPanel.top - dp(4f)
                )

                if (titleArea.contains(e.x, e.y)) {
                    showRenameFolderDialog(id)
                    return true
                }

                if (!folderPanel.contains(e.x, e.y)) {
                    closeFolder()
                    return true
                }

                val index = folderCellAt(e.x, e.y)

                if (index >= 0) {
                    val pkg = folder.apps.getOrNull(index)

                    if (pkg != null) {
                        launch(pkg)
                        closeFolder()
                    }
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                ui.removeCallbacks(folderLongPress)
                folderDragPkg = null
                animateFolderToPage(folderPage)
                mode = MODE_NONE
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

    // Arrastrar y soltar

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

    private fun leaveFolderWithDrag() {
        val pkg = folderDragPkg ?: return
        val fid = openFolderId ?: return

        folderDragPkg = null

        closeFolder()

        dragFromFolderId = fid
        dragPkg = pkg

        srcInDock = false
        srcPage = currentPage
        srcIndex = -1

        edgeDir = 0
        mode = MODE_DRAG

        parent?.requestDisallowInterceptTouchEvent(true)
        invalidate()
    }

    private fun dropFromFolder(pkg: String, folderId: String) {
        val dIdx = dockCellAt(dragX, dragY)
        val gIdx = if (dIdx >= 0) -1 else gridCellAt(dragX, dragY)

        if (dIdx < 0 && gIdx < 0) return

        val arr: Array<String?> = if (dIdx >= 0) {
            store.dock
        } else {
            store.pages.getOrNull(currentPage) ?: return
        }

        val idx = if (dIdx >= 0) dIdx else gIdx
        val target = arr.getOrNull(idx)

        when {
            target == null -> {
                store.removeAppFromFolder(folderId, pkg)
                arr[idx] = pkg
            }

            store.isFolderSlot(target) -> {
                val tid = store.folderIdFromSlot(target)

                if (tid != null && tid != folderId) {
                    store.removeAppFromFolder(folderId, pkg)
                    store.addAppToFolder(tid, pkg)
                }
            }

            else -> {
                store.removeAppFromFolder(folderId, pkg)

                val folder = store.createFolder(pkg, target)

                if (folder != null) {
                    post {
                        showRenameFolderDialog(folder.id)
                    }
                }
            }
        }
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
        val fromFolder = dragFromFolderId
        dragFromFolderId = null

        if (pkg != null && fromFolder != null) {
            dropFromFolder(pkg, fromFolder)
        } else if (pkg != null) {
            val dIdx = dockCellAt(dragX, dragY)

            val target = if (dIdx >= 0) {
                store.dock.getOrNull(dIdx)
            } else {
                val gIdx = gridCellAt(dragX, dragY)

                if (gIdx >= 0) {
                    store.pages.getOrNull(currentPage)?.getOrNull(gIdx)
                } else {
                    null
                }
            }

            val targetIsValid = dIdx >= 0 ||
                gridCellAt(dragX, dragY) >= 0

            if (targetIsValid) {
                when {
                    target == null -> {
                        place(
                            pkg,
                            dIdx >= 0,
                            currentPage,
                            if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                        )
                    }

                    target == pkg -> {
                        // No hacer nada al soltar sobre el mismo espacio.
                    }

                    store.isFolderSlot(pkg) -> {
                        // Las carpetas se pueden mover entre espacios.
                        place(
                            pkg,
                            dIdx >= 0,
                            currentPage,
                            if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                        )
                    }

                    store.isFolderSlot(target) -> {
                        val folderId = store.folderIdFromSlot(target)

                        if (folderId != null) {
                            store.addAppToFolder(folderId, pkg)
                        }
                    }

                    else -> {
                        val folder = store.createFolder(pkg, target)

                        if (folder == null) {
                            place(
                                pkg,
                                dIdx >= 0,
                                currentPage,
                                if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                            )
                        } else {
                            post {
                                showRenameFolderDialog(folder.id)
                            }
                        }
                    }
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
            store.pages.getOrNull(toPage) ?: return
        }

        val src = if (srcInDock) {
            store.dock
        } else {
            store.pages.getOrNull(srcPage) ?: return
        }

        if (toIdx !in target.indices || srcIndex !in src.indices) return

        val displaced = target[toIdx]

        target[toIdx] = pkg
        src[srcIndex] = displaced
    }

    // Animaciones

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

        val a = ValueAnimator.ofFloat(dotsMix, target)
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
